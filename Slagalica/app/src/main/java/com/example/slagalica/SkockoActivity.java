package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.challenge.ChallengeRepository;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.example.slagalica.skocko.SkockoSessionRepository;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkockoActivity extends AppCompatActivity {

    public static final String SYM_SKOCKO = "👾";
    public static final String SYM_KRUG = "⚫";
    public static final String SYM_SRCE = "♥";
    public static final String SYM_TROUGAO = "🔺";
    public static final String SYM_ZVEZDA = "⭐";
    public static final String SYM_KVADRAT = "◼";

    private static final List<String> SYMBOLS = Arrays.asList(
            SYM_SKOCKO, SYM_KRUG, SYM_SRCE, SYM_TROUGAO, SYM_ZVEZDA, SYM_KVADRAT
    );

    private static final int COLOR_CORRECT = Color.parseColor("#43A047");
    private static final int COLOR_PRESENT = Color.parseColor("#FBC02D");
    private static final int COLOR_ABSENT = Color.parseColor("#37474F");

    private static final int ACTIVE_DURATION_SECONDS = 30;
    private static final int TAKEOVER_DURATION_SECONDS = 10;

    private TextView[][] gridSlots = new TextView[6][4];
    private View[][] feedbackDots = new View[6][4];
    private TextView[] previewSlots = new TextView[4];
    private TextView tvRoundLabel, tvTimer, tvPlayer1Name, tvPlayer1Score, tvPlayer2Name, tvPlayer2Score;
    private CircularProgressIndicator progressTimer;
    private Button btnConfirm, btnClear, btnBack;
    private View[] pickerButtons = new View[6];

    private PartyRepository partyRepository;
    private ChallengeRepository challengeRepository;
    private SkockoSessionRepository sessionRepository;
    private ProfileStatsUpdater profileStatsUpdater;

    private String sessionId;
    private String partyId;
    private String challengeId;
    private String gameDocId;
    private String gameKey;
    private String currentUserId;
    private boolean challengeMode = false;
    private boolean countsForStats = true;
    private boolean syncedMode = false;
    private boolean isOwner = true;
    private boolean canControlGameFlow = true;
    private boolean opponentForfeited = false;
    private boolean returningToParty = false;
    private boolean phaseAdvanceRequested = false;
    private boolean initializeRequested = false;
    private boolean waitingForGameRetryScheduled = false;
    private boolean resultAdvanceScheduled = false;
    private boolean challengeScoreSubmitted = false;
    private boolean partyFinishSubmitted = false;
    private boolean statsRecorded = false;
    private int partyOwnerTotal = 0;
    private int partyGuestTotal = 0;
    private int lastOwnerGameScore = 0;
    private int lastGuestGameScore = 0;

    private SkockoSessionRepository.SessionInfo sessionInfo;
    private SkockoSessionRepository.GameState currentState;
    private String lastObservedPhase;
    private ListenerRegistration gameListener;
    private ListenerRegistration partyListener;
    private CountDownTimer countDownTimer;

    private List<Integer> currentGuess = new ArrayList<>();

    private int soloRound = 1;
    private List<Integer> soloSecret = new ArrayList<>();
    private int soloCurrentRow = 0;
    private int soloRound1Score = 0;
    private int soloRound2Score = 0;
    private boolean soloRoundRunning = false;
    private boolean soloFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        partyRepository = new PartyRepository();
        challengeRepository = new ChallengeRepository();
        sessionRepository = new SkockoSessionRepository();
        profileStatsUpdater = new ProfileStatsUpdater();

        sessionId = getIntent().getStringExtra("sessionId");
        partyId = getIntent().getStringExtra("partyId");
        challengeId = getIntent().getStringExtra("challengeId");
        gameDocId = getIntent().getStringExtra("gameDocId");
        gameKey = getIntent().getStringExtra("gameKey");
        challengeMode = getIntent().getBooleanExtra("challengeMode", false);
        countsForStats = getIntent().getBooleanExtra("countsForStats", true);
        isOwner = getIntent().getBooleanExtra("isOwner", true);
        canControlGameFlow = isOwner;

        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "skocko";
        }
        if (gameDocId == null || gameDocId.trim().isEmpty()) {
            gameDocId = sessionId != null ? sessionId : "skocko_local";
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        syncedMode = !challengeMode && sessionId != null && !sessionId.trim().isEmpty();

        bindViews();
        setupSymbolPicker();
        setupInitialHeader();

        if (syncedMode) {
            loadSessionAndStart();
        } else {
            startSoloMode();
        }
    }

    private void bindViews() {
        tvRoundLabel = findViewById(R.id.tvRoundLabel);
        tvTimer = findViewById(R.id.tvTimer);
        progressTimer = findViewById(R.id.progressTimer);
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);

        int[][] slotIds = {
                {R.id.r1s1, R.id.r1s2, R.id.r1s3, R.id.r1s4},
                {R.id.r2s1, R.id.r2s2, R.id.r2s3, R.id.r2s4},
                {R.id.r3s1, R.id.r3s2, R.id.r3s3, R.id.r3s4},
                {R.id.r4s1, R.id.r4s2, R.id.r4s3, R.id.r4s4},
                {R.id.r5s1, R.id.r5s2, R.id.r5s3, R.id.r5s4},
                {R.id.r6s1, R.id.r6s2, R.id.r6s3, R.id.r6s4}
        };
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 4; c++)
                gridSlots[r][c] = findViewById(slotIds[r][c]);

        int[][] dotIds = {
                {R.id.r1fb1, R.id.r1fb2, R.id.r1fb3, R.id.r1fb4},
                {R.id.r2fb1, R.id.r2fb2, R.id.r2fb3, R.id.r2fb4},
                {R.id.r3fb1, R.id.r3fb2, R.id.r3fb3, R.id.r3fb4},
                {R.id.r4fb1, R.id.r4fb2, R.id.r4fb3, R.id.r4fb4},
                {R.id.r5fb1, R.id.r5fb2, R.id.r5fb3, R.id.r5fb4},
                {R.id.r6fb1, R.id.r6fb2, R.id.r6fb3, R.id.r6fb4}
        };
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 4; c++)
                feedbackDots[r][c] = findViewById(dotIds[r][c]);

        previewSlots[0] = findViewById(R.id.tvSlot1);
        previewSlots[1] = findViewById(R.id.tvSlot2);
        previewSlots[2] = findViewById(R.id.tvSlot3);
        previewSlots[3] = findViewById(R.id.tvSlot4);

        btnConfirm = findViewById(R.id.btnConfirm);
        btnClear = findViewById(R.id.btnClear);
        btnBack = findViewById(R.id.btnBack);

        btnConfirm.setOnClickListener(v -> {
            if (syncedMode) {
                confirmGuessSynced();
            } else {
                confirmGuessSolo();
            }
        });
        btnClear.setOnClickListener(v -> clearLastSymbol());

        if (syncedMode) {
            btnBack.setText("Odustani");
        } else if (challengeMode) {
            btnBack.setText("Nazad u izazov");
        }
        btnBack.setOnClickListener(v -> onQuitPressed());
    }

    private void onQuitPressed() {
        if (isGameOver() || (!syncedMode && !soloRoundRunning)) {
            finish();
            return;
        }
        String message = partyId != null
                ? "Napustanjem gubite celu partiju i ne dobijate zvezde. Protivnik nastavlja sam."
                : (challengeMode
                ? "Napustanjem gubite rezultat ove igre u izazovu."
                : "Napustanjem prekidate igru u toku.");
        new AlertDialog.Builder(this)
                .setTitle("Napustiti igru?")
                .setMessage(message)
                .setPositiveButton("Napusti", (dialog, which) -> performQuit())
                .setNegativeButton("Ostani", null)
                .show();
    }

    private void performQuit() {
        if (partyId != null && syncedMode) {
            forfeitParty();
            return;
        }
        if (syncedMode) {
            abortGuestSessionGame();
            return;
        }
        finish();
    }

    private void abortGuestSessionGame() {
        SkockoSessionRepository.GameState state = currentState;
        if (state == null || SkockoSessionRepository.PHASE_FINISHED.equals(state.phase)) {
            finish();
            return;
        }
        sessionRepository.finishGame(gameDocId, state.ownerScore, state.guestScore,
                isOwner ? "guest" : "owner", "Protivnik je napustio igru.",
                new SkockoSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> finish());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> finish());
                    }
                });
    }

    @Override
    public void onBackPressed() {
        onQuitPressed();
    }

    private boolean isGameOver() {
        if (syncedMode) {
            return currentState != null && SkockoSessionRepository.PHASE_FINISHED.equals(currentState.phase);
        }
        return soloFinished;
    }

    private void setupSymbolPicker() {
        int[] pickerIds = {
                R.id.btnSkocko, R.id.btnKrug, R.id.btnSrce,
                R.id.btnTrougao, R.id.btnZvezda, R.id.btnKvadrat
        };
        for (int i = 0; i < pickerIds.length; i++) {
            final int symbolIndex = i;
            pickerButtons[i] = findViewById(pickerIds[i]);
            pickerButtons[i].setOnClickListener(v -> addSymbol(symbolIndex));
        }
    }

    private void setupInitialHeader() {
        tvPlayer1Name.setText(syncedMode ? "Igrac 1" : "Runda 1");
        tvPlayer2Name.setText(syncedMode ? "Igrac 2" : "Runda 2");
        tvPlayer1Score.setText("0 bod");
        tvPlayer2Score.setText("0 bod");
        tvRoundLabel.setText("Priprema igre...");
        tvTimer.setText(String.valueOf(ACTIVE_DURATION_SECONDS));
        progressTimer.setMax(ACTIVE_DURATION_SECONDS);
        progressTimer.setProgress(ACTIVE_DURATION_SECONDS);
        setInputEnabled(false);
        clearBoard();
    }

    private void loadSessionAndStart() {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            Toast.makeText(this, "Sesija nije pronadjena", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sessionRepository.loadSessionInfo(sessionId, new SkockoSessionRepository.SessionInfoCallback() {
            @Override
            public void onSuccess(SkockoSessionRepository.SessionInfo info) {
                sessionInfo = info;
                if (currentUserId != null && info.ownerId != null) {
                    isOwner = currentUserId.equals(info.ownerId);
                    canControlGameFlow = isOwner;
                }
                runOnUiThread(() -> {
                    updatePlayerNames();
                    observeParty();
                    observeGame();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void observeGame() {
        gameListener = sessionRepository.observeGame(gameDocId, new SkockoSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(SkockoSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void observeParty() {
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

        partyOwnerTotal = party.ownerTotalScore;
        partyGuestTotal = party.guestTotalScore;
        updateScoreViews(lastOwnerGameScore, lastGuestGameScore);

        if (currentState == null) {
            if (opponentForfeited && canControlGameFlow && sessionInfo != null && !initializeRequested) {
                initializeGame();
            }
            return;
        }

        skipForfeitedTurnIfNeeded(currentState);
    }

    private void handleGameState(SkockoSessionRepository.GameState gameState) {
        if (gameState == null) {
            if (canControlGameFlow && sessionInfo != null && !initializeRequested) {
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

        if (!safePhase(gameState).equals(lastObservedPhase)) {
            phaseAdvanceRequested = false;
            resultAdvanceScheduled = false;
            stopTimer();
            handlePhaseEntered(gameState);
        } else {
            renderBoard(gameState);
            updateStatusLabel(gameState);
            updateInputForState(gameState);
        }

        skipForfeitedTurnIfNeeded(gameState);
        lastObservedPhase = safePhase(gameState);
    }

    private String safePhase(SkockoSessionRepository.GameState state) {
        return state.phase != null ? state.phase : "";
    }

    private void initializeGame() {
        initializeRequested = true;
        tvRoundLabel.setText("Pokretanje igre...");
        sessionRepository.initializeGame(gameDocId, sessionInfo, generateSecret(), generateSecret(),
                new SkockoSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        initializeRequested = false;
                        runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_LONG).show());
                    }
                });
    }

    private void showWaitingForGameState() {
        currentState = null;
        stopTimer();
        setInputEnabled(false);
        tvRoundLabel.setText("Cekanje da protivnik pokrene igru...");
    }

    private void scheduleGameRefreshRetry() {
        if (waitingForGameRetryScheduled || canControlGameFlow) {
            return;
        }
        waitingForGameRetryScheduled = true;
        tvRoundLabel.postDelayed(() -> {
            waitingForGameRetryScheduled = false;
            if (currentState == null) {
                sessionRepository.fetchGameOnce(gameDocId, new SkockoSessionRepository.GameStateListener() {
                    @Override
                    public void onGameStateChanged(SkockoSessionRepository.GameState gameState) {
                        runOnUiThread(() -> handleGameState(gameState));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> scheduleGameRefreshRetry());
                    }
                });
            }
        }, 1200);
    }

    private void handlePhaseEntered(SkockoSessionRepository.GameState state) {
        clearPreview();
        renderBoard(state);
        updateStatusLabel(state);
        updateInputForState(state);

        if (SkockoSessionRepository.PHASE_FINISHED.equals(state.phase)) {
            showFinalResult(state);
            return;
        }

        if (SkockoSessionRepository.PHASE_ROUND_RESULT.equals(state.phase)) {
            stopTimer();
            scheduleResultAdvance(state);
            return;
        }

        startPhaseTimer(state);
    }

    private void scheduleResultAdvance(SkockoSessionRepository.GameState state) {
        if (!canControlGameFlow || resultAdvanceScheduled) {
            return;
        }
        resultAdvanceScheduled = true;
        final int roundAtSchedule = state.currentRound;
        tvRoundLabel.postDelayed(() -> {
            if (currentState == null
                    || !SkockoSessionRepository.PHASE_ROUND_RESULT.equals(currentState.phase)
                    || currentState.currentRound != roundAtSchedule
                    || !canControlGameFlow) {
                return;
            }
            if (roundAtSchedule == 1) {
                sessionRepository.advancePhase(gameDocId, SkockoSessionRepository.PHASE_ROUND2_ACTIVE, 2,
                        new ArrayList<>(), currentState.ownerScore, currentState.guestScore,
                        currentState.ownerHitAttempt, currentState.guestHitAttempt, "",
                        repositoryToastCallback());
            } else {
                finishSyncedGame(currentState);
            }
        }, 3000);
    }

    private void finishSyncedGame(SkockoSessionRepository.GameState state) {
        String winner = determineWinner(state.ownerScore, state.guestScore);
        String message = buildFinalResultMessage(state.ownerScore, state.guestScore, winner);
        sessionRepository.finishGame(gameDocId, state.ownerScore, state.guestScore, winner, message,
                new SkockoSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                        recordStatsIfNeeded(state, winner);
                        finishPartyGameIfNeeded(state.ownerScore, state.guestScore);
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void recordStatsIfNeeded(SkockoSessionRepository.GameState state, String winner) {
        if (statsRecorded || partyId == null || !countsForStats || sessionInfo == null
                || sessionInfo.ownerId == null || sessionInfo.guestId == null) {
            return;
        }
        statsRecorded = true;
        profileStatsUpdater.recordSkocko(sessionInfo.ownerId, sessionInfo.guestId,
                state.ownerScore, state.guestScore, winner,
                state.ownerHitAttempt, state.guestHitAttempt);
    }

    private void finishPartyGameIfNeeded(int ownerScore, int guestScore) {
        if (partyId == null || !canControlGameFlow || partyFinishSubmitted) {
            return;
        }
        partyFinishSubmitted = true;
        partyRepository.finishGameAndAdvance(partyId, gameKey, ownerScore, guestScore,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> finish());
                    }

                    @Override
                    public void onError(String message) {
                        partyFinishSubmitted = false;
                        runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void showFinalResult(SkockoSessionRepository.GameState state) {
        stopTimer();
        setInputEnabled(false);
        btnBack.setText(partyId != null ? "Nazad u partiju" : "Zatvori");
        String message = state.resultMessage != null && !state.resultMessage.trim().isEmpty()
                ? state.resultMessage
                : buildFinalResultMessage(state.ownerScore, state.guestScore, state.winner);
        tvRoundLabel.setText(message);
    }

    private void addSymbol(int symbolIndex) {
        boolean allowed = syncedMode
                ? (currentState != null && isPlayPhase(currentState) && isMyTurn(currentState))
                : soloRoundRunning;
        if (!allowed || currentGuess.size() >= 4) {
            return;
        }
        currentGuess.add(symbolIndex);
        int idx = currentGuess.size() - 1;
        setSymbolText(previewSlots[idx], symbolIndex);
    }

    private void clearLastSymbol() {
        if (currentGuess.isEmpty()) {
            return;
        }
        int idx = currentGuess.size() - 1;
        currentGuess.remove(idx);
        previewSlots[idx].setText("·");
        previewSlots[idx].setTextColor(Color.parseColor("#555580"));
    }

    private void clearPreview() {
        currentGuess.clear();
        for (TextView tv : previewSlots) {
            tv.setText("·");
            tv.setTextColor(Color.parseColor("#555580"));
        }
    }

    private void clearBoard() {
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 4; c++) {
                gridSlots[r][c].setText("");
                gridSlots[r][c].setTextColor(Color.WHITE);
                feedbackDots[r][c].setBackgroundResource(R.drawable.bg_dot_empty);
            }
        }
    }

    private void confirmGuessSynced() {
        SkockoSessionRepository.GameState state = currentState;
        if (state == null || !isPlayPhase(state) || !isMyTurn(state)
                || phaseAdvanceRequested || currentGuess.size() < 4) {
            return;
        }

        List<Integer> secret = state.activeSecret();
        if (secret.size() < 4) {
            return;
        }

        List<String> rows = new ArrayList<>(state.rows);
        rows.add(joinRow(currentGuess));

        int[] feedback = computeFeedback(currentGuess, secret);
        boolean won = feedback[0] == 4;
        boolean takeover = isTakeoverPhase(state.phase);

        int ownerScore = state.ownerScore;
        int guestScore = state.guestScore;
        int ownerHit = state.ownerHitAttempt;
        int guestHit = state.guestHitAttempt;
        String message;
        String nextPhase = state.phase;

        if (takeover) {
            if (won) {
                if (isOwner) ownerScore += 10;
                else guestScore += 10;
                message = "Preuzimanje uspesno! +10 bodova.";
            } else {
                message = "Preuzimanje nije uspelo.";
            }
            nextPhase = SkockoSessionRepository.PHASE_ROUND_RESULT;
        } else {
            if (won) {
                int attempt = rows.size();
                int points = attempt <= 2 ? 20 : (attempt <= 4 ? 15 : 10);
                if (isOwner) {
                    ownerScore += points;
                    ownerHit = attempt;
                } else {
                    guestScore += points;
                    guestHit = attempt;
                }
                message = "Kombinacija pogodjena u " + attempt + ". pokusaju! +" + points + " bodova.";
                nextPhase = SkockoSessionRepository.PHASE_ROUND_RESULT;
            } else if (rows.size() >= 6) {
                message = "Kombinacija nije pogodjena.";
                nextPhase = opponentForfeited
                        ? SkockoSessionRepository.PHASE_ROUND_RESULT
                        : takeoverPhaseFor(state.phase);
            } else {
                message = "";
            }
        }

        clearPreview();

        if (nextPhase.equals(state.phase)) {
            sessionRepository.updateRows(gameDocId, rows, message, repositoryToastCallback());
        } else {
            phaseAdvanceRequested = true;
            List<String> rowsToWrite = SkockoSessionRepository.PHASE_ROUND_RESULT.equals(nextPhase)
                    ? rows
                    : new ArrayList<>();
            sessionRepository.advancePhase(gameDocId, nextPhase, state.currentRound, rowsToWrite,
                    ownerScore, guestScore, ownerHit, guestHit, message, repositoryErrorResetCallback());
        }
    }

    private void skipForfeitedTurnIfNeeded(SkockoSessionRepository.GameState state) {
        if (!opponentForfeited || state == null || phaseAdvanceRequested || !isPlayPhase(state)
                || isMyTurn(state)) {
            return;
        }

        String nextPhase;
        if (SkockoSessionRepository.PHASE_ROUND1_ACTIVE.equals(state.phase)) {
            nextPhase = SkockoSessionRepository.PHASE_ROUND1_TAKEOVER;
        } else if (SkockoSessionRepository.PHASE_ROUND2_ACTIVE.equals(state.phase)) {
            nextPhase = SkockoSessionRepository.PHASE_ROUND2_TAKEOVER;
        } else {
            nextPhase = SkockoSessionRepository.PHASE_ROUND_RESULT;
        }

        phaseAdvanceRequested = true;
        List<String> rowsToWrite = SkockoSessionRepository.PHASE_ROUND_RESULT.equals(nextPhase)
                ? state.rows
                : new ArrayList<>();
        sessionRepository.advancePhase(gameDocId, nextPhase, state.currentRound, rowsToWrite,
                state.ownerScore, state.guestScore, state.ownerHitAttempt, state.guestHitAttempt,
                "Protivnik je odustao. Nastavljate bez cekanja.", repositoryErrorResetCallback());
    }

    private void startPhaseTimer(SkockoSessionRepository.GameState state) {
        stopTimer();

        int duration = phaseDurationSeconds(state.phase);
        int secondsLeft = getRemainingSeconds(state, duration);
        progressTimer.setMax(duration);
        progressTimer.setProgress(secondsLeft);
        tvTimer.setText(String.valueOf(secondsLeft));
        resetTimerStyle();

        if (secondsLeft <= 0) {
            advanceAfterTimeout();
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
                advanceAfterTimeout();
            }
        }.start();
    }

    private void advanceAfterTimeout() {
        SkockoSessionRepository.GameState state = currentState;
        if (state == null || phaseAdvanceRequested || !isPlayPhase(state) || !isMyTurn(state)) {
            return;
        }

        String nextPhase;
        if (isTakeoverPhase(state.phase)) {
            nextPhase = SkockoSessionRepository.PHASE_ROUND_RESULT;
        } else {
            nextPhase = opponentForfeited
                    ? SkockoSessionRepository.PHASE_ROUND_RESULT
                    : takeoverPhaseFor(state.phase);
        }

        phaseAdvanceRequested = true;
        List<String> rowsToWrite = SkockoSessionRepository.PHASE_ROUND_RESULT.equals(nextPhase)
                ? state.rows
                : new ArrayList<>();
        sessionRepository.advancePhase(gameDocId, nextPhase, state.currentRound, rowsToWrite,
                state.ownerScore, state.guestScore, state.ownerHitAttempt, state.guestHitAttempt,
                "Vreme je isteklo.", repositoryErrorResetCallback());
    }

    private void renderBoard(SkockoSessionRepository.GameState state) {
        clearBoard();
        List<Integer> secret = state.activeSecret();
        if (secret.size() < 4) {
            return;
        }

        int rowsToRender = Math.min(state.rows.size(), 6);
        for (int r = 0; r < rowsToRender; r++) {
            List<Integer> guess = parseRow(state.rows.get(r));
            if (guess.size() < 4) {
                continue;
            }
            for (int c = 0; c < 4; c++) {
                setSymbolText(gridSlots[r][c], guess.get(c));
            }
            applyFeedback(r, computeFeedback(guess, secret));
        }
    }

    private void updateStatusLabel(SkockoSessionRepository.GameState state) {
        String base;
        String round = "RUNDA " + state.currentRound + "/2 — ";
        switch (safePhase(state)) {
            case SkockoSessionRepository.PHASE_ROUND1_ACTIVE:
            case SkockoSessionRepository.PHASE_ROUND2_ACTIVE:
                base = round + (isMyTurn(state) ? "TVOJ RED: pogodi kombinaciju" : "Protivnik pogadja...");
                break;
            case SkockoSessionRepository.PHASE_ROUND1_TAKEOVER:
            case SkockoSessionRepository.PHASE_ROUND2_TAKEOVER:
                base = round + (isMyTurn(state)
                        ? "PREUZIMANJE: 1 pokusaj za 10 bodova!"
                        : "Protivnik preuzima...");
                break;
            case SkockoSessionRepository.PHASE_ROUND_RESULT:
                base = round + "Runda je zavrsena.";
                break;
            case SkockoSessionRepository.PHASE_FINISHED:
                base = "Igra je zavrsena.";
                break;
            default:
                base = "";
                break;
        }

        String result = state.resultMessage != null ? state.resultMessage.trim() : "";
        tvRoundLabel.setText(result.isEmpty() ? base : base + " " + result);
    }

    private void updateInputForState(SkockoSessionRepository.GameState state) {
        setInputEnabled(isPlayPhase(state) && isMyTurn(state));
    }

    private void setInputEnabled(boolean enabled) {
        for (View picker : pickerButtons) {
            if (picker != null) {
                picker.setEnabled(enabled);
            }
        }
        btnConfirm.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    private boolean isPlayPhase(SkockoSessionRepository.GameState state) {
        String phase = safePhase(state);
        return SkockoSessionRepository.PHASE_ROUND1_ACTIVE.equals(phase)
                || SkockoSessionRepository.PHASE_ROUND1_TAKEOVER.equals(phase)
                || SkockoSessionRepository.PHASE_ROUND2_ACTIVE.equals(phase)
                || SkockoSessionRepository.PHASE_ROUND2_TAKEOVER.equals(phase);
    }

    private boolean isTakeoverPhase(String phase) {
        return SkockoSessionRepository.PHASE_ROUND1_TAKEOVER.equals(phase)
                || SkockoSessionRepository.PHASE_ROUND2_TAKEOVER.equals(phase);
    }

    private String takeoverPhaseFor(String activePhase) {
        return SkockoSessionRepository.PHASE_ROUND1_ACTIVE.equals(activePhase)
                ? SkockoSessionRepository.PHASE_ROUND1_TAKEOVER
                : SkockoSessionRepository.PHASE_ROUND2_TAKEOVER;
    }

    private boolean isMyTurn(SkockoSessionRepository.GameState state) {
        switch (safePhase(state)) {
            case SkockoSessionRepository.PHASE_ROUND1_ACTIVE:
                return isOwner;
            case SkockoSessionRepository.PHASE_ROUND1_TAKEOVER:
                return !isOwner;
            case SkockoSessionRepository.PHASE_ROUND2_ACTIVE:
                return !isOwner;
            case SkockoSessionRepository.PHASE_ROUND2_TAKEOVER:
                return isOwner;
            default:
                return false;
        }
    }

    private int phaseDurationSeconds(String phase) {
        return isTakeoverPhase(phase) ? TAKEOVER_DURATION_SECONDS : ACTIVE_DURATION_SECONDS;
    }

    private int getRemainingSeconds(SkockoSessionRepository.GameState state, int duration) {
        if (state.phaseStartedAtMs == null) {
            return duration;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - state.phaseStartedAtMs);
        int remaining = duration - (int) (elapsedMs / 1000L);
        return Math.max(0, Math.min(duration, remaining));
    }

    private void startSoloMode() {
        soloRound1Score = 0;
        soloRound2Score = 0;
        soloFinished = false;
        startSoloRound(1);
    }

    private void startSoloRound(int round) {
        soloRound = round;
        soloSecret = generateSecret();
        soloCurrentRow = 0;
        soloRoundRunning = true;
        clearBoard();
        clearPreview();
        tvRoundLabel.setText("RUNDA " + round + "/2 — PRONADJI TACNU KOMBINACIJU");
        setInputEnabled(true);
        startSoloTimer();
    }

    private void startSoloTimer() {
        stopTimer();
        progressTimer.setMax(ACTIVE_DURATION_SECONDS);
        progressTimer.setProgress(ACTIVE_DURATION_SECONDS);
        tvTimer.setText(String.valueOf(ACTIVE_DURATION_SECONDS));
        resetTimerStyle();

        countDownTimer = new CountDownTimer(ACTIVE_DURATION_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(String.valueOf(secondsLeft));
                progressTimer.setProgress(secondsLeft);
                if (secondsLeft <= 10) {
                    tvTimer.setTextColor(Color.parseColor("#E53935"));
                    progressTimer.setIndicatorColor(Color.parseColor("#E53935"));
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                endSoloRound(false, 0);
            }
        }.start();
    }

    private void confirmGuessSolo() {
        if (!soloRoundRunning || currentGuess.size() < 4) {
            return;
        }

        List<Integer> guess = new ArrayList<>(currentGuess);
        for (int c = 0; c < 4; c++) {
            setSymbolText(gridSlots[soloCurrentRow][c], guess.get(c));
        }

        int[] feedback = computeFeedback(guess, soloSecret);
        applyFeedback(soloCurrentRow, feedback);

        boolean won = feedback[0] == 4;
        soloCurrentRow++;
        clearPreview();

        if (won) {
            int points = soloCurrentRow <= 2 ? 20 : (soloCurrentRow <= 4 ? 15 : 10);
            endSoloRound(true, points);
        } else if (soloCurrentRow >= 6) {
            endSoloRound(false, 0);
        }
    }

    private void endSoloRound(boolean guessed, int points) {
        if (!soloRoundRunning) {
            return;
        }
        soloRoundRunning = false;
        stopTimer();
        setInputEnabled(false);

        if (soloRound == 1) {
            soloRound1Score = points;
        } else {
            soloRound2Score = points;
        }
        updateScoreViews(soloRound1Score, soloRound2Score);
        tvRoundLabel.setText("RUNDA " + soloRound + "/2 zavrsena. "
                + (guessed ? "+" + points + " bodova." : "Kombinacija nije pogodjena."));

        if (soloRound == 1) {
            tvRoundLabel.postDelayed(() -> startSoloRound(2), 1500);
        } else {
            finishSolo();
        }
    }

    private void finishSolo() {
        soloFinished = true;
        int totalScore = soloRound1Score + soloRound2Score;
        btnBack.setText(challengeMode ? "Nazad u izazov" : "Zatvori");
        tvRoundLabel.setText("Kraj igre. Ukupno: " + totalScore + " bodova.");

        if (challengeMode) {
            submitChallengeScore(totalScore);
        } else {
            Toast.makeText(this, "Kraj igre. Rezultat: " + totalScore, Toast.LENGTH_LONG).show();
        }
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
                            Toast.makeText(SkockoActivity.this, "Rezultat izazova je sacuvan.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        challengeScoreSubmitted = false;
                        runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private List<Integer> generateSecret() {
        List<Integer> secret = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            secret.add((int) (Math.random() * SYMBOLS.size()));
        }
        return secret;
    }

    private int[] computeFeedback(List<Integer> guess, List<Integer> secret) {
        boolean[] secretUsed = new boolean[4];
        boolean[] guessUsed = new boolean[4];
        int correct = 0;
        int present = 0;

        for (int i = 0; i < 4; i++) {
            if (guess.get(i).equals(secret.get(i))) {
                correct++;
                secretUsed[i] = true;
                guessUsed[i] = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!secretUsed[j] && guess.get(i).equals(secret.get(j))) {
                    present++;
                    secretUsed[j] = true;
                    break;
                }
            }
        }

        return new int[]{correct, present};
    }

    private void applyFeedback(int row, int[] feedback) {
        int correct = feedback[0];
        int present = feedback[1];
        int absent = 4 - correct - present;

        int dotIdx = 0;
        for (int i = 0; i < correct; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_CORRECT);
        for (int i = 0; i < present; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_PRESENT);
        for (int i = 0; i < absent; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_ABSENT);
    }

    private void setSymbolText(TextView view, int symbolIndex) {
        if (symbolIndex < 0 || symbolIndex >= SYMBOLS.size()) {
            return;
        }
        String symbol = SYMBOLS.get(symbolIndex);
        view.setText(symbol);
        if (symbol.equals(SYM_SRCE)) {
            view.setTextColor(Color.parseColor("#E53935"));
        } else if (symbol.equals(SYM_KVADRAT)) {
            view.setTextColor(Color.parseColor("#1A1A1A"));
        } else {
            view.setTextColor(Color.WHITE);
        }
    }

    private String joinRow(List<Integer> guess) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < guess.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(guess.get(i));
        }
        return builder.toString();
    }

    private List<Integer> parseRow(String row) {
        List<Integer> values = new ArrayList<>();
        if (row == null || row.trim().isEmpty()) {
            return values;
        }
        for (String part : row.split(",")) {
            try {
                values.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private void updatePlayerNames() {
        if (!syncedMode || sessionInfo == null) {
            return;
        }
        tvPlayer1Name.setText(sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        tvPlayer2Name.setText(sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
    }

    private void updateScoreViews(int firstScore, int secondScore) {
        lastOwnerGameScore = firstScore;
        lastGuestGameScore = secondScore;
        if (partyId != null) {
            tvPlayer1Score.setText(firstScore + " bod | Partija: " + (partyOwnerTotal + firstScore));
            tvPlayer2Score.setText(secondScore + " bod | Partija: " + (partyGuestTotal + secondScore));
        } else {
            tvPlayer1Score.setText(firstScore + " bod");
            tvPlayer2Score.setText(secondScore + " bod");
        }
    }

    private String determineWinner(int ownerScore, int guestScore) {
        if (ownerScore > guestScore) return "owner";
        if (guestScore > ownerScore) return "guest";
        return "draw";
    }

    private String buildFinalResultMessage(int ownerScore, int guestScore, String winner) {
        if ("draw".equals(winner)) {
            return "Nereseno! Rezultat je " + ownerScore + " : " + guestScore;
        }
        boolean currentUserWon = (isOwner && "owner".equals(winner)) || (!isOwner && "guest".equals(winner));
        return (currentUserWon ? "Pobedili ste! " : "Izgubili ste. ")
                + "Rezultat je " + ownerScore + " : " + guestScore;
    }

    private SkockoSessionRepository.RepositoryCallback repositoryToastCallback() {
        return new SkockoSessionRepository.RepositoryCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        };
    }

    private SkockoSessionRepository.RepositoryCallback repositoryErrorResetCallback() {
        return new SkockoSessionRepository.RepositoryCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
                phaseAdvanceRequested = false;
                runOnUiThread(() -> Toast.makeText(SkockoActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        };
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
