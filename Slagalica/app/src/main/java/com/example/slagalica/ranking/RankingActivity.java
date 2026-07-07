package com.example.slagalica.ranking;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.leagues.LeagueUiHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RankingActivity extends AppCompatActivity {

    private static final long REFRESH_INTERVAL_MS = 2 * 60 * 1000L;

    private Button btnWeekly, btnMonthly, btnRankingBack;
    private TextView tvCycleRange, tvLastRefresh, tvRankingEmpty;
    private RecyclerView rvRanking;

    private RankingRepository rankingRepository;
    private RankingAdapter adapter;
    private String currentUserId;
    private boolean weeklyMode = true;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadRanking();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za rang liste.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        rankingRepository = new RankingRepository();

        btnWeekly = findViewById(R.id.btnWeekly);
        btnMonthly = findViewById(R.id.btnMonthly);
        btnRankingBack = findViewById(R.id.btnRankingBack);
        tvCycleRange = findViewById(R.id.tvCycleRange);
        tvLastRefresh = findViewById(R.id.tvLastRefresh);
        tvRankingEmpty = findViewById(R.id.tvRankingEmpty);
        rvRanking = findViewById(R.id.rvRanking);

        adapter = new RankingAdapter();
        rvRanking.setLayoutManager(new LinearLayoutManager(this));
        rvRanking.setAdapter(adapter);

        btnWeekly.setOnClickListener(v -> switchMode(true));
        btnMonthly.setOnClickListener(v -> switchMode(false));
        btnRankingBack.setOnClickListener(v -> finish());

        rankingRepository.finalizePreviousCyclesIfNeeded();
        switchMode(true);
    }

    private void switchMode(boolean weekly) {
        weeklyMode = weekly;
        btnWeekly.setBackgroundColor(weekly ? Color.parseColor("#6200EE") : Color.parseColor("#9E9E9E"));
        btnMonthly.setBackgroundColor(weekly ? Color.parseColor("#9E9E9E") : Color.parseColor("#6200EE"));
        tvCycleRange.setText("Ciklus: " + (weekly ? CycleUtils.weekRangeLabel() : CycleUtils.monthRangeLabel()));
        loadRanking();
    }

    private void loadRanking() {
        boolean requestedWeekly = weeklyMode;
        rankingRepository.loadCycle(requestedWeekly, new RankingRepository.RowsCallback() {
            @Override
            public void onRows(List<RankingRepository.RankingRow> rows) {
                runOnUiThread(() -> {
                    if (requestedWeekly != weeklyMode) {
                        return;
                    }
                    adapter.submit(rows);
                    tvRankingEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    tvLastRefresh.setText("Osvezeno: " + new SimpleDateFormat("HH:mm:ss",
                            Locale.getDefault()).format(new Date()));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(RankingActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        loadRanking();
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RowHolder> {

        private final List<RankingRepository.RankingRow> items = new ArrayList<>();

        void submit(List<RankingRepository.RankingRow> rows) {
            items.clear();
            items.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ranking_row, parent, false);
            return new RowHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            RankingRepository.RankingRow row = items.get(position);
            holder.tvRank.setText(row.rank + ".");
            holder.tvName.setText(row.username);
            holder.tvLeague.setText(LeagueUiHelper.displayNameForStars(row.totalStars));
            holder.tvStars.setText(row.cycleStars + " \u2b50");

            boolean isSelf = row.uid.equals(currentUserId);
            holder.itemView.setBackgroundColor(isSelf ? Color.parseColor("#EDE7F6") : Color.WHITE);
            holder.tvName.setTypeface(null, isSelf ? Typeface.BOLD : Typeface.NORMAL);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class RowHolder extends RecyclerView.ViewHolder {
            final TextView tvRank;
            final TextView tvName;
            final TextView tvLeague;
            final TextView tvStars;

            RowHolder(@NonNull View itemView) {
                super(itemView);
                tvRank = itemView.findViewById(R.id.tvRowRank);
                tvName = itemView.findViewById(R.id.tvRowName);
                tvLeague = itemView.findViewById(R.id.tvRowLeague);
                tvStars = itemView.findViewById(R.id.tvRowStars);
            }
        }
    }
}
