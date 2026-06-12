package com.example.slagalica.koznazna;

public class QuizQuestion {

    private final String text;
    private final String[] answers;
    private final int correctAnswerIndex;

    public QuizQuestion(String text, String[] answers, int correctAnswerIndex) {
        this.text = text;
        this.answers = answers;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getText() {
        return text;
    }

    public String[] getAnswers() {
        return answers;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }
}
