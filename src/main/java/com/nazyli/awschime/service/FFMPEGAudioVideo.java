package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.nazyli.awschime.dto.ContentDto;
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
    private static final String TEMPORARY_SAVE = "/Users/nazyli/downloads/v-call/" + FOLDER_MEETING;

    public FFMPEGAudioVideo(AmazonS3 s3Client) throws IOException {
        this.s3Client = s3Client;
    }

    public String mergeMedia(String meetingId) throws ParseException, IOException {
        String key = "";
        String audioName = "audio_" + meetingId + ".mp4";
//        List<String> audioUrl = listMediaUrl(meetingId, "audio").stream()
//                .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
//                .collect(Collectors.toList());
//
//        Date startAudio = toDate(audioUrl.get(0));
//
//        String fileAudio = audioProcess(meetingId, audioName, audioUrl);
//        if (fileAudio != null) {
//
//            String videoName = "video_" + meetingId + ".mp4";
//            List<String> videoUrl = listMediaUrl(meetingId, "video").stream()
//                    .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
//                    .collect(Collectors.toList());
//            if (!videoUrl.isEmpty()) {
//                String fileVideo = videoProcess(meetingId, fileAudio, startAudio, videoName, videoUrl);
//                if (fileVideo != null) {
//                    key = String.format(FOLDER_MEETING, meetingId) + videoName;
////                    s3Client.putObject(bucketName, key, new File(fileVideo));
//                } else {
//                    throw new RuntimeException("Error video merge");
//                }
//            } else {
//                key = String.format(FOLDER_MEETING, meetingId) + audioName;
////                s3Client.putObject(bucketName, key, new File(fileAudio));
//            }
//
//        } else {
//            throw new RuntimeException("Error audio merge");
//        }
//        FileUtils.deleteDirectory(new File(String.format(TEMPORARY_SAVE, meetingId)));
        key = String.format(FOLDER_MEETING, meetingId) + audioName;

        s3Client.putObject(bucketName, key, new File("/Users/nazyli/downloads/v-call/captures/3603d522-41da-4ca8-9fce-4d34491b0706/audio_3603d522-41da-4ca8-9fce-4d34491b0706.mp4"));

        return s3Client.getUrl(bucketName, key).toExternalForm();
    }

    public String audioProcess(String meetingId, String audioName, List<String> audioUrl) throws IOException {
        String outputFile = String.format(TEMPORARY_SAVE, meetingId);
        String outputFileAudio = outputFile + audioName;

        File theDir = new File(outputFile);
        boolean checkDir = theDir.exists();
        if (!checkDir && !audioUrl.isEmpty()) {
            checkDir = theDir.mkdirs();
        }
        if (checkDir) {
            try {
                String filesStrings = Lists.newArrayList(audioUrl)
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
                    return outputFileAudio;
                }
                return null;
            } catch (IOException e) {
                throw new IOException(e);
            }
        }
        return null;
    }

    public String videoProcess(String meetingId, String fileAudio, Date startAudio, String videoName, List<String> videoUrl) throws IOException, ParseException {
        String outputFile = String.format(TEMPORARY_SAVE, meetingId);
        String outputFileContent = outputFile + videoName;

        List<ContentDto> contentList = new ArrayList<>();
        for (String url : videoUrl) {
            FFmpegProbeResult probeResult = ffprobe.probe(url);
            FFmpegFormat format = probeResult.getFormat();
            double duration = format.duration;
            Date urlDate = toDate(url);
            long start = urlDate != null ? urlDate.getTime() - startAudio.getTime() : 0;
            long end = (long) ((duration * 1000) + start);

            ContentDto contentDto = new ContentDto();
            contentDto.setUrl(url);
            contentDto.setDateContent(toDate(url));
            contentDto.setStart(start);
            contentDto.setEnd(end);
            contentDto.setDuration(duration);
            contentList.add(contentDto);
        }

        File theDir = new File(outputFile);
        boolean checkDir = theDir.exists();
        if (!checkDir && !videoUrl.isEmpty()) {
            checkDir = theDir.mkdirs();
        }
        if (checkDir) {
            videoUrl.add(0, fileAudio);
            FFmpegBuilder builder = new FFmpegBuilder();
            videoUrl.forEach(builder::addInput);
            builder.setComplexFilter(filterComplexVideo(contentList))
                    .addOutput(outputFileContent)
//                    .setAudioCodec("aac")
                    .setVideoCodec("libx264")
                    .addExtraArgs("-vsync", "2", "-map", "[final]", "-map", "0:a")
                    .setFormat("mp4")
                    .setVideoMovFlags("+faststart")
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            FFmpegJob job = executor.createJob(builder);
            job.run();

            Runtime rt = Runtime.getRuntime();
//            FFmpegJob.State.RUNNING
//                    job.getState()


            if (FFmpegJob.State.FINISHED == job.getState()) {
                return outputFileContent;
            }
            return null;
        }

        return null;
    }

    public String filterComplexVideo(List<ContentDto> contentList) {
        StringBuilder filterComplex = new StringBuilder(), finalAll = new StringBuilder("[audio]");
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
                stopContent = "[0:v] scale=640:480,trim=start='" + l.getEnd() + "ms':end='" + contentList.get(i).getStart() + "ms', setpts=PTS-STARTPTS[stop-content-" + i + "]; ";
            } else {
                stopContent = "[0:v] scale=640:480,trim=start='" + l.getEnd() + "ms', setpts=PTS-STARTPTS[stop-content-" + i + "]; ";
            }
            String resultContent = "[content-user-" + i + "][stop-content-" + i + "]concat=n=2[result-content-" + i + "]; ";
            filterComplex.append(content + audio + overlay + stopContent + resultContent);

            finalAll.append("[result-content-" + i + "]");
            i++;
        }
        finalAll.append("concat=n=" + i + "[final]");
        return filterComplex.append(finalAll).toString();
    }

    public List<String> listMediaUrl(String meetingId, String folder) {
        String prefixBucket = String.format(FOLDER_MEETING, meetingId) + folder;
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(prefixBucket);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        List<S3ObjectSummary> summaries = objects.getObjectSummaries();
        while (objects.isTruncated()) {
            objects = s3Client.listNextBatchOfObjects (objects);
            summaries.addAll (objects.getObjectSummaries());
        }
        return summaries
                .stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified))
                .map(S3ObjectSummary::getKey).collect(Collectors.toList());
    }

    public Date toDate(String key) throws ParseException {
        String timeString = key;
        if (key.lastIndexOf("/") != -1) {
            timeString = key.substring(key.lastIndexOf("/") + 1, key.lastIndexOf(".mp4"));
        }
        timeString = timeString.substring(0, 23);
        return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").parse(timeString);
    }
}

