package com.example.slagalica.profile;

public class UserProfile {

    public final String uid;
    public final String username;
    public final String email;
    public final String region;
    public final int avatarTheme;
    public final int tokens;
    public final int stars;
    public final int matchesPlayed;
    public final int wins;
    public final int losses;
    public final GameStats koZnaZna;
    public final GameStats spojnice;
    public final GameStats mojBroj;
    public final GameStats korakPoKorak;

    public UserProfile(String uid, String username, String email, String region, int avatarTheme,
                       int tokens, int stars, int matchesPlayed, int wins, int losses,
                       GameStats koZnaZna, GameStats spojnice, GameStats mojBroj, GameStats korakPoKorak) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.region = region;
        this.avatarTheme = avatarTheme;
        this.tokens = tokens;
        this.stars = stars;
        this.matchesPlayed = matchesPlayed;
        this.wins = wins;
        this.losses = losses;
        this.koZnaZna = koZnaZna;
        this.spojnice = spojnice;
        this.mojBroj = mojBroj;
        this.korakPoKorak = korakPoKorak;
    }

    public static class GameStats {
        public final int gamesPlayed;
        public final int totalScore;
        public final int correctAnswers;
        public final int wrongAnswers;
        public final int successfulLinks;
        public final int attemptedLinks;
        public final int exactHits;
        public final int roundsPlayed;
        public final int step1Hits;
        public final int step2Hits;
        public final int step3Hits;
        public final int step4Hits;
        public final int step5Hits;
        public final int step6Hits;
        public final int step7Hits;

        public GameStats(int gamesPlayed, int totalScore, int correctAnswers, int wrongAnswers,
                         int successfulLinks, int attemptedLinks, int exactHits, int roundsPlayed,
                         int step1Hits, int step2Hits, int step3Hits, int step4Hits,
                         int step5Hits, int step6Hits, int step7Hits) {
            this.gamesPlayed = gamesPlayed;
            this.totalScore = totalScore;
            this.correctAnswers = correctAnswers;
            this.wrongAnswers = wrongAnswers;
            this.successfulLinks = successfulLinks;
            this.attemptedLinks = attemptedLinks;
            this.exactHits = exactHits;
            this.roundsPlayed = roundsPlayed;
            this.step1Hits = step1Hits;
            this.step2Hits = step2Hits;
            this.step3Hits = step3Hits;
            this.step4Hits = step4Hits;
            this.step5Hits = step5Hits;
            this.step6Hits = step6Hits;
            this.step7Hits = step7Hits;
        }
    }
}
