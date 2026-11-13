package game.mandate.core.policy;

import game.mandate.core.data.Csv;
import game.mandate.core.economy.Channel;
import game.mandate.core.economy.FiscalClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolute Desk's policy table (its {@code policy/PolicyDef}, exported to {@code data/rd/policies.tsv}) with this game's
 * scores (data/policy_scores.tsv): each policy's levels, the level in force in 2029, how it can be changed, what a step
 * costs, its evidence, its politics, and what a step does to the economy and to Resolute Desk's other outcomes.
 */
public final class PolicyCatalog {
    public static final String[] AREAS = {"Taxes and the budget", "The safety net", "Jobs and work", "Health care", "Housing and transportation",
            "Energy and the environment", "Immigration", "Crime and justice", "Education", "Rights and democracy", "Technology and business", "Defense and trade"};
    /** Resolute Desk's outcomes that are not the economy's, with their names and units. */
    public static final Map<String, String> OUTCOMES = new LinkedHashMap<>();
    static {
        OUTCOMES.put("unins", "Uninsured (points)"); OUTCOMES.put("pov", "Poverty (points)"); OUTCOMES.put("crime", "Violent crime (%)");
        OUTCOMES.put("od", "Overdose deaths (%)"); OUTCOMES.put("border", "Border crossings (%)"); OUTCOMES.put("emis", "Emissions (%)");
        OUTCOMES.put("scores", "Test scores (points)");
    }
    /** How much of the evidence's range each rating spans (Resolute Desk's spreads). */
    public static final double[] SPREAD = {0.25, 0.5, 0.8, 1.0};
    public static final String[] EVIDENCE = {"Strong", "Moderate", "Weak", "Contested"};

    /** How a policy can be changed: a law, an executive order, an agency rule, the budget. */
    public enum Route {
        BILL('B', 1.0), ORDER('O', 0.6), RULE('R', 0.85), BUDGET('$', 1.0);

        public final char code;
        /** Resolute Desk's strengths: how much of the public's reaction each way of acting draws. */
        public final double strength;

        Route(char code, double strength) { this.code = code; this.strength = strength; }
    }

    /** What one step up does to the economy (a step down does the opposite). */
    public record Score(FiscalClass fiscalClass, double[] phase, Integer lag, double[] channels, String basis) {}

    /** One policy. {@code cost} holds each step's cost from the lowest level up, $ billions a year. */
    public record Def(String id, int area, String name, List<String> levels, int level2029, String routes, double legal, double[] cost,
                      double support, int lean, boolean signature, Map<String, Double> outcomes, int lag, int evidence, String winners,
                      String losers, String precedent, Score score) {
        public int top() { return levels.size() - 1; }
        public boolean can(Route r) { return routes.indexOf(r.code) >= 0; }
        /** Public support for a move this way, %. */
        public double supportFor(int steps) { return steps > 0 ? support : 100 - support; }
        /** The cost of the step from {@code from} to {@code from + 1}, $ billions a year. */
        public double stepCost(int from) { return cost[Math.max(0, Math.min(cost.length - 1, from))]; }
        public String areaName() { return AREAS[area]; }
        public String evidenceName() { return EVIDENCE[evidence]; }
        public double spread() { return SPREAD[evidence]; }
        /** The party that wants it higher: +1 Democrats, -1 Republicans, 0 neither. */
        public int leanSign() { return lean; }
    }

    private static final PolicyCatalog SHARED = new PolicyCatalog();

    private final Map<String, Def> defs = new LinkedHashMap<>();

    /** The table, read once. */
    public static PolicyCatalog shared() { return SHARED; }

    public PolicyCatalog() {
        Map<String, String[]> scores = new LinkedHashMap<>();
        for (String[] r : Csv.readTsv("/data/policy_scores.tsv")) scores.put(r[0], r);
        for (String[] row : Csv.readTsv("/data/rd/policies.tsv")) {
            String[] t = String.join("\t", row).split("\\|", -1);
            if (t.length < 21) continue;
            Def d = def(t, scores.remove(t[0]));
            defs.put(d.id(), d);
        }
        if (!scores.isEmpty()) throw new IllegalStateException("Scores for policies that do not exist: " + scores.keySet());
    }

