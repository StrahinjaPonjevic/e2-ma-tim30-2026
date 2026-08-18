package com.example.slagalica.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.auth.FirebaseManager;
import com.example.slagalica.leagues.LeagueNotificationRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChallengeActivity extends AppCompatActivity {

    private TextView tvTitle;
    private EditText etStarsStake;
    private EditText etTokensStake;
    private Button btnCreate;
    private Button btnBack;
    private RecyclerView rvChallenges;

    private FirebaseManager firebaseManager;
    private ChallengeRepository challengeRepository;
    private FirebaseUser currentUser;
    private ListenerRegistration listenerRegistration;
    private ChallengeAdapter adapter;
    private String username;
    private String region;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge);

        firebaseManager = new FirebaseManager();
        challengeRepository = new ChallengeRepository();
        currentUser = firebaseManager.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za izazove.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupList();
        btnBack.setOnClickListener(v -> finish());
        btnCreate.setOnClickListener(v -> createChallenge());
        loadUser();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvChallengeTitle);
        etStarsStake = findViewById(R.id.etStarsStake);
        etTokensStake = findViewById(R.id.etTokensStake);
        btnCreate = findViewById(R.id.btnCreateChallenge);
        btnBack = findViewById(R.id.btnChallengeBack);
        rvChallenges = findViewById(R.id.rvChallenges);
    }

    private void setupList() {
        adapter = new ChallengeAdapter();
        rvChallenges.setLayoutManager(new LinearLayoutManager(this));
        rvChallenges.setAdapter(adapter);
    }

    private void loadUser() {
        firebaseManager.loadUserData(currentUser.getUid(), new FirebaseManager.UserDataCallback() {
            @Override
            public void onSuccess(String loadedUsername, String loadedRegion) {
                username = loadedUsername;
                region = loadedRegion;
                runOnUiThread(() -> {
                    tvTitle.setText("Izazovi - " + region);
                    listenChallenges();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ChallengeActivity.this, message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void listenChallenges() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }

        listenerRegistration = challengeRepository.listenRegionChallenges(region, new ChallengeRepository.ChallengeListListener() {
            @Override
            public void onChallenges(List<ChallengeData> challenges) {
                runOnUiThread(() -> adapter.submit(challenges));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ChallengeActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void createChallenge() {
        int starsStake = parseInt(etStarsStake.getText().toString());
        int tokensStake = parseInt(etTokensStake.getText().toString());
        btnCreate.setEnabled(false);
        challengeRepository.createChallenge(region, currentUser.getUid(), username, starsStake, tokensStake,
                new ChallengeRepository.CreateCallback() {
                    @Override
                    public void onSuccess(String challengeId) {
                        runOnUiThread(() -> {
                            btnCreate.setEnabled(true);
                            etStarsStake.setText("");
                            etTokensStake.setText("");
                            openDetail(challengeId);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnCreate.setEnabled(true);
                            Toast.makeText(ChallengeActivity.this, message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void openDetail(String challengeId) {
        Intent intent = new Intent(this, ChallengeDetailActivity.class);
        intent.putExtra("challengeId", challengeId);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LeagueNotificationRepository.setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        LeagueNotificationRepository.clearCurrentActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    private final class ChallengeAdapter extends RecyclerView.Adapter<ChallengeViewHolder> {
        private final List<ChallengeData> challenges = new ArrayList<>();

        void submit(List<ChallengeData> newChallenges) {
            challenges.clear();
            challenges.addAll(newChallenges);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ChallengeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge, parent, false);
            return new ChallengeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChallengeViewHolder holder, int position) {
            ChallengeData challenge = challenges.get(position);
            holder.tvTitle.setText(challenge.creatorUsername + " - " + challenge.status);
            holder.tvSubtitle.setText(String.format(Locale.getDefault(),
                    "Ulog: %d zvezdi, %d tokena | Ucesnici: %d/4",
                    challenge.starsStake,
                    challenge.tokensStake,
                    challenge.participants.size()));
            holder.itemView.setOnClickListener(v -> openDetail(challenge.challengeId));
        }

        @Override
        public int getItemCount() {
            return challenges.size();
        }
    }

    private static final class ChallengeViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;

        ChallengeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvChallengeItemTitle);
            tvSubtitle = itemView.findViewById(R.id.tvChallengeItemSubtitle);
        }
    }
}
