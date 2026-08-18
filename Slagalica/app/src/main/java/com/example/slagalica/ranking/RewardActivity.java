package com.example.slagalica.ranking;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RewardActivity extends AppCompatActivity {

    private TextView tvTrophy, tvRewardText, tvStarsLeft, tvStarsRight;
    private Button btnClaim;
    private ToneGenerator toneGenerator;
    private final Handler soundHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reward);

        tvTrophy = findViewById(R.id.tvTrophy);
        tvRewardText = findViewById(R.id.tvRewardText);
        tvStarsLeft = findViewById(R.id.tvStarsLeft);
        tvStarsRight = findViewById(R.id.tvStarsRight);
        btnClaim = findViewById(R.id.btnClaimReward);

        String rewardText = getIntent().getStringExtra("rewardText");
        tvRewardText.setText(rewardText != null && !rewardText.trim().isEmpty()
                ? rewardText
                : "Osvojili ste nagradu na rang listi!");

        btnClaim.setOnClickListener(v -> claimAndClose());

        playAnimation();
        playSound();
    }

    private void playAnimation() {
        tvTrophy.setScaleX(0f);
        tvTrophy.setScaleY(0f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvTrophy, "scaleX", 0f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvTrophy, "scaleY", 0f, 1.3f, 1f);
        scaleX.setDuration(900);
        scaleY.setDuration(900);
        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());
        scaleX.start();
        scaleY.start();

        ObjectAnimator wobble = ObjectAnimator.ofFloat(tvTrophy, "rotation", -8f, 8f);
        wobble.setDuration(700);
        wobble.setRepeatCount(ValueAnimator.INFINITE);
        wobble.setRepeatMode(ValueAnimator.REVERSE);
        wobble.setStartDelay(900);
        wobble.start();

        animateStars(tvStarsLeft, 0);
        animateStars(tvStarsRight, 350);
    }

    private void animateStars(TextView view, long delay) {
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 0.2f, 1f);
        alpha.setDuration(650);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatMode(ValueAnimator.REVERSE);
        alpha.setStartDelay(delay);
        alpha.start();

        ObjectAnimator translate = ObjectAnimator.ofFloat(view, "translationY", 0f, -24f);
        translate.setDuration(900);
        translate.setRepeatCount(ValueAnimator.INFINITE);
        translate.setRepeatMode(ValueAnimator.REVERSE);
        translate.setStartDelay(delay);
        translate.start();
    }

    private void playSound() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
            soundHandler.postDelayed(() -> safeTone(ToneGenerator.TONE_PROP_BEEP), 150);
            soundHandler.postDelayed(() -> safeTone(ToneGenerator.TONE_PROP_BEEP2), 500);
            soundHandler.postDelayed(() -> safeTone(ToneGenerator.TONE_PROP_ACK), 900);
        } catch (RuntimeException ignored) {
        }
    }

    private void safeTone(int tone) {
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(tone, 220);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void claimAndClose() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            new RankingRepository().clearPendingReward(currentUser.getUid());
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        soundHandler.removeCallbacksAndMessages(null);
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }
}
