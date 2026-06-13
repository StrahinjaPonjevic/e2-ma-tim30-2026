package com.example.slagalica.spojnice;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceSessionRepository {

    private static final String SESSIONS_COLLECTION = "sessions";
    private static final String GAMES_COLLECTION = "games";

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

    public SpojniceSessionRepository() {
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

    public ListenerRegistration observeGame(String sessionId, GameStateListener listener) {
        return db.collection(GAMES_COLLECTION).document(sessionId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot snapshot,
                                        com.google.firebase.firestore.FirebaseFirestoreException error) {
                        if (error != null) {
                            listener.onError("Greska pri osluškivanju partije");
                            return;
                        }

                        if (snapshot == null || !snapshot.exists()) {
                            listener.onGameStateChanged(null);
                            return;
                        }

                        listener.onGameStateChanged(mapGameState(snapshot));
                    }
                });
    }

    public void fetchGameOnce(String sessionId, GameStateListener listener) {
        db.collection(GAMES_COLLECTION).document(sessionId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful()) {
                            listener.onError("Greska pri ucitavanju partije");
                            return;
                        }

                        DocumentSnapshot snapshot = task.getResult();
                        if (snapshot == null || !snapshot.exists()) {
                            listener.onGameStateChanged(null);
                            return;
                        }

                        listener.onGameStateChanged(mapGameState(snapshot));
                    }
                });
    }

    public void initializeGame(String sessionId, SessionInfo sessionInfo, List<SpojniceSet> sets,
                               RepositoryCallback callback) {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("sessionId", sessionId);
        gameData.put("gameType", "spojnice");
        gameData.put("ownerId", sessionInfo.ownerId != null ? sessionInfo.ownerId : "");
        gameData.put("guestId", sessionInfo.guestId != null ? sessionInfo.guestId : "");
        gameData.put("ownerUsername", sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        gameData.put("guestUsername", sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
        gameData.put("round1Set", toSetMap(sets.get(0)));
        gameData.put("round2Set", toSetMap(sets.get(1)));
        gameData.put("currentRound", 1);
        gameData.put("phase", "round1_owner_turn");
        gameData.put("ownerScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("attemptsUsed", 0);
        gameData.put("solvedLeftIndices", new ArrayList<Integer>());
        gameData.put("solvedRightIndices", new ArrayList<Integer>());
        gameData.put("ownerSolvedLeftIndices", new ArrayList<Integer>());
        gameData.put("ownerSolvedRightIndices", new ArrayList<Integer>());
        gameData.put("guestSolvedLeftIndices", new ArrayList<Integer>());
        gameData.put("guestSolvedRightIndices", new ArrayList<Integer>());
        gameData.put("ownerAttemptCount", 0);
        gameData.put("guestAttemptCount", 0);
        gameData.put("gameFinished", false);
        gameData.put("winner", null);
        gameData.put("resultMessage", "");
        gameData.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .set(gameData)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri pokretanju igre"));
    }

    public void updateAfterMatch(String sessionId, String nextPhase, int ownerScore, int guestScore,
                                 int attemptsUsed, List<Integer> solvedLeftIndices,
                                 List<Integer> solvedRightIndices, List<Integer> ownerSolvedLeftIndices,
                                 List<Integer> ownerSolvedRightIndices, List<Integer> guestSolvedLeftIndices,
                                 List<Integer> guestSolvedRightIndices, int ownerAttemptCount,
                                 int guestAttemptCount, String resultMessage,
                                 RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", nextPhase);
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("attemptsUsed", attemptsUsed);
        updates.put("solvedLeftIndices", new ArrayList<>(solvedLeftIndices));
        updates.put("solvedRightIndices", new ArrayList<>(solvedRightIndices));
        updates.put("ownerSolvedLeftIndices", new ArrayList<>(ownerSolvedLeftIndices));
        updates.put("ownerSolvedRightIndices", new ArrayList<>(ownerSolvedRightIndices));
        updates.put("guestSolvedLeftIndices", new ArrayList<>(guestSolvedLeftIndices));
        updates.put("guestSolvedRightIndices", new ArrayList<>(guestSolvedRightIndices));
        updates.put("ownerAttemptCount", ownerAttemptCount);
        updates.put("guestAttemptCount", guestAttemptCount);
        updates.put("resultMessage", resultMessage);
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri azuriranju poteza"));
    }

    public void advancePhase(String sessionId, String nextPhase, int nextRound, int ownerScore, int guestScore,
                             List<Integer> solvedLeftIndices, List<Integer> solvedRightIndices,
                             RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", nextPhase);
        updates.put("currentRound", nextRound);
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("attemptsUsed", 0);
        updates.put("solvedLeftIndices", new ArrayList<>(solvedLeftIndices));
        updates.put("solvedRightIndices", new ArrayList<>(solvedRightIndices));
        updates.put("ownerSolvedLeftIndices", new ArrayList<Integer>());
        updates.put("ownerSolvedRightIndices", new ArrayList<Integer>());
        updates.put("guestSolvedLeftIndices", new ArrayList<Integer>());
        updates.put("guestSolvedRightIndices", new ArrayList<Integer>());
        updates.put("ownerAttemptCount", 0);
        updates.put("guestAttemptCount", 0);
        updates.put("resultMessage", "");
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri prelazu faze"));
    }

    public void finishGame(String sessionId, int ownerScore, int guestScore, String winner, String resultMessage,
                           RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", "finished");
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("winner", winner);
        updates.put("resultMessage", resultMessage);
        updates.put("gameFinished", true);

        db.collection(GAMES_COLLECTION).document(sessionId)
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
                snapshot.getLong("ownerScore") != null ? snapshot.getLong("ownerScore").intValue() : 0,
                snapshot.getLong("guestScore") != null ? snapshot.getLong("guestScore").intValue() : 0,
                snapshot.getLong("attemptsUsed") != null ? snapshot.getLong("attemptsUsed").intValue() : 0,
                castIntegerList(snapshot.get("solvedLeftIndices")),
                castIntegerList(snapshot.get("solvedRightIndices")),
                castIntegerList(snapshot.get("ownerSolvedLeftIndices")),
                castIntegerList(snapshot.get("ownerSolvedRightIndices")),
                castIntegerList(snapshot.get("guestSolvedLeftIndices")),
                castIntegerList(snapshot.get("guestSolvedRightIndices")),
                snapshot.getLong("ownerAttemptCount") != null ? snapshot.getLong("ownerAttemptCount").intValue() : 0,
                snapshot.getLong("guestAttemptCount") != null ? snapshot.getLong("guestAttemptCount").intValue() : 0,
                mapSet(snapshot.get("round1Set")),
                mapSet(snapshot.get("round2Set")),
                snapshot.getString("resultMessage"),
                Boolean.TRUE.equals(snapshot.getBoolean("gameFinished")),
                snapshot.getString("winner"),
                phaseStartedAt != null ? phaseStartedAt.toDate().getTime() : null
        );
    }

    private Map<String, Object> toSetMap(SpojniceSet set) {
        Map<String, Object> setMap = new HashMap<>();
        setMap.put("leftItems", new ArrayList<>(set.getLeftItems()));
        setMap.put("rightItems", new ArrayList<>(set.getRightItems()));
        setMap.put("correctRightForLeft", new ArrayList<>(set.getCorrectRightForLeft()));
        return setMap;
    }

    @SuppressWarnings("unchecked")
    private SpojniceSet mapSet(Object rawSet) {
        if (!(rawSet instanceof Map)) {
            return null;
        }

        Map<String, Object> setMap = (Map<String, Object>) rawSet;
        List<String> leftItems = (List<String>) setMap.get("leftItems");
        List<String> rightItems = (List<String>) setMap.get("rightItems");
        List<Number> rawPairs = (List<Number>) setMap.get("correctRightForLeft");

        if (leftItems == null || rightItems == null || rawPairs == null) {
            return null;
        }

        List<Integer> pairs = new ArrayList<>();
        for (Number value : rawPairs) {
            pairs.add(value.intValue());
        }

        return new SpojniceSet(leftItems, rightItems, pairs);
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
        public final String sessionId;
        public final String ownerId;
        public final String guestId;
        public final String ownerUsername;
        public final String guestUsername;
        public final String phase;
        public final int currentRound;
        public final int ownerScore;
        public final int guestScore;
        public final int attemptsUsed;
        public final List<Integer> solvedLeftIndices;
        public final List<Integer> solvedRightIndices;
        public final List<Integer> ownerSolvedLeftIndices;
        public final List<Integer> ownerSolvedRightIndices;
        public final List<Integer> guestSolvedLeftIndices;
        public final List<Integer> guestSolvedRightIndices;
        public final int ownerAttemptCount;
        public final int guestAttemptCount;
        public final SpojniceSet round1Set;
        public final SpojniceSet round2Set;
        public final String resultMessage;
        public final boolean gameFinished;
        public final String winner;
        public final Long phaseStartedAtMs;

        public GameState(String sessionId, String ownerId, String guestId, String ownerUsername, String guestUsername,
                         String phase, int currentRound, int ownerScore, int guestScore, int attemptsUsed,
                         List<Integer> solvedLeftIndices, List<Integer> solvedRightIndices,
                         List<Integer> ownerSolvedLeftIndices, List<Integer> ownerSolvedRightIndices,
                         List<Integer> guestSolvedLeftIndices, List<Integer> guestSolvedRightIndices,
                         int ownerAttemptCount, int guestAttemptCount,
                         SpojniceSet round1Set, SpojniceSet round2Set, String resultMessage, boolean gameFinished,
                         String winner, Long phaseStartedAtMs) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.guestId = guestId;
            this.ownerUsername = ownerUsername;
            this.guestUsername = guestUsername;
            this.phase = phase;
            this.currentRound = currentRound;
            this.ownerScore = ownerScore;
            this.guestScore = guestScore;
            this.attemptsUsed = attemptsUsed;
            this.solvedLeftIndices = solvedLeftIndices;
            this.solvedRightIndices = solvedRightIndices;
            this.ownerSolvedLeftIndices = ownerSolvedLeftIndices;
            this.ownerSolvedRightIndices = ownerSolvedRightIndices;
            this.guestSolvedLeftIndices = guestSolvedLeftIndices;
            this.guestSolvedRightIndices = guestSolvedRightIndices;
            this.ownerAttemptCount = ownerAttemptCount;
            this.guestAttemptCount = guestAttemptCount;
            this.round1Set = round1Set;
            this.round2Set = round2Set;
            this.resultMessage = resultMessage;
            this.gameFinished = gameFinished;
            this.winner = winner;
            this.phaseStartedAtMs = phaseStartedAtMs;
        }
    }
}
