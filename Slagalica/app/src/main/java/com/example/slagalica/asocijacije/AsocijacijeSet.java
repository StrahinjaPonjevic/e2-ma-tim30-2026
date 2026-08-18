package com.example.slagalica.asocijacije;

import java.util.ArrayList;
import java.util.List;

public class AsocijacijeSet {

    private final List<String> columnA;
    private final List<String> columnB;
    private final List<String> columnC;
    private final List<String> columnD;
    private final String solutionA;
    private final String solutionB;
    private final String solutionC;
    private final String solutionD;
    private final String finalSolution;

    public AsocijacijeSet(List<String> columnA, String solutionA,
                          List<String> columnB, String solutionB,
                          List<String> columnC, String solutionC,
                          List<String> columnD, String solutionD,
                          String finalSolution) {
        this.columnA = new ArrayList<>(columnA);
        this.columnB = new ArrayList<>(columnB);
        this.columnC = new ArrayList<>(columnC);
        this.columnD = new ArrayList<>(columnD);
        this.solutionA = solutionA;
        this.solutionB = solutionB;
        this.solutionC = solutionC;
        this.solutionD = solutionD;
        this.finalSolution = finalSolution;
    }

    public List<String> getColumnItems(char column) {
        switch (column) {
            case 'A':
                return columnA;
            case 'B':
                return columnB;
            case 'C':
                return columnC;
            default:
                return columnD;
        }
    }

    public String getColumnSolution(char column) {
        switch (column) {
            case 'A':
                return solutionA;
            case 'B':
                return solutionB;
            case 'C':
                return solutionC;
            default:
                return solutionD;
        }
    }

    public String getFinalSolution() {
        return finalSolution;
    }

    public boolean isValid() {
        return columnA.size() == 4 && columnB.size() == 4 && columnC.size() == 4 && columnD.size() == 4
                && solutionA != null && solutionB != null && solutionC != null && solutionD != null
                && finalSolution != null;
    }
}
