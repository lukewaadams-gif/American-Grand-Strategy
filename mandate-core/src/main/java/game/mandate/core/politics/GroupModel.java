package game.mandate.core.politics;

import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The country's voter groups (Resolute Desk's opinion/Demographics, Groups and {@code Approval.cells}): approval in each
 * of the 96 voter cells of every state (sixteen blocs, three ages, two sexes), leaning as the cell voted in 2024, shifted
 * together so the nation matches the headline number; each of thirty groups is the cells it covers, weighted by eligible
 * voters. Nine groups (suburban, rural, urban, union households, veterans, gun owners, LGBTQ, students, parents) cut
 * across the cells: their share of each cell comes from the state's own figures (Resolute Desk's {@code Elect.overlays}).
 * Effects aimed at a group shift its cells; they redistribute approval among groups without moving the headline.
 */
public final class GroupModel {
    public static final int NG = 30, NCELL = 96;
    public static final String[] GROUP = {"Gen Z (18-29)", "Seniors (65+)", "Young men", "Young women", "Women", "Men",
            "White evangelicals", "White Catholics", "White, no degree", "White, college degree", "Black voters", "Black men",
            "Hispanic voters", "Hispanic men", "Cuban & Venezuelan", "Puerto Rican & Dominican", "Asian Americans",
            "Jewish voters", "Arab & Muslim Americans", "Latter-day Saints", "Native Americans", "Suburban voters",
            "Rural voters", "Urban voters", "Union households", "Veterans & families", "Gun owners", "LGBTQ voters",
            "College students", "Parents"};
    /** The policy table's code for each group (the order above). */
    public static final String[] CODE = {"genz", "sen", "ym", "yw", "wom", "men", "evan", "cath", "wnc", "wcol", "blk", "blkm", "hisp", "hispm", "cuban",
            "prdom", "asian", "jew", "arab", "lds", "native", "sub", "rural", "urban", "union", "vet", "gun", "lgbt", "stud", "par"};
    /** The groups Resolute Desk shows first. */
    public static final int[] SHOW = {0, 1, 4, 5, 8, 9, 10, 12, 16, 6, 7, 21, 22, 23, 24, 25, 29};

    static final int B_WEV_NC = 0, B_WEV_C = 1, B_WCA_NC = 2, B_WCA_C = 3, B_WOT_NC = 4, B_WOT_C = 5, B_JEW = 6, B_LDS = 7,
            B_BLK = 8, B_HMX = 9, B_HCU = 10, B_HPR = 11, B_ASN = 12, B_NAT = 13, B_ARB = 14;

    private final List<String> states = new ArrayList<>();
    private final double[][] weight, base;
    private final float[][][] mem;

    public GroupModel() {
        Map<String, double[][]> cells = new LinkedHashMap<>();
        for (String[] r : Csv.readResource("/data/electorate_cells.csv")) {
            String st = r[0];
            int b = Integer.parseInt(r[1]), a = Integer.parseInt(r[3]), x = Integer.parseInt(r[5]);
            double[][] c = cells.computeIfAbsent(st, k -> new double[NCELL][2]);
            int i = (b * 3 + a) * 2 + x;
            c[i][0] = Double.parseDouble(r[7]);
            c[i][1] = Double.parseDouble(r[9]);
        }
        Map<String, double[]> traits = new LinkedHashMap<>();
        for (String[] r : Csv.readResource("/data/state_traits.csv"))
            traits.put(r[0], new double[]{Double.parseDouble(r[1]), Double.parseDouble(r[2]), Double.parseDouble(r[3]), Double.parseDouble(r[4]),
                    Double.parseDouble(r[5]), Double.parseDouble(r[6])});
        states.addAll(cells.keySet());
        int ns = states.size();
        weight = new double[ns][NCELL];
        base = new double[ns][NCELL];
        mem = new float[NG][ns][NCELL];
        for (int s = 0; s < ns; s++) {
            double[][] c = cells.get(states.get(s));
            double[] t = traits.getOrDefault(states.get(s), new double[]{20, 25, 10, 8, 40, 6});
            for (int i = 0; i < NCELL; i++) {
                weight[s][i] = c[i][0];
                base[s][i] = c[i][1];
                double[] ov = overlays(t, i / 6, (i / 2) % 3, i % 2);
                for (int g = 0; g < NG; g++) mem[g][s][i] = (float) (g >= 21 ? ov[g - 21] : inGroup(g, i / 6, (i / 2) % 3, i % 2));
            }
        }
    }

