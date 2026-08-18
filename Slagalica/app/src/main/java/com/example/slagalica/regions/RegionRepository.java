package com.example.slagalica.regions;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionRepository {

    private static final String USERS = "users";
    private static final String REGION_STATS = "region_stats";
    private static final String REGION_CYCLES = "region_cycles";
    private static final int BATCH_SIZE = 400;

    private final FirebaseFirestore db;

    public interface DashboardCallback {
        void onSuccess(DashboardData data);
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public static final class DashboardData {
        public final List<RegionSummary> summaries;
        public final List<RegionLeaderboardRow> leaderboard;
        public final List<RegionPoint> playerPoints;
        public final RegionDefinition currentPlayerRegion;

        DashboardData(List<RegionSummary> summaries,
                      List<RegionLeaderboardRow> leaderboard,
                      List<RegionPoint> playerPoints,
                      RegionDefinition currentPlayerRegion) {
            this.summaries = summaries;
            this.leaderboard = leaderboard;
            this.playerPoints = playerPoints;
            this.currentPlayerRegion = currentPlayerRegion;
        }

        public RegionSummary summaryFor(String regionId) {
            for (RegionSummary summary : summaries) {
                if (summary.region.id.equals(regionId)) {
                    return summary;
                }
            }
            return null;
        }
    }

    public RegionRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadDashboard(String currentUid, DashboardCallback callback) {
        db.collection(REGION_STATS).get()
                .addOnSuccessListener(statsSnapshot -> db.collection(USERS).get()
                        .addOnSuccessListener(usersSnapshot -> callback.onSuccess(buildDashboard(
                                currentUid, usersSnapshot, statsSnapshot)))
                        .addOnFailureListener(e -> callback.onError(messageOf(
                                e, "Igrači nisu dostupni"))))
                .addOnFailureListener(e -> callback.onError(messageOf(
                        e, "Statistika regiona nije dostupna")));
    }

    public void ensurePreviousCycleFinalized(OperationCallback callback) {
        String previousMonth = RegionDefinition.previousMonthKey();
        DocumentReference cycleRef = db.collection(REGION_CYCLES).document(previousMonth);
        cycleRef.get()
                .addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        applyAvatarFrames(parseRankings(existing), callback);
                    } else {
                        calculateAndFinalizePreviousCycle(previousMonth, cycleRef, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(messageOf(
                        e, "Prethodni regionalni ciklus nije dostupan")));
    }

    private DashboardData buildDashboard(String currentUid, QuerySnapshot usersSnapshot,
                                         QuerySnapshot statsSnapshot) {
        Map<String, MutableSummary> mutable = new HashMap<>();
        for (RegionDefinition region : RegionDefinition.ALL) {
            mutable.put(region.id, new MutableSummary(region));
        }

        for (DocumentSnapshot statDoc : statsSnapshot.getDocuments()) {
            RegionDefinition region = RegionDefinition.find(statDoc.getString("regionId"));
            if (region == null) {
                region = RegionDefinition.findById(statDoc.getId());
            }
            if (region != null) {
                MutableSummary summary = mutable.get(region.id);
                summary.firstPlaces = intValue(statDoc.get("firstPlaces"));
                summary.secondPlaces = intValue(statDoc.get("secondPlaces"));
                summary.thirdPlaces = intValue(statDoc.get("thirdPlaces"));
            }
        }

        String currentMonth = RegionDefinition.currentMonthKey();
        RegionDefinition currentPlayerRegion = null;
        List<RegionPoint> points = new ArrayList<>();
        List<MapPointBackfill> backfills = new ArrayList<>();

        for (DocumentSnapshot userDoc : usersSnapshot.getDocuments()) {
            RegionDefinition region = RegionDefinition.find(userDoc.getString("region"));
            if (region == null) {
                continue;
            }
            MutableSummary summary = mutable.get(region.id);
            summary.registeredPlayers++;
            if (Boolean.TRUE.equals(userDoc.getBoolean("isLoggedIn"))) {
                summary.activePlayers++;
            }
            if (currentMonth.equals(userDoc.getString("monthlyRankMonth"))) {
                summary.monthlyStars += Math.max(0, intValue(userDoc.get("monthlyStars")));
            }

            boolean isCurrentPlayer = userDoc.getId().equals(currentUid);
            if (isCurrentPlayer) {
                currentPlayerRegion = region;
            }

            float[] coordinates = storedPoint(userDoc, region);
            if (coordinates == null) {
                coordinates = region.deterministicPoint(userDoc.getId() + ":" + region.id);
                backfills.add(new MapPointBackfill(userDoc.getReference(), coordinates[0], coordinates[1]));
            }
            points.add(new RegionPoint(userDoc.getId(), region.id,
                    coordinates[0], coordinates[1], isCurrentPlayer));
        }

        backfillMapPoints(backfills);

        List<RegionSummary> summaries = new ArrayList<>();
        for (RegionDefinition region : RegionDefinition.ALL) {
            summaries.add(mutable.get(region.id).freeze());
        }

        List<RegionSummary> sorted = new ArrayList<>(summaries);
        sorted.sort((left, right) -> {
            int starComparison = Integer.compare(right.monthlyStars, left.monthlyStars);
            return starComparison != 0
                    ? starComparison
                    : left.region.displayName.compareToIgnoreCase(right.region.displayName);
        });

        List<RegionLeaderboardRow> leaderboard = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RegionSummary summary = sorted.get(i);
            leaderboard.add(new RegionLeaderboardRow(
                    i + 1,
                    summary.region,
                    summary.monthlyStars,
                    currentPlayerRegion != null && currentPlayerRegion.id.equals(summary.region.id)
            ));
        }
        return new DashboardData(summaries, leaderboard, points, currentPlayerRegion);
    }

    private void calculateAndFinalizePreviousCycle(String previousMonth, DocumentReference cycleRef,
                                                   OperationCallback callback) {
        db.collection(USERS)
                .whereEqualTo("monthlyRankMonth", previousMonth)
                .get()
                .addOnSuccessListener(usersSnapshot -> {
                    Map<String, Integer> starsByRegion = new HashMap<>();
                    for (DocumentSnapshot userDoc : usersSnapshot.getDocuments()) {
                        RegionDefinition region = RegionDefinition.find(userDoc.getString("region"));
                        int monthlyStars = Math.max(0, intValue(userDoc.get("monthlyStars")));
                        if (region != null && monthlyStars > 0) {
                            starsByRegion.put(region.id,
                                    starsByRegion.getOrDefault(region.id, 0) + monthlyStars);
                        }
                    }

                    List<CycleRanking> rankings = createTopRankings(starsByRegion);
                    List<Map<String, Object>> rankingData = rankingMaps(rankings);
                    db.runTransaction(transaction -> {
                                DocumentSnapshot cycle = transaction.get(cycleRef);
                                if (cycle.exists()) {
                                    return false;
                                }

                                Map<String, Object> cycleData = new HashMap<>();
                                cycleData.put("monthKey", previousMonth);
                                cycleData.put("finalizedAt", FieldValue.serverTimestamp());
                                cycleData.put("rankings", rankingData);
                                transaction.set(cycleRef, cycleData);

                                for (CycleRanking ranking : rankings) {
                                    DocumentReference statsRef = db.collection(REGION_STATS)
                                            .document(ranking.region.id);
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("regionId", ranking.region.id);
                                    updates.put("displayName", ranking.region.displayName);
                                    updates.put(placementField(ranking.rank), FieldValue.increment(1));
                                    updates.put("updatedAt", FieldValue.serverTimestamp());
                                    transaction.set(statsRef, updates, SetOptions.merge());
                                }
                                return true;
                            })
                            .addOnSuccessListener(created -> cycleRef.get()
                                    .addOnSuccessListener(finalizedCycle -> applyAvatarFrames(
                                            parseRankings(finalizedCycle), callback))
                                    .addOnFailureListener(e -> callback.onError(messageOf(
                                            e, "Regionalni ciklus je sačuvan, ali rang nije učitan"))))
                            .addOnFailureListener(e -> callback.onError(messageOf(
                                    e, "Finalizacija regionalnog ciklusa nije uspela")));
                })
                .addOnFailureListener(e -> callback.onError(messageOf(
                        e, "Rezultati prethodnog meseca nisu dostupni")));
    }

    private List<CycleRanking> createTopRankings(Map<String, Integer> starsByRegion) {
        List<CycleRanking> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : starsByRegion.entrySet()) {
            RegionDefinition region = RegionDefinition.findById(entry.getKey());
            if (region != null && entry.getValue() > 0) {
                candidates.add(new CycleRanking(0, region, entry.getValue()));
            }
        }
        candidates.sort((left, right) -> {
            int starsComparison = Integer.compare(right.monthlyStars, left.monthlyStars);
            return starsComparison != 0
                    ? starsComparison
                    : left.region.displayName.compareToIgnoreCase(right.region.displayName);
        });

        List<CycleRanking> top = new ArrayList<>();
        int count = Math.min(3, candidates.size());
        for (int i = 0; i < count; i++) {
            CycleRanking candidate = candidates.get(i);
            top.add(new CycleRanking(i + 1, candidate.region, candidate.monthlyStars));
        }
        return top;
    }

    private List<Map<String, Object>> rankingMaps(List<CycleRanking> rankings) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (CycleRanking ranking : rankings) {
            Map<String, Object> row = new HashMap<>();
            row.put("rank", ranking.rank);
            row.put("regionId", ranking.region.id);
            row.put("displayName", ranking.region.displayName);
            row.put("monthlyStars", ranking.monthlyStars);
            data.add(row);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<CycleRanking> parseRankings(DocumentSnapshot cycle) {
        Object raw = cycle.get("rankings");
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<CycleRanking> rankings = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) item;
            RegionDefinition region = RegionDefinition.find(stringValue(row.get("regionId")));
            int rank = intValue(row.get("rank"));
            if (region != null && rank >= 1 && rank <= 3) {
                rankings.add(new CycleRanking(rank, region, intValue(row.get("monthlyStars"))));
            }
        }
        rankings.sort(Comparator.comparingInt(ranking -> ranking.rank));
        return rankings;
    }

    private void applyAvatarFrames(List<CycleRanking> rankings, OperationCallback callback) {
        if (rankings.isEmpty()) {
            callback.onSuccess();
            return;
        }
        collectFrameUpdates(rankings, 0, new ArrayList<>(), callback);
    }

    private void collectFrameUpdates(List<CycleRanking> rankings, int index,
                                     List<FrameUpdate> updates, OperationCallback callback) {
        if (index >= rankings.size()) {
            commitFrameBatches(updates, 0, callback);
            return;
        }
        CycleRanking ranking = rankings.get(index);
        db.collection(USERS)
                .whereEqualTo("region", ranking.region.displayName)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot user : snapshot.getDocuments()) {
                        updates.add(new FrameUpdate(user.getReference(), ranking.rank));
                    }
                    collectFrameUpdates(rankings, index + 1, updates, callback);
                })
                .addOnFailureListener(e -> callback.onError(messageOf(
                        e, "Okviri avatara nisu dodeljeni")));
    }

    private void commitFrameBatches(List<FrameUpdate> updates, int start,
                                    OperationCallback callback) {
        if (start >= updates.size()) {
            callback.onSuccess();
            return;
        }
        int end = Math.min(start + BATCH_SIZE, updates.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            FrameUpdate update = updates.get(i);
            Map<String, Object> values = new HashMap<>();
            values.put("avatarFrameRank", update.rank);
            values.put("avatarFrameCycleMonth", RegionDefinition.previousMonthKey());
            values.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(update.reference, values);
        }
        batch.commit()
                .addOnSuccessListener(unused -> commitFrameBatches(updates, end, callback))
                .addOnFailureListener(e -> callback.onError(messageOf(
                        e, "Okviri avatara nisu sačuvani")));
    }

    private void backfillMapPoints(List<MapPointBackfill> backfills) {
        for (int start = 0; start < backfills.size(); start += BATCH_SIZE) {
            WriteBatch batch = db.batch();
            int end = Math.min(start + BATCH_SIZE, backfills.size());
            for (int i = start; i < end; i++) {
                MapPointBackfill point = backfills.get(i);
                Map<String, Object> values = new HashMap<>();
                values.put("mapPointX", point.x);
                values.put("mapPointY", point.y);
                values.put("updatedAt", FieldValue.serverTimestamp());
                batch.update(point.reference, values);
            }
            batch.commit();
        }
    }

    private float[] storedPoint(DocumentSnapshot userDoc, RegionDefinition region) {
        Object rawX = userDoc.get("mapPointX");
        Object rawY = userDoc.get("mapPointY");
        if (!(rawX instanceof Number) || !(rawY instanceof Number)) {
            return null;
        }
        float x = ((Number) rawX).floatValue();
        float y = ((Number) rawY).floatValue();
        return region.contains(x, y) ? new float[]{x, y} : null;
    }

    private String placementField(int rank) {
        if (rank == 1) {
            return "firstPlaces";
        }
        if (rank == 2) {
            return "secondPlaces";
        }
        return "thirdPlaces";
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private String messageOf(Exception error, String fallback) {
        return error != null && error.getMessage() != null ? error.getMessage() : fallback;
    }

    private static final class MutableSummary {
        final RegionDefinition region;
        int monthlyStars;
        int firstPlaces;
        int secondPlaces;
        int thirdPlaces;
        int activePlayers;
        int registeredPlayers;

        MutableSummary(RegionDefinition region) {
            this.region = region;
        }

        RegionSummary freeze() {
            return new RegionSummary(region, monthlyStars, firstPlaces, secondPlaces,
                    thirdPlaces, activePlayers, registeredPlayers);
        }
    }

    private static final class MapPointBackfill {
        final DocumentReference reference;
        final float x;
        final float y;

        MapPointBackfill(DocumentReference reference, float x, float y) {
            this.reference = reference;
            this.x = x;
            this.y = y;
        }
    }

    private static final class CycleRanking {
        final int rank;
        final RegionDefinition region;
        final int monthlyStars;

        CycleRanking(int rank, RegionDefinition region, int monthlyStars) {
            this.rank = rank;
            this.region = region;
            this.monthlyStars = monthlyStars;
        }
    }

    private static final class FrameUpdate {
        final DocumentReference reference;
        final int rank;

        FrameUpdate(DocumentReference reference, int rank) {
            this.reference = reference;
            this.rank = rank;
        }
    }
}
