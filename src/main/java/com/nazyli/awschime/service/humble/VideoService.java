package com.nazyli.awschime.service.humble;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import io.humble.video.*;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class VideoService {
    private final AmazonS3 s3Client;
    private final AudioService audioService;

    public VideoService(AmazonS3 s3Client, AudioService audioService) {
        this.s3Client = s3Client;
        this.audioService = audioService;
    }

    @Value("${aws.bucket.name}")
    private String bucketName;

    private static final String TMP = "/tmp/v-call/";
    private static final String FILENAME = "processedVideo.mp4";


    public Object getObject(String meetingId) throws IOException, InterruptedException {
        final String PREFIX = "captures/" + meetingId + "/video";
        String temporary = TMP + PREFIX + "/";
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(PREFIX);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        List<String> filePath = objects.getObjectSummaries().stream().map(S3ObjectSummary::getKey).collect(Collectors.toList());
        new File(temporary).mkdirs();
        Thread t = new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                downloadFile(filePath, temporary);
                mergeURL(temporary, FILENAME);
                FileUtils.deleteDirectory(new File(temporary));

//                audio
                audioService.getObject(meetingId);
            }
        });
        t.start();

        return filePath;
    }

    public void mergeURL(String directory, String fileName) throws InterruptedException, IOException {
        File videoDir = new File(directory);
        File[] ListDir = videoDir.listFiles();
        assert ListDir != null;
        Arrays.sort(ListDir, Comparator.comparingLong(File::lastModified));

        File saveAs = new File(directory.replaceAll("/video", "") + fileName);
        Muxer muxer = Muxer.make(saveAs.toString(), null, "mp4");

        final MediaPacket packet = MediaPacket.make();
        long dts_offset = 0;
        long pts_offset = 0;

        for (File key : ListDir) {
            Demuxer demuxer = Demuxer.make();
            demuxer.open(key.toString(), null, false, true, null, null);
            if (muxer.getState() == Muxer.State.STATE_INITED) {
                int numStreams = demuxer.getNumStreams();
                for (int s = 0; s < numStreams; s++) {
                    DemuxerStream demuxerStream = demuxer.getStream(s);
                    Decoder decoder = demuxerStream.getDecoder();
                    muxer.addNewStream(decoder);
                }
                muxer.open(null, null);
            }
            long dts_max = 0;
            long pts_max = 0;
            while (demuxer.read(packet) >= 0) {
                if (packet.isComplete()) {
                    packet.setDts(packet.getDts() + dts_offset);
                    packet.setPts(packet.getPts() + pts_offset);
                    dts_max = packet.getDts() + 1;
                    pts_max = packet.getPts() + 1;

                    muxer.write(packet, false);
                }
            }
            dts_offset = dts_max;
            pts_offset = pts_max;
            demuxer.close();
        }
        muxer.close();
    }


    public List<String> downloadFile(List<String> urls, String temporary) throws IOException {
        List<String> path = new ArrayList<>();
        for (String key : urls) {
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, key);
            req.setExpiration(new Date(System.currentTimeMillis() + 3600000));
            URL url = s3Client.generatePresignedUrl(req);
            System.out.println(url.toString());
            try (InputStream inputStream = url.openStream()) {
                File file = new File(temporary + new Date().getTime() + ".mp4");
                // commons-io
                FileUtils.copyInputStreamToFile(inputStream, file);
                path.add(file.toString());
            }
        }
        return path;
    }


    public InputStream getFileInputStreamFromBucket(String fileName) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, fileName);
        S3Object s3Object = s3Client.getObject(getObjectRequest);
        InputStream fileInputStream = s3Object.getObjectContent();
        System.out.println("File Input Stream fetched from s3 bucket for File " + fileName);
        return fileInputStream;
    }

    public void checkFile(List<String> linkUrl) throws URISyntaxException, IOException {
        FileWriter myWriter = new FileWriter("/tmp/audio_list2.txt");
        for (String key : linkUrl) {
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest("bucket-vcall", key);
            req.setExpiration(new Date(System.currentTimeMillis() + 3600000));
            URL url = s3Client.generatePresignedUrl(req);
            System.out.println(key + " : " + url.toString());
            myWriter.write("file '" + url.toString() + "'\n");
        }
        myWriter.close();
        String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i /tmp/audio_list.txt -c copy /tmp/ " + new Date() + " -y";
        Runtime.getRuntime().exec(cmd);
    }

    private static void merge(List<String> key) throws InterruptedException, IOException {

        File aggregate = new File("/Users/nazyli/Downloads/chime/sample/" + new Date() + ".mp4");

        File videoDir = new File("/Users/nazyli/Downloads/chime/sample/video");
        File[] segments = videoDir.listFiles();
        assert segments != null;
        Arrays.sort(segments, Comparator.comparingLong(File::lastModified));

        Muxer muxer = Muxer.make(aggregate.toString(), null, "mp4");

        final MediaPacket packet = MediaPacket.make();
        long dts_offset = 0;
        long pts_offset = 0;

        for (File segment : segments) {
            Demuxer demuxer = Demuxer.make();
            System.out.println(segment.toString());
            demuxer.open(segment.toString(), null, false, true, null, null);
            if (muxer.getState() == Muxer.State.STATE_INITED) {
                int numStreams = demuxer.getNumStreams();
                for (int s = 0; s < numStreams; s++) {
                    DemuxerStream demuxerStream = demuxer.getStream(s);
                    Decoder decoder = demuxerStream.getDecoder();
                    muxer.addNewStream(decoder);
                }
                muxer.open(null, null);
            }
            long dts_max = 0;
            long pts_max = 0;
            while (demuxer.read(packet) >= 0) {
                if (packet.isComplete()) {
                    packet.setDts(packet.getDts() + dts_offset);
                    packet.setPts(packet.getPts() + pts_offset);
                    dts_max = packet.getDts() + 1;
                    pts_max = packet.getPts() + 1;

                    muxer.write(packet, false);
                }
            }
            dts_offset = dts_max;
            pts_offset = pts_max;
            demuxer.close();
        }
        muxer.close();
    }
}
