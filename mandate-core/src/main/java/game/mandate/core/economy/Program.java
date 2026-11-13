package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stream of federal money a policy puts into the economy (or takes out of it): its kind, how much a year at full
 * effect in percent of GDP (positive adds to the deficit: more spending or lower taxes), how it phases in by year,
 * the week it starts and, for a program with an end, the week it ends. {@code draw} scales its effect on demand:
 * the law's luck, drawn within its range when it passed (1 is the score's central estimate).
 */
public final class Program {
    final String id;
    final FiscalClass cls;
    final double fullPctGdp;
    final double[] phase;
    final int startWeek, endWeek;
    final double draw;
    /** How much of this program the economy has adjusted to (% of GDP): demand responds to the rest. */
    double adapted;

    public Program(String id, FiscalClass cls, double fullPctGdp, double[] phase, int startWeek, int endWeek, double draw) {
        this.id = id;
        this.cls = cls;
        this.fullPctGdp = fullPctGdp;
        this.phase = phase.clone();
        this.startWeek = startWeek;
        this.endWeek = endWeek;
        this.draw = draw;
    }

    public String id() { return id; }
    public FiscalClass fiscalClass() { return cls; }
    public double fullPctGdp() { return fullPctGdp; }
    public int startWeek() { return startWeek; }
    public int endWeek() { return endWeek; }

    public double draw() { return draw; }

    /** Percent of GDP in a given week: the full effect times that year's share of it; nothing before the start or after the end. */
    public double pctGdp(int week) {
        if (week < startWeek || (endWeek >= 0 && week >= endWeek)) return 0;
        int year = (week - startWeek) / 52;
        return fullPctGdp * phase[Math.min(year, phase.length - 1)];
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("class", cls.name()); m.put("full", fullPctGdp);
        List<Object> ph = new ArrayList<>();
        for (double v : phase) ph.add(v);
        m.put("phase", ph); m.put("start", startWeek); m.put("end", endWeek); m.put("draw", draw); m.put("adapted", adapted);
        return m;
    }

    static Program fromJson(Map<String, Object> m) {
        List<Object> ph = Json.array(m.get("phase"));
        double[] phase = new double[ph.size()];
        for (int i = 0; i < phase.length; i++) phase[i] = Json.num(ph.get(i));
        Program p = new Program((String) m.get("id"), FiscalClass.valueOf((String) m.get("class")), Json.num(m.get("full")), phase,
                Json.integer(m.get("start")), Json.integer(m.get("end")), Json.num(m.get("draw")));
        p.adapted = Json.num(m.get("adapted"));
        return p;
    }
}
