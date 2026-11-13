package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The federal budget and debt, in percent of GDP, kept as CBO keeps them: revenue, mandatory and discretionary spending,
 * and interest, with the programs that laws have added on top. Owned by {@link EconomyEngine}.
 */
public final class BudgetState {
    int week;
    double revenue, mandatory, discretionary, interest, averageRate, debt, deficit, programsNow;
    final List<Program> programs = new ArrayList<>();
    transient boolean legacy;
    /** A save from before the structural model carried a single policy deficit. */
    transient double legacyPolicyDeficit;

    BudgetState() {}

    public double revenue() { return revenue; }
    public double mandatory() { return mandatory; }
    public double discretionary() { return discretionary; }
    public double interest() { return interest; }
    /** Total outlays: mandatory, discretionary, interest and the programs laws added. */
    public double outlays() { return mandatory + discretionary + interest + Math.max(0, programsNow); }
    /** The average interest rate the government pays on its debt, %. */
    public double averageRate() { return averageRate; }
    public double debt() { return debt; }
    public double deficit() { return deficit; }
    /** The deficit without the programs laws added. */
    public double baselineDeficit() { return deficit - programsNow; }
    /** What the programs in force add to the deficit at full effect, % of GDP. */
    public double policyDeficit() {
        double d = 0;
        for (Program p : programs) if (p.endWeek < 0 || week < p.endWeek) d += p.fullPctGdp;
        return d;
    }
    /** What the programs add to this year's deficit, as they phase in. */
    public double programsNow() { return programsNow; }
    public List<Program> programs() { return Collections.unmodifiableList(programs); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 2); m.put("week", week);
        m.put("values", EconomyState.nums(revenue, mandatory, discretionary, interest, averageRate, debt, deficit, programsNow));
        List<Object> ps = new ArrayList<>();
        for (Program p : programs) ps.add(p.toJson());
        m.put("programs", ps);
        return m;
    }

    public static BudgetState fromJson(Map<String, Object> m) {
        BudgetState b = new BudgetState();
        if (!m.containsKey("version")) {
            b.legacy = true;
            b.debt = Json.num(m.get("debt"));
            b.legacyPolicyDeficit = Json.num(m.get("policyDeficit"));
            return b;
        }
        b.week = Json.integer(m.get("week"));
        double[] v = EconomyState.read(m.get("values"));
        b.revenue = v[0]; b.mandatory = v[1]; b.discretionary = v[2]; b.interest = v[3]; b.averageRate = v[4]; b.debt = v[5]; b.deficit = v[6];
        b.programsNow = v[7];
        for (Object o : Json.array(m.get("programs"))) b.programs.add(Program.fromJson(Json.object(o)));
        return b;
    }
}
