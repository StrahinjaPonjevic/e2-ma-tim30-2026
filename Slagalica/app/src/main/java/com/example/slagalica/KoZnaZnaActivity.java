package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class KoZnaZnaActivity extends AppCompatActivity {

    private TextView tvRoundInfo;
    private TextView tvTimer;
    private TextView tvQuestion;
    private TextView tvPlayerScore;
    private TextView tvOpponentScore;

    private Button btnAnswerA;
    private Button btnAnswerB;
    private Button btnAnswerC;
    private Button btnAnswerD;
    private Button btnNextQuestion;
    private Button btnBack;

    private int currentQuestionIndex = 0;
    private int playerScore = 0;
    private int opponentScore = 0;

    private String[] questions = {
            "Koji je glavni grad Srbije?",
            "Koliko igrača ima jedan fudbalski tim na terenu?",
            "Koja planeta je poznata kao crvena planeta?",
            "Ko je napisao delo 'Na Drini ćuprija'?",
            "Koliko strana ima trougao?"
    };

    private String[][] answers = {
            {"Novi Sad", "Beograd", "Niš", "Kragujevac"},
            {"9", "10", "11", "12"},
            {"Venera", "Mars", "Jupiter", "Saturn"},
            {"Ivo Andrić", "Branko Ćopić", "Mesa Selimović", "Danilo Kiš"},
            {"2", "3", "4", "5"}
    };

    private int[] correctAnswers = {1, 2, 1, 0, 1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        tvRoundInfo = findViewById(R.id.tvRoundInfo);
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvPlayerScore = findViewById(R.id.tvPlayerScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);

        btnAnswerA = findViewById(R.id.btnAnswerA);
        btnAnswerB = findViewById(R.id.btnAnswerB);
        btnAnswerC = findViewById(R.id.btnAnswerC);
        btnAnswerD = findViewById(R.id.btnAnswerD);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        btnBack = findViewById(R.id.btnBack);

        showQuestion();

        btnAnswerA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAnswer(0);
            }
        });

        btnAnswerB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAnswer(1);
            }
        });

        btnAnswerC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAnswer(2);
            }
        });

        btnAnswerD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkAnswer(3);
            }
        });

        btnNextQuestion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToNextQuestion();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    private void showQuestion() {
        tvRoundInfo.setText("Pitanje " + (currentQuestionIndex + 1) + "/5");
        tvTimer.setText("Vreme: 5s");

        tvQuestion.setText(questions[currentQuestionIndex]);

        btnAnswerA.setText("A) " + answers[currentQuestionIndex][0]);
        btnAnswerB.setText("B) " + answers[currentQuestionIndex][1]);
        btnAnswerC.setText("C) " + answers[currentQuestionIndex][2]);
        btnAnswerD.setText("D) " + answers[currentQuestionIndex][3]);

        enableAnswerButtons(true);
    }

    private void checkAnswer(int selectedAnswerIndex) {
        if (selectedAnswerIndex == correctAnswers[currentQuestionIndex]) {
            playerScore += 10;
            Toast.makeText(this, "Tačan odgovor! +10 bodova", Toast.LENGTH_SHORT).show();
        } else {
            playerScore -= 5;
            Toast.makeText(this, "Netačan odgovor! -5 bodova", Toast.LENGTH_SHORT).show();
        }

        tvPlayerScore.setText("Ti: " + playerScore + " bodova");
        tvOpponentScore.setText("Protivnik: " + opponentScore + " bodova");

        enableAnswerButtons(false);
    }

    private void goToNextQuestion() {
        if (currentQuestionIndex < questions.length - 1) {
            currentQuestionIndex++;
            showQuestion();
        } else {
            Toast.makeText(this, "Kraj igre! Osvojio si " + playerScore + " bodova.", Toast.LENGTH_LONG).show();
        }
    }

    private void enableAnswerButtons(boolean enabled) {
        btnAnswerA.setEnabled(enabled);
        btnAnswerB.setEnabled(enabled);
        btnAnswerC.setEnabled(enabled);
        btnAnswerD.setEnabled(enabled);
    }
}