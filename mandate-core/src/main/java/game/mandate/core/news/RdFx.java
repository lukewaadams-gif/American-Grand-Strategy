package game.mandate.core.news;

import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Resolute Desk's effects language ({@code Diplo.fx}, "perf=-2 sal:ECON=0.4 g22=-5") and its conditions language
 * ({@code Stories.holds}, "unemp>5,p=0.01"), as this game's effects. Keys for systems this game does not model yet (the
 * home front's protest, overdose and crime indices, the grid, cyber) are dropped; their milestones bring them.
 */
public final class RdFx {
    private RdFx() {}

    /** Resolute Desk's thirty voter groups, by index (opinion/Demographics.java), as the policy table's codes. */
    public static final String[] GROUPS = {"genz", "sen", "ym", "yw", "wom", "men", "evan", "cath", "wnc", "wcol", "blk", "blkm", "hisp", "hispm", "cuban",
            "prdom", "asian", "jew", "arab", "lds", "native", "sub", "rural", "urban", "union", "vet", "gun", "lgbt", "stud", "par"};

    /** Resolute Desk's fourteen issues, in its order (opinion/Issues.java). */
    public static final String[] ISSUES = {"ECON", "PRICES", "JOBS", "HEALTH", "IMMIG", "CRIME", "FOREIGN", "SECURITY", "TRADE", "ENERGY", "RIGHTS", "TECH", "EDU", "ETHICS"};

    /** One of Resolute Desk's issues by its index, as this game's issue. */
    public static Issue issue(int rd) { return AmbientNews.issue(ISSUES[Math.max(0, Math.min(ISSUES.length - 1, rd))]); }

    /** A salience change on Resolute Desk's scale (its base is 0.5), relative to the issue's usual weight here. */
    public static Effect salience(Issue i, double rd) { return new Effect.Salience(i, rd * i.baseSalience()); }

    public static List<Effect> effects(String fx) {
        List<Effect> out = new ArrayList<>();
        if (fx == null || fx.trim().isEmpty()) return out;
        for (String tok : fx.trim().split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            String k = tok.substring(0, eq);
            double v;
            try { v = Double.parseDouble(tok.substring(eq + 1)); } catch (NumberFormatException e) { continue; }
            if (k.startsWith("sal:")) { Issue i = AmbientNews.issue(k.substring(4)); if (i != null) out.add(salience(i, v)); continue; }
            if (k.startsWith("st:")) { out.add(new Effect.StateStanding(k.substring(3), v)); continue; }
            if (k.length() > 1 && k.charAt(0) == 'g' && Character.isDigit(k.charAt(1))) {
                int g = Integer.parseInt(k.substring(1));
                if (g >= 0 && g < GROUPS.length) out.add(new Effect.GroupStanding(GROUPS[g], v));
                continue;
            }
            switch (k) {
                case "perf" -> out.add(new Effect.Performance(v));
                case "rally" -> out.add(new Effect.Rally(v));
                case "scandal" -> out.add(new Effect.Scandal(v));
                case "econ", "jobs" -> out.add(new Effect.IssueStanding(Issue.ECONOMY, v));
                case "prices" -> out.add(new Effect.IssueStanding(Issue.COST_OF_LIVING, v));
                case "health" -> out.add(new Effect.IssueStanding(Issue.HEALTHCARE, v));
                case "imm" -> out.add(new Effect.IssueStanding(Issue.IMMIGRATION, v));
                case "crime" -> out.add(new Effect.IssueStanding(Issue.CRIME, v));
                case "energy" -> out.add(new Effect.IssueStanding(Issue.CLIMATE_ENERGY, v));
                case "tech" -> out.add(new Effect.IssueStanding(Issue.AI_TECH, v));
                case "edu" -> out.add(new Effect.IssueStanding(Issue.EDUCATION, v));
                case "ethics" -> out.add(new Effect.IssueStanding(Issue.DEMOCRACY, v));
                case "rights" -> out.add(new Effect.IssueStanding(Issue.ABORTION, v));
                case "sec", "for" -> out.add(new Effect.IssueStanding(Issue.CHINA, v));
                case "base" -> {
                    out.add(new Effect.WingMood(0, v));
                    out.add(new Effect.WingMood(1, 0.6 * v));
                    out.add(new Effect.BlocRelations(true, 0.5 * v));
                }
                case "opp" -> out.add(new Effect.BlocRelations(false, v));
                case "stress" -> out.add(new Effect.PresidentStrain(0, v));
                case "fin" -> out.add(new Effect.FinancialShock(v));
                case "def" -> out.add(new Effect.Deficit(v));
                case "infl" -> out.add(new Effect.EconomyShock(0, v, 6));
                case "demand" -> out.add(new Effect.EconomyShock(v, 0, 6));
                // a supply shock (negative v) costs output and raises prices
                case "supply" -> out.add(new Effect.EconomyShock(0.5 * v, -0.6 * v, 6));
                case "slump" -> out.add(new Effect.EconomyShock(v, 0, 9));
                // oil, dollars a barrel: about a tenth of a point of inflation and a hundredth of growth per $3
                case "oil" -> out.add(new Effect.EconomyShock(-0.004 * v, 0.03 * v, 6));
                default -> { }
            }
        }
        return out;
    }

    /** "unemp>7,month<4": every clause must hold; "p=0.02" is drawn only if every clause before it held. */
    public static boolean holds(String c, ToDoubleFunction<String> var, DeterministicRandomManager rng, String stream) {
        if (c == null || c.isEmpty()) return true;
        for (String t : c.split(",")) {
            if (t.startsWith("p=")) { if (!rng.chance(stream, Double.parseDouble(t.substring(2)))) return false; continue; }
            int i = t.indexOf('>'), j = t.indexOf('<');
            if (i > 0) { if (!(var.applyAsDouble(t.substring(0, i)) > Double.parseDouble(t.substring(i + 1)))) return false; }
            else if (j > 0) { if (!(var.applyAsDouble(t.substring(0, j)) < Double.parseDouble(t.substring(j + 1)))) return false; }
        }
        return true;
    }

    /** Any of the space-separated alternatives holds. */
    public static boolean any(String alternatives, ToDoubleFunction<String> var, DeterministicRandomManager rng, String stream) {
        if (alternatives == null || alternatives.trim().isEmpty()) return false;
        for (String alt : alternatives.trim().split("\\s+")) if (holds(alt, var, rng, stream)) return true;
        return false;
    }

    /** Resolute Desk's fuel: "unemp>5:0.5 app<42:0.3" adds each value whose condition holds (no draws). */
    public static double fuel(String spec, ToDoubleFunction<String> var) {
        double f = 0;
        if (spec == null) return 0;
        for (String tok : spec.trim().split("\\s+")) {
            int c = tok.lastIndexOf(':');
            if (c <= 0) continue;
            if (holds(tok.substring(0, c), var, null, null)) f += Double.parseDouble(tok.substring(c + 1));
        }
        return f;
    }
}
