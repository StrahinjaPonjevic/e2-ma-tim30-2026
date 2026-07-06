package com.example.slagalica.notifications;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NotificationStore {

    private static final String USERS_COLLECTION = "users";
    private static final String NOTIFICATIONS_COLLECTION = "notifications";

    public interface NotificationsListener {
        void onChanged(List<NotificationItem> notifications);
        void onError(String message);
    }

    private NotificationStore() {
    }

    public static void save(String uid, String channel, String title, String message,
                            String type, String targetId) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("channel", channel != null ? channel : NotificationChannelManager.CHANNEL_OTHER);
        data.put("title", title != null ? title : "");
        data.put("message", message != null ? message : "");
        data.put("type", type != null ? type : "other");
        data.put("targetId", targetId);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .add(data);
    }

    public static ListenerRegistration observe(String uid, NotificationsListener listener) {
        return FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage() != null
                                ? error.getMessage()
                                : "Greska pri ucitavanju notifikacija");
                        return;
                    }
                    List<NotificationItem> items = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            items.add(NotificationItem.fromSnapshot(doc));
                        }
                    }
                    listener.onChanged(items);
                });
    }

    public static void markRead(String uid, String notificationId) {
        if (uid == null || notificationId == null) {
            return;
        }
        FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("read", true);
    }
}
