package game.mandate.core.engine;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One reading of the national indicators the player sees, taken at the end of every governing turn (and at the
 * inauguration), so screens can chart how the country changed. A record, not a model: nothing reads it back.
 *
 * <p>{@code detail} holds the economy's finer figures, in the order of {@link #DETAIL} (rounded to hundredths, to keep
 * saves small); a reading from a save made before the structural economy has none, and {@link #detail(String)} gives NaN.
 */
public record IndicatorPoint(LocalDate date, double approval, double unemployment, double inflation, double growth, double gas,
                             double tenYear, double fedRate, double deficit, double deterrence, double[] detail) {

    /** The economy's finer figures, in order. */
    public static final List<String> DETAIL = List.of("core", "shelter", "food", "energy", "medical", "goods", "services",
            "housing", "manufacturing", "ai", "farm", "energyProduction", "tourism", "sentiment", "stocks", "dollar", "oil", "mortgage",
            "payrolls", "gap", "potential", "revenue", "outlays", "interest", "debt");

    public IndicatorPoint(LocalDate date, double approval, double unemployment, double inflation, double growth, double gas,
                          double tenYear, double fedRate, double deficit, double deterrence) {
        this(date, approval, unemployment, inflation, growth, gas, tenYear, fedRate, deficit, deterrence, new double[0]);
    }

    /** One of the finer figures by its name in {@link #DETAIL}, or NaN if this reading has none. */
    public double detail(String key) {
        int i = DETAIL.indexOf(key);
        return i < 0 || i >= detail.length ? Double.NaN : detail[i];
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString()); m.put("approval", approval); m.put("unemployment", unemployment); m.put("inflation", inflation);
        m.put("growth", growth); m.put("gas", gas); m.put("tenYear", tenYear); m.put("fed", fedRate); m.put("deficit", deficit); m.put("deterrence", deterrence);
        if (detail.length > 0) {
            List<Object> d = new ArrayList<>(detail.length);
            for (double v : detail) d.add(v);
            m.put("detail", d);
        }
        return m;
    }

    static IndicatorPoint fromJson(Map<String, Object> m) {
        double[] detail = new double[0];
        if (m.containsKey("detail")) {
            List<Object> d = Json.array(m.get("detail"));
            detail = new double[d.size()];
            for (int i = 0; i < detail.length; i++) detail[i] = Json.num(d.get(i));
        }
        return new IndicatorPoint(LocalDate.parse((String) m.get("date")), Json.num(m.get("approval")), Json.num(m.get("unemployment")),
                Json.num(m.get("inflation")), Json.num(m.get("growth")), Json.num(m.get("gas")), Json.num(m.get("tenYear")), Json.num(m.get("fed")),
                Json.num(m.get("deficit")), Json.num(m.get("deterrence")), detail);
    }

    /** Rounded to hundredths. */
    static double r2(double v) { return Math.round(v * 100) / 100.0; }
}
