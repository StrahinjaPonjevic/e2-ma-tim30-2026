package com.example.slagalica.regions;

public final class RegionSummary {
    public final RegionDefinition region;
    public final int monthlyStars;
    public final int firstPlaces;
    public final int secondPlaces;
    public final int thirdPlaces;
    public final int activePlayers;
    public final int registeredPlayers;

    public RegionSummary(RegionDefinition region, int monthlyStars,
                         int firstPlaces, int secondPlaces, int thirdPlaces,
                         int activePlayers, int registeredPlayers) {
        this.region = region;
        this.monthlyStars = monthlyStars;
        this.firstPlaces = firstPlaces;
        this.secondPlaces = secondPlaces;
        this.thirdPlaces = thirdPlaces;
        this.activePlayers = activePlayers;
        this.registeredPlayers = registeredPlayers;
    }
}
