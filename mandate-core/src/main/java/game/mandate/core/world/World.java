package game.mandate.core.world;

import game.mandate.core.rng.RngStreams;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Flashpoint tensions and the Taiwan escalation ladder. Tension drifts monthly; deterrence pushes the
 * Taiwan drift down. Crossing a rung produces an escalation the game turns into a decision event, so
 * the player always gets to respond before the next rung — wars are never a surprise roll.
 */
public final class World {

    /** Rungs of the Taiwan ladder; each is entered at most once per game. */
    public enum Escalation {
        DRILLS(50), QUARANTINE(65), BLOCKADE(80), WAR(92);

        private final double threshold;

        Escalation(double t) { threshold = t; }

        public double threshold() { return threshold; }
    }

    private final Map<HotSpot, Double> tension = new EnumMap<>(HotSpot.class);
    private double deterrence = 50;
    private double munitions = 45;
    private boolean japanBasing = true;
    private Escalation rung;            // highest rung reached, null = calm
    private int monthsSinceBlockade = -1;
    private boolean warOver;

    public World() {
        tension.put(HotSpot.TAIWAN_STRAIT, 30.0);
        tension.put(HotSpot.MIDDLE_EAST, 45.0);
        tension.put(HotSpot.EASTERN_EUROPE, 50.0);
    }

    /** Advance one month and return any newly crossed Taiwan rungs. */
    public List<Escalation> stepMonth(RngStreams rng) {
        for (HotSpot h : HotSpot.values()) {
            double t = tension.get(h);
            double drift = switch (h) {
                // Mild pull back toward calm, pushed by low deterrence; each rung crossed adds escalation momentum.
                case TAIWAN_STRAIT -> warOver ? -1.0
                        : 0.8 - (deterrence - 50) * 0.05 - 0.012 * (t - 30) + (rung == null ? 0 : 0.7 * (rung.ordinal() + 1));
                default -> 0.08 * (45 - t);
            };
            tension.put(h, clamp(t + drift + 3.0 * rng.gaussian("world." + h.name())));
        }
        if (monthsSinceBlockade >= 0) monthsSinceBlockade++;
        List<Escalation> crossed = new ArrayList<>();
        if (warOver) return crossed;
        for (Escalation e : Escalation.values()) {
            if (rung != null && e.ordinal() <= rung.ordinal()) continue;
            if (tension.get(HotSpot.TAIWAN_STRAIT) >= e.threshold()) {
                // War also needs Beijing to judge the odds acceptable: deterrence can stop it at the brink.
                if (e == Escalation.WAR && rng.uniform("world.war") < deterrence / 100.0) break;
                rung = e;
                crossed.add(e);
                if (e == Escalation.BLOCKADE) monthsSinceBlockade = 0;
                break; // at most one rung per month
            }
        }
        munitions = Math.min(100, munitions + 0.4);
        return crossed;
    }

    public double tension(HotSpot h) { return tension.get(h); }
    public void addTension(HotSpot h, double d) { tension.put(h, clamp(tension.get(h) + d)); }
    public double deterrence() { return deterrence; }
    public void addDeterrence(double d) { deterrence = clamp(deterrence + d); }
    public double munitions() { return munitions; }
    public void addMunitions(double d) { munitions = clamp(munitions + d); }
    public boolean japanBasing() { return japanBasing; }
    public void setJapanBasing(boolean b) { japanBasing = b; }
    public int warningMonths() { return Math.max(0, monthsSinceBlockade); }
    public Escalation rung() { return rung; }

    public void endWar(double deterrenceAfter) {
        warOver = true;
        deterrence = deterrenceAfter;
        tension.put(HotSpot.TAIWAN_STRAIT, 40.0);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
