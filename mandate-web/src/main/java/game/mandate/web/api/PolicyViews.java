package game.mandate.web.api;

import game.mandate.core.economy.Part;
import game.mandate.core.economy.Score;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.policy.PolicyCatalog;
import game.mandate.core.policy.PolicyTableState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Cabinet Room and the budget office (presidency milestone 4): the policy table by area, each policy's levels,
 * routes, evidence, politics and history, this game's bills as the steps they make, and the score of a step or a bill.
 */
final class PolicyViews {
    private PolicyViews() {}

    private static final String[] ROUTE_NAMES = {"a law", "an executive order", "an agency rule", "the budget"};
    private static final String[] SPREADS = {"25%", "50%", "80%", "100%"};
    private static final String[] PRICE_LABELS = {"Shelter", "Food", "Energy", "Medical care", "Goods", "Other services"};
    private static final String[] SECTOR_LABELS = {"Housing starts", "Manufacturing", "AI investment", "Farm income", "Energy production", "Tourism"};
    private static final String[] SECTOR_UNITS = {"%", " points", "%", " points", " points", " points"};

    static Map<String, Object> cabinet(GameEngine g) {
        Map<String, Object> m = EngineApi.ok();
        PolicyCatalog cat = g.policies();
        PolicyTableState t = g.policyTable();
        List<Object> areas = new ArrayList<>();
        for (int a = 0; a < PolicyCatalog.AREAS.length; a++) {
            List<Object> ps = new ArrayList<>();
            for (PolicyCatalog.Def d : cat.all()) if (d.area() == a) ps.add(policy(d, t));
            areas.add(Map.of("name", PolicyCatalog.AREAS[a], "policies", ps));
        }
        m.put("areas", areas);
        List<Object> bills = new ArrayList<>();
        for (String id : cat.bills()) {
            if (!g.bills().has(id)) continue;
            List<Object> steps = new ArrayList<>();
            cat.bill(id).forEach((p, n) -> {
                PolicyCatalog.Def d = cat.get(p);
                int from = t.level(p), to = Math.max(0, Math.min(d.top(), from + n));
                steps.add(Map.of("policy", p, "name", d.name(), "from", d.levels().get(from), "to", d.levels().get(to), "steps", to - from));
            });
            bills.add(Map.of("id", id, "name", g.bills().get(id).name(), "law", g.laws().contains(id), "steps", steps));
        }
        m.put("bills", bills);
        List<Object> outcomes = new ArrayList<>();
        g.policyOutcomes().forEach((k, v) -> { if (Math.abs(v) >= 0.01) outcomes.add(Map.of("label", PolicyCatalog.OUTCOMES.get(k), "value", r2(v))); });
        m.put("outcomes", outcomes);
        m.put("date", g.date().toString());
        return m;
    }

    private static Map<String, Object> policy(PolicyCatalog.Def d, PolicyTableState t) {
        Map<String, Object> p = new LinkedHashMap<>();
        int level = t.level(d.id());
        p.put("id", d.id()); p.put("name", d.name()); p.put("levels", d.levels()); p.put("level", level); p.put("start", d.level2029());
        List<Object> routes = new ArrayList<>();
        PolicyCatalog.Route[] rs = PolicyCatalog.Route.values();
        for (int i = 0; i < rs.length; i++) if (d.can(rs[i])) routes.add(ROUTE_NAMES[i]);
        p.put("routes", routes);
        p.put("evidence", d.evidenceName()); p.put("spread", SPREADS[d.evidence()]); p.put("lag", d.lag()); p.put("support", d.support());
        p.put("lean", d.leanSign() > 0 ? "Democrats" : d.leanSign() < 0 ? "Republicans" : null);
        p.put("signature", d.signature());
        p.put("winners", d.winners()); p.put("losers", d.losers()); p.put("precedent", d.precedent());
        p.put("basis", d.score() == null ? null : d.score().basis());
        p.put("scored", d.score() != null);
        p.put("costUp", level < d.top() ? d.stepCost(level) : null);
        p.put("costDown", level > 0 ? -d.stepCost(level - 1) : null);
        List<Object> moves = new ArrayList<>();
        for (PolicyTableState.Move mv : t.moves()) if (mv.policy().equals(d.id()))
            moves.add(Map.of("date", mv.date().toString(), "from", d.levels().get(mv.from()), "to", d.levels().get(mv.to()), "law", mv.law() == null ? mv.route().name().toLowerCase() : mv.law()));
        p.put("moves", moves);
        List<Object> outcomes = new ArrayList<>();
        d.outcomes().forEach((k, v) -> outcomes.add(Map.of("label", PolicyCatalog.OUTCOMES.get(k), "value", v)));
        p.put("outcomes", outcomes);
        return p;
    }

