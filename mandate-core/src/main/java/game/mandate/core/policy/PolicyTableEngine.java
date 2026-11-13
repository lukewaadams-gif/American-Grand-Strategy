package game.mandate.core.policy;

import game.mandate.core.economy.Channel;
import game.mandate.core.economy.FiscalClass;
import game.mandate.core.effects.Effect;
import game.mandate.core.policy.PolicyCatalog.Def;
import game.mandate.core.policy.PolicyCatalog.Route;
import game.mandate.core.policy.PolicyTableState.Move;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves policies on the table (docs/ECONOMY_MODEL.md, "Laws, orders, rules and budgets"): a bill moves a policy up to
 * two steps, an order, a rule or the budget one. Each step draws its luck when it becomes law; its effect on the economy
 * goes out as a {@link Effect.PolicyStep}, and the public's reaction as approval (Resolute Desk's rule). The economy is
 * the economy's: this engine only says what each step is.
 */
public final class PolicyTableEngine {
    private final PolicyCatalog catalog;
    /** Nominal GDP in 2029, $ billions: what a policy's cost is a share of. */
    private final double nominalGdp;

    public PolicyTableEngine(PolicyCatalog catalog, double nominalGdp) {
        this.catalog = catalog;
        this.nominalGdp = nominalGdp;
    }

    public PolicyCatalog catalog() { return catalog; }

    /** The table on inauguration day: every policy at its 2029 level. */
    public PolicyTableState newTable() {
        PolicyTableState s = new PolicyTableState();
        for (Def d : catalog.all()) s.levels.put(d.id(), d.level2029());
        return s;
    }

    /** What a move did: the effects for the router, and the steps it made. */
    public record Moved(List<Effect> effects, List<Move> moves) {}

    /** How far a route can move a policy at once. */
    public static int reach(Route r) { return r == Route.BILL ? 2 : 1; }

    /**
     * Moves a policy {@code steps} levels (up if positive), as far as the route allows and the levels go. Returns
     * nothing if the policy cannot move that way.
     */
    public Moved move(PolicyTableState s, String id, int steps, Route route, String law, LocalDate date, DeterministicRandomManager rng) {
        Def d = catalog.get(id);
        if (!d.can(route)) throw new IllegalArgumentException(d.name() + " cannot be changed by " + route.name().toLowerCase());
        return moveAnyway(s, d, steps, route, law, date, rng);
    }

    /** A move without the route check: a law Congress passed can change what the president's own channels cannot. */
    private Moved moveAnyway(PolicyTableState s, Def d, int steps, Route route, String law, LocalDate date, DeterministicRandomManager rng) {
        String id = d.id();
        int level = s.level(id), reach = reach(route);
        int target = Math.max(0, Math.min(d.top(), level + Math.max(-reach, Math.min(reach, steps))));
        int n = target - level, sign = Integer.signum(n);
        List<Effect> effects = new ArrayList<>();
        List<Move> moves = new ArrayList<>();
        for (int k = 0; k < Math.abs(n); k++) {
            int from = level + k * sign;
            double draw = Math.max(-1, Math.min(1, 0.5 * rng.gaussian("policy.draw")));
            String effectId = id + "@" + date + "#" + s.moves.size();
            Effect.PolicyStep step = step(d, from, sign, draw, effectId, law);
            if (step != null) effects.add(step);
            Move m = new Move(id, from, from + sign, date, route, law, draw, effectId);
            s.moves.add(m);
            moves.add(m);
        }
        if (n != 0) {
            s.levels.put(id, target);
            effects.add(new Effect.Approval(reaction(d, n, route)));
            // the groups it touches (Resolute Desk's Policies.groupEffect)
            for (Map.Entry<String, Double> g : d.groups().entrySet())
                effects.add(new Effect.GroupStanding(g.getKey(), g.getValue() * n * route.strength * (d.signature() ? 1.5 : 1)));
        }
        return new Moved(effects, moves);
    }

    /**
     * A bill becomes law: each of its policy steps, its money and other channels scaled, a pay-for offsetting part of its
     * cost, and the public's reaction summed. Returns the effects and the reaction (approval points, before scaling to
     * a lasting share).
     */
    public record Passed(List<Effect> effects, List<Move> moves, double reaction, double cost) {}

