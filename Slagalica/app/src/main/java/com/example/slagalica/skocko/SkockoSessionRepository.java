package com.example.slagalica.skocko;

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

public class SkockoSessionRepository {

    private static final String SESSIONS_COLLECTION = "sessions";
    private static final String GAMES_COLLECTION = "games";

    public static final String PHASE_ROUND1_ACTIVE = "round1_active";
    public static final String PHASE_ROUND1_TAKEOVER = "round1_takeover";
    public static final String PHASE_ROUND2_ACTIVE = "round2_active";
    public static final String PHASE_ROUND2_TAKEOVER = "round2_takeover";
    public static final String PHASE_ROUND_RESULT = "round_result";
    public static final String PHASE_FINISHED = "finished";

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

    public SkockoSessionRepository() {
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

    public void initializeGame(String gameDocId, SessionInfo sessionInfo, List<Integer> secret1,
                               List<Integer> secret2, RepositoryCallback callback) {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("gameType", "skocko");
        gameData.put("ownerId", sessionInfo.ownerId != null ? sessionInfo.ownerId : "");
        gameData.put("guestId", sessionInfo.guestId != null ? sessionInfo.guestId : "");
        gameData.put("ownerUsername", sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        gameData.put("guestUsername", sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
        gameData.put("secret1", new ArrayList<>(secret1));
        gameData.put("secret2", new ArrayList<>(secret2));
        gameData.put("phase", PHASE_ROUND1_ACTIVE);
        gameData.put("currentRound", 1);
        gameData.put("rows", new ArrayList<String>());
        gameData.put("ownerScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("ownerHitAttempt", 0);
        gameData.put("guestHitAttempt", 0);
        gameData.put("resultMessage", "");
        gameData.put("winner", null);
        gameData.put("gameFinished", false);
        gameData.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .set(gameData)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri pokretanju igre"));
    }

    public void updateRows(String gameDocId, List<String> rows, String resultMessage, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("rows", new ArrayList<>(rows));
        updates.put("resultMessage", resultMessage);

        db.collection(GAMES_COLLECTION).document(gameDocId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri azuriranju poteza"));
    }

    public void advancePhase(String gameDocId, String nextPhase, int nextRound, List<String> rows,
                             int ownerScore, int guestScore, int ownerHitAttempt, int guestHitAttempt,
                             String resultMessage, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", nextPhase);
        updates.put("currentRound", nextRound);
        updates.put("rows", new ArrayList<>(rows));
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("ownerHitAttempt", ownerHitAttempt);
        updates.put("guestHitAttempt", guestHitAttempt);
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

        return new GameState(
                snapshot.getId(),
                snapshot.getString("ownerId"),
                snapshot.getString("guestId"),
                snapshot.getString("ownerUsername"),
                snapshot.getString("guestUsername"),
                snapshot.getString("phase"),
                snapshot.getLong("currentRound") != null ? snapshot.getLong("currentRound").intValue() : 1,
                castIntegerList(snapshot.get("secret1")),
                castIntegerList(snapshot.get("secret2")),
                castStringList(snapshot.get("rows")),
                snapshot.getLong("ownerScore") != null ? snapshot.getLong("ownerScore").intValue() : 0,
                snapshot.getLong("guestScore") != null ? snapshot.getLong("guestScore").intValue() : 0,
                snapshot.getLong("ownerHitAttempt") != null ? snapshot.getLong("ownerHitAttempt").intValue() : 0,
                snapshot.getLong("guestHitAttempt") != null ? snapshot.getLong("guestHitAttempt").intValue() : 0,
                snapshot.getString("resultMessage"),
                Boolean.TRUE.equals(snapshot.getBoolean("gameFinished")),
                snapshot.getString("winner"),
                phaseStartedAt != null ? phaseStartedAt.toDate().getTime() : null
        );
    }

    @SuppressWarnings("unchecked")
    private List<Integer> castIntegerList(Object rawValue) {
        List<Integer> values = new ArrayList<>();
        if (!(rawValue instanceof List)) {
            return values;
        }
        for (Object item : (List<Object>) rawValue) {
            if (item instanceof Number) {
                values.add(((Number) item).intValue());
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object rawValue) {
        List<String> values = new ArrayList<>();
        if (!(rawValue instanceof List)) {
            return values;
        }
        for (Object item : (List<Object>) rawValue) {
            if (item instanceof String) {
                values.add((String) item);
            }
        }
        return values;
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
        public final List<Integer> secret1;
        public final List<Integer> secret2;
        public final List<String> rows;
        public final int ownerScore;
        public final int guestScore;
        public final int ownerHitAttempt;
        public final int guestHitAttempt;
        public final String resultMessage;
        public final boolean gameFinished;
        public final String winner;
        public final Long phaseStartedAtMs;

        public GameState(String gameDocId, String ownerId, String guestId, String ownerUsername,
                         String guestUsername, String phase, int currentRound, List<Integer> secret1,
                         List<Integer> secret2, List<String> rows, int ownerScore, int guestScore,
                         int ownerHitAttempt, int guestHitAttempt, String resultMessage, boolean gameFinished,
                         String winner, Long phaseStartedAtMs) {
            this.gameDocId = gameDocId;
            this.ownerId = ownerId;
            this.guestId = guestId;
            this.ownerUsername = ownerUsername;
            this.guestUsername = guestUsername;
            this.phase = phase;
            this.currentRound = currentRound;
            this.secret1 = secret1;
            this.secret2 = secret2;
            this.rows = rows;
            this.ownerScore = ownerScore;
            this.guestScore = guestScore;
            this.ownerHitAttempt = ownerHitAttempt;
            this.guestHitAttempt = guestHitAttempt;
            this.resultMessage = resultMessage;
            this.gameFinished = gameFinished;
            this.winner = winner;
            this.phaseStartedAtMs = phaseStartedAtMs;
        }

        public List<Integer> activeSecret() {
            return currentRound == 1 ? secret1 : secret2;
        }
    }
}
