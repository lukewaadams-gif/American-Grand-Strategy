package game.mandate.core.congress;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legislation and executive policy in force. Owned by {@link CongressEngine}. */
public final class PolicyState {
    /** One enacted measure: a law, or an executive order that survived the courts. */
    public record Enacted(String billId, LocalDate date, LegislativeStrategy route, double payoff) {}

    final List<Enacted> enacted = new ArrayList<>();
    int ordersBlocked;
    int billsFailed;

    PolicyState() {}

    public List<Enacted> enacted() { return List.copyOf(enacted); }
    public List<String> laws() { return enacted.stream().map(Enacted::billId).toList(); }
    public boolean inForce(String billId) { return enacted.stream().anyMatch(e -> e.billId().equals(billId)); }
    public int ordersBlocked() { return ordersBlocked; }
    public int billsFailed() { return billsFailed; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> l = new ArrayList<>();
        for (Enacted e : enacted) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("bill", e.billId()); x.put("date", e.date().toString()); x.put("route", e.route()); x.put("payoff", e.payoff());
            l.add(x);
        }
        m.put("enacted", l); m.put("ordersBlocked", ordersBlocked); m.put("billsFailed", billsFailed);
        return m;
    }

    public static PolicyState fromJson(Map<String, Object> m) {
        PolicyState s = new PolicyState();
        for (Object o : Json.array(m.get("enacted"))) {
            Map<String, Object> x = Json.object(o);
            s.enacted.add(new Enacted((String) x.get("bill"), LocalDate.parse((String) x.get("date")),
                    LegislativeStrategy.valueOf((String) x.get("route")), Json.num(x.get("payoff"))));
        }
        s.ordersBlocked = Json.integer(m.get("ordersBlocked")); s.billsFailed = Json.integer(m.get("billsFailed"));
        return s;
    }
}
