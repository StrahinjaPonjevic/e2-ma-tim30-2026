package com.example.slagalica.party;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.AsocijacijeActivity;
import com.example.slagalica.KoZnaZnaActivity;
import com.example.slagalica.KorakPoKorakActivity;
import com.example.slagalica.MainActivity;
import com.example.slagalica.MojBrojActivity;
import com.example.slagalica.R;
import com.example.slagalica.SkockoActivity;
import com.example.slagalica.SpojniceActivity;
import com.example.slagalica.leagues.LeagueNotificationRepository;
import com.example.slagalica.leagues.LeagueUiHelper;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class PartyActivity extends AppCompatActivity {

    public static final String EXTRA_PARTY_ID = "partyId";
    public static final String EXTRA_QUEUE_WAITING = "queueWaiting";
    public static final String EXTRA_QUEUE_STARTED_AT_MS = "queueStartedAtMs";
    private static final long FORFEIT_AUTO_CLEANUP_MS = 100_000L;

    private TextView tvTitle;
    private TextView tvProfileLine;
    private TextView tvPlayers;
    private TextView tvCurrentGame;
    private TextView tvTotalScore;
    private TextView tvStatus;
    private Button btnPrimary;
    private Button btnForfeit;

    private PartyRepository partyRepository;
    private UserProfileRepository profileRepository;
    private ListenerRegistration partyListener;
    private FirebaseUser currentUser;
    private String partyId;
    private boolean queueWaiting;
    private long queueStartedAtMs;
    private String launchedGameDocId;
    private PartyData latestParty;
    private Runnable autoCleanupRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_party);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za partiju.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        partyRepository = new PartyRepository();
        profileRepository = new UserProfileRepository();
        partyId = getIntent().getStringExtra(EXTRA_PARTY_ID);
        queueWaiting = getIntent().getBooleanExtra(EXTRA_QUEUE_WAITING, false);
        queueStartedAtMs = getIntent().getLongExtra(EXTRA_QUEUE_STARTED_AT_MS, 0L);

        bindViews();
        bindListeners();
        loadProfileLine();

        if (queueWaiting) {
            showQueueWaiting();
            listenForQueuedParty();
        } else if (partyId != null && !partyId.trim().isEmpty()) {
            listenParty(partyId);
        } else {
            Toast.makeText(this, "Partija nije pronadjena.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvPartyTitle);
        tvProfileLine = findViewById(R.id.tvPartyProfileLine);
        tvPlayers = findViewById(R.id.tvPartyPlayers);
        tvCurrentGame = findViewById(R.id.tvPartyCurrentGame);
        tvTotalScore = findViewById(R.id.tvPartyTotalScore);
        tvStatus = findViewById(R.id.tvPartyStatus);
        btnPrimary = findViewById(R.id.btnPartyPrimary);
        btnForfeit = findViewById(R.id.btnPartyForfeit);
    }

    private void bindListeners() {
        btnPrimary.setOnClickListener(v -> {
            if (queueWaiting) {
                cancelQueueAndFinish();
            } else if (latestParty != null && latestParty.hasCurrentUserForfeited(currentUser.getUid())) {
                Intent intent = new Intent(PartyActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else if (latestParty != null && PartyData.STATUS_IN_PROGRESS.equals(latestParty.status)) {
                openCurrentGame(latestParty);
            } else {
                finish();
            }
        });

        btnForfeit.setOnClickListener(v -> {
            if (latestParty == null) {
                finish();
                return;
            }
            partyRepository.forfeitParty(latestParty.partyId, currentUser.getUid(), new PartyRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> Toast.makeText(PartyActivity.this, "Odustali ste od partije.", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(PartyActivity.this, message, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    private void loadProfileLine() {
        profileRepository.loadProfile(currentUser.getUid(), new UserProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runOnUiThread(() -> tvProfileLine.setText("Tokeni: " + profile.tokens
                        + " | Zvezde: " + profile.stars
                        + " | Liga: " + LeagueUiHelper.displayNameForStars(profile.stars)));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> tvProfileLine.setText("Profil nije dostupan"));
            }
        });
    }

    private void showQueueWaiting() {
        tvTitle.setText("Trazenje protivnika");
        tvPlayers.setText("Cekanje igraca za regularnu partiju...");
        tvCurrentGame.setText("Partija pocinje cim se nadje protivnik.");
        tvTotalScore.setText("");
        tvStatus.setText("Mozete otkazati cekanje bez trosenja dodatnih tokena.");
        btnPrimary.setText("Otkazi cekanje");
        btnForfeit.setEnabled(false);
    }

    private void listenForQueuedParty() {
        partyListener = partyRepository.listenOwnedInProgressParty(currentUser.getUid(), queueStartedAtMs, new PartyRepository.PartyListener() {
            @Override
            public void onPartyChanged(PartyData party) {
                runOnUiThread(() -> {
                    queueWaiting = false;
                    partyId = party.partyId;
                    if (partyListener != null) {
                        partyListener.remove();
                    }
                    listenParty(party.partyId);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> tvStatus.setText(message));
            }
        });
    }

    private void listenParty(String id) {
        partyListener = partyRepository.listenParty(id, new PartyRepository.PartyListener() {
            @Override
            public void onPartyChanged(PartyData party) {
                runOnUiThread(() -> renderParty(party));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText(message);
                    Toast.makeText(PartyActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void renderParty(PartyData party) {
        latestParty = party;
        scheduleAutoCleanupIfNeeded(party);
        tvTitle.setText(party.isFriendly() ? "Prijateljska partija" : "Regularna partija");
        tvPlayers.setText(party.ownerUsername + " vs " + party.guestUsername);
        tvTotalScore.setText("Ukupno: " + party.ownerTotalScore + " : " + party.guestTotalScore);

        if (PartyData.STATUS_IN_PROGRESS.equals(party.status)) {
            tvCurrentGame.setText("Igra " + (party.currentGameIndex + 1) + "/"
                    + PartyData.GAME_KEYS.length + ": " + PartyData.displayNameForGame(party.currentGameKey));
            if (party.hasCurrentUserForfeited(currentUser.getUid())) {
                btnForfeit.setEnabled(false);
                btnPrimary.setEnabled(true);
                btnPrimary.setText("Nazad");
                tvStatus.setText("Odustali ste. Protivnik nastavlja partiju.");
                return;
            }

            btnForfeit.setEnabled(!party.hasForfeit());
            btnForfeit.setText(party.hasForfeit() ? "Protivnik je odustao" : "Odustani");
            btnPrimary.setEnabled(true);
            btnPrimary.setText("Nastavi igru");

            if (isSoloSubmittedAndWaiting(party)) {
                tvStatus.setText("Rezultat je poslat. Cekanje protivnika za sledecu igru.");
                return;
            }

            tvStatus.setText(party.hasForfeit()
                    ? "Protivnik je odustao. Nastavljate partiju bez cekanja."
                    : "Pokretanje trenutne igre...");
            openCurrentGame(party);
            return;
        }

        btnForfeit.setEnabled(false);
        btnPrimary.setText("Zatvori");
        if (PartyData.STATUS_FORFEITED.equals(party.status)) {
            tvCurrentGame.setText("Partija je predata.");
            tvStatus.setText(resolveWinnerText(party));
        } else {
            tvCurrentGame.setText("Partija je zavrsena.");
            tvStatus.setText(resolveWinnerText(party));
        }
    }

    private void scheduleAutoCleanupIfNeeded(PartyData party) {
        clearAutoCleanup();
        if (party == null
                || !PartyData.STATUS_IN_PROGRESS.equals(party.status)
                || !party.hasForfeit()) {
            return;
        }

        long referenceMs = 0L;
        if (party.updatedAt != null) {
            referenceMs = party.updatedAt.toDate().getTime();
        } else if (party.createdAt != null) {
            referenceMs = party.createdAt.toDate().getTime();
        }
        long elapsed = referenceMs > 0L ? Math.max(0L, System.currentTimeMillis() - referenceMs) : FORFEIT_AUTO_CLEANUP_MS;
        long delayMs = Math.max(0L, FORFEIT_AUTO_CLEANUP_MS - elapsed);
        String currentPartyId = party.partyId;

        autoCleanupRunnable = () -> partyRepository.cleanupInactiveForfeitedParty(
                currentPartyId,
                FORFEIT_AUTO_CLEANUP_MS,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
        tvStatus.postDelayed(autoCleanupRunnable, delayMs);
    }

    private void clearAutoCleanup() {
        if (autoCleanupRunnable != null) {
            tvStatus.removeCallbacks(autoCleanupRunnable);
            autoCleanupRunnable = null;
        }
    }

    private boolean isSoloSubmittedAndWaiting(PartyData party) {
        return isSoloIntegratedGame(party.currentGameKey)
                && party.hasCurrentUserSubmittedSoloScore(currentUser.getUid());
    }

    private void openCurrentGame(PartyData party) {
        if (party == null || !PartyData.STATUS_IN_PROGRESS.equals(party.status)) {
            return;
        }

        String gameDocId = party.gameDocId();
        if (gameDocId.equals(launchedGameDocId)) {
            return;
        }

        Class<?> activityClass = activityForGame(party.currentGameKey);
        if (activityClass == null) {
            tvStatus.setText("Igra nije podrzana: " + party.currentGameKey);
            return;
        }

        launchedGameDocId = gameDocId;
        Intent intent = new Intent(this, activityClass);
        intent.putExtra("sessionId", party.partyId);
        intent.putExtra("partyId", party.partyId);
        intent.putExtra("gameDocId", gameDocId);
        intent.putExtra("gameKey", party.currentGameKey);
        intent.putExtra("partyType", party.type);
        intent.putExtra("countsForStats", party.countsForStats && !party.hasForfeit());
        intent.putExtra("isOwner", resolvePlayAsOwner(party));
        startActivity(intent);
    }

    private boolean resolvePlayAsOwner(PartyData party) {
        boolean currentUserIsOwner = party.isOwner(currentUser.getUid());
        if (party.ownerForfeited && currentUser.getUid().equals(party.guestId)) {
            return true;
        }
        if (party.guestForfeited && currentUserIsOwner) {
            return true;
        }
        return currentUserIsOwner;
    }

    private Class<?> activityForGame(String gameKey) {
        switch (gameKey) {
            case "ko_zna_zna":
                return KoZnaZnaActivity.class;
            case "spojnice":
                return SpojniceActivity.class;
            case "asocijacije":
                return AsocijacijeActivity.class;
            case "skocko":
                return SkockoActivity.class;
            case "korak_po_korak":
                return KorakPoKorakActivity.class;
            case "moj_broj":
                return MojBrojActivity.class;
            default:
                return null;
        }
    }

    private boolean isSoloIntegratedGame(String gameKey) {
        return "asocijacije".equals(gameKey) || "skocko".equals(gameKey);
    }

    private String resolveWinnerText(PartyData party) {
        if ("draw".equals(party.winner)) {
            return "Nereseno. Finalni rezultat: " + party.ownerTotalScore + " : " + party.guestTotalScore;
        }
        if (party.winner == null) {
            return "Finalni rezultat: " + party.ownerTotalScore + " : " + party.guestTotalScore;
        }
        boolean iWon = party.winner.equals(currentUser.getUid());
        String prefix = iWon ? "Pobedili ste." : "Izgubili ste.";
        return prefix + " Finalni rezultat: " + party.ownerTotalScore + " : " + party.guestTotalScore;
    }

    private void cancelQueueAndFinish() {
        partyRepository.cancelQueue(currentUser.getUid(), new PartyRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> finish());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(PartyActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LeagueNotificationRepository.setCurrentActivity(this);
        launchedGameDocId = null;
        loadProfileLine();
    }

    @Override
    protected void onPause() {
        LeagueNotificationRepository.clearCurrentActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearAutoCleanup();
        if (partyListener != null) {
            partyListener.remove();
        }
    }
}
