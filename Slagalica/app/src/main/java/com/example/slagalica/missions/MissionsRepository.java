package com.example.slagalica.missions;

import com.example.slagalica.ranking.CycleUtils;
import com.example.slagalica.ranking.StarRewardHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public final class MissionsRepository {

    public static final String MISSION_WIN_PARTY = "winParty";
    public static final String MISSION_CHAT_MESSAGE = "chatMessage";
    public static final String MISSION_FRIENDLY_PARTY = "friendlyParty";
    public static final String MISSION_TOURNAMENT_WIN = "tournamentWin";

    private static final String[] ALL_MISSIONS = {
            MISSION_WIN_PARTY, MISSION_CHAT_MESSAGE, MISSION_FRIENDLY_PARTY, MISSION_TOURNAMENT_WIN
    };

    private static final String USERS = "users";
    private static final String MISSIONS = "missions";

    public interface MissionsListener {
        void onChanged(MissionState state);
        void onError(String message);
    }

    private MissionsRepository() {
    }

    public static void markPartyWon(String uid) {
        complete(uid, MISSION_WIN_PARTY);
    }

    public static void markChatMessage(String uid) {
        complete(uid, MISSION_CHAT_MESSAGE);
    }

    public static void markFriendlyPlayed(String uid) {
        complete(uid, MISSION_FRIENDLY_PARTY);
    }

    public static void markTournamentWin(String uid) {
        complete(uid, MISSION_TOURNAMENT_WIN);
    }

    private static void complete(String uid, String missionField) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String dayKey = CycleUtils.currentDayKey();
        DocumentReference missionRef = db.collection(USERS).document(uid)
                .collection(MISSIONS).document(dayKey);
        DocumentReference userRef = db.collection(USERS).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot missionSnap = transaction.get(missionRef);
            if (missionSnap.exists() && Boolean.TRUE.equals(missionSnap.getBoolean(missionField))) {
                return null;
            }

            DocumentSnapshot userSnap = transaction.get(userRef);
            if (!userSnap.exists()) {
                return null;
            }

            Map<String, Object> missionData = new HashMap<>();
            boolean allCompleted = true;
            for (String mission : ALL_MISSIONS) {
                boolean completed = mission.equals(missionField)
                        || (missionSnap.exists() && Boolean.TRUE.equals(missionSnap.getBoolean(mission)));
                missionData.put(mission, completed);
                allCompleted = allCompleted && completed;
            }

            boolean bonusAlreadyAwarded = missionSnap.exists()
                    && Boolean.TRUE.equals(missionSnap.getBoolean("bonusAwarded"));
            boolean awardBonusNow = allCompleted && !bonusAlreadyAwarded;

            missionData.put("bonusAwarded", bonusAlreadyAwarded || awardBonusNow);
            missionData.put("dayKey", dayKey);
            missionData.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(missionRef, missionData);

            int starsDelta = 3 + (awardBonusNow ? 3 : 0);
            int tokensDelta = awardBonusNow ? 2 : 0;
            transaction.update(userRef, StarRewardHelper.starAwardUpdates(userSnap, starsDelta, tokensDelta));
            return null;
        });
    }

    public static ListenerRegistration listenToday(String uid, MissionsListener listener) {
        String dayKey = CycleUtils.currentDayKey();
        return FirebaseFirestore.getInstance()
                .collection(USERS).document(uid)
                .collection(MISSIONS).document(dayKey)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage() != null
                                ? error.getMessage()
                                : "Misije nisu dostupne");
                        return;
                    }
                    listener.onChanged(MissionState.fromSnapshot(snapshot));
                });
    }

    public static final class MissionState {
        public final boolean winParty;
        public final boolean chatMessage;
        public final boolean friendlyParty;
        public final boolean tournamentWin;
        public final boolean bonusAwarded;

        private MissionState(boolean winParty, boolean chatMessage, boolean friendlyParty,
                             boolean tournamentWin, boolean bonusAwarded) {
            this.winParty = winParty;
            this.chatMessage = chatMessage;
            this.friendlyParty = friendlyParty;
            this.tournamentWin = tournamentWin;
            this.bonusAwarded = bonusAwarded;
        }

        public static MissionState fromSnapshot(DocumentSnapshot snapshot) {
            if (snapshot == null || !snapshot.exists()) {
                return new MissionState(false, false, false, false, false);
            }
            return new MissionState(
                    Boolean.TRUE.equals(snapshot.getBoolean(MISSION_WIN_PARTY)),
                    Boolean.TRUE.equals(snapshot.getBoolean(MISSION_CHAT_MESSAGE)),
                    Boolean.TRUE.equals(snapshot.getBoolean(MISSION_FRIENDLY_PARTY)),
                    Boolean.TRUE.equals(snapshot.getBoolean(MISSION_TOURNAMENT_WIN)),
                    Boolean.TRUE.equals(snapshot.getBoolean("bonusAwarded"))
            );
        }

        public int completedCount() {
            int count = 0;
            if (winParty) count++;
            if (chatMessage) count++;
            if (friendlyParty) count++;
            if (tournamentWin) count++;
            return count;
        }
    }
}
