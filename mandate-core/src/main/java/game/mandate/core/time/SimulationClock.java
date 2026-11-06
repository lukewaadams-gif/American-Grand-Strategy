package game.mandate.core.time;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The master clock. One clock for the whole world, with a granularity per mode of play: monthly governing turns,
 * weekly campaign turns, daily turns and operational military turns of a few hours. Owned by the game engine.
 */
public final class SimulationClock {
    public enum Granularity { OPERATIONAL, DAILY, WEEKLY, MONTHLY }

    private LocalDateTime now;
    private Granularity mode;
    private long turns;

    public SimulationClock(LocalDate start, Granularity mode) {
        this.now = start.atStartOfDay();
        this.mode = mode;
    }

    public LocalDate date() { return now.toLocalDate(); }
    public LocalDateTime now() { return now; }
    public Granularity mode() { return mode; }
    public long turns() { return turns; }

    public void setMode(Granularity g) { mode = g; }

    /** Advance one turn at the current granularity (operational turns use {@link #advanceHours}). */
    public void advance() {
        now = switch (mode) {
            case MONTHLY -> now.plusMonths(1);
            case WEEKLY -> now.plusWeeks(1);
            case DAILY -> now.plusDays(1);
            case OPERATIONAL -> throw new IllegalStateException("Operational turns have a length; use advanceHours");
        };
        turns++;
    }

    public void advanceHours(double hours) {
        now = now.plusMinutes(Math.round(hours * 60));
        turns++;
    }

    /** Jump to a calendar date (a scheduled milestone such as election day or the inauguration). */
    public void set(LocalDate date) { now = date.atStartOfDay(); }

    /** Length of the next turn in weeks (for systems whose rates are per week). */
    public double weeksInNextTurn() {
        return switch (mode) {
            case MONTHLY -> java.time.temporal.ChronoUnit.DAYS.between(now, now.plusMonths(1)) / 7.0;
            case WEEKLY -> 1.0;
            case DAILY -> 1.0 / 7.0;
            case OPERATIONAL -> 0.0;
        };
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("now", now.toString()); m.put("mode", mode.name()); m.put("turns", turns);
        return m;
    }

    public static SimulationClock fromJson(Map<String, Object> m) {
        SimulationClock c = new SimulationClock(LocalDate.now(), Granularity.valueOf((String) m.get("mode")));
        c.now = LocalDateTime.parse((String) m.get("now"));
        c.turns = ((Number) m.get("turns")).longValue();
        return c;
    }
}
