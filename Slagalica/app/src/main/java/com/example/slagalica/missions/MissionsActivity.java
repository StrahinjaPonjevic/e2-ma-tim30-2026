package com.example.slagalica.missions;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.ranking.CycleUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class MissionsActivity extends AppCompatActivity {

    private TextView tvMissionsDay, tvMissionWin, tvMissionChat, tvMissionFriendly,
            tvMissionTournament, tvMissionsBonus, tvMissionsProgress;
    private Button btnMissionsBack;
    private ListenerRegistration missionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missions);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za dnevne misije.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvMissionsDay = findViewById(R.id.tvMissionsDay);
        tvMissionWin = findViewById(R.id.tvMissionWin);
        tvMissionChat = findViewById(R.id.tvMissionChat);
        tvMissionFriendly = findViewById(R.id.tvMissionFriendly);
        tvMissionTournament = findViewById(R.id.tvMissionTournament);
        tvMissionsBonus = findViewById(R.id.tvMissionsBonus);
        tvMissionsProgress = findViewById(R.id.tvMissionsProgress);
        btnMissionsBack = findViewById(R.id.btnMissionsBack);

        tvMissionsDay.setText("Misije za dan: " + CycleUtils.currentDayKey());
        btnMissionsBack.setOnClickListener(v -> finish());

        missionsListener = MissionsRepository.listenToday(currentUser.getUid(),
                new MissionsRepository.MissionsListener() {
                    @Override
                    public void onChanged(MissionsRepository.MissionState state) {
                        runOnUiThread(() -> render(state));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(MissionsActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void render(MissionsRepository.MissionState state) {
        renderMission(tvMissionWin, "Pobedi partiju", state.winParty);
        renderMission(tvMissionChat, "Posalji poruku u cet", state.chatMessage);
        renderMission(tvMissionFriendly, "Odigraj prijateljsku partiju", state.friendlyParty);
        renderMission(tvMissionTournament, "Pobedi partiju u turniru", state.tournamentWin);

        tvMissionsProgress.setText("Zavrseno: " + state.completedCount() + "/4");
        if (state.bonusAwarded) {
            tvMissionsBonus.setText("\u2705 Bonus osvojen: +2 tokena i +3 zvezde!");
            tvMissionsBonus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            tvMissionsBonus.setText("Resi sve 4 misije za bonus: +2 tokena i +3 zvezde");
            tvMissionsBonus.setTextColor(Color.parseColor("#555555"));
        }
    }

    private void renderMission(TextView view, String title, boolean completed) {
        view.setText((completed ? "\u2705 " : "\u2b1c ") + title + "  (+3 \u2b50)");
        view.setTextColor(completed ? Color.parseColor("#2E7D32") : Color.parseColor("#333333"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (missionsListener != null) {
            missionsListener.remove();
        }
    }
}
