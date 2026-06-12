package com.example.slagalica.koznazna;

import java.util.List;

public class KoZnaZnaGame {

    public static final int PLAYER_ONE = 0;
    public static final int PLAYER_TWO = 1;

    private final List<QuizQuestion> questions;
    private final int[] playerScores = new int[2];
    private final PlayerAnswer[] currentAnswers = new PlayerAnswer[2];

    private int currentQuestionIndex = 0;

    public KoZnaZnaGame(List<QuizQuestion> questions) {
        this.questions = questions;
    }

    public QuizQuestion getCurrentQuestion() {
        return questions.get(currentQuestionIndex);
    }

    public int getCurrentQuestionNumber() {
        return currentQuestionIndex + 1;
    }

    public int getQuestionCount() {
        return questions.size();
    }

    public int getPlayerScore(int playerIndex) {
        return playerScores[playerIndex];
    }

    public boolean hasPlayerAnswered(int playerIndex) {
        return currentAnswers[playerIndex] != null;
    }

    public void recordAnswer(int playerIndex, int answerIndex, long elapsedMillis) {
        if (currentAnswers[playerIndex] != null) {
            return;
        }

        boolean correct = getCurrentQuestion().getCorrectAnswerIndex() == answerIndex;
        currentAnswers[playerIndex] = new PlayerAnswer(answerIndex, correct, elapsedMillis);
    }

    public QuestionOutcome finishCurrentQuestion() {
        PlayerAnswer first = currentAnswers[PLAYER_ONE];
        PlayerAnswer second = currentAnswers[PLAYER_TWO];
        int[] deltas = new int[]{0, 0};

        if (first != null && first.correct && second != null && second.correct) {
            int fasterPlayer = first.elapsedMillis <= second.elapsedMillis ? PLAYER_ONE : PLAYER_TWO;
            deltas[fasterPlayer] += 10;
        } else {
            applySingleAnswerOutcome(PLAYER_ONE, first, deltas);
            applySingleAnswerOutcome(PLAYER_TWO, second, deltas);
        }

        playerScores[PLAYER_ONE] += deltas[PLAYER_ONE];
        playerScores[PLAYER_TWO] += deltas[PLAYER_TWO];

        String summary = buildSummary(first, second, deltas);
        clearCurrentAnswers();

        return new QuestionOutcome(deltas, summary);
    }

    public boolean hasNextQuestion() {
        return currentQuestionIndex < questions.size() - 1;
    }

    public void moveToNextQuestion() {
        if (hasNextQuestion()) {
            currentQuestionIndex++;
        }
    }

    private void applySingleAnswerOutcome(int playerIndex, PlayerAnswer answer, int[] deltas) {
        if (answer == null) {
            return;
        }

        deltas[playerIndex] += answer.correct ? 10 : -5;
    }

    private String buildSummary(PlayerAnswer first, PlayerAnswer second, int[] deltas) {
        if (first == null && second == null) {
            return "Niko nije odgovorio. Bodovi ostaju nepromenjeni.";
        }

        if (first != null && first.correct && second != null && second.correct) {
            int fasterPlayer = first.elapsedMillis <= second.elapsedMillis ? 1 : 2;
            return "Oba igrača su odgovorila tačno. Bodove dobija brži igrač: Igrač " + fasterPlayer + ".";
        }

        return "Igrač 1: " + formatDelta(deltas[PLAYER_ONE]) + " | Igrač 2: " + formatDelta(deltas[PLAYER_TWO]);
    }

    private String formatDelta(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        return String.valueOf(delta);
    }

    private void clearCurrentAnswers() {
        currentAnswers[PLAYER_ONE] = null;
        currentAnswers[PLAYER_TWO] = null;
    }

    private static class PlayerAnswer {
        private final int answerIndex;
        private final boolean correct;
        private final long elapsedMillis;

        private PlayerAnswer(int answerIndex, boolean correct, long elapsedMillis) {
            this.answerIndex = answerIndex;
            this.correct = correct;
            this.elapsedMillis = elapsedMillis;
        }
    }

    public static class QuestionOutcome {
        private final int[] deltas;
        private final String summary;

        public QuestionOutcome(int[] deltas, String summary) {
            this.deltas = deltas;
            this.summary = summary;
        }

        public int getDeltaForPlayer(int playerIndex) {
            return deltas[playerIndex];
        }

        public String getSummary() {
            return summary;
        }
    }
}
