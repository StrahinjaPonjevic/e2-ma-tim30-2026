package com.example.slagalica.notifications;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.ProfileActivity;
import com.example.slagalica.R;
import com.example.slagalica.chat.ChatActivity;
import com.example.slagalica.party.FriendlyInviteActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_UNREAD = 1;
    private static final int FILTER_READ = 2;

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private Button btnFilterAll, btnFilterUnread, btnFilterRead, btnBack;

    private NotificationAdapter adapter;
    private ListenerRegistration notificationsListener;
    private String currentUserId;
    private int activeFilter = FILTER_ALL;
    private List<NotificationItem> allNotifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za pregled notifikacija.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvNotificationsEmpty);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);
        btnFilterRead = findViewById(R.id.btnFilterRead);
        btnBack = findViewById(R.id.btnNotificationsBack);

        adapter = new NotificationAdapter(this::onNotificationClicked);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        btnFilterAll.setOnClickListener(v -> applyFilter(FILTER_ALL));
        btnFilterUnread.setOnClickListener(v -> applyFilter(FILTER_UNREAD));
        btnFilterRead.setOnClickListener(v -> applyFilter(FILTER_READ));
        btnBack.setOnClickListener(v -> finish());

        highlightFilterButtons();
        observeNotifications();
    }

    private void observeNotifications() {
        notificationsListener = NotificationStore.observe(currentUserId,
                new NotificationStore.NotificationsListener() {
                    @Override
                    public void onChanged(List<NotificationItem> notifications) {
                        runOnUiThread(() -> {
                            allNotifications = notifications;
                            renderList();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void applyFilter(int filter) {
        activeFilter = filter;
        highlightFilterButtons();
        renderList();
    }

    private void highlightFilterButtons() {
        btnFilterAll.setBackgroundColor(activeFilter == FILTER_ALL
                ? Color.parseColor("#6200EE") : Color.parseColor("#9E9E9E"));
        btnFilterUnread.setBackgroundColor(activeFilter == FILTER_UNREAD
                ? Color.parseColor("#6200EE") : Color.parseColor("#9E9E9E"));
        btnFilterRead.setBackgroundColor(activeFilter == FILTER_READ
                ? Color.parseColor("#6200EE") : Color.parseColor("#9E9E9E"));
    }

    private void renderList() {
        List<NotificationItem> filtered = new ArrayList<>();
        for (NotificationItem item : allNotifications) {
            if (activeFilter == FILTER_ALL
                    || (activeFilter == FILTER_UNREAD && !item.read)
                    || (activeFilter == FILTER_READ && item.read)) {
                filtered.add(item);
            }
        }
        adapter.submit(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }

    private void onNotificationClicked(NotificationItem item) {
        if (!item.read) {
            NotificationStore.markRead(currentUserId, item.notificationId);
        }
        routeToTarget(item);
    }

    private void routeToTarget(NotificationItem item) {
        String type = item.type != null ? item.type : "";
        switch (type) {
            case "chat":
                startActivity(new Intent(this, ChatActivity.class));
                break;
            case "friendly_invite":
                startActivity(new Intent(this, FriendlyInviteActivity.class));
                break;
            case "league":
            case "ranking":
            case "reward":
                startActivity(new Intent(this, ProfileActivity.class));
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsListener != null) {
            notificationsListener.remove();
        }
    }
}
