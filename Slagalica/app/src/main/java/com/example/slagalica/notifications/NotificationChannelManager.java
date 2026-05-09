package com.example.slagalica.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class NotificationChannelManager {

    public static final String CHANNEL_CHAT = "channel_chat";
    public static final String CHANNEL_RANKING = "channel_ranking";
    public static final String CHANNEL_REWARDS = "channel_rewards";
    public static final String CHANNEL_OTHER = "channel_other";

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_CHAT, "Čet poruke", NotificationManager.IMPORTANCE_HIGH));

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_RANKING, "Rangiranje", NotificationManager.IMPORTANCE_DEFAULT));

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_HIGH));

        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_DEFAULT));
    }
}