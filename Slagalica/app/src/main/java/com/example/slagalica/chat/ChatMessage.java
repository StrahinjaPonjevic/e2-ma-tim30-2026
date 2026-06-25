package com.example.slagalica.chat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class ChatMessage {
    public String messageId;
    public String senderId;
    public String senderName;
    public String text;
    public Timestamp createdAt;

    public static ChatMessage fromSnapshot(DocumentSnapshot snapshot) {
        ChatMessage message = new ChatMessage();
        message.messageId = snapshot.getId();
        message.senderId = snapshot.getString("senderId");
        message.senderName = snapshot.getString("senderName");
        message.text = snapshot.getString("text");
        message.createdAt = snapshot.getTimestamp("createdAt");
        return message;
    }
}
