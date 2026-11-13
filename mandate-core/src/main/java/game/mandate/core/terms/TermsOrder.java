package game.mandate.core.terms;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** One thing the president does about the terms of office. Carried by {@code Command.Terms}. */
public sealed interface TermsOrder {
    /** Answers one of the questions on the desk (whether to run, the second term's theme, the inaugural address). */
    record Answer(String question, int option) implements TermsOrder {}
    /** Endorses one of the rivals for the party's nomination, by index (once a race). */
    record Endorse(int rival) implements TermsOrder {}

    static Map<String, Object> toJson(TermsOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Answer x -> { m.put("question", x.question()); m.put("option", x.option()); }
            case Endorse x -> m.put("rival", x.rival());
        }
        return m;
    }

    static TermsOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Answer" -> new Answer((String) m.get("question"), Json.integer(m.get("option")));
            case "Endorse" -> new Endorse(Json.integer(m.get("rival")));
            default -> throw new IllegalArgumentException("Unknown terms order " + m.get("type"));
        };
    }
}
