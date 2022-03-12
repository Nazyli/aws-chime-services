package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.nazyli.awschime.dto.ContentDto;
import com.nazyli.awschime.dto.RegisterOffset;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FFMPEGAudioVideo {
    @Value("${aws.bucket.name}")
    private String bucketName;

    private final AmazonS3 s3Client;
    //    private final FFmpeg ffmpeg = new FFmpeg("/usr/local/bin/ffmpeg");
    private final FFmpeg ffmpeg = new FFmpeg();
    private final FFprobe ffprobe = new FFprobe();
    private static final String FOLDER_MEETING = "captures/%s/";
    private static final String TEMPORARY_SAVE = "/tmp/" + FOLDER_MEETING;

    public FFMPEGAudioVideo(AmazonS3 s3Client) throws IOException {
        this.s3Client = s3Client;
    }

    public Object AudioCompositing(String meetingId) throws IOException {
        String audio = "audio";
        String audioName = audio + "_" + meetingId + ".mp4";
        String outputFile = String.format(TEMPORARY_SAVE, meetingId);
        String outputFileAudio = outputFile + audioName;

        List<String> pathList = listMediaUrl(meetingId, audio).stream()
                .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
                .collect(Collectors.toList());

        File theDir = new File(outputFile);
        boolean checkDir = theDir.exists();
        if (!checkDir && !pathList.isEmpty()) {
            checkDir = theDir.mkdirs();
        }
        if (checkDir) {
            try {
                String filesStrings = Lists.newArrayList(pathList)
                        .stream()
                        .map(p -> "file '" + p + "'")
                        .collect(Collectors.joining(System.getProperty("line.separator")));

                Path listOfFiles = Files.createTempFile(Path.of(outputFile), "ffmpeg-audio-", ".txt");
                Files.write(listOfFiles, filesStrings.getBytes());

                FFmpegBuilder builder = new FFmpegBuilder()
                        .setInput(listOfFiles.toAbsolutePath().toString())
                        .addExtraArgs("-f", "concat", "-safe", "0", "-protocol_whitelist", "file,https,tls,tcp")
                        .setFormat("mp4")
                        .addOutput(outputFileAudio)
                        .addExtraArgs("-c", "copy")
                        .setAudioCodec("aac")
                        .setVideoCodec("libx264")
                        .done();
                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
                FFmpegJob job = executor.createJob(builder);
                job.run();
                if (FFmpegJob.State.FINISHED == job.getState()) {
                    String key = String.format(FOLDER_MEETING, meetingId) + audioName;
                    s3Client.putObject(bucketName, key, new File(outputFileAudio));
                    FileUtils.deleteDirectory(new File(outputFile));

                    return s3Client.getUrl(bucketName, key).toExternalForm();
                }
                return null;
            } catch (IOException e) {
                throw new IOException(e);
            }
        }
        return null;
    }

    public Object VideoCompositing(String meetingId) throws IOException, ParseException {
        List<String> audioUrl = listMediaUrl(meetingId, "audio").stream()
                .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
                .collect(Collectors.toList());

        List<String> videoUrl = listMediaUrl(meetingId, "video").stream()
                .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
                .collect(Collectors.toList());

        registerOffset("audio", audioUrl.get(0));

        List<ContentDto> contentList = new ArrayList<>();
        for (String url : videoUrl) {
            FFmpegProbeResult probeResult = ffprobe.probe(url);
            FFmpegFormat format = probeResult.getFormat();
            double duration = format.duration;
            Date urlDate = toDate(url);
            long start = urlDate != null ? urlDate.getTime() - RegisterOffset.AUDIO_TIME.getTime() : 0;
            long end = (long) ((duration * 1000) + start);

            ContentDto contentDto = new ContentDto();
            contentDto.setUrl(url);
            contentDto.setDateContent(toDate(url));
            contentDto.setStart(start);
            contentDto.setEnd(end);
            contentDto.setDuration(duration);
            contentList.add(contentDto);
        }
        long next = 0;
        for (int i = 0; i < contentList.size(); i++) {
            if (i != contentList.size() - 1) {
                next = contentList.get(i + 1).getStart() - contentList.get(i).getEnd();
            }
            contentList.get(i).setNext(next);

            Map<String, Object> map = new HashMap<>();
            map.put("start", toStringTime(contentList.get(i).getStart()));
            map.put("end", toStringTime(contentList.get(i).getEnd()));
            map.put("next", toStringTime(contentList.get(i).getNext()));
            contentList.get(i).setInString(map);
        }

        StringBuilder inputVideo = new StringBuilder();
        StringBuilder finalAll = new StringBuilder("[audio]");
        StringBuilder filterComplex = new StringBuilder();
        int i = 1;
        for (ContentDto l : contentList) {
            if (i == 1) {
                filterComplex.append(" [0:v] scale=640:480,trim=start='0ms':end='" + l.getStart() + "ms', setpts=PTS-STARTPTS[audio]; ");
            }

            String content = "[" + i + ":v] scale=640:480[content-" + i + "]; ";
            String audio = "[0:v] scale=120:90, trim=start='" + l.getStart() + "ms', setpts=PTS-STARTPTS[user-" + i + "]; ";
            String overlay = "[content-" + i + "][user-" + i + "] overlay=510:10:eof_action=pass[content-user-" + i + "]; ";
            String stopContent;
            if (contentList.size() != i) {
                stopContent = "[0:v] scale=640:480,trim=start='" + l.getEnd() + "ms':end='" + (l.getEnd() + l.getNext()) + "ms', setpts=PTS-STARTPTS[stop-content-" + i + "]; ";
            } else {
                stopContent = "[0:v] scale=640:480,trim=start='" + l.getEnd() + "ms', setpts=PTS-STARTPTS[stop-content-" + i + "]; ";
            }
            String resultContent = "[content-user-" + i + "][stop-content-" + i + "]concat=n=2[result-content-" + i + "]; ";
            finalAll.append("[result-content-" + i + "]");
            String finalyContent = content + audio + overlay + stopContent + resultContent;
            filterComplex.append(finalyContent);
            inputVideo.append(" -i ").append(l.getUrl());

            i++;
        }
        finalAll.append("concat=n=" + i + "[final]");
        Map<String, Object> result = new HashMap<>();
        result.put("contentList", contentList);
        result.put("inputVideo", inputVideo.toString());
        result.put("filterComplex", filterComplex.toString());
        result.put("final", finalAll.toString());

        return result;
    }

    public List<String> listMediaUrl(String meetingId, String folder) {
        String prefixBucket = String.format(FOLDER_MEETING, meetingId) + folder;
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(prefixBucket);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        return objects.getObjectSummaries()
                .stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified))
                .map(S3ObjectSummary::getKey).collect(Collectors.toList());
    }


    public void registerOffset(String type, String key) throws ParseException {
        Date start_timestamp = toDate(key);
        if (type.equalsIgnoreCase("audio")) {
            RegisterOffset.AUDIO_TIME = start_timestamp;
        }
        if (type.equalsIgnoreCase("content")) {
            RegisterOffset.CONTENT_TIME = start_timestamp;
        }
    }

    public Date toDate(String key) throws ParseException {
        String timeString = key;
        if (key.lastIndexOf("/") != -1) {
            timeString = key.substring(key.lastIndexOf("/") + 1, key.lastIndexOf(".mp4"));
        }
        timeString = timeString.substring(0, 23);
        return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").parse(timeString);
    }

    public String toStringTime(long time) {
        return new SimpleDateFormat("mm.ss.SSS").format(new Date(time));
    }
}
