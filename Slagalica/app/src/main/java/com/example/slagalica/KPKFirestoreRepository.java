package com.example.slagalica;

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

public class KPKFirestoreRepository {

    private static final String COLLECTION = "korak_po_korak_questions";
    private final FirebaseFirestore db;

    public interface LoadSetsCallback {
        void onSuccess(List<KPKQuestionData.QuestionSet> sets);
        void onError(String message);
    }

    public KPKFirestoreRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadQuestionSets(int count, LoadSetsCallback callback) {
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (!task.isSuccessful()) {
                            callback.onError("Greška pri učitavanju pitanja iz baze");
                            return;
                        }

                        List<KPKQuestionData.QuestionSet> sets = mapSets(task.getResult());
                        if (sets.size() >= count) {
                            callback.onSuccess(pickRandomSets(sets, count));
                            return;
                        }

                        seedSets(count, callback);
                    }
                });
    }

    private void seedSets(int count, LoadSetsCallback callback) {
        List<KPKQuestionData.QuestionSet> allSeed = new ArrayList<>();
        Collections.addAll(allSeed, KPKQuestionData.SETS);
        WriteBatch batch = db.batch();

        for (int i = 0; i < allSeed.size(); i++) {
            KPKQuestionData.QuestionSet set = allSeed.get(i);
            Map<String, Object> setMap = new HashMap<>();
            List<String> cluesList = new ArrayList<>();
            Collections.addAll(cluesList, set.clues);
            setMap.put("clues", cluesList);
            setMap.put("answer", set.answer);
            batch.set(db.collection(COLLECTION).document("set_" + i), setMap);
        }

        batch.commit().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (!task.isSuccessful()) {
                    callback.onError("Greška pri inicijalizaciji podataka u bazi");
                    return;
                }

                loadQuestionSets(count, callback);
            }
        });
    }

    private List<KPKQuestionData.QuestionSet> mapSets(QuerySnapshot snapshot) {
        List<KPKQuestionData.QuestionSet> sets = new ArrayList<>();
        if (snapshot == null) {
            return sets;
        }

        snapshot.getDocuments().forEach(document -> {
            List<String> cluesList = (List<String>) document.get("clues");
            String answer = document.getString("answer");

            if (cluesList == null || answer == null || cluesList.size() != 7) {
                return;
            }

            sets.add(new KPKQuestionData.QuestionSet(
                    cluesList.toArray(new String[0]),
                    answer
            ));
        });

        return sets;
    }

    private List<KPKQuestionData.QuestionSet> pickRandomSets(
            List<KPKQuestionData.QuestionSet> allSets, int count) {
        List<KPKQuestionData.QuestionSet> shuffled = new ArrayList<>(allSets);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
