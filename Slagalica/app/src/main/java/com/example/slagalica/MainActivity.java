package com.example.slagalica;

import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.challenge.ChallengeActivity;
import com.example.slagalica.chat.ChatActivity;
import com.example.slagalica.chat.ChatRepository;
import com.example.slagalica.leagues.LeagueNotificationRepository;
import com.example.slagalica.leagues.LeagueUiHelper;
import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationsActivity;
import com.example.slagalica.missions.MissionsActivity;
import com.example.slagalica.ranking.RankingActivity;
import com.example.slagalica.ranking.RankingRepository;
import com.example.slagalica.ranking.RewardActivity;
import com.example.slagalica.tournament.TournamentActivity;
import com.example.slagalica.party.FriendlyInviteActivity;
import com.example.slagalica.party.FriendlyInviteRepository;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;
import com.example.slagalica.regions.RegionAvatarFrameHelper;
import com.example.slagalica.regions.RegionMapActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private Button btnOpenFriendly;
    private Button btnOpenChat;
    private Button btnOpenChallenges;
    private Button btnOpenRegions;
    private TextView tvLoggedInUser;
    private TextView tvTokensStars;
    private FirebaseManager firebaseManager;
    private UserProfileRepository profileRepository;
    private Button btnOpenNotifications;
    private Button btnOpenRanking;
    private Button btnOpenTournament;
    private Button btnOpenMissions;
    private RankingRepository rankingRepository;
    private boolean rewardScreenShown = false;

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
        btnOpenFriendly = findViewById(R.id.btnOpenFriendly);
        btnOpenChat = findViewById(R.id.btnOpenChat);
        btnOpenChallenges = findViewById(R.id.btnOpenChallenges);
        btnOpenRegions = findViewById(R.id.btnOpenRegions);
        btnOpenNotifications = findViewById(R.id.btnOpenNotifications);
        btnOpenRanking = findViewById(R.id.btnOpenRanking);
        btnOpenTournament = findViewById(R.id.btnOpenTournament);
        btnOpenMissions = findViewById(R.id.btnOpenMissions);
        rankingRepository = new RankingRepository();
        tvLoggedInUser = findViewById(R.id.tvLoggedInUser);
        tvTokensStars = findViewById(R.id.tvTokensStars);

        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            showLoggedInView(currentUser);
        } else {
            showGuestView();
            if (currentUser == null) {
                firebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
            }
        }
    }

    private void showGuestView() {
        guestSection.setVisibility(View.VISIBLE);
        loggedInSection.setVisibility(View.GONE);
        ChatRepository.stopNotificationListener();
        FriendlyInviteRepository.stopNotificationListener();
        LeagueNotificationRepository.stopLeagueChangeListener();

        btnOpenLogin.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, LoginActivity.class)));
        btnOpenRegister.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));
        btnOpenPlayGuest.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SessionActivity.class)));
    }

    private void showLoggedInView(FirebaseUser user) {
        guestSection.setVisibility(View.GONE);
        loggedInSection.setVisibility(View.VISIBLE);
        tvLoggedInUser.setText("Dobrodosli!");
        tvTokensStars.setText("Ucitavanje profila...");
        firebaseManager.markCurrentUserLoggedIn();
        LeagueNotificationRepository.startLeagueChangeListener(MainActivity.this, user.getUid());
        rankingRepository.finalizePreviousCyclesIfNeeded();
        rankingRepository.checkPendingReward(user.getUid(), rewardText -> runOnUiThread(() -> {
            if (rewardScreenShown) {
                return;
            }
            rewardScreenShown = true;
            Intent rewardIntent = new Intent(MainActivity.this, RewardActivity.class);
            rewardIntent.putExtra("rewardText", rewardText);
            startActivity(rewardIntent);
        }));

        profileRepository.grantDailyTokensIfNeeded(user.getUid(), new UserProfileRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                loadProfileForMain(user);
            }

            @Override
            public void onError(String message) {
                loadProfileForMain(user);
            }
        });

        btnOpenProfile.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        btnOpenPlayLoggedIn.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SessionActivity.class)));
        btnOpenFriendly.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, FriendlyInviteActivity.class)));
        btnOpenChat.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ChatActivity.class)));
        btnOpenChallenges.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ChallengeActivity.class)));
        btnOpenRegions.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, RegionMapActivity.class)));
        btnOpenNotifications.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, NotificationsActivity.class)));
        btnOpenRanking.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, RankingActivity.class)));
        btnOpenTournament.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, TournamentActivity.class)));
        btnOpenMissions.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, MissionsActivity.class)));
    }

    private void loadProfileForMain(FirebaseUser user) {
        profileRepository.loadProfile(user.getUid(), new UserProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                ChatRepository.startNotificationListener(MainActivity.this, profile.region, user.getUid());
                FriendlyInviteRepository.startNotificationListener(MainActivity.this, user.getUid());
                runOnUiThread(() -> {
                    tvLoggedInUser.setText("Dobrodosli, " + profile.username + "!");
                    tvTokensStars.setText("Tokeni: " + profile.tokens
                            + " | Zvezde: " + profile.stars
                            + " | Liga: " + LeagueUiHelper.displayNameForStars(profile.stars));
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        LeagueNotificationRepository.setCurrentActivity(this);
        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null && !currentUser.isAnonymous()) {
            showLoggedInView(currentUser);
        } else {
            showGuestView();
        }
    }

    @Override
    protected void onPause() {
        LeagueNotificationRepository.clearCurrentActivity(this);
        super.onPause();
    }

    private void applyProfileAvatar(UserProfile profile) {
        String initials = extractInitials(profile.username);
        int safeIndex = Math.max(0, Math.min(profile.avatarTheme, AVATAR_COLORS.length - 1));

        btnOpenProfile.setText(initials);
        btnOpenProfile.setTextColor(Color.WHITE);
        RegionAvatarFrameHelper.apply(btnOpenProfile, AVATAR_COLORS[safeIndex],
                profile.avatarFrameRank, profile.avatarFrameCycleMonth);
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
