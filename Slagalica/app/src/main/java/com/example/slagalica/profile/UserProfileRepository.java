package com.example.slagalica.profile;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UserProfileRepository {

    private static final String USERS_COLLECTION = "users";

    private final FirebaseFirestore db;

    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public UserProfileRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadProfile(String uid, ProfileCallback callback) {
        db.collection(USERS_COLLECTION).document(uid)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            callback.onError("Profil nije pronadjen");
                            return;
                        }

                        callback.onSuccess(mapProfile(task.getResult()));
                    }
                });
    }

    public void updateAvatarTheme(String uid, int avatarTheme, OperationCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("avatarTheme", avatarTheme);

        db.collection(USERS_COLLECTION).document(uid)
                .update(updates)
                .addOnCompleteListener(task -> notifyCallback(task, callback, "Greska pri cuvanju avatara"));
    }

    public void ensureProfileDefaults(String uid, OperationCallback callback) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("avatarTheme", 0);
        defaults.put("avatarFrameRank", 0);
        defaults.put("avatarFrameCycleMonth", "");
        defaults.put("tokens", 5);
        defaults.put("stars", 0);
        defaults.put("starTokenProgress", 0);
        defaults.put("lastDailyTokenGrant", null);
        defaults.put("matchesPlayed", 0);
        defaults.put("wins", 0);
        defaults.put("losses", 0);
        defaults.put("stats.koZnaZna.gamesPlayed", 0);
        defaults.put("stats.koZnaZna.totalScore", 0);
        defaults.put("stats.koZnaZna.correctAnswers", 0);
        defaults.put("stats.koZnaZna.wrongAnswers", 0);
        defaults.put("stats.spojnice.gamesPlayed", 0);
        defaults.put("stats.spojnice.totalScore", 0);
        defaults.put("stats.spojnice.successfulLinks", 0);
        defaults.put("stats.spojnice.attemptedLinks", 0);
        defaults.put("stats.mojBroj.gamesPlayed", 0);
        defaults.put("stats.mojBroj.totalScore", 0);
        defaults.put("stats.mojBroj.exactHits", 0);
        defaults.put("stats.mojBroj.roundsPlayed", 0);
        defaults.put("stats.korakPoKorak.gamesPlayed", 0);
        defaults.put("stats.korakPoKorak.totalScore", 0);
        defaults.put("stats.korakPoKorak.step1Hits", 0);
        defaults.put("stats.korakPoKorak.step2Hits", 0);
        defaults.put("stats.korakPoKorak.step3Hits", 0);
        defaults.put("stats.korakPoKorak.step4Hits", 0);
        defaults.put("stats.korakPoKorak.step5Hits", 0);
        defaults.put("stats.korakPoKorak.step6Hits", 0);
        defaults.put("stats.korakPoKorak.step7Hits", 0);

        db.collection(USERS_COLLECTION).document(uid)
                .update(defaults)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() || task.getException() == null) {
                        callback.onSuccess();
                    } else {
                        callback.onSuccess();
                    }
                });
    }

    public void grantDailyTokensIfNeeded(String uid, OperationCallback callback) {
        DocumentReference userRef = db.collection(USERS_COLLECTION).document(uid);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(userRef);
                    if (!snapshot.exists()) {
                        return false;
                    }

                    Timestamp lastGrant = snapshot.getTimestamp("lastDailyTokenGrant");
                    if (isToday(lastGrant)) {
                        return false;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tokens", FieldValue.increment(5));
                    updates.put("lastDailyTokenGrant", FieldValue.serverTimestamp());
                    transaction.update(userRef, updates);
                    return true;
                })
                .addOnSuccessListener(granted -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Greska pri dnevnom token grantu");
                    }
                });
    }

    private void notifyCallback(Task<?> task, OperationCallback callback, String fallbackMessage) {
        if (callback == null) {
            return;
        }

        if (task.isSuccessful()) {
            callback.onSuccess();
            return;
        }

        String message = task.getException() != null ? task.getException().getMessage() : fallbackMessage;
        callback.onError(message);
    }

    @SuppressWarnings("unchecked")
    private UserProfile mapProfile(DocumentSnapshot snapshot) {
        Map<String, Object> stats = (Map<String, Object>) snapshot.get("stats");
        Map<String, Object> koZnaZna = stats != null && stats.get("koZnaZna") instanceof Map
                ? (Map<String, Object>) stats.get("koZnaZna") : null;
        Map<String, Object> spojnice = stats != null && stats.get("spojnice") instanceof Map
                ? (Map<String, Object>) stats.get("spojnice") : null;
        Map<String, Object> mojBroj = stats != null && stats.get("mojBroj") instanceof Map
                ? (Map<String, Object>) stats.get("mojBroj") : null;
        Map<String, Object> korakPoKorak = stats != null && stats.get("korakPoKorak") instanceof Map
                ? (Map<String, Object>) stats.get("korakPoKorak") : null;

        return new UserProfile(
                snapshot.getId(),
                valueOrDefault(snapshot.getString("username"), "Nepoznat korisnik"),
                valueOrDefault(snapshot.getString("email"), ""),
                valueOrDefault(snapshot.getString("region"), "Nepoznat region"),
                intValue(snapshot.get("avatarTheme")),
                intValue(snapshot.get("avatarFrameRank")),
                valueOrDefault(snapshot.getString("avatarFrameCycleMonth"), ""),
                snapshot.contains("tokens") ? intValue(snapshot.get("tokens")) : 5,
                intValue(snapshot.get("stars")),
                intValue(snapshot.get("matchesPlayed")),
                intValue(snapshot.get("wins")),
                intValue(snapshot.get("losses")),
                mapGameStats(koZnaZna),
                mapGameStats(spojnice),
                mapGameStats(mojBroj),
                mapGameStats(korakPoKorak)
        );
    }

    private UserProfile.GameStats mapGameStats(Map<String, Object> rawStats) {
        if (rawStats == null) {
            return new UserProfile.GameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        return new UserProfile.GameStats(
                intValue(rawStats.get("gamesPlayed")),
                intValue(rawStats.get("totalScore")),
                intValue(rawStats.get("correctAnswers")),
                intValue(rawStats.get("wrongAnswers")),
                intValue(rawStats.get("successfulLinks")),
                intValue(rawStats.get("attemptedLinks")),
                intValue(rawStats.get("exactHits")),
                intValue(rawStats.get("roundsPlayed")),
                intValue(rawStats.get("step1Hits")),
                intValue(rawStats.get("step2Hits")),
                intValue(rawStats.get("step3Hits")),
                intValue(rawStats.get("step4Hits")),
                intValue(rawStats.get("step5Hits")),
                intValue(rawStats.get("step6Hits")),
                intValue(rawStats.get("step7Hits"))
        );
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private boolean isToday(Timestamp timestamp) {
        if (timestamp == null) {
            return false;
        }

        Calendar grantDay = Calendar.getInstance();
        grantDay.setTime(timestamp.toDate());
        Calendar today = Calendar.getInstance();
        return grantDay.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && grantDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

}
