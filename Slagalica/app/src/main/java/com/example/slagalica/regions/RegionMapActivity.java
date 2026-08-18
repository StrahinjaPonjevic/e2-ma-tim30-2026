package com.example.slagalica.regions;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RegionMapActivity extends AppCompatActivity {

    private RegionMapView regionMapView;
    private ProgressBar progressRegions;
    private TextView tvRegionStatsTitle;
    private TextView tvFirstPlaces;
    private TextView tvSecondPlaces;
    private TextView tvThirdPlaces;
    private TextView tvActivePlayers;
    private TextView tvRegisteredPlayers;
    private RegionLeaderboardAdapter leaderboardAdapter;
    private RegionRepository repository;
    private RegionRepository.DashboardData dashboardData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni da biste videli regione", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_region_map);
        repository = new RegionRepository();
        bindViews();

        Button btnBack = findViewById(R.id.btnRegionBack);
        btnBack.setOnClickListener(view -> finish());
        regionMapView.setOnRegionClickListener(region -> {
            if (dashboardData != null) {
                renderRegionStats(dashboardData.summaryFor(region.id));
            }
        });

        progressRegions.setVisibility(View.VISIBLE);
        repository.ensurePreviousCycleFinalized(new RegionRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                loadDashboard(user.getUid());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(
                        RegionMapActivity.this, message, Toast.LENGTH_LONG).show());
                loadDashboard(user.getUid());
            }
        });
    }

    private void bindViews() {
        regionMapView = findViewById(R.id.regionMapView);
        progressRegions = findViewById(R.id.progressRegions);
        tvRegionStatsTitle = findViewById(R.id.tvRegionStatsTitle);
        tvFirstPlaces = findViewById(R.id.tvFirstPlaces);
        tvSecondPlaces = findViewById(R.id.tvSecondPlaces);
        tvThirdPlaces = findViewById(R.id.tvThirdPlaces);
        tvActivePlayers = findViewById(R.id.tvActivePlayers);
        tvRegisteredPlayers = findViewById(R.id.tvRegisteredPlayers);

        RecyclerView leaderboard = findViewById(R.id.rvRegionLeaderboard);
        leaderboard.setLayoutManager(new LinearLayoutManager(this));
        leaderboard.setNestedScrollingEnabled(false);
        leaderboardAdapter = new RegionLeaderboardAdapter();
        leaderboard.setAdapter(leaderboardAdapter);
    }

    private void loadDashboard(String uid) {
        repository.loadDashboard(uid, new RegionRepository.DashboardCallback() {
            @Override
            public void onSuccess(RegionRepository.DashboardData data) {
                runOnUiThread(() -> renderDashboard(data));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressRegions.setVisibility(View.GONE);
                    Toast.makeText(RegionMapActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderDashboard(RegionRepository.DashboardData data) {
        dashboardData = data;
        progressRegions.setVisibility(View.GONE);
        regionMapView.setData(data.summaries, data.playerPoints, data.currentPlayerRegion);
        leaderboardAdapter.submit(data.leaderboard);

        RegionSummary selected = data.currentPlayerRegion != null
                ? data.summaryFor(data.currentPlayerRegion.id)
                : (data.summaries.isEmpty() ? null : data.summaries.get(0));
        renderRegionStats(selected);
    }

    private void renderRegionStats(RegionSummary summary) {
        if (summary == null) {
            tvRegionStatsTitle.setText("Statistika regiona nije dostupna");
            return;
        }
        tvRegionStatsTitle.setText(summary.region.icon + " " + summary.region.displayName);
        tvFirstPlaces.setText(String.format(Locale.getDefault(),
                "Prvih mesta: %d", summary.firstPlaces));
        tvSecondPlaces.setText(String.format(Locale.getDefault(),
                "Drugih mesta: %d", summary.secondPlaces));
        tvThirdPlaces.setText(String.format(Locale.getDefault(),
                "Trećih mesta: %d", summary.thirdPlaces));
        tvActivePlayers.setText(String.format(Locale.getDefault(),
                "Trenutno aktivnih igrača: %d", summary.activePlayers));
        tvRegisteredPlayers.setText(String.format(Locale.getDefault(),
                "Ukupno registrovanih igrača: %d", summary.registeredPlayers));
    }

    private final class RegionLeaderboardAdapter
            extends RecyclerView.Adapter<RegionLeaderboardViewHolder> {
        private final List<RegionLeaderboardRow> rows = new ArrayList<>();

        void submit(List<RegionLeaderboardRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RegionLeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_region_leaderboard, parent, false);
            return new RegionLeaderboardViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RegionLeaderboardViewHolder holder, int position) {
            RegionLeaderboardRow row = rows.get(position);
            holder.tvRank.setText(String.valueOf(row.rank));
            holder.tvIcon.setText(row.region.icon);
            holder.tvName.setText(row.region.displayName);
            holder.tvStars.setText(String.format(Locale.getDefault(), "%d ★", row.monthlyStars));
            holder.tvCurrent.setVisibility(row.currentPlayerRegion ? View.VISIBLE : View.GONE);
            holder.itemView.setBackgroundColor(row.currentPlayerRegion
                    ? Color.rgb(237, 231, 246) : Color.WHITE);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class RegionLeaderboardViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvStars;
        final TextView tvCurrent;

        RegionLeaderboardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRegionRank);
            tvIcon = itemView.findViewById(R.id.tvRegionIcon);
            tvName = itemView.findViewById(R.id.tvRegionName);
            tvStars = itemView.findViewById(R.id.tvRegionMonthlyStars);
            tvCurrent = itemView.findViewById(R.id.tvCurrentRegionMarker);
        }
    }
}
