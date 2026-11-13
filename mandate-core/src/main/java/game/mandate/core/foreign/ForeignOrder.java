package game.mandate.core.foreign;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the president does in the Situation Room (Resolute Desk's {@code SitRoom} and {@code StatecraftUI}).
 * Carried by {@code Command.Foreign}. Countries are their two-letter codes; wars their ids.
 */
public sealed interface ForeignOrder {
    record Call(String country) implements ForeignOrder {}
    record Invite(String country) implements ForeignOrder {}
    record Summit(String country) implements ForeignOrder {}
    record Tariff(String country, int level) implements ForeignOrder {}
    record Sanction(String country, int level) implements ForeignOrder {}
    record Aid(String country, int level) implements ForeignOrder {}
    record Operation(int index) implements ForeignOrder {}
    record Covert(int index) implements ForeignOrder {}
    record MilitaryAid(String country, int level) implements ForeignOrder {}
    record Arms(String country, int level) implements ForeignOrder {}
    /** American forces based in a country: an index into {@code TROOP_STEP}. */
    record Troops(String country, int step) implements ForeignOrder {}
    record Ties(String country, int level) implements ForeignOrder {}
    record Peacekeepers(String country, boolean on) implements ForeignOrder {}
    record TradeDeal(String country) implements ForeignOrder {}
    record Treaty(String country) implements ForeignOrder {}
    /** A covert program against one country: 0 influence, 1 cyber sabotage, 2 arm the opposition, 3 regime change. */
    record CovertProgram(String country, int kind) implements ForeignOrder {}
    record Objective(String id) implements ForeignOrder {}
    record Posture(int area, int level) implements ForeignOrder {}
    /** The alliance buttons: "article5", "press" or "drills". */
    record Alliance(String what) implements ForeignOrder {}
    record Authorize(String country) implements ForeignOrder {}
    /** A war of choice: 0 strikes, 1 a blockade, 2 a no-fly zone, 3 an invasion. */
    record StartWar(String country, int kind) implements ForeignOrder {}
    /** Troops in a war: a share up (positive) or down (negative), 0.1 or 0.25. */
    record WarTroops(String war, double change) implements ForeignOrder {}
    record WarStrategy(String war, int strategy) implements ForeignOrder {}
    /** The ways out of a war: "withdraw", "declare", "timetable26", "timetable52", "handover", "escalate". */
    record EndWar(String war, String how) implements ForeignOrder {}
    record Answer(String question, int option) implements ForeignOrder {}

    static Map<String, Object> toJson(ForeignOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Call x -> m.put("country", x.country());
            case Invite x -> m.put("country", x.country());
            case Summit x -> m.put("country", x.country());
            case Tariff x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Sanction x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Aid x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Operation x -> m.put("index", x.index());
            case Covert x -> m.put("index", x.index());
            case MilitaryAid x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Arms x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Troops x -> { m.put("country", x.country()); m.put("step", x.step()); }
            case Ties x -> { m.put("country", x.country()); m.put("level", x.level()); }
            case Peacekeepers x -> { m.put("country", x.country()); m.put("on", x.on()); }
            case TradeDeal x -> m.put("country", x.country());
            case Treaty x -> m.put("country", x.country());
            case CovertProgram x -> { m.put("country", x.country()); m.put("kind", x.kind()); }
            case Objective x -> m.put("id", x.id());
            case Posture x -> { m.put("area", x.area()); m.put("level", x.level()); }
            case Alliance x -> m.put("what", x.what());
            case Authorize x -> m.put("country", x.country());
            case StartWar x -> { m.put("country", x.country()); m.put("kind", x.kind()); }
            case WarTroops x -> { m.put("war", x.war()); m.put("change", x.change()); }
            case WarStrategy x -> { m.put("war", x.war()); m.put("strategy", x.strategy()); }
            case EndWar x -> { m.put("war", x.war()); m.put("how", x.how()); }
            case Answer x -> { m.put("question", x.question()); m.put("option", x.option()); }
        }
        return m;
    }

    static ForeignOrder fromJson(Map<String, Object> m) {
        String c = (String) m.get("country");
        return switch ((String) m.get("type")) {
            case "Call" -> new Call(c);
            case "Invite" -> new Invite(c);
            case "Summit" -> new Summit(c);
            case "Tariff" -> new Tariff(c, Json.integer(m.get("level")));
            case "Sanction" -> new Sanction(c, Json.integer(m.get("level")));
            case "Aid" -> new Aid(c, Json.integer(m.get("level")));
            case "Operation" -> new Operation(Json.integer(m.get("index")));
            case "Covert" -> new Covert(Json.integer(m.get("index")));
            case "MilitaryAid" -> new MilitaryAid(c, Json.integer(m.get("level")));
            case "Arms" -> new Arms(c, Json.integer(m.get("level")));
            case "Troops" -> new Troops(c, Json.integer(m.get("step")));
            case "Ties" -> new Ties(c, Json.integer(m.get("level")));
            case "Peacekeepers" -> new Peacekeepers(c, (Boolean) m.get("on"));
            case "TradeDeal" -> new TradeDeal(c);
            case "Treaty" -> new Treaty(c);
            case "CovertProgram" -> new CovertProgram(c, Json.integer(m.get("kind")));
            case "Objective" -> new Objective((String) m.get("id"));
            case "Posture" -> new Posture(Json.integer(m.get("area")), Json.integer(m.get("level")));
            case "Alliance" -> new Alliance((String) m.get("what"));
            case "Authorize" -> new Authorize(c);
            case "StartWar" -> new StartWar(c, Json.integer(m.get("kind")));
            case "WarTroops" -> new WarTroops((String) m.get("war"), Json.num(m.get("change")));
            case "WarStrategy" -> new WarStrategy((String) m.get("war"), Json.integer(m.get("strategy")));
            case "EndWar" -> new EndWar((String) m.get("war"), (String) m.get("how"));
            case "Answer" -> new Answer((String) m.get("question"), Json.integer(m.get("option")));
            default -> throw new IllegalArgumentException("Unknown Situation Room order " + m.get("type"));
        };
    }
}
