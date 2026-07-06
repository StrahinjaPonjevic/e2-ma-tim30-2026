package com.example.slagalica.profile;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class ProfileStatsUpdater {

    private static final String USERS_COLLECTION = "users";

    private final FirebaseFirestore db;

    public ProfileStatsUpdater() {
        db = FirebaseFirestore.getInstance();
    }

    public void recordKoZnaZna(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                               int ownerCorrect, int ownerWrong, int guestCorrect, int guestWrong) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId), buildMatchUpdate(
                ownerScore, winner, "owner", "stats.koZnaZna.gamesPlayed", "stats.koZnaZna.totalScore",
                "stats.koZnaZna.correctAnswers", ownerCorrect, "stats.koZnaZna.wrongAnswers", ownerWrong,
                null, 0, null, 0
        ));
        batch.update(db.collection(USERS_COLLECTION).document(guestId), buildMatchUpdate(
                guestScore, winner, "guest", "stats.koZnaZna.gamesPlayed", "stats.koZnaZna.totalScore",
                "stats.koZnaZna.correctAnswers", guestCorrect, "stats.koZnaZna.wrongAnswers", guestWrong,
                null, 0, null, 0
        ));
        batch.commit();
    }

    public void recordSpojnice(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                               int ownerSuccessfulLinks, int ownerAttempts, int guestSuccessfulLinks, int guestAttempts) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId), buildMatchUpdate(
                ownerScore, winner, "owner", "stats.spojnice.gamesPlayed", "stats.spojnice.totalScore",
                "stats.spojnice.successfulLinks", ownerSuccessfulLinks, "stats.spojnice.attemptedLinks", ownerAttempts,
                null, 0, null, 0
        ));
        batch.update(db.collection(USERS_COLLECTION).document(guestId), buildMatchUpdate(
                guestScore, winner, "guest", "stats.spojnice.gamesPlayed", "stats.spojnice.totalScore",
                "stats.spojnice.successfulLinks", guestSuccessfulLinks, "stats.spojnice.attemptedLinks", guestAttempts,
                null, 0, null, 0
        ));
        batch.commit();
    }

    public void recordMojBroj(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                              int ownerExactHits, int guestExactHits) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId), buildMatchUpdate(
                ownerScore, winner, "owner", "stats.mojBroj.gamesPlayed", "stats.mojBroj.totalScore",
                "stats.mojBroj.exactHits", ownerExactHits, "stats.mojBroj.roundsPlayed", 2,
                null, 0, null, 0
        ));
        batch.update(db.collection(USERS_COLLECTION).document(guestId), buildMatchUpdate(
                guestScore, winner, "guest", "stats.mojBroj.gamesPlayed", "stats.mojBroj.totalScore",
                "stats.mojBroj.exactHits", guestExactHits, "stats.mojBroj.roundsPlayed", 2,
                null, 0, null, 0
        ));
        batch.commit();
    }

    public void recordKorakPoKorak(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                                   int[] ownerStepHits, int[] guestStepHits) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId), buildKorakUpdate(ownerScore, winner, "owner", ownerStepHits));
        batch.update(db.collection(USERS_COLLECTION).document(guestId), buildKorakUpdate(guestScore, winner, "guest", guestStepHits));
        batch.commit();
    }

    public void recordAsocijacije(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                                  int ownerSolvedFinals, int guestSolvedFinals, int roundsPerPlayer) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId), buildMatchUpdate(
                ownerScore, winner, "owner", "stats.asocijacije.gamesPlayed", "stats.asocijacije.totalScore",
                "stats.asocijacije.solvedFinals", ownerSolvedFinals, "stats.asocijacije.roundsPlayed", roundsPerPlayer,
                null, 0, null, 0
        ));
        batch.update(db.collection(USERS_COLLECTION).document(guestId), buildMatchUpdate(
                guestScore, winner, "guest", "stats.asocijacije.gamesPlayed", "stats.asocijacije.totalScore",
                "stats.asocijacije.solvedFinals", guestSolvedFinals, "stats.asocijacije.roundsPlayed", roundsPerPlayer,
                null, 0, null, 0
        ));
        batch.commit();
    }

    public void recordSkocko(String ownerId, String guestId, int ownerScore, int guestScore, String winner,
                             int ownerHitAttempt, int guestHitAttempt) {
        WriteBatch batch = db.batch();
        batch.update(db.collection(USERS_COLLECTION).document(ownerId),
                buildSkockoUpdate(ownerScore, winner, "owner", ownerHitAttempt));
        batch.update(db.collection(USERS_COLLECTION).document(guestId),
                buildSkockoUpdate(guestScore, winner, "guest", guestHitAttempt));
        batch.commit();
    }

    private Map<String, Object> buildSkockoUpdate(int score, String winner, String side, int hitAttempt) {
        Map<String, Object> updates = buildBaseMatchUpdate(score, winner, side);
        updates.put("stats.skocko.gamesPlayed", FieldValue.increment(1));
        updates.put("stats.skocko.totalScore", FieldValue.increment(score));
        updates.put("stats.skocko.roundsPlayed", FieldValue.increment(1));
        if (hitAttempt >= 1 && hitAttempt <= 6) {
            updates.put("stats.skocko.attempt" + hitAttempt + "Hits", FieldValue.increment(1));
        } else {
            updates.put("stats.skocko.misses", FieldValue.increment(1));
        }
        return updates;
    }

    private Map<String, Object> buildKorakUpdate(int score, String winner, String side, int[] stepHits) {
        Map<String, Object> updates = buildBaseMatchUpdate(score, winner, side);
        updates.put("stats.korakPoKorak.gamesPlayed", FieldValue.increment(1));
        updates.put("stats.korakPoKorak.totalScore", FieldValue.increment(score));
        updates.put("stats.korakPoKorak.step1Hits", FieldValue.increment(stepHits[0]));
        updates.put("stats.korakPoKorak.step2Hits", FieldValue.increment(stepHits[1]));
        updates.put("stats.korakPoKorak.step3Hits", FieldValue.increment(stepHits[2]));
        updates.put("stats.korakPoKorak.step4Hits", FieldValue.increment(stepHits[3]));
        updates.put("stats.korakPoKorak.step5Hits", FieldValue.increment(stepHits[4]));
        updates.put("stats.korakPoKorak.step6Hits", FieldValue.increment(stepHits[5]));
        updates.put("stats.korakPoKorak.step7Hits", FieldValue.increment(stepHits[6]));
        return updates;
    }

    private Map<String, Object> buildMatchUpdate(int score, String winner, String side,
                                                 String gamesPlayedPath, String totalScorePath,
                                                 String statPath1, int statValue1, String statPath2, int statValue2,
                                                 String ignored1, int ignoredVal1, String ignored2, int ignoredVal2) {
        Map<String, Object> updates = buildBaseMatchUpdate(score, winner, side);
        updates.put(gamesPlayedPath, FieldValue.increment(1));
        updates.put(totalScorePath, FieldValue.increment(score));
        if (statPath1 != null) {
            updates.put(statPath1, FieldValue.increment(statValue1));
        }
        if (statPath2 != null) {
            updates.put(statPath2, FieldValue.increment(statValue2));
        }
        return updates;
    }

    private Map<String, Object> buildBaseMatchUpdate(int score, String winner, String side) {
        return new HashMap<>();
    }
}
