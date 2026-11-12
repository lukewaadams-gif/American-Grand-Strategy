package game.mandate.core.engine;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One reading of the national indicators the player sees, taken at the end of every governing turn (and at the
 * inauguration), so screens can chart how the country changed. A record, not a model: nothing reads it back.
 */
public record IndicatorPoint(LocalDate date, double approval, double unemployment, double inflation, double growth, double gas,
                             double tenYear, double fedRate, double deficit, double deterrence) {

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString()); m.put("approval", approval); m.put("unemployment", unemployment); m.put("inflation", inflation);
        m.put("growth", growth); m.put("gas", gas); m.put("tenYear", tenYear); m.put("fed", fedRate); m.put("deficit", deficit); m.put("deterrence", deterrence);
        return m;
    }

    static IndicatorPoint fromJson(Map<String, Object> m) {
        return new IndicatorPoint(LocalDate.parse((String) m.get("date")), Json.num(m.get("approval")), Json.num(m.get("unemployment")),
                Json.num(m.get("inflation")), Json.num(m.get("growth")), Json.num(m.get("gas")), Json.num(m.get("tenYear")), Json.num(m.get("fed")),
                Json.num(m.get("deficit")), Json.num(m.get("deterrence")));
    }
}
