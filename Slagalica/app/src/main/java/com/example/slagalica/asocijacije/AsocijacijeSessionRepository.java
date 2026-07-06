package com.example.slagalica.asocijacije;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeSessionRepository {

    private static final String SESSIONS_COLLECTION = "sessions";
    private static final String GAMES_COLLECTION = "games";

    public static final String PHASE_ROUND1 = "round1_active";
    public static final String PHASE_ROUND2 = "round2_active";
    public static final String PHASE_ROUND_RESULT = "round_result";
    public static final String PHASE_FINISHED = "finished";

    public static final String SIDE_OWNER = "owner";
    public static final String SIDE_GUEST = "guest";

    private final FirebaseFirestore db;

    public interface SessionInfoCallback {
        void onSuccess(SessionInfo sessionInfo);
        void onError(String message);
    }

    public interface RepositoryCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface GameStateListener {
        void onGameStateChanged(GameState gameState);
        void onError(String message);
    }

    public AsocijacijeSessionRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadSessionInfo(String sessionId, SessionInfoCallback callback) {
        db.collection(SESSIONS_COLLECTION).document(sessionId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            callback.onError("Sesija nije pronadjena");
                            return;
                        }

                        DocumentSnapshot snapshot = task.getResult();
                        callback.onSuccess(new SessionInfo(
                                snapshot.getString("ownerId"),
                                snapshot.getString("ownerUsername"),
                                snapshot.getString("guestId"),
                                snapshot.getString("guestUsername")
                        ));
                    }
                });
    }

    public ListenerRegistration observeGame(String gameDocId, GameStateListener listener) {
        return db.collection(GAMES_COLLECTION).document(gameDocId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError("Greska pri osluskivanju igre");
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onGameStateChanged(null);
                        return;
                    }
                    listener.onGameStateChanged(mapGameState(snapshot));
                });
    }

    public void fetchGameOnce(String gameDocId, GameStateListener listener) {
        db.collection(GAMES_COLLECTION).document(gameDocId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        listener.onError("Greska pri ucitavanju igre");
                        return;
                    }
                    DocumentSnapshot snapshot = task.getResult();
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onGameStateChanged(null);
                        return;
                    }
                    listener.onGameStateChanged(mapGameState(snapshot));
                });
    }

    public void initializeGame(String gameDocId, SessionInfo sessionInfo, List<AsocijacijeSet> sets,
                               RepositoryCallback callback) {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("gameType", "asocijacije");
        gameData.put("ownerId", sessionInfo.ownerId != null ? sessionInfo.ownerId : "");
        gameData.put("guestId", sessionInfo.guestId != null ? sessionInfo.guestId : "");
        gameData.put("ownerUsername", sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        gameData.put("guestUsername", sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
        gameData.put("round1Set", FirestoreAsocijacijeRepository.toSetMap(sets.get(0)));
        gameData.put("round2Set", FirestoreAsocijacijeRepository.toSetMap(sets.get(1)));
        gameData.put("phase", PHASE_ROUND1);
        gameData.put("currentRound", 1);
        gameData.put("activeSide", SIDE_OWNER);
        gameData.put("mustOpen", true);
        gameData.put("openedFields", new ArrayList<String>());
        gameData.put("solvedColumns", new HashMap<String, Object>());
        gameData.put("finalSolvedBy", null);
        gameData.put("ownerScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("ownerFinals", 0);
        gameData.put("guestFinals", 0);
        gameData.put("resultMessage", "");
        gameData.put("winner", null);
        gameData.put("gameFinished", false);
        gameData.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .set(gameData)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri pokretanju igre"));
    }

    public void updateMove(String gameDocId, String activeSide, boolean mustOpen, List<String> openedFields,
                           Map<String, String> solvedColumns, String finalSolvedBy,
                           int ownerScore, int guestScore, int ownerFinals, int guestFinals,
                           String resultMessage, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("activeSide", activeSide);
        updates.put("mustOpen", mustOpen);
        updates.put("openedFields", new ArrayList<>(openedFields));
        updates.put("solvedColumns", new HashMap<>(solvedColumns));
        updates.put("finalSolvedBy", finalSolvedBy);
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("ownerFinals", ownerFinals);
        updates.put("guestFinals", guestFinals);
        updates.put("resultMessage", resultMessage);

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri azuriranju poteza"));
    }

    public void advancePhase(String gameDocId, String nextPhase, int nextRound, String activeSide, boolean mustOpen,
                             List<String> openedFields, Map<String, String> solvedColumns, String finalSolvedBy,
                             int ownerScore, int guestScore, int ownerFinals, int guestFinals,
                             String resultMessage, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", nextPhase);
        updates.put("currentRound", nextRound);
        updates.put("activeSide", activeSide);
        updates.put("mustOpen", mustOpen);
        updates.put("openedFields", new ArrayList<>(openedFields));
        updates.put("solvedColumns", new HashMap<>(solvedColumns));
        updates.put("finalSolvedBy", finalSolvedBy);
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("ownerFinals", ownerFinals);
        updates.put("guestFinals", guestFinals);
        updates.put("resultMessage", resultMessage);
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri prelazu faze"));
    }

    public void finishGame(String gameDocId, int ownerScore, int guestScore, String winner, String resultMessage,
                           RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", PHASE_FINISHED);
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("winner", winner);
        updates.put("resultMessage", resultMessage);
        updates.put("gameFinished", true);

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri zavrsetku igre"));
    }

    private void notifyCallback(Task<?> task, RepositoryCallback callback, String fallbackMessage) {
        if (callback == null) {
            return;
        }
        if (task.isSuccessful()) {
            callback.onSuccess();
            return;
        }
        String message = task.getException() != null ? task.getException().getMessage() : fallbackMessage;
        callback.onError(message);
    }

    @SuppressWarnings("unchecked")
    private GameState mapGameState(DocumentSnapshot snapshot) {
        Timestamp phaseStartedAt = snapshot.getTimestamp("phaseStartedAt");

        Map<String, String> solvedColumns = new HashMap<>();
        Object rawSolved = snapshot.get("solvedColumns");
        if (rawSolved instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) rawSolved).entrySet()) {
                if (entry.getValue() instanceof String) {
                    solvedColumns.put(entry.getKey(), (String) entry.getValue());
                }
            }
        }

        List<String> openedFields = new ArrayList<>();
        Object rawOpened = snapshot.get("openedFields");
        if (rawOpened instanceof List) {
            for (Object item : (List<Object>) rawOpened) {
                if (item instanceof String) {
                    openedFields.add((String) item);
                }
            }
        }

        return new GameState(
                snapshot.getId(),
                snapshot.getString("ownerId"),
                snapshot.getString("guestId"),
                snapshot.getString("ownerUsername"),
                snapshot.getString("guestUsername"),
                snapshot.getString("phase"),
                snapshot.getLong("currentRound") != null ? snapshot.getLong("currentRound").intValue() : 1,
                snapshot.getString("activeSide"),
                Boolean.TRUE.equals(snapshot.getBoolean("mustOpen")),
                openedFields,
                solvedColumns,
                snapshot.getString("finalSolvedBy"),
                snapshot.getLong("ownerScore") != null ? snapshot.getLong("ownerScore").intValue() : 0,
                snapshot.getLong("guestScore") != null ? snapshot.getLong("guestScore").intValue() : 0,
                snapshot.getLong("ownerFinals") != null ? snapshot.getLong("ownerFinals").intValue() : 0,
                snapshot.getLong("guestFinals") != null ? snapshot.getLong("guestFinals").intValue() : 0,
                FirestoreAsocijacijeRepository.fromSetMap(snapshot.get("round1Set")),
                FirestoreAsocijacijeRepository.fromSetMap(snapshot.get("round2Set")),
                snapshot.getString("resultMessage"),
                Boolean.TRUE.equals(snapshot.getBoolean("gameFinished")),
                snapshot.getString("winner"),
                phaseStartedAt != null ? phaseStartedAt.toDate().getTime() : null
        );
    }

    public static final class SessionInfo {
        public final String ownerId;
        public final String ownerUsername;
        public final String guestId;
        public final String guestUsername;

        public SessionInfo(String ownerId, String ownerUsername, String guestId, String guestUsername) {
            this.ownerId = ownerId;
            this.ownerUsername = ownerUsername;
            this.guestId = guestId;
            this.guestUsername = guestUsername;
        }
    }

    public static final class GameState {
        public final String gameDocId;
        public final String ownerId;
        public final String guestId;
        public final String ownerUsername;
        public final String guestUsername;
        public final String phase;
        public final int currentRound;
        public final String activeSide;
        public final boolean mustOpen;
        public final List<String> openedFields;
        public final Map<String, String> solvedColumns;
        public final String finalSolvedBy;
        public final int ownerScore;
        public final int guestScore;
        public final int ownerFinals;
        public final int guestFinals;
        public final AsocijacijeSet round1Set;
        public final AsocijacijeSet round2Set;
        public final String resultMessage;
        public final boolean gameFinished;
        public final String winner;
        public final Long phaseStartedAtMs;

        public GameState(String gameDocId, String ownerId, String guestId, String ownerUsername,
                         String guestUsername, String phase, int currentRound, String activeSide, boolean mustOpen,
                         List<String> openedFields, Map<String, String> solvedColumns, String finalSolvedBy,
                         int ownerScore, int guestScore, int ownerFinals, int guestFinals,
                         AsocijacijeSet round1Set, AsocijacijeSet round2Set, String resultMessage,
                         boolean gameFinished, String winner, Long phaseStartedAtMs) {
            this.gameDocId = gameDocId;
            this.ownerId = ownerId;
            this.guestId = guestId;
            this.ownerUsername = ownerUsername;
            this.guestUsername = guestUsername;
            this.phase = phase;
            this.currentRound = currentRound;
            this.activeSide = activeSide;
            this.mustOpen = mustOpen;
            this.openedFields = openedFields;
            this.solvedColumns = solvedColumns;
            this.finalSolvedBy = finalSolvedBy;
            this.ownerScore = ownerScore;
            this.guestScore = guestScore;
            this.ownerFinals = ownerFinals;
            this.guestFinals = guestFinals;
            this.round1Set = round1Set;
            this.round2Set = round2Set;
            this.resultMessage = resultMessage;
            this.gameFinished = gameFinished;
            this.winner = winner;
            this.phaseStartedAtMs = phaseStartedAtMs;
        }

        public AsocijacijeSet activeSet() {
            return currentRound == 1 ? round1Set : round2Set;
        }
    }
}
