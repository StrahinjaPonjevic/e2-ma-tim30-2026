package com.example.slagalica;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.asocijacije.AsocijacijeSessionRepository;
import com.example.slagalica.asocijacije.AsocijacijeSet;
import com.example.slagalica.asocijacije.FirestoreAsocijacijeRepository;
import com.example.slagalica.challenge.ChallengeRepository;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AsocijacijeActivity extends AppCompatActivity {

    private static final int ROUND_DURATION_SECONDS = 120;
    private static final char[] COLUMNS = {'A', 'B', 'C', 'D'};

    private static final int COLOR_A = Color.parseColor("#5C4DB1");
    private static final int COLOR_B = Color.parseColor("#7B5EA7");
    private static final int COLOR_C = Color.parseColor("#9C6FBE");
    private static final int COLOR_D = Color.parseColor("#B48FD4");
    private static final int COLOR_SOLVED = Color.parseColor("#2E7D32");
    private static final int COLOR_REVEALED = Color.parseColor("#78909C");

    private TextView tvPlayer1Name, tvPlayer1Score, tvPlayer2Name, tvPlayer2Score;
    private TextView tvRoundLabel, tvTimer;
    private CircularProgressIndicator progressTimer;
    private Button[] cellsA, cellsB, cellsC, cellsD;
    private TextView tvAnswerA, tvAnswerB, tvAnswerC, tvAnswerD, tvAnswerFinal;
    private EditText etGuess;
    private Button btnClear, btnConfirm, btnPass, btnBack;

    private PartyRepository partyRepository;
    private ChallengeRepository challengeRepository;
    private FirestoreAsocijacijeRepository setRepository;
    private AsocijacijeSessionRepository sessionRepository;
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
    private boolean turnFlipRequested = false;
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

    private AsocijacijeSessionRepository.SessionInfo sessionInfo;
    private AsocijacijeSessionRepository.GameState currentState;
    private String lastObservedPhase;
    private ListenerRegistration gameListener;
    private ListenerRegistration partyListener;
    private CountDownTimer countDownTimer;

    private List<AsocijacijeSet> soloSets;
    private int soloRound = 1;
    private Set<String> soloOpened = new HashSet<>();
    private Map<String, String> soloSolved = new HashMap<>();
    private boolean soloFinalSolved = false;
    private int soloScore = 0;
    private int soloFinals = 0;
    private boolean soloRoundRunning = false;
    private boolean soloFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        partyRepository = new PartyRepository();
        challengeRepository = new ChallengeRepository();
        setRepository = new FirestoreAsocijacijeRepository();
        sessionRepository = new AsocijacijeSessionRepository();
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
            gameKey = "asocijacije";
        }
        if (gameDocId == null || gameDocId.trim().isEmpty()) {
            gameDocId = sessionId != null ? sessionId : "asocijacije_local";
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        syncedMode = !challengeMode && sessionId != null && !sessionId.trim().isEmpty();

        bindViews();
        bindListeners();
        setupInitialHeader();

        if (syncedMode) {
            loadSessionAndStart();
        } else {
            startSoloMode();
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

        cellsA = new Button[]{findViewById(R.id.cellA1), findViewById(R.id.cellA2),
                findViewById(R.id.cellA3), findViewById(R.id.cellA4)};
        cellsB = new Button[]{findViewById(R.id.cellB1), findViewById(R.id.cellB2),
                findViewById(R.id.cellB3), findViewById(R.id.cellB4)};
        cellsC = new Button[]{findViewById(R.id.cellC1), findViewById(R.id.cellC2),
                findViewById(R.id.cellC3), findViewById(R.id.cellC4)};
        cellsD = new Button[]{findViewById(R.id.cellD1), findViewById(R.id.cellD2),
                findViewById(R.id.cellD3), findViewById(R.id.cellD4)};

        tvAnswerA = findViewById(R.id.tvAnswerA);
        tvAnswerB = findViewById(R.id.tvAnswerB);
        tvAnswerC = findViewById(R.id.tvAnswerC);
        tvAnswerD = findViewById(R.id.tvAnswerD);
        tvAnswerFinal = findViewById(R.id.tvAnswerFinal);

        etGuess = findViewById(R.id.etGuess);
        btnClear = findViewById(R.id.btnClear);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnPass = findViewById(R.id.btnPass);
        btnBack = findViewById(R.id.btnBack);
    }

    private void bindListeners() {
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            cellsA[i].setOnClickListener(v -> onCellClicked('A', idx));
            cellsB[i].setOnClickListener(v -> onCellClicked('B', idx));
            cellsC[i].setOnClickListener(v -> onCellClicked('C', idx));
            cellsD[i].setOnClickListener(v -> onCellClicked('D', idx));
        }

        btnClear.setOnClickListener(v -> etGuess.setText(""));

        btnConfirm.setOnClickListener(v -> {
            String guess = etGuess.getText().toString().trim();
            if (guess.isEmpty()) return;
            etGuess.setText("");
            if (syncedMode) {
                handleGuessSynced(guess);
            } else {
                handleGuessSolo(guess);
            }
        });

        etGuess.setOnEditorActionListener((v, actionId, event) -> {
            btnConfirm.performClick();
            return true;
        });

        btnPass.setOnClickListener(v -> handlePass());

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
        AsocijacijeSessionRepository.GameState state = currentState;
        if (state == null || AsocijacijeSessionRepository.PHASE_FINISHED.equals(state.phase)) {
            finish();
            return;
        }
        sessionRepository.finishGame(gameDocId, state.ownerScore, state.guestScore,
                otherSide(mySide()), "Protivnik je napustio igru.",
                new AsocijacijeSessionRepository.RepositoryCallback() {
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
            return currentState != null && AsocijacijeSessionRepository.PHASE_FINISHED.equals(currentState.phase);
        }
        return soloFinished;
    }

    private void setupInitialHeader() {
        tvPlayer1Name.setText(syncedMode ? "Igrac 1" : "Ti");
        tvPlayer2Name.setText(syncedMode ? "Igrac 2" : "");
        tvPlayer1Score.setText("0 bodova");
        tvPlayer2Score.setText(syncedMode ? "0 bodova" : "");
        tvRoundLabel.setText("Priprema igre...");
        tvTimer.setText(String.valueOf(ROUND_DURATION_SECONDS));
        progressTimer.setMax(ROUND_DURATION_SECONDS);
        progressTimer.setProgress(ROUND_DURATION_SECONDS);
        setInputEnabled(false, false, false);
        if (!syncedMode) {
            btnPass.setEnabled(false);
        }
    }

    private void loadSessionAndStart() {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            Toast.makeText(this, "Sesija nije pronadjena", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sessionRepository.loadSessionInfo(sessionId, new AsocijacijeSessionRepository.SessionInfoCallback() {
            @Override
            public void onSuccess(AsocijacijeSessionRepository.SessionInfo info) {
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
                    Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void observeGame() {
        gameListener = sessionRepository.observeGame(gameDocId, new AsocijacijeSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(AsocijacijeSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
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

        flipTurnAfterForfeitIfNeeded(currentState);
    }

    private void handleGameState(AsocijacijeSessionRepository.GameState gameState) {
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

        if (mySide().equals(gameState.activeSide)) {
            turnFlipRequested = false;
        }

        if (!safePhase(gameState).equals(lastObservedPhase)) {
            phaseAdvanceRequested = false;
            resultAdvanceScheduled = false;
            stopTimer();
            handlePhaseEntered(gameState);
        } else {
            renderState(gameState);
        }

        flipTurnAfterForfeitIfNeeded(gameState);
        lastObservedPhase = safePhase(gameState);
    }

    private String safePhase(AsocijacijeSessionRepository.GameState state) {
        return state.phase != null ? state.phase : "";
    }

    private void initializeGame() {
        initializeRequested = true;
        tvRoundLabel.setText("Priprema asocijacija iz baze...");
        setRepository.loadSets(2, new FirestoreAsocijacijeRepository.LoadSetsCallback() {
            @Override
            public void onSuccess(List<AsocijacijeSet> sets) {
                if (sets.size() < 2) {
                    runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this,
                            "Nema dovoljno asocijacija u bazi", Toast.LENGTH_LONG).show());
                    return;
                }
                sessionRepository.initializeGame(gameDocId, sessionInfo, sets,
                        new AsocijacijeSessionRepository.RepositoryCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError(String message) {
                                initializeRequested = false;
                                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_LONG).show());
                            }
                        });
            }

            @Override
            public void onError(String message) {
                initializeRequested = false;
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showWaitingForGameState() {
        currentState = null;
        stopTimer();
        setInputEnabled(false, false, false);
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
                sessionRepository.fetchGameOnce(gameDocId, new AsocijacijeSessionRepository.GameStateListener() {
                    @Override
                    public void onGameStateChanged(AsocijacijeSessionRepository.GameState gameState) {
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

    private void handlePhaseEntered(AsocijacijeSessionRepository.GameState state) {
        renderState(state);

        if (AsocijacijeSessionRepository.PHASE_FINISHED.equals(state.phase)) {
            showFinalResult(state);
            return;
        }

        if (AsocijacijeSessionRepository.PHASE_ROUND_RESULT.equals(state.phase)) {
            stopTimer();
            scheduleResultAdvance(state);
            return;
        }

        startRoundTimer(state);
    }

    private void scheduleResultAdvance(AsocijacijeSessionRepository.GameState state) {
        if (!canControlGameFlow || resultAdvanceScheduled) {
            return;
        }
        resultAdvanceScheduled = true;
        final int roundAtSchedule = state.currentRound;
        tvRoundLabel.postDelayed(() -> {
            if (currentState == null
                    || !AsocijacijeSessionRepository.PHASE_ROUND_RESULT.equals(currentState.phase)
                    || currentState.currentRound != roundAtSchedule
                    || !canControlGameFlow) {
                return;
            }
            if (roundAtSchedule == 1) {
                String round2Side = opponentForfeited ? mySide() : AsocijacijeSessionRepository.SIDE_GUEST;
                sessionRepository.advancePhase(gameDocId, AsocijacijeSessionRepository.PHASE_ROUND2, 2,
                        round2Side, true, new ArrayList<>(), new HashMap<>(), null,
                        currentState.ownerScore, currentState.guestScore,
                        currentState.ownerFinals, currentState.guestFinals, "",
                        repositoryToastCallback());
            } else {
                finishSyncedGame(currentState);
            }
        }, 3000);
    }

    private void finishSyncedGame(AsocijacijeSessionRepository.GameState state) {
        String winner = determineWinner(state.ownerScore, state.guestScore);
        String message = buildFinalResultMessage(state.ownerScore, state.guestScore, winner);
        sessionRepository.finishGame(gameDocId, state.ownerScore, state.guestScore, winner, message,
                new AsocijacijeSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                        recordStatsIfNeeded(state, winner);
                        finishPartyGameIfNeeded(state.ownerScore, state.guestScore);
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void recordStatsIfNeeded(AsocijacijeSessionRepository.GameState state, String winner) {
        if (statsRecorded || partyId == null || !countsForStats || sessionInfo == null
                || sessionInfo.ownerId == null || sessionInfo.guestId == null) {
            return;
        }
        statsRecorded = true;
        profileStatsUpdater.recordAsocijacije(sessionInfo.ownerId, sessionInfo.guestId,
                state.ownerScore, state.guestScore, winner, state.ownerFinals, state.guestFinals, 2);
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
                        runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void showFinalResult(AsocijacijeSessionRepository.GameState state) {
        stopTimer();
        setInputEnabled(false, false, false);
        btnBack.setText(partyId != null ? "Nazad u partiju" : "Zatvori");
        String message = state.resultMessage != null && !state.resultMessage.trim().isEmpty()
                ? state.resultMessage
                : buildFinalResultMessage(state.ownerScore, state.guestScore, state.winner);
        tvRoundLabel.setText(message);
    }

    private void onCellClicked(char column, int index) {
        if (!syncedMode) {
            onSoloCellClicked(column, index);
            return;
        }

        AsocijacijeSessionRepository.GameState state = currentState;
        if (state == null || !isActivePhase(state) || !isMyTurn(state) || !effectiveMustOpen(state)) {
            return;
        }

        String key = fieldKey(column, index);
        if (state.openedFields.contains(key) || state.solvedColumns.containsKey(String.valueOf(column))) {
            return;
        }

        List<String> opened = new ArrayList<>(state.openedFields);
        opened.add(key);

        sessionRepository.updateMove(gameDocId, state.activeSide, false, opened,
                state.solvedColumns, state.finalSolvedBy,
                state.ownerScore, state.guestScore, state.ownerFinals, state.guestFinals,
                "", repositoryToastCallback());
    }

    private void handleGuessSynced(String rawGuess) {
        AsocijacijeSessionRepository.GameState state = currentState;
        if (state == null || !isActivePhase(state) || !isMyTurn(state)
                || effectiveMustOpen(state) || phaseAdvanceRequested) {
            return;
        }

        AsocijacijeSet set = state.activeSet();
        if (set == null) {
            return;
        }

        String guess = normalize(rawGuess);
        String side = mySide();
        int ownerScore = state.ownerScore;
        int guestScore = state.guestScore;

        for (char column : COLUMNS) {
            String colKey = String.valueOf(column);
            if (state.solvedColumns.containsKey(colKey)) {
                continue;
            }
            if (guess.equalsIgnoreCase(normalize(set.getColumnSolution(column)))) {
                int points = 2 + hiddenCount(column, state.openedFields);
                if (isOwner) ownerScore += points;
                else guestScore += points;

                Map<String, String> solved = new HashMap<>(state.solvedColumns);
                solved.put(colKey, side);

                sessionRepository.updateMove(gameDocId, state.activeSide, false, state.openedFields,
                        solved, state.finalSolvedBy, ownerScore, guestScore,
                        state.ownerFinals, state.guestFinals,
                        "Kolona " + column + " resena! +" + points + " bodova.",
                        repositoryToastCallback());
                return;
            }
        }

        if (guess.equalsIgnoreCase(normalize(set.getFinalSolution()))) {
            int points = calcFinalPoints(state);
            int ownerFinals = state.ownerFinals;
            int guestFinals = state.guestFinals;
            if (isOwner) {
                ownerScore += points;
                ownerFinals++;
            } else {
                guestScore += points;
                guestFinals++;
            }

            phaseAdvanceRequested = true;
            sessionRepository.advancePhase(gameDocId, AsocijacijeSessionRepository.PHASE_ROUND_RESULT,
                    state.currentRound, state.activeSide, false, state.openedFields, state.solvedColumns,
                    side, ownerScore, guestScore, ownerFinals, guestFinals,
                    "Konacno resenje pogodjeno! +" + points + " bodova.",
                    repositoryErrorResetCallback());
            return;
        }

        String nextSide = opponentForfeited ? side : otherSide(side);
        sessionRepository.updateMove(gameDocId, nextSide, true, state.openedFields,
                state.solvedColumns, state.finalSolvedBy, ownerScore, guestScore,
                state.ownerFinals, state.guestFinals,
                "Netacan odgovor.", repositoryToastCallback());
    }

    private void handlePass() {
        if (!syncedMode) {
            return;
        }
        AsocijacijeSessionRepository.GameState state = currentState;
        if (state == null || !isActivePhase(state) || !isMyTurn(state) || effectiveMustOpen(state)) {
            return;
        }
        String side = mySide();
        String nextSide = opponentForfeited ? side : otherSide(side);
        sessionRepository.updateMove(gameDocId, nextSide, true, state.openedFields,
                state.solvedColumns, state.finalSolvedBy,
                state.ownerScore, state.guestScore, state.ownerFinals, state.guestFinals,
                "Potez je prepusten.", repositoryToastCallback());
    }

    private void flipTurnAfterForfeitIfNeeded(AsocijacijeSessionRepository.GameState state) {
        if (!opponentForfeited || state == null || turnFlipRequested || phaseAdvanceRequested
                || !isActivePhase(state)) {
            return;
        }
        if (mySide().equals(state.activeSide)) {
            return;
        }
        turnFlipRequested = true;
        sessionRepository.updateMove(gameDocId, mySide(), true, state.openedFields,
                state.solvedColumns, state.finalSolvedBy,
                state.ownerScore, state.guestScore, state.ownerFinals, state.guestFinals,
                "Protivnik je odustao. Nastavljate bez cekanja.",
                new AsocijacijeSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        turnFlipRequested = false;
                    }
                });
    }

    private void startRoundTimer(AsocijacijeSessionRepository.GameState state) {
        stopTimer();

        int secondsLeft = getRemainingSeconds(state);
        progressTimer.setMax(ROUND_DURATION_SECONDS);
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
        AsocijacijeSessionRepository.GameState state = currentState;
        if (state == null || phaseAdvanceRequested || !isActivePhase(state)) {
            return;
        }
        boolean shouldWrite = isMyTurn(state) || (canControlGameFlow && opponentForfeited);
        if (!shouldWrite) {
            return;
        }
        phaseAdvanceRequested = true;
        sessionRepository.advancePhase(gameDocId, AsocijacijeSessionRepository.PHASE_ROUND_RESULT,
                state.currentRound, state.activeSide, false, state.openedFields, state.solvedColumns,
                state.finalSolvedBy, state.ownerScore, state.guestScore,
                state.ownerFinals, state.guestFinals, "Vreme je isteklo.",
                repositoryErrorResetCallback());
    }

    private void renderState(AsocijacijeSessionRepository.GameState state) {
        AsocijacijeSet set = state.activeSet();
        if (set == null) {
            return;
        }

        boolean revealAll = AsocijacijeSessionRepository.PHASE_ROUND_RESULT.equals(state.phase)
                || AsocijacijeSessionRepository.PHASE_FINISHED.equals(state.phase);

        renderColumns(set, state.openedFields, state.solvedColumns, state.finalSolvedBy, revealAll);
        updateScoreViews(state.ownerScore, state.guestScore);
        updateStatusLabel(state);
        updateInputForState(state);
    }

    private void updateInputForState(AsocijacijeSessionRepository.GameState state) {
        boolean active = isActivePhase(state);
        boolean myTurn = active && isMyTurn(state);
        boolean mustOpen = myTurn && effectiveMustOpen(state);
        setInputEnabled(mustOpen, myTurn && !mustOpen, myTurn && !mustOpen);
    }

    private void updateStatusLabel(AsocijacijeSessionRepository.GameState state) {
        String base;
        if (AsocijacijeSessionRepository.PHASE_FINISHED.equals(state.phase)) {
            base = "Igra je zavrsena.";
        } else if (AsocijacijeSessionRepository.PHASE_ROUND_RESULT.equals(state.phase)) {
            base = "Runda " + state.currentRound + "/2 je zavrsena.";
        } else {
            String roundPrefix = "RUNDA " + state.currentRound + "/2 — ";
            if (isMyTurn(state)) {
                base = roundPrefix + (effectiveMustOpen(state)
                        ? "TVOJ POTEZ: otvori polje"
                        : "Pogadjaj resenje ili pritisni Dalje");
            } else {
                base = roundPrefix + "Protivnik igra...";
            }
        }

        String result = state.resultMessage != null ? state.resultMessage.trim() : "";
        tvRoundLabel.setText(result.isEmpty() ? base : base + " " + result);
    }

    private boolean isActivePhase(AsocijacijeSessionRepository.GameState state) {
        return AsocijacijeSessionRepository.PHASE_ROUND1.equals(state.phase)
                || AsocijacijeSessionRepository.PHASE_ROUND2.equals(state.phase);
    }

    private boolean isMyTurn(AsocijacijeSessionRepository.GameState state) {
        return isActivePhase(state) && mySide().equals(state.activeSide);
    }

    private String mySide() {
        return isOwner ? AsocijacijeSessionRepository.SIDE_OWNER : AsocijacijeSessionRepository.SIDE_GUEST;
    }

    private String otherSide(String side) {
        return AsocijacijeSessionRepository.SIDE_OWNER.equals(side)
                ? AsocijacijeSessionRepository.SIDE_GUEST
                : AsocijacijeSessionRepository.SIDE_OWNER;
    }

    private boolean effectiveMustOpen(AsocijacijeSessionRepository.GameState state) {
        return state.mustOpen && hasOpenableField(state);
    }

    private boolean hasOpenableField(AsocijacijeSessionRepository.GameState state) {
        for (char column : COLUMNS) {
            if (state.solvedColumns.containsKey(String.valueOf(column))) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                if (!state.openedFields.contains(fieldKey(column, i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int hiddenCount(char column, List<String> openedFields) {
        int hidden = 0;
        for (int i = 0; i < 4; i++) {
            if (!openedFields.contains(fieldKey(column, i))) {
                hidden++;
            }
        }
        return hidden;
    }

    private int calcFinalPoints(AsocijacijeSessionRepository.GameState state) {
        int points = 7;
        for (char column : COLUMNS) {
            if (state.solvedColumns.containsKey(String.valueOf(column))) {
                continue;
            }
            int hidden = hiddenCount(column, state.openedFields);
            points += (hidden == 4) ? 6 : (2 + hidden);
        }
        return points;
    }

    private String fieldKey(char column, int index) {
        return column + String.valueOf(index);
    }

    private void startSoloMode() {
        tvRoundLabel.setText("Priprema asocijacija iz baze...");
        btnPass.setEnabled(false);
        setRepository.loadSets(2, new FirestoreAsocijacijeRepository.LoadSetsCallback() {
            @Override
            public void onSuccess(List<AsocijacijeSet> sets) {
                if (sets.size() < 2) {
                    runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this,
                            "Nema dovoljno asocijacija u bazi", Toast.LENGTH_LONG).show());
                    return;
                }
                soloSets = sets;
                runOnUiThread(() -> startSoloRound(1));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startSoloRound(int round) {
        soloRound = round;
        soloOpened = new HashSet<>();
        soloSolved = new HashMap<>();
        soloFinalSolved = false;
        soloRoundRunning = true;

        renderColumns(soloActiveSet(), new ArrayList<>(soloOpened), soloSolved, null, false);
        tvRoundLabel.setText("RUNDA " + round + "/2 — otvaraj polja i pogadjaj");
        updateScoreViews(soloScore, 0);
        setInputEnabled(true, true, false);
        startSoloTimer();
    }

    private AsocijacijeSet soloActiveSet() {
        return soloSets.get(soloRound - 1);
    }

    private void startSoloTimer() {
        stopTimer();
        progressTimer.setMax(ROUND_DURATION_SECONDS);
        progressTimer.setProgress(ROUND_DURATION_SECONDS);
        tvTimer.setText(String.valueOf(ROUND_DURATION_SECONDS));
        resetTimerStyle();

        countDownTimer = new CountDownTimer(ROUND_DURATION_SECONDS * 1000L, 1000L) {
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
                endSoloRound("Vreme je isteklo.");
            }
        }.start();
    }

    private void onSoloCellClicked(char column, int index) {
        if (!soloRoundRunning || soloSolved.containsKey(String.valueOf(column))) {
            return;
        }
        String key = fieldKey(column, index);
        if (soloOpened.contains(key)) {
            return;
        }
        soloOpened.add(key);
        renderColumns(soloActiveSet(), new ArrayList<>(soloOpened), soloSolved, null, false);
    }

    private void handleGuessSolo(String rawGuess) {
        if (!soloRoundRunning) {
            return;
        }

        AsocijacijeSet set = soloActiveSet();
        String guess = normalize(rawGuess);

        for (char column : COLUMNS) {
            String colKey = String.valueOf(column);
            if (soloSolved.containsKey(colKey)) {
                continue;
            }
            if (guess.equalsIgnoreCase(normalize(set.getColumnSolution(column)))) {
                int points = 2 + soloHiddenCount(column);
                soloScore += points;
                soloSolved.put(colKey, "owner");
                renderColumns(set, new ArrayList<>(soloOpened), soloSolved, null, false);
                updateScoreViews(soloScore, 0);
                tvRoundLabel.setText("RUNDA " + soloRound + "/2 — Kolona " + column + " resena! +" + points);
                return;
            }
        }

        if (guess.equalsIgnoreCase(normalize(set.getFinalSolution()))) {
            int points = 7;
            for (char column : COLUMNS) {
                if (soloSolved.containsKey(String.valueOf(column))) {
                    continue;
                }
                int hidden = soloHiddenCount(column);
                points += (hidden == 4) ? 6 : (2 + hidden);
            }
            soloScore += points;
            soloFinals++;
            soloFinalSolved = true;
            updateScoreViews(soloScore, 0);
            endSoloRound("Konacno resenje pogodjeno! +" + points + " bodova.");
            return;
        }

        Toast.makeText(this, "Netacan odgovor!", Toast.LENGTH_SHORT).show();
        etGuess.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E53935")));
        etGuess.postDelayed(() -> etGuess.setBackgroundTintList(null), 600);
    }

    private int soloHiddenCount(char column) {
        int hidden = 0;
        for (int i = 0; i < 4; i++) {
            if (!soloOpened.contains(fieldKey(column, i))) {
                hidden++;
            }
        }
        return hidden;
    }

    private void endSoloRound(String message) {
        if (!soloRoundRunning) {
            return;
        }
        soloRoundRunning = false;
        stopTimer();
        setInputEnabled(false, false, false);
        renderColumns(soloActiveSet(), new ArrayList<>(soloOpened), soloSolved,
                soloFinalSolved ? "owner" : null, true);
        tvRoundLabel.setText("RUNDA " + soloRound + "/2 zavrsena. " + message);

        if (soloRound == 1) {
            tvRoundLabel.postDelayed(() -> startSoloRound(2), 2500);
        } else {
            finishSolo();
        }
    }

    private void finishSolo() {
        soloFinished = true;
        btnBack.setText(challengeMode ? "Nazad u izazov" : "Zatvori");
        tvRoundLabel.setText("Kraj igre. Ukupno: " + soloScore + " bodova.");
        if (challengeMode) {
            submitChallengeScore(soloScore);
        } else {
            Toast.makeText(this, "Kraj igre. Rezultat: " + soloScore, Toast.LENGTH_LONG).show();
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
                            Toast.makeText(AsocijacijeActivity.this, "Rezultat izazova je sacuvan.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        challengeScoreSubmitted = false;
                        runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderColumns(AsocijacijeSet set, List<String> openedFields, Map<String, String> solvedColumns,
                               String finalSolvedBy, boolean revealAll) {
        renderColumn('A', cellsA, tvAnswerA, COLOR_A, set, openedFields, solvedColumns, revealAll);
        renderColumn('B', cellsB, tvAnswerB, COLOR_B, set, openedFields, solvedColumns, revealAll);
        renderColumn('C', cellsC, tvAnswerC, COLOR_C, set, openedFields, solvedColumns, revealAll);
        renderColumn('D', cellsD, tvAnswerD, COLOR_D, set, openedFields, solvedColumns, revealAll);

        if (finalSolvedBy != null) {
            tvAnswerFinal.setText(set.getFinalSolution());
            tvAnswerFinal.setBackgroundColor(COLOR_SOLVED);
            tvAnswerFinal.setAlpha(1f);
        } else if (revealAll) {
            tvAnswerFinal.setText(set.getFinalSolution());
            tvAnswerFinal.setBackgroundColor(COLOR_REVEALED);
            tvAnswerFinal.setAlpha(1f);
        } else {
            tvAnswerFinal.setText("KONACNO");
            tvAnswerFinal.setBackgroundColor(Color.parseColor("#6200EE"));
            tvAnswerFinal.setAlpha(1f);
        }
    }

    private void renderColumn(char column, Button[] cells, TextView answerView, int baseColor,
                              AsocijacijeSet set, List<String> openedFields, Map<String, String> solvedColumns,
                              boolean revealAll) {
        List<String> items = set.getColumnItems(column);
        boolean solved = solvedColumns.containsKey(String.valueOf(column));

        for (int i = 0; i < 4; i++) {
            boolean opened = openedFields.contains(fieldKey(column, i));
            if (solved) {
                cells[i].setText(items.get(i));
                cells[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_SOLVED));
            } else if (opened) {
                cells[i].setText(items.get(i));
                cells[i].setBackgroundTintList(ColorStateList.valueOf(lighten(baseColor)));
            } else if (revealAll) {
                cells[i].setText(items.get(i));
                cells[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_REVEALED));
            } else {
                cells[i].setText(column + String.valueOf(i + 1));
                cells[i].setBackgroundTintList(ColorStateList.valueOf(baseColor));
            }
        }

        if (solved) {
            answerView.setText(set.getColumnSolution(column));
            answerView.setBackgroundColor(COLOR_SOLVED);
            answerView.setAlpha(1f);
        } else if (revealAll) {
            answerView.setText(set.getColumnSolution(column));
            answerView.setBackgroundColor(COLOR_REVEALED);
            answerView.setAlpha(1f);
        } else {
            answerView.setText(String.valueOf(column));
            answerView.setBackgroundColor(Color.parseColor("#424275"));
            answerView.setAlpha(1f);
        }
    }

    private void setInputEnabled(boolean cellsEnabled, boolean guessEnabled, boolean passEnabled) {
        for (int i = 0; i < 4; i++) {
            cellsA[i].setEnabled(cellsEnabled);
            cellsB[i].setEnabled(cellsEnabled);
            cellsC[i].setEnabled(cellsEnabled);
            cellsD[i].setEnabled(cellsEnabled);
        }
        etGuess.setEnabled(guessEnabled);
        btnConfirm.setEnabled(guessEnabled);
        btnClear.setEnabled(guessEnabled);
        btnPass.setEnabled(passEnabled);
    }

    private void updatePlayerNames() {
        if (!syncedMode || sessionInfo == null) {
            return;
        }
        tvPlayer1Name.setText(sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrac 1");
        tvPlayer2Name.setText(sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrac 2");
    }

    private void updateScoreViews(int ownerScore, int guestScore) {
        lastOwnerGameScore = ownerScore;
        lastGuestGameScore = guestScore;
        if (partyId != null) {
            tvPlayer1Score.setText(ownerScore + " bod | Partija: " + (partyOwnerTotal + ownerScore));
            tvPlayer2Score.setText(guestScore + " bod | Partija: " + (partyGuestTotal + guestScore));
        } else {
            tvPlayer1Score.setText(ownerScore + " bodova");
            tvPlayer2Score.setText(syncedMode ? guestScore + " bodova" : "");
        }
    }

    private int getRemainingSeconds(AsocijacijeSessionRepository.GameState state) {
        if (state.phaseStartedAtMs == null) {
            return ROUND_DURATION_SECONDS;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - state.phaseStartedAtMs);
        int remaining = ROUND_DURATION_SECONDS - (int) (elapsedMs / 1000L);
        return Math.max(0, Math.min(ROUND_DURATION_SECONDS, remaining));
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase()
                .replace('Ž', 'Z')
                .replace('Đ', 'D')
                .replace('Č', 'C')
                .replace('Š', 'S')
                .replace('Ć', 'C');
    }

    private int lighten(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * 1.25f);
        return Color.HSVToColor(hsv);
    }

    private AsocijacijeSessionRepository.RepositoryCallback repositoryToastCallback() {
        return new AsocijacijeSessionRepository.RepositoryCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        };
    }

    private AsocijacijeSessionRepository.RepositoryCallback repositoryErrorResetCallback() {
        return new AsocijacijeSessionRepository.RepositoryCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String message) {
                phaseAdvanceRequested = false;
                runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_SHORT).show());
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
