package game.mandate.core.economy;

import game.mandate.core.data.Csv;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The economy state by state (Resolute Desk's {@code economy/StateEconomy}): each state's unemployment is the nation's,
 * scaled by its usual difference and moved by the industries it leans on (manufacturing, farming, energy, tech,
 * tourism); its gas price is the nation's plus its own difference. Computed from the national economy, so nothing is
 * stored. Both feed approval in the states.
 */
public final class StateEconomy {
    /** One state's usual differences and industries. */
    record Row(double unemployment, double gas, Set<String> industries) {}

    private static final StateEconomy SHARED = new StateEconomy();

    private final Map<String, Row> rows = new TreeMap<>();

    /** The table, read once. */
    public static StateEconomy shared() { return SHARED; }

    public StateEconomy() {
        for (String[] r : Csv.readTsv("/data/rd/state_economy.tsv")) {
            Set<String> ind = r.length > 3 && !r[3].isBlank() ? Set.of(r[3].trim().split(" ")) : Set.of();
            rows.put(r[0], new Row(Double.parseDouble(r[1]), Double.parseDouble(r[2]), ind));
        }
    }

    public Set<String> states() { return Collections.unmodifiableSet(rows.keySet()); }

    /** The industries a state leans on: manufacturing, farming, energy, tech, tourism. */
    public Set<String> industries(String state) {
        Row r = rows.get(state);
        return r == null ? Set.of() : r.industries();
    }

    /** Unemployment in a state, %. */
    public double unemployment(EconomyState e, String state) {
        Row r = rows.get(state);
        if (r == null) return e.unemployment;
        double u = e.unemployment + r.unemployment() * (e.unemployment / 4.0);
        double mfg = (e.manufacturing - 50) / 10, farm = (e.farm - 100) / 100, energy = (e.energyIndex - 100) / 100 + (e.oil - e.oilBase) / 200,
                tech = (e.aiInvest - 100) / 400, tour = (e.tourism - 100) / 100;
        if (r.industries().contains("manufacturing")) u -= 0.8 * mfg;
        if (r.industries().contains("farming")) u -= 0.9 * farm;
        if (r.industries().contains("energy")) u -= 0.8 * energy;
        if (r.industries().contains("tech")) u -= 0.5 * tech;
        if (r.industries().contains("tourism")) u -= 1.0 * tour;
        return Math.max(1.5, Math.min(20, u));
    }

    /** Gas in a state, $ a gallon. */
    public double gas(EconomyState e, String state) {
        Row r = rows.get(state);
        return r == null ? e.gas : e.gas + r.gas() * e.priceLevel;
    }

    /**
     * What a state's own economy adds to the president's standing there, in logit points (Resolute Desk's
     * {@code Approval.cells}): worse jobs and dearer gas than the nation's cost a little.
     */
    public Map<String, Double> approvalTerms(EconomyState e) {
        Map<String, Double> m = new TreeMap<>();
        for (String st : rows.keySet())
            m.put(st, -0.07 * (unemployment(e, st) - e.unemployment) - 0.10 * (gas(e, st) - e.gas) / e.priceLevel);
        return m;
    }
}
