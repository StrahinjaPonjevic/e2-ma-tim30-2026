package com.example.slagalica;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.auth.SessionManager;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity implements SensorEventListener {

    private static final int ROUND_DURATION = 60;
    private static final int AUTO_REVEAL_DELAY = 5;
    private static final int SHAKE_THRESHOLD = 12;
    private static final long SHAKE_SKIP_MS = 500;

    // Header views
    private TextView tvPlayer1Name, tvPlayer1Score, tvPlayer2Name, tvPlayer2Score;
    private TextView tvRoundLabel, tvTimer;
    private CircularProgressIndicator progressTimer;

    // Game views
    private TextView tvTurnInfo;
    private TextView tvTargetNumber, tvCurrentResult;
    private TextView tvExpressionValue;
    private Button[] numberButtons;
    private Button btnClearExpression, btnConfirmExpression, btnForfeit;
    private Button[] operatorButtons;

    // Firebase
    private FirebaseManager firebaseManager;
    private SessionManager sessionManager;
    private ProfileStatsUpdater profileStatsUpdater;
    private PartyRepository partyRepository;
    private FirebaseFirestore db;
    private String sessionId;
    private String partyId;
    private String gameDocId;
    private String gameKey;
    private boolean countsForStats = true;
    private String currentUserId;
    private boolean isOwner;
    private ListenerRegistration gameListener;
    private ListenerRegistration partyListener;
    private boolean returningToParty = false;

    // Game state
    private boolean gameInitialized = false;
    private boolean gameFinished = false;
    private boolean resultShown = false;
    private String currentPhase;
    private String previousPhase;
    private int currentRound = 1;
    private int ownerScore = 0;
    private int guestScore = 0;
    private String ownerId, guestId;
    private int targetRound1, targetRound2;
    private int[] numbersRound1, numbersRound2;
    private Integer ownerResult, guestResult;
    private int ownerExactHits = 0;
    private int guestExactHits = 0;
    private boolean isMyTurn = false;
    private boolean submittedThisRound = false;

    // Timers
    private CountDownTimer countDownTimer;

    // Expression
    private final List<ExpressionToken> expressionTokens = new ArrayList<>();

    // Shake
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private float lastX, lastY, lastZ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        sessionId = getIntent().getStringExtra("sessionId");
        partyId = getIntent().getStringExtra("partyId");
        gameDocId = getIntent().getStringExtra("gameDocId");
        gameKey = getIntent().getStringExtra("gameKey");
        countsForStats = getIntent().getBooleanExtra("countsForStats", true);
        isOwner = getIntent().getBooleanExtra("isOwner", true);
        if (gameDocId == null || gameDocId.trim().isEmpty()) {
            gameDocId = sessionId;
        }
        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "moj_broj";
        }

        firebaseManager = new FirebaseManager();
        sessionManager = new SessionManager();
        profileStatsUpdater = new ProfileStatsUpdater();
        partyRepository = new PartyRepository();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        bindViews();
        setupHeader();
        listenForPartyAdvance();

        if (sessionId != null) {
            resolveSessionIds();
        } else {
            Toast.makeText(this, "Sesija nije prona\u0111ena", Toast.LENGTH_SHORT).show();
            finish();
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
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
        tvTargetNumber = findViewById(R.id.tvTargetNumber);
        tvCurrentResult = findViewById(R.id.tvCurrentResult);
        tvExpressionValue = findViewById(R.id.tvExpressionValue);

        numberButtons = new Button[]{
                findViewById(R.id.btnNumber1),
                findViewById(R.id.btnNumber2),
                findViewById(R.id.btnNumber3),
                findViewById(R.id.btnNumber4),
                findViewById(R.id.btnNumber5),
                findViewById(R.id.btnNumber6)
        };

        operatorButtons = new Button[]{
                findViewById(R.id.btnOpPlus),
                findViewById(R.id.btnOpMinus),
                findViewById(R.id.btnOpMultiply),
                findViewById(R.id.btnOpDivide),
                findViewById(R.id.btnOpLeftBracket),
                findViewById(R.id.btnOpRightBracket)
        };

        btnClearExpression = findViewById(R.id.btnClearExpression);
        btnConfirmExpression = findViewById(R.id.btnConfirmExpression);
        btnForfeit = findViewById(R.id.btnForfeit);

        bindNumberButtons();
        bindOperatorButtons();

        btnClearExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeLastToken();
            }
        });

        btnConfirmExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleConfirmClick();
            }
        });

        btnConfirmExpression.setEnabled(false);
        btnForfeit.setOnClickListener(v -> {
            if (partyId != null) {
                forfeitParty();
            } else {
                finish();
            }
        });

        setInputEnabled(false);
    }

    private void bindNumberButtons() {
        for (final Button btn : numberButtons) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isMyTurn) return;

                    if (appendToExpression(btn.getText().toString(), btn)) {
                        setButtonUsed(btn);
                    }
                }
            });
        }
    }

    private void bindOperatorButtons() {
        for (final Button btn : operatorButtons) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isMyTurn) return;
                    appendToExpression(btn.getText().toString(), null);
                }
            });
        }
    }

    private void setupHeader() {
        tvPlayer1Name.setText("Igra\u010d 1");
        tvPlayer2Name.setText("Igra\u010d 2");
        tvPlayer1Score.setText("0");
        tvPlayer2Score.setText("0");
        tvRoundLabel.setText("RUNDA 1/2 \u2014 MOJ BROJ");
        tvTimer.setText(String.valueOf(ROUND_DURATION));
        progressTimer.setMax(ROUND_DURATION);
        progressTimer.setProgress(ROUND_DURATION);
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
                .addOnFailureListener(e -> listenForGameData());
    }

    private void listenForGameData() {
        gameListener = db.collection("games").document(gameDocId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        if (!gameInitialized && isOwner) initializeGame();
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        if (!gameInitialized && isOwner) initializeGame();
                        return;
                    }

                    gameInitialized = true;

                    String phase = snapshot.getString("phase");
                    if (phase != null) currentPhase = phase;

                    currentRound = snapshot.getLong("currentRound") != null
                            ? snapshot.getLong("currentRound").intValue() : 1;
                    ownerScore = snapshot.getLong("ownerScore") != null
                            ? snapshot.getLong("ownerScore").intValue() : 0;
                    guestScore = snapshot.getLong("guestScore") != null
                            ? snapshot.getLong("guestScore").intValue() : 0;

                    targetRound1 = snapshot.getLong("targetRound1") != null
                            ? snapshot.getLong("targetRound1").intValue() : 0;
                    targetRound2 = snapshot.getLong("targetRound2") != null
                            ? snapshot.getLong("targetRound2").intValue() : 0;

                    numbersRound1 = castToIntArray(snapshot.get("numbersRound1"));
                    numbersRound2 = castToIntArray(snapshot.get("numbersRound2"));

                    Long or = snapshot.getLong("ownerResult");
                    ownerResult = or != null ? or.intValue() : null;
                    Long gr = snapshot.getLong("guestResult");
                    guestResult = gr != null ? gr.intValue() : null;
                    ownerExactHits = snapshot.getLong("ownerExactHits") != null
                            ? snapshot.getLong("ownerExactHits").intValue() : 0;
                    guestExactHits = snapshot.getLong("guestExactHits") != null
                            ? snapshot.getLong("guestExactHits").intValue() : 0;

                    if (snapshot.getString("ownerId") != null) ownerId = snapshot.getString("ownerId");
                    if (snapshot.getString("guestId") != null) guestId = snapshot.getString("guestId");

                    gameFinished = snapshot.getBoolean("gameFinished") != null
                            && snapshot.getBoolean("gameFinished");

                    if (isOwner && !gameFinished && currentPhase != null
                            && (currentPhase.endsWith("r1_play") || currentPhase.endsWith("r2_play"))
                            && ownerResult != null && guestResult != null) {
                        String next = currentPhase.equals("r1_play") ? "r1_end" : "r2_end";
                        Map<String, Object> autoUpdates = new HashMap<>();
                        autoUpdates.put("phase", next);
                        autoUpdates.put("phaseStartedAt", FieldValue.serverTimestamp());
                        db.collection("games").document(gameDocId).update(autoUpdates);
                        cancelLocalTimer();
                    }

                    updateUIForPhase();
                    updateScoreDisplay();

                    if (gameFinished && !resultShown) {
                        resultShown = true;
                        String winner = snapshot.getString("winner");
                        if (winner != null) {
                            showGameResult(winner);
                        }
                    }
                });
    }

    private void listenForPartyAdvance() {
        if (partyId == null || partyId.trim().isEmpty()) {
            return;
        }

        partyListener = partyRepository.listenParty(partyId, new PartyRepository.PartyListener() {
            @Override
            public void onPartyChanged(PartyData party) {
                if (returningToParty) {
                    return;
                }

                boolean partyMovedOn = !PartyData.STATUS_IN_PROGRESS.equals(party.status)
                        || (party.currentGameKey != null && !party.currentGameKey.equals(gameKey));
                if (!partyMovedOn) {
                    return;
                }

                returningToParty = true;
                runOnUiThread(() -> {
                    cancelLocalTimer();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                // Game state remains the source of truth while this screen is open.
            }
        });
    }

    private void initializeGame() {
        int target1 = generateTarget();
        int target2 = generateTarget();
        int[] nums1 = generateNumbers();
        int[] nums2 = generateNumbers();

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("sessionId", sessionId);
        gameData.put("gameType", "moj_broj");
        gameData.put("ownerId", ownerId != null ? ownerId : "");
        gameData.put("guestId", guestId != null ? guestId : "");
        gameData.put("targetRound1", target1);
        gameData.put("targetRound2", target2);
        gameData.put("numbersRound1", intToList(nums1));
        gameData.put("numbersRound2", intToList(nums2));
        gameData.put("currentRound", 1);
        gameData.put("phase", "r1_intro");
        gameData.put("ownerScore", 0);
        gameData.put("guestScore", 0);
        gameData.put("ownerResult", null);
        gameData.put("guestResult", null);
        gameData.put("ownerExactHits", 0);
        gameData.put("guestExactHits", 0);
        gameData.put("gameFinished", false);
        gameData.put("phaseStartedAt", FieldValue.serverTimestamp());

        db.collection("games").document(gameDocId).set(gameData);
    }

    private int[] generateNumbers() {
        int[] nums = new int[6];
        Random rng = new Random();
        for (int i = 0; i < 4; i++) nums[i] = rng.nextInt(9) + 1;
        int[] mid = {10, 15, 20};
        nums[4] = mid[rng.nextInt(mid.length)];
        int[] big = {25, 50, 75, 100};
        nums[5] = big[rng.nextInt(big.length)];
        for (int i = nums.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp;
        }
        return nums;
    }

    private int generateTarget() {
        return new Random().nextInt(900) + 100;
    }

    private List<Integer> intToList(int[] arr) {
        List<Integer> list = new ArrayList<>();
        for (int v : arr) list.add(v);
        return list;
    }

    private int[] castToIntArray(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            int[] result = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object val = list.get(i);
                if (val instanceof Long) result[i] = ((Long) val).intValue();
                else if (val instanceof Integer) result[i] = (Integer) val;
            }
            return result;
        }
        return new int[0];
    }

    private void updateUIForPhase() {
        if (currentPhase == null) return;

        boolean phaseChanged = previousPhase == null || !currentPhase.equals(previousPhase);
        previousPhase = currentPhase;

        if (!phaseChanged) return;

        cancelLocalTimer();
        submittedThisRound = false;
        isMyTurn = false;

        switch (currentPhase) {
            case "r1_intro":
                tvRoundLabel.setText("RUNDA 1/2 \u2014 MOJ BROJ");
                tvTargetNumber.setText("Tra\u017eeni broj: ---");
                resetNumberButtonsText();
                resetExpression();
                tvCurrentResult.setText("= ---");
                if (isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za otkrivanje brojeva");
                    setInputEnabled(false);
                    showStopButton();
                    isMyTurn = true;
                    startAutoTimer();
                } else {
                    tvTurnInfo.setText("\u010cekajte da protivnik zapo\u010dne...");
                    setInputEnabled(false);
                    hideConfirmButton();
                }
                break;

            case "r1_target":
                tvTargetNumber.setText("Tra\u017eeni broj: " + targetRound1);
                if (isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za brojeve (ili " + AUTO_REVEAL_DELAY + "s)");
                    showStopButton();
                    isMyTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik otkriva brojeve...");
                    hideConfirmButton();
                }
                startAutoTimer();
                break;

            case "r1_numbers":
                setNumbersOnButtons(numbersRound1);
                if (isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za po\u010detak (ili " + AUTO_REVEAL_DELAY + "s)");
                    showStopButton();
                    isMyTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik zapo\u010dinje rundu...");
                    hideConfirmButton();
                }
                startAutoTimer();
                break;

            case "r1_play":
                setNumbersOnButtons(numbersRound1);
                tvTargetNumber.setText("Tra\u017eeni broj: " + targetRound1);
                tvRoundLabel.setText("RUNDA 1/2 \u2014 MOJ BROJ");
                tvTurnInfo.setText("Pogodi broj! (" + ROUND_DURATION + "s)");
                setInputEnabled(true);
                showConfirmButton("POTVRDI");
                isMyTurn = true;
                startRoundTimer();
                break;

            case "r1_end":
                tvTurnInfo.setText("Runda 1 zavr\u0161ena");
                setInputEnabled(false);
                hideConfirmButton();
                if (isOwner) {
                    scoreAndAdvanceRound1();
                }
                break;

            case "r2_intro":
                tvRoundLabel.setText("RUNDA 2/2 \u2014 MOJ BROJ");
                tvTargetNumber.setText("Tra\u017eeni broj: ---");
                resetNumberButtonsText();
                resetExpression();
                tvCurrentResult.setText("= ---");
                if (!isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za otkrivanje brojeva");
                    setInputEnabled(false);
                    showStopButton();
                    isMyTurn = true;
                    startAutoTimer();
                } else {
                    tvTurnInfo.setText("\u010cekajte da protivnik zapo\u010dne...");
                    setInputEnabled(false);
                    hideConfirmButton();
                }
                break;

            case "r2_target":
                tvTargetNumber.setText("Tra\u017eeni broj: " + targetRound2);
                if (!isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za brojeve (ili " + AUTO_REVEAL_DELAY + "s)");
                    showStopButton();
                    isMyTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik otkriva brojeve...");
                    hideConfirmButton();
                }
                startAutoTimer();
                break;

            case "r2_numbers":
                setNumbersOnButtons(numbersRound2);
                if (!isOwner) {
                    tvTurnInfo.setText("Pritisnite STOP za po\u010detak (ili " + AUTO_REVEAL_DELAY + "s)");
                    showStopButton();
                    isMyTurn = true;
                } else {
                    tvTurnInfo.setText("Protivnik zapo\u010dinje rundu...");
                    hideConfirmButton();
                }
                startAutoTimer();
                break;

            case "r2_play":
                setNumbersOnButtons(numbersRound2);
                tvTargetNumber.setText("Tra\u017eeni broj: " + targetRound2);
                tvRoundLabel.setText("RUNDA 2/2 \u2014 MOJ BROJ");
                tvTurnInfo.setText("Pogodi broj! (" + ROUND_DURATION + "s)");
                setInputEnabled(true);
                showConfirmButton("POTVRDI");
                isMyTurn = true;
                startRoundTimer();
                break;

            case "r2_end":
                tvTurnInfo.setText("Kraj igre");
                setInputEnabled(false);
                hideConfirmButton();
                if (isOwner) {
                    scoreAndFinishRound2();
                }
                break;

            case "finished":
                cancelLocalTimer();
                setInputEnabled(false);
                hideConfirmButton();
                break;
        }
    }

    private void handleConfirmClick() {
        if (gameFinished) return;
        if (currentPhase == null) return;

        if (currentPhase.endsWith("_intro") || currentPhase.endsWith("_target")
                || currentPhase.endsWith("_numbers")) {
            if (isMyTurn) advancePhase();
        } else if (currentPhase.endsWith("_play")) {
            submitExpression();
        }
    }

    private void advancePhase() {
        if (!isMyTurn) return;

        String next = null;
        switch (currentPhase) {
            case "r1_intro": next = "r1_target"; break;
            case "r1_target": next = "r1_numbers"; break;
            case "r1_numbers": next = "r1_play"; break;
            case "r2_intro": next = "r2_target"; break;
            case "r2_target": next = "r2_numbers"; break;
            case "r2_numbers": next = "r2_play"; break;
        }

        if (next != null) {
            cancelLocalTimer();
            Map<String, Object> updates = new HashMap<>();
            updates.put("phase", next);
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
            db.collection("games").document(gameDocId).update(updates);
        }
    }

    private void submitExpression() {
        if (gameFinished || submittedThisRound) return;

        String expression = getExpressionText();
        if (expression.isEmpty()) return;

        int result;
        try {
            result = evaluateExpression(expression);
        } catch (Exception ex) {
            Toast.makeText(this, "Neispravan izraz", Toast.LENGTH_SHORT).show();
            return;
        }

        submittedThisRound = true;
        setInputEnabled(false);

        int target = (currentRound == 1) ? targetRound1 : targetRound2;
        boolean exact = (result == target);

        Map<String, Object> updates = new HashMap<>();
        if (isOwner) {
            updates.put("ownerResult", result);
        } else {
            updates.put("guestResult", result);
        }
        db.collection("games").document(gameDocId).update(updates);

        if (exact) {
            tvCurrentResult.setText("= " + result + " (pogodak!)");
            Toast.makeText(this, "Ta\u010dno! +10 bodova", Toast.LENGTH_SHORT).show();
        } else {
            tvCurrentResult.setText("= " + result + " (cilj: " + target + ")");
        }
    }

    private void handleTimerExpiry() {
        if (gameFinished) return;
        if (currentPhase == null) return;

        String next = null;

        switch (currentPhase) {
            case "r1_intro":
                if (isMyTurn) next = "r1_target";
                break;
            case "r1_target":
                if (isMyTurn) next = "r1_numbers";
                break;
            case "r1_numbers":
                if (isMyTurn) next = "r1_play";
                break;
            case "r1_play":
                if (isOwner) next = "r1_end";
                break;
            case "r2_intro":
                if (isMyTurn) next = "r2_target";
                break;
            case "r2_target":
                if (isMyTurn) next = "r2_numbers";
                break;
            case "r2_numbers":
                if (isMyTurn) next = "r2_play";
                break;
            case "r2_play":
                if (isOwner) next = "r2_end";
                break;
        }

        if (next != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("phase", next);
            updates.put("phaseStartedAt", FieldValue.serverTimestamp());
            db.collection("games").document(gameDocId).update(updates);
            tvTurnInfo.setText("Vreme je isteklo");
        }
    }

    private void scoreAndAdvanceRound1() {
        boolean ownerExact = ownerResult != null && ownerResult == targetRound1;
        boolean guestExact = guestResult != null && guestResult == targetRound1;

        if (ownerExact) {
            ownerScore += 10;
        } else if (guestExact) {
            guestScore += 10;
        } else {
            Integer oR = ownerResult;
            Integer gR = guestResult;

            if (oR != null && gR != null && oR.intValue() == gR.intValue() && oR != 0) {
                ownerScore += 5;
            } else {
                int ownerDiff = oR != null ? Math.abs(oR - targetRound1) : Integer.MAX_VALUE;
                int guestDiff = gR != null ? Math.abs(gR - targetRound1) : Integer.MAX_VALUE;
                if (ownerDiff < guestDiff) ownerScore += 5;
                else if (guestDiff < ownerDiff) guestScore += 5;
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("ownerExactHits", ownerExactHits + (ownerExact ? 1 : 0));
        updates.put("guestExactHits", guestExactHits + (guestExact ? 1 : 0));
        updates.put("currentRound", 2);
        updates.put("phase", "r2_intro");
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        updates.put("ownerResult", null);
        updates.put("guestResult", null);
        db.collection("games").document(gameDocId).update(updates);
    }

    private void scoreAndFinishRound2() {
        boolean ownerExact = ownerResult != null && ownerResult == targetRound2;
        boolean guestExact = guestResult != null && guestResult == targetRound2;

        if (guestExact) {
            guestScore += 10;
        } else if (ownerExact) {
            ownerScore += 10;
        } else {
            Integer oR = ownerResult;
            Integer gR = guestResult;

            if (oR != null && gR != null && oR.intValue() == gR.intValue() && oR != 0) {
                guestScore += 5;
            } else {
                int ownerDiff = oR != null ? Math.abs(oR - targetRound2) : Integer.MAX_VALUE;
                int guestDiff = gR != null ? Math.abs(gR - targetRound2) : Integer.MAX_VALUE;
                if (ownerDiff < guestDiff) ownerScore += 5;
                else if (guestDiff < ownerDiff) guestScore += 5;
            }
        }

        String winner;
        if (ownerScore > guestScore) winner = "owner";
        else if (guestScore > ownerScore) winner = "guest";
        else winner = "draw";

        Map<String, Object> updates = new HashMap<>();
        updates.put("ownerScore", ownerScore);
        updates.put("guestScore", guestScore);
        updates.put("ownerExactHits", ownerExactHits + (ownerExact ? 1 : 0));
        updates.put("guestExactHits", guestExactHits + (guestExact ? 1 : 0));
        updates.put("gameFinished", true);
        updates.put("winner", winner);
        updates.put("phase", "finished");
        updates.put("phaseStartedAt", FieldValue.serverTimestamp());
        db.collection("games").document(gameDocId).update(updates)
                .addOnSuccessListener(unused -> {
                    if (countsForStats && ownerId != null && guestId != null) {
                        profileStatsUpdater.recordMojBroj(
                                ownerId,
                                guestId,
                                ownerScore,
                                guestScore,
                                winner,
                                ownerExactHits + (ownerExact ? 1 : 0),
                                guestExactHits + (guestExact ? 1 : 0)
                        );
                    }
                    finishPartyGameIfNeeded(ownerScore, guestScore);
                });
    }

    private void showGameResult(String winner) {
        cancelLocalTimer();
        setInputEnabled(false);

        String message;
        if (isOwner && "owner".equals(winner)) {
            message = "Pobedili ste! Rezultat: " + ownerScore + " : " + guestScore;
        } else if (!isOwner && "guest".equals(winner)) {
            message = "Pobedili ste! Rezultat: " + guestScore + " : " + ownerScore;
        } else if ("draw".equals(winner)) {
            message = "Nere\u0161eno! Rezultat: " + ownerScore + " : " + guestScore;
        } else {
            message = "Izgubili ste. Rezultat: " + (isOwner ? ownerScore : guestScore)
                    + " : " + (isOwner ? guestScore : ownerScore);
        }

        tvTurnInfo.setText(message);
        showConfirmButton(partyId != null ? "Nazad u partiju" : "Zatvori");
        btnForfeit.setEnabled(false);
        btnConfirmExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void finishPartyGameIfNeeded(int ownerScore, int guestScore) {
        if (partyId == null || !isOwner) {
            return;
        }

        partyRepository.finishGameAndAdvance(partyId, gameKey, ownerScore, guestScore,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(MojBrojActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void forfeitParty() {
        partyRepository.forfeitParty(partyId, currentUserId, new PartyRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> finish());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MojBrojActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ===== TIMER =====

    private void startAutoTimer() {
        startLocalTimer(AUTO_REVEAL_DELAY);
    }

    private void startRoundTimer() {
        startLocalTimer(ROUND_DURATION);
    }

    private void startLocalTimer(int durationSeconds) {
        cancelLocalTimer();
        final long total = durationSeconds * 1000L;
        progressTimer.setMax(durationSeconds);
        progressTimer.setProgress(durationSeconds);
        tvTimer.setText(String.valueOf(durationSeconds));

        countDownTimer = new CountDownTimer(total, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long remaining = millisUntilFinished / 1000;
                tvTimer.setText(String.valueOf(remaining));
                progressTimer.setProgress((int) remaining);
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                handleTimerExpiry();
            }
        }.start();
    }

    private void cancelLocalTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ===== EXPRESSION BUILDING =====

    private boolean appendToExpression(String token, Button sourceButton) {
        if (sourceButton != null && !expressionTokens.isEmpty()) {
            ExpressionToken last = expressionTokens.get(expressionTokens.size() - 1);
            if (last.sourceButton != null) return false;
        }
        expressionTokens.add(new ExpressionToken(token, sourceButton));
        refreshExpression();
        return true;
    }

    private void removeLastToken() {
        if (expressionTokens.isEmpty()) return;

        ExpressionToken removed = expressionTokens.remove(expressionTokens.size() - 1);

        if (removed.sourceButton != null) {
            setButtonAvailable(removed.sourceButton);
        }

        refreshExpression();
    }

    private void refreshExpression() {
        String expr = getExpressionText();
        tvExpressionValue.setText("Izraz: " + expr);
        if (!expr.isEmpty()) {
            try {
                int result = evaluateExpression(expr);
                tvCurrentResult.setText("= " + result);
            } catch (Exception e) {
                tvCurrentResult.setText("= ---");
            }
        } else {
            tvCurrentResult.setText("= ---");
        }
    }

    private String getExpressionText() {
        StringBuilder sb = new StringBuilder();
        for (ExpressionToken t : expressionTokens) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(t.value);
        }
        return sb.toString();
    }

    // ===== EXPRESSION EVALUATOR (recursive descent) =====

    private int evaluateExpression(String expr) throws Exception {
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) throw new Exception("empty");
        parseIndex = 0;
        int result = parseExpression(tokens);
        if (parseIndex != tokens.size()) throw new Exception("unexpected tokens");
        return result;
    }

    private int parseIndex = 0;

    private List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (char c : expr.toCharArray()) {
            if (c == ' ') continue;
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.length() > 0) { tokens.add(num.toString()); num.setLength(0); }
                tokens.add(String.valueOf(c));
            }
        }
        if (num.length() > 0) tokens.add(num.toString());
        return tokens;
    }

    private int parseExpression(List<String> tokens) throws Exception {
        int result = parseTerm(tokens);
        while (parseIndex < tokens.size()) {
            String op = tokens.get(parseIndex);
            if (op.equals("+") || op.equals("-")) {
                parseIndex++;
                int right = parseTerm(tokens);
                if (op.equals("+")) result += right;
                else result -= right;
            } else break;
        }
        return result;
    }

    private int parseTerm(List<String> tokens) throws Exception {
        int result = parseFactor(tokens);
        while (parseIndex < tokens.size()) {
            String op = tokens.get(parseIndex);
            if (op.equals("*") || op.equals("/")) {
                parseIndex++;
                int right = parseFactor(tokens);
                if (op.equals("*")) result *= right;
                else if (right != 0) result /= right;
                else throw new Exception("division by zero");
            } else break;
        }
        return result;
    }

    private int parseFactor(List<String> tokens) throws Exception {
        if (parseIndex >= tokens.size()) throw new Exception("unexpected end");
        String token = tokens.get(parseIndex);
        if (token.equals("(")) {
            parseIndex++;
            int result = parseExpression(tokens);
            if (parseIndex >= tokens.size() || !tokens.get(parseIndex).equals(")"))
                throw new Exception("missing )");
            parseIndex++;
            return result;
        }
        parseIndex++;
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new Exception("invalid number: " + token);
        }
    }

    // ===== UI HELPERS =====

    private void setInputEnabled(boolean enabled) {
        for (Button btn : numberButtons) {
            boolean hasNumber = !btn.getText().toString().equals("-");

            if (enabled && hasNumber) {
                setButtonAvailable(btn);
            } else {
                setButtonDisabled(btn);
            }
        }

        for (Button btn : operatorButtons) {
            if (enabled) {
                setOperatorAvailable(btn);
            } else {
                setOperatorDisabled(btn);
            }
        }

        if (enabled) {
            btnClearExpression.setEnabled(true);
            btnClearExpression.setAlpha(1.0f);
            btnClearExpression.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
            btnClearExpression.setTextColor(Color.parseColor("#1E1E1E"));
        } else {
            btnClearExpression.setEnabled(false);
            btnClearExpression.setAlpha(0.45f);
            btnClearExpression.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEEEEE")));
            btnClearExpression.setTextColor(Color.parseColor("#999999"));
        }
    }

    private void setNumbersOnButtons(int[] numbers) {
        for (int i = 0; i < numberButtons.length && i < numbers.length; i++) {
            numberButtons[i].setText(String.valueOf(numbers[i]));
            setButtonDisabled(numberButtons[i]);
        }
    }

    private void resetNumberButtonsText() {
        for (Button btn : numberButtons) {
            btn.setText("-");
            setButtonDisabled(btn);
        }
    }

    private void resetExpression() {
        expressionTokens.clear();
        tvExpressionValue.setText("Izraz: ");
        tvCurrentResult.setText("= ---");
    }

    private void showStopButton() {
        btnConfirmExpression.setText("STOP");
        btnConfirmExpression.setEnabled(true);
        btnConfirmExpression.setAlpha(1.0f);
        btnConfirmExpression.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
        btnConfirmExpression.setTextColor(Color.parseColor("#1E1E1E"));
    }

    private void showConfirmButton(String text) {
        btnConfirmExpression.setText(text);
        btnConfirmExpression.setEnabled(true);
        btnConfirmExpression.setAlpha(1.0f);
        btnConfirmExpression.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
        btnConfirmExpression.setTextColor(Color.parseColor("#1E1E1E"));
    }

    private void hideConfirmButton() {
        btnConfirmExpression.setEnabled(false);
        btnConfirmExpression.setAlpha(0.45f);
        btnConfirmExpression.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEEEEE")));
        btnConfirmExpression.setTextColor(Color.parseColor("#999999"));
    }

    private void updateScoreDisplay() {
        tvPlayer1Score.setText(String.valueOf(ownerScore));
        tvPlayer2Score.setText(String.valueOf(guestScore));
    }

    private void setButtonAvailable(Button button) {
        button.setEnabled(true);
        button.setAlpha(1.0f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
        button.setTextColor(Color.parseColor("#1E1E1E"));
    }

    private void setButtonUsed(Button button) {
        button.setEnabled(false);
        button.setAlpha(0.35f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#CFCFCF")));
        button.setTextColor(Color.parseColor("#777777"));
    }

    private void setButtonDisabled(Button button) {
        button.setEnabled(false);
        button.setAlpha(0.45f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEEEEE")));
        button.setTextColor(Color.parseColor("#999999"));
    }

    private void setOperatorAvailable(Button button) {
        button.setEnabled(true);
        button.setAlpha(1.0f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
        button.setTextColor(Color.parseColor("#1E1E1E"));
    }

    private void setOperatorDisabled(Button button) {
        button.setEnabled(false);
        button.setAlpha(0.45f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEEEEE")));
        button.setTextColor(Color.parseColor("#999999"));
    }

    // ===== SHAKE SENSOR =====

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        long now = System.currentTimeMillis();
        if ((now - lastShakeTime) > SHAKE_SKIP_MS) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float dx = Math.abs(x - lastX);
            float dy = Math.abs(y - lastY);
            float dz = Math.abs(z - lastZ);

            if ((dx + dy + dz) > SHAKE_THRESHOLD) {
                lastShakeTime = now;
                handleConfirmClick();
            }

            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelLocalTimer();
        if (gameListener != null) gameListener.remove();
        if (partyListener != null) partyListener.remove();
    }

    // ===== INNER CLASS =====

    private static class ExpressionToken {
        final String value;
        final Button sourceButton;

        ExpressionToken(String value, Button sourceButton) {
            this.value = value;
            this.sourceButton = sourceButton;
        }
    }
}
