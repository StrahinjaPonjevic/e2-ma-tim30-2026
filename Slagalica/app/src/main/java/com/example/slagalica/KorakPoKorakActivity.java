package com.example.slagalica;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.auth.SessionManager;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KorakPoKorakActivity extends AppCompatActivity {

    private static final int ROUND_DURATION = 70;
    private static final int STEAL_DURATION = 10;
    private static final int STEP_INTERVAL = 10;
    private static final int TOTAL_STEPS = 7;

    private TextView tvPlayer1Name, tvPlayer1Score, tvPlayer2Name, tvPlayer2Score;
    private TextView tvRoundLabel, tvTimer;
    private CircularProgressIndicator progressTimer;
    private TextView tvTurnInfo, tvCurrentPoints;
    private TextView[] stepViews;
    private EditText etAnswer;
    private Button btnConfirmAnswer, btnForfeit;

    private FirebaseManager firebaseManager;
    private SessionManager sessionManager;
    private ProfileStatsUpdater profileStatsUpdater;
    private KPKFirestoreRepository kpkRepository;
    private FirebaseFirestore db;
    private String sessionId;
    private boolean isOwner;
    private boolean gameInitialized = false;
    private CountDownTimer countDownTimer;
    private com.google.firebase.firestore.ListenerRegistration gameListener;

    private String currentPhase;
    private int currentRound = 1;
    private int ownerScore = 0;
    private int guestScore = 0;
    private String ownerId, guestId;
    private String[] cluesRound1, cluesRound2;
    private String answerRound1, answerRound2;
    private final int[] ownerStepHits = new int[TOTAL_STEPS];
    private final int[] guestStepHits = new int[TOTAL_STEPS];
    private boolean isMyTurn = false;
    private boolean isStealTurn = false;
    private boolean gameFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        sessionId = getIntent().getStringExtra("sessionId");
        isOwner = getIntent().getBooleanExtra("isOwner", true);

        firebaseManager = new FirebaseManager();
        sessionManager = new SessionManager();
        profileStatsUpdater = new ProfileStatsUpdater();
        kpkRepository = new KPKFirestoreRepository();
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupHeader();

        if (sessionId != null) {
            resolveSessionIds();
        } else {
            Toast.makeText(this, "Sesija nije pronađena", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void resolveSessionIds() {
        db.collection("sessions").document(sessionId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        ownerId = snapshot.getString("ownerId");
                        guestId = snapshot.getString("guestId");
                    }
                    listenForGameData();
                })
                .addOnFailureListener(e -> {
                    listenForGameData();
                });
    }

    private void bindViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);
        tvRoundLabel = findViewById(R.id.tvRoundLabel);
        tvTimer = findViewById(R.id.tvTimer);
        progressTimer = findViewById(R.id.progressTimer);
        tvTurnInfo = findViewById(R.id.tvTurnInfo);
        tvCurrentPoints = findViewById(R.id.tvCurrentPoints);

        stepViews = new TextView[TOTAL_STEPS];
        stepViews[0] = findViewById(R.id.tvStep0);
        stepViews[1] = findViewById(R.id.tvStep1);
        stepViews[2] = findViewById(R.id.tvStep2);
        stepViews[3] = findViewById(R.id.tvStep3);
        stepViews[4] = findViewById(R.id.tvStep4);
        stepViews[5] = findViewById(R.id.tvStep5);
        stepViews[6] = findViewById(R.id.tvStep6);

        etAnswer = findViewById(R.id.etAnswer);
        btnConfirmAnswer = findViewById(R.id.btnConfirmAnswer);
        btnForfeit = findViewById(R.id.btnForfeit);

        btnConfirmAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitAnswer();
            }
        });

        btnForfeit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                forfeitGame();
            }
        });

        disableAnswerInput();
    }

    private void setupHeader() {
        tvPlayer1Name.setText("Igrač 1");
        tvPlayer2Name.setText("Igrač 2");
        tvRoundLabel.setText("RUNDA 1/2 — KORAK PO KORAK");
        tvTimer.setText(String.valueOf(ROUND_DURATION));
        progressTimer.setMax(ROUND_DURATION);
        progressTimer.setProgress(ROUND_DURATION);
    }

    private void listenForGameData() {
        gameListener = db.collection("games").document(sessionId)
                .addSnapshotListener(new com.google.firebase.firestore.EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(DocumentSnapshot snapshot,
                                        com.google.firebase.firestore.FirebaseFirestoreException e) {
                        if (e != null) {
                            if (!gameInitialized && isOwner) {
                                initializeGame();
                            }
                            return;
                        }

                        if (snapshot == null || !snapshot.exists()) {
                            if (!gameInitialized && isOwner) {
                                initializeGame();
                            }
                            return;
                        }

                        gameInitialized = true;
                        String phase = snapshot.getString("phase");
                        if (phase != null) {
                            currentPhase = phase;
                        }
                        currentRound = snapshot.getLong("currentRound") != null
                                ? snapshot.getLong("currentRound").intValue() : 1;
                        ownerScore = snapshot.getLong("ownerScore") != null
                                ? snapshot.getLong("ownerScore").intValue() : 0;
                        guestScore = snapshot.getLong("guestScore") != null
                                ? snapshot.getLong("guestScore").intValue() : 0;

                        cluesRound1 = castStringList(snapshot.get("cluesRound1"));
                        cluesRound2 = castStringList(snapshot.get("cluesRound2"));
                        answerRound1 = snapshot.getString("answerRound1");
                        answerRound2 = snapshot.getString("answerRound2");
                        for (int i = 0; i < TOTAL_STEPS; i++) {
                            ownerStepHits[i] = snapshot.getLong("ownerStep" + (i + 1) + "Hits") != null
                                    ? snapshot.getLong("ownerStep" + (i + 1) + "Hits").intValue() : 0;
                            guestStepHits[i] = snapshot.getLong("guestStep" + (i + 1) + "Hits") != null
                                    ? snapshot.getLong("guestStep" + (i + 1) + "Hits").intValue() : 0;
                        }
                        if (snapshot.getString("ownerId") != null) ownerId = snapshot.getString("ownerId");
                        if (snapshot.getString("guestId") != null) guestId = snapshot.getString("guestId");

                        gameFinished = snapshot.getBoolean("gameFinished") != null
                                && snapshot.getBoolean("gameFinished");

                        updateUIForPhase();
                        updateScoreDisplay();

                        if (gameFinished) {
                            String winner = snapshot.getString("winner");
                            showGameResult(winner);
                        }
                    }
                });
    }

    private void initializeGame() {
        tvTurnInfo.setText("Priprema podataka iz baze...");
        enableAnswerInput(false);

        kpkRepository.loadQuestionSets(2, new KPKFirestoreRepository.LoadSetsCallback() {
            @Override
            public void onSuccess(final List<KPKQuestionData.QuestionSet> loadedSets) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        KPKQuestionData.QuestionSet round1Set = loadedSets.get(0);
                        KPKQuestionData.QuestionSet round2Set = loadedSets.get(1);

                        Map<String, Object> gameData = new HashMap<>();
                        gameData.put("sessionId", sessionId);
                        gameData.put("gameType", "korak_po_korak");
                        gameData.put("ownerId", ownerId != null ? ownerId : "");
                        gameData.put("guestId", guestId != null ? guestId : "");
                        gameData.put("cluesRound1", java.util.Arrays.asList(round1Set.clues));
                        gameData.put("answerRound1", round1Set.answer);
                        gameData.put("cluesRound2", java.util.Arrays.asList(round2Set.clues));
                        gameData.put("answerRound2", round2Set.answer);
                        gameData.put("currentRound", 1);
                        gameData.put("phase", "waiting_for_owner");
                        gameData.put("ownerScore", 0);
                        gameData.put("guestScore", 0);
                        for (int i = 0; i < TOTAL_STEPS; i++) {
                            gameData.put("ownerStep" + (i + 1) + "Hits", 0);
                            gameData.put("guestStep" + (i + 1) + "Hits", 0);
                        }
                        gameData.put("gameFinished", false);
                        gameData.put("phaseStartedAt", FieldValue.serverTimestamp());

                        db.collection("games").document(sessionId)
                                .set(gameData)
                                .addOnSuccessListener(aVoid -> {
                                    if (isOwner) {
                                        startRound1();
                                    }
                                });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(KorakPoKorakActivity.this, message, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void startRound1() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentRound", 1);
        updates.put("phase", "owner_playing");
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        db.collection("games").document(sessionId).update(updates);
    }

    private void updateUIForPhase() {
        if (currentPhase == null) return;

        boolean ownerIsMe = isOwner;

        switch (currentPhase) {
            case "waiting_for_owner":
                if (isOwner) {
                    tvTurnInfo.setText("Pritisnite Start za početak");
                    enableAnswerInput(false);
                } else {
                    tvTurnInfo.setText("Čekajte da protivnik započne igru...");
                    enableAnswerInput(false);
                }
                break;

            case "owner_playing":
                currentRound = 1;
                tvRoundLabel.setText("RUNDA 1/2 — KORAK PO KORAK");
                if (ownerIsMe) {
                    tvTurnInfo.setText("Tvoj red! Pogodi pojam.");
                    enableAnswerInput(true);
                    isMyTurn = true;
                    isStealTurn = false;
                } else {
                    tvTurnInfo.setText("Protivnik pogađa...");
                    enableAnswerInput(false);
                    isMyTurn = false;
                    isStealTurn = false;
                }
                startLocalTimer(ROUND_DURATION);
                break;

            case "guest_steal":
                if (!ownerIsMe) {
                    tvTurnInfo.setText("Krađa! Pogodi za 5 bodova!");
                    enableAnswerInput(true);
                    isMyTurn = true;
                    isStealTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik krade...");
                    enableAnswerInput(false);
                    isMyTurn = false;
                    isStealTurn = false;
                }
                startLocalTimer(STEAL_DURATION);
                break;

            case "guest_playing":
                currentRound = 2;
                tvRoundLabel.setText("RUNDA 2/2 — KORAK PO KORAK");
                if (!ownerIsMe) {
                    tvTurnInfo.setText("Tvoj red! Pogodi pojam.");
                    enableAnswerInput(true);
                    isMyTurn = true;
                    isStealTurn = false;
                } else {
                    tvTurnInfo.setText("Protivnik pogađa...");
                    enableAnswerInput(false);
                    isMyTurn = false;
                    isStealTurn = false;
                }
                startLocalTimer(ROUND_DURATION);
                break;

            case "owner_steal":
                if (ownerIsMe) {
                    tvTurnInfo.setText("Krađa! Pogodi za 5 bodova!");
                    enableAnswerInput(true);
                    isMyTurn = true;
                    isStealTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik krade...");
                    enableAnswerInput(false);
                    isMyTurn = false;
                    isStealTurn = false;
                }
                startLocalTimer(STEAL_DURATION);
                break;

            case "guest_steal_done":
                startRound2();
                break;

            case "round1_done":
                startRound2();
                break;

            case "round2_done":
                determineWinner();
                break;
        }

        updateStepDisplay(0);
    }

    private void startRound2() {
        if (!isOwner) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentRound", 2);
        updates.put("phase", "guest_playing");
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        db.collection("games").document(sessionId).update(updates);
    }

    private void startLocalTimer(int durationSeconds) {
        if (countDownTimer != null) countDownTimer.cancel();

        final long totalMillis = durationSeconds * 1000L;
        progressTimer.setMax(durationSeconds);
        progressTimer.setProgress(durationSeconds);

        countDownTimer = new CountDownTimer(totalMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long elapsed = (totalMillis - millisUntilFinished) / 1000;
                int step = (int) Math.min(elapsed / STEP_INTERVAL, TOTAL_STEPS - 1);
                updateStepDisplay(step);
                updateTimerDisplay(millisUntilFinished / 1000);
                updatePointsDisplay(step);
            }

            @Override
            public void onFinish() {
                updateStepDisplay(TOTAL_STEPS - 1);
                updateTimerDisplay(0);
                handleTimerExpiry();
            }
        }.start();
    }

    private void updateTimerDisplay(long seconds) {
        tvTimer.setText(String.valueOf(seconds));
        progressTimer.setProgress((int) seconds);
    }

    private void updateStepDisplay(int step) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            if (i <= step) {
                String clue = getClueForCurrentRound(i);
                if (clue != null && !clue.isEmpty()) {
                    stepViews[i].setText(clue);
                    stepViews[i].setVisibility(View.VISIBLE);
                }
            } else {
                stepViews[i].setVisibility(View.GONE);
            }
        }
    }

    private String getClueForCurrentRound(int stepIndex) {
        String[] clues = (currentRound == 1) ? cluesRound1 : cluesRound2;
        if (clues != null && stepIndex < clues.length) {
            return clues[stepIndex];
        }
        return "";
    }

    private void updatePointsDisplay(int step) {
        if (isMyTurn && !isStealTurn) {
            int points = calculatePoints(step);
            tvCurrentPoints.setText("Mogući bodovi: " + points);
        } else if (isMyTurn && isStealTurn) {
            tvCurrentPoints.setText("Krađa: +5 bodova");
        } else {
            tvCurrentPoints.setText("");
        }
    }

    private int calculatePoints(int step) {
        return Math.max(0, 20 - (step * 2));
    }

    private void updateScoreDisplay() {
        tvPlayer1Score.setText(ownerScore + " bodova");
        tvPlayer2Score.setText(guestScore + " bodova");
    }

    private void submitAnswer() {
        if (!isMyTurn || gameFinished) return;

        String answer = etAnswer.getText().toString().trim();
        if (answer.isEmpty()) {
            etAnswer.setError("Unesite odgovor");
            return;
        }

        String correctAnswer = getCurrentCorrectAnswer();

        if (answer.equalsIgnoreCase(correctAnswer)) {
            if (countDownTimer != null) countDownTimer.cancel();
            enableAnswerInput(false);
            handleCorrectAnswer();
        } else {
            Toast.makeText(this, "Netačan odgovor!", Toast.LENGTH_SHORT).show();
            if (isStealTurn) {
                if (countDownTimer != null) countDownTimer.cancel();
                enableAnswerInput(false);
                advanceAfterFailedSteal();
            } else {
                int currentStep = getCurrentStep();
                if (currentStep >= TOTAL_STEPS - 1) {
                    if (countDownTimer != null) countDownTimer.cancel();
                    enableAnswerInput(false);
                    handleTimerExpiry();
                } else {
                    etAnswer.setText("");
                    etAnswer.setEnabled(true);
                    btnConfirmAnswer.setEnabled(true);
                    etAnswer.requestFocus();
                }
            }
        }
    }

    private String getCurrentCorrectAnswer() {
        return (currentRound == 1) ? answerRound1 : answerRound2;
    }

    private void handleCorrectAnswer() {
        int points;
        String nextPhase;

        if (isStealTurn) {
            points = 5;
            nextPhase = (currentRound == 1) ? "guest_steal_done" : "round2_done";
            Toast.makeText(this, "Tačno! +5 bodova (krađa)", Toast.LENGTH_SHORT).show();
        } else {
            int step = getCurrentStep();
            points = calculatePoints(step);
            if (currentRound == 1) {
                nextPhase = "round1_done";
            } else {
                nextPhase = "round2_done";
            }
            Toast.makeText(this, "Tačno! +" + points + " bodova", Toast.LENGTH_SHORT).show();
        }

        final int pointsToAdd = points;
        final String phaseToSet = nextPhase;
        final int solvedStep = !isStealTurn ? getCurrentStep() : -1;

        DocumentReference gameRef = db.collection("games").document(sessionId);
        gameRef.get().addOnSuccessListener(snapshot -> {
            int currentOwner = snapshot.getLong("ownerScore") != null
                    ? snapshot.getLong("ownerScore").intValue() : 0;
            int currentGuest = snapshot.getLong("guestScore") != null
                    ? snapshot.getLong("guestScore").intValue() : 0;

            Map<String, Object> updates = new HashMap<>();
            if (isOwner) {
                updates.put("ownerScore", currentOwner + pointsToAdd);
            } else {
                updates.put("guestScore", currentGuest + pointsToAdd);
            }
            if (solvedStep >= 0) {
                String stepField = (isOwner ? "ownerStep" : "guestStep") + (solvedStep + 1) + "Hits";
                int currentStepValue = snapshot.getLong(stepField) != null
                        ? snapshot.getLong(stepField).intValue() : 0;
                updates.put(stepField, currentStepValue + 1);
            }
            updates.put("phase", phaseToSet);
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
            gameRef.update(updates);
        });
    }

    private void advanceAfterFailedSteal() {
        DocumentReference gameRef = db.collection("games").document(sessionId);
        String nextPhase = (currentRound == 1) ? "round1_done" : "round2_done";
        Map<String, Object> updates = new HashMap<>();
        updates.put("phase", nextPhase);
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        gameRef.update(updates);
    }

    private void handleTimerExpiry() {
        if (gameFinished) return;

        DocumentReference gameRef = db.collection("games").document(sessionId);
        String currentPhase = this.currentPhase;
        String nextPhase = null;

        if ("owner_playing".equals(currentPhase)) {
            nextPhase = "guest_steal";
        } else if ("guest_steal".equals(currentPhase)) {
            nextPhase = "round1_done";
        } else if ("guest_playing".equals(currentPhase)) {
            nextPhase = "owner_steal";
        } else if ("owner_steal".equals(currentPhase)) {
            nextPhase = "round2_done";
        }

        if (nextPhase != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("phase", nextPhase);
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
            gameRef.update(updates);
        }
    }

    private void determineWinner() {
        DocumentReference gameRef = db.collection("games").document(sessionId);
        gameRef.get().addOnSuccessListener(snapshot -> {
            int ownerSc = snapshot.getLong("ownerScore") != null
                    ? snapshot.getLong("ownerScore").intValue() : 0;
            int guestSc = snapshot.getLong("guestScore") != null
                    ? snapshot.getLong("guestScore").intValue() : 0;

            String winner;
            if (ownerSc > guestSc) winner = "owner";
            else if (guestSc > ownerSc) winner = "guest";
            else winner = "draw";

            Map<String, Object> updates = new HashMap<>();
            updates.put("gameFinished", true);
            updates.put("winner", winner);
            updates.put("phase", "finished");
            gameRef.update(updates).addOnSuccessListener(unused -> {
                if (ownerId != null && guestId != null) {
                    profileStatsUpdater.recordKorakPoKorak(
                            ownerId,
                            guestId,
                            ownerSc,
                            guestSc,
                            winner,
                            ownerStepHits,
                            guestStepHits
                    );
                }
            });
        });
    }

    private void showGameResult(String winner) {
        if (countDownTimer != null) countDownTimer.cancel();
        enableAnswerInput(false);

        String message;
        if (isOwner && "owner".equals(winner)) {
            message = "Pobedili ste! Rezultat: " + ownerScore + " : " + guestScore;
        } else if (!isOwner && "guest".equals(winner)) {
            message = "Pobedili ste! Rezultat: " + guestScore + " : " + ownerScore;
        } else if ("draw".equals(winner)) {
            message = "Nerešeno! Rezultat: " + ownerScore + " : " + guestScore;
        } else {
            message = "Izgubili ste. Rezultat: " + (isOwner ? ownerScore : guestScore)
                    + " : " + (isOwner ? guestScore : ownerScore);
        }

        tvTurnInfo.setText(message);
        btnForfeit.setText("Zatvori");
        btnForfeit.setOnClickListener(v -> finish());
    }

    private void forfeitGame() {
        if (countDownTimer != null) countDownTimer.cancel();
        gameFinished = true;

        String winner = isOwner ? "guest" : "owner";
        Map<String, Object> updates = new HashMap<>();
        updates.put("gameFinished", true);
        updates.put("winner", winner);
        updates.put("phase", "finished");
        db.collection("games").document(sessionId).update(updates);

        Toast.makeText(this, "Odustali ste.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int getCurrentStep() {
        for (int i = TOTAL_STEPS - 1; i >= 0; i--) {
            if (stepViews[i].getVisibility() == View.VISIBLE) {
                return i;
            }
        }
        return 0;
    }

    private void enableAnswerInput(boolean enabled) {
        etAnswer.setEnabled(enabled);
        btnConfirmAnswer.setEnabled(enabled);
        if (enabled) {
            etAnswer.requestFocus();
        } else {
            etAnswer.setText("");
        }
    }

    private void disableAnswerInput() {
        etAnswer.setEnabled(false);
        btnConfirmAnswer.setEnabled(false);
    }

    private String[] castStringList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return list.toArray(new String[0]);
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (gameListener != null) gameListener.remove();
    }
}
