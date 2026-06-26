package com.example.slagalica.challenge;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.auth.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;
import java.util.Map;

public class ChallengeDetailActivity extends AppCompatActivity {

    private static final String[] GAME_LABELS = {
            "Ko zna zna",
            "Spojnice",
            "Asocijacije",
            "Skocko",
            "Korak po korak",
            "Moj broj"
    };

    private TextView tvTitle;
    private TextView tvInfo;
    private TextView tvParticipants;
    private TextView tvResult;
    private LinearLayout scoreInputs;
    private EditText[] scoreFields;
    private Button btnPrimary;
    private Button btnSubmitScore;
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
        btnSubmitScore.setOnClickListener(v -> submitSoloScore());
        loadUserAndListen();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvChallengeDetailTitle);
        tvInfo = findViewById(R.id.tvChallengeInfo);
        tvParticipants = findViewById(R.id.tvChallengeParticipants);
        tvResult = findViewById(R.id.tvChallengeResult);
        scoreInputs = findViewById(R.id.scoreInputs);
        btnPrimary = findViewById(R.id.btnChallengePrimary);
        btnSubmitScore = findViewById(R.id.btnSubmitChallengeScore);
        btnBack = findViewById(R.id.btnChallengeDetailBack);

        scoreFields = new EditText[]{
                findViewById(R.id.etScoreKoZnaZna),
                findViewById(R.id.etScoreSpojnice),
                findViewById(R.id.etScoreAsocijacije),
                findViewById(R.id.etScoreSkocko),
                findViewById(R.id.etScoreKorak),
                findViewById(R.id.etScoreMojBroj)
        };
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
        boolean scored = challenge.hasScore(currentUser.getUid());
        boolean creator = currentUser.getUid().equals(challenge.creatorId);

        scoreInputs.setVisibility(View.GONE);
        btnSubmitScore.setVisibility(View.GONE);
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
            if (participant && !scored) {
                btnPrimary.setVisibility(View.GONE);
                scoreInputs.setVisibility(View.VISIBLE);
                btnSubmitScore.setVisibility(View.VISIBLE);
            } else {
                btnPrimary.setText(scored ? "Rezultat poslat" : "Niste ucesnik");
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

    private void submitSoloScore() {
        int total = 0;
        for (int i = 0; i < scoreFields.length; i++) {
            String raw = scoreFields[i].getText().toString().trim();
            if (raw.isEmpty()) {
                scoreFields[i].setError(GAME_LABELS[i] + " score je obavezan");
                return;
            }
            total += parseInt(raw);
        }

        btnSubmitScore.setEnabled(false);
        challengeRepository.submitScore(challengeId, currentUser.getUid(), total, new ChallengeRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSubmitScore.setEnabled(true);
                    Toast.makeText(ChallengeDetailActivity.this, "Rezultat poslat.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSubmitScore.setEnabled(true);
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
            builder.append("- ")
                    .append(entry.getValue())
                    .append(challenge.scores.get(entry.getKey()) instanceof Number
                            ? " (" + challenge.scores.get(entry.getKey()) + ")"
                            : "")
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String buildResultText(ChallengeData challenge) {
        String winnerName = challenge.participantName(challenge.winnerId);
        String secondName = challenge.secondPlaceId != null ? challenge.participantName(challenge.secondPlaceId) : "-";
        return "Pobednik: " + winnerName + "\nDrugo mesto: " + secondName;
    }

    private int parseInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            return 0;
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
