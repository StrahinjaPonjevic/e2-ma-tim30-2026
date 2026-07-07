package com.example.slagalica.ranking;

import com.example.slagalica.leagues.LeagueProgressionHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.Map;

public final class StarRewardHelper {

    private StarRewardHelper() {
    }

    public static Map<String, Object> starAwardUpdates(DocumentSnapshot user, int starsDelta, int tokensDelta) {
        int currentStars = intValue(user.get("stars"));
        int newStars = Math.max(0, currentStars + starsDelta);
        Map<String, Object> updates = LeagueProgressionHelper.buildStarsAndLeagueUpdate(currentStars, newStars);

        int totalTokens = tokensDelta;
        if (starsDelta > 0) {
            int currentProgress = intValue(user.get("starTokenProgress"));
            int newProgress = currentProgress + starsDelta;
            totalTokens += newProgress / 50;
            updates.put("starTokenProgress", newProgress % 50);
        }
        if (totalTokens != 0) {
            updates.put("tokens", FieldValue.increment(totalTokens));
        }

        String currentMonth = CycleUtils.currentMonthKey();
        int currentMonthlyStars = currentMonth.equals(user.getString("monthlyRankMonth"))
                ? intValue(user.get("monthlyStars"))
                : 0;
        updates.put("monthlyRankMonth", currentMonth);
        updates.put("monthlyStars", Math.max(0, currentMonthlyStars + starsDelta));

        String currentWeek = CycleUtils.currentWeekKey();
        int currentWeeklyStars = currentWeek.equals(user.getString("weeklyRankKey"))
                ? intValue(user.get("weeklyStars"))
                : 0;
        updates.put("weeklyRankKey", currentWeek);
        updates.put("weeklyStars", Math.max(0, currentWeeklyStars + starsDelta));
        return updates;
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
