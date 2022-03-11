package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FFMPEGNetBramp {
    @Value("${aws.bucket.name}")
    private String bucketName;

    private final AmazonS3 s3Client;
//    private final FFmpeg ffmpeg = new FFmpeg("/usr/local/bin/ffmpeg");
    private final FFmpeg ffmpeg = new FFmpeg();
    private final FFprobe ffprobe = new FFprobe();
    private static final String FOLDER_MEETING = "captures/%s/";
    private static final String TEMPORARY_SAVE = "/tmp/" + FOLDER_MEETING;

    public FFMPEGNetBramp(AmazonS3 s3Client) throws IOException {
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
}