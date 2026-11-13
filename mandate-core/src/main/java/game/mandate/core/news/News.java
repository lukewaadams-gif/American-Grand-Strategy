package game.mandate.core.news;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One story of the week, weighed as The Capital Ledger weighs it (Resolute Desk's {@code model.News}): the biggest
 * Washington story leads the paper, the rest fill its columns and the In Brief list. {@code section} is the paper's
 * section for a story from beyond the White House (the era's news), or null for Washington's own.
 */
public record News(LocalDate date, Kind kind, String head, String body, double weight, String section) {

    /** Resolute Desk's news kinds: an ordinary item, good or bad news for the president, the economy, the world. */
    public enum Kind { INFO, GOOD, BAD, ECON, WORLD }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString()); m.put("kind", kind.name()); m.put("head", head); m.put("body", body); m.put("weight", weight); m.put("section", section);
        return m;
    }

    public static News fromJson(Map<String, Object> m) {
        return new News(LocalDate.parse((String) m.get("date")), Kind.valueOf((String) m.get("kind")), (String) m.get("head"), (String) m.get("body"),
                Json.num(m.get("weight")), (String) m.get("section"));
    }
}
