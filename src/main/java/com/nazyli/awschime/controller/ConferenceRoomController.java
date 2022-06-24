package com.nazyli.awschime.controller;

import com.amazonaws.services.chime.model.*;
import com.nazyli.awschime.config.aws.ChimeServices;
import com.nazyli.awschime.dto.LogsConfig;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/rest")
public class ConferenceRoomController {
    private final ChimeServices chimeService;

    public ConferenceRoomController(ChimeServices chimeService) {
        this.chimeService = chimeService;
    }

    @PostMapping("/breakouts")
    public Meeting breakouts(@RequestParam String meeting) {
        return chimeService.getMeeting(meeting);
    }
    @PostMapping("/create")
    public Map<String, Object> create(@RequestParam String meeting,
                                      @RequestParam String breakout) {
        System.out.println(breakout);
        Meeting meet = chimeService.getMeeting(breakout);
        if (meet == null) {
            meet = chimeService.createMeeting();
            System.out.println("Creating new meeting before joining: " + meet);
        }
        Map<String, Object> joinInfo = new HashMap<>();
        joinInfo.put("Title", meeting);
        joinInfo.put("Meeting", meet);

        Map<String, Object> res = new HashMap<>();
        res.put("JoinInfo", joinInfo);
        return res;
    }

    @PostMapping("/join")
    public Object joinAPi(@RequestParam String title) {
        Meeting meet = chimeService.getMeeting(title);
        if (meet == null) {
            meet = chimeService.createMeeting();
        }
        Attendee attendee = chimeService.createAttendee(meet.getMeetingId());
        System.out.println(meet.getMeetingId());

        Map<String, Object> joinInfo = new HashMap<>();
        joinInfo.put("Meeting", meet);
        joinInfo.put("Attendee", attendee);

        Map<String, Object> res = new HashMap<>();
        res.put("JoinInfo", joinInfo);
        return res;
    }

    @GetMapping("/attendee")
    public Object getAttendee(@RequestParam String title,
                              @RequestParam String attendee) {
        Attendee att = chimeService.getAttendee(title, attendee);

        Map<String, Object> attendInfo = new HashMap<>();
        attendInfo.put("AttendeeId", att.getAttendeeId());
        attendInfo.put("Name", "Evry Nazyli");

        Map<String, Object> res = new HashMap<>();
        res.put("AttendeeInfo", attendInfo);
        return res;
    }

    @PostMapping("/end")
    public Object deleteMeeting(@RequestParam String title) {
        Map<String, Object> res = new HashMap<>();
        chimeService.deleteMeeting(title);
        res.put("deleted", true);
        return res;
    }

    @PostMapping("/record")
    public Object record(@RequestParam String meetingTitle,
                            @RequestParam boolean setRecording,
                            @RequestParam(defaultValue = "") String mediaPipeline) {

        if (setRecording) {
            return chimeService.deleteMediaCapturePipeline(mediaPipeline);
        } else {
            MediaCapturePipeline mcp = chimeService.createMediaCapturePipeline(meetingTitle);
            System.out.println("CAPTURE INFO : " + mcp.toString());

            Map<String, Object> res = new HashMap<>();
            res.put("MediaCapturePipeline", mcp);
            return res;

        }
    }

    @PostMapping("/logs")
    public Object logs(@RequestBody LogsConfig ls) {
        System.out.println("LOGS : " + ls.toString());
        return ls;
    }
}
