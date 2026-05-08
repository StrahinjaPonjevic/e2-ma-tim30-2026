package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MojBrojActivity extends AppCompatActivity {

    private EditText etExpression;
    private Button btnStopTargetNumber;
    private Button btnStopNumbers;
    private Button btnClearExpression;
    private Button btnConfirmExpression;
    private Button btnGiveUpMojBroj;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        etExpression = findViewById(R.id.etExpression);
        btnStopTargetNumber = findViewById(R.id.btnStopTargetNumber);
        btnStopNumbers = findViewById(R.id.btnStopNumbers);
        btnClearExpression = findViewById(R.id.btnClearExpression);
        btnConfirmExpression = findViewById(R.id.btnConfirmExpression);
        btnGiveUpMojBroj = findViewById(R.id.btnGiveUpMojBroj);

        btnStopTargetNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(
                        MojBrojActivity.this,
                        "Zaustavljen je traženi broj",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        btnStopNumbers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(
                        MojBrojActivity.this,
                        "Zaustavljeni su ponuđeni brojevi",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        btnClearExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                etExpression.setText("");
            }
        });

        btnConfirmExpression.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String expression = etExpression.getText().toString().trim();

                if (expression.isEmpty()) {
                    etExpression.setError("Unesite izraz");
                    return;
                }

                Toast.makeText(
                        MojBrojActivity.this,
                        "Izraz je poslat: " + expression,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        btnGiveUpMojBroj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}