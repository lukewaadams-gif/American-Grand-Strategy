package game.mandate.core.presidency;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One change to the president's week or to the watch plan, as Resolute Desk's calendar and watch screen offer them.
 * Carried by {@code Command.Presidency}. Slots run 0-20 (day x 3 + morning, afternoon, evening, from Monday).
 */
public sealed interface PresidencyOrder {
    /** One slot: an activity and, if it needs one, an issue's name or a state's code. */
    record Slot(int slot, Activity activity, String arg) implements PresidencyOrder {}
    /** A ready-made week. */
    record Week(Preset week) implements PresidencyOrder {}
    /** Hands the calendar to the chief of staff, or takes it back. */
    record Delegate(boolean on) implements PresidencyOrder {}
    /** A ready-made watch plan (0-5), or 6 for the player's own. */
    record WatchPreset(int preset) implements PresidencyOrder {}
    /** One priority of the watch plan, 0 to 1. */
    record WatchPriority(int priority, double value) implements PresidencyOrder {}
    /** Whether watch mode stops for a kind of decision. */
    record WatchStop(int stop, boolean on) implements PresidencyOrder {}
    /** Watch mode plans the coming week by the plan (Resolute Desk's {@code Auto.week}). */
    record WatchWeek() implements PresidencyOrder {}

    /** The calendar's ready-made weeks, and clearing it. */
    enum Preset { ROUTINE, ROAD, REST, CLEAR }

    static Map<String, Object> toJson(PresidencyOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Slot x -> { m.put("slot", x.slot()); m.put("activity", x.activity().name()); m.put("arg", x.arg()); }
            case Week x -> m.put("week", x.week().name());
            case Delegate x -> m.put("on", x.on());
            case WatchPreset x -> m.put("preset", x.preset());
            case WatchPriority x -> { m.put("priority", x.priority()); m.put("value", x.value()); }
            case WatchStop x -> { m.put("stop", x.stop()); m.put("on", x.on()); }
            case WatchWeek x -> { }
        }
        return m;
    }

    static PresidencyOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Slot" -> new Slot(Json.integer(m.get("slot")), Activity.valueOf((String) m.get("activity")), (String) m.get("arg"));
            case "Week" -> new Week(Preset.valueOf((String) m.get("week")));
            case "Delegate" -> new Delegate((Boolean) m.get("on"));
            case "WatchPreset" -> new WatchPreset(Json.integer(m.get("preset")));
            case "WatchPriority" -> new WatchPriority(Json.integer(m.get("priority")), Json.num(m.get("value")));
            case "WatchStop" -> new WatchStop(Json.integer(m.get("stop")), (Boolean) m.get("on"));
            case "WatchWeek" -> new WatchWeek();
            default -> throw new IllegalArgumentException("Unknown presidency order " + m.get("type"));
        };
    }
}
