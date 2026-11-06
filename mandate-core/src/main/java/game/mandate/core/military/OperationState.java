package game.mandate.core.military;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** A war in progress, stepped in operational turns by {@link MilitaryEngine}. */
public final class OperationState {
    TheaterScenario.Commitment commitment;
    double hours;
    double redStrength;
    double progress;            // 0..100; Taiwan falls at 100
    double fog;                 // campaign-level effectiveness draw
    double deterrence;
    boolean basing;
    int warningMonths;
    double defenseCompetence;
    int killed, ships, carriers, aircraft;
    double munitionsUsed;

    OperationState() {}

    public TheaterScenario.Commitment commitment() { return commitment; }
    public double days() { return hours / 24.0; }
    public double redStrength() { return redStrength; }
    public double progress() { return progress; }
    public int killed() { return killed; }
    public int shipsLost() { return ships; }
    public int carriersLost() { return carriers; }
    public int aircraftLost() { return aircraft; }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commitment", commitment); m.put("hours", hours); m.put("red", redStrength); m.put("progress", progress); m.put("fog", fog);
        m.put("deterrence", deterrence); m.put("basing", basing); m.put("warning", warningMonths); m.put("defense", defenseCompetence);
        m.put("killed", killed); m.put("ships", ships); m.put("carriers", carriers); m.put("aircraft", aircraft); m.put("munitionsUsed", munitionsUsed);
        return m;
    }

    static OperationState fromJson(Map<String, Object> m) {
        OperationState o = new OperationState();
        o.commitment = TheaterScenario.Commitment.valueOf((String) m.get("commitment"));
        o.hours = Json.num(m.get("hours")); o.redStrength = Json.num(m.get("red")); o.progress = Json.num(m.get("progress")); o.fog = Json.num(m.get("fog"));
        o.deterrence = Json.num(m.get("deterrence")); o.basing = (Boolean) m.get("basing"); o.warningMonths = Json.integer(m.get("warning"));
        o.defenseCompetence = Json.num(m.get("defense")); o.killed = Json.integer(m.get("killed")); o.ships = Json.integer(m.get("ships"));
        o.carriers = Json.integer(m.get("carriers")); o.aircraft = Json.integer(m.get("aircraft")); o.munitionsUsed = Json.num(m.get("munitionsUsed"));
        return o;
    }
}
