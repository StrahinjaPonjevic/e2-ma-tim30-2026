package com.example.slagalica.party;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class FriendlyInviteActivity extends AppCompatActivity {
    private TextView tvTitle;
    private TextView tvIncomingInvite;
    private Button btnAcceptInvite;
    private Button btnDeclineInvite;
    private Button btnBack;
    private RecyclerView rvUsers;

    private FirebaseManager firebaseManager;
    private FriendlyInviteRepository inviteRepository;
    private FirebaseUser currentUser;
    private ListenerRegistration incomingListener;
    private ListenerRegistration outgoingListener;
    private FriendlyUserAdapter adapter;
    private FriendlyInviteData currentInvite;
    private String outgoingInviteId;
    private String username;
    private String region;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friendly_invite);

        firebaseManager = new FirebaseManager();
        inviteRepository = new FriendlyInviteRepository();
        currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za prijateljske partije.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupList();
        bindActions();
        loadUser();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvFriendlyTitle);
        tvIncomingInvite = findViewById(R.id.tvIncomingInvite);
        btnAcceptInvite = findViewById(R.id.btnAcceptFriendlyInvite);
        btnDeclineInvite = findViewById(R.id.btnDeclineFriendlyInvite);
        btnBack = findViewById(R.id.btnFriendlyBack);
        rvUsers = findViewById(R.id.rvFriendlyUsers);
    }

    private void setupList() {
        adapter = new FriendlyUserAdapter();
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }

    private void bindActions() {
        btnBack.setOnClickListener(v -> finish());
        btnAcceptInvite.setOnClickListener(v -> acceptCurrentInvite());
        btnDeclineInvite.setOnClickListener(v -> declineCurrentInvite());
        renderNoInvite();
    }

    private void loadUser() {
        firebaseManager.loadUserData(currentUser.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String loadedUsername, String loadedRegion) {
                username = loadedUsername;
                region = loadedRegion;
                runOnUiThread(() -> {
                    tvTitle.setText("Prijateljska partija - " + region);
                    listenIncomingInvites();
                    loadRegionUsers();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void listenIncomingInvites() {
        if (incomingListener != null) {
            incomingListener.remove();
        }
        incomingListener = inviteRepository.listenIncomingInvite(currentUser.getUid(), new FriendlyInviteRepository.InviteListener() {
            @Override
            public void onInvite(FriendlyInviteData invite) {
                runOnUiThread(() -> renderInvite(invite));
            }

            @Override
            public void onNone() {
                runOnUiThread(() -> renderNoInvite());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadRegionUsers() {
        inviteRepository.listUsersInRegion(region, currentUser.getUid(), new FriendlyInviteRepository.UsersCallback() {
            @Override
            public void onUsers(List<FriendlyInviteRepository.UserSummary> users) {
                runOnUiThread(() -> adapter.submit(users));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderInvite(FriendlyInviteData invite) {
        currentInvite = invite;
        tvIncomingInvite.setText(invite.inviterUsername + " vas poziva na prijateljsku partiju.");
        btnAcceptInvite.setVisibility(View.VISIBLE);
        btnDeclineInvite.setVisibility(View.VISIBLE);

        long delayMs = invite.expiresAt != null
                ? Math.max(0L, invite.expiresAt.toDate().getTime() - System.currentTimeMillis())
                : 10_000L;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (currentInvite != null && currentInvite.inviteId.equals(invite.inviteId)) {
                inviteRepository.expireInvite(invite.inviteId, null);
                renderNoInvite();
            }
        }, delayMs);
    }

    private void renderNoInvite() {
        currentInvite = null;
        tvIncomingInvite.setText("Nema aktivnih poziva.");
        btnAcceptInvite.setVisibility(View.GONE);
        btnDeclineInvite.setVisibility(View.GONE);
    }

    private void acceptCurrentInvite() {
        if (currentInvite == null) {
            return;
        }
        btnAcceptInvite.setEnabled(false);
        btnDeclineInvite.setEnabled(false);
        inviteRepository.acceptInvite(currentInvite.inviteId, currentUser.getUid(), new FriendlyInviteRepository.PartyReadyCallback() {
            @Override
            public void onSuccess(String partyId) {
                runOnUiThread(() -> {
                    btnAcceptInvite.setEnabled(true);
                    btnDeclineInvite.setEnabled(true);
                    Intent intent = new Intent(FriendlyInviteActivity.this, PartyActivity.class);
                    intent.putExtra(PartyActivity.EXTRA_PARTY_ID, partyId);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnAcceptInvite.setEnabled(true);
                    btnDeclineInvite.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void declineCurrentInvite() {
        if (currentInvite == null) {
            return;
        }
        inviteRepository.declineInvite(currentInvite.inviteId, new FriendlyInviteRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> renderNoInvite());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendInvite(FriendlyInviteRepository.UserSummary user) {
        inviteRepository.sendInvite(currentUser.getUid(), username, user.uid, user.username,
                new FriendlyInviteRepository.SendInviteCallback() {
                    @Override
                    public void onSuccess(String inviteId) {
                        runOnUiThread(() -> {
                            outgoingInviteId = inviteId;
                            listenOutgoingInvite(inviteId);
                            Toast.makeText(FriendlyInviteActivity.this,
                                    "Poziv poslat. Cekanje prihvatanja...", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void listenOutgoingInvite(String inviteId) {
        if (outgoingListener != null) {
            outgoingListener.remove();
            outgoingListener = null;
        }

        outgoingListener = inviteRepository.listenOutgoingInvite(inviteId, new FriendlyInviteRepository.OutgoingInviteListener() {
            @Override
            public void onAccepted(String partyId) {
                runOnUiThread(() -> {
                    clearOutgoingInviteListener();
                    Intent intent = new Intent(FriendlyInviteActivity.this, PartyActivity.class);
                    intent.putExtra(PartyActivity.EXTRA_PARTY_ID, partyId);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onDeclined() {
                runOnUiThread(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Poziv je odbijen.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onExpired() {
                runOnUiThread(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Poziv je istekao.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPending() {
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void clearOutgoingInviteListener() {
        outgoingInviteId = null;
        if (outgoingListener != null) {
            outgoingListener.remove();
            outgoingListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (incomingListener != null) {
            incomingListener.remove();
        }
        if (outgoingListener != null) {
            outgoingListener.remove();
        }
    }

    private final class FriendlyUserAdapter extends RecyclerView.Adapter<FriendlyUserViewHolder> {
        private final List<FriendlyInviteRepository.UserSummary> users = new ArrayList<>();

        void submit(List<FriendlyInviteRepository.UserSummary> newUsers) {
            users.clear();
            users.addAll(newUsers);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FriendlyUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friendly_user, parent, false);
            return new FriendlyUserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FriendlyUserViewHolder holder, int position) {
            FriendlyInviteRepository.UserSummary user = users.get(position);
            holder.tvUsername.setText(user.username);
            holder.tvRegion.setText(user.region);
            holder.btnInvite.setOnClickListener(v -> sendInvite(user));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }
    }

    private static final class FriendlyUserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvUsername;
        final TextView tvRegion;
        final Button btnInvite;

        FriendlyUserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvFriendlyUserName);
            tvRegion = itemView.findViewById(R.id.tvFriendlyUserRegion);
            btnInvite = itemView.findViewById(R.id.btnInviteFriendlyUser);
        }
    }
}
