package com.nazyli.awschime.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogsConfig {
    String logs;
    String meetingId;
    String attendeeId;
    String appName;
}

// Sample Request
//{ "meetingId": "7e34a954-ffd8-4f63-9a13-6732dd7c0706", "attendeeId": "3104db93-29fa-182d-07fa-ea87b85ea1ae", "appName": "SDK_LOGS", "logs": [ { "sequenceNumber": 0, "message": "no transition found from Disconnecting with Update", "timestampMs": 1646379415860, "logLevel": "WARN" }, { "sequenceNumber": 1, "message": "will retry due to status code TaskFailed and error: serial group task AudioVideoStart/7e34a954-ffd8-4f63-9a13-6732dd7c0706/3104db93-29fa-182d-07fa-ea87b85ea1ae was canceled due to subtask AudioVideoStart/7e34a954-ffd8-4f63-9a13-6732dd7c0706/3104db93-29fa-182d-07fa-ea87b85ea1ae/Timeout15000ms error: serial group task AudioVideoStart/7e34a954-ffd8-4f63-9a13-6732dd7c0706/3104db93-29fa-182d-07fa-ea87b85ea1ae/Timeout15000ms/Peer was canceled due to subtask AudioVideoStart/7e34a954-ffd8-4f63-9a13-6732dd7c0706/3104db93-29fa-182d-07fa-ea87b85ea1ae/Timeout15000ms/Peer/SetRemoteDescriptionTask error: AudioVideoStart/7e34a954-ffd8-4f63-9a13-6732dd7c0706/3104db93-29fa-182d-07fa-ea87b85ea1ae/Timeout15000ms/Peer/SetRemoteDescriptionTask got canceled while waiting for the ICE connection state", "timestampMs": 1646379428347, "logLevel": "WARN" } ] }