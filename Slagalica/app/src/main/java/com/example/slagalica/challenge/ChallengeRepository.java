package com.example.slagalica.challenge;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeRepository {

    private static final String CHALLENGES = "challenges";
    private static final String USERS = "users";

    private final FirebaseFirestore db;

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface CreateCallback {
        void onSuccess(String challengeId);
        void onError(String message);
    }

    public interface ChallengeListener {
        void onChallenge(ChallengeData challenge);
        void onError(String message);
    }

    public interface ChallengeListListener {
        void onChallenges(List<ChallengeData> challenges);
        void onError(String message);
    }

    public ChallengeRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public ListenerRegistration listenRegionChallenges(String region, ChallengeListListener listener) {
        return db.collection(CHALLENGES)
                .whereEqualTo("region", region)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri ucitavanju izazova"));
                        return;
                    }

                    List<ChallengeData> challenges = new ArrayList<>();
                    if (snapshot != null) {
                        snapshot.getDocuments().forEach(doc -> challenges.add(ChallengeData.fromSnapshot(doc)));
                    }
                    listener.onChallenges(challenges);
                });
    }

    public ListenerRegistration listenChallenge(String challengeId, ChallengeListener listener) {
        return db.collection(CHALLENGES).document(challengeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri ucitavanju izazova"));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onError("Izazov nije pronadjen");
                        return;
                    }
                    listener.onChallenge(ChallengeData.fromSnapshot(snapshot));
                });
    }

    public void createChallenge(String region, String creatorId, String creatorUsername,
                                int starsStake, int tokensStake, CreateCallback callback) {
        if (!validStake(starsStake, tokensStake, callback)) {
            return;
        }

        DocumentReference challengeRef = db.collection(CHALLENGES).document();
        DocumentReference userRef = db.collection(USERS).document(creatorId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot user = transaction.get(userRef);
                    ensureFunds(user, starsStake, tokensStake);

                    Map<String, Object> participants = new HashMap<>();
                    participants.put(creatorId, creatorUsername);

                    Map<String, Object> challenge = new HashMap<>();
                    challenge.put("region", region);
                    challenge.put("creatorId", creatorId);
                    challenge.put("creatorUsername", creatorUsername);
                    challenge.put("starsStake", starsStake);
                    challenge.put("tokensStake", tokensStake);
                    challenge.put("status", ChallengeData.STATUS_OPEN);
                    challenge.put("participants", participants);
                    challenge.put("scores", new HashMap<String, Object>());
                    challenge.put("winnerId", null);
                    challenge.put("secondPlaceId", null);
                    challenge.put("createdAt", FieldValue.serverTimestamp());
                    challenge.put("updatedAt", FieldValue.serverTimestamp());

                    transaction.update(userRef, "stars", FieldValue.increment(-starsStake),
                            "tokens", FieldValue.increment(-tokensStake));
                    transaction.set(challengeRef, challenge);
                    return challengeRef.getId();
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Kreiranje izazova nije uspelo")));
    }

    public void acceptChallenge(String challengeId, String uid, String username, OperationCallback callback) {
        DocumentReference challengeRef = db.collection(CHALLENGES).document(challengeId);
        DocumentReference userRef = db.collection(USERS).document(uid);
        db.runTransaction(transaction -> {
                    DocumentSnapshot challengeSnap = transaction.get(challengeRef);
                    if (!challengeSnap.exists()) {
                        throw abort("Izazov nije pronadjen");
                    }
                    ChallengeData challenge = ChallengeData.fromSnapshot(challengeSnap);
                    if (!ChallengeData.STATUS_OPEN.equals(challenge.status)) {
                        throw abort("Izazov nije otvoren");
                    }
                    if (challenge.hasParticipant(uid)) {
                        return null;
                    }
                    if (challenge.participants.size() >= 4) {
                        throw abort("Izazov vec ima maksimalan broj ucesnika");
                    }

                    DocumentSnapshot user = transaction.get(userRef);
                    ensureFunds(user, challenge.starsStake, challenge.tokensStake);
                    transaction.update(userRef,
                            "stars", FieldValue.increment(-challenge.starsStake),
                            "tokens", FieldValue.increment(-challenge.tokensStake));
                    transaction.update(challengeRef,
                            "participants." + uid, username,
                            "updatedAt", FieldValue.serverTimestamp());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Prihvatanje izazova nije uspelo")));
    }

    public void startChallenge(String challengeId, String uid, OperationCallback callback) {
        DocumentReference challengeRef = db.collection(CHALLENGES).document(challengeId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(challengeRef);
                    if (!snapshot.exists()) {
                        throw abort("Izazov nije pronadjen");
                    }
                    ChallengeData challenge = ChallengeData.fromSnapshot(snapshot);
                    if (!uid.equals(challenge.creatorId)) {
                        throw abort("Samo kreator moze startovati izazov");
                    }
                    if (!ChallengeData.STATUS_OPEN.equals(challenge.status)) {
                        return null;
                    }
                    if (challenge.participants.size() < 2) {
                        throw abort("Potrebna su bar 2 ucesnika");
                    }
                    transaction.update(challengeRef,
                            "status", ChallengeData.STATUS_IN_PROGRESS,
                            "updatedAt", FieldValue.serverTimestamp());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Start izazova nije uspeo")));
    }

    public void submitScore(String challengeId, String uid, int score, OperationCallback callback) {
        DocumentReference challengeRef = db.collection(CHALLENGES).document(challengeId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(challengeRef);
                    if (!snapshot.exists()) {
                        throw abort("Izazov nije pronadjen");
                    }
                    ChallengeData challenge = ChallengeData.fromSnapshot(snapshot);
                    if (!ChallengeData.STATUS_IN_PROGRESS.equals(challenge.status)) {
                        throw abort("Izazov nije u toku");
                    }
                    if (!challenge.hasParticipant(uid)) {
                        throw abort("Niste ucesnik izazova");
                    }
                    if (challenge.hasScore(uid)) {
                        return null;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("scores." + uid, score);
                    updates.put("updatedAt", FieldValue.serverTimestamp());

                    Map<String, Object> mergedScores = new HashMap<>(challenge.scores);
                    mergedScores.put(uid, score);
                    if (mergedScores.size() == challenge.participants.size()) {
                        Winners winners = resolveWinners(mergedScores);
                        updates.put("status", ChallengeData.STATUS_FINISHED);
                        updates.put("winnerId", winners.winnerId);
                        updates.put("secondPlaceId", winners.secondPlaceId);
                        applyPayouts(transaction, challenge, winners);
                    }

                    transaction.update(challengeRef, updates);
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Predaja rezultata nije uspela")));
    }

    private void applyPayouts(com.google.firebase.firestore.Transaction transaction,
                              ChallengeData challenge,
                              Winners winners) {
        int participantCount = challenge.participants.size();
        int totalStars = challenge.starsStake * participantCount;
        int totalTokens = challenge.tokensStake * participantCount;
        int winnerStars = (int) Math.floor(totalStars * 0.75);
        int winnerTokens = (int) Math.floor(totalTokens * 0.75);

        DocumentReference winnerRef = db.collection(USERS).document(winners.winnerId);
        transaction.update(winnerRef,
                "stars", FieldValue.increment(winnerStars),
                "tokens", FieldValue.increment(winnerTokens));

        if (winners.secondPlaceId != null && !winners.secondPlaceId.equals(winners.winnerId)) {
            DocumentReference secondRef = db.collection(USERS).document(winners.secondPlaceId);
            transaction.update(secondRef,
                    "stars", FieldValue.increment(challenge.starsStake),
                    "tokens", FieldValue.increment(challenge.tokensStake));
        }
    }

    private Winners resolveWinners(Map<String, Object> scores) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(scores.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, Object> entry) ->
                entry.getValue() instanceof Number ? ((Number) entry.getValue()).intValue() : 0).reversed());

        Winners winners = new Winners();
        winners.winnerId = entries.get(0).getKey();
        winners.secondPlaceId = entries.size() > 1 ? entries.get(1).getKey() : null;
        return winners;
    }

    private boolean validStake(int starsStake, int tokensStake, CreateCallback callback) {
        if (starsStake < 0 || starsStake > 10 || tokensStake < 0 || tokensStake > 2) {
            callback.onError("Ulog je max 10 zvezdi i max 2 tokena");
            return false;
        }
        return true;
    }

    private void ensureFunds(DocumentSnapshot user, int starsStake, int tokensStake) throws FirebaseFirestoreException {
        int stars = intValue(user.get("stars"));
        int tokens = intValue(user.get("tokens"));
        if (stars < starsStake || tokens < tokensStake) {
            throw abort("Nemate dovoljno zvezdi ili tokena");
        }
    }

    private FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.ABORTED);
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String messageOf(Exception e, String fallback) {
        return e != null && e.getMessage() != null ? e.getMessage() : fallback;
    }

    private static final class Winners {
        String winnerId;
        String secondPlaceId;
    }
}
