package com.example.slagalica.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
import com.example.slagalica.party.PartyData;
import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;
import java.util.Map;

public class ChallengeDetailActivity extends AppCompatActivity {

    private TextView tvTitle;
    private TextView tvInfo;
    private TextView tvParticipants;
    private TextView tvResult;
    private LinearLayout scoreInputs;
    private Button btnPrimary;
    private Button btnPlayNextGame;
    private Button btnBack;

    private FirebaseManager firebaseManager;
    private ChallengeRepository challengeRepository;
    private FirebaseUser currentUser;
    private ListenerRegistration listenerRegistration;
    private String challengeId;
    private String username;
    private ChallengeData latestChallenge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_detail);

        challengeId = getIntent().getStringExtra("challengeId");
        firebaseManager = new FirebaseManager();
        challengeRepository = new ChallengeRepository();
        currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous() || challengeId == null) {
            Toast.makeText(this, "Izazov nije dostupan.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        btnBack.setOnClickListener(v -> finish());
        btnPrimary.setOnClickListener(v -> handlePrimaryAction());
        btnPlayNextGame.setOnClickListener(v -> openNextChallengeGame());
        loadUserAndListen();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvChallengeDetailTitle);
        tvInfo = findViewById(R.id.tvChallengeInfo);
        tvParticipants = findViewById(R.id.tvChallengeParticipants);
        tvResult = findViewById(R.id.tvChallengeResult);
        scoreInputs = findViewById(R.id.scoreInputs);
        btnPrimary = findViewById(R.id.btnChallengePrimary);
        btnPlayNextGame = findViewById(R.id.btnSubmitChallengeScore);
        btnBack = findViewById(R.id.btnChallengeDetailBack);
    }

    private void loadUserAndListen() {
        firebaseManager.loadUserData(currentUser.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String loadedUsername, String region) {
                username = loadedUsername;
                listenChallenge();
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChallengeDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void listenChallenge() {
        listenerRegistration = challengeRepository.listenChallenge(challengeId, new ChallengeRepository.ChallengeListener() {
            @Override
            public void onChallenge(ChallengeData challenge) {
                runOnUiThread(() -> renderChallenge(challenge));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChallengeDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderChallenge(ChallengeData challenge) {
        latestChallenge = challenge;
        tvTitle.setText("Izazov - " + challenge.status);
        tvInfo.setText(String.format(Locale.getDefault(),
                "Region: %s | Ulog: %d zvezdi, %d tokena",
                challenge.region,
                challenge.starsStake,
                challenge.tokensStake));
        tvParticipants.setText(buildParticipantsText(challenge));

        boolean participant = challenge.hasParticipant(currentUser.getUid());
        boolean completed = challenge.hasCompletedRun(currentUser.getUid());
        boolean creator = currentUser.getUid().equals(challenge.creatorId);

        scoreInputs.setVisibility(View.GONE);
        btnPlayNextGame.setVisibility(View.GONE);
        btnPrimary.setVisibility(View.VISIBLE);

        if (ChallengeData.STATUS_OPEN.equals(challenge.status)) {
            tvResult.setText("Cekanje ucesnika. Start je moguc sa 2 do 4 ucesnika.");
            if (!participant) {
                btnPrimary.setText("Prihvati izazov");
                btnPrimary.setEnabled(challenge.participants.size() < 4);
            } else if (creator) {
                btnPrimary.setText("Startuj izazov");
                btnPrimary.setEnabled(challenge.participants.size() >= 2);
            } else {
                btnPrimary.setText("Cekanje starta");
                btnPrimary.setEnabled(false);
            }
            return;
        }

        if (ChallengeData.STATUS_IN_PROGRESS.equals(challenge.status)) {
            tvResult.setText("Izazov je u toku.");
            if (participant && !completed) {
                String nextGameKey = challenge.currentGameKey(currentUser.getUid());
                btnPrimary.setVisibility(View.GONE);
                btnPlayNextGame.setVisibility(View.VISIBLE);
                btnPlayNextGame.setEnabled(nextGameKey != null);
                btnPlayNextGame.setText("Odigraj: " + PartyData.displayNameForGame(nextGameKey));
            } else {
                btnPrimary.setText(completed ? "Rezultat poslat" : "Niste ucesnik");
                btnPrimary.setEnabled(false);
            }
            return;
        }

        btnPrimary.setText("Zavrseno");
        btnPrimary.setEnabled(false);
        tvResult.setText(buildResultText(challenge));
    }

    private void handlePrimaryAction() {
        if (latestChallenge == null) {
            return;
        }

        if (!latestChallenge.hasParticipant(currentUser.getUid())) {
            challengeRepository.acceptChallenge(challengeId, currentUser.getUid(), username, callback());
        } else if (currentUser.getUid().equals(latestChallenge.creatorId)) {
            challengeRepository.startChallenge(challengeId, currentUser.getUid(), callback());
        }
    }

    private void openNextChallengeGame() {
        if (latestChallenge == null || !latestChallenge.hasParticipant(currentUser.getUid())) {
            return;
        }
        String gameKey = latestChallenge.currentGameKey(currentUser.getUid());
        Class<?> activityClass = activityForGame(gameKey);
        if (gameKey == null || activityClass == null) {
            Toast.makeText(this, "Igra nije dostupna.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPlayNextGame.setEnabled(false);
        challengeRepository.prepareGameSession(challengeId, currentUser.getUid(), username, gameKey,
                new ChallengeRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnPlayNextGame.setEnabled(true);
                    Intent intent = new Intent(ChallengeDetailActivity.this, activityClass);
                    String gameDocId = latestChallenge.gameDocId(currentUser.getUid(), gameKey);
                    intent.putExtra("sessionId", gameDocId);
                    intent.putExtra("gameDocId", gameDocId);
                    intent.putExtra("gameKey", gameKey);
                    intent.putExtra("isOwner", true);
                    intent.putExtra("countsForStats", false);
                    intent.putExtra("challengeMode", true);
                    intent.putExtra("challengeId", challengeId);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnPlayNextGame.setEnabled(true);
                    Toast.makeText(ChallengeDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private ChallengeRepository.OperationCallback callback() {
        return new ChallengeRepository.OperationCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChallengeDetailActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        };
    }

    private String buildParticipantsText(ChallengeData challenge) {
        StringBuilder builder = new StringBuilder("Ucesnici:\n");
        for (Map.Entry<String, Object> entry : challenge.participants.entrySet()) {
            String uid = entry.getKey();
            builder.append("- ")
                    .append(entry.getValue())
                    .append(challenge.hasCompletedRun(uid)
                            ? " (" + challenge.totalScore(uid) + ")"
                            : buildProgressSuffix(challenge, uid))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String buildResultText(ChallengeData challenge) {
        String winnerName = challenge.participantName(challenge.winnerId);
        String secondName = challenge.secondPlaceId != null ? challenge.participantName(challenge.secondPlaceId) : "-";
        return "Pobednik: " + winnerName + "\nDrugo mesto: " + secondName;
    }

    private String buildProgressSuffix(ChallengeData challenge, String uid) {
        if (!ChallengeData.STATUS_IN_PROGRESS.equals(challenge.status)) {
            return "";
        }
        int current = challenge.currentGameIndex(uid) + 1;
        return " (igra " + current + "/" + ChallengeData.GAME_KEYS.length + ")";
    }

    private Class<?> activityForGame(String gameKey) {
        if (gameKey == null) {
            return null;
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
