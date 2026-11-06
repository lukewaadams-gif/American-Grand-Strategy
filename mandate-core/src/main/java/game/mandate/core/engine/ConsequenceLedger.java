package game.mandate.core.engine;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The historical record of decisions and their consequences. Every transition the game engine dispatches is
 * recorded against its cause (a player command, a decision, an election, a war, the passage of time) and the
 * engine that applied it, so any later state can be traced back to the choices that produced it.
 */
public final class ConsequenceLedger {
    /** A player decision (or a scheduled event that acts like one). */
    public record Decision(int id, LocalDate date, String description) {}

    /** One transition applied to the world. {@code decisionId} is 0 for system causes. */
    public record Consequence(int decisionId, LocalDate date, String cause, String owner, String effect) {}

    private final List<Decision> decisions = new ArrayList<>();
    private final List<Consequence> consequences = new ArrayList<>();
    private int nextId = 1;

    public int decide(LocalDate date, String description) {
        Decision d = new Decision(nextId++, date, description);
        decisions.add(d);
        return d.id();
    }

    public void record(int decisionId, LocalDate date, String cause, String owner, String effect) {
        consequences.add(new Consequence(decisionId, date, cause, owner, effect));
    }

    public List<Decision> decisions() { return List.copyOf(decisions); }
    public List<Consequence> consequences() { return List.copyOf(consequences); }

    public List<Consequence> consequencesOf(int decisionId) {
        return consequences.stream().filter(c -> c.decisionId() == decisionId).toList();
    }

    public List<Consequence> byCause(String causePrefix) {
        return consequences.stream().filter(c -> c.cause().startsWith(causePrefix)).toList();
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> d = new ArrayList<>(), c = new ArrayList<>();
        for (Decision x : decisions) d.add(List.of((long) x.id(), x.date().toString(), x.description()));
        for (Consequence x : consequences) c.add(List.of((long) x.decisionId(), x.date().toString(), x.cause(), x.owner(), x.effect()));
        m.put("decisions", d); m.put("consequences", c); m.put("nextId", nextId);
        return m;
    }

    static ConsequenceLedger fromJson(Map<String, Object> m) {
        ConsequenceLedger l = new ConsequenceLedger();
        for (Object o : Json.array(m.get("decisions"))) {
            List<Object> x = Json.array(o);
            l.decisions.add(new Decision(Json.integer(x.get(0)), LocalDate.parse((String) x.get(1)), (String) x.get(2)));
        }
        for (Object o : Json.array(m.get("consequences"))) {
            List<Object> x = Json.array(o);
            l.consequences.add(new Consequence(Json.integer(x.get(0)), LocalDate.parse((String) x.get(1)), (String) x.get(2), (String) x.get(3), (String) x.get(4)));
        }
        l.nextId = Json.integer(m.get("nextId"));
        return l;
    }
}
