package com.example.slagalica.regions;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class RegionDefinition {

    public static final List<RegionDefinition> ALL = Collections.unmodifiableList(Arrays.asList(
            new RegionDefinition("backa", "Bačka", "Bačka", "🌾", 0xFFFFD166, new float[][]{
                    {0.24f, 0.04f}, {0.48f, 0.04f}, {0.48f, 0.23f}, {0.20f, 0.23f}, {0.16f, 0.14f}
            }),
            new RegionDefinition("banat", "Banat", "Banat", "🌻", 0xFFFFB347, new float[][]{
                    {0.48f, 0.04f}, {0.70f, 0.08f}, {0.76f, 0.27f}, {0.52f, 0.33f}, {0.48f, 0.23f}
            }),
            new RegionDefinition("srem", "Srem", "Srem", "🍇", 0xFFB39DDB, new float[][]{
                    {0.12f, 0.23f}, {0.48f, 0.23f}, {0.52f, 0.33f}, {0.40f, 0.38f}, {0.16f, 0.34f}
            }),
            new RegionDefinition("beograd", "Beograd", "BG", "🏙️", 0xFF90CAF9, new float[][]{
                    {0.40f, 0.38f}, {0.52f, 0.33f}, {0.58f, 0.40f}, {0.50f, 0.47f}, {0.40f, 0.45f}
            }),
            new RegionDefinition("sumadija", "Šumadija", "Šumadija", "🌳", 0xFFA5D6A7, new float[][]{
                    {0.30f, 0.39f}, {0.40f, 0.38f}, {0.40f, 0.45f}, {0.50f, 0.47f},
                    {0.54f, 0.61f}, {0.30f, 0.61f}, {0.24f, 0.50f}
            }),
            new RegionDefinition("zapadna_srbija", "Zapadna Srbija", "Zapad", "⛰️", 0xFF81C784, new float[][]{
                    {0.16f, 0.34f}, {0.40f, 0.38f}, {0.30f, 0.39f}, {0.24f, 0.50f},
                    {0.30f, 0.61f}, {0.23f, 0.75f}, {0.11f, 0.65f}, {0.12f, 0.48f}
            }),
            new RegionDefinition("istocna_srbija", "Istočna Srbija", "Istok", "🏞️", 0xFF80CBC4, new float[][]{
                    {0.52f, 0.33f}, {0.70f, 0.32f}, {0.76f, 0.48f}, {0.66f, 0.67f},
                    {0.54f, 0.61f}, {0.50f, 0.47f}, {0.58f, 0.40f}
            }),
            new RegionDefinition("juzna_srbija", "Južna Srbija", "Jug", "☀️", 0xFFFFCC80, new float[][]{
                    {0.30f, 0.61f}, {0.54f, 0.61f}, {0.66f, 0.67f}, {0.62f, 0.81f},
                    {0.45f, 0.87f}, {0.23f, 0.75f}
            }),
            new RegionDefinition("kosovo_i_metohija", "Kosovo i Metohija", "KiM", "🕊️", 0xFFE0E0E0, new float[][]{
                    {0.23f, 0.75f}, {0.45f, 0.87f}, {0.47f, 0.96f}, {0.30f, 0.95f}, {0.15f, 0.84f}
            })
    ));

    public final String id;
    public final String displayName;
    public final String shortLabel;
    public final String icon;
    public final int color;
    public final float[][] polygon;

    private RegionDefinition(String id, String displayName, String shortLabel, String icon,
                             int color, float[][] polygon) {
        this.id = id;
        this.displayName = displayName;
        this.shortLabel = shortLabel;
        this.icon = icon;
        this.color = color;
        this.polygon = polygon;
    }

    public static RegionDefinition findByDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        for (RegionDefinition definition : ALL) {
            if (definition.displayName.equalsIgnoreCase(displayName.trim())) {
                return definition;
            }
        }
        return null;
    }

    public static RegionDefinition findById(String id) {
        if (id == null) {
            return null;
        }
        for (RegionDefinition definition : ALL) {
            if (definition.id.equalsIgnoreCase(id.trim())) {
                return definition;
            }
        }
        return null;
    }

    public static RegionDefinition find(String value) {
        RegionDefinition byName = findByDisplayName(value);
        return byName != null ? byName : findById(value);
    }

    public static String currentMonthKey() {
        return monthKey(Calendar.getInstance());
    }

    public static String previousMonthKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);
        return monthKey(calendar);
    }

    private static String monthKey(Calendar calendar) {
        return String.format(Locale.US, "%04d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
    }

    public boolean contains(float x, float y) {
        boolean inside = false;
        int previous = polygon.length - 1;
        for (int current = 0; current < polygon.length; current++) {
            float currentX = polygon[current][0];
            float currentY = polygon[current][1];
            float previousX = polygon[previous][0];
            float previousY = polygon[previous][1];
            boolean crosses = (currentY > y) != (previousY > y)
                    && x < (previousX - currentX) * (y - currentY)
                    / (previousY - currentY) + currentX;
            if (crosses) {
                inside = !inside;
            }
            previous = current;
        }
        return inside;
    }

    public float[] randomPoint(Random random) {
        float minX = 1f;
        float minY = 1f;
        float maxX = 0f;
        float maxY = 0f;
        for (float[] point : polygon) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }

        for (int attempt = 0; attempt < 200; attempt++) {
            float x = minX + random.nextFloat() * (maxX - minX);
            float y = minY + random.nextFloat() * (maxY - minY);
            if (contains(x, y)) {
                return new float[]{x, y};
            }
        }
        return centroid();
    }

    public float[] deterministicPoint(String key) {
        long seed = 1469598103934665603L;
        String value = key != null ? key : id;
        for (int i = 0; i < value.length(); i++) {
            seed ^= value.charAt(i);
            seed *= 1099511628211L;
        }
        return randomPoint(new Random(seed));
    }

    private float[] centroid() {
        float x = 0f;
        float y = 0f;
        for (float[] point : polygon) {
            x += point[0];
            y += point[1];
        }
        return new float[]{x / polygon.length, y / polygon.length};
    }
}
