package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnOpenLogin;
    private Button btnOpenRegister;
    private Button btnOpenProfile;
    private Button btnOpenKorakPoKorak;
    private Button btnOpenMojBroj;
    private Button btnOpenKoZnaZna;
    private Button btnOpenSpojnice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOpenLogin = findViewById(R.id.btnOpenLogin);
        btnOpenRegister = findViewById(R.id.btnOpenRegister);
        btnOpenProfile = findViewById(R.id.btnOpenProfile);
        btnOpenKorakPoKorak = findViewById(R.id.btnOpenKorakPoKorak);
        btnOpenMojBroj = findViewById(R.id.btnOpenMojBroj);
        btnOpenKoZnaZna = findViewById(R.id.btnOpenKoZnaZna);
        btnOpenSpojnice = findViewById(R.id.btnOpenSpojnice);

        btnOpenLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        btnOpenRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        btnOpenProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(intent);
            }
        });

        btnOpenKorakPoKorak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, KorakPoKorakActivity.class);
                startActivity(intent);
            }
        });

        btnOpenMojBroj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, MojBrojActivity.class);
                startActivity(intent);
            }
        });

        btnOpenKoZnaZna.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, KoZnaZnaActivity.class);
                startActivity(intent);
            }
        });

        btnOpenSpojnice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SpojniceActivity.class);
                startActivity(intent);
            }
        });
    }
}