package com.example.slagalica.leagues;

public final class LeagueUiHelper {

    private LeagueUiHelper() {
    }

    public static String displayNameForStars(int stars) {
        return displayName(LeagueProgressionHelper.resolveLeague(stars));
    }

    public static String displayNameForLevel(int level) {
        return displayName(LeagueProgressionHelper.resolveLeagueByLevel(level));
    }

    public static String profileSummaryForStars(int stars) {
        LeagueDefinition current = LeagueProgressionHelper.resolveLeague(stars);
        int dailyTokens = LeagueProgressionHelper.dailyTokenGrant(stars);
        if (current.level == LeagueDefinition.LEGENDARY.level) {
            return displayName(current) + " | Maksimalna liga | Dnevni tokeni: " + dailyTokens;
        }

        LeagueDefinition next = LeagueProgressionHelper.resolveLeagueByLevel(current.level + 1);
        int remainingStars = Math.max(0, next.requiredStars - Math.max(0, stars));
        return displayName(current) + " | Do sledeće: " + remainingStars
                + " zvezda | Dnevni tokeni: " + dailyTokens;
    }

    public static String changeMessage(int fromLevel, int toLevel, String direction) {
        boolean movedUp = "up".equals(direction) || (direction == null && toLevel > fromLevel);
        if (movedUp) {
            return "Čestitamo! Prešao si u " + displayNameForLevel(toLevel) + ".";
        }
        return "Pao si u " + displayNameForLevel(toLevel) + ".";
    }

    private static String displayName(LeagueDefinition league) {
        return league.icon + " " + league.name;
    }
}
