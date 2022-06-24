package com.nazyli.awschime.config.aws;

import com.amazonaws.services.chime.AmazonChime;
import com.amazonaws.services.chime.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChimeServicesImpl implements ChimeServices{
    @Autowired
    private AmazonChime chimeClient;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.account.id}")
    private String awsAccountId;

    @Value("${aws.bucket.name}")
    private String bucketName;

    private static final String FOLDER_NAME = "captures";

    public ListMeetingsResult listMeeting(String nextToken, Integer maxResults) {
        ListMeetingsRequest req = new ListMeetingsRequest();
        req.setNextToken(nextToken);
        req.setMaxResults(maxResults);
        try {
            return chimeClient.listMeetings(req);
        } catch (NotFoundException e) {
            return null;
        }
    }

    public Meeting createMeeting() {
        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setClientRequestToken(UUID.randomUUID().toString());
        req.setMediaRegion(region);
        try {
            return chimeClient.createMeeting(req).getMeeting();
        } catch (Exception e) {
            return null;
        }
    }

    public Meeting getMeeting(String meetingId) {
        GetMeetingRequest req = new GetMeetingRequest();
        req.setMeetingId(meetingId);
        try {
            return chimeClient.getMeeting(req).getMeeting();
        } catch (NotFoundException e) {
            return null;
        }
    }

    public Attendee createAttendee(String meetingId) {
        CreateAttendeeRequest req = new CreateAttendeeRequest();
        req.setMeetingId(meetingId);
        req.setExternalUserId(UUID.randomUUID().toString());
        try {
            return chimeClient.createAttendee(req).getAttendee();
        } catch (NotFoundException e) {
            return null;
        }
    }

    public Attendee getAttendee(String meetingId, String attendeeId) {
        GetAttendeeRequest req = new GetAttendeeRequest();
        req.setMeetingId(meetingId);
        req.setAttendeeId(attendeeId);
        try {
            return chimeClient.getAttendee(req).getAttendee();
        } catch (NotFoundException | ForbiddenException e) {
            return null;
        }
    }
    public ListAttendeesResult listAttendees(String meetingId, String nextToken, Integer maxResults) {
        ListAttendeesRequest req = new ListAttendeesRequest();
        req.setMeetingId(meetingId);
        req.setNextToken(nextToken);
        req.setMaxResults(maxResults);
        try {
            return chimeClient.listAttendees(req);
        } catch (NotFoundException | ForbiddenException e) {
            return null;
        }
    }


    public void deleteMeeting(String meetingId) {
        DeleteMeetingRequest req = new DeleteMeetingRequest();
        req.setMeetingId(meetingId);
        try {
            chimeClient.deleteMeeting(req);
        } catch (NotFoundException ignored) {

        }
    }

    public ListMediaCapturePipelinesResult listMediaCapture(String nextToken, Integer maxResults) {
        ListMediaCapturePipelinesRequest req = new ListMediaCapturePipelinesRequest();
        req.setNextToken(nextToken);
        req.setMaxResults(maxResults);
        try {
            return chimeClient.listMediaCapturePipelines(req);
        } catch (NotFoundException e) {
            return null;
        }
    }


    public MediaCapturePipeline createMediaCapturePipeline(String meetingId){
        CreateMediaCapturePipelineRequest req = new CreateMediaCapturePipelineRequest();
        req.setSourceType("ChimeSdkMeeting");
        req.setSourceArn("arn:aws:chime::" + awsAccountId + ":meeting:" + meetingId);
        req.setSinkType("S3Bucket");
        req.setSinkArn("arn:aws:s3:::" + bucketName + "/" + FOLDER_NAME + "/" + meetingId);
        try {
            return chimeClient.createMediaCapturePipeline(req).getMediaCapturePipeline();
        } catch (NotFoundException e) {
            return null;
        }
    }

    public ArtifactsConfiguration artifactsConfiguration(){
        ArtifactsConfiguration artifactsConfiguration = new ArtifactsConfiguration();
        AudioArtifactsConfiguration audio = new AudioArtifactsConfiguration();
        audio.setMuxType("AudioWithActiveSpeakerVideo");
        artifactsConfiguration.setAudio(audio);

        VideoArtifactsConfiguration video = new VideoArtifactsConfiguration();
        video.setMuxType("Enabled");
        video.setState("VideoOnly");
        artifactsConfiguration.setVideo(video);

        ContentArtifactsConfiguration content = new ContentArtifactsConfiguration();
        content.setMuxType("ContentOnly");
        content.setState("Enabled");
        artifactsConfiguration.setContent(content);

        return artifactsConfiguration;
    }
    public DeleteMediaCapturePipelineResult deleteMediaCapturePipeline(String mediaPipelineId) {
        DeleteMediaCapturePipelineRequest delPipelineRequest = new DeleteMediaCapturePipelineRequest();
        delPipelineRequest.setMediaPipelineId(mediaPipelineId);
        try {
            return chimeClient.deleteMediaCapturePipeline(delPipelineRequest);
        } catch (NotFoundException e) {
            return null;
        }
    }
}
