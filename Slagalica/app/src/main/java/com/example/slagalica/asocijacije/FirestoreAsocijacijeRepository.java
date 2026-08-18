package com.example.slagalica.asocijacije;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreAsocijacijeRepository {

    private static final String COLLECTION = "asocijacije_sets";

    private final FirebaseFirestore db;

    public interface LoadSetsCallback {
        void onSuccess(List<AsocijacijeSet> sets);
        void onError(String message);
    }

    public FirestoreAsocijacijeRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadSets(int count, LoadSetsCallback callback) {
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (!task.isSuccessful()) {
                            callback.onError("Greska pri ucitavanju asocijacija iz baze");
                            return;
                        }

                        List<AsocijacijeSet> sets = mapSets(task.getResult());
                        if (sets.size() < count) {
                            callback.onError("Nema dovoljno asocijacija u bazi. Dodajte bar " + count + " seta u kolekciju asocijacije_sets.");
                            return;
                        }
                        callback.onSuccess(pickRandomSets(sets, count));
                    }
                });
    }

    public static Map<String, Object> toSetMap(AsocijacijeSet set) {
        Map<String, Object> map = new HashMap<>();
        map.put("columnA", new ArrayList<>(set.getColumnItems('A')));
        map.put("columnB", new ArrayList<>(set.getColumnItems('B')));
        map.put("columnC", new ArrayList<>(set.getColumnItems('C')));
        map.put("columnD", new ArrayList<>(set.getColumnItems('D')));
        map.put("solutionA", set.getColumnSolution('A'));
        map.put("solutionB", set.getColumnSolution('B'));
        map.put("solutionC", set.getColumnSolution('C'));
        map.put("solutionD", set.getColumnSolution('D'));
        map.put("finalSolution", set.getFinalSolution());
        return map;
    }

    @SuppressWarnings("unchecked")
    public static AsocijacijeSet fromSetMap(Object rawSet) {
        if (!(rawSet instanceof Map)) {
            return null;
        }

        Map<String, Object> map = (Map<String, Object>) rawSet;
        List<String> columnA = (List<String>) map.get("columnA");
        List<String> columnB = (List<String>) map.get("columnB");
        List<String> columnC = (List<String>) map.get("columnC");
        List<String> columnD = (List<String>) map.get("columnD");
        String solutionA = (String) map.get("solutionA");
        String solutionB = (String) map.get("solutionB");
        String solutionC = (String) map.get("solutionC");
        String solutionD = (String) map.get("solutionD");
        String finalSolution = (String) map.get("finalSolution");

        if (columnA == null || columnB == null || columnC == null || columnD == null) {
            return null;
        }

        AsocijacijeSet set = new AsocijacijeSet(columnA, solutionA, columnB, solutionB,
                columnC, solutionC, columnD, solutionD, finalSolution);
        return set.isValid() ? set : null;
    }

    private List<AsocijacijeSet> mapSets(QuerySnapshot snapshot) {
        List<AsocijacijeSet> sets = new ArrayList<>();
        if (snapshot == null) {
            return sets;
        }

        for (QueryDocumentSnapshot doc : snapshot) {
            AsocijacijeSet set = fromSetMap(doc.getData());
            if (set != null) {
                sets.add(set);
            }
        }
        return sets;
    }

    private List<AsocijacijeSet> pickRandomSets(List<AsocijacijeSet> sets, int count) {
        List<AsocijacijeSet> shuffled = new ArrayList<>(sets);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, Math.min(count, shuffled.size())));
    }
}