    /** Membership of a cell in one of the first twenty-one groups. */
    static double inGroup(int g, int b, int a, int s) {
        boolean hisp = b == B_HMX || b == B_HCU || b == B_HPR;
        return switch (g) {
            case 0 -> a == 0 ? 1 : 0;
            case 1 -> a == 2 ? 1 : 0;
            case 2 -> a == 0 && s == 0 ? 1 : 0;
            case 3 -> a == 0 && s == 1 ? 1 : 0;
            case 4 -> s == 1 ? 1 : 0;
            case 5 -> s == 0 ? 1 : 0;
            case 6 -> b == B_WEV_NC || b == B_WEV_C ? 1 : 0;
            case 7 -> b == B_WCA_NC || b == B_WCA_C ? 1 : 0;
            case 8 -> b == B_WEV_NC || b == B_WCA_NC || b == B_WOT_NC ? 1 : 0;
            case 9 -> b == B_WEV_C || b == B_WCA_C || b == B_WOT_C ? 1 : 0;
            case 10 -> b == B_BLK ? 1 : 0;
            case 11 -> b == B_BLK && s == 0 ? 1 : 0;
            case 12 -> hisp ? 1 : 0;
            case 13 -> hisp && s == 0 ? 1 : 0;
            case 14 -> b == B_HCU ? 1 : 0;
            case 15 -> b == B_HPR ? 1 : 0;
            case 16 -> b == B_ASN ? 1 : 0;
            case 17 -> b == B_JEW ? 1 : 0;
            case 18 -> b == B_ARB ? 1 : 0;
            case 19 -> b == B_LDS ? 1 : 0;
            case 20 -> b == B_NAT ? 1 : 0;
            default -> 0;
        };
    }

    /**
     * The overlay shares of a cell (Resolute Desk's {@code Elect.overlays}), in the order suburban, rural, urban, union,
     * veterans, gun owners, LGBTQ, students, parents. {@code t} is the state's rural, urban, union, veteran, gun and LGBTQ
     * percentages.
     */
    static double[] overlays(double[] t, int b, int a, int x) {
        double rb, ub;
        switch (b) {
            case B_WEV_NC, B_WEV_C -> { rb = 1.5; ub = 0.4; }
            case B_WCA_NC, B_WCA_C -> { rb = 1.0; ub = 0.7; }
            case B_WOT_NC -> { rb = 1.3; ub = 0.6; }
            case B_WOT_C -> { rb = 0.75; ub = 1.25; }
            case B_BLK -> { rb = 0.55; ub = 1.8; }
            case B_HMX -> { rb = 0.45; ub = 1.5; }
            case B_HCU -> { rb = 0.2; ub = 1.3; }
            case B_HPR -> { rb = 0.3; ub = 1.8; }
            case B_ASN -> { rb = 0.2; ub = 1.6; }
            case B_NAT -> { rb = 2.0; ub = 0.5; }
            case B_JEW -> { rb = 0.15; ub = 1.6; }
            case B_LDS -> { rb = 1.0; ub = 0.6; }
            case B_ARB -> { rb = 0.2; ub = 1.6; }
            default -> { rb = 0.6; ub = 1.3; }
        }
        boolean col = b == B_WEV_C || b == B_WCA_C || b == B_WOT_C, hisp = b == B_HMX || b == B_HCU || b == B_HPR;
        double rural = clamp(t[0] / 100 * rb * (a == 0 ? 0.85 : a == 2 ? 1.15 : 1), 0, 0.92);
        double urban = clamp(t[1] / 100 * ub * (a == 0 ? 1.3 : a == 2 ? 0.8 : 1), 0, 0.97 - rural);
        double suburb = Math.max(0, 1 - rural - urban);
        double union = clamp(t[2] / 100 * (col ? 0.8 : 1.15) * (b == B_BLK ? 1.3 : hisp ? 0.9 : 1) * (a == 0 ? 0.6 : a == 1 ? 1.1 : 1.0), 0, 0.7);
        double vet = clamp(t[3] / 100 * 1.4 * (x == 0 ? 1.8 : 0.35) * (a == 0 ? 0.4 : a == 1 ? 1.0 : 1.8), 0, 0.6);
        double gb = b <= B_WEV_C ? 1.3 : b == B_WOT_NC ? 1.15 : b == B_WOT_C ? 0.75 : b == B_WCA_NC || b == B_WCA_C ? 1.0 : b == B_LDS ? 1.1 : b == B_NAT ? 1.0
                : b == B_JEW || b == B_ARB ? 0.3 : b == B_ASN ? 0.4 : 0.6;
        double gun = clamp(t[4] / 100 * gb * (x == 0 ? 1.25 : 0.8) * (0.8 + rural * 0.6), 0, 0.92);
        double lgbt = clamp(t[5] / 100 * (a == 0 ? 2.6 : a == 1 ? 0.75 : 0.3) * (b <= B_WEV_C ? 0.3 : b == B_LDS ? 0.4 : 1), 0, 0.4);
        double student = a == 0 ? (col ? 0.55 : 0.28) : 0;
        double parent = a == 1 ? 0.48 : a == 0 ? 0.15 : 0.02;
        return new double[]{suburb, rural, urban, union, vet, gun, lgbt, student, parent};
    }

