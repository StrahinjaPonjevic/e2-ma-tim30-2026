package com.example.slagalica;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.party.PartyRepository;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AsocijacijeActivity extends AppCompatActivity {

    private static final int COLOR_A      = Color.parseColor("#5C4DB1");
    private static final int COLOR_B      = Color.parseColor("#7B5EA7");
    private static final int COLOR_C      = Color.parseColor("#9C6FBE");
    private static final int COLOR_D      = Color.parseColor("#B48FD4");
    private static final int COLOR_SOLVED = Color.parseColor("#2E7D32");
    private static final int COLOR_FINAL  = Color.parseColor("#6200EE");

    private static final long ROUND_DURATION_MS = 120_000;

    private final String[] itemsA = {"Sapun", "Azot", "Kristal", "Govor"};
    private final String[] itemsB = {"Ogledalo", "Escajg", "Medalja", "Nakit"};
    private final String[] itemsC = {"Toplomer", "Ograda", "Svirka", "Istina"};
    private final String[] itemsD = {"Zub", "Groznica", "Ribica", "Ćutanje"};
    private final String solutionA = "TECNI";
    private final String solutionB = "SREBRO";
    private final String solutionC = "ZIVA";
    private final String solutionD = "ZLATO";
    private final String solutionFinal = "METAL";

    private boolean[] revealedA = new boolean[4];
    private boolean[] revealedB = new boolean[4];
    private boolean[] revealedC = new boolean[4];
    private boolean[] revealedD = new boolean[4];
    private boolean solvedA, solvedB, solvedC, solvedD, solvedFinal;

    private boolean canReveal = true;

    private int player1Score = 0;
    private int player2Score = 0;

    private Button[] cellsA, cellsB, cellsC, cellsD;
    private TextView tvAnswerA, tvAnswerB, tvAnswerC, tvAnswerD, tvAnswerFinal;
    private TextView tvPlayer1Name, tvPlayer1Score, tvPlayer2Name, tvPlayer2Score;
    private TextView tvRoundLabel, tvTimer;
    private CircularProgressIndicator progressTimer;
    private EditText etGuess;
    private Button btnClear, btnConfirm, btnBack;

    private CountDownTimer countDownTimer;
    private int timeLeftSeconds;
    private PartyRepository partyRepository;
    private String partyId;
    private String gameKey;
    private String currentUserId;
    private boolean gameFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        partyId = getIntent().getStringExtra("partyId");
        gameKey = getIntent().getStringExtra("gameKey");
        if (gameKey == null || gameKey.trim().isEmpty()) {
            gameKey = "asocijacije";
        }
        partyRepository = new PartyRepository();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = currentUser != null ? currentUser.getUid() : null;

        bindViews();
        setupPlayerInfo();
        setupCellListeners();
        setupBottomButtons();
        startTimer();
    }

    private void bindViews() {
        tvPlayer1Name  = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Score = findViewById(R.id.tvPlayer1Score);
        tvPlayer2Name  = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Score = findViewById(R.id.tvPlayer2Score);
        tvRoundLabel   = findViewById(R.id.tvRoundLabel);
        tvTimer        = findViewById(R.id.tvTimer);
        progressTimer  = findViewById(R.id.progressTimer);

        cellsA = new Button[]{
                findViewById(R.id.cellA1),
                findViewById(R.id.cellA2),
                findViewById(R.id.cellA3),
                findViewById(R.id.cellA4)
        };

        cellsB = new Button[]{
                findViewById(R.id.cellB1),
                findViewById(R.id.cellB2),
                findViewById(R.id.cellB3),
                findViewById(R.id.cellB4)
        };

        cellsC = new Button[]{
                findViewById(R.id.cellC4),
                findViewById(R.id.cellC3),
                findViewById(R.id.cellC2),
                findViewById(R.id.cellC1)
        };

        cellsD = new Button[]{
                findViewById(R.id.cellD4),
                findViewById(R.id.cellD3),
                findViewById(R.id.cellD2),
                findViewById(R.id.cellD1)
        };

        tvAnswerA     = findViewById(R.id.tvAnswerA);
        tvAnswerB     = findViewById(R.id.tvAnswerB);
        tvAnswerC     = findViewById(R.id.tvAnswerC);
        tvAnswerD     = findViewById(R.id.tvAnswerD);
        tvAnswerFinal = findViewById(R.id.tvAnswerFinal);

        etGuess    = findViewById(R.id.etGuess);
        btnClear   = findViewById(R.id.btnClear);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnBack    = findViewById(R.id.btnBack);
    }


    private void setupPlayerInfo() {
        // TODO: ucitaj igrace
        tvPlayer1Name.setText("Igrač 1");
        tvPlayer2Name.setText("Igrač 2");
        updateScoreViews();

        progressTimer.setMax(100);
        progressTimer.setProgress(100);
    }

    private void setupCellListeners() {
        for (int i = 0; i < 4; i++) {
            final int row = i;
            cellsA[i].setOnClickListener(v -> onCellClicked('A', row));
        }

        for (int i = 0; i < 4; i++) {
            final int row = i;
            cellsB[i].setOnClickListener(v -> onCellClicked('B', row));
        }

        for (int i = 0; i < 4; i++) {
            final int xmlIndex = i;
            final int dataIndex = 3 - i;
            cellsC[i].setOnClickListener(v -> onCellClickedCD('C', xmlIndex, dataIndex));
        }

        for (int i = 0; i < 4; i++) {
            final int xmlIndex = i;
            final int dataIndex = 3 - i;
            cellsD[i].setOnClickListener(v -> onCellClickedCD('D', xmlIndex, dataIndex));
        }
    }

    private void onCellClicked(char col, int row) {
        if (!canReveal) return;

        boolean[] revealed = (col == 'A') ? revealedA : revealedB;
        Button[]  cells    = (col == 'A') ? cellsA    : cellsB;
        String[]  items    = (col == 'A') ? itemsA    : itemsB;
        boolean   solved   = (col == 'A') ? solvedA   : solvedB;
        int       color    = (col == 'A') ? COLOR_A   : COLOR_B;

        if (solved || revealed[row]) return;

        revealed[row] = true;
        canReveal = false;

        cells[row].setText(items[row]);
        cells[row].setBackgroundTintList(ColorStateList.valueOf(lighten(color)));

        cells[row].postDelayed(() -> canReveal = true, 400);
    }

    private void onCellClickedCD(char col, int xmlIndex, int dataIndex) {
        if (!canReveal) return;

        boolean[] revealed = (col == 'C') ? revealedC : revealedD;
        Button[]  cells    = (col == 'C') ? cellsC    : cellsD;
        String[]  items    = (col == 'C') ? itemsC    : itemsD;
        boolean   solved   = (col == 'C') ? solvedC   : solvedD;
        int       color    = (col == 'C') ? COLOR_C   : COLOR_D;

        if (solved || revealed[dataIndex]) return;

        revealed[dataIndex] = true;
        canReveal = false;

        cells[xmlIndex].setText(items[dataIndex]);
        cells[xmlIndex].setBackgroundTintList(ColorStateList.valueOf(lighten(color)));

        cells[xmlIndex].postDelayed(() -> canReveal = true, 400);
    }

    private void setupBottomButtons() {
        btnClear.setOnClickListener(v -> etGuess.setText(""));

        btnConfirm.setOnClickListener(v -> {
            String guess = etGuess.getText().toString().trim().toUpperCase();
            if (guess.isEmpty()) return;
            handleGuess(guess);
            etGuess.setText("");
        });

        etGuess.setOnEditorActionListener((v, actionId, event) -> {
            btnConfirm.performClick();
            return true;
        });

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

    private void handleGuess(String inputGuess) {
        String guess = inputGuess
                .replace('Ž', 'Z')
                .replace('Đ', 'D')
                .replace('Č', 'C')
                .replace('Š', 'S')
                .replace('Ć', 'C');

        boolean hit = false;

        if (!solvedA && guess.equalsIgnoreCase(solutionA)) {
            solvedA = true;
            revealColumnSolution('A');
            addPoints(calcColumnPoints(revealedA));
            hit = true;
        } else if (!solvedB && guess.equalsIgnoreCase(solutionB)) {
            solvedB = true;
            revealColumnSolution('B');
            addPoints(calcColumnPoints(revealedB));
            hit = true;
        } else if (!solvedC && guess.equalsIgnoreCase(solutionC)) {
            solvedC = true;
            revealColumnSolution('C');
            addPoints(calcColumnPoints(revealedC));
            hit = true;
        } else if (!solvedD && guess.equalsIgnoreCase(solutionD)) {
            solvedD = true;
            revealColumnSolution('D');
            addPoints(calcColumnPoints(revealedD));
            hit = true;
        } else if (!solvedFinal && guess.equalsIgnoreCase(solutionFinal)) {
            solvedFinal = true;
            revealFinalSolution();
            addPoints(calcFinalPoints());
            hit = true;
        }

        if (!hit) {
            Toast.makeText(this, "Netačan odgovor!", Toast.LENGTH_SHORT).show();
            etGuess.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E53935")));
            etGuess.postDelayed(() ->
                    etGuess.setBackgroundTintList(null), 600);
        } else {
            updateScoreViews();
            if (solvedFinal) {
                finishGame();
            }
        }
    }

    private void revealColumnSolution(char col) {
        switch (col) {
            case 'A':
                for (int i = 0; i < 4; i++) {
                    cellsA[i].setText(itemsA[i]);
                    cellsA[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_SOLVED));
                }
                tvAnswerA.setText(solutionA);
                tvAnswerA.setBackgroundColor(COLOR_SOLVED);
                tvAnswerA.setAlpha(1f);
                break;
            case 'B':
                for (int i = 0; i < 4; i++) {
                    cellsB[i].setText(itemsB[i]);
                    cellsB[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_SOLVED));
                }
                tvAnswerB.setText(solutionB);
                tvAnswerB.setBackgroundColor(COLOR_SOLVED);
                tvAnswerB.setAlpha(1f);
                break;
            case 'C':
                for (int i = 0; i < 4; i++) {
                    cellsC[i].setText(itemsC[3 - i]);
                    cellsC[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_SOLVED));
                }
                tvAnswerC.setText(solutionC);
                tvAnswerC.setBackgroundColor(COLOR_SOLVED);
                tvAnswerC.setAlpha(1f);
                break;
            case 'D':
                for (int i = 0; i < 4; i++) {
                    cellsD[i].setText(itemsD[3 - i]);
                    cellsD[i].setBackgroundTintList(ColorStateList.valueOf(COLOR_SOLVED));
                }
                tvAnswerD.setText(solutionD);
                tvAnswerD.setBackgroundColor(COLOR_SOLVED);
                tvAnswerD.setAlpha(1f);
                break;
        }
    }

    private void revealFinalSolution() {
        tvAnswerFinal.setText(solutionFinal);
        tvAnswerFinal.setBackgroundColor(COLOR_SOLVED);
        tvAnswerFinal.setAlpha(1f);
    }

    private int calcColumnPoints(boolean[] revealed) {
        int hidden = 0;
        for (boolean r : revealed) if (!r) hidden++;
        return 2 + hidden;
    }

    private int calcFinalPoints() {
        int points = 7;
        if (!solvedA) points += 6;
        if (!solvedB) points += 6;
        if (!solvedC) points += 6;
        if (!solvedD) points += 6;
        return points;
    }

    private void addPoints(int pts) {
        // TODO: u pravoj igri razlikovati igraca ciji je potez
        player1Score += pts;
    }

    private void updateScoreViews() {
        tvPlayer1Score.setText(player1Score + " bodova");
        tvPlayer2Score.setText(player2Score + " bodova");
    }

    private void startTimer() {
        timeLeftSeconds = (int) (ROUND_DURATION_MS / 1000);
        progressTimer.setMax(timeLeftSeconds);
        progressTimer.setProgress(timeLeftSeconds);

        countDownTimer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftSeconds = (int) (millisUntilFinished / 1000);
                tvTimer.setText(String.valueOf(timeLeftSeconds));
                progressTimer.setProgress(timeLeftSeconds);

                if (timeLeftSeconds <= 10) {
                    progressTimer.setIndicatorColor(Color.parseColor("#E53935"));
                    tvTimer.setTextColor(Color.parseColor("#E53935"));
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                progressTimer.setProgress(0);
                onTimeUp();
            }
        }.start();
    }

    private void onTimeUp() {
        canReveal = false;
        etGuess.setEnabled(false);
        btnConfirm.setEnabled(false);
        btnClear.setEnabled(false);

        if (!solvedA) revealColumnSolution('A');
        if (!solvedB) revealColumnSolution('B');
        if (!solvedC) revealColumnSolution('C');
        if (!solvedD) revealColumnSolution('D');
        if (!solvedFinal) revealFinalSolution();

        Toast.makeText(this, "Vreme je isteklo!", Toast.LENGTH_SHORT).show();

        finishGame();
    }

    private void finishGame() {
        if (gameFinished) {
            return;
        }

        gameFinished = true;
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        canReveal = false;
        etGuess.setEnabled(false);
        btnConfirm.setEnabled(false);
        btnClear.setEnabled(false);
        btnBack.setText(partyId != null ? "Nazad u partiju" : "Zatvori");

        if (partyId == null) {
            Toast.makeText(this, "Kraj igre. Rezultat: " + player1Score, Toast.LENGTH_LONG).show();
            return;
        }

        partyRepository.submitPlayerGameScoreAndAdvance(partyId, gameKey, currentUserId, player1Score,
                new PartyRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> Toast.makeText(AsocijacijeActivity.this,
                                "Rezultat poslat. Cekanje protivnika ako jos nije zavrsio.",
                                Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onError(String message) {
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

    private int lighten(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] * 1.25f);
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
