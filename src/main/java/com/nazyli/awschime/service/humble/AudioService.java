package com.nazyli.awschime.service.humble;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import io.humble.video.*;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AudioService {
    private final AmazonS3 s3Client;

    public AudioService(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Value("${aws.bucket.name}")
    private String bucketName;

    private static final String TMP = "/tmp/v-call/";
    private static final String FILENAME = "processedAudio.mp4";


    public Object getObject(String meetingId) throws IOException, InterruptedException {
        final String PREFIX = "captures/" + meetingId + "/audio";
        String temporary = TMP + PREFIX + "/";
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(PREFIX);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        List<String> filePath = objects.getObjectSummaries().stream().sorted(Comparator.comparing(S3ObjectSummary::getLastModified)).map(S3ObjectSummary::getKey).collect(Collectors.toList());
        new File(temporary).mkdirs();
        Thread t = new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                downloadFile(filePath, temporary);
//                mergeURL(temporary, FILENAME);
                ffmpeg(temporary, FILENAME);
                FileUtils.deleteDirectory(new File(temporary));
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

        File saveAs = new File(directory.replaceAll("/audio", "") + fileName);
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

    public void ffmpeg(String directory, String fileName) throws IOException, InterruptedException {
        String fileList = directory.replaceAll("/audio", "") + "audio_list.txt";
        fileName = directory.replace("/audio", "") + fileName;
        File videoDir = new File(directory);
        File[] ListDir = videoDir.listFiles();
        assert ListDir != null;
        Arrays.sort(ListDir, Comparator.comparingLong(File::lastModified));

        FileWriter myWriter = new FileWriter(fileList);
        for (File key : ListDir) {
            System.out.println(key + " : " + key.toString());
            myWriter.write("file '" + key.toString() + "'\n");
        }
        myWriter.close();
        String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i " + fileList + " -c copy " + fileName + " -y";
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
//        System.out.println("OUTPUT :" + IOUtils.toString(p.getInputStream(), "UTF-8");
//        System.out.println("ERROR :" + IOUtils.toString(p.getErrorStream(), "UTF-8");
    }
}
