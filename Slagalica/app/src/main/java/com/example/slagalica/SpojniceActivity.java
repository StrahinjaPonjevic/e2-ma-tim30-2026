package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.CircularProgressIndicator;

public class SpojniceActivity extends AppCompatActivity {

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

    private int selectedLeftIndex = -1;
    private Button selectedLeftButton = null;

    private int score = 0;
    private int round = 1;

    // Tačni parovi:
    // 0 Tesla -> Naizmenična struja
    // 1 Đoković -> Tenis
    // 2 Andrić -> Na Drini ćuprija
    // 3 Mocart -> Muzika
    // 4 Einstein -> Relativnost
    private final int[] correctRightForLeft = {2, 0, 4, 3, 1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        bindViews();
        setupHeader();
        setupLeftButtons();
        setupRightButtons();
        bindActionButtons();
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

    private void setupHeader() {
        tvPlayer1Name.setText("Igrač 1");
        tvPlayer2Name.setText("Igrač 2");
        tvRoundLabel.setText("RUNDA 1/2 — SPOJNICE");
        tvTimer.setText("30");
        progressTimer.setMax(30);
        progressTimer.setProgress(30);
        updateScoreViews();
    }

    private void bindActionButtons() {
        btnNextRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToNextRound();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void setupLeftButtons() {
        btnLeft1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectLeft(0, btnLeft1);
            }
        });

        btnLeft2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectLeft(1, btnLeft2);
            }
        });

        btnLeft3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectLeft(2, btnLeft3);
            }
        });

        btnLeft4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectLeft(3, btnLeft4);
            }
        });

        btnLeft5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectLeft(4, btnLeft5);
            }
        });
    }

    private void setupRightButtons() {
        btnRight1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPair(0, btnRight1);
            }
        });

        btnRight2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPair(1, btnRight2);
            }
        });

        btnRight3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPair(2, btnRight3);
            }
        });

        btnRight4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPair(3, btnRight4);
            }
        });

        btnRight5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPair(4, btnRight5);
            }
        });
    }

    private void selectLeft(int leftIndex, Button leftButton) {
        selectedLeftIndex = leftIndex;
        selectedLeftButton = leftButton;

        tvSelected.setText("Izabrano: " + leftButton.getText().toString());
        Toast.makeText(this, "Sada izaberi pojam iz desne kolone", Toast.LENGTH_SHORT).show();
    }

    private void checkPair(int rightIndex, Button rightButton) {
        if (selectedLeftIndex == -1) {
            Toast.makeText(this, "Prvo izaberi pojam iz leve kolone", Toast.LENGTH_SHORT).show();
            return;
        }

        if (correctRightForLeft[selectedLeftIndex] == rightIndex) {
            score += 2;
            updateScoreViews();

            Toast.makeText(this, "Tačan spoj! +2 boda", Toast.LENGTH_SHORT).show();

            selectedLeftButton.setEnabled(false);
            rightButton.setEnabled(false);
        } else {
            Toast.makeText(this, "Netačan spoj", Toast.LENGTH_SHORT).show();
        }

        selectedLeftIndex = -1;
        selectedLeftButton = null;
        tvSelected.setText("Izabrano: ništa");
    }

    private void goToNextRound() {
        if (round == 1) {
            round = 2;
            tvRoundLabel.setText("RUNDA 2/2 — SPOJNICE");
            tvTimer.setText("30");
            progressTimer.setProgress(30);

            resetButtons();

            Toast.makeText(this, "Počinje druga runda", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Kraj igre! Osvojio si " + score + " bodova.", Toast.LENGTH_LONG).show();
        }
    }

    private void resetButtons() {
        btnLeft1.setEnabled(true);
        btnLeft2.setEnabled(true);
        btnLeft3.setEnabled(true);
        btnLeft4.setEnabled(true);
        btnLeft5.setEnabled(true);

        btnRight1.setEnabled(true);
        btnRight2.setEnabled(true);
        btnRight3.setEnabled(true);
        btnRight4.setEnabled(true);
        btnRight5.setEnabled(true);

        selectedLeftIndex = -1;
        selectedLeftButton = null;
        tvSelected.setText("Izabrano: ništa");
    }

    private void updateScoreViews() {
        tvPlayer1Score.setText(score + " bodova");
        tvPlayer2Score.setText("0 bodova");
    }
}
