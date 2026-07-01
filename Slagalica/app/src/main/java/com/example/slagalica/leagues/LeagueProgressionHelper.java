package com.example.slagalica.leagues;

import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LeagueProgressionHelper {

    private static final int BASE_DAILY_TOKENS = 5;

    private LeagueProgressionHelper() {
    }

    public static int resolveLeagueLevel(int stars) {
        return resolveLeague(stars).level;
    }

    public static LeagueDefinition resolveLeague(int stars) {
        int safeStars = Math.max(0, stars);
        for (int index = LeagueDefinition.ALL.size() - 1; index >= 0; index--) {
            LeagueDefinition league = LeagueDefinition.ALL.get(index);
            if (safeStars >= league.requiredStars) {
                return league;
            }
        }
        return LeagueDefinition.BEGINNER;
    }

    public static LeagueDefinition resolveLeagueByLevel(int level) {
        int safeLevel = Math.max(0, Math.min(level, LeagueDefinition.ALL.size() - 1));
        return LeagueDefinition.ALL.get(safeLevel);
    }

    public static int dailyTokenGrant(int stars) {
        return BASE_DAILY_TOKENS + resolveLeagueLevel(stars);
    }

    public static LeagueChange detectChange(int oldStars, int newStars) {
        LeagueDefinition oldLeague = resolveLeague(oldStars);
        LeagueDefinition newLeague = resolveLeague(newStars);
        if (oldLeague.level == newLeague.level) {
            return null;
        }
        return new LeagueChange(oldLeague, newLeague);
    }

    public static Map<String, Object> buildStarsAndLeagueUpdate(int oldStars, int newStars) {
        int safeNewStars = Math.max(0, newStars);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stars", safeNewStars);
        updates.put("leagueLevel", resolveLeagueLevel(safeNewStars));
        updates.put("updatedAt", FieldValue.serverTimestamp());

        LeagueChange change = detectChange(oldStars, safeNewStars);
        if (change != null) {
            updates.put("lastLeagueChangeFrom", change.fromLevel);
            updates.put("lastLeagueChangeTo", change.toLevel);
            updates.put("lastLeagueChangeDirection", change.direction);
            updates.put("lastLeagueChangeAt", FieldValue.serverTimestamp());
            updates.put("lastLeagueNotificationId", UUID.randomUUID().toString());
        }
        return updates;
    }
}