    public Passed enactBill(PolicyTableState s, Effect.LawPassed law, LocalDate date, DeterministicRandomManager rng) {
        List<Effect.PolicyStep> steps = new ArrayList<>();
        List<Effect> groups = new ArrayList<>();
        List<Move> moves = new ArrayList<>();
        double reaction = 0;
        for (Map.Entry<String, Integer> e : stepsOf(law.billId()).entrySet()) {
            Moved m = moveAnyway(s, catalog.get(e.getKey()), e.getValue(), Route.BILL, law.law(), date, rng);
            moves.addAll(m.moves());
            for (Effect x : m.effects()) {
                if (x instanceof Effect.Approval a) reaction += a.points();
                else if (x instanceof Effect.PolicyStep p) steps.add(scaled(p, law));
                else if (x instanceof Effect.GroupStanding g) groups.add(new Effect.GroupStanding(g.group(), g.points() * law.moneyScale()));
            }
        }
        double cost = payFor(steps, law, law.billId() + "@" + date + "-payfor");
        List<Effect> out = new ArrayList<Effect>(steps);
        out.addAll(groups);
        return new Passed(out, moves, Math.max(-5, Math.min(5, reaction * law.moneyScale())), cost);
    }

    /** What a bill would do at a given luck for every step (-1, 0, +1), from where the table stands, without moving it. */
    public List<Effect.PolicyStep> billSteps(PolicyTableState s, Effect.LawPassed law, double draw) {
        List<Effect.PolicyStep> steps = new ArrayList<>();
        int k = 0;
        for (Map.Entry<String, Integer> e : stepsOf(law.billId()).entrySet()) {
            Def d = catalog.get(e.getKey());
            int level = s.level(d.id()), target = Math.max(0, Math.min(d.top(), level + e.getValue())), sign = Integer.signum(target - level);
            for (int i = 0; i < Math.abs(target - level); i++) {
                Effect.PolicyStep st = step(d, level + i * sign, sign, draw, law.billId() + "#score" + k++, law.law());
                if (st != null) steps.add(scaled(st, law));
            }
        }
        payFor(steps, law, law.billId() + "#score-payfor");
        return steps;
    }

    /** The steps a law makes: one of the game's bills (data/bill_policies.tsv), or a policy-step bill ({@code pol:<policy>:<from>:<to>}). */
    public Map<String, Integer> stepsOf(String billId) {
        if (billId.startsWith("pol:")) {
            String[] t = billId.split(":");
            return Map.of(t[1], Integer.parseInt(t[3]) - Integer.parseInt(t[2]));
        }
        return catalog.bill(billId);
    }

    private static Effect.PolicyStep scaled(Effect.PolicyStep p, Effect.LawPassed law) {
        double[] ch = p.channels().clone();
        for (int i = 0; i < ch.length; i++) ch[i] *= law.channelScale();
        return new Effect.PolicyStep(p.id(), p.label(), p.fiscalClass(), p.pctGdp() * law.moneyScale(), p.phase(), p.lagWeeks(), p.draw(), ch);
    }

    /**
     * The pay-for: taxes on the wealthy and corporations, or cuts elsewhere, cover part of what the bill adds, phased in
     * with the money they pay for (one offset for each step that costs money). Adds the offsets to {@code steps} and
     * returns what the bill still adds to the deficit at full effect, % of GDP.
     */
    private static double payFor(List<Effect.PolicyStep> steps, Effect.LawPassed law, String id) {
        double cost = 0, spending = 0;
        for (Effect.PolicyStep p : steps) { cost += p.pctGdp(); spending += Math.max(0, p.pctGdp()); }
        double share = switch (law.payFor()) { case "REVENUE" -> 0.8; case "CUTS" -> 0.7; default -> 0; };
        if (cost <= 0 || share <= 0) return cost;
        boolean revenue = law.payFor().equals("REVENUE");
        double f = share * cost / spending;
        List<Effect.PolicyStep> offsets = new ArrayList<>();
        int k = 0;
        for (Effect.PolicyStep p : steps) {
            if (p.pctGdp() <= 0) continue;
            double offset = f * p.pctGdp();
            double[] ch = new double[Channel.COUNT];
            if (revenue) ch[Channel.USER_COST.ordinal()] = 1.5 * offset / 0.3;   // about 1.5% on the cost of capital per 0.3% of GDP raised
            offsets.add(new Effect.PolicyStep(id + (k++ == 0 ? "" : "-" + k), law.law() + ": " + (revenue ? "taxes on the wealthy and corporations" : "cuts elsewhere"),
                    revenue ? FiscalClass.TAX_HIGH : FiscalClass.PURCHASES, -offset, p.phase(), Math.min(13, p.lagWeeks()), 1, ch));
        }
        steps.addAll(offsets);
        return cost * (1 - share);
    }

