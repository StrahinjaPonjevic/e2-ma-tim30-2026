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
import com.example.slagalica.MojBrojActivity;
import com.example.slagalica.R;
import com.example.slagalica.SkockoActivity;
import com.example.slagalica.SpojniceActivity;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class PartyActivity extends AppCompatActivity {

    public static final String EXTRA_PARTY_ID = "partyId";
    public static final String EXTRA_QUEUE_WAITING = "queueWaiting";

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
    private String launchedGameDocId;
    private PartyData latestParty;

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
                        + " | Liga: " + resolveLeague(profile.stars)));
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
        partyListener = partyRepository.listenOwnedInProgressParty(currentUser.getUid(), new PartyRepository.PartyListener() {
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
        tvTitle.setText(party.isFriendly() ? "Prijateljska partija" : "Regularna partija");
        tvPlayers.setText(party.ownerUsername + " vs " + party.guestUsername);
        tvTotalScore.setText("Ukupno: " + party.ownerTotalScore + " : " + party.guestTotalScore);

        if (PartyData.STATUS_IN_PROGRESS.equals(party.status)) {
            tvCurrentGame.setText("Igra " + (party.currentGameIndex + 1) + "/"
                    + PartyData.GAME_KEYS.length + ": " + PartyData.displayNameForGame(party.currentGameKey));
            btnForfeit.setEnabled(true);
            btnForfeit.setText("Odustani");
            btnPrimary.setText("Nastavi igru");

            if (isSoloSubmittedAndWaiting(party)) {
                tvStatus.setText("Rezultat je poslat. Cekanje protivnika za sledecu igru.");
                return;
            }

            tvStatus.setText("Pokretanje trenutne igre...");
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
        intent.putExtra("countsForStats", party.countsForStats);
        intent.putExtra("isOwner", party.isOwner(currentUser.getUid()));
        startActivity(intent);
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

    private String resolveLeague(int stars) {
        if (stars >= 200) {
            return "Zlatna";
        }
        if (stars >= 100) {
            return "Srebrna";
        }
        return "Bronzana";
    }

    @Override
    protected void onResume() {
        super.onResume();
        launchedGameDocId = null;
        loadProfileLine();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (partyListener != null) {
            partyListener.remove();
        }
    }
}
