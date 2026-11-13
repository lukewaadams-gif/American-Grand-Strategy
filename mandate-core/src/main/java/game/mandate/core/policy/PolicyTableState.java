package game.mandate.core.policy;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where each policy stands, and every move since the inauguration: which way, by what route, under what law, and the
 * luck each step drew (never shown; the player learns it by watching what the law does). Owned by {@link PolicyTableEngine}.
 */
public final class PolicyTableState {
    /** One step of one policy: its route, the law that made it, and its draw (-1 to +1, near 0). */
    public record Move(String policy, int from, int to, LocalDate date, PolicyCatalog.Route route, String law, double draw, String effectId) {
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("policy", policy); m.put("from", from); m.put("to", to); m.put("date", date.toString()); m.put("route", route.name());
            m.put("law", law); m.put("draw", draw); m.put("effect", effectId);
            return m;
        }

        static Move fromJson(Map<String, Object> m) {
            return new Move((String) m.get("policy"), Json.integer(m.get("from")), Json.integer(m.get("to")), LocalDate.parse((String) m.get("date")),
                    PolicyCatalog.Route.valueOf((String) m.get("route")), (String) m.get("law"), Json.num(m.get("draw")), (String) m.get("effect"));
        }
    }

    final Map<String, Integer> levels = new TreeMap<>();
    final List<Move> moves = new ArrayList<>();

    PolicyTableState() {}

    public int level(String policy) { return levels.getOrDefault(policy, 0); }
    public Map<String, Integer> levels() { return Collections.unmodifiableMap(levels); }
    public List<Move> moves() { return Collections.unmodifiableList(moves); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("levels", new LinkedHashMap<String, Object>(levels));
        List<Object> mv = new ArrayList<>();
        for (Move x : moves) mv.add(x.toJson());
        m.put("moves", mv);
        return m;
    }

    public static PolicyTableState fromJson(Map<String, Object> m) {
        PolicyTableState s = new PolicyTableState();
        Json.object(m.get("levels")).forEach((k, v) -> s.levels.put(k, Json.integer(v)));
        for (Object o : Json.array(m.get("moves"))) s.moves.add(Move.fromJson(Json.object(o)));
        return s;
    }
}
