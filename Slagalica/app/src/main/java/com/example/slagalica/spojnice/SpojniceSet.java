package com.example.slagalica.spojnice;

import java.util.List;

public class SpojniceSet {

    private final List<String> leftItems;
    private final List<String> rightItems;
    private final List<Integer> correctRightForLeft;

    public SpojniceSet(List<String> leftItems, List<String> rightItems, List<Integer> correctRightForLeft) {
        this.leftItems = leftItems;
        this.rightItems = rightItems;
        this.correctRightForLeft = correctRightForLeft;
    }

    public List<String> getLeftItems() {
        return leftItems;
    }

    public List<String> getRightItems() {
        return rightItems;
    }

    public List<Integer> getCorrectRightForLeft() {
        return correctRightForLeft;
    }
}
