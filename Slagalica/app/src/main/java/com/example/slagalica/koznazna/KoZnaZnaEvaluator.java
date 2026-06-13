package com.example.slagalica.koznazna;

public final class KoZnaZnaEvaluator {

    private KoZnaZnaEvaluator() {
    }

    public static EvaluationResult evaluate(
            QuizQuestion question,
            Integer ownerAnswerIndex,
            Long ownerAnswerTimeMs,
            Integer guestAnswerIndex,
            Long guestAnswerTimeMs
    ) {
        boolean ownerAnswered = ownerAnswerIndex != null;
        boolean guestAnswered = guestAnswerIndex != null;
        boolean ownerCorrect = ownerAnswered && ownerAnswerIndex == question.getCorrectAnswerIndex();
        boolean guestCorrect = guestAnswered && guestAnswerIndex == question.getCorrectAnswerIndex();

        int ownerDelta = 0;
        int guestDelta = 0;
        String resultMessage;

        if (!ownerAnswered && !guestAnswered) {
            resultMessage = "Niko nije odgovorio. Bodovi ostaju nepromenjeni.";
        } else if (ownerCorrect && guestCorrect) {
            long ownerTime = ownerAnswerTimeMs != null ? ownerAnswerTimeMs : Long.MAX_VALUE;
            long guestTime = guestAnswerTimeMs != null ? guestAnswerTimeMs : Long.MAX_VALUE;

            if (ownerTime <= guestTime) {
                ownerDelta = 10;
                resultMessage = "Oba igrača su odgovorila tačno. Igrač 1 je bio brži i dobija +10.";
            } else {
                guestDelta = 10;
                resultMessage = "Oba igrača su odgovorila tačno. Igrač 2 je bio brži i dobija +10.";
            }
        } else {
            if (ownerAnswered) {
                ownerDelta = ownerCorrect ? 10 : -5;
            }
            if (guestAnswered) {
                guestDelta = guestCorrect ? 10 : -5;
            }
            resultMessage = "Igrač 1: " + formatDelta(ownerDelta) + " | Igrač 2: " + formatDelta(guestDelta);
        }

        return new EvaluationResult(ownerDelta, guestDelta, resultMessage);
    }

    private static String formatDelta(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        return String.valueOf(delta);
    }

    public static final class EvaluationResult {
        private final int ownerDelta;
        private final int guestDelta;
        private final String resultMessage;

        public EvaluationResult(int ownerDelta, int guestDelta, String resultMessage) {
            this.ownerDelta = ownerDelta;
            this.guestDelta = guestDelta;
            this.resultMessage = resultMessage;
        }

        public int getOwnerDelta() {
            return ownerDelta;
        }

        public int getGuestDelta() {
            return guestDelta;
        }

        public String getResultMessage() {
            return resultMessage;
        }
    }
}
