package com.nazyli.awschime.controller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.nazyli.awschime.config.aws.ChimeServices;
import com.nazyli.awschime.service.FFMPEGNetBramp;
import com.nazyli.awschime.service.FFMPEGCompositing;
import com.nazyli.awschime.service.humble.VideoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.*;

@RestController
@RequestMapping("/rest")
public class ServiceMeetingController {
    private final ChimeServices chimeService;
    private final VideoService videoService;
    private final FFMPEGCompositing ffmpegCompositing;
    private final AmazonS3 s3Client;
    private final FFMPEGNetBramp ffmpegByMaven;

    @Value("${aws.bucket.name}")
    private String bucketName;

    public ServiceMeetingController(ChimeServices chimeService, VideoService videoService, FFMPEGCompositing ffmpegCompositing, AmazonS3 s3Client, FFMPEGNetBramp ffmpegByMaven) {
        this.chimeService = chimeService;
        this.videoService = videoService;
        this.ffmpegCompositing = ffmpegCompositing;
        this.s3Client = s3Client;
        this.ffmpegByMaven = ffmpegByMaven;
    }

    @GetMapping("/humble/{id}")
    public Object convertHumble(@PathVariable String id) throws IOException, InterruptedException {
        Map<String, Object> res = new HashMap<>();
        res.put("listVideo", videoService.getObject(id));
        return res;
    }

    @GetMapping("/ffmpeg/{id}")
    public Object convertFFMPEG(@PathVariable String id) throws IOException, InterruptedException, ParseException {
        Map<String, Object> res = new HashMap<>();
        res.put("listMedia", ffmpegCompositing.composittingObject(id));
        return res;
    }

    @GetMapping("/ffmpeg-bramp/{id}")
    public Object test(@PathVariable String id) throws IOException {
        Map<String, Object> res = new HashMap<>();
        res.put("listMedia", ffmpegByMaven.AudioProcess(id));
        return res;
    }

    @GetMapping("/listMeeting")
    public Object listMeeting(){
        Map<String, Object> res = new HashMap<>();
        res.put("listMeeting", chimeService.listMeeting(null, null));
        return res;
    }

    @GetMapping("/listMediaCapture")
    public Object listMediaCapture() {
        Map<String, Object> res = new HashMap<>();
        res.put("listMediaCapture", chimeService.listMediaCapture(null, null));
        return res;
    }

    @GetMapping("/listBucket")
    public Object listBucket() {
        Map<String, Object> res = new HashMap<>();
        List<Bucket> buckets = s3Client.listBuckets();
        List<String> bucketName = new ArrayList<>();
        for(Bucket bucket : buckets) {
            bucketName.add(bucket.getName());
        }
        res.put("bucketName", bucketName);
        return res;
    }

    @GetMapping("/listObject")
    public Object listObject() {
        Map<String, Object> res = new HashMap<>();
        List<String> listObject = new ArrayList<>();
        ObjectListing objectListing = s3Client.listObjects(bucketName);
        for(S3ObjectSummary os : objectListing.getObjectSummaries()) {
            listObject.add(os.getKey());
        }
        res.put("listBucket", listObject);
        return res;
    }

    @GetMapping("/object")
    public URI object(@RequestParam String key) {
        return s3Client.getObject(bucketName, key).getObjectContent().getHttpRequest().getURI();
    }

}
