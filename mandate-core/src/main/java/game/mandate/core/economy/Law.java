package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One policy step in force, as the economy sees it: its channels at full effect (already scaled by the law's luck), how
 * they phase in, the week they start, how much of each has been applied, and what the law has contributed so far
 * (docs/ECONOMY_MODEL.md, "Credit for what laws did"). Its money is the {@link Program} with the same id.
 */
public final class Law {
    final String id, label;
    final double[] channels, phase;
    final int startWeek;
    final double[] applied = new double[Channel.COUNT];
    /** Contributions so far: to the output gap (points), to potential output and to consumer prices (%), to unemployment (points). */
    double gapCredit, potentialCredit, priceCredit;

    Law(String id, String label, double[] channels, double[] phase, int startWeek) {
        this.id = id;
        this.label = label;
        this.channels = channels.clone();
        this.phase = phase.clone();
        this.startWeek = startWeek;
    }

    public String id() { return id; }
    public String label() { return label; }
    public int startWeek() { return startWeek; }
    public double channel(Channel c) { return channels[c.ordinal()]; }
    public double applied(Channel c) { return applied[c.ordinal()]; }
    /** What the law has added to output so far, %: its demand and its supply. */
    public double outputCredit() { return gapCredit + potentialCredit; }
    public double gapCredit() { return gapCredit; }
    public double potentialCredit() { return potentialCredit; }
    /** What the law has added to the consumer price level so far, %. */
    public double priceCredit() { return priceCredit; }
    /** What the law has added to unemployment so far, points: the natural rate it moved, less the demand it added. */
    public double unemploymentCredit(EconomyParams p) { return applied[Channel.NAIRU.ordinal()] - p.okun * gapCredit; }

    /**
     * The share of the full effect at {@code years} after the start: rising from nothing to the first year's share over
     * the first year, then in straight lines between the years' shares.
     */
    static double share(double[] phase, double years) {
        if (years <= 0) return 0;
        if (years < 1) return phase[0] * years;
        int y = (int) Math.floor(years);
        double a = phase[Math.min(y - 1, phase.length - 1)], b = phase[Math.min(y, phase.length - 1)];
        return a + (b - a) * (years - y);
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("label", label); m.put("channels", EconomyState.nums(channels)); m.put("phase", EconomyState.nums(phase));
        m.put("start", startWeek); m.put("applied", EconomyState.nums(applied));
        m.put("credit", EconomyState.nums(gapCredit, potentialCredit, priceCredit));
        return m;
    }

    static Law fromJson(Map<String, Object> m) {
        Law l = new Law((String) m.get("id"), (String) m.get("label"), EconomyState.read(m.get("channels")), EconomyState.read(m.get("phase")),
                Json.integer(m.get("start")));
        double[] a = EconomyState.read(m.get("applied"));
        System.arraycopy(a, 0, l.applied, 0, Math.min(a.length, Channel.COUNT));
        double[] c = EconomyState.read(m.get("credit"));
        l.gapCredit = c[0]; l.potentialCredit = c[1]; l.priceCredit = c[2];
        return l;
    }

}
