package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SpojniceActivity extends AppCompatActivity {

    private TextView tvRound;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvSelected;

    private Button btnLeft1, btnLeft2, btnLeft3, btnLeft4, btnLeft5;
    private Button btnRight1, btnRight2, btnRight3, btnRight4, btnRight5;
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
    private int[] correctRightForLeft = {2, 0, 4, 3, 1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        tvRound = findViewById(R.id.tvRound);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
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

        setupLeftButtons();
        setupRightButtons();

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
            tvScore.setText("Bodovi: " + score);

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
            tvRound.setText("Runda 2/2");
            tvTimer.setText("Vreme: 30s");

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
}