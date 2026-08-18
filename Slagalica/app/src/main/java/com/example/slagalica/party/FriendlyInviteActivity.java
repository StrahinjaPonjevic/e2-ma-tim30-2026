package com.example.slagalica.party;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.regions.RegionAvatarFrameHelper;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.MultiFormatWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FriendlyInviteActivity extends AppCompatActivity {
    private static final int[] AVATAR_COLORS = {
            Color.rgb(201, 134, 82),
            Color.rgb(61, 126, 170),
            Color.rgb(109, 76, 156),
            Color.rgb(84, 126, 91),
            Color.rgb(198, 73, 91),
            Color.rgb(73, 124, 199)
    };

    private TextView tvTitle;
    private TextView tvIncomingInvite;
    private TextView tvOutgoingInvite;
    private TextView tvIncomingFriendRequest;
    private EditText etFriendUsername;
    private Button btnAcceptInvite;
    private Button btnDeclineInvite;
    private Button btnCancelInvite;
    private Button btnAcceptFriendRequest;
    private Button btnDeclineFriendRequest;
    private Button btnAddFriendByUsername;
    private Button btnShowMyQr;
    private Button btnScanFriendQr;
    private Button btnBack;
    private LinearLayout layoutFriendRequestActions;
    private RecyclerView rvUsers;

    private FirebaseManager firebaseManager;
    private FriendsRepository friendsRepository;
    private FriendlyInviteRepository inviteRepository;
    private FirebaseUser currentUser;
    private ListenerRegistration incomingListener;
    private ListenerRegistration outgoingListener;
    private ListenerRegistration friendRequestListener;
    private FriendAdapter adapter;
    private FriendlyInviteData currentInvite;
    private FriendsRepository.FriendRequest currentFriendRequest;
    private String outgoingInviteId;
    private String username;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable inviteTimeoutRunnable;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friendly_invite);

        firebaseManager = new FirebaseManager();
        friendsRepository = new FriendsRepository();
        inviteRepository = new FriendlyInviteRepository();
        currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za prijatelje.", Toast.LENGTH_SHORT).show();
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
        tvOutgoingInvite = findViewById(R.id.tvOutgoingInvite);
        tvIncomingFriendRequest = findViewById(R.id.tvIncomingFriendRequest);
        etFriendUsername = findViewById(R.id.etFriendUsername);
        btnAcceptInvite = findViewById(R.id.btnAcceptFriendlyInvite);
        btnDeclineInvite = findViewById(R.id.btnDeclineFriendlyInvite);
        btnCancelInvite = findViewById(R.id.btnCancelFriendlyInvite);
        btnAcceptFriendRequest = findViewById(R.id.btnAcceptFriendRequest);
        btnDeclineFriendRequest = findViewById(R.id.btnDeclineFriendRequest);
        btnAddFriendByUsername = findViewById(R.id.btnAddFriendByUsername);
        btnShowMyQr = findViewById(R.id.btnShowMyQr);
        btnScanFriendQr = findViewById(R.id.btnScanFriendQr);
        btnBack = findViewById(R.id.btnFriendlyBack);
        layoutFriendRequestActions = findViewById(R.id.layoutFriendRequestActions);
        rvUsers = findViewById(R.id.rvFriendlyUsers);
    }

    private void setupList() {
        adapter = new FriendAdapter();
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }

    private void bindActions() {
        btnBack.setOnClickListener(v -> finish());
        btnAcceptInvite.setOnClickListener(v -> acceptCurrentInvite());
        btnDeclineInvite.setOnClickListener(v -> declineCurrentInvite());
        btnCancelInvite.setOnClickListener(v -> cancelOutgoingInvite());
        btnAcceptFriendRequest.setOnClickListener(v -> acceptCurrentFriendRequest());
        btnDeclineFriendRequest.setOnClickListener(v -> declineCurrentFriendRequest());
        btnAddFriendByUsername.setOnClickListener(v -> addFriendByUsername());
        btnShowMyQr.setOnClickListener(v -> showMyQr());
        btnScanFriendQr.setOnClickListener(v -> scanFriendQr());
        renderNoInvite();
        renderNoOutgoingInvite();
        renderNoFriendRequest();
    }

    private void loadUser() {
        firebaseManager.loadUserData(currentUser.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String loadedUsername, String loadedRegion) {
                username = loadedUsername;
                runIfActive(() -> {
                    tvTitle.setText("Prijatelji");
                    listenIncomingInvites();
                    listenIncomingFriendRequests();
                    loadFriends();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void listenIncomingFriendRequests() {
        if (friendRequestListener != null) {
            friendRequestListener.remove();
        }
        friendRequestListener = friendsRepository.listenIncomingFriendRequest(currentUser.getUid(), new FriendsRepository.FriendRequestListener() {
            @Override
            public void onRequest(FriendsRepository.FriendRequest request) {
                runIfActive(() -> renderFriendRequest(request));
            }

            @Override
            public void onNone() {
                runIfActive(() -> renderNoFriendRequest());
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runIfActive(() -> renderInvite(invite));
            }

            @Override
            public void onNone() {
                runIfActive(() -> renderNoInvite());
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadFriends() {
        friendsRepository.listFriends(currentUser.getUid(), new FriendsRepository.FriendsCallback() {
            @Override
            public void onFriends(List<FriendsRepository.FriendSummary> friends) {
                runIfActive(() -> adapter.submit(friends));
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void addFriendByUsername() {
        String query = etFriendUsername.getText().toString().trim();
        btnAddFriendByUsername.setEnabled(false);
        friendsRepository.searchByUsername(currentUser.getUid(), query, new FriendsRepository.SearchCallback() {
            @Override
            public void onFound(FriendsRepository.FriendSummary user, boolean alreadyFriend) {
                if (alreadyFriend) {
                    runIfActive(() -> {
                        btnAddFriendByUsername.setEnabled(true);
                        Toast.makeText(FriendlyInviteActivity.this, "Igrac je vec u listi prijatelja.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                sendFriendRequest(user.uid, true);
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    btnAddFriendByUsername.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sendFriendRequest(String friendUid, boolean fromUsernameSearch) {
        friendsRepository.sendFriendRequest(currentUser.getUid(), friendUid, new FriendsRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runIfActive(() -> {
                    btnAddFriendByUsername.setEnabled(true);
                    if (fromUsernameSearch) {
                        etFriendUsername.setText("");
                    }
                    Toast.makeText(FriendlyInviteActivity.this, "Zahtev za prijateljstvo je poslat.", Toast.LENGTH_SHORT).show();
                    loadFriends();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    btnAddFriendByUsername.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showMyQr() {
        try {
            Bitmap qr = generateQrBitmap(friendsRepository.qrPayloadForUser(currentUser.getUid()), 720);
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(qr);
            imageView.setPadding(32, 32, 32, 32);
            new AlertDialog.Builder(this)
                    .setTitle("Moj QR")
                    .setView(imageView)
                    .setPositiveButton("Zatvori", null)
                    .show();
        } catch (WriterException e) {
            Toast.makeText(this, "QR nije mogao biti generisan.", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap generateQrBitmap(String payload, int size) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void scanFriendQr() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Skeniraj QR prijatelja");
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                friendsRepository.sendFriendRequestFromQrPayload(currentUser.getUid(), result.getContents(), new FriendsRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runIfActive(() -> {
                            Toast.makeText(FriendlyInviteActivity.this, "Zahtev za prijateljstvo je poslat.", Toast.LENGTH_SHORT).show();
                            loadFriends();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void renderInvite(FriendlyInviteData invite) {
        currentInvite = invite;
        tvIncomingInvite.setText(invite.inviterUsername + " vas poziva na prijateljsku partiju.");
        btnAcceptInvite.setVisibility(View.VISIBLE);
        btnDeclineInvite.setVisibility(View.VISIBLE);

        long delayMs = invite.expiresAt != null
                ? Math.max(0L, invite.expiresAt.toDate().getTime() - System.currentTimeMillis())
                : 10_000L;
        clearInviteTimeout();
        inviteTimeoutRunnable = () -> {
            if (!isActivityActive()) {
                return;
            }
            if (currentInvite != null && currentInvite.inviteId.equals(invite.inviteId)) {
                inviteRepository.expireInvite(invite.inviteId, null);
                renderNoInvite();
            }
        };
        handler.postDelayed(inviteTimeoutRunnable, delayMs);
    }

    private void renderNoInvite() {
        clearInviteTimeout();
        currentInvite = null;
        tvIncomingInvite.setText("Nema aktivnih poziva.");
        btnAcceptInvite.setVisibility(View.GONE);
        btnDeclineInvite.setVisibility(View.GONE);
    }

    private void renderOutgoingInvite(String inviteId) {
        outgoingInviteId = inviteId;
        tvOutgoingInvite.setText("Zahtev za partiju je poslat. Cekanje odgovora...");
        btnCancelInvite.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void renderNoOutgoingInvite() {
        outgoingInviteId = null;
        tvOutgoingInvite.setText("Nema poslatog zahteva.");
        btnCancelInvite.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void renderFriendRequest(FriendsRepository.FriendRequest request) {
        currentFriendRequest = request;
        tvIncomingFriendRequest.setText(request.requesterUsername + " zeli da vas doda za prijatelja.");
        layoutFriendRequestActions.setVisibility(View.VISIBLE);
        btnAcceptFriendRequest.setEnabled(true);
        btnDeclineFriendRequest.setEnabled(true);
    }

    private void renderNoFriendRequest() {
        currentFriendRequest = null;
        tvIncomingFriendRequest.setText("Nema zahteva za prijateljstvo.");
        layoutFriendRequestActions.setVisibility(View.GONE);
        btnAcceptFriendRequest.setEnabled(true);
        btnDeclineFriendRequest.setEnabled(true);
    }

    private void acceptCurrentFriendRequest() {
        if (currentFriendRequest == null) {
            return;
        }
        btnAcceptFriendRequest.setEnabled(false);
        btnDeclineFriendRequest.setEnabled(false);
        friendsRepository.acceptFriendRequest(currentUser.getUid(), currentFriendRequest.requestId, new FriendsRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runIfActive(() -> {
                    renderNoFriendRequest();
                    Toast.makeText(FriendlyInviteActivity.this, "Prijatelj je dodat.", Toast.LENGTH_SHORT).show();
                    loadFriends();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    btnAcceptFriendRequest.setEnabled(true);
                    btnDeclineFriendRequest.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void declineCurrentFriendRequest() {
        if (currentFriendRequest == null) {
            return;
        }
        btnAcceptFriendRequest.setEnabled(false);
        btnDeclineFriendRequest.setEnabled(false);
        friendsRepository.declineFriendRequest(currentUser.getUid(), currentFriendRequest.requestId, new FriendsRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runIfActive(() -> {
                    renderNoFriendRequest();
                    Toast.makeText(FriendlyInviteActivity.this, "Zahtev je odbijen.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    btnAcceptFriendRequest.setEnabled(true);
                    btnDeclineFriendRequest.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
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
                runIfActive(() -> {
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
                runIfActive(() -> {
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
                runIfActive(() -> renderNoInvite());
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendInvite(FriendsRepository.FriendSummary friend) {
        if (!friend.isAvailableForInvite()) {
            Toast.makeText(this, "Prijatelj trenutno nije dostupan za partiju.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (outgoingInviteId != null) {
            Toast.makeText(this, "Vec imate poslat zahtev za partiju.", Toast.LENGTH_SHORT).show();
            return;
        }

        inviteRepository.sendInvite(currentUser.getUid(), username, friend.uid, friend.username,
                new FriendlyInviteRepository.SendInviteCallback() {
                    @Override
                    public void onSuccess(String inviteId) {
                        runIfActive(() -> {
                            renderOutgoingInvite(inviteId);
                            listenOutgoingInvite(inviteId);
                            Toast.makeText(FriendlyInviteActivity.this,
                                    "Poziv poslat. Cekanje prihvatanja...", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runIfActive(() -> {
                    clearOutgoingInviteListener();
                    Intent intent = new Intent(FriendlyInviteActivity.this, PartyActivity.class);
                    intent.putExtra(PartyActivity.EXTRA_PARTY_ID, partyId);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onDeclined() {
                runIfActive(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Poziv je odbijen.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onExpired() {
                runIfActive(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Poziv je istekao.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onCancelled() {
                runIfActive(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Zahtev je prekinut.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPending() {
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void cancelOutgoingInvite() {
        if (outgoingInviteId == null) {
            return;
        }
        btnCancelInvite.setEnabled(false);
        inviteRepository.cancelInvite(outgoingInviteId, currentUser.getUid(), new FriendlyInviteRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runIfActive(() -> {
                    btnCancelInvite.setEnabled(true);
                    clearOutgoingInviteListener();
                    Toast.makeText(FriendlyInviteActivity.this, "Zahtev je prekinut.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> {
                    btnCancelInvite.setEnabled(true);
                    Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void clearOutgoingInviteListener() {
        renderNoOutgoingInvite();
        if (outgoingListener != null) {
            outgoingListener.remove();
            outgoingListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null && username != null) {
            listenIncomingInvites();
            listenIncomingFriendRequests();
            loadFriends();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        clearInviteTimeout();
        if (incomingListener != null) {
            incomingListener.remove();
        }
        if (outgoingListener != null) {
            outgoingListener.remove();
        }
        if (friendRequestListener != null) {
            friendRequestListener.remove();
        }
    }

    private void clearInviteTimeout() {
        if (inviteTimeoutRunnable != null) {
            handler.removeCallbacks(inviteTimeoutRunnable);
            inviteTimeoutRunnable = null;
        }
    }

    private void runIfActive(Runnable action) {
        runOnUiThread(() -> {
            if (!isActivityActive()) {
                return;
            }
            action.run();
        });
    }

    private boolean isActivityActive() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    private final class FriendAdapter extends RecyclerView.Adapter<FriendViewHolder> {
        private final List<FriendsRepository.FriendSummary> friends = new ArrayList<>();

        void submit(List<FriendsRepository.FriendSummary> newFriends) {
            friends.clear();
            friends.addAll(newFriends);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friendly_user, parent, false);
            return new FriendViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
            FriendsRepository.FriendSummary friend = friends.get(position);
            holder.tvAvatar.setText(extractInitials(friend.username));
            applyAvatarTheme(holder.tvAvatar, friend.avatarTheme,
                    friend.avatarFrameRank, friend.avatarFrameCycleMonth);
            holder.tvUsername.setText(friend.username);
            holder.tvStats.setText(String.format(Locale.getDefault(),
                    "Rang: %s | Zvezde: %d | %s",
                    friend.monthlyRank > 0 ? "#" + friend.monthlyRank : "-",
                    friend.stars,
                    friend.leagueName()));
            holder.tvStatus.setText(resolveFriendStatus(friend));
            boolean canInvite = friend.isAvailableForInvite() && outgoingInviteId == null;
            holder.btnInvite.setEnabled(canInvite);
            holder.btnInvite.setText(outgoingInviteId == null ? "Pozovi" : "Cekanje");
            holder.btnInvite.setOnClickListener(v -> sendInvite(friend));
            holder.btnRemove.setOnClickListener(v -> confirmRemoveFriend(friend));
        }

        @Override
        public int getItemCount() {
            return friends.size();
        }
    }

    private static final class FriendViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvUsername;
        final TextView tvStats;
        final TextView tvStatus;
        final Button btnInvite;
        final Button btnRemove;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvFriendlyUserAvatar);
            tvUsername = itemView.findViewById(R.id.tvFriendlyUserName);
            tvStats = itemView.findViewById(R.id.tvFriendlyUserStats);
            tvStatus = itemView.findViewById(R.id.tvFriendlyUserStatus);
            btnInvite = itemView.findViewById(R.id.btnInviteFriendlyUser);
            btnRemove = itemView.findViewById(R.id.btnRemoveFriendlyUser);
        }
    }

    private void confirmRemoveFriend(FriendsRepository.FriendSummary friend) {
        new AlertDialog.Builder(this)
                .setTitle("Obrisi prijatelja")
                .setMessage("Da li zelite da obrisete " + friend.username + " iz liste prijatelja?")
                .setNegativeButton("Odustani", null)
                .setPositiveButton("Obrisi", (dialog, which) -> removeFriend(friend))
                .show();
    }

    private void removeFriend(FriendsRepository.FriendSummary friend) {
        friendsRepository.removeFriend(currentUser.getUid(), friend.uid, new FriendsRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runIfActive(() -> {
                    Toast.makeText(FriendlyInviteActivity.this, "Prijatelj je obrisan.", Toast.LENGTH_SHORT).show();
                    loadFriends();
                });
            }

            @Override
            public void onError(String message) {
                runIfActive(() -> Toast.makeText(FriendlyInviteActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String resolveFriendStatus(FriendsRepository.FriendSummary friend) {
        if (!friend.isLoggedIn) {
            return "Nije ulogovan";
        }
        if (!friend.isAvailableForInvite()) {
            return "U partiji";
        }
        return "Dostupan";
    }

    private void applyAvatarTheme(TextView avatarView, int avatarTheme,
                                  int avatarFrameRank, String avatarFrameCycleMonth) {
        int safeIndex = Math.max(0, Math.min(avatarTheme, AVATAR_COLORS.length - 1));
        RegionAvatarFrameHelper.apply(avatarView, AVATAR_COLORS[safeIndex],
                avatarFrameRank, avatarFrameCycleMonth);
    }

    private String extractInitials(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "IG";
        }

        String cleaned = username.trim();
        if (cleaned.length() == 1) {
            return cleaned.toUpperCase(Locale.getDefault());
        }
        return cleaned.substring(0, 2).toUpperCase(Locale.getDefault());
    }
}
