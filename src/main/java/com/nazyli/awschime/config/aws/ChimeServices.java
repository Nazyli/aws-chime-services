package com.nazyli.awschime.config.aws;

import com.amazonaws.services.chime.model.*;


public interface ChimeServices {
    ListMeetingsResult listMeeting(String nextToken, Integer maxResults);
    Meeting createMeeting();
    Meeting getMeeting(String meetingId);
    Attendee createAttendee(String meetingId);
    Attendee getAttendee(String meetingId, String attendeeId);
    void deleteMeeting(String meetingId);
    ListMediaCapturePipelinesResult listMediaCapture(String nextToken, Integer maxResults);
    MediaCapturePipeline createMediaCapturePipeline(String meetingId);
    DeleteMediaCapturePipelineResult deleteMediaCapturePipeline(String mediaPipelineId);
    ListAttendeesResult listAttendees(String meetingId, String nextToken, Integer maxResults);
}
