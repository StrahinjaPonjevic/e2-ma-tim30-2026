package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private static final String[] AVATAR_NAMES = {"Pesak", "More", "Sumrak", "Bor", "Rubin", "Nebo"};
    private static final int[] AVATAR_COLORS = {
            Color.rgb(201, 134, 82),
            Color.rgb(61, 126, 170),
            Color.rgb(109, 76, 156),
            Color.rgb(84, 126, 91),
            Color.rgb(198, 73, 91),
            Color.rgb(73, 124, 199)
    };

    private TextView tvAvatarInitials;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegion;
    private TextView tvTokens;
    private TextView tvStars;
    private TextView tvLeague;
    private TextView tvTotalMatches;
    private TextView tvWinLose;
    private TextView tvKoZnaZnaAverage;
    private TextView tvSpojniceAverage;
    private TextView tvMojBrojAverage;
    private TextView tvKorakPoKorakAverage;
    private TextView tvKoZnaZnaStats;
    private TextView tvSpojniceStats;
    private TextView tvMojBrojStats;
    private TextView tvKorakPoKorakStats;
    private Button btnEditAvatar;
    private Button btnLogout;
    private Button btnBackToMain;

    private FirebaseManager firebaseManager;
    private UserProfileRepository profileRepository;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        firebaseManager = new FirebaseManager();
        profileRepository = new UserProfileRepository();
        currentUser = firebaseManager.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Morate biti prijavljeni", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        bindListeners();
        loadUserProfile();
    }

    private void bindViews() {
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvRegion = findViewById(R.id.tvRegion);
        tvTokens = findViewById(R.id.tvTokens);
        tvStars = findViewById(R.id.tvStars);
        tvLeague = findViewById(R.id.tvLeague);
        tvTotalMatches = findViewById(R.id.tvTotalMatches);
        tvWinLose = findViewById(R.id.tvWinLose);
        tvKoZnaZnaAverage = findViewById(R.id.tvKoZnaZnaAverage);
        tvSpojniceAverage = findViewById(R.id.tvSpojniceAverage);
        tvMojBrojAverage = findViewById(R.id.tvMojBrojAverage);
        tvKorakPoKorakAverage = findViewById(R.id.tvKorakPoKorakAverage);
        tvKoZnaZnaStats = findViewById(R.id.tvKoZnaZnaStats);
        tvSpojniceStats = findViewById(R.id.tvSpojniceStats);
        tvMojBrojStats = findViewById(R.id.tvMojBrojStats);
        tvKorakPoKorakStats = findViewById(R.id.tvKorakPoKorakStats);
        btnEditAvatar = findViewById(R.id.btnEditAvatar);
        btnLogout = findViewById(R.id.btnLogout);
        btnBackToMain = findViewById(R.id.btnBackToMain);
    }

    private void bindListeners() {
        btnEditAvatar.setOnClickListener(v -> openAvatarPicker());

        btnLogout.setOnClickListener(v -> {
            firebaseManager.logout();
            Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnBackToMain.setOnClickListener(v -> finish());
    }

    private void loadUserProfile() {
        profileRepository.loadProfile(currentUser.getUid(), new UserProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                runOnUiThread(() -> renderProfile(profile));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderProfile(UserProfile profile) {
        tvUsername.setText(profile.username);
        tvEmail.setText(profile.email);
        tvRegion.setText("Region: " + profile.region);
        tvTokens.setText(String.valueOf(profile.tokens));
        tvStars.setText(String.valueOf(profile.stars));
        tvLeague.setText(resolveLeague(profile.stars));
        tvTotalMatches.setText("Ukupno partija: " + profile.matchesPlayed);
        tvWinLose.setText(String.format(Locale.getDefault(), "Pobede: %d | Porazi: %d", profile.wins, profile.losses));

        tvAvatarInitials.setText(extractInitials(profile.username));
        applyAvatarTheme(profile.avatarTheme);

        tvKoZnaZnaAverage.setText(buildAverageText("Ko zna zna", profile.koZnaZna.gamesPlayed, profile.koZnaZna.totalScore, 50));
        tvSpojniceAverage.setText(buildAverageText("Spojnice", profile.spojnice.gamesPlayed, profile.spojnice.totalScore, 20));
        tvMojBrojAverage.setText(buildAverageText("Moj broj", profile.mojBroj.gamesPlayed, profile.mojBroj.totalScore, 20));
        tvKorakPoKorakAverage.setText(buildAverageText("Korak po korak", profile.korakPoKorak.gamesPlayed, profile.korakPoKorak.totalScore, 40));

        tvKoZnaZnaStats.setText(String.format(Locale.getDefault(),
                "Ko zna zna: %d pogodjenih / %d promasenih pitanja",
                profile.koZnaZna.correctAnswers, profile.koZnaZna.wrongAnswers));

        tvSpojniceStats.setText(String.format(Locale.getDefault(),
                "Spojnice: %d/%d uspesnih spajanja (%s)",
                profile.spojnice.successfulLinks,
                profile.spojnice.attemptedLinks,
                formatPercent(profile.spojnice.successfulLinks, profile.spojnice.attemptedLinks)));

        tvMojBrojStats.setText(String.format(Locale.getDefault(),
                "Moj broj: tacan broj pogodjen u %s",
                formatPercent(profile.mojBroj.exactHits, profile.mojBroj.roundsPlayed)));

        int totalKpkRounds = Math.max(profile.korakPoKorak.gamesPlayed, 1);
        tvKorakPoKorakStats.setText(String.format(Locale.getDefault(),
                "Korak po korak: 1.%s  2.%s  3.%s  4.%s  5.%s  6.%s  7.%s",
                formatPercent(profile.korakPoKorak.step1Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step2Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step3Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step4Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step5Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step6Hits, totalKpkRounds),
                formatPercent(profile.korakPoKorak.step7Hits, totalKpkRounds)));
    }

    private void openAvatarPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Izaberi avatar")
                .setItems(AVATAR_NAMES, (dialog, which) -> profileRepository.updateAvatarTheme(
                        currentUser.getUid(),
                        which,
                        new UserProfileRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                loadUserProfile();
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show());
                            }
                        }))
                .show();
    }

    private void applyAvatarTheme(int avatarTheme) {
        int safeIndex = Math.max(0, Math.min(avatarTheme, AVATAR_COLORS.length - 1));
        if (tvAvatarInitials.getBackground() instanceof GradientDrawable) {
            GradientDrawable drawable = (GradientDrawable) tvAvatarInitials.getBackground().mutate();
            drawable.setColor(AVATAR_COLORS[safeIndex]);
            drawable.setStroke(3, Color.rgb(255, 248, 239));
        }
    }

    private String buildAverageText(String label, int gamesPlayed, int totalScore, int maxScore) {
        if (gamesPlayed == 0) {
            return label + ": nema odigranih partija";
        }

        double average = (double) totalScore / gamesPlayed;
        return String.format(Locale.getDefault(), "%s: prosecno %.1f/%d bodova", label, average, maxScore);
    }

    private String formatPercent(int value, int total) {
        if (total <= 0) {
            return "0%";
        }
        int percent = Math.round((value * 100f) / total);
        return percent + "%";
    }

    private String resolveLeague(int stars) {
        if (stars >= 200) {
            return "Zlatna liga";
        }
        if (stars >= 100) {
            return "Srebrna liga";
        }
        return "Bronzana liga";
    }

    private String extractInitials(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "IG";
        }

        String cleaned = username.trim();
        if (cleaned.length() == 1) {
            return cleaned.toUpperCase(Locale.getDefault());
        }
        return cleaned.substring(0, 2).toUpperCase(Locale.getDefault());
    }
}
