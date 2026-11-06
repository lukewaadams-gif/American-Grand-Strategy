package game.mandate.core.executive;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The president, the cabinet, the agenda and the president's attention. Owned by {@link ExecutiveEngine}. */
public final class GovernmentState {
    public enum SeatStatus { VACANT, CONFIRMED, ACTING }

    /** One cabinet seat. */
    public record Seat(String name, NomineeType type, SeatStatus status) {}

    String presidentId;
    Party presidentParty;
    LocalDate inaugurated;
    int term;
    int attention;
    final List<String> agenda = new ArrayList<>();
    final Map<Post, Seat> cabinet = new EnumMap<>(Post.class);

    GovernmentState() {
        for (Post p : Post.values()) cabinet.put(p, new Seat(null, null, SeatStatus.VACANT));
    }

    public String presidentId() { return presidentId; }
    public Party presidentParty() { return presidentParty; }
    public LocalDate inaugurated() { return inaugurated; }
    public int term() { return term; }
    public int attention() { return attention; }
    public List<String> agenda() { return List.copyOf(agenda); }
    public Seat seat(Post p) { return cabinet.get(p); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("presidentId", presidentId); m.put("presidentParty", presidentParty == null ? null : presidentParty.name());
        m.put("inaugurated", inaugurated == null ? null : inaugurated.toString()); m.put("term", term); m.put("attention", attention);
        m.put("agenda", new ArrayList<>(agenda));
        Map<String, Object> c = new LinkedHashMap<>();
        cabinet.forEach((post, s) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", s.name()); x.put("type", s.type() == null ? null : s.type().name()); x.put("status", s.status().name());
            c.put(post.name(), x);
        });
        m.put("cabinet", c);
        return m;
    }

    public static GovernmentState fromJson(Map<String, Object> m) {
        GovernmentState g = new GovernmentState();
        g.presidentId = (String) m.get("presidentId");
        g.presidentParty = m.get("presidentParty") == null ? null : Party.valueOf((String) m.get("presidentParty"));
        g.inaugurated = m.get("inaugurated") == null ? null : LocalDate.parse((String) m.get("inaugurated"));
        g.term = Json.integer(m.get("term")); g.attention = Json.integer(m.get("attention"));
        for (Object o : Json.array(m.get("agenda"))) g.agenda.add((String) o);
        Json.object(m.get("cabinet")).forEach((k, v) -> {
            Map<String, Object> x = Json.object(v);
            g.cabinet.put(Post.valueOf(k), new Seat((String) x.get("name"), x.get("type") == null ? null : NomineeType.valueOf((String) x.get("type")),
                    SeatStatus.valueOf((String) x.get("status"))));
        });
        return g;
    }
}
