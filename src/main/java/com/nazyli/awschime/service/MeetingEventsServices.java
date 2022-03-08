package com.nazyli.awschime.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MeetingEventsServices {
    @Value("${aws.bucket.name}")
    private String bucketName;

    private static final String FOLDER_MEETING = "captures/%s/";
    private static final String MEETING_EVENTS = "meeting-events";

    private final AmazonS3 s3Client;

    public MeetingEventsServices(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    public List<Object> getObject(String meetingId) throws IOException {
        List<S3ObjectInputStream> file = listMediaUrl(meetingId).stream()
                .map(e -> s3Client.getObject(bucketName, e).getObjectContent())
                .collect(Collectors.toList());
        List<Object> readFile = new ArrayList<>();

        for (S3ObjectInputStream s3 : file) {
            InputStreamReader streamReader = new InputStreamReader(s3, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(streamReader);
            String line;
            while ((line = reader.readLine()) != null) {
                readFile.add(new ObjectMapper().readValue(line, Object.class));
            }
        }
        return readFile;
    }

    public List<String> listMediaUrl(String meetingId) {
        String prefixBucket = String.format(FOLDER_MEETING, meetingId) + MEETING_EVENTS;
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName).withPrefix(prefixBucket);
        ObjectListing objects = s3Client.listObjects(listObjectsRequest);
        return objects.getObjectSummaries()
                .stream()
                .sorted(Comparator.comparing(S3ObjectSummary::getLastModified))
                .map(S3ObjectSummary::getKey).collect(Collectors.toList());
    }
}
