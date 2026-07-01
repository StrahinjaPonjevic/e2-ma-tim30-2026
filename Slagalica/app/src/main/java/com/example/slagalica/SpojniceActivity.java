package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.challenge.ChallengeRepository;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.example.slagalica.spojnice.FirestoreSpojniceRepository;
import com.example.slagalica.spojnice.SpojniceEvaluator;
import com.example.slagalica.spojnice.SpojniceSessionRepository;
import com.example.slagalica.spojnice.SpojniceSet;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpojniceActivity extends AppCompatActivity {

    private static final int ROUND_DURATION_SECONDS = 30;
    private static final int ITEMS_PER_ROUND = 5;
    private static final int OWNER_SOLVED_COLOR = Color.rgb(239, 154, 154);
    private static final int GUEST_SOLVED_COLOR = Color.rgb(144, 202, 249);

    private TextView tvPlayer1Name;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Name;
    private TextView tvPlayer2Score;
    private TextView tvRoundLabel;
    private TextView tvTimer;
    private CircularProgressIndicator progressTimer;
    private TextView tvSelected;

    private Button btnLeft1;
    private Button btnLeft2;
    private Button btnLeft3;
    private Button btnLeft4;
    private Button btnLeft5;
    private Button btnRight1;
    private Button btnRight2;
    private Button btnRight3;
    private Button btnRight4;
    private Button btnRight5;
    private Button btnNextRound;
    private Button btnBack;

    private FirebaseManager firebaseManager;
    private FirestoreSpojniceRepository setRepository;
    private SpojniceSessionRepository sessionRepository;
    private ProfileStatsUpdater profileStatsUpdater;
    private PartyRepository partyRepository;
    private ChallengeRepository challengeRepository;

    private String sessionId;
    private String partyId;
    private String challengeId;
    private String gameDocId;
    private String gameKey;
    private boolean countsForStats = true;
    private boolean challengeMode = false;
    private boolean isOwner;
    private boolean canControlGameFlow;
    private String currentUserId;
    private ListenerRegistration gameListener;
    private ListenerRegistration partyListener;
    private CountDownTimer countDownTimer;

    private SpojniceSessionRepository.SessionInfo sessionInfo;
    private SpojniceSessionRepository.GameState currentState;
    private String lastObservedPhase;
    private int selectedLeftIndex = -1;
    private boolean waitingForGameRetryScheduled = false;
    private boolean phaseAdvanceRequested = false;
    private boolean challengeScoreSubmitted = false;
    private boolean opponentForfeited = false;
    private boolean returningToParty = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        firebaseManager = new FirebaseManager();
        setRepository = new FirestoreSpojniceRepository();
        sessionRepository = new SpojniceSessionRepository();
        profileStatsUpdater = new ProfileStatsUpdater();
        partyRepository = new PartyRepository();
        challengeRepository = new ChallengeRepository();

        sessionId = getIntent().getStringExtra("sessionId");
        partyId = getIntent().getStringExtra("partyId");
        challengeId = getIntent().getStringExtra("challengeId");
        gameDocId = getIntent().getStringExtra("gameDocId");
        gameKey = getIntent().getStringExtra("gameKey");
        countsForStats = getIntent().getBooleanExtra("countsForStats", true);
        challengeMode = getIntent().getBooleanExtra("challengeMode", false);
        isOwner = getIntent().getBooleanExtra("isOwner", true);
        canControlGameFlow = isOwner;
        if (gameDocId == null || gameDocId.trim().isEmpty()) {
            gameDocId = sessionId;
        }
        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "spojnice";
        }

        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(this, "Sesija nije pronadjena", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        bindViews();
        bindListeners();
        setupInitialHeader();
        loadSessionAndStart();
    }

    private void bindViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);
        tvRoundLabel = findViewById(R.id.tvRoundLabel);
        tvTimer = findViewById(R.id.tvTimer);
        progressTimer = findViewById(R.id.progressTimer);
        tvSelected = findViewById(R.id.tvSelected);

        btnLeft1 = findViewById(R.id.btnLeft1);
        btnLeft2 = findViewById(R.id.btnLeft2);
        btnLeft3 = findViewById(R.id.btnLeft3);
        btnLeft4 = findViewById(R.id.btnLeft4);
        btnLeft5 = findViewById(R.id.btnLeft5);

        btnRight1 = findViewById(R.id.btnRight1);
        btnRight2 = findViewById(R.id.btnRight2);
        btnRight3 = findViewById(R.id.btnRight3);
        btnRight4 = findViewById(R.id.btnRight4);
        btnRight5 = findViewById(R.id.btnRight5);

        btnNextRound = findViewById(R.id.btnNextRound);
        btnBack = findViewById(R.id.btnBack);
    }

    private void bindListeners() {
        btnLeft1.setOnClickListener(v -> selectLeft(0));
        btnLeft2.setOnClickListener(v -> selectLeft(1));
        btnLeft3.setOnClickListener(v -> selectLeft(2));
        btnLeft4.setOnClickListener(v -> selectLeft(3));
        btnLeft5.setOnClickListener(v -> selectLeft(4));

        btnRight1.setOnClickListener(v -> tryMatch(0));
        btnRight2.setOnClickListener(v -> tryMatch(1));
        btnRight3.setOnClickListener(v -> tryMatch(2));
        btnRight4.setOnClickListener(v -> tryMatch(3));
        btnRight5.setOnClickListener(v -> tryMatch(4));

        btnNextRound.setOnClickListener(v -> handleNextPhaseClick());
        if (partyId != null && !challengeMode) {
            btnBack.setText("Odustani");
        } else if (challengeMode) {
            btnBack.setText("Nazad u izazov");
        }
        btnBack.setOnClickListener(v -> {
            if (partyId != null && !challengeMode) {
                forfeitParty();
            } else {
                finish();
            }
        });
    }

    private void setupInitialHeader() {
        tvPlayer1Name.setText("Igrac 1");
        tvPlayer2Name.setText("Igrac 2");
        tvRoundLabel.setText("RUNDA 1/2 - SPOJNICE");
        tvTimer.setText(String.valueOf(ROUND_DURATION_SECONDS));
        progressTimer.setMax(ROUND_DURATION_SECONDS);
        progressTimer.setProgress(ROUND_DURATION_SECONDS);
        updateScoreViews(0, 0);
        btnNextRound.setEnabled(false);
        tvSelected.setText("Cekanje partije...");
    }

    private void loadSessionAndStart() {
        sessionRepository.loadSessionInfo(sessionId, new SpojniceSessionRepository.SessionInfoCallback() {
            @Override
            public void onSuccess(SpojniceSessionRepository.SessionInfo info) {
                sessionInfo = info;
                if (currentUserId != null) {
                    isOwner = currentUserId.equals(info.ownerId);
                }
                updatePlayerNames();
                observePartyIfNeeded();
                observeGame();
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void observeGame() {
        gameListener = sessionRepository.observeGame(gameDocId, new SpojniceSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(SpojniceSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void observePartyIfNeeded() {
        if (partyId == null || challengeMode) {
            return;
        }

        partyListener = partyRepository.listenParty(partyId, new PartyRepository.PartyListener() {
            @Override
            public void onPartyChanged(PartyData party) {
                runOnUiThread(() -> handlePartyUpdate(party));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void handlePartyUpdate(PartyData party) {
        if (party == null || currentUserId == null) {
            return;
        }

        boolean partyMovedOn = !PartyData.STATUS_IN_PROGRESS.equals(party.status)
                || (party.currentGameKey != null && !party.currentGameKey.equals(gameKey));
        if (partyMovedOn) {
            if (!returningToParty) {
                returningToParty = true;
                stopTimer();
                finish();
            }
            return;
        }

        boolean currentUserForfeited = party.hasCurrentUserForfeited(currentUserId);
        opponentForfeited = party.hasForfeit() && !currentUserForfeited;

        boolean shouldControlFlow = party.isOwner(currentUserId);
        if (party.ownerForfeited && currentUserId.equals(party.guestId)) {
            shouldControlFlow = true;
        }
        if (party.guestForfeited && currentUserId.equals(party.ownerId)) {
            shouldControlFlow = true;
        }
        canControlGameFlow = shouldControlFlow;

        if (!opponentForfeited || currentState == null) {
            if (currentState == null && opponentForfeited && sessionInfo != null && canControlGameFlow) {
                initializeGame();
            }
            return;
        }

        if (shouldSkipOpponentTurn(currentState)) {
            advanceAfterOpponentForfeit(currentState);
            return;
        }

        if ("round_result".equals(currentState.phase)) {
            btnNextRound.setEnabled(canControlGameFlow);
        } else {
            updateStatusText(currentState);
        }
    }

    private void handleGameState(SpojniceSessionRepository.GameState gameState) {
        if (gameState == null) {
            if (canControlGameFlow && sessionInfo != null) {
                initializeGame();
            } else {
                showWaitingForGameState();
                scheduleGameRefreshRetry();
            }
            return;
        }

        waitingForGameRetryScheduled = false;
        currentState = gameState;
        updatePlayerNames();
        updateScoreViews(gameState.ownerScore, gameState.guestScore);

        if (!gameState.phase.equals(lastObservedPhase)) {
            phaseAdvanceRequested = false;
            selectedLeftIndex = -1;
            stopTimer();
            handlePhaseEntered(gameState);
        } else {
            refreshBoard(gameState);
            updateStatusText(gameState);
        }

        if (opponentForfeited && shouldSkipOpponentTurn(gameState)) {
            advanceAfterOpponentForfeit(gameState);
            return;
        }

        lastObservedPhase = gameState.phase;
    }

    private void initializeGame() {
        tvSelected.setText("Priprema spojnica iz baze...");
        setRepository.loadSets(2, new FirestoreSpojniceRepository.LoadSetsCallback() {
            @Override
            public void onSuccess(List<SpojniceSet> sets) {
                sessionRepository.initializeGame(gameDocId, sessionInfo, sets,
                        new SpojniceSessionRepository.RepositoryCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_LONG).show());
                            }
                        });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void handlePhaseEntered(SpojniceSessionRepository.GameState gameState) {
        refreshBoard(gameState);

        if ("finished".equals(gameState.phase)) {
            showFinalResult(gameState);
            return;
        }

        if ("round1_owner_turn".equals(gameState.phase) || "round1_guest_cleanup".equals(gameState.phase)) {
            tvRoundLabel.setText("RUNDA 1/2 - SPOJNICE");
        } else if ("round2_guest_turn".equals(gameState.phase) || "round2_owner_cleanup".equals(gameState.phase)) {
            tvRoundLabel.setText("RUNDA 2/2 - SPOJNICE");
        } else if ("round_result".equals(gameState.phase)) {
            stopTimer();
            btnNextRound.setEnabled(canControlGameFlow);
            btnNextRound.setText(gameState.currentRound == 1 ? "Pokreni rundu 2" : "Prikazi rezultat");
            updateStatusText(gameState);
            return;
        }

        updateStatusText(gameState);
        startRoundTimer(gameState);
    }

    private void refreshBoard(SpojniceSessionRepository.GameState gameState) {
        SpojniceSet activeSet = getActiveSet(gameState);
        if (activeSet == null) {
            return;
        }

        setButtonText(btnLeft1, activeSet.getLeftItems().get(0));
        setButtonText(btnLeft2, activeSet.getLeftItems().get(1));
        setButtonText(btnLeft3, activeSet.getLeftItems().get(2));
        setButtonText(btnLeft4, activeSet.getLeftItems().get(3));
        setButtonText(btnLeft5, activeSet.getLeftItems().get(4));

        setButtonText(btnRight1, activeSet.getRightItems().get(0));
        setButtonText(btnRight2, activeSet.getRightItems().get(1));
        setButtonText(btnRight3, activeSet.getRightItems().get(2));
        setButtonText(btnRight4, activeSet.getRightItems().get(3));
        setButtonText(btnRight5, activeSet.getRightItems().get(4));

        updateSolvedButtons(gameState);
        updateSelectionHighlight();

        boolean myTurn = isCurrentPlayerTurn(gameState);
        boolean selectionAllowed = myTurn
                && !"round_result".equals(gameState.phase)
                && !"finished".equals(gameState.phase);
        setSelectionEnabled(selectionAllowed);
        btnNextRound.setEnabled(canControlGameFlow && "round_result".equals(gameState.phase));
    }

    private void selectLeft(int leftIndex) {
        if (currentState == null || !isCurrentPlayerTurn(currentState) || isSolvedLeft(leftIndex)) {
            return;
        }

        selectedLeftIndex = leftIndex;
        updateStatusText(currentState);
        updateSelectionHighlight();
    }

    private void tryMatch(int rightIndex) {
        if (currentState == null
                || !isCurrentPlayerTurn(currentState)
                || selectedLeftIndex == -1
                || isSolvedRight(rightIndex)) {
            return;
        }

        SpojniceSet activeSet = getActiveSet(currentState);
        boolean correct = SpojniceEvaluator.isCorrectMatch(activeSet, selectedLeftIndex, rightIndex);

        List<Integer> solvedLeft = new ArrayList<>(currentState.solvedLeftIndices);
        List<Integer> solvedRight = new ArrayList<>(currentState.solvedRightIndices);
        List<Integer> ownerSolvedLeft = new ArrayList<>(currentState.ownerSolvedLeftIndices);
        List<Integer> ownerSolvedRight = new ArrayList<>(currentState.ownerSolvedRightIndices);
        List<Integer> guestSolvedLeft = new ArrayList<>(currentState.guestSolvedLeftIndices);
        List<Integer> guestSolvedRight = new ArrayList<>(currentState.guestSolvedRightIndices);
        int ownerAttemptCount = currentState.ownerAttemptCount;
        int guestAttemptCount = currentState.guestAttemptCount;
        int ownerScore = currentState.ownerScore;
        int guestScore = currentState.guestScore;

        if (isOwner) {
            ownerAttemptCount++;
        } else {
            guestAttemptCount++;
        }

        if (correct) {
            solvedLeft.add(selectedLeftIndex);
            solvedRight.add(rightIndex);
            if (isOwner) {
                ownerScore += 2;
                ownerSolvedLeft.add(selectedLeftIndex);
                ownerSolvedRight.add(rightIndex);
            } else {
                guestScore += 2;
                guestSolvedLeft.add(selectedLeftIndex);
                guestSolvedRight.add(rightIndex);
            }
        }

        int attemptsUsed = currentState.attemptsUsed + 1;
        String nextPhase = determineNextPhase(currentState, attemptsUsed, solvedLeft.size());
        String resultMessage = correct ? "Tacan spoj! +2 boda." : "Netacan spoj.";

        selectedLeftIndex = -1;
        updateSelectionHighlight();
        phaseAdvanceRequested = "round_result".equals(nextPhase);

        sessionRepository.updateAfterMatch(
                gameDocId,
                nextPhase,
                ownerScore,
                guestScore,
                attemptsUsed,
                solvedLeft,
                solvedRight,
                ownerSolvedLeft,
                ownerSolvedRight,
                guestSolvedLeft,
                guestSolvedRight,
                ownerAttemptCount,
                guestAttemptCount,
                resultMessage,
                new SpojniceSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void handleNextPhaseClick() {
        if (currentState == null || !canControlGameFlow || !"round_result".equals(currentState.phase)) {
            return;
        }

        if (currentState.currentRound == 1) {
            sessionRepository.advancePhase(
                    gameDocId,
                    "round2_guest_turn",
                    2,
                    currentState.ownerScore,
                    currentState.guestScore,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new SpojniceSessionRepository.RepositoryCallback() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    }
            );
        } else {
            String winner = determineWinner(currentState.ownerScore, currentState.guestScore);
            String resultMessage = buildFinalResultMessage(currentState.ownerScore, currentState.guestScore, winner);
            sessionRepository.finishGame(
                    gameDocId,
                    currentState.ownerScore,
                    currentState.guestScore,
                    winner,
                    resultMessage,
                    new SpojniceSessionRepository.RepositoryCallback() {
                        @Override
                        public void onSuccess() {
                            if (countsForStats && sessionInfo != null) {
                                profileStatsUpdater.recordSpojnice(
                                        sessionInfo.ownerId,
                                        sessionInfo.guestId,
                                        currentState.ownerScore,
                                        currentState.guestScore,
                                        winner,
                                        currentState.ownerSolvedLeftIndices.size(),
                                        currentState.ownerAttemptCount,
                                        currentState.guestSolvedLeftIndices.size(),
                                        currentState.guestAttemptCount
                                );
                            }
                            finishPartyGameIfNeeded(currentState.ownerScore, currentState.guestScore);
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    }
            );
        }
    }

    private String determineNextPhase(SpojniceSessionRepository.GameState gameState, int attemptsUsed, int solvedCount) {
        if (solvedCount == ITEMS_PER_ROUND) {
            return "round_result";
        }

        if ("round1_owner_turn".equals(gameState.phase) && attemptsUsed >= ITEMS_PER_ROUND) {
            return challengeMode || (opponentForfeited && !isOwner) ? "round_result" : "round1_guest_cleanup";
        }

        if ("round2_guest_turn".equals(gameState.phase) && attemptsUsed >= ITEMS_PER_ROUND) {
            return challengeMode || (opponentForfeited && isOwner) ? "round_result" : "round2_owner_cleanup";
        }

        if ("round1_guest_cleanup".equals(gameState.phase) || "round2_owner_cleanup".equals(gameState.phase)) {
            return solvedCount == ITEMS_PER_ROUND ? "round_result" : gameState.phase;
        }

        return gameState.phase;
    }

    private void startRoundTimer(SpojniceSessionRepository.GameState gameState) {
        stopTimer();

        int secondsLeft = getRemainingSeconds(gameState);
        progressTimer.setMax(ROUND_DURATION_SECONDS);
        progressTimer.setProgress(secondsLeft);
        tvTimer.setText(String.valueOf(secondsLeft));
        resetTimerStyle();

        if (secondsLeft <= 0) {
            if (isCurrentPlayerTurn(gameState)) {
                advanceAfterTimeout();
            }
            return;
        }

        countDownTimer = new CountDownTimer(secondsLeft * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int updatedSecondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(String.valueOf(updatedSecondsLeft));
                progressTimer.setProgress(updatedSecondsLeft);

                if (updatedSecondsLeft <= 10) {
                    tvTimer.setTextColor(Color.parseColor("#E53935"));
                    progressTimer.setIndicatorColor(Color.parseColor("#E53935"));
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                if (currentState != null
                        && !"round_result".equals(currentState.phase)
                        && !"finished".equals(currentState.phase)
                        && isCurrentPlayerTurn(currentState)) {
                    advanceAfterTimeout();
                }
            }
        }.start();
    }

    private void advanceAfterTimeout() {
        if (currentState == null || phaseAdvanceRequested || !isCurrentPlayerTurn(currentState)) {
            return;
        }

        phaseAdvanceRequested = true;
        String nextPhase = "round_result";

        if ("round1_owner_turn".equals(currentState.phase)) {
            nextPhase = challengeMode || (opponentForfeited && !isOwner) ? "round_result" : "round1_guest_cleanup";
        } else if ("round1_guest_cleanup".equals(currentState.phase)) {
            nextPhase = "round_result";
        } else if ("round2_guest_turn".equals(currentState.phase)) {
            nextPhase = challengeMode || (opponentForfeited && isOwner) ? "round_result" : "round2_owner_cleanup";
        } else if ("round2_owner_cleanup".equals(currentState.phase)) {
            nextPhase = "round_result";
        }

        sessionRepository.updateAfterMatch(
                gameDocId,
                nextPhase,
                currentState.ownerScore,
                currentState.guestScore,
                currentState.attemptsUsed,
                new ArrayList<>(currentState.solvedLeftIndices),
                new ArrayList<>(currentState.solvedRightIndices),
                new ArrayList<>(currentState.ownerSolvedLeftIndices),
                new ArrayList<>(currentState.ownerSolvedRightIndices),
                new ArrayList<>(currentState.guestSolvedLeftIndices),
                new ArrayList<>(currentState.guestSolvedRightIndices),
                currentState.ownerAttemptCount,
                currentState.guestAttemptCount,
                "Vreme je isteklo.",
                new SpojniceSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        phaseAdvanceRequested = false;
                        runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void updateSolvedButtons(SpojniceSessionRepository.GameState gameState) {
        Button[] leftButtons = {btnLeft1, btnLeft2, btnLeft3, btnLeft4, btnLeft5};
        Button[] rightButtons = {btnRight1, btnRight2, btnRight3, btnRight4, btnRight5};

        for (int i = 0; i < leftButtons.length; i++) {
            styleSolvedState(leftButtons[i], resolveSolvedColor(gameState, true, i));
        }

        for (int i = 0; i < rightButtons.length; i++) {
            styleSolvedState(rightButtons[i], resolveSolvedColor(gameState, false, i));
        }
    }

    private void styleSolvedState(Button button, int solvedColor) {
        button.setTextColor(Color.parseColor("#1E1E1E"));
        if (solvedColor != -1) {
            button.setEnabled(false);
            button.setAlpha(0.75f);
            button.setBackgroundColor(solvedColor);
        } else {
            button.setAlpha(1.0f);
            button.setBackgroundColor(Color.parseColor("#E9E9E9"));
        }
    }

    private void setSelectionEnabled(boolean enabled) {
        Button[] leftButtons = {btnLeft1, btnLeft2, btnLeft3, btnLeft4, btnLeft5};
        Button[] rightButtons = {btnRight1, btnRight2, btnRight3, btnRight4, btnRight5};

        for (int i = 0; i < leftButtons.length; i++) {
            leftButtons[i].setEnabled(enabled && !isSolvedLeft(i));
        }

        for (int i = 0; i < rightButtons.length; i++) {
            rightButtons[i].setEnabled(enabled && !isSolvedRight(i));
        }
    }

    private boolean shouldSkipOpponentTurn(SpojniceSessionRepository.GameState gameState) {
        if (!opponentForfeited || gameState == null || phaseAdvanceRequested) {
            return false;
        }

        if ("round1_owner_turn".equals(gameState.phase) && !isOwner) {
            return true;
        }

        return "round2_guest_turn".equals(gameState.phase) && isOwner;
    }

    private void advanceAfterOpponentForfeit(SpojniceSessionRepository.GameState gameState) {
        if (!shouldSkipOpponentTurn(gameState)) {
            return;
        }

        phaseAdvanceRequested = true;
        String nextPhase;
        if ("round1_owner_turn".equals(gameState.phase)) {
            nextPhase = gameState.solvedLeftIndices.size() == ITEMS_PER_ROUND ? "round_result" : "round1_guest_cleanup";
        } else {
            nextPhase = gameState.solvedLeftIndices.size() == ITEMS_PER_ROUND ? "round_result" : "round2_owner_cleanup";
        }

        sessionRepository.updateAfterMatch(
                gameDocId,
                nextPhase,
                gameState.ownerScore,
                gameState.guestScore,
                gameState.attemptsUsed,
                new ArrayList<>(gameState.solvedLeftIndices),
                new ArrayList<>(gameState.solvedRightIndices),
                new ArrayList<>(gameState.ownerSolvedLeftIndices),
                new ArrayList<>(gameState.ownerSolvedRightIndices),
                new ArrayList<>(gameState.guestSolvedLeftIndices),
                new ArrayList<>(gameState.guestSolvedRightIndices),
                gameState.ownerAttemptCount,
                gameState.guestAttemptCount,
                "Protivnik je odustao. Nastavljate bez cekanja.",
                new SpojniceSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        phaseAdvanceRequested = false;
                        runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void showWaitingForGameState() {
        currentState = null;
        stopTimer();
        setSelectionEnabled(false);
        btnNextRound.setEnabled(false);
        tvSelected.setText("Cekanje da protivnik pokrene igru...");
    }

    private void scheduleGameRefreshRetry() {
        if (waitingForGameRetryScheduled || canControlGameFlow) {
            return;
        }

        waitingForGameRetryScheduled = true;
        tvSelected.postDelayed(() -> {
            waitingForGameRetryScheduled = false;
            if (currentState == null) {
                refreshGameStateOnce();
            }
        }, 1200);
    }

    private void refreshGameStateOnce() {
        sessionRepository.fetchGameOnce(gameDocId, new SpojniceSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(SpojniceSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show();
                    scheduleGameRefreshRetry();
                });
            }
        });
    }

    private void showFinalResult(SpojniceSessionRepository.GameState gameState) {
        stopTimer();
        setSelectionEnabled(false);
        btnNextRound.setEnabled(partyId != null || challengeMode);
        btnNextRound.setText(challengeMode ? "Nazad u izazov" : (partyId != null ? "Nazad u partiju" : "Kraj igre"));
        if (partyId != null || challengeMode) {
            btnNextRound.setOnClickListener(v -> finish());
        }
        tvSelected.setText(buildFinalResultMessage(gameState.ownerScore, gameState.guestScore, gameState.winner));
        if (challengeMode) {
            submitChallengeScore(gameState.ownerScore);
        }
    }

    private void finishPartyGameIfNeeded(int ownerScore, int guestScore) {
        if (partyId == null || !canControlGameFlow) {
            return;
        }

        partyRepository.finishGameAndAdvance(partyId, gameKey, ownerScore, guestScore,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> finish());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void submitChallengeScore(int score) {
        if (challengeScoreSubmitted || challengeId == null || currentUserId == null) {
            return;
        }
        challengeScoreSubmitted = true;
        challengeRepository.submitGameScore(challengeId, currentUserId, gameKey, score,
                new ChallengeRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(SpojniceActivity.this, "Rezultat izazova je sacuvan.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        challengeScoreSubmitted = false;
                        runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updatePlayerNames() {
        if (sessionInfo == null) {
            return;
        }

        tvPlayer1Name.setText(sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        tvPlayer2Name.setText(sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
    }

    private void updateScoreViews(int ownerScore, int guestScore) {
        tvPlayer1Score.setText(ownerScore + " bodova");
        tvPlayer2Score.setText(guestScore + " bodova");
    }

    private void setButtonText(Button button, String text) {
        button.setText(text);
        button.setTextColor(Color.parseColor("#1E1E1E"));
    }

    private void updateStatusText(SpojniceSessionRepository.GameState gameState) {
        if (selectedLeftIndex != -1 && isCurrentPlayerTurn(gameState)) {
            SpojniceSet activeSet = getActiveSet(gameState);
            if (activeSet != null && selectedLeftIndex < activeSet.getLeftItems().size()) {
                tvSelected.setText("Izabrano: " + activeSet.getLeftItems().get(selectedLeftIndex));
                return;
            }
        }

        String baseMessage;
        switch (gameState.phase) {
            case "round1_owner_turn":
                baseMessage = isOwner ? "Tvoj red: povezi 5 pojmova." : "Protivnik povezuje pojmove.";
                break;
            case "round1_guest_cleanup":
                baseMessage = !isOwner ? "Tvoj cleanup: povezi preostale pojmove." : "Protivnik cisti preostale pojmove.";
                break;
            case "round2_guest_turn":
                baseMessage = (!isOwner || challengeMode) ? "Tvoj red: povezi 5 pojmova." : "Protivnik povezuje pojmove.";
                break;
            case "round2_owner_cleanup":
                baseMessage = isOwner ? "Tvoj cleanup: povezi preostale pojmove." : "Protivnik cisti preostale pojmove.";
                break;
            case "round_result":
                baseMessage = gameState.currentRound == 1 ? "Runda 1 je zavrsena." : "Runda 2 je zavrsena.";
                break;
            case "finished":
                baseMessage = "Partija je zavrsena.";
                break;
            default:
                baseMessage = "";
                break;
        }

        String resultMessage = gameState.resultMessage != null ? gameState.resultMessage.trim() : "";
        if (opponentForfeited && isCurrentPlayerTurn(gameState) && resultMessage.isEmpty()) {
            resultMessage = "Protivnik je odustao. Nastavljate bez cekanja.";
        }
        if (!resultMessage.isEmpty()) {
            tvSelected.setText(String.format(Locale.getDefault(), "%s %s", baseMessage, resultMessage).trim());
        } else {
            tvSelected.setText(baseMessage);
        }
    }

    private int getRemainingSeconds(SpojniceSessionRepository.GameState gameState) {
        if (gameState.phaseStartedAtMs == null) {
            return ROUND_DURATION_SECONDS;
        }

        long elapsedMs = Math.max(0L, System.currentTimeMillis() - gameState.phaseStartedAtMs);
        int elapsedSeconds = (int) (elapsedMs / 1000L);
        int remaining = ROUND_DURATION_SECONDS - elapsedSeconds;
        return Math.max(0, Math.min(ROUND_DURATION_SECONDS, remaining));
    }

    private void updateSelectionHighlight() {
        Button[] leftButtons = {btnLeft1, btnLeft2, btnLeft3, btnLeft4, btnLeft5};

        for (int i = 0; i < leftButtons.length; i++) {
            if (currentState != null && currentState.solvedLeftIndices.contains(i)) {
                leftButtons[i].setBackgroundColor(resolveSolvedColor(currentState, true, i));
            } else if (i == selectedLeftIndex) {
                leftButtons[i].setBackgroundColor(Color.parseColor("#CDE7FF"));
            } else {
                leftButtons[i].setBackgroundColor(Color.parseColor("#E9E9E9"));
            }
            leftButtons[i].setTextColor(Color.parseColor("#1E1E1E"));
        }
    }

    private int resolveSolvedColor(SpojniceSessionRepository.GameState gameState, boolean leftColumn, int index) {
        if (gameState == null) {
            return -1;
        }

        if (leftColumn) {
            if (gameState.ownerSolvedLeftIndices.contains(index)) {
                return OWNER_SOLVED_COLOR;
            }
            if (gameState.guestSolvedLeftIndices.contains(index)) {
                return GUEST_SOLVED_COLOR;
            }
        } else {
            if (gameState.ownerSolvedRightIndices.contains(index)) {
                return OWNER_SOLVED_COLOR;
            }
            if (gameState.guestSolvedRightIndices.contains(index)) {
                return GUEST_SOLVED_COLOR;
            }
        }

        return -1;
    }

    private boolean isCurrentPlayerTurn(SpojniceSessionRepository.GameState gameState) {
        switch (gameState.phase) {
            case "round1_owner_turn":
                return isOwner;
            case "round1_guest_cleanup":
                return !isOwner;
            case "round2_guest_turn":
                return !isOwner || challengeMode;
            case "round2_owner_cleanup":
                return isOwner;
            default:
                return false;
        }
    }

    private SpojniceSet getActiveSet(SpojniceSessionRepository.GameState gameState) {
        return gameState.currentRound == 1 ? gameState.round1Set : gameState.round2Set;
    }

    private boolean isSolvedLeft(int index) {
        return currentState != null && currentState.solvedLeftIndices.contains(index);
    }

    private boolean isSolvedRight(int index) {
        return currentState != null && currentState.solvedRightIndices.contains(index);
    }

    private String determineWinner(int ownerScore, int guestScore) {
        if (ownerScore > guestScore) {
            return "owner";
        }
        if (guestScore > ownerScore) {
            return "guest";
        }
        return "draw";
    }

    private String buildFinalResultMessage(int ownerScore, int guestScore, String winner) {
        if ("draw".equals(winner)) {
            return "Nereseno! Rezultat je " + ownerScore + " : " + guestScore;
        }

        boolean currentUserWon = (isOwner && "owner".equals(winner))
                || (!isOwner && "guest".equals(winner));

        if (currentUserWon) {
            return "Pobedili ste! Rezultat je " + ownerScore + " : " + guestScore;
        }

        return "Izgubili ste. Rezultat je " + ownerScore + " : " + guestScore;
    }

    private void resetTimerStyle() {
        tvTimer.setTextColor(Color.parseColor("#1E1E1E"));
        progressTimer.setIndicatorColor(Color.parseColor("#6200EE"));
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (gameListener != null) {
            gameListener.remove();
        }
        if (partyListener != null) {
            partyListener.remove();
        }
    }
}
