package com.nazyli.awschime.controller;

import com.nazyli.awschime.config.aws.ChimeServices;
import com.nazyli.awschime.service.FFMPEGCompositing;
import com.nazyli.awschime.service.humble.VideoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/rest")
public class ServiceMeetingController {
    private final ChimeServices chimeService;
    private final VideoService videoService;
    private final FFMPEGCompositing ffmpegCompositing;

    public ServiceMeetingController(ChimeServices chimeService, VideoService videoService, FFMPEGCompositing ffmpegCompositing) {
        this.chimeService = chimeService;
        this.videoService = videoService;
        this.ffmpegCompositing = ffmpegCompositing;
    }

    @GetMapping("/humble/{id}")
    public Object convertHumble(@PathVariable String id) throws IOException, InterruptedException, ParseException {
        Map<String, Object> res = new HashMap<>();
        res.put("listVideo", videoService.getObject(id));
        return res;
    }

    @GetMapping("/ffmpeg/{id}")
    public Object converFFMPEG(@PathVariable String id) throws IOException, InterruptedException, ParseException {
        Map<String, Object> res = new HashMap<>();
        res.put("listMedia", ffmpegCompositing.composittingObject(id));
        return res;
    }

}
