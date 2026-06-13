package com.example.slagalica.spojnice;

public final class SpojniceEvaluator {

    private SpojniceEvaluator() {
    }

    public static boolean isCorrectMatch(SpojniceSet set, int leftIndex, int rightIndex) {
        if (set == null || leftIndex < 0 || leftIndex >= set.getCorrectRightForLeft().size()) {
            return false;
        }
        return set.getCorrectRightForLeft().get(leftIndex) == rightIndex;
    }
}
