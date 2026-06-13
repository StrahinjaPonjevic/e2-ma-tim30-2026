package com.example.slagalica.koznazna;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreQuizQuestionRepository {

    private static final String COLLECTION = "ko_zna_zna_questions";
    private final FirebaseFirestore db;

    public interface LoadQuestionsCallback {
        void onSuccess(List<QuizQuestion> questions);
        void onError(String message);
    }

    public FirestoreQuizQuestionRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadQuestions(int count, LoadQuestionsCallback callback) {
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (!task.isSuccessful()) {
                            callback.onError("Greška pri učitavanju pitanja iz baze");
                            return;
                        }

                        List<QuizQuestion> questions = mapQuestions(task.getResult());
                        if (questions.size() >= count) {
                            callback.onSuccess(pickRandomQuestions(questions, count));
                            return;
                        }

                        seedQuestions(count, callback);
                    }
                });
    }

    private void seedQuestions(int count, LoadQuestionsCallback callback) {
        List<QuizQuestion> seedQuestions = KoZnaZnaSeedData.createQuestions();
        WriteBatch batch = db.batch();

        for (int i = 0; i < seedQuestions.size(); i++) {
            QuizQuestion question = seedQuestions.get(i);
            Map<String, Object> questionMap = new HashMap<>();
            questionMap.put("text", question.getText());
            questionMap.put("answers", toAnswerList(question.getAnswers()));
            questionMap.put("correctAnswerIndex", question.getCorrectAnswerIndex());
            batch.set(db.collection(COLLECTION).document("question_" + i), questionMap);
        }

        batch.commit().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (!task.isSuccessful()) {
                    callback.onError("Greška pri inicijalizaciji pitanja u bazi");
                    return;
                }

                loadQuestions(count, callback);
            }
        });
    }

    private List<QuizQuestion> mapQuestions(QuerySnapshot snapshot) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (snapshot == null) {
            return questions;
        }

        snapshot.getDocuments().forEach(document -> {
            String text = document.getString("text");
            List<String> answersList = (List<String>) document.get("answers");
            Long correctAnswerIndex = document.getLong("correctAnswerIndex");

            if (text == null || answersList == null || correctAnswerIndex == null || answersList.size() != 4) {
                return;
            }

            questions.add(new QuizQuestion(
                    text,
                    answersList.toArray(new String[0]),
                    correctAnswerIndex.intValue()
            ));
        });

        return questions;
    }

    private List<QuizQuestion> pickRandomQuestions(List<QuizQuestion> allQuestions, int count) {
        List<QuizQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }

    private List<String> toAnswerList(String[] answers) {
        List<String> answerList = new ArrayList<>();
        Collections.addAll(answerList, answers);
        return answerList;
    }
}
