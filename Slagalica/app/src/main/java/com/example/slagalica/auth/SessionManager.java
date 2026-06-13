package com.example.slagalica.auth;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SessionManager {

    private static final String SESSIONS_COLLECTION = "sessions";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final FirebaseFirestore db;

    public interface SessionCallback {
        void onSuccess(String sessionId);
        void onError(String message);
    }

    public interface JoinCallback {
        void onSuccess(String sessionId);
        void onError(String message);
    }

    public interface SessionListener {
        void onSessionUpdated(SessionData data);
        void onError(String message);
    }

    public static class SessionData {
        public String sessionId;
        public String code;
        public String ownerId;
        public String ownerUsername;
        public String guestId;
        public String guestUsername;
        public String status;
        public String selectedGame;

        public SessionData(String sessionId, String code, String ownerId, String ownerUsername,
                           String guestId, String guestUsername, String status, String selectedGame) {
            this.sessionId = sessionId;
            this.code = code;
            this.ownerId = ownerId;
            this.ownerUsername = ownerUsername;
            this.guestId = guestId;
            this.guestUsername = guestUsername;
            this.status = status;
            this.selectedGame = selectedGame;
        }
    }

    public SessionManager() {
        db = FirebaseFirestore.getInstance();
    }

    public void createSession(String ownerId, String ownerUsername, SessionCallback callback) {
        String code = generateCode();

        Map<String, Object> session = new HashMap<>();
        session.put("code", code);
        session.put("ownerId", ownerId);
        session.put("ownerUsername", ownerUsername);
        session.put("guestId", null);
        session.put("guestUsername", null);
        session.put("status", "waiting");
        session.put("selectedGame", null);
        session.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection(SESSIONS_COLLECTION)
                .add(session)
                .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        if (task.isSuccessful()) {
                            String sessionId = task.getResult().getId();
                            callback.onSuccess(sessionId);
                        } else {
                            callback.onError("Greška pri kreiranju sesije");
                        }
                    }
                });
    }

    public void joinSession(String code, String guestId, String guestUsername, JoinCallback callback) {
        db.collection(SESSIONS_COLLECTION)
                .whereEqualTo("code", code)
                .whereEqualTo("status", "waiting")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            String sessionId = doc.getId();

                            Map<String, Object> updates = new HashMap<>();
                            updates.put("guestId", guestId);
                            updates.put("guestUsername", guestUsername);
                            updates.put("status", "joined");

                            db.collection(SESSIONS_COLLECTION).document(sessionId)
                                    .update(updates)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> updateTask) {
                                            if (updateTask.isSuccessful()) {
                                                callback.onSuccess(sessionId);
                                            } else {
                                                callback.onError("Greška pri pridruživanju sesiji");
                                            }
                                        }
                                    });
                        } else {
                            callback.onError("Kod nije validan ili je sesija istekla");
                        }
                    }
                });
    }

    public void selectGame(String sessionId, String gameName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("selectedGame", gameName);
        updates.put("status", "in_game");

        db.collection(SESSIONS_COLLECTION).document(sessionId)
                .update(updates);
    }

    public void listenSession(String sessionId, SessionListener listener) {
        db.collection(SESSIONS_COLLECTION).document(sessionId)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot snapshot, com.google.firebase.firestore.FirebaseFirestoreException e) {
                        if (e != null) {
                            listener.onError("Greška pri osluškivanju sesije");
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            SessionData data = new SessionData(
                                    sessionId,
                                    snapshot.getString("code"),
                                    snapshot.getString("ownerId"),
                                    snapshot.getString("ownerUsername"),
                                    snapshot.getString("guestId"),
                                    snapshot.getString("guestUsername"),
                                    snapshot.getString("status"),
                                    snapshot.getString("selectedGame")
                            );
                            listener.onSessionUpdated(data);
                        }
                    }
                });
    }

    public void endSession(String sessionId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "finished");

        db.collection(SESSIONS_COLLECTION).document(sessionId)
                .update(updates);
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }
}
