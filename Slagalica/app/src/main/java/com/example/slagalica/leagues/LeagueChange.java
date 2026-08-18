package com.example.slagalica.leagues;

public final class LeagueChange {

    public final LeagueDefinition from;
    public final LeagueDefinition to;
    public final int fromLevel;
    public final int toLevel;
    public final String direction;

    public LeagueChange(LeagueDefinition from, LeagueDefinition to) {
        this.from = from;
        this.to = to;
        this.fromLevel = from.level;
        this.toLevel = to.level;
        this.direction = to.level > from.level ? "up" : "down";
    }

    public boolean isUp() {
        return "up".equals(direction);
    }

    public boolean isDown() {
        return "down".equals(direction);
    }
}
