package com.example.slagalica.tournament;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentData {

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_SEMIFINALS = "semifinals";
    public static final String STATUS_FINAL = "final";
    public static final String STATUS_FINISHED = "finished";

    public String tournamentId;
    public String status;
    public List<String> playerIds = new ArrayList<>();
    public List<Map<String, Object>> players = new ArrayList<>();
    public String semi1PartyId;
    public String semi2PartyId;
    public String finalPartyId;
    public String semi1WinnerId;
    public String semi2WinnerId;
    public boolean semi1Rewarded;
    public boolean semi2Rewarded;
    public String finalWinnerId;
    public boolean finalRewarded;
    public Timestamp createdAt;

    @SuppressWarnings("unchecked")
    public static TournamentData fromSnapshot(DocumentSnapshot snapshot) {
        TournamentData data = new TournamentData();
        data.tournamentId = snapshot.getId();
        data.status = snapshot.getString("status") != null ? snapshot.getString("status") : STATUS_WAITING;

        Object rawIds = snapshot.get("playerIds");
        if (rawIds instanceof List) {
            for (Object item : (List<Object>) rawIds) {
                if (item instanceof String) {
                    data.playerIds.add((String) item);
                }
            }
        }

        Object rawPlayers = snapshot.get("players");
        if (rawPlayers instanceof List) {
            for (Object item : (List<Object>) rawPlayers) {
                if (item instanceof Map) {
                    data.players.add(new HashMap<>((Map<String, Object>) item));
                }
            }
        }

        data.semi1PartyId = snapshot.getString("semi1PartyId");
        data.semi2PartyId = snapshot.getString("semi2PartyId");
        data.finalPartyId = snapshot.getString("finalPartyId");
        data.semi1WinnerId = snapshot.getString("semi1WinnerId");
        data.semi2WinnerId = snapshot.getString("semi2WinnerId");
        data.semi1Rewarded = Boolean.TRUE.equals(snapshot.getBoolean("semi1Rewarded"));
        data.semi2Rewarded = Boolean.TRUE.equals(snapshot.getBoolean("semi2Rewarded"));
        data.finalWinnerId = snapshot.getString("finalWinnerId");
        data.finalRewarded = Boolean.TRUE.equals(snapshot.getBoolean("finalRewarded"));
        data.createdAt = snapshot.getTimestamp("createdAt");
        return data;
    }

    public boolean containsPlayer(String uid) {
        return uid != null && playerIds.contains(uid);
    }

    public Map<String, Object> playerInfo(String uid) {
        for (Map<String, Object> player : players) {
            if (uid != null && uid.equals(player.get("uid"))) {
                return player;
            }
        }
        return null;
    }

    public String playerUsername(String uid) {
        Map<String, Object> info = playerInfo(uid);
        Object username = info != null ? info.get("username") : null;
        return username instanceof String ? (String) username : "Igrac";
    }

    public boolean isActive() {
        return STATUS_WAITING.equals(status) || STATUS_SEMIFINALS.equals(status) || STATUS_FINAL.equals(status);
    }
}
