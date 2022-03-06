package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.nazyli.awschime.dto.RegisterOffset;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FFMPEGCompositing {
    private final AmazonS3 s3Client;

    public FFMPEGCompositing(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Value("${aws.bucket.name}")
    private String bucketName;

    private static final String PREFIX = "captures/%s/%s/";
    //    private static final String DIRECTORY_SAVE = "/tmp/v-call/" + PREFIX;
    private static final String DIRECTORY_SAVE = "/Users/nazyli/downloads/v-call/" + PREFIX;
    private static final String AUDIO = "audio";
    private static final String VIDEO = "video";
    private static final String AUDIO_NAME = "processedAudio.mp4";
    private static final String VIDEO_NAME = "processedVideo.mp4";
    private static final String FINAL_FILE = "processed.mp4";


    public List<String> composittingObject(String meetingId) throws IOException, InterruptedException, ParseException {
        List<String> videoUrl = listMediaUrl(meetingId, VIDEO);
        List<String> audioUrl = listMediaUrl(meetingId, AUDIO);


        new Thread(() -> {
            try {
                downloadFile(videoUrl, meetingId, VIDEO);
                downloadFile(audioUrl, meetingId, AUDIO);

                registerOffset(AUDIO, audioUrl.get(0));
                String audioLink = audio_process(meetingId);
                Map<String, Object> videoLink = video_process(meetingId);
                composite_process(meetingId, audioLink, videoLink);
                System.out.println("REGISTER : " + RegisterOffset.AUDIO_TIME + " " + RegisterOffset.VIDEO1_TIME + " " + RegisterOffset.VIDEO2_TIME + " " + RegisterOffset.CONTENT_TIME);
            } catch (IOException | ParseException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return Stream.concat(videoUrl.stream(), audioUrl.stream())
                .collect(Collectors.toList());
    }

    public void registerOffset(String type, String key) throws ParseException {
        String timeString = key;
        if (key.lastIndexOf("/") != -1) {
            timeString = key.substring(key.lastIndexOf("/") + 1, key.lastIndexOf(".mp4"));
        }
        timeString = timeString.substring(0, 19);
        Date start_timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").parse(timeString);
        if (type.equalsIgnoreCase(AUDIO)) {
            RegisterOffset.AUDIO_TIME = start_timestamp;
        }
        if (type.equalsIgnoreCase("video1")) {
            RegisterOffset.VIDEO1_TIME = start_timestamp;
        }
        if (type.equalsIgnoreCase("video2")) {
            RegisterOffset.VIDEO2_TIME = start_timestamp;
        }
        if (type.equalsIgnoreCase("content")) {
            RegisterOffset.CONTENT_TIME = start_timestamp;
        }
    }

    public List<String> listMediaUrl(String meetingId, String media) {
        String prefixBucket = String.format(PREFIX, meetingId, media);

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(prefixBucket);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        return objects.getObjectSummaries()
                .stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified))
                .map(S3ObjectSummary::getKey).collect(Collectors.toList());
    }

    public List<String> downloadFile(List<String> urls, String meetingId, String media) throws IOException {
        String saveAs = String.format(DIRECTORY_SAVE, meetingId, media);

        File theDir = new File(saveAs);
        boolean checkDir = theDir.exists();
        if (!checkDir) {
            checkDir = theDir.mkdirs();
        }

        if (checkDir) {
            List<String> path = new ArrayList<>();
            for (String key : urls) {
                GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, key);
                req.setExpiration(new Date(System.currentTimeMillis() + 3600000));
                URL url = s3Client.generatePresignedUrl(req);
                try (InputStream inputStream = url.openStream()) {
                    String fileName = key.substring(key.lastIndexOf("/") + 1, key.lastIndexOf(".mp4"));
                    File file = new File(saveAs + fileName + ".mp4");
                    // commons-io
                    FileUtils.copyInputStreamToFile(inputStream, file);
                    path.add(file.toString());
                }
            }
            return path;
        }
        return null;
    }

    public String audio_process(String meetingId) throws IOException, InterruptedException {
        String directoryAudio = String.format(DIRECTORY_SAVE, meetingId, AUDIO);

        String audioList = directoryAudio.replace("/audio", "") + "audio_list.txt";
        String fileName = directoryAudio.replace("/audio", "") + AUDIO_NAME;

        File audioDir = new File(directoryAudio);
        File[] ListDir = audioDir.listFiles();
        assert ListDir != null;
        Arrays.sort(ListDir, Comparator.comparingLong(File::lastModified));

        FileWriter myWriter = new FileWriter(audioList);
        for (File key : ListDir) {
            myWriter.write("file '" + key.toString() + "'\n");
        }
        myWriter.close();
        String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i " + audioList + " -c copy " + fileName + " -y";
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        System.out.println("ERROR AUDIO :" + IOUtils.toString(p.getErrorStream(), "UTF-8"));

        new File(audioList).delete();
//        FileUtils.deleteDirectory(new File(directoryAudio));
        System.out.println("SUCCESSFULY AUDIO : " + fileName);
        return fileName;
    }

    public Map<String, Object> video_process(String meetingId) throws IOException, InterruptedException, ParseException {
        String userA = "", userB = "";
        Map<String, Object> res = new HashMap<>();
        String directoryVideo = String.format(DIRECTORY_SAVE, meetingId, VIDEO);
        List<String> contentList = new ArrayList<>(), userAList = new ArrayList<>(), userBList = new ArrayList<>();
        String fileNameVideo = directoryVideo.replace("/video", "") + VIDEO_NAME;
        String fileNameUserA = directoryVideo.replace("/video", "") + "usera.mp4";
        String fileNameUserB = directoryVideo.replace("/video", "") + "userb.mp4";


        File videoDir = new File(directoryVideo);
        File[] ListDir = videoDir.listFiles();
        assert ListDir != null;
        Arrays.sort(ListDir, Comparator.comparingLong(File::lastModified));


        for (File key : ListDir) {
            String fileName = key.toString();
            int val = -1;
            for (int i = 0; i < 7; i++) {
                val = fileName.indexOf("-", val + 1);
            }
            String userid = fileName.substring(val + 1);
            if (fileName.contains("#content") && RegisterOffset.CONTENT_TIME == null) {
                registerOffset("content", fileName);
            } else if (userA.isEmpty() && !fileName.contains("#content")) {
                userA = userid;
                registerOffset("video1", fileName);
            } else if (userB.isEmpty() && !userid.equals(userA) && !fileName.contains("#content")) {
                userB = userid;
                registerOffset("video1", fileName);
            }

            if (fileName.contains("#content")) {
                contentList.add(fileName);
            } else if (userA.equals(userid)) {
                userAList.add(fileName);
            } else if (userB.equals(userid)) {
                userBList.add(fileName);
            }
        }

        String contentTXT = directoryVideo.replace("/video", "") + "content_list.txt";
        String userATXT = directoryVideo.replace("/video", "") + "userA_list.txt";
        String userBTXT = directoryVideo.replace("/video", "") + "userB_list.txt";

        FileWriter myWriter1 = new FileWriter(contentTXT);
        for (String key : contentList) {
            myWriter1.write("file '" + key + "'\n");
        }
        myWriter1.close();
        FileWriter myWriter2 = new FileWriter(userATXT);
        for (String key : userAList) {
            myWriter2.write("file '" + key + "'\n");
        }
        myWriter2.close();
        FileWriter myWriter3 = new FileWriter(userBTXT);
        for (String key : userBList) {
            myWriter3.write("file '" + key + "'\n");
        }
        myWriter3.close();

        if (userAList.size() > 0) {
            String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i " + userATXT + " -c copy " + fileNameUserA + " -movflags frag_keyframe+empty_moov -y";
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            System.out.println("ERROR VIDEO A :" + IOUtils.toString(p.getErrorStream(), "UTF-8"));
            res.put("usera", fileNameUserA);
        }
        if (userBList.size() > 0) {
            String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i " + userBTXT + " -c copy  " + fileNameUserB + " -movflags frag_keyframe+empty_moov -y";
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            System.out.println("ERROR VIDEO B :" + IOUtils.toString(p.getErrorStream(), "UTF-8"));
            res.put("userb", fileNameUserB);

        }
        if (contentList.size() > 0) {
            String cmd = "ffmpeg -f concat -safe 0 -protocol_whitelist file,https,tls,tcp -i " + contentTXT + " -c copy " + fileNameVideo + " -movflags frag_keyframe+empty_moov -y";
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            System.out.println("ERROR VIDEO :" + IOUtils.toString(p.getErrorStream(), "UTF-8"));
            res.put("video", fileNameVideo);
        }
        new File(contentTXT).delete();
        new File(userATXT).delete();
        new File(userBTXT).delete();
        return res;
    }

    public String composite_process(String meetingId, String audio, Map<String, Object> video) throws IOException, InterruptedException {
        String directoryVideo = String.format(DIRECTORY_SAVE, meetingId, VIDEO);
        String fileNameVideo = directoryVideo.replace("/video", "") + FINAL_FILE;
        long video1_delay = RegisterOffset.VIDEO1_TIME != null ? TimeUnit.MILLISECONDS.toSeconds(RegisterOffset.VIDEO1_TIME.getTime() - RegisterOffset.AUDIO_TIME.getTime()) : 0;
        long video2_delay = RegisterOffset.VIDEO2_TIME != null ? TimeUnit.MILLISECONDS.toSeconds(RegisterOffset.VIDEO2_TIME.getTime() - RegisterOffset.AUDIO_TIME.getTime()) : 0;
        long content_delay = RegisterOffset.CONTENT_TIME != null ? TimeUnit.MILLISECONDS.toSeconds(RegisterOffset.CONTENT_TIME.getTime() - RegisterOffset.AUDIO_TIME.getTime()) : 0;

        String[] cmd = new String[]{};
        Process p;
        if (video.get("video") != null && video.get("usera") != null && video.get("userb") != null) {
            cmd = new String[]{
                    "ffmpeg",
                    "-i",
                    video.get("usera").toString(),
                    "-i",
                    video.get("userb").toString(),
                    "-i",
                    audio,
                    "-i",
                    video.get("video").toString(),
                    "-filter_complex",
                    "[3:v] scale=640:480, tpad=start_duration=" + content_delay + ":start_mode=add:color=blue[content]; [0:v] scale=120:90, tpad=start_duration=" + video1_delay + ":start_mode=add:color=blue[userA]; [1:v] scale=120:90, tpad=start_duration=" + video2_delay + ":start_mode=add:color=blue[userB]; [content][userA] overlay=510:10[content-userA]; [content-userA][userB] overlay=510:110[final]",
                    "-map",
                    "[final]",
                    "-map",
                    "2:a",
                    "-f",
                    "mp4",
                    "-movflags",
                    "+faststart",
                    fileNameVideo};

        } else if (video.get("video") == null && video.get("usera") != null && video.get("userb") != null) {
            cmd = new String[]{"ffmpeg", "-i", video.get("usera").toString(),
                    "-i",
                    video.get("userb").toString(),
                    "-i",
                    audio,
                    "-filter_complex",
                    "[0:v] scale=640:480, pad=640*2:480, tpad=start_duration=" + video1_delay + ":start_mode=add:color=blue[left]; [1:v] scale=640:480, pad=640*2:480, tpad=start_duration=" + video2_delay + ":start_mode=add:color=blue[right]; [left][right] overlay=main_w/2:0[final]",
                    "-map",
                    "[final]",
                    "-map",
                    "2:a",
                    "-f",
                    "mp4",
                    "-movflags",
                    "+faststart",
                    fileNameVideo
            };
        } else if (video.get("video") != null && video.get("usera") != null) {
            cmd = new String[]{
                    "ffmpeg",
                    "-i",
                    video.get("usera").toString(),
                    "-i",
                    audio,
                    "-i",
                    video.get("video").toString(),
                    "-filter_complex",
                    "[0:v] scale=640:480, tpad=start_duration='" + content_delay + "':start_mode=add:color=blue[content]; [1:v] scale=120:90, tpad=start_duration='" + video1_delay + "':start_mode=add:color=blue[userA]; [content][userA] overlay=510:10[final]",
                    "-map",
                    "[final]",
                    "-map",
                    "1:a",
                    "-f", "mp4",
                    "-movflags",
                    "faststart",
                    fileNameVideo
            };
        } else if (video.get("video") != null) {
//            only content make usera with audio
            video.put("usera", audio);
            cmd = new String[]{
                    "ffmpeg",
                    "-i",
                    video.get("video").toString(),
                    "-i",
                    audio,
                    "-i",
                    video.get("usera").toString(),
                    "-filter_complex",
                    "[0:v] scale=640:480, tpad=start_duration='" + content_delay + "':start_mode=add:color=blue[content]; [1:v] scale=120:90, tpad=start_duration='" + video1_delay + "':start_mode=add:color=blue[userA]; [content][userA] overlay=510:10[final]",
                    "-map",
                    "[final]",
                    "-map",
                    "1:a",
                    "-f",
                    "mp4",
                    "-movflags",
                    "+faststart",
                    fileNameVideo
            };
        }
        System.out.println("CMD : " + Arrays.toString(cmd));
        p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        System.out.println("ERROR :" + IOUtils.toString(p.getErrorStream(), "UTF-8"));

        return fileNameVideo;
    }
}
