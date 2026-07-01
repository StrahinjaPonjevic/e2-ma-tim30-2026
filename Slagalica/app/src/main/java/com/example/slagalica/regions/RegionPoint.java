package com.example.slagalica.regions;

public final class RegionPoint {
    public final String uid;
    public final String regionId;
    public final float x;
    public final float y;
    public final boolean currentPlayer;

    public RegionPoint(String uid, String regionId, float x, float y, boolean currentPlayer) {
        this.uid = uid;
        this.regionId = regionId;
        this.x = x;
        this.y = y;
        this.currentPlayer = currentPlayer;
    }
}
