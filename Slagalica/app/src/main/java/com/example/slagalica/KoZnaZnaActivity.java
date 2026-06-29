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
import com.example.slagalica.koznazna.FirestoreQuizQuestionRepository;
import com.example.slagalica.koznazna.KoZnaZnaEvaluator;
import com.example.slagalica.koznazna.KoZnaZnaSessionRepository;
import com.example.slagalica.koznazna.QuizQuestion;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.ProfileStatsUpdater;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class KoZnaZnaActivity extends AppCompatActivity {

    private static final int QUESTION_COUNT = 5;
    private static final int QUESTION_DURATION_SECONDS = 5;

    private TextView tvPlayer1Name;
    private TextView tvPlayer1Score;
    private TextView tvPlayer2Name;
    private TextView tvPlayer2Score;
    private TextView tvRoundLabel;
    private TextView tvTimer;
    private CircularProgressIndicator progressTimer;
    private TextView tvQuestionCounter;
    private TextView tvTurnInfo;
    private TextView tvQuestion;

    private Button btnAnswerA;
    private Button btnAnswerB;
    private Button btnAnswerC;
    private Button btnAnswerD;
    private Button btnNextQuestion;
    private Button btnBack;

    private FirebaseManager firebaseManager;
    private FirestoreQuizQuestionRepository questionRepository;
    private KoZnaZnaSessionRepository sessionRepository;
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
    private String currentUserId;
    private ListenerRegistration gameListener;
    private CountDownTimer countDownTimer;

    private KoZnaZnaSessionRepository.SessionInfo sessionInfo;
    private KoZnaZnaSessionRepository.GameState currentState;
    private String lastObservedPhase;
    private int lastObservedQuestionIndex = -1;
    private boolean resolutionRequested = false;
    private long currentQuestionStartedAtMs = 0L;
    private Integer pendingLocalAnswerIndex;
    private Long pendingLocalAnswerTimeMs;
    private boolean waitingForGameRetryScheduled = false;
    private boolean challengeScoreSubmitted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        firebaseManager = new FirebaseManager();
        questionRepository = new FirestoreQuizQuestionRepository();
        sessionRepository = new KoZnaZnaSessionRepository();
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
        if (gameDocId == null || gameDocId.trim().isEmpty()) {
            gameDocId = sessionId;
        }
        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "ko_zna_zna";
        }

        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(this, "Sesija nije pronađena", Toast.LENGTH_SHORT).show();
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
        tvQuestionCounter = findViewById(R.id.tvQuestionCounter);
        tvTurnInfo = findViewById(R.id.tvTurnInfo);
        tvQuestion = findViewById(R.id.tvQuestion);

        btnAnswerA = findViewById(R.id.btnAnswerA);
        btnAnswerB = findViewById(R.id.btnAnswerB);
        btnAnswerC = findViewById(R.id.btnAnswerC);
        btnAnswerD = findViewById(R.id.btnAnswerD);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        btnBack = findViewById(R.id.btnBack);
    }

    private void bindListeners() {
        btnAnswerA.setOnClickListener(v -> submitAnswer(0));
        btnAnswerB.setOnClickListener(v -> submitAnswer(1));
        btnAnswerC.setOnClickListener(v -> submitAnswer(2));
        btnAnswerD.setOnClickListener(v -> submitAnswer(3));

        btnNextQuestion.setOnClickListener(v -> handleNextQuestionClick());
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
        tvPlayer1Name.setText("Igrač 1");
        tvPlayer2Name.setText("Igrač 2");
        tvRoundLabel.setText("RUNDA 1/1 — KO ZNA ZNA");
        tvQuestionCounter.setText("Pitanje 1/" + QUESTION_COUNT);
        tvTurnInfo.setText("Učitavanje pitanja...");
        tvTimer.setText(String.valueOf(QUESTION_DURATION_SECONDS));
        progressTimer.setMax(QUESTION_DURATION_SECONDS);
        progressTimer.setProgress(QUESTION_DURATION_SECONDS);
        updateScoreViews(0, 0);
        btnNextQuestion.setEnabled(false);
    }

    private void loadSessionAndStart() {
        sessionRepository.loadSessionInfo(sessionId, new KoZnaZnaSessionRepository.SessionInfoCallback() {
            @Override
            public void onSuccess(KoZnaZnaSessionRepository.SessionInfo info) {
                sessionInfo = info;
                updatePlayerNames();
                observeGame();
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void observeGame() {
        gameListener = sessionRepository.observeGame(gameDocId, new KoZnaZnaSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(KoZnaZnaSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleGameState(KoZnaZnaSessionRepository.GameState gameState) {
        if (gameState == null) {
            if (isOwner && sessionInfo != null) {
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

        if ("question_active".equals(gameState.phase)) {
            boolean questionChanged = !gameState.phase.equals(lastObservedPhase)
                    || gameState.currentQuestionIndex != lastObservedQuestionIndex;
            if (questionChanged) {
                resolutionRequested = false;
                pendingLocalAnswerIndex = null;
                pendingLocalAnswerTimeMs = null;
                startQuestion(gameState);
            } else {
                updateWaitingState(gameState);
            }

            if (isOwner && shouldResolveQuestion(gameState)) {
                resolveCurrentQuestion(gameState);
            }
        } else if ("question_result".equals(gameState.phase)) {
            stopTimer();
            pendingLocalAnswerIndex = null;
            pendingLocalAnswerTimeMs = null;
            showQuestionResult(gameState);
            resolutionRequested = false;
        } else if ("finished".equals(gameState.phase)) {
            stopTimer();
            pendingLocalAnswerIndex = null;
            pendingLocalAnswerTimeMs = null;
            showFinalResult(gameState);
            resolutionRequested = false;
        }

        lastObservedPhase = gameState.phase;
        lastObservedQuestionIndex = gameState.currentQuestionIndex;
    }

    private void initializeGame() {
        tvTurnInfo.setText("Priprema pitanja iz baze...");
        enableAnswerButtons(false);
        questionRepository.loadQuestions(QUESTION_COUNT, new FirestoreQuizQuestionRepository.LoadQuestionsCallback() {
            @Override
            public void onSuccess(List<QuizQuestion> questions) {
                sessionRepository.initializeGame(gameDocId, sessionInfo, questions,
                        new KoZnaZnaSessionRepository.RepositoryCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_LONG).show());
                            }
                        });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startQuestion(KoZnaZnaSessionRepository.GameState gameState) {
        stopTimer();

        QuizQuestion question = getCurrentQuestion(gameState);
        if (question == null) {
            tvTurnInfo.setText("Pitanje nije pronađeno.");
            enableAnswerButtons(false);
            return;
        }

        currentQuestionStartedAtMs = System.currentTimeMillis();
        tvQuestionCounter.setText("Pitanje " + (gameState.currentQuestionIndex + 1) + "/" + gameState.questions.size());
        tvQuestion.setText(question.getText());
        btnAnswerA.setText("A) " + question.getAnswers()[0]);
        btnAnswerB.setText("B) " + question.getAnswers()[1]);
        btnAnswerC.setText("C) " + question.getAnswers()[2]);
        btnAnswerD.setText("D) " + question.getAnswers()[3]);
        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText(isOwner && hasMoreQuestions(gameState) ? "Sledeće pitanje" : "Prikaži rezultat");
        resetTimerStyle();
        updateWaitingState(gameState);
        startTimer();
    }

    private void showWaitingForGameState() {
        currentState = null;
        enableAnswerButtons(false);
        btnNextQuestion.setEnabled(false);
        tvQuestionCounter.setText("Pitanje 1/" + QUESTION_COUNT);
        tvQuestion.setText("Čekanje da vlasnik sesije pokrene partiju...");
        tvTurnInfo.setText("Čekanje da protivnik pokrene igru...");
        tvTimer.setText(String.valueOf(QUESTION_DURATION_SECONDS));
        progressTimer.setMax(QUESTION_DURATION_SECONDS);
        progressTimer.setProgress(QUESTION_DURATION_SECONDS);
    }

    private void scheduleGameRefreshRetry() {
        if (waitingForGameRetryScheduled || isOwner) {
            return;
        }

        waitingForGameRetryScheduled = true;
        tvQuestion.postDelayed(() -> {
            waitingForGameRetryScheduled = false;
            if (currentState == null) {
                refreshGameStateOnce();
            }
        }, 1200);
    }

    private void refreshGameStateOnce() {
        sessionRepository.fetchGameOnce(gameDocId, new KoZnaZnaSessionRepository.GameStateListener() {
            @Override
            public void onGameStateChanged(KoZnaZnaSessionRepository.GameState gameState) {
                runOnUiThread(() -> handleGameState(gameState));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show();
                    scheduleGameRefreshRetry();
                });
            }
        });
    }

    private void updateWaitingState(KoZnaZnaSessionRepository.GameState gameState) {
        boolean iAnswered = isCurrentUserAnswered(gameState);
        enableAnswerButtons(!iAnswered);

        if (iAnswered) {
            tvTurnInfo.setText("Odgovor je poslat. Čekanje protivnika...");
        } else {
            tvTurnInfo.setText("Odgovorite na pitanje u roku od 5 sekundi.");
        }
    }

    private void submitAnswer(int answerIndex) {
        if (currentState == null || !"question_active".equals(currentState.phase)) {
            return;
        }

        if (isCurrentUserAnswered(currentState)) {
            return;
        }

        long answerTimeMs = Math.max(0L, System.currentTimeMillis() - currentQuestionStartedAtMs);
        pendingLocalAnswerIndex = answerIndex;
        pendingLocalAnswerTimeMs = answerTimeMs;
        sessionRepository.submitAnswer(gameDocId, isOwner, answerIndex, answerTimeMs);
        enableAnswerButtons(false);
        tvTurnInfo.setText("Odgovor je poslat. Čekanje protivnika...");
    }

    private void handleNextQuestionClick() {
        if (currentState == null || !"question_result".equals(currentState.phase) || !isOwner) {
            return;
        }

        if (hasMoreQuestions(currentState)) {
            sessionRepository.startNextQuestion(gameDocId, currentState.currentQuestionIndex + 1,
                    new KoZnaZnaSessionRepository.RepositoryCallback() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    });
        } else {
            String winner = determineWinner(currentState.ownerScore, currentState.guestScore);
            sessionRepository.finishGame(gameDocId, currentState.ownerScore, currentState.guestScore, winner,
                    new KoZnaZnaSessionRepository.RepositoryCallback() {
                        @Override
                        public void onSuccess() {
                            if (countsForStats && sessionInfo != null) {
                                profileStatsUpdater.recordKoZnaZna(
                                        sessionInfo.ownerId,
                                        sessionInfo.guestId,
                                        currentState.ownerScore,
                                        currentState.guestScore,
                                        winner,
                                        currentState.ownerCorrectAnswers,
                                        currentState.ownerWrongAnswers,
                                        currentState.guestCorrectAnswers,
                                        currentState.guestWrongAnswers
                                );
                            }
                            finishPartyGameIfNeeded(currentState.ownerScore, currentState.guestScore);
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    });
        }
    }

    private boolean shouldResolveQuestion(KoZnaZnaSessionRepository.GameState gameState) {
        Integer ownerAnswerIndex = gameState.ownerAnswerIndex;
        if (ownerAnswerIndex == null && isOwner && pendingLocalAnswerIndex != null) {
            ownerAnswerIndex = pendingLocalAnswerIndex;
        }

        return !resolutionRequested
                && ownerAnswerIndex != null
                && gameState.guestAnswerIndex != null;
    }

    private void resolveCurrentQuestion(KoZnaZnaSessionRepository.GameState gameState) {
        QuizQuestion question = getCurrentQuestion(gameState);
        if (question == null) {
            return;
        }

        resolutionRequested = true;
        Integer ownerAnswerIndex = gameState.ownerAnswerIndex;
        Long ownerAnswerTimeMs = gameState.ownerAnswerTimeMs;
        if (ownerAnswerIndex == null && isOwner && pendingLocalAnswerIndex != null) {
            ownerAnswerIndex = pendingLocalAnswerIndex;
            ownerAnswerTimeMs = pendingLocalAnswerTimeMs;
        }

        KoZnaZnaEvaluator.EvaluationResult result = KoZnaZnaEvaluator.evaluate(
                question,
                ownerAnswerIndex,
                ownerAnswerTimeMs,
                gameState.guestAnswerIndex,
                gameState.guestAnswerTimeMs
        );

        sessionRepository.publishQuestionResult(gameDocId, gameState, result,
                new KoZnaZnaSessionRepository.RepositoryCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                        resolutionRequested = false;
                        runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void showQuestionResult(KoZnaZnaSessionRepository.GameState gameState) {
        QuizQuestion question = getCurrentQuestion(gameState);
        if (question != null) {
            tvQuestion.setText(question.getText());
            btnAnswerA.setText("A) " + question.getAnswers()[0]);
            btnAnswerB.setText("B) " + question.getAnswers()[1]);
            btnAnswerC.setText("C) " + question.getAnswers()[2]);
            btnAnswerD.setText("D) " + question.getAnswers()[3]);
        }

        tvQuestionCounter.setText("Pitanje " + (gameState.currentQuestionIndex + 1) + "/" + gameState.questions.size());
        tvTurnInfo.setText(gameState.resultMessage != null && !gameState.resultMessage.isEmpty()
                ? gameState.resultMessage
                : "Pitanje je završeno.");
        enableAnswerButtons(false);

        if (isOwner) {
            btnNextQuestion.setEnabled(true);
            btnNextQuestion.setText(hasMoreQuestions(gameState) ? "Sledeće pitanje" : "Prikaži rezultat");
        } else {
            btnNextQuestion.setEnabled(false);
            btnNextQuestion.setText("Čekanje vlasnika sesije...");
        }
    }

    private void showFinalResult(KoZnaZnaSessionRepository.GameState gameState) {
        enableAnswerButtons(false);
        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText("Kraj igre");
        tvQuestionCounter.setText("Partija završena");
        tvTurnInfo.setText(buildFinalResultMessage(gameState));
        if (challengeMode) {
            btnNextQuestion.setEnabled(true);
            btnNextQuestion.setText("Nazad u izazov");
            btnNextQuestion.setOnClickListener(v -> finish());
            submitChallengeScore(gameState.ownerScore);
        } else if (partyId != null) {
            btnNextQuestion.setEnabled(true);
            btnNextQuestion.setText("Nazad u partiju");
            btnNextQuestion.setOnClickListener(v -> finish());
        }
    }

    private void finishPartyGameIfNeeded(int ownerScore, int guestScore) {
        if (partyId == null || !isOwner) {
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
                        runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
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
                            Toast.makeText(KoZnaZnaActivity.this, "Rezultat izazova je sacuvan.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        challengeScoreSubmitted = false;
                        runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startTimer() {
        progressTimer.setMax(QUESTION_DURATION_SECONDS);
        progressTimer.setProgress(QUESTION_DURATION_SECONDS);
        tvTimer.setText(String.valueOf(QUESTION_DURATION_SECONDS));

        countDownTimer = new CountDownTimer(QUESTION_DURATION_SECONDS * 1000L, 100L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(String.valueOf(secondsLeft));
                progressTimer.setProgress(secondsLeft);

                if (secondsLeft <= 2) {
                    tvTimer.setTextColor(Color.parseColor("#E53935"));
                    progressTimer.setIndicatorColor(Color.parseColor("#E53935"));
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                enableAnswerButtons(false);

                if (isOwner && currentState != null && "question_active".equals(currentState.phase) && !resolutionRequested) {
                    tvTurnInfo.setText("Vreme je isteklo. Zaključavanje pitanja...");
                    tvTimer.postDelayed(() -> {
                        if (currentState != null && "question_active".equals(currentState.phase) && !resolutionRequested) {
                            resolveCurrentQuestion(currentState);
                        }
                    }, 350);
                } else {
                    tvTurnInfo.setText("Vreme je isteklo. Čekanje zaključavanja pitanja...");
                }
            }
        }.start();
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void updatePlayerNames() {
        if (sessionInfo == null) {
            return;
        }

        tvPlayer1Name.setText(sessionInfo.ownerUsername != null ? sessionInfo.ownerUsername : "Igrač 1");
        tvPlayer2Name.setText(sessionInfo.guestUsername != null ? sessionInfo.guestUsername : "Igrač 2");
    }

    private void updateScoreViews(int ownerScore, int guestScore) {
        tvPlayer1Score.setText(ownerScore + " bodova");
        tvPlayer2Score.setText(guestScore + " bodova");
    }

    private void enableAnswerButtons(boolean enabled) {
        btnAnswerA.setEnabled(enabled);
        btnAnswerB.setEnabled(enabled);
        btnAnswerC.setEnabled(enabled);
        btnAnswerD.setEnabled(enabled);
    }

    private void resetTimerStyle() {
        tvTimer.setTextColor(Color.parseColor("#1E1E1E"));
        progressTimer.setIndicatorColor(Color.parseColor("#6200EE"));
    }

    private boolean isCurrentUserAnswered(KoZnaZnaSessionRepository.GameState gameState) {
        return isOwner ? gameState.ownerAnswerIndex != null : gameState.guestAnswerIndex != null;
    }

    private QuizQuestion getCurrentQuestion(KoZnaZnaSessionRepository.GameState gameState) {
        if (gameState.questions == null
                || gameState.currentQuestionIndex < 0
                || gameState.currentQuestionIndex >= gameState.questions.size()) {
            return null;
        }
        return gameState.questions.get(gameState.currentQuestionIndex);
    }

    private boolean hasMoreQuestions(KoZnaZnaSessionRepository.GameState gameState) {
        return gameState.currentQuestionIndex < gameState.questions.size() - 1;
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

    private String buildFinalResultMessage(KoZnaZnaSessionRepository.GameState gameState) {
        if ("draw".equals(gameState.winner)) {
            return "Nerešeno! Rezultat je " + gameState.ownerScore + " : " + gameState.guestScore;
        }

        boolean currentUserWon = (isOwner && "owner".equals(gameState.winner))
                || (!isOwner && "guest".equals(gameState.winner));

        if (currentUserWon) {
            return "Pobedili ste! Rezultat je " + gameState.ownerScore + " : " + gameState.guestScore;
        }

        return "Izgubili ste. Rezultat je " + gameState.ownerScore + " : " + gameState.guestScore;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
        if (gameListener != null) {
            gameListener.remove();
        }
    }
}
