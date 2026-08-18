package com.example.slagalica.ranking;

import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationStore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RankingRepository {

    private static final String USERS = "users";
    private static final String CYCLES = "ranking_cycles";
    private static final int[] WEEKLY_REWARDS = {5, 3, 2, 1, 1, 1, 1, 1, 1, 1};
    private static final int[] MONTHLY_REWARDS = {10, 6, 4, 2, 2, 2, 2, 2, 2, 2};

    private final FirebaseFirestore db;

    public interface RowsCallback {
        void onRows(List<RankingRow> rows);
        void onError(String message);
    }

    public interface PendingRewardCallback {
        void onReward(String rewardText);
    }

    public RankingRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadCycle(boolean weekly, RowsCallback callback) {
        String keyField = weekly ? "weeklyRankKey" : "monthlyRankMonth";
        String starsField = weekly ? "weeklyStars" : "monthlyStars";
        String currentKey = weekly ? CycleUtils.currentWeekKey() : CycleUtils.currentMonthKey();

        db.collection(USERS)
                .whereEqualTo(keyField, currentKey)
                .limit(500)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RankingRow> rows = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        rows.add(new RankingRow(
                                doc.getId(),
                                valueOrDefault(doc.getString("username"), "Igrac"),
                                intValue(doc.get(starsField)),
                                intValue(doc.get("stars"))
                        ));
                    }
                    rows.sort((left, right) -> {
                        int starCompare = Integer.compare(right.cycleStars, left.cycleStars);
                        if (starCompare != 0) {
                            return starCompare;
                        }
                        return left.username.compareToIgnoreCase(right.username);
                    });
                    for (int i = 0; i < rows.size(); i++) {
                        rows.get(i).rank = i + 1;
                    }
                    callback.onRows(rows);
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Rang lista nije dostupna"));
    }

    public void finalizePreviousCyclesIfNeeded() {
        finalizeCycle(true);
        finalizeCycle(false);
    }

    private void finalizeCycle(boolean weekly) {
        String previousKey = weekly ? CycleUtils.previousWeekKey() : CycleUtils.previousMonthKey();
        String cycleId = (weekly ? "weekly_" : "monthly_") + previousKey;
        DocumentReference cycleRef = db.collection(CYCLES).document(cycleId);

        cycleRef.get().addOnSuccessListener(cycleSnap -> {
            if (cycleSnap.exists()) {
                return;
            }
            String keyField = weekly ? "weeklyRankKey" : "monthlyRankMonth";
            String starsField = weekly ? "weeklyStars" : "monthlyStars";

            db.collection(USERS)
                    .whereEqualTo(keyField, previousKey)
                    .limit(500)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<RankingRow> rows = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            rows.add(new RankingRow(
                                    doc.getId(),
                                    valueOrDefault(doc.getString("username"), "Igrac"),
                                    intValue(doc.get(starsField)),
                                    intValue(doc.get("stars"))
                            ));
                        }
                        rows.sort((left, right) -> {
                            int starCompare = Integer.compare(right.cycleStars, left.cycleStars);
                            if (starCompare != 0) {
                                return starCompare;
                            }
                            return left.username.compareToIgnoreCase(right.username);
                        });
                        List<RankingRow> winners = new ArrayList<>(rows.subList(0, Math.min(10, rows.size())));
                        distributeRewards(weekly, previousKey, cycleRef, winners);
                    });
        });
    }

    private void distributeRewards(boolean weekly, String cycleKey, DocumentReference cycleRef,
                                   List<RankingRow> winners) {
        int[] rewards = weekly ? WEEKLY_REWARDS : MONTHLY_REWARDS;
        String cycleLabel = weekly ? "nedeljnoj" : "mesecnoj";

        db.runTransaction(transaction -> {
                    DocumentSnapshot existing = transaction.get(cycleRef);
                    if (existing.exists()) {
                        return null;
                    }

                    List<DocumentSnapshot> winnerSnaps = new ArrayList<>();
                    for (RankingRow winner : winners) {
                        winnerSnaps.add(transaction.get(db.collection(USERS).document(winner.uid)));
                    }

                    List<Map<String, Object>> standings = new ArrayList<>();
                    for (int i = 0; i < winners.size(); i++) {
                        RankingRow winner = winners.get(i);
                        int reward = rewards[i];
                        DocumentSnapshot userSnap = winnerSnaps.get(i);

                        Map<String, Object> standing = new HashMap<>();
                        standing.put("uid", winner.uid);
                        standing.put("username", winner.username);
                        standing.put("place", i + 1);
                        standing.put("cycleStars", winner.cycleStars);
                        standing.put("tokens", reward);
                        standings.add(standing);

                        if (!userSnap.exists()) {
                            continue;
                        }
                        String rewardLine = String.format(Locale.getDefault(),
                                "%d. mesto na %s rang listi (%s): +%d tokena!",
                                i + 1, cycleLabel, cycleKey, reward);
                        String existingText = userSnap.getString("pendingRewardText");
                        String fullText = existingText != null && !existingText.trim().isEmpty()
                                ? existingText + "\n" + rewardLine
                                : rewardLine;

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("tokens", FieldValue.increment(reward));
                        updates.put("pendingRewardId", cycleRef.getId() + "_" + winner.uid);
                        updates.put("pendingRewardText", fullText);
                        updates.put("updatedAt", FieldValue.serverTimestamp());
                        transaction.update(db.collection(USERS).document(winner.uid), updates);
                    }

                    Map<String, Object> cycleData = new HashMap<>();
                    cycleData.put("type", weekly ? "weekly" : "monthly");
                    cycleData.put("cycleKey", cycleKey);
                    cycleData.put("standings", standings);
                    cycleData.put("distributedAt", FieldValue.serverTimestamp());
                    transaction.set(cycleRef, cycleData);
                    return winners;
                })
                .addOnSuccessListener(result -> {
                    if (result == null) {
                        return;
                    }
                    for (int i = 0; i < winners.size(); i++) {
                        RankingRow winner = winners.get(i);
                        NotificationStore.save(winner.uid,
                                NotificationChannelManager.CHANNEL_REWARDS,
                                "Nagrada sa rang liste",
                                String.format(Locale.getDefault(),
                                        "Osvojili ste %d. mesto na %s rang listi i %d tokena!",
                                        i + 1, cycleLabel, rewards[i]),
                                "reward",
                                cycleRef.getId());
                    }
                });
    }

    public void checkPendingReward(String uid, PendingRewardCallback callback) {
        db.collection(USERS).document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String rewardId = snapshot.getString("pendingRewardId");
                    if (rewardId == null || rewardId.trim().isEmpty()) {
                        return;
                    }
                    String text = snapshot.getString("pendingRewardText");
                    callback.onReward(text != null && !text.trim().isEmpty()
                            ? text
                            : "Osvojili ste nagradu na rang listi!");
                });
    }

    public void clearPendingReward(String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("pendingRewardId", null);
        updates.put("pendingRewardText", null);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(USERS).document(uid).update(updates);
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    public static final class RankingRow {
        public final String uid;
        public final String username;
        public final int cycleStars;
        public final int totalStars;
        public int rank;

        public RankingRow(String uid, String username, int cycleStars, int totalStars) {
            this.uid = uid;
            this.username = username;
            this.cycleStars = cycleStars;
            this.totalStars = totalStars;
        }
    }
}
