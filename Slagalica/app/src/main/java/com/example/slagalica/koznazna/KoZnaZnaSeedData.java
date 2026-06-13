package com.example.slagalica.koznazna;

import java.util.ArrayList;
import java.util.List;

public final class KoZnaZnaSeedData {

    private KoZnaZnaSeedData() {
    }

    public static List<QuizQuestion> createQuestions() {
        List<QuizQuestion> questions = new ArrayList<>();

        questions.add(new QuizQuestion(
                "Koji je glavni grad Srbije?",
                new String[]{"Novi Sad", "Beograd", "Niš", "Kragujevac"},
                1
        ));

        questions.add(new QuizQuestion(
                "Koliko igrača ima jedan fudbalski tim na terenu?",
                new String[]{"9", "10", "11", "12"},
                2
        ));

        questions.add(new QuizQuestion(
                "Koja planeta je poznata kao crvena planeta?",
                new String[]{"Venera", "Mars", "Jupiter", "Saturn"},
                1
        ));

        questions.add(new QuizQuestion(
                "Ko je napisao delo 'Na Drini ćuprija'?",
                new String[]{"Ivo Andrić", "Branko Ćopić", "Meša Selimović", "Danilo Kiš"},
                0
        ));

        questions.add(new QuizQuestion(
                "Koliko strana ima trougao?",
                new String[]{"2", "3", "4", "5"},
                1
        ));

        questions.add(new QuizQuestion(
                "Koja reka protiče kroz Beograd?",
                new String[]{"Morava", "Tisa", "Sava", "Drina"},
                2
        ));

        questions.add(new QuizQuestion(
                "Koji hemijski element ima oznaku O?",
                new String[]{"Zlato", "Kiseonik", "Olovo", "Azot"},
                1
        ));

        return questions;
    }
}
