package com.example.slagalica;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KPKQuestionData {

    public static class QuestionSet {
        public final String[] clues;
        public final String answer;

        public QuestionSet(String[] clues, String answer) {
            this.clues = clues;
            this.answer = answer;
        }
    }

    private static final QuestionSet[] SETS = new QuestionSet[]{
            new QuestionSet(
                    new String[]{
                            "Ima veze sa Savom i Dunavom",
                            "Ima veze sa zdravljem",
                            "Za vozilo je obavezno",
                            "Ima veze sa morem",
                            "Ima veze sa sportom",
                            "Ima veze sa plivanjem",
                            "Potrebno za brod"
                    },
                    "Voda"
            ),
            new QuestionSet(
                    new String[]{
                            "Ima veze sa nebom",
                            "Ima veze sa muzikom",
                            "Ima veze sa medijima",
                            "Ima veze sa filmom",
                            "Ima veze sa pozorištem",
                            "Ima veze sa kućom",
                            "Pušta se na radiju"
                    },
                    "Zvuk"
            ),
            new QuestionSet(
                    new String[]{
                            "Ima veze sa školom",
                            "Ima veze sa pisanjem",
                            "Ima veze sa bibliotekom",
                            "Ima veze sa crtanjem",
                            "Ima veze sa novinama",
                            "Ima veze sa zakonom",
                            "Ima veze sa potpisom"
                    },
                    "Slovo"
            ),
            new QuestionSet(
                    new String[]{
                            "Ima veze sa gradom",
                            "Ima veze sa saobraćajem",
                            "Ima veze sa vremenom",
                            "Ima veze sa rasporedom",
                            "Ima veze sa brojevima",
                            "Ima veze sa Perom",
                            "Ima veze sa brodom"
                    },
                    "Sat"
            ),
            new QuestionSet(
                    new String[]{
                            "Ima veze sa prirodom",
                            "Ima veze sa hranom",
                            "Ima veze sa zemljom",
                            "Ima veze sa cvećem",
                            "Ima veze sa voćem",
                            "Ima veze sa zdravljem",
                            "Ima veze sa sokom"
                    },
                    "Vitamin"
            )
    };

    public static QuestionSet getRandom() {
        return SETS[new Random().nextInt(SETS.length)];
    }

    public static List<QuestionSet> pickTwoRandom() {
        List<QuestionSet> all = new ArrayList<>();
        for (QuestionSet s : SETS) all.add(s);

        List<QuestionSet> picked = new ArrayList<>();
        Random rng = new Random();
        for (int i = 0; i < 2 && !all.isEmpty(); i++) {
            int idx = rng.nextInt(all.size());
            picked.add(all.remove(idx));
        }
        return picked;
    }
}