    /** The score of moving one policy {@code steps} levels from where it stands. */
    static Map<String, Object> policyScore(GameEngine g, String id, int steps) {
        PolicyCatalog.Def d = g.policies().get(id);
        int from = g.policyTable().level(id), to = Math.max(0, Math.min(d.top(), from + steps));
        Map<String, Object> m = score(g.scorePolicy(id, to - from), d.evidenceName());
        m.put("title", d.name() + ": " + d.levels().get(from) + " to " + d.levels().get(to));
        List<Object> outcomes = new ArrayList<>();
        g.policyOutcomesOf(id, to - from).forEach((k, v) -> { if (v != 0) outcomes.add(Map.of("label", PolicyCatalog.OUTCOMES.get(k), "value", r2(v))); });
        m.put("outcomes", outcomes);
        m.put("basis", d.score() == null ? List.of() : List.of(Map.of("policy", d.name(), "basis", d.score().basis(), "evidence", d.evidenceName())));
        return m;
    }

    /** The score of one of this game's bills: every step it makes, from where the table stands. */
    static Map<String, Object> billScore(GameEngine g, String billId) {
        PolicyCatalog cat = g.policies();
        // the weakest evidence among the bill's policies sets the plain reading's caution
        int worst = 0;
        for (String p : cat.bill(billId).keySet()) worst = Math.max(worst, cat.get(p).evidence());
        Map<String, Object> m = score(g.scoreBill(billId), PolicyCatalog.EVIDENCE[worst]);
        m.put("title", g.bills().get(billId).name());
        Map<String, Double> sum = new LinkedHashMap<>();
        List<Object> basis = new ArrayList<>();
        cat.bill(billId).forEach((p, n) -> {
            g.policyOutcomesOf(p, n).forEach((k, v) -> sum.merge(k, v, Double::sum));
            PolicyCatalog.Def d = cat.get(p);
            if (d.score() != null) basis.add(Map.of("policy", d.name(), "basis", d.score().basis(), "evidence", d.evidenceName()));
        });
        List<Object> outcomes = new ArrayList<>();
        sum.forEach((k, v) -> { if (v != 0) outcomes.add(Map.of("label", PolicyCatalog.OUTCOMES.get(k), "value", r2(v))); });
        m.put("outcomes", outcomes);
        m.put("basis", basis);
        return m;
    }

    /** A score: the plain reading, the measures at one, three and ten years with their range, the budget, prices and sectors. */
    private static Map<String, Object> score(Score.Range r, String evidence) {
        Map<String, Object> m = EngineApi.ok();
        m.put("reading", Score.reading(r));
        m.put("evidence", evidence);
        m.put("years", List.of(1, 3, 10));
        List<Object> rows = new ArrayList<>();
        rows.add(row("Output", "%", r, 0));
        rows.add(row("Growth", " points", r, 1));
        rows.add(row("Jobs", " million", r, 2));
        rows.add(row("Unemployment", " points", r, 3));
        rows.add(row("Inflation", " points", r, 4));
        m.put("rows", rows);
        Score c = r.central();
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("direct", Math.round(c.direct())); budget.put("directLow", Math.round(r.min(Score::direct))); budget.put("directHigh", Math.round(r.max(Score::direct)));
        budget.put("total", Math.round(c.cost())); budget.put("deficit", r2(c.deficit())); budget.put("debt", r2(c.debt())); budget.put("tenYear", r2(c.tenYear()));
        m.put("budget", budget);
        List<Object> prices = new ArrayList<>();
        for (int i = 0; i < Part.COUNT; i++) {
            final int k = i;
            if (Math.abs(c.prices()[i]) >= 0.1)
                prices.add(Map.of("label", PRICE_LABELS[i], "value", r2(c.prices()[i]), "low", r2(r.min(s -> s.prices()[k])), "high", r2(r.max(s -> s.prices()[k]))));
        }
        m.put("prices", prices);
        List<Object> sectors = new ArrayList<>();
        for (int i = 0; i < 6; i++) if (Math.abs(c.sectors()[i]) >= 0.1)
            sectors.add(Map.of("label", SECTOR_LABELS[i], "value", r2(c.sectors()[i]), "unit", SECTOR_UNITS[i]));
        m.put("sectors", sectors);
        return m;
    }

    private static Map<String, Object> row(String label, String unit, Score.Range r, int which) {
        List<Object> central = new ArrayList<>(), low = new ArrayList<>(), high = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            final int k = y;
            java.util.function.ToDoubleFunction<Score> f = s -> switch (which) {
                case 0 -> s.output()[k]; case 1 -> s.growth()[k]; case 2 -> s.jobs()[k]; case 3 -> s.unemployment()[k]; default -> s.inflation()[k];
            };
            central.add(r2(f.applyAsDouble(r.central()))); low.add(r2(r.min(f))); high.add(r2(r.max(f)));
        }
        return Map.of("label", label, "unit", unit, "central", central, "low", low, "high", high);
    }

    static double r2(double v) { return Math.round(v * 100) / 100.0; }
}