    /** A group's index by its code, or -1. */
    public static int index(String code) {
        for (int i = 0; i < CODE.length; i++) if (CODE[i].equals(code)) return i;
        return -1;
    }

    /**
     * Approval in each group, percent: every cell leans as it voted in 2024 (more strongly the more polarized the era),
     * plus the effects aimed at its groups and the president's standing in its state, all shifted so the nation matches
     * {@code national}.
     */
    public double[] approval(double national, Party president, double polarization, double[] adj, Map<String, Double> stateBump) {
        int ns = states.size();
        double sign = president == Party.REPUBLICAN ? -1 : 1, k = 0.8 + 0.6 * polarization;
        double[][] l0 = new double[ns][NCELL];
        for (int s = 0; s < ns; s++) {
            double local = 0.06 * stateBump.getOrDefault(states.get(s), 0.0);
            for (int c = 0; c < NCELL; c++) {
                double l = k * sign * base[s][c] + local;
                for (int g = 0; g < NG; g++) if (adj[g] != 0) l += adj[g] * mem[g][s][c];
                l0[s][c] = l;
            }
        }
        double target = Math.max(0.02, Math.min(0.98, national / 100)), lo = -6, hi = 6, off = 0;
        for (int it = 0; it < 40; it++) {
            off = (lo + hi) / 2;
            double num = 0, den = 0;
            for (int s = 0; s < ns; s++) for (int c = 0; c < NCELL; c++) { double w = weight[s][c]; if (w > 0) { num += w * logistic(l0[s][c] + off); den += w; } }
            double v = num / den;
            if (Math.abs(v - target) < 0.00005) break;
            if (v < target) lo = off; else hi = off;
        }
        double[] gs = new double[NG], gw = new double[NG];
        for (int s = 0; s < ns; s++) for (int c = 0; c < NCELL; c++) {
            double w = weight[s][c];
            if (w <= 0) continue;
            double p = logistic(l0[s][c] + off);
            for (int g = 0; g < NG; g++) { double m = mem[g][s][c]; if (m > 0) { gs[g] += w * m * p; gw[g] += w * m; } }
        }
        double[] out = new double[NG];
        for (int g = 0; g < NG; g++) out[g] = gw[g] > 0 ? gs[g] / gw[g] * 100 : national;
        return out;
    }

    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
