package com.example.slagalica.challenge;

import com.example.slagalica.party.PartyData;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ChallengeData {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_FINISHED = "finished";
    public static final String[] GAME_KEYS = PartyData.GAME_KEYS;

    public String challengeId;
    public String region;
    public String creatorId;
    public String creatorUsername;
    public int starsStake;
    public int tokensStake;
    public String status;
    public Map<String, Object> participants;
    public Map<String, Object> scores;
    public Map<String, Object> runs;
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
        data.runs = snapshot.get("runs") instanceof Map
                ? new HashMap<>((Map<String, Object>) snapshot.get("runs"))
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

    public int totalScore(String uid) {
        Object value = scores != null ? scores.get(uid) : null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        Map<String, Object> run = runFor(uid);
        return intValue(run.get("totalScore"));
    }

    public boolean hasCompletedRun(String uid) {
        Map<String, Object> run = runFor(uid);
        return "completed".equals(run.get("status")) || hasScore(uid);
    }

    public int currentGameIndex(String uid) {
        Map<String, Object> run = runFor(uid);
        int index = intValue(run.get("currentGameIndex"));
        if (index < 0) {
            return 0;
        }
        if (index >= GAME_KEYS.length) {
            return GAME_KEYS.length - 1;
        }
        return index;
    }

    public String currentGameKey(String uid) {
        if (hasCompletedRun(uid)) {
            return null;
        }
        return GAME_KEYS[currentGameIndex(uid)];
    }

    public String gameDocId(String uid, String gameKey) {
        return challengeId + "_" + uid + "_" + gameKey;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runFor(String uid) {
        Object raw = uid != null && runs != null ? runs.get(uid) : null;
        if (raw instanceof Map) {
            return new HashMap<>((Map<String, Object>) raw);
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("currentGameIndex", 0);
        fallback.put("totalScore", 0);
        fallback.put("status", STATUS_IN_PROGRESS);
        fallback.put("gameScores", new HashMap<String, Object>());
        return fallback;
    }

    public String participantName(String uid) {
        Object value = participants != null ? participants.get(uid) : null;
        return value != null ? String.valueOf(value) : uid;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
