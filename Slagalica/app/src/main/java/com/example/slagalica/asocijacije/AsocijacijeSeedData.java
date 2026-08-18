package com.example.slagalica.asocijacije;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AsocijacijeSeedData {

    private AsocijacijeSeedData() {
    }

    public static List<AsocijacijeSet> createSets() {
        List<AsocijacijeSet> sets = new ArrayList<>();

        sets.add(new AsocijacijeSet(
                Arrays.asList("Sapun", "Azot", "Kristal", "Govor"), "TECNI",
                Arrays.asList("Ogledalo", "Escajg", "Medalja", "Nakit"), "SREBRO",
                Arrays.asList("Toplomer", "Ograda", "Svirka", "Istina"), "ZIVA",
                Arrays.asList("Zub", "Groznica", "Ribica", "Cutanje"), "ZLATO",
                "METAL"
        ));

        sets.add(new AsocijacijeSet(
                Arrays.asList("Mreza", "Loptica", "Reket", "Teren"), "TENIS",
                Arrays.asList("Gol", "Sudija", "Trava", "Kopacke"), "FUDBAL",
                Arrays.asList("Kos", "Trojka", "Parket", "Skok"), "KOSARKA",
                Arrays.asList("Bazen", "Kapica", "Voda", "Stil"), "PLIVANJE",
                "SPORT"
        ));

        sets.add(new AsocijacijeSet(
                Arrays.asList("Tastatura", "Mis", "Ekran", "Procesor"), "RACUNAR",
                Arrays.asList("Ekran", "Poziv", "Aplikacija", "Poruka"), "TELEFON",
                Arrays.asList("Struja", "Punjac", "Kapacitet", "Prazna"), "BATERIJA",
                Arrays.asList("Mreza", "Sajt", "Pretraga", "Brzina"), "INTERNET",
                "TEHNOLOGIJA"
        ));

        return sets;
    }
}
