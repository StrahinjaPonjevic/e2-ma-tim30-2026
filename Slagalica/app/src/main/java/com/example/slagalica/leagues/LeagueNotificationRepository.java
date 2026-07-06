package com.example.slagalica.leagues;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationHelper;
import com.example.slagalica.notifications.NotificationStore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.lang.ref.WeakReference;

public final class LeagueNotificationRepository {

    private static final String PREFERENCES_NAME = "league_notifications";
    private static final String LAST_SEEN_PREFIX = "last_seen_";

    private static ListenerRegistration leagueChangeListener;
    private static String listenerUid;
    private static boolean initialSnapshot = true;
    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);

    private LeagueNotificationRepository() {
    }

    public static void startLeagueChangeListener(Context context, String uid) {
        if (context == null || uid == null || uid.trim().isEmpty()) {
            stopLeagueChangeListener();
            return;
        }
        if (leagueChangeListener != null && uid.equals(listenerUid)) {
            return;
        }

        stopLeagueChangeListener();
        listenerUid = uid;
        initialSnapshot = true;
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);

        leagueChangeListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    String notificationId = snapshot.getString("lastLeagueNotificationId");
                    if (initialSnapshot) {
                        initialSnapshot = false;
                        rememberNotification(preferences, uid, notificationId);
                        return;
                    }
                    if (notificationId == null || notificationId.trim().isEmpty()) {
                        return;
                    }

                    String lastSeenId = preferences.getString(LAST_SEEN_PREFIX + uid, "");
                    if (notificationId.equals(lastSeenId)) {
                        return;
                    }
                    rememberNotification(preferences, uid, notificationId);
                    notifyLeagueChange(appContext, uid, snapshot, notificationId);
                });
    }

    public static void stopLeagueChangeListener() {
        if (leagueChangeListener != null) {
            leagueChangeListener.remove();
            leagueChangeListener = null;
        }
        listenerUid = null;
        initialSnapshot = true;
    }

    public static void setCurrentActivity(Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    public static void clearCurrentActivity(Activity activity) {
        Activity active = currentActivity.get();
        if (active == activity) {
            currentActivity.clear();
        }
    }

    private static void notifyLeagueChange(Context appContext, String uid, DocumentSnapshot snapshot,
                                           String notificationId) {
        int fromLevel = intValue(snapshot.get("lastLeagueChangeFrom"));
        int toLevel = intValue(snapshot.get("lastLeagueChangeTo"));
        String direction = snapshot.getString("lastLeagueChangeDirection");
        boolean movedUp = "up".equals(direction) || (direction == null && toLevel > fromLevel);
        String title = movedUp ? "Nova liga!" : "Pad u nižu ligu";
        String message = LeagueUiHelper.changeMessage(fromLevel, toLevel, direction);

        NotificationStore.save(uid, NotificationChannelManager.CHANNEL_RANKING,
                title, message, "league", null);

        Activity activity = currentActivity.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    new AlertDialog.Builder(activity)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton("U redu", null)
                            .show();
                }
            });
            return;
        }

        NotificationHelper.send(
                appContext,
                NotificationChannelManager.CHANNEL_RANKING,
                title,
                message,
                notificationId.hashCode() & 0x7fffffff
        );
    }

    private static void rememberNotification(SharedPreferences preferences, String uid,
                                             String notificationId) {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            return;
        }
        preferences.edit().putString(LAST_SEEN_PREFIX + uid, notificationId).apply();
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