    /** Resolute Desk's public reaction to a move: support against an even split, more for a signature policy, capped at 5 points. */
    public static double reaction(Def d, int steps, Route route) {
        double v = (d.supportFor(steps) - 50) * (d.signature() ? 0.2 : 0.06) * Math.abs(steps) * route.strength;
        return Math.max(-5, Math.min(5, v));
    }

    /**
     * One step's effect on the economy, from level {@code from} in direction {@code sign}, at the given luck. Its cost
     * is the budget office's and is not drawn; every other channel is scaled by {@code 1 + spread x draw}.
     */
    public Effect.PolicyStep step(Def d, int from, int sign, double draw, String effectId, String law) {
        double cost = sign > 0 ? d.stepCost(from) : -d.stepCost(from - 1);
        PolicyCatalog.Score score = d.score();
        if (score == null && cost == 0) return null;
        double k = 1 + d.spread() * draw;
        double[] channels = new double[Channel.COUNT];
        if (score != null) for (int i = 0; i < Channel.COUNT; i++) channels[i] = score.channels()[i] * sign * k;
        FiscalClass cls = score == null ? FiscalClass.PURCHASES : score.fiscalClass();
        double[] phase = score == null ? new double[]{1} : score.phase();
        int lag = score != null && score.lag() != null ? score.lag() : d.lag();
        String label = d.name() + ": " + d.levels().get(from + sign) + (law == null ? "" : " (" + law + ")");
        return new Effect.PolicyStep(effectId, label, cls, cost / nominalGdp * 100, phase, lag, k, channels);
    }

    /** The steps a move would make at the two ends of its range and in the middle (luck -1, 0, +1): what the budget office scores. */
    public List<List<Effect.PolicyStep>> scoring(PolicyTableState s, String id, int steps) {
        Def d = catalog.get(id);
        int level = s.level(id), target = Math.max(0, Math.min(d.top(), level + steps)), n = target - level, sign = Integer.signum(n);
        List<List<Effect.PolicyStep>> out = new ArrayList<>();
        for (double draw : new double[]{-1, 0, 1}) {
            List<Effect.PolicyStep> l = new ArrayList<>();
            for (int k = 0; k < Math.abs(n); k++) {
                Effect.PolicyStep st = step(d, level + k * sign, sign, draw, id + "#score" + k, null);
                if (st != null) l.add(st);
            }
            out.add(l);
        }
        return out;
    }

    /** What a move would do to Resolute Desk's other outcomes at full effect (central estimate). */
    public Map<String, Double> outcomesOf(String id, int steps) {
        Def d = catalog.get(id);
        Map<String, Double> m = new LinkedHashMap<>();
        d.outcomes().forEach((k, v) -> m.put(k, v * steps));
        return m;
    }

    /** What the moves so far have done to Resolute Desk's other outcomes by a date, each phased in after its lag. */
    public Map<String, Double> outcomes(PolicyTableState s, LocalDate date) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String k : PolicyCatalog.OUTCOMES.keySet()) m.put(k, 0.0);
        for (Move mv : s.moves) {
            Def d = catalog.get(mv.policy());
            if (d.outcomes().isEmpty()) continue;
            int lag = d.score() != null && d.score().lag() != null ? d.score().lag() : d.lag();
            double years = (ChronoUnit.DAYS.between(mv.date(), date) - 7.0 * lag) / 365.25;
            double share = share(d.score() == null ? new double[]{1} : d.score().phase(), years);
            double k = (1 + d.spread() * mv.draw()) * Integer.signum(mv.to() - mv.from()) * share;
            d.outcomes().forEach((key, v) -> m.merge(key, v * k, Double::sum));
        }
        return m;
    }

    /** The share of a full effect {@code years} after it starts (as the economy phases laws in). */
    static double share(double[] phase, double years) {
        if (years <= 0) return 0;
        if (years < 1) return phase[0] * years;
        int y = (int) Math.floor(years);
        double a = phase[Math.min(y - 1, phase.length - 1)], b = phase[Math.min(y, phase.length - 1)];
        return a + (b - a) * (years - y);
    }
}
