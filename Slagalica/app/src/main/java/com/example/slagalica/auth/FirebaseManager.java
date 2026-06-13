package com.example.slagalica.auth;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {

    private static final String USERS_COLLECTION = "users";

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface LoginCallback {
        void onSuccess(String email);
        void onError(String message);
    }

    public interface UserDataCallback {
        void onSuccess(String username, String region);
        void onError(String message);
    }

    public FirebaseManager() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void registerUser(String email, String password, String username, String region, AuthCallback callback) {
        checkUsernameUnique(username, new AuthCallback() {
            @Override
            public void onSuccess() {
                createAuthUser(email, password, username, region, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void checkUsernameUnique(String username, AuthCallback callback) {
        db.collection(USERS_COLLECTION)
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (task.getResult() != null && !task.getResult().isEmpty()) {
                                callback.onError("Korisničko ime je već zauzeto");
                            } else {
                                callback.onSuccess();
                            }
                        } else {
                            callback.onError("Greška pri proveri korisničkog imena");
                        }
                    }
                });
    }

    private void createAuthUser(String email, String password, String username, String region, AuthCallback callback) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                saveUserToFirestore(user.getUid(), email, username, region, callback);
                            } else {
                                callback.onError("Greška pri kreiranju naloga");
                            }
                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Registracija nije uspela";
                            callback.onError(error);
                        }
                    }
                });
    }

    private void saveUserToFirestore(String uid, String email, String username, String region, AuthCallback callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("username", username);
        userData.put("region", region);
        userData.put("avatarTheme", 0);
        userData.put("tokens", 5);
        userData.put("stars", 0);
        userData.put("matchesPlayed", 0);
        userData.put("wins", 0);
        userData.put("losses", 0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("koZnaZna", createStatsMap());
        stats.put("spojnice", createStatsMap());
        stats.put("mojBroj", createStatsMap());
        stats.put("korakPoKorak", createStatsMap());
        userData.put("stats", stats);

        db.collection(USERS_COLLECTION).document(uid)
                .set(userData)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            sendVerificationEmail(callback);
                        } else {
                            deleteAuthUserOnFailure();
                            callback.onError("Greška pri čuvanju podataka o korisniku");
                        }
                    }
                });
    }

    private Map<String, Object> createStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("gamesPlayed", 0);
        stats.put("totalScore", 0);
        stats.put("correctAnswers", 0);
        stats.put("wrongAnswers", 0);
        stats.put("successfulLinks", 0);
        stats.put("attemptedLinks", 0);
        stats.put("exactHits", 0);
        stats.put("roundsPlayed", 0);
        stats.put("step1Hits", 0);
        stats.put("step2Hits", 0);
        stats.put("step3Hits", 0);
        stats.put("step4Hits", 0);
        stats.put("step5Hits", 0);
        stats.put("step6Hits", 0);
        stats.put("step7Hits", 0);
        return stats;
    }

    private void sendVerificationEmail(AuthCallback callback) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                mAuth.signOut();
                                callback.onSuccess();
                            } else {
                                deleteAuthUserOnFailure();
                                callback.onError("Greška pri slanju verifikacionog emaila");
                            }
                        }
                    });
        } else {
            callback.onError("Korisnik nije pronađen");
        }
    }

    private void deleteAuthUserOnFailure() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.delete();
        }
    }

    public void loginWithEmail(String email, String password, LoginCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null && user.isEmailVerified()) {
                                callback.onSuccess(email);
                            } else if (user != null) {
                                mAuth.signOut();
                                callback.onError("Potvrdite email pre prijave. Link je poslat na vaš email.");
                            } else {
                                callback.onError("Greška pri prijavi");
                            }
                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Prijava nije uspela";
                            callback.onError(error);
                        }
                    }
                });
    }

    public void loginWithUsername(String username, String password, LoginCallback callback) {
        db.collection(USERS_COLLECTION)
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            String email = task.getResult().getDocuments().get(0).getString("email");
                            if (email != null) {
                                loginWithEmail(email, password, callback);
                            } else {
                                callback.onError("Korisnički nalog nije pronađen");
                            }
                        } else {
                            callback.onError("Korisničko ime nije pronađeno");
                        }
                    }
                });
    }

    public void resetPassword(String email, String oldPassword, String newPassword, AuthCallback callback) {
        mAuth.signInWithEmailAndPassword(email, oldPassword)
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                user.updatePassword(newPassword)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> updateTask) {
                                                mAuth.signOut();
                                                if (updateTask.isSuccessful()) {
                                                    callback.onSuccess();
                                                } else {
                                                    String error = updateTask.getException() != null
                                                            ? updateTask.getException().getMessage()
                                                            : "Promena lozinke nije uspela";
                                                    callback.onError(error);
                                                }
                                            }
                                        });
                            } else {
                                mAuth.signOut();
                                callback.onError("Greška pri resetovanju lozinke");
                            }
                        } else {
                            callback.onError("Stara lozinka nije tačna");
                        }
                    }
                });
    }

    public void signInAnonymously(final AuthCallback callback) {
        mAuth.signInAnonymously()
                .addOnCompleteListener(new OnCompleteListener<com.google.firebase.auth.AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<com.google.firebase.auth.AuthResult> task) {
                        if (task.isSuccessful()) {
                            callback.onSuccess();
                        } else {
                            callback.onError("Greška pri anonimnom logovanju");
                        }
                    }
                });
    }

    public void logout() {
        mAuth.signOut();
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public void loadUserData(String uid, UserDataCallback callback) {
        db.collection(USERS_COLLECTION).document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        String username = task.getResult().getString("username");
                        String region = task.getResult().getString("region");
                        if (username != null && region != null) {
                            callback.onSuccess(username, region);
                        } else {
                            callback.onError("Podaci o korisniku nisu potpuni");
                        }
                    } else {
                        callback.onError("Korisnik nije pronađen u bazi");
                    }
                });
    }
}