    private static Def def(String[] t, String[] s) {
        List<String> levels = List.of(t[3].split(";"));
        int level = 0;
        for (String kv : t[4].split(" ")) { String[] p = kv.split(":"); if (Integer.parseInt(p[0]) <= 2029) level = Integer.parseInt(p[1]); }
        double rdCost = Double.parseDouble(t[8]);
        Map<String, Double> outcomes = new LinkedHashMap<>();
        if (!t[14].equals("-")) for (String kv : t[14].split(" ")) {
            String k = kv.split("=")[0];
            if (OUTCOMES.containsKey(k)) outcomes.put(k, Double.parseDouble(kv.split("=")[1]));
        }
        double[] cost = new double[levels.size() - 1];
        java.util.Arrays.fill(cost, rdCost);
        Score score = null;
        if (s != null) {
            if (!s[2].equals("-")) {
                String[] c = s[2].trim().split(" ");
                if (c.length == 1) java.util.Arrays.fill(cost, Double.parseDouble(c[0]));
                else if (c.length == cost.length) for (int i = 0; i < c.length; i++) cost[i] = Double.parseDouble(c[i]);
                else throw new IllegalStateException(t[0] + ": " + c.length + " costs for " + cost.length + " steps");
            }
            score = score(t[0], s);
        } else if (rdCost != 0) {
            score = new Score(FiscalClass.PURCHASES, new double[]{1, 1, 1, 1, 1}, null, new double[Channel.COUNT],
                    "Resolute Desk's cost; no economic channels beyond the money");
        }
        int lean = t[12].equals("D") ? 1 : t[12].equals("R") ? -1 : 0;
        return new Def(t[0], Integer.parseInt(t[1]), t[2], levels, level, t[5], Double.parseDouble(t[7]), cost, Double.parseDouble(t[11]), lean,
                t[13].equals("1"), Collections.unmodifiableMap(outcomes), Integer.parseInt(t[16]), "SMWC".indexOf(t[17].charAt(0)), t[18], t[19], t[20], score);
    }

    /** Columns: id class cost phase lag immigration participation nairu tfp_level tfp_growth capital prices oil gas sectors basis. */
    private static Score score(String id, String[] s) {
        if (s.length < 16) throw new IllegalStateException(id + ": a score needs 16 columns, has " + s.length);
        FiscalClass cls = switch (s[1]) {
            case "purchases" -> FiscalClass.PURCHASES; case "infrastructure" -> FiscalClass.INFRASTRUCTURE; case "grants" -> FiscalClass.STATE_GRANTS;
            case "transfers" -> FiscalClass.TRANSFERS; case "retirees" -> FiscalClass.RETIREES; case "tax_middle" -> FiscalClass.TAX_MIDDLE;
            case "tax_high" -> FiscalClass.TAX_HIGH; case "corporate" -> FiscalClass.CORPORATE; case "none" -> FiscalClass.NONE;
            default -> throw new IllegalStateException(id + ": no fiscal class " + s[1]);
        };
        String[] ph = s[3].trim().split(" ");
        double[] phase = new double[ph.length];
        for (int i = 0; i < ph.length; i++) phase[i] = Double.parseDouble(ph[i]);
        Integer lag = s[4].equals("-") ? null : Integer.parseInt(s[4]);
        double[] ch = new double[Channel.COUNT];
        String[] cols = {"immigration", "participation", "nairu", "tfp_level", "tfp_growth", "capital"};
        for (int i = 0; i < cols.length; i++) if (!s[5 + i].equals("-")) ch[Channel.of(cols[i], false).ordinal()] = Double.parseDouble(s[5 + i]);
        if (!s[11].equals("-")) for (String kv : s[11].trim().split(" ")) ch[Channel.of(kv.split("=")[0], false).ordinal()] = Double.parseDouble(kv.split("=")[1]);
        if (!s[12].equals("-")) ch[Channel.OIL.ordinal()] = Double.parseDouble(s[12]);
        if (!s[13].equals("-")) ch[Channel.GAS.ordinal()] = Double.parseDouble(s[13]);
        if (!s[14].equals("-")) for (String kv : s[14].trim().split(" ")) ch[Channel.of(kv.split("=")[0], true).ordinal()] = Double.parseDouble(kv.split("=")[1]);
        return new Score(cls, phase, lag, ch, s[15]);
    }

    public List<Def> all() { return new ArrayList<>(defs.values()); }

    public Def get(String id) {
        Def d = defs.get(id);
        if (d == null) throw new IllegalArgumentException("No policy " + id);
        return d;
    }

    public boolean has(String id) { return defs.containsKey(id); }
}
