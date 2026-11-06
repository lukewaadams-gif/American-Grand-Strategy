package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * National economic conditions. Owned by {@link EconomyEngine}: other systems read these values and may only
 * change them through transitions the engine accepts (setters are package-private on purpose).
 */
public final class EconomyState {
    double outputGap, growth, unemployment, inflation, fedRate, credibility;
    double oil, tenYear, confidence;
    double reportedUnemployment, reportedInflation;
    double sprRelief;
    boolean fedPressured;
    double startMisery;
    final List<Shock> shocks = new ArrayList<>();

    EconomyState() {}

    public double outputGap() { return outputGap; }
    public double growth() { return growth; }
    public double unemployment() { return unemployment; }
    public double inflation() { return inflation; }
    public double fedRate() { return fedRate; }
    public double credibility() { return credibility; }
    public double oil() { return oil; }
    public double tenYear() { return tenYear; }
    public double mortgageRate(EconomyParams p) { return tenYear + p.mortgageSpread(); }
    public double confidence() { return confidence; }
    public double reportedUnemployment() { return reportedUnemployment; }
    public double reportedInflation() { return reportedInflation; }
    public boolean fedPressured() { return fedPressured; }
    public double startMisery() { return startMisery; }
    /** The "misery index" voters feel; used by the epilogue. */
    public double misery() { return unemployment + inflation; }
    public int activeShocks() { return shocks.size(); }

    /** A demand / supply impulse spread over time. */
    static final class Shock {
        final double growthPerMonth, inflationPerMonth;
        double monthsLeft;

        Shock(double g, double i, double m) { growthPerMonth = g; inflationPerMonth = i; monthsLeft = m; }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outputGap", outputGap); m.put("growth", growth); m.put("unemployment", unemployment); m.put("inflation", inflation);
        m.put("fedRate", fedRate); m.put("credibility", credibility); m.put("oil", oil); m.put("tenYear", tenYear);
        m.put("confidence", confidence); m.put("reportedUnemployment", reportedUnemployment); m.put("reportedInflation", reportedInflation);
        m.put("sprRelief", sprRelief); m.put("fedPressured", fedPressured); m.put("startMisery", startMisery);
        List<Object> sh = new ArrayList<>();
        for (Shock s : shocks) sh.add(List.of(s.growthPerMonth, s.inflationPerMonth, s.monthsLeft));
        m.put("shocks", sh);
        return m;
    }

    public static EconomyState fromJson(Map<String, Object> m) {
        EconomyState s = new EconomyState();
        s.outputGap = Json.num(m.get("outputGap")); s.growth = Json.num(m.get("growth")); s.unemployment = Json.num(m.get("unemployment"));
        s.inflation = Json.num(m.get("inflation")); s.fedRate = Json.num(m.get("fedRate")); s.credibility = Json.num(m.get("credibility"));
        s.oil = Json.num(m.get("oil")); s.tenYear = Json.num(m.get("tenYear")); s.confidence = Json.num(m.get("confidence"));
        s.reportedUnemployment = Json.num(m.get("reportedUnemployment")); s.reportedInflation = Json.num(m.get("reportedInflation"));
        s.sprRelief = Json.num(m.get("sprRelief")); s.fedPressured = (Boolean) m.get("fedPressured"); s.startMisery = Json.num(m.get("startMisery"));
        for (Object o : Json.array(m.get("shocks"))) {
            List<Object> l = Json.array(o);
            s.shocks.add(new Shock(Json.num(l.get(0)), Json.num(l.get(1)), Json.num(l.get(2))));
        }
        return s;
    }
}
