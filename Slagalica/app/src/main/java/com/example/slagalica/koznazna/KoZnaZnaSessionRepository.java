package com.example.slagalica.koznazna;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaSessionRepository {

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

    public KoZnaZnaSessionRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadSessionInfo(String sessionId, SessionInfoCallback callback) {
        db.collection(SESSIONS_COLLECTION).document(sessionId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            callback.onError("Sesija nije pronađena");
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
                            listener.onError("Greška pri osluškivanju partije");
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
                            listener.onError("Greška pri učitavanju partije");
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

    public void initializeGame(String sessionId, SessionInfo sessionInfo, List<QuizQuestion> questions,
                               RepositoryCallback callback) {
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("sessionId", sessionId);
        gameData.put("gameType", "ko_zna_zna");
        gameData.put("ownerId", sessionInfo.ownerId != null ? sessionInfo.ownerId : "");
        gameData.put("guestId", sessionInfo.guestId != null ? sessionInfo.guestId : "");
        gameData.put("ownerUsername", sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrač 1");
        gameData.put("guestUsername", sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrač 2");
        gameData.put("questions", toQuestionMapList(questions));
        gameData.put("currentQuestionIndex", 0);
        gameData.put("phase", "question_active");
        gameData.put("ownerScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("ownerAnswerIndex", null);
        gameData.put("guestAnswerIndex", null);
        gameData.put("ownerAnswerTimeMs", null);
        gameData.put("guestAnswerTimeMs", null);
        gameData.put("ownerCorrectAnswers", 0);
        gameData.put("ownerWrongAnswers", 0);
        gameData.put("guestCorrectAnswers", 0);
        gameData.put("guestWrongAnswers", 0);
        gameData.put("resultMessage", "");
        gameData.put("gameFinished", false);
        gameData.put("winner", null);
        gameData.put("questionStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .set(gameData)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greška pri pokretanju igre"));
    }

    public void submitAnswer(String sessionId, boolean isOwner, int answerIndex, long answerTimeMs) {
        Map<String, Object> updates = new HashMap<>();
        if (isOwner) {
            updates.put("ownerAnswerIndex", answerIndex);
            updates.put("ownerAnswerTimeMs", answerTimeMs);
        } else {
            updates.put("guestAnswerIndex", answerIndex);
            updates.put("guestAnswerTimeMs", answerTimeMs);
        }

        db.collection(GAMES_COLLECTION).document(sessionId).update(updates);
    }

    public void publishQuestionResult(String sessionId, GameState state,
                                      KoZnaZnaEvaluator.EvaluationResult result, RepositoryCallback callback) {
        QuizQuestion currentQuestion = null;
        if (state.questions != null
                && state.currentQuestionIndex >= 0
                && state.currentQuestionIndex < state.questions.size()) {
            currentQuestion = state.questions.get(state.currentQuestionIndex);
        }

        boolean ownerCorrect = currentQuestion != null
                && state.ownerAnswerIndex != null
                && state.ownerAnswerIndex == currentQuestion.getCorrectAnswerIndex();
        boolean guestCorrect = currentQuestion != null
                && state.guestAnswerIndex != null
                && state.guestAnswerIndex == currentQuestion.getCorrectAnswerIndex();

        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", "question_result");
        updates.put("ownerScore", state.ownerScore + result.getOwnerDelta());
        updates.put("guestScore", state.guestScore + result.getGuestDelta());
        updates.put("ownerCorrectAnswers", state.ownerCorrectAnswers + (ownerCorrect ? 1 : 0));
        updates.put("ownerWrongAnswers", state.ownerWrongAnswers + (!ownerCorrect && state.ownerAnswerIndex != null ? 1 : 0));
        updates.put("guestCorrectAnswers", state.guestCorrectAnswers + (guestCorrect ? 1 : 0));
        updates.put("guestWrongAnswers", state.guestWrongAnswers + (!guestCorrect && state.guestAnswerIndex != null ? 1 : 0));
        updates.put("resultMessage", result.getResultMessage());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greška pri zaključavanju pitanja"));
    }

    public void startNextQuestion(String sessionId, int nextQuestionIndex, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentQuestionIndex", nextQuestionIndex);
        updates.put("phase", "question_active");
        updates.put("ownerAnswerIndex", null);
        updates.put("guestAnswerIndex", null);
        updates.put("ownerAnswerTimeMs", null);
        updates.put("guestAnswerTimeMs", null);
        updates.put("resultMessage", "");
        updates.put("questionStartedAt", FieldValue.serverTimestamp());

        db.collection(GAMES_COLLECTION).document(sessionId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greška pri prelazu na sledeće pitanje"));
    }

    public void finishGame(String sessionId, int ownerScore, int guestScore, String winner, RepositoryCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", "finished");
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("winner", winner);
        updates.put("gameFinished", true);

        db.collection(GAMES_COLLECTION).document(sessionId)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greška pri završetku igre"));
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
        List<QuizQuestion> questions = mapQuestions(snapshot.get("questions"));

        return new GameState(
                snapshot.getId(),
                snapshot.getString("ownerId"),
                snapshot.getString("guestId"),
                snapshot.getString("ownerUsername"),
                snapshot.getString("guestUsername"),
                snapshot.getString("phase"),
                snapshot.getLong("currentQuestionIndex") != null
                        ? snapshot.getLong("currentQuestionIndex").intValue() : 0,
                snapshot.getLong("ownerScore") != null ? snapshot.getLong("ownerScore").intValue() : 0,
                snapshot.getLong("guestScore") != null ? snapshot.getLong("guestScore").intValue() : 0,
                snapshot.getLong("ownerAnswerIndex") != null ? snapshot.getLong("ownerAnswerIndex").intValue() : null,
                snapshot.getLong("guestAnswerIndex") != null ? snapshot.getLong("guestAnswerIndex").intValue() : null,
                snapshot.getLong("ownerAnswerTimeMs"),
                snapshot.getLong("guestAnswerTimeMs"),
                snapshot.getLong("ownerCorrectAnswers") != null ? snapshot.getLong("ownerCorrectAnswers").intValue() : 0,
                snapshot.getLong("ownerWrongAnswers") != null ? snapshot.getLong("ownerWrongAnswers").intValue() : 0,
                snapshot.getLong("guestCorrectAnswers") != null ? snapshot.getLong("guestCorrectAnswers").intValue() : 0,
                snapshot.getLong("guestWrongAnswers") != null ? snapshot.getLong("guestWrongAnswers").intValue() : 0,
                snapshot.getString("resultMessage"),
                Boolean.TRUE.equals(snapshot.getBoolean("gameFinished")),
                snapshot.getString("winner"),
                questions
        );
    }

    private List<Map<String, Object>> toQuestionMapList(List<QuizQuestion> questions) {
        List<Map<String, Object>> questionMaps = new ArrayList<>();
        for (QuizQuestion question : questions) {
            Map<String, Object> questionMap = new HashMap<>();
            questionMap.put("text", question.getText());

            List<String> answers = new ArrayList<>();
            for (String answer : question.getAnswers()) {
                answers.add(answer);
            }
            questionMap.put("answers", answers);
            questionMap.put("correctAnswerIndex", question.getCorrectAnswerIndex());
            questionMaps.add(questionMap);
        }
        return questionMaps;
    }

    @SuppressWarnings("unchecked")
    private List<QuizQuestion> mapQuestions(Object rawQuestions) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (!(rawQuestions instanceof List)) {
            return questions;
        }

        List<Object> rawList = (List<Object>) rawQuestions;
        for (Object item : rawList) {
            if (!(item instanceof Map)) {
                continue;
            }

            Map<String, Object> questionMap = (Map<String, Object>) item;
            Object rawAnswers = questionMap.get("answers");
            Object rawCorrectIndex = questionMap.get("correctAnswerIndex");
            String text = (String) questionMap.get("text");

            if (!(rawAnswers instanceof List) || !(rawCorrectIndex instanceof Number) || text == null) {
                continue;
            }

            List<?> answersList = (List<?>) rawAnswers;
            String[] answers = new String[answersList.size()];
            for (int i = 0; i < answersList.size(); i++) {
                answers[i] = String.valueOf(answersList.get(i));
            }

            questions.add(new QuizQuestion(text, answers, ((Number) rawCorrectIndex).intValue()));
        }

        return questions;
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
        public final int currentQuestionIndex;
        public final int ownerScore;
        public final int guestScore;
        public final Integer ownerAnswerIndex;
        public final Integer guestAnswerIndex;
        public final Long ownerAnswerTimeMs;
        public final Long guestAnswerTimeMs;
        public final int ownerCorrectAnswers;
        public final int ownerWrongAnswers;
        public final int guestCorrectAnswers;
        public final int guestWrongAnswers;
        public final String resultMessage;
        public final boolean gameFinished;
        public final String winner;
        public final List<QuizQuestion> questions;

        public GameState(String sessionId, String ownerId, String guestId, String ownerUsername, String guestUsername,
                         String phase, int currentQuestionIndex, int ownerScore, int guestScore,
                         Integer ownerAnswerIndex, Integer guestAnswerIndex, Long ownerAnswerTimeMs,
                         Long guestAnswerTimeMs, int ownerCorrectAnswers, int ownerWrongAnswers,
                         int guestCorrectAnswers, int guestWrongAnswers, String resultMessage, boolean gameFinished,
                         String winner, List<QuizQuestion> questions) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.guestId = guestId;
            this.ownerUsername = ownerUsername;
            this.guestUsername = guestUsername;
            this.phase = phase;
            this.currentQuestionIndex = currentQuestionIndex;
            this.ownerScore = ownerScore;
            this.guestScore = guestScore;
            this.ownerAnswerIndex = ownerAnswerIndex;
            this.guestAnswerIndex = guestAnswerIndex;
            this.ownerAnswerTimeMs = ownerAnswerTimeMs;
            this.guestAnswerTimeMs = guestAnswerTimeMs;
            this.ownerCorrectAnswers = ownerCorrectAnswers;
            this.ownerWrongAnswers = ownerWrongAnswers;
            this.guestCorrectAnswers = guestCorrectAnswers;
            this.guestWrongAnswers = guestWrongAnswers;
            this.resultMessage = resultMessage;
            this.gameFinished = gameFinished;
            this.winner = winner;
            this.questions = questions;
        }
    }
}
