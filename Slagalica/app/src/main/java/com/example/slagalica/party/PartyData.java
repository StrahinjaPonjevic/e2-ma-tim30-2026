package com.example.slagalica.party;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class PartyData {

    public static final String TYPE_REGULAR = "regular";
    public static final String TYPE_FRIENDLY = "friendly";
    public static final String TYPE_CHALLENGE = "challenge";

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_FINISHED = "finished";
    public static final String STATUS_FORFEITED = "forfeited";

    public static final String[] GAME_KEYS = {
            "ko_zna_zna",
            "spojnice",
            "asocijacije",
            "skocko",
            "korak_po_korak",
            "moj_broj"
    };

    public String partyId;
    public String ownerId;
    public String ownerUsername;
    public String guestId;
    public String guestUsername;
    public String type;
    public String status;
    public int currentGameIndex;
    public String currentGameKey;
    public int ownerTotalScore;
    public int guestTotalScore;
    public Map<String, Object> gameScores;
    public String winner;
    public String forfeitedBy;
    public boolean ownerForfeited;
    public boolean guestForfeited;
    public boolean countsForStats;
    public boolean usesTokens;
    public boolean rewardApplied;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public static PartyData fromSnapshot(DocumentSnapshot snapshot) {
        PartyData data = new PartyData();
        data.partyId = snapshot.getId();
        data.ownerId = snapshot.getString("ownerId");
        data.ownerUsername = snapshot.getString("ownerUsername");
        data.guestId = snapshot.getString("guestId");
        data.guestUsername = snapshot.getString("guestUsername");
        data.type = valueOrDefault(snapshot.getString("type"), TYPE_REGULAR);
        data.status = valueOrDefault(snapshot.getString("status"), STATUS_WAITING);
        data.currentGameIndex = intValue(snapshot.get("currentGameIndex"));
        data.currentGameKey = valueOrDefault(snapshot.getString("currentGameKey"), GAME_KEYS[0]);
        data.ownerTotalScore = intValue(snapshot.get("ownerTotalScore"));
        data.guestTotalScore = intValue(snapshot.get("guestTotalScore"));
        Object rawScores = snapshot.get("gameScores");
        data.gameScores = rawScores instanceof Map ? new HashMap<>((Map<String, Object>) rawScores) : new HashMap<>();
        data.winner = snapshot.getString("winner");
        data.forfeitedBy = snapshot.getString("forfeitedBy");
        data.ownerForfeited = Boolean.TRUE.equals(snapshot.getBoolean("ownerForfeited"));
        data.guestForfeited = Boolean.TRUE.equals(snapshot.getBoolean("guestForfeited"));
        data.countsForStats = !Boolean.FALSE.equals(snapshot.getBoolean("countsForStats"));
        data.usesTokens = !Boolean.FALSE.equals(snapshot.getBoolean("usesTokens"));
        data.rewardApplied = Boolean.TRUE.equals(snapshot.getBoolean("rewardApplied"));
        data.createdAt = snapshot.getTimestamp("createdAt");
        data.updatedAt = snapshot.getTimestamp("updatedAt");
        return data;
    }

    public boolean isRegular() {
        return TYPE_REGULAR.equals(type);
    }

    public boolean isFriendly() {
        return TYPE_FRIENDLY.equals(type);
    }

    public String gameDocId() {
        return partyId + "_" + currentGameKey;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> currentGameScoreMap() {
        Object raw = gameScores != null ? gameScores.get(currentGameKey) : null;
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return new HashMap<>();
    }

    public boolean hasCurrentUserSubmittedSoloScore(String uid) {
        Map<String, Object> current = currentGameScoreMap();
        if (uid != null && uid.equals(ownerId)) {
            return current.get("ownerScore") instanceof Number;
        }
        if (uid != null && uid.equals(guestId)) {
            return current.get("guestScore") instanceof Number;
        }
        return false;
    }

    public boolean isOwner(String uid) {
        return uid != null && uid.equals(ownerId);
    }

    public boolean hasForfeit() {
        return ownerForfeited || guestForfeited || forfeitedBy != null;
    }

    public boolean hasCurrentUserForfeited(String uid) {
        if (uid != null && uid.equals(ownerId)) {
            return ownerForfeited;
        }
        if (uid != null && uid.equals(guestId)) {
            return guestForfeited;
        }
        return false;
    }

    public static String displayNameForGame(String gameKey) {
        switch (gameKey) {
            case "ko_zna_zna":
                return "Ko zna zna";
            case "spojnice":
                return "Spojnice";
            case "asocijacije":
                return "Asocijacije";
            case "skocko":
                return "Skocko";
            case "korak_po_korak":
                return "Korak po korak";
            case "moj_broj":
                return "Moj broj";
            default:
                return gameKey;
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }
}
