package com.example.slagalica.leagues;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LeagueDefinition {

    public static final LeagueDefinition BEGINNER =
            new LeagueDefinition(0, "Početnička liga", "🌱", 0);
    public static final LeagueDefinition BRONZE =
            new LeagueDefinition(1, "Bronzana liga", "🥉", 100);
    public static final LeagueDefinition SILVER =
            new LeagueDefinition(2, "Srebrna liga", "🥈", 200);
    public static final LeagueDefinition GOLD =
            new LeagueDefinition(3, "Zlatna liga", "🥇", 400);
    public static final LeagueDefinition DIAMOND =
            new LeagueDefinition(4, "Dijamantska liga", "💎", 800);
    public static final LeagueDefinition LEGENDARY =
            new LeagueDefinition(5, "Legendarna liga", "👑", 1600);

    public static final List<LeagueDefinition> ALL = Collections.unmodifiableList(Arrays.asList(
            BEGINNER,
            BRONZE,
            SILVER,
            GOLD,
            DIAMOND,
            LEGENDARY
    ));

    public final int level;
    public final String name;
    public final String icon;
    public final int requiredStars;

    private LeagueDefinition(int level, String name, String icon, int requiredStars) {
        this.level = level;
        this.name = name;
        this.icon = icon;
        this.requiredStars = requiredStars;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public int getRequiredStars() {
        return requiredStars;
    }
}
