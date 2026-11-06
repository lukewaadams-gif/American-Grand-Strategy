package game.mandate.core.executive;

import game.mandate.core.data.Csv;

import java.util.EnumMap;
import java.util.Map;

/** Resolute Desk's three transition-team archetypes; their numbers live in {@code /data/cabinet_archetypes.csv}. */
public enum NomineeType {
    LOYALIST, EXPERT, CROSSOVER;

    private static Map<NomineeType, double[]> stats;

    private static synchronized double[] stats(NomineeType t) {
        if (stats == null) {
            Map<NomineeType, double[]> m = new EnumMap<>(NomineeType.class);
            for (String[] f : Csv.readResource("/data/cabinet_archetypes.csv"))
                m.put(NomineeType.valueOf(f[0]), new double[]{Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3])});
            stats = m;
        }
        return stats.get(t);
    }

    public int competence() { return (int) stats(this)[0]; }
    public int loyalty() { return (int) stats(this)[1]; }
    public double controversy() { return stats(this)[2]; }
}
