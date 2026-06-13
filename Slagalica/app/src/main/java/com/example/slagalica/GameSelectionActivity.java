package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class GameSelectionActivity extends AppCompatActivity {

    private Button btnKorakPoKorak;
    private Button btnMojBroj;
    private Button btnKoZnaZna;
    private Button btnSpojnice;
    private Button btnAsocijacije;
    private Button btnSkocko;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_selection);

        btnKorakPoKorak = findViewById(R.id.btnOpenKorakPoKorak);
        btnMojBroj = findViewById(R.id.btnOpenMojBroj);
        btnKoZnaZna = findViewById(R.id.btnOpenKoZnaZna);
        btnSpojnice = findViewById(R.id.btnOpenSpojnice);
        btnAsocijacije = findViewById(R.id.btnOpenAsocijacije);
        btnSkocko = findViewById(R.id.btnOpenSkocko);
        btnBack = findViewById(R.id.btnBack);

        btnKorakPoKorak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, KorakPoKorakActivity.class));
            }
        });

        btnMojBroj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, MojBrojActivity.class));
            }
        });

        btnKoZnaZna.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, KoZnaZnaActivity.class));
            }
        });

        btnSpojnice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, SpojniceActivity.class));
            }
        });

        btnAsocijacije.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, AsocijacijeActivity.class));
            }
        });

        btnSkocko.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(GameSelectionActivity.this, SkockoActivity.class));
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