/*
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

 */

/*
CONTENT RESOULITON 1920 * 1080
ffmpeg -i /Users/nazyli/downloads/processedAudio.mp4
-i /Users/nazyli/downloads/processedVideo.mp4
-i /Users/nazyli/downloads/processedAudio.mp4
-filter_complex "
[0:v] scale=1920:1080,trim=0:15[audio];
[1:v] scale=1920:1080, tpad=start_duration='0':start_mode=add:color=blue[content];
[2:v] scale=360:202, trim=start=15, setpts=PTS-STARTPTS[user];
[content][user] overlay=1530:10:eof_action=pass[content-user];
[audio][content-user]concat=n=2[final]
" -vsync 2 -map "[final]" -map 0:a  -f mp4 -movflags +faststart /Users/nazyli/downloads/processed.mp4

//Sample overlay
[content][user] overlay=main_w-(overlay_w+10):10:eof_action=pass[content-user];

//
ffmpeg -i /Users/nazyli/downloads/processedAudio.mp4
-i /Users/nazyli/downloads/v-call/captures/a4b6afef-6990-4f36-adff-f55695330706/video/2022-03-12-06-27-38-975-b6ede27c-310a-9ba5-286c-6fe086752645#content.mp4
-i /Users/nazyli/downloads/v-call/captures/a4b6afef-6990-4f36-adff-f55695330706/video/2022-03-12-06-28-02-326-b6ede27c-310a-9ba5-286c-6fe086752645#content.mp4
-filter_complex "
[0:v] scale=640:480,trim=start='0':end='25', setpts=PTS-STARTPTS[audio];

[1:v] scale=640:480, tpad=start_mode=add:color=blue [content-1];
[0:v] scale=120:90, trim=start='25', setpts=PTS-STARTPTS[user-1];
[content-1][user-1] overlay=510:10:eof_action=pass[content-user-1];
[0:v] scale=640:480,trim=start='28':end='47', setpts=PTS-STARTPTS[end-1];
[content-user-1][end-1]concat=n=2[res-1];

[2:v] scale=640:480, tpad=start_mode=add:color=blue [content-2];
[0:v] scale=120:90, trim=start='48', setpts=PTS-STARTPTS[user-2];
[content-2][user-2] overlay=510:10:eof_action=pass[content-user-2];
[0:v] scale=640:480,trim=start='50078ms':end='50078ms', setpts=PTS-STARTPTS[end-2];
[content-user-2][end-2]concat=n=2[res-2];

[audio][res-1][res-2]concat=n=3[final]
" -vsync 2 -map "[final]" -map 0:a -f mp4 -movflags +faststart /Users/nazyli/downloads/processed.mp4

[0:v] scale=640:480,trim=start='28':end=28+19(end + next), setpts=PTS-STARTPTS[end];
 */