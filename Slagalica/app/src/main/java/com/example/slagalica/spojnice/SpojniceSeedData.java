package com.example.slagalica.spojnice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SpojniceSeedData {

    private SpojniceSeedData() {
    }

    public static List<SpojniceSet> createSets() {
        List<SpojniceSet> sets = new ArrayList<>();

        sets.add(new SpojniceSet(
                Arrays.asList("Nikola Tesla", "Novak Đoković", "Ivo Andrić", "Mocart", "Albert Einstein"),
                Arrays.asList("Tenis", "Relativnost", "Naizmenična struja", "Muzika", "Na Drini ćuprija"),
                Arrays.asList(2, 0, 4, 3, 1)
        ));

        sets.add(new SpojniceSet(
                Arrays.asList("Lav", "Orao", "Delfin", "Vuk", "Medved"),
                Arrays.asList("More", "Šuma", "Nebo", "Planina", "Safari"),
                Arrays.asList(4, 2, 0, 1, 3)
        ));

        sets.add(new SpojniceSet(
                Arrays.asList("Java", "HTML", "SQL", "Python", "CSS"),
                Arrays.asList("Stil stranice", "Baza podataka", "Skript jezik", "Programski jezik", "Struktura stranice"),
                Arrays.asList(3, 4, 1, 2, 0)
        ));

        return sets;
    }
}
