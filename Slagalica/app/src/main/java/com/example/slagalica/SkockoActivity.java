package com.example.slagalica;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

import com.example.slagalica.party.PartyRepository;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkockoActivity extends AppCompatActivity {

    public static final String SYM_SKOCKO  = "👾";
    public static final String SYM_KRUG    = "⚫";
    public static final String SYM_SRCE    = "♥";
    public static final String SYM_TROUGAO = "🔺";
    public static final String SYM_ZVEZDA  = "⭐";
    public static final String SYM_KVADRAT = "◼";

    private static final List<String> SYMBOLS = Arrays.asList(
            SYM_SKOCKO, SYM_KRUG, SYM_SRCE, SYM_TROUGAO, SYM_ZVEZDA, SYM_KVADRAT
    );

    private static final int COLOR_CORRECT  = Color.parseColor("#43A047"); // zelena – tačna pozicija
    private static final int COLOR_PRESENT  = Color.parseColor("#FBC02D"); // žuta   – tačan znak, pogrešna pos.
    private static final int COLOR_ABSENT   = Color.parseColor("#37474F"); // siva   – nije u kombinaciji
    private static final int COLOR_EMPTY    = Color.parseColor("#2D2D4E"); // prazan slot

    private String[] secret = new String[4];
    private List<String> currentGuess = new ArrayList<>();
    private int currentRow = 0;
    private int currentRound = 1;

    private int player1Score = 0;
    private int player2Score = 0;

    private boolean roundOver = false;

    private TextView[][] gridSlots = new TextView[6][4];

    private View[][] feedbackDots = new View[6][4];

    private TextView[] previewSlots = new TextView[4];

    private CountDownTimer countDownTimer;
    private static final long ROUND_DURATION_MS = 30_000;
    private TextView tvTimer;
    private CircularProgressIndicator progressTimer;

    private TextView tvRoundLabel, tvPlayer1Score, tvPlayer2Score;
    private Button btnBack;
    private PartyRepository partyRepository;
    private String partyId;
    private String gameKey;
    private String currentUserId;
    private boolean gameFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        partyId = getIntent().getStringExtra("partyId");
        gameKey = getIntent().getStringExtra("gameKey");
        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "skocko";
        }
        partyRepository = new PartyRepository();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        bindViews();
        setupSymbolPicker();
        generateSecret();
        startRound();
    }

    private void bindViews() {
        tvRoundLabel    = findViewById(R.id.tvRoundLabel);
        tvTimer         = findViewById(R.id.tvTimer);
        progressTimer   = findViewById(R.id.progressTimer);
        tvPlayer1Score  = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Score  = findViewById(R.id.tvPlayer2Score);

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

        findViewById(R.id.btnConfirm).setOnClickListener(v -> confirmGuess());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearLastSymbol());
        btnBack = findViewById(R.id.btnBack);
        if (partyId != null) {
            btnBack.setText("Odustani");
        }
        btnBack.setOnClickListener(v -> {
            if (partyId != null && !gameFinished) {
                forfeitParty();
            } else {
                finish();
            }
        });
    }

    private void setupSymbolPicker() {
        int[] pickerIds = {
                R.id.btnSkocko, R.id.btnKrug, R.id.btnSrce,
                R.id.btnTrougao, R.id.btnZvezda, R.id.btnKvadrat
        };
        for (int i = 0; i < pickerIds.length; i++) {
            final String symbol = SYMBOLS.get(i);
            findViewById(pickerIds[i]).setOnClickListener(v -> addSymbol(symbol));
        }
    }

    private void generateSecret() {
        for (int i = 0; i < 4; i++) {
            int idx = (int) (Math.random() * SYMBOLS.size());
            secret[i] = SYMBOLS.get(idx);
        }
    }

    private void startRound() {
        currentRow = 0;
        currentGuess.clear();
        roundOver = false;
        clearPreview();
        updateRoundLabel();
        startTimer(ROUND_DURATION_MS);
    }

    private void updateRoundLabel() {
        tvRoundLabel.setText("RUNDA " + currentRound + " — PRONAĐI TAČNU KOMBINACIJU");
    }

    private void addSymbol(String symbol) {
        if (roundOver || currentGuess.size() >= 4) return;
        currentGuess.add(symbol);
        int idx = currentGuess.size() - 1;
        previewSlots[idx].setText(symbol);
        if (symbol.equals(SYM_SRCE))    previewSlots[idx].setTextColor(Color.parseColor("#E53935"));
        else if (symbol.equals(SYM_KVADRAT)) previewSlots[idx].setTextColor(Color.parseColor("#1A1A1A"));
        else                             previewSlots[idx].setTextColor(Color.WHITE);
    }

    private void clearLastSymbol() {
        if (currentGuess.isEmpty()) return;
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

    private void confirmGuess() {
        if (roundOver || currentGuess.size() < 4) return;

        String[] guess = currentGuess.toArray(new String[0]);

        for (int c = 0; c < 4; c++) {
            gridSlots[currentRow][c].setText(guess[c]);
            if (guess[c].equals(SYM_SRCE))       gridSlots[currentRow][c].setTextColor(Color.parseColor("#E53935"));
            else if (guess[c].equals(SYM_KVADRAT)) gridSlots[currentRow][c].setTextColor(Color.parseColor("#1A1A1A"));
            else                                   gridSlots[currentRow][c].setTextColor(Color.WHITE);
        }

        int[] feedback = computeFeedback(guess, secret);
        applyFeedback(currentRow, feedback);

        boolean won = (feedback[0] == 4);
        currentRow++;

        if (won) {
            endRound(true);
        } else if (currentRow >= 6) {
            endRound(false);
        } else {
            clearPreview();
        }
    }

    private int[] computeFeedback(String[] guess, String[] secret) {
        boolean[] secretUsed = new boolean[4];
        boolean[] guessUsed  = new boolean[4];
        int correct = 0, present = 0;

        for (int i = 0; i < 4; i++) {
            if (guess[i].equals(secret[i])) {
                correct++;
                secretUsed[i] = true;
                guessUsed[i]  = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!secretUsed[j] && guess[i].equals(secret[j])) {
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
        int absent  = 4 - correct - present;

        int dotIdx = 0;
        for (int i = 0; i < correct; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_CORRECT);
        for (int i = 0; i < present; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_PRESENT);
        for (int i = 0; i < absent; i++)
            feedbackDots[row][dotIdx++].setBackgroundColor(COLOR_ABSENT);
    }

    private void endRound(boolean guessed) {
        roundOver = true;
        stopTimer();

        int points = 0;
        if (guessed) {
            if (currentRow <= 2)      points = 20;
            else if (currentRow <= 4) points = 15;
            else                      points = 10;
        }

        if (currentRound == 1) {
            player1Score += points;
            tvPlayer1Score.setText(player1Score + " bod");
        } else {
            player2Score += points;
            tvPlayer2Score.setText(player2Score + " bod");
        }

        if (currentRound == 1) {
            currentRound = 2;
            generateSecret();
            startRound();
        } else {
            finishGame();
        }
    }

    private void finishGame() {
        if (gameFinished) {
            return;
        }

        gameFinished = true;
        stopTimer();
        btnBack.setText(partyId != null ? "Nazad u partiju" : "Zatvori");

        int totalScore = player1Score + player2Score;
        if (partyId == null) {
            Toast.makeText(this, "Kraj igre. Rezultat: " + totalScore, Toast.LENGTH_LONG).show();
            return;
        }

        partyRepository.submitPlayerGameScoreAndAdvance(partyId, gameKey, currentUserId, totalScore,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> Toast.makeText(SkockoActivity.this,
                                "Rezultat poslat. Cekanje protivnika ako jos nije zavrsio.",
                                Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onError(String message) {
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

    private void startTimer(long durationMs) {
        progressTimer.setMax(100);
        progressTimer.setProgress(100);

        countDownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secs = (int) (millisUntilFinished / 1000);
                tvTimer.setText(String.valueOf(secs));
                int progress = (int) ((millisUntilFinished * 100) / durationMs);
                progressTimer.setProgress(progress);
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                endRound(false);
            }
        }.start();
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
    }
}
