package com.example.slagalica;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.notifications.NotificationHelper;
import com.example.slagalica.notifications.NotificationChannelManager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private LinearLayout guestSection;
    private LinearLayout loggedInSection;
    private Button btnOpenLogin;
    private Button btnOpenRegister;
    private Button btnOpenPlayGuest;
    private Button btnOpenProfile;
    private Button btnOpenPlayLoggedIn;
    private TextView tvLoggedInUser;
    private TextView tvTokensStars;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseManager = new FirebaseManager();

        NotificationChannelManager.createChannels(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        guestSection = findViewById(R.id.guestSection);
        loggedInSection = findViewById(R.id.loggedInSection);
        btnOpenLogin = findViewById(R.id.btnOpenLogin);
        btnOpenRegister = findViewById(R.id.btnOpenRegister);
        btnOpenPlayGuest = findViewById(R.id.btnOpenPlayGuest);
        btnOpenProfile = findViewById(R.id.btnOpenProfile);
        btnOpenPlayLoggedIn = findViewById(R.id.btnOpenPlayLoggedIn);
        tvLoggedInUser = findViewById(R.id.tvLoggedInUser);
        tvTokensStars = findViewById(R.id.tvTokensStars);

        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null) {
            showLoggedInView(currentUser);
        } else {
            showGuestView();
        }
    }

    private void showGuestView() {
        guestSection.setVisibility(View.VISIBLE);
        loggedInSection.setVisibility(View.GONE);

        btnOpenLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
        });

        btnOpenRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, RegisterActivity.class));
            }
        });

        btnOpenPlayGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, GameSelectionActivity.class));
            }
        });
    }

    private void showLoggedInView(FirebaseUser user) {
        guestSection.setVisibility(View.GONE);
        loggedInSection.setVisibility(View.VISIBLE);

        tvLoggedInUser.setText("Dobrodošli!");

        firebaseManager.loadUserData(user.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(final String username, final String region) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLoggedInUser.setText("Dobrodošli, " + username + "!");
                        tvTokensStars.setText("Tokeni: 0 | Zvezde: 0 | Liga: Bronzana");
                    }
                });
            }

            @Override
            public void onError(final String message) {
            }
        });

        btnOpenProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            }
        });

        btnOpenPlayLoggedIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, GameSelectionActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null) {
            if (loggedInSection.getVisibility() != View.VISIBLE) {
                showLoggedInView(currentUser);
            }
        } else {
            if (guestSection.getVisibility() != View.VISIBLE) {
                showGuestView();
            }
        }
    }
}
