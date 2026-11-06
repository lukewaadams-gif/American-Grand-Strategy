package game.mandate.core.congress;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Congressional composition and procedure, and the president's relationship with each bloc. Owned by {@link CongressEngine}. */
public final class CongressState {
    final Congress congress;
    final TreeSet<Integer> reconciliationYears = new TreeSet<>();
    final Map<String, Double> relationship = new LinkedHashMap<>();

    CongressState(Congress congress) {
        this.congress = congress;
        for (Party p : Party.values()) for (Bloc b : Bloc.defaultsFor(p)) relationship.put(b.name(), 0.0);
    }

    public Congress congress() { return congress; }
    public boolean reconciliationUsed(int year) { return reconciliationYears.contains(year); }
    public double relationship(String bloc) { return relationship.getOrDefault(bloc, 0.0); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("houseDem", congress.house(Party.DEMOCRAT));
        m.put("senateDem", congress.senate(Party.DEMOCRAT));
        m.put("filibuster", congress.filibuster());
        m.put("reconciliationYears", new ArrayList<>(reconciliationYears));
        m.put("relationship", new LinkedHashMap<>(relationship));
        return m;
    }

    public static CongressState fromJson(Map<String, Object> m) {
        Congress c = new Congress(Json.integer(m.get("houseDem")), Json.integer(m.get("senateDem")));
        c.setFilibuster((Boolean) m.get("filibuster"));
        CongressState s = new CongressState(c);
        for (Object o : Json.array(m.get("reconciliationYears"))) s.reconciliationYears.add(Json.integer(o));
        Json.object(m.get("relationship")).forEach((k, v) -> s.relationship.put(k, Json.num(v)));
        return s;
    }
}
