package com.example.slagalica.challenge;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ChallengeData {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_FINISHED = "finished";

    public String challengeId;
    public String region;
    public String creatorId;
    public String creatorUsername;
    public int starsStake;
    public int tokensStake;
    public String status;
    public Map<String, Object> participants;
    public Map<String, Object> scores;
    public String winnerId;
    public String secondPlaceId;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    @SuppressWarnings("unchecked")
    public static ChallengeData fromSnapshot(DocumentSnapshot snapshot) {
        ChallengeData data = new ChallengeData();
        data.challengeId = snapshot.getId();
        data.region = snapshot.getString("region");
        data.creatorId = snapshot.getString("creatorId");
        data.creatorUsername = snapshot.getString("creatorUsername");
        data.starsStake = intValue(snapshot.get("starsStake"));
        data.tokensStake = intValue(snapshot.get("tokensStake"));
        data.status = snapshot.getString("status");
        data.participants = snapshot.get("participants") instanceof Map
                ? new HashMap<>((Map<String, Object>) snapshot.get("participants"))
                : new HashMap<>();
        data.scores = snapshot.get("scores") instanceof Map
                ? new HashMap<>((Map<String, Object>) snapshot.get("scores"))
                : new HashMap<>();
        data.winnerId = snapshot.getString("winnerId");
        data.secondPlaceId = snapshot.getString("secondPlaceId");
        data.createdAt = snapshot.getTimestamp("createdAt");
        data.updatedAt = snapshot.getTimestamp("updatedAt");
        return data;
    }

    public boolean hasParticipant(String uid) {
        return uid != null && participants != null && participants.containsKey(uid);
    }

    public boolean hasScore(String uid) {
        return uid != null && scores != null && scores.get(uid) instanceof Number;
    }

    public String participantName(String uid) {
        Object value = participants != null ? participants.get(uid) : null;
        return value != null ? String.valueOf(value) : uid;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
