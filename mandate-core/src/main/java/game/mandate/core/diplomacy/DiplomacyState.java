package game.mandate.core.diplomacy;

import game.mandate.core.data.Json;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** International relationships, flashpoint tensions, deterrence and the Taiwan ladder. Owned by {@link DiplomacyEngine}. */
public final class DiplomacyState {
    final Map<HotSpot, Double> tension = new EnumMap<>(HotSpot.class);
    final Map<String, Double> relations = new LinkedHashMap<>();
    double deterrence;
    boolean japanBasing;
    Escalation rung;
    int monthsSinceBlockade = -1;
    boolean warOver;

    DiplomacyState() {}

    public double tension(HotSpot h) { return tension.get(h); }
    public double relation(String countryId) { return relations.get(countryId); }
    public Map<String, Double> relations() { return java.util.Collections.unmodifiableMap(relations); }
    public double deterrence() { return deterrence; }
    public boolean japanBasing() { return japanBasing; }
    public Escalation rung() { return rung; }
    public int warningMonths() { return Math.max(0, monthsSinceBlockade); }
    public boolean warOver() { return warOver; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> t = new LinkedHashMap<>();
        tension.forEach((k, v) -> t.put(k.name(), v));
        m.put("tension", t);
        m.put("relations", new LinkedHashMap<>(relations));
        m.put("deterrence", deterrence); m.put("japanBasing", japanBasing); m.put("rung", rung == null ? null : rung.name());
        m.put("monthsSinceBlockade", monthsSinceBlockade); m.put("warOver", warOver);
        return m;
    }

    public static DiplomacyState fromJson(Map<String, Object> m) {
        DiplomacyState s = new DiplomacyState();
        Json.object(m.get("tension")).forEach((k, v) -> s.tension.put(HotSpot.valueOf(k), Json.num(v)));
        Json.object(m.get("relations")).forEach((k, v) -> s.relations.put(k, Json.num(v)));
        s.deterrence = Json.num(m.get("deterrence")); s.japanBasing = (Boolean) m.get("japanBasing");
        s.rung = m.get("rung") == null ? null : Escalation.valueOf((String) m.get("rung"));
        s.monthsSinceBlockade = Json.integer(m.get("monthsSinceBlockade")); s.warOver = (Boolean) m.get("warOver");
        return s;
    }
}
