package game.mandate.core.military;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Military forces, inventories and the permanent record of losses. Owned by {@link MilitaryEngine}. Losses are
 * never restored: ships and aircraft lost stay lost for the rest of the game; munitions only come back through
 * production.
 */
public final class MilitaryState {
    final Map<String, Integer> forces = new LinkedHashMap<>();
    final Map<String, Double> munitions = new LinkedHashMap<>();
    final Map<String, Integer> forcesLost = new LinkedHashMap<>();
    final Map<String, Double> expended = new LinkedHashMap<>();
    double productionMultiplier = 1.0;
    int killed;
    final List<TheaterOutcome> wars = new ArrayList<>();
    OperationState operation;

    MilitaryState() {}

    public int force(String id) { return forces.getOrDefault(id, 0); }
    public double munitions(String id) { return munitions.getOrDefault(id, 0.0); }
    public int lost(String id) { return forcesLost.getOrDefault(id, 0); }
    public double expended(String id) { return expended.getOrDefault(id, 0.0); }
    public int killed() { return killed; }
    public double productionMultiplier() { return productionMultiplier; }
    public List<TheaterOutcome> wars() { return List.copyOf(wars); }
    public boolean operationActive() { return operation != null; }
    public OperationState operation() { return operation; }
    public Map<String, Integer> forces() { return java.util.Collections.unmodifiableMap(forces); }
    public Map<String, Double> munitions() { return java.util.Collections.unmodifiableMap(munitions); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("forces", new LinkedHashMap<>(forces)); m.put("munitions", new LinkedHashMap<>(munitions));
        m.put("forcesLost", new LinkedHashMap<>(forcesLost)); m.put("expended", new LinkedHashMap<>(expended));
        m.put("productionMultiplier", productionMultiplier); m.put("killed", killed);
        List<Object> w = new ArrayList<>();
        for (TheaterOutcome o : wars) w.add(outcomeJson(o));
        m.put("wars", w);
        m.put("operation", operation == null ? null : operation.toJson());
        return m;
    }

    public static MilitaryState fromJson(Map<String, Object> m) {
        MilitaryState s = new MilitaryState();
        Json.object(m.get("forces")).forEach((k, v) -> s.forces.put(k, Json.integer(v)));
        Json.object(m.get("munitions")).forEach((k, v) -> s.munitions.put(k, Json.num(v)));
        Json.object(m.get("forcesLost")).forEach((k, v) -> s.forcesLost.put(k, Json.integer(v)));
        Json.object(m.get("expended")).forEach((k, v) -> s.expended.put(k, Json.num(v)));
        s.productionMultiplier = Json.num(m.get("productionMultiplier")); s.killed = Json.integer(m.get("killed"));
        for (Object o : Json.array(m.get("wars"))) s.wars.add(outcomeFromJson(Json.object(o)));
        s.operation = m.get("operation") == null ? null : OperationState.fromJson(Json.object(m.get("operation")));
        return s;
    }

    public static Map<String, Object> outcomeJson(TheaterOutcome o) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("status", o.status()); x.put("weeks", o.durationWeeks()); x.put("killed", o.usKilled()); x.put("growth", o.growthShock());
        x.put("inflation", o.inflationShock()); x.put("approval", o.approvalShift()); x.put("deterrence", o.deterrenceAfter()); x.put("summary", o.summary());
        x.put("ships", o.shipsLost()); x.put("carriers", o.carriersLost()); x.put("aircraft", o.aircraftLost()); x.put("munitions", o.munitionsExpended());
        return x;
    }

    public static TheaterOutcome outcomeFromJson(Map<String, Object> x) {
        return new TheaterOutcome(TheaterOutcome.TaiwanStatus.valueOf((String) x.get("status")), Json.integer(x.get("weeks")), Json.integer(x.get("killed")),
                Json.num(x.get("growth")), Json.num(x.get("inflation")), Json.num(x.get("approval")), Json.num(x.get("deterrence")), (String) x.get("summary"),
                Json.integer(x.get("ships")), Json.integer(x.get("carriers")), Json.integer(x.get("aircraft")), Json.integer(x.get("munitions")));
    }
}
