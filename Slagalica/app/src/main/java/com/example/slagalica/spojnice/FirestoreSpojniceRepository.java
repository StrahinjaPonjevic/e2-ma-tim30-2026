package com.example.slagalica.spojnice;

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

public class FirestoreSpojniceRepository {

    private static final String COLLECTION = "spojnice_sets";
    private final FirebaseFirestore db;

    public interface LoadSetsCallback {
        void onSuccess(List<SpojniceSet> sets);
        void onError(String message);
    }

    public FirestoreSpojniceRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadSets(int count, LoadSetsCallback callback) {
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (!task.isSuccessful()) {
                            callback.onError("Greška pri učitavanju spojnica iz baze");
                            return;
                        }

                        List<SpojniceSet> sets = mapSets(task.getResult());
                        if (sets.size() >= count) {
                            callback.onSuccess(pickRandomSets(sets, count));
                            return;
                        }

                        seedSets(count, callback);
                    }
                });
    }

    private void seedSets(int count, LoadSetsCallback callback) {
        List<SpojniceSet> seedSets = SpojniceSeedData.createSets();
        WriteBatch batch = db.batch();

        for (int i = 0; i < seedSets.size(); i++) {
            SpojniceSet set = seedSets.get(i);
            Map<String, Object> setMap = new HashMap<>();
            setMap.put("leftItems", new ArrayList<>(set.getLeftItems()));
            setMap.put("rightItems", new ArrayList<>(set.getRightItems()));
            setMap.put("correctRightForLeft", new ArrayList<>(set.getCorrectRightForLeft()));
            batch.set(db.collection(COLLECTION).document("set_" + i), setMap);
        }

        batch.commit().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (!task.isSuccessful()) {
                    callback.onError("Greška pri inicijalizaciji spojnica u bazi");
                    return;
                }

                loadSets(count, callback);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<SpojniceSet> mapSets(QuerySnapshot snapshot) {
        List<SpojniceSet> sets = new ArrayList<>();
        if (snapshot == null) {
            return sets;
        }

        snapshot.getDocuments().forEach(document -> {
            List<String> leftItems = (List<String>) document.get("leftItems");
            List<String> rightItems = (List<String>) document.get("rightItems");
            List<Number> rawPairs = (List<Number>) document.get("correctRightForLeft");

            if (leftItems == null || rightItems == null || rawPairs == null
                    || leftItems.size() != 5 || rightItems.size() != 5 || rawPairs.size() != 5) {
                return;
            }

            List<Integer> correctPairs = new ArrayList<>();
            for (Number value : rawPairs) {
                correctPairs.add(value.intValue());
            }

            sets.add(new SpojniceSet(leftItems, rightItems, correctPairs));
        });

        return sets;
    }

    private List<SpojniceSet> pickRandomSets(List<SpojniceSet> allSets, int count) {
        List<SpojniceSet> shuffled = new ArrayList<>(allSets);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
