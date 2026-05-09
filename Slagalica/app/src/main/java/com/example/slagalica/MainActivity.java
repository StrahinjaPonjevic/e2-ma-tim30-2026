package com.example.slagalica;

import com.example.slagalica.notifications.NotificationHelper;
import com.example.slagalica.notifications.NotificationChannelManager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private Button btnOpenLogin;
    private Button btnOpenRegister;
    private Button btnOpenProfile;
    private Button btnOpenKorakPoKorak;
    private Button btnOpenMojBroj;
    private Button btnOpenKoZnaZna;
    private Button btnOpenSpojnice;
    private Button btnOpenAsocijacije;
    private Button btnOpenSkocko;
    private Button btnTestNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        NotificationChannelManager.createChannels(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        btnOpenLogin = findViewById(R.id.btnOpenLogin);
        btnOpenRegister = findViewById(R.id.btnOpenRegister);
        btnOpenProfile = findViewById(R.id.btnOpenProfile);
        btnOpenKorakPoKorak = findViewById(R.id.btnOpenKorakPoKorak);
        btnOpenMojBroj = findViewById(R.id.btnOpenMojBroj);
        btnOpenKoZnaZna = findViewById(R.id.btnOpenKoZnaZna);
        btnOpenSpojnice = findViewById(R.id.btnOpenSpojnice);
        btnOpenAsocijacije = findViewById(R.id.btnOpenAsocijacije);
        btnOpenSkocko = findViewById(R.id.btnOpenSkocko);
        btnTestNotif = findViewById(R.id.btnTestNotif);

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

        btnOpenAsocijacije.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AsocijacijeActivity.class);
                startActivity(intent);
            }
        });

        btnOpenSkocko.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SkockoActivity.class);
                startActivity(intent);
            }
        });

        btnTestNotif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NotificationHelper.send(
                        MainActivity.this,
                        NotificationChannelManager.CHANNEL_REWARDS,
                        "Test nagrada",
                        "Osvojio si 5 tokena!",
                        1001
                );
            }
        });
    }
}