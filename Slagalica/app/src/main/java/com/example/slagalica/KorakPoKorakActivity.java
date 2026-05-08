package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class KorakPoKorakActivity extends AppCompatActivity {
    private EditText etAnswer;
    private Button btnConfirmAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        etAnswer = findViewById(R.id.etAnswer);
        btnConfirmAnswer = findViewById(R.id.btnConfirmAnswer);

        btnConfirmAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String answer = etAnswer.getText().toString().trim();

                if (answer.isEmpty()) {
                    etAnswer.setError("Unesite odgovor");
                    return;
                }

                Toast.makeText(
                        KorakPoKorakActivity.this,
                        "Odgovor je poslat: " + answer,
                        Toast.LENGTH_SHORT
                        ).show();
            }
        });
    }
}
