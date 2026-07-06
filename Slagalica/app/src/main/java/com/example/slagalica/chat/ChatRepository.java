package com.example.slagalica.chat;

import android.content.Context;

import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationHelper;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRepository {

    private static ListenerRegistration notificationListener;
    private static String notificationRegion;
    private static boolean notificationInitialSnapshot = true;

    private final FirebaseFirestore db;

    public interface MessagesCallback {
        void onMessages(List<ChatMessage> messages);
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public ListenerRegistration listenMessages(String region, MessagesCallback callback) {
        return messagesCollection(region)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(100)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage() != null ? error.getMessage() : "Greska pri citanju ceta");
                        return;
                    }

                    List<ChatMessage> messages = new ArrayList<>();
                    if (snapshot != null) {
                        snapshot.getDocuments().forEach(doc -> messages.add(ChatMessage.fromSnapshot(doc)));
                    }
                    callback.onMessages(messages);
                });
    }

    public void sendMessage(String region, String senderId, String senderName, String text,
                            OperationCallback callback) {
        String clean = text != null ? text.trim() : "";
        if (clean.isEmpty()) {
            callback.onError("Poruka je prazna");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("senderId", senderId);
        data.put("senderName", senderName);
        data.put("text", clean);
        data.put("createdAt", FieldValue.serverTimestamp());

        messagesCollection(region)
                .add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage() != null ? e.getMessage() : "Slanje nije uspelo"));
    }

    public static void startNotificationListener(Context context, String region, String currentUserId) {
        if (region == null || region.trim().isEmpty() || currentUserId == null) {
            stopNotificationListener();
            return;
        }

        if (notificationListener != null && region.equals(notificationRegion)) {
            return;
        }

        stopNotificationListener();
        notificationRegion = region;
        notificationInitialSnapshot = true;
        Context appContext = context.getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        notificationListener = db.collection("region_chats")
                .document(region)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(1)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || snapshot.isEmpty()) {
                        return;
                    }

                    ChatMessage latest = ChatMessage.fromSnapshot(snapshot.getDocuments().get(0));
                    if (notificationInitialSnapshot) {
                        notificationInitialSnapshot = false;
                        return;
                    }

                    if (currentUserId.equals(latest.senderId) || ChatActivity.isActiveForRegion(region)) {
                        return;
                    }

                    NotificationHelper.sendAndStore(
                            appContext,
                            currentUserId,
                            NotificationChannelManager.CHANNEL_CHAT,
                            "Nova cet poruka",
                            latest.senderName + ": " + latest.text,
                            Math.abs(latest.messageId != null ? latest.messageId.hashCode() : 17),
                            "chat",
                            region
                    );
                });
    }

    public static void stopNotificationListener() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
        notificationRegion = null;
        notificationInitialSnapshot = true;
    }

    private com.google.firebase.firestore.CollectionReference messagesCollection(String region) {
        return db.collection("region_chats")
                .document(region)
                .collection("messages");
    }
}
