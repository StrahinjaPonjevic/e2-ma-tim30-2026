package com.example.slagalica.regions;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public final class RegionAvatarFrameHelper {

    private static final int GOLD = Color.rgb(255, 193, 7);
    private static final int SILVER = Color.rgb(176, 190, 197);
    private static final int BRONZE = Color.rgb(205, 127, 50);

    private RegionAvatarFrameHelper() {
    }

    public static void apply(View avatarView, int fillColor, int rank, String cycleMonth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);

        int frameColor = frameColor(rank, cycleMonth);
        float density = avatarView.getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round((frameColor == Color.TRANSPARENT ? 2f : 5f) * density));
        drawable.setStroke(width, frameColor == Color.TRANSPARENT
                ? Color.rgb(255, 248, 239) : frameColor);
        avatarView.setBackground(drawable);
    }

    public static boolean hasCurrentFrame(int rank, String cycleMonth) {
        return rank >= 1 && rank <= 3
                && RegionDefinition.previousMonthKey().equals(cycleMonth);
    }

    public static int frameColor(int rank, String cycleMonth) {
        if (!hasCurrentFrame(rank, cycleMonth)) {
            return Color.TRANSPARENT;
        }
        if (rank == 1) {
            return GOLD;
        }
        if (rank == 2) {
            return SILVER;
        }
        return BRONZE;
    }
}
