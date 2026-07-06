package com.example.slagalica.notifications;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class NotificationItem {

    public String notificationId;
    public String channel;
    public String title;
    public String message;
    public String type;
    public String targetId;
    public boolean read;
    public Long createdAtMs;

    public static NotificationItem fromSnapshot(DocumentSnapshot snapshot) {
        NotificationItem item = new NotificationItem();
        item.notificationId = snapshot.getId();
        item.channel = snapshot.getString("channel");
        item.title = snapshot.getString("title");
        item.message = snapshot.getString("message");
        item.type = snapshot.getString("type");
        item.targetId = snapshot.getString("targetId");
        item.read = Boolean.TRUE.equals(snapshot.getBoolean("read"));
        Timestamp createdAt = snapshot.getTimestamp("createdAt");
        item.createdAtMs = createdAt != null ? createdAt.toDate().getTime() : null;
        return item;
    }
}
