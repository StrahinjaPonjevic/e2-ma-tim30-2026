package com.example.slagalica;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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

    private static final int[] AVATAR_COLORS = {
            Color.rgb(201, 134, 82),
            Color.rgb(61, 126, 170),
            Color.rgb(109, 76, 156),
            Color.rgb(84, 126, 91),
            Color.rgb(198, 73, 91),
            Color.rgb(73, 124, 199)
    };

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
    private UserProfileRepository profileRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseManager = new FirebaseManager();
        profileRepository = new UserProfileRepository();

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
            firebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        FirebaseUser user = firebaseManager.getCurrentUser();
                        if (user != null) {
                            showLoggedInView(user);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                }
            });
        }
    }

    private void showGuestView() {
        guestSection.setVisibility(View.VISIBLE);
        loggedInSection.setVisibility(View.GONE);

        btnOpenLogin.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        btnOpenRegister.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));
        btnOpenPlayGuest.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SessionActivity.class)));
    }

    private void showLoggedInView(FirebaseUser user) {
        guestSection.setVisibility(View.GONE);
        loggedInSection.setVisibility(View.VISIBLE);
        tvLoggedInUser.setText("Dobrodosli!");
        tvTokensStars.setText("Ucitavanje profila...");

        profileRepository.loadProfile(user.getUid(), new UserProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runOnUiThread(() -> {
                    tvLoggedInUser.setText("Dobrodosli, " + profile.username + "!");
                    tvTokensStars.setText("Tokeni: " + profile.tokens
                            + " | Zvezde: " + profile.stars
                            + " | Liga: " + resolveLeague(profile.stars));
                    applyProfileAvatar(profile);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvTokensStars.setText("Profil nije dostupan");
                    btnOpenProfile.setText("P");
                });
            }
        });

        btnOpenProfile.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        btnOpenPlayLoggedIn.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SessionActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null) {
            if (loggedInSection.getVisibility() != View.VISIBLE) {
                showLoggedInView(currentUser);
            } else {
                showLoggedInView(currentUser);
            }
        } else {
            if (guestSection.getVisibility() != View.VISIBLE) {
                showGuestView();
            }
        }
    }

    private String resolveLeague(int stars) {
        if (stars >= 200) {
            return "Zlatna";
        }
        if (stars >= 100) {
            return "Srebrna";
        }
        return "Bronzana";
    }

    private void applyProfileAvatar(UserProfile profile) {
        String initials = extractInitials(profile.username);
        int safeIndex = Math.max(0, Math.min(profile.avatarTheme, AVATAR_COLORS.length - 1));

        btnOpenProfile.setText(initials);
        btnOpenProfile.setTextColor(Color.WHITE);
        btnOpenProfile.setBackgroundTintList(ColorStateList.valueOf(AVATAR_COLORS[safeIndex]));
    }

    private String extractInitials(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "P";
        }

        String cleaned = username.trim();
        if (cleaned.length() == 1) {
            return cleaned.toUpperCase();
        }
        return cleaned.substring(0, 2).toUpperCase();
    }
}
