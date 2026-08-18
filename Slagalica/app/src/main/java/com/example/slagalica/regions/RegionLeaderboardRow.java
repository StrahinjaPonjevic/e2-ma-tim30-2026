package com.example.slagalica.regions;

public final class RegionLeaderboardRow {
    public final int rank;
    public final RegionDefinition region;
    public final int monthlyStars;
    public final boolean currentPlayerRegion;

    public RegionLeaderboardRow(int rank, RegionDefinition region, int monthlyStars,
                                boolean currentPlayerRegion) {
        this.rank = rank;
        this.region = region;
        this.monthlyStars = monthlyStars;
        this.currentPlayerRegion = currentPlayerRegion;
    }
}
