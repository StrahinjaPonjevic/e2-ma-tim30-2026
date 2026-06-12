package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.koznazna.InMemoryQuizQuestionRepository;
import com.example.slagalica.koznazna.KoZnaZnaGame;
import com.example.slagalica.koznazna.QuizQuestion;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public class KoZnaZnaActivity extends AppCompatActivity {

    private static final long QUESTION_DURATION_MS = 5_000L;
    private static final long TIMER_TICK_MS = 100L;

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

    private KoZnaZnaGame game;
    private CountDownTimer countDownTimer;
    private long timeLeftMs;
    private boolean questionFinished = false;
    private int activePlayerIndex = KoZnaZnaGame.PLAYER_ONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        game = new KoZnaZnaGame(new InMemoryQuizQuestionRepository().getQuestions());

        bindViews();
        setupHeader();
        bindListeners();
        startQuestion();
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

    private void setupHeader() {
        tvPlayer1Name.setText("Igrač 1");
        tvPlayer2Name.setText("Igrač 2");
        tvRoundLabel.setText("RUNDA 1/1 — KO ZNA ZNA");
        updateScoreViews();
    }

    private void bindListeners() {
        btnAnswerA.setOnClickListener(v -> handleAnswer(0));
        btnAnswerB.setOnClickListener(v -> handleAnswer(1));
        btnAnswerC.setOnClickListener(v -> handleAnswer(2));
        btnAnswerD.setOnClickListener(v -> handleAnswer(3));

        btnNextQuestion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!questionFinished) {
                    return;
                }

                if (game.hasNextQuestion()) {
                    game.moveToNextQuestion();
                    startQuestion();
                } else {
                    Toast.makeText(
                            KoZnaZnaActivity.this,
                            buildFinalResultMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void startQuestion() {
        stopTimer();

        QuizQuestion question = game.getCurrentQuestion();
        questionFinished = false;
        activePlayerIndex = KoZnaZnaGame.PLAYER_ONE;
        timeLeftMs = QUESTION_DURATION_MS;

        tvQuestionCounter.setText(
                "Pitanje " + game.getCurrentQuestionNumber() + "/" + game.getQuestionCount()
        );
        tvTurnInfo.setText("Na potezu: Igrač 1");
        tvQuestion.setText(question.getText());

        String[] answers = question.getAnswers();
        btnAnswerA.setText("A) " + answers[0]);
        btnAnswerB.setText("B) " + answers[1]);
        btnAnswerC.setText("C) " + answers[2]);
        btnAnswerD.setText("D) " + answers[3]);

        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText(game.hasNextQuestion() ? "Sledeće pitanje" : "Prikaži rezultat");

        enableAnswerButtons(true);
        resetTimerStyle();
        startTimer();
    }

    private void handleAnswer(int answerIndex) {
        if (questionFinished) {
            return;
        }

        long elapsedMs = QUESTION_DURATION_MS - timeLeftMs;
        game.recordAnswer(activePlayerIndex, answerIndex, elapsedMs);

        if (activePlayerIndex == KoZnaZnaGame.PLAYER_ONE) {
            activePlayerIndex = KoZnaZnaGame.PLAYER_TWO;
            tvTurnInfo.setText("Na potezu: Igrač 2");
            Toast.makeText(this, "Igrač 1 je odgovorio. Sada odgovara Igrač 2.", Toast.LENGTH_SHORT).show();
            return;
        }

        finishQuestion(false);
    }

    private void startTimer() {
        progressTimer.setMax((int) (QUESTION_DURATION_MS / 1000));
        progressTimer.setProgress((int) (QUESTION_DURATION_MS / 1000));
        tvTimer.setText(String.valueOf(QUESTION_DURATION_MS / 1000));

        countDownTimer = new CountDownTimer(QUESTION_DURATION_MS, TIMER_TICK_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMs = millisUntilFinished;
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
                timeLeftMs = 0L;
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                finishQuestion(true);
            }
        }.start();
    }

    private void finishQuestion(boolean timeExpired) {
        if (questionFinished) {
            return;
        }

        questionFinished = true;
        stopTimer();
        enableAnswerButtons(false);

        boolean playerOneAnswered = game.hasPlayerAnswered(KoZnaZnaGame.PLAYER_ONE);
        boolean playerTwoAnswered = game.hasPlayerAnswered(KoZnaZnaGame.PLAYER_TWO);
        KoZnaZnaGame.QuestionOutcome outcome = game.finishCurrentQuestion();
        updateScoreViews();

        if (timeExpired && !playerOneAnswered && !playerTwoAnswered) {
            tvTurnInfo.setText("Vreme je isteklo. Niko nije odgovorio.");
        } else if (timeExpired) {
            tvTurnInfo.setText("Vreme je isteklo. Pitanje je zaključeno.");
        } else {
            tvTurnInfo.setText("Pitanje je završeno.");
        }

        btnNextQuestion.setEnabled(true);
        Toast.makeText(this, outcome.getSummary(), Toast.LENGTH_LONG).show();
    }

    private void updateScoreViews() {
        tvPlayer1Score.setText(game.getPlayerScore(KoZnaZnaGame.PLAYER_ONE) + " bodova");
        tvPlayer2Score.setText(game.getPlayerScore(KoZnaZnaGame.PLAYER_TWO) + " bodova");
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

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private String buildFinalResultMessage() {
        int playerOne = game.getPlayerScore(KoZnaZnaGame.PLAYER_ONE);
        int playerTwo = game.getPlayerScore(KoZnaZnaGame.PLAYER_TWO);

        if (playerOne == playerTwo) {
            return "Kraj igre! Nerešeno je: " + playerOne + " : " + playerTwo;
        }

        int winner = playerOne > playerTwo ? 1 : 2;
        return "Kraj igre! Pobedio je Igrač " + winner + ". Rezultat je " + playerOne + " : " + playerTwo;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}
