package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
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

    private static final String FOLDER_MEETING = "captures/%s/";
    private static final String TEMPORARY_SAVE = "/Users/nazyli/downloads/v-call/" + FOLDER_MEETING;
    private static final String AUDIO = "audio";
    private static final String AUDIO_NAME = "processedAudio.mp4";

    private final AmazonS3 s3Client;

    public FFMPEGNetBramp(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }


    public PutObjectResult AudioProcess(String meetingId) throws IOException {
        String outputFile = String.format(TEMPORARY_SAVE, meetingId);
        String outputFileAudio = outputFile + AUDIO_NAME;

        List<String> path = listMediaUrl(meetingId, AUDIO).stream()
                .map(e -> s3Client.getUrl(bucketName, e).toExternalForm())
                .collect(Collectors.toList());

        try {
            final FFmpeg ffmpeg = new FFmpeg("/usr/local/bin/ffmpeg");
            final FFprobe ffprobe = new FFprobe("/usr/local/bin/ffprobe");

            String filesStrings = Lists.newArrayList(path)
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
            executor.createJob(builder).run();

            return upload(meetingId, outputFileAudio);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public PutObjectResult upload(String meetingId, String fileTmp) {
        String key = String.format(FOLDER_MEETING, meetingId) + AUDIO_NAME;
        return s3Client.putObject(bucketName, key, new File(fileTmp));
    }

    public List<String> listMediaUrl(String meetingId, String media) {
        String prefixBucket = String.format(FOLDER_MEETING, meetingId) + media;
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(prefixBucket);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        return objects.getObjectSummaries()
                .stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified))
                .map(S3ObjectSummary::getKey).collect(Collectors.toList());
    }

}
