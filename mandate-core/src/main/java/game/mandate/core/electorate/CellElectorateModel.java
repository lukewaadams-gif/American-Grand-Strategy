package game.mandate.core.electorate;

import game.mandate.core.data.Csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The Battleground 2028 96-cell electorate behind {@link ElectorateModel}: every state is 16 voter blocs x 3 age
 * groups x 2 sexes, each cell with its eligible voters, base turnout, calibrated 2024 Democratic logit and
 * persuadability, as exported by {@code tools/BgExport.java} into {@value #RESOURCE}.
 *
 * <p>A cell's Democratic two-party logit is
 * <pre>
 *   baseLogit + (swing2026 - mean swing2026) + elasticity * (nationalSwing + localShift) * k
 * </pre>
 * and a state's margin is the turnout-weighted (eligible voters x base turnout) two-party result of its cells.
 * <ul>
 *   <li>The 2026 term is the group swing that outlasts the midterms (Hispanic, young, Black, Asian and college
 *       white voters back toward the Democrats). It is recentred on its national turnout-weighted mean, so it moves
 *       groups and states relative to one another without moving the national result: the national environment
 *       belongs to {@code nationalSwing}.</li>
 *   <li>{@code k} converts points of margin into logit. It is derived from the data at construction so that a
 *       +1 point {@code nationalSwing} moves this electorate's national margin by +1 point at the baseline.</li>
 * </ul>
 *
 * <p>Ported from the source game: the calibrated electorate only. Campaign coupling (group images, issue
 * ideals and salience, overlays, ad markets) is not ported; campaigns reach this model through
 * {@code localShift}. See {@code docs/SOURCE_AUDIT.md}.
 *
 * <p>Instances are immutable. The content file is parsed once and cached.
 */
public final class CellElectorateModel implements ElectorateModel {
    public static final String RESOURCE = "/data/electorate_cells.csv";

    /** Which eligible-voter composition weights the cells. */
    public enum Composition {
        /** BG's 2024 composition: with the 2026 swing off, this reproduces BG's own 2024 calibration. */
        Y2024,
        /** BG's projected 2028 composition (the game's default). */
        Y2028
    }

    /** One group's expected two-party result. {@code share} is the group's fraction of expected votes cast. */
    public record GroupMargin(String label, double margin, double share) {}

    /** Crosstabs of one expected result: national margin plus margins by bloc, by age and by sex. */
    public record GroupResults(double nationalMargin, List<GroupMargin> byBloc, List<GroupMargin> byAge, List<GroupMargin> bySex) {
        public GroupResults {
            byBloc = List.copyOf(byBloc);
            byAge = List.copyOf(byAge);
            bySex = List.copyOf(bySex);
        }
    }

    private static final List<String> SEX_LABELS = List.of("Men", "Women");

    private final Cells cells;
    private final Composition composition;
    private final boolean with2026Swing;
    /** [state][cell] expected votes: eligible voters in the chosen composition x base turnout. */
    private final double[][] weight;
    /** [state][cell] logit at zero swing: base logit plus the recentred 2026 swing when it is on. */
    private final double[][] logit0;
    private final double swing2026Mean;
    private final double k;

    private CellElectorateModel(Cells cells, Composition composition, boolean with2026Swing) {
        this.cells = cells;
        this.composition = composition;
        this.with2026Swing = with2026Swing;
        int ns = cells.codes.size(), nc = cells.cellCount();
        weight = new double[ns][nc];
        logit0 = new double[ns][nc];
        double sw = 0, w = 0;
        for (int s = 0; s < ns; s++)
            for (int c = 0; c < nc; c++) {
                double cvap = composition == Composition.Y2024 ? cells.cvap2024[s][c] : cells.cvap2028[s][c];
                weight[s][c] = cvap * cells.turnout[s][c];
                sw += weight[s][c] * cells.swing2026[s][c];
                w += weight[s][c];
            }
        swing2026Mean = with2026Swing ? sw / w : 0;
        double slope = 0;
        for (int s = 0; s < ns; s++)
            for (int c = 0; c < nc; c++) {
                logit0[s][c] = cells.baseLogit[s][c] + (with2026Swing ? cells.swing2026[s][c] - swing2026Mean : 0);
                double p = logistic(logit0[s][c]);
                slope += weight[s][c] * p * (1 - p) * cells.elasticity[s][c];
            }
        // d(national margin)/d(swing) = 200 * k * sum(w p (1-p) e) / sum(w); set it to 1 at the baseline.
        k = w / (200 * slope);
    }

    /** The game's electorate: the 2028 composition with the recentred 2026 group swing. */
    public static CellElectorateModel load() { return of(Composition.Y2028, true); }

    public static CellElectorateModel of(Composition composition, boolean with2026Swing) {
        return new CellElectorateModel(Cache.CELLS, composition, with2026Swing);
    }

    // ------------------------------------------------------------------------------------------------ the seam

    @Override
    public double expectedMargin(UsState state, double nationalSwing, double localShift) {
        return stateMargin(state.code(), nationalSwing, localShift);
    }

    // ------------------------------------------------------------------------------------------------ read-only queries

    /** Expected two-party margin in one state, D minus R, in points. */
    public double stateMargin(String code, double nationalSwing, double localShift) {
        int s = cells.indexOf(code);
        double shift = (nationalSwing + localShift) * k, dv = 0, tv = 0;
        for (int c = 0; c < weight[s].length; c++) {
            double w = weight[s][c];
            dv += w * logistic(logit0[s][c] + cells.elasticity[s][c] * shift);
            tv += w;
        }
        return marginOf(dv, tv);
    }

    /** Expected margins in all 51 units, in content order. */
    public Map<String, Double> stateMargins(double nationalSwing, ToDoubleFunction<String> localShift) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String code : cells.codes) m.put(code, stateMargin(code, nationalSwing, localShift.applyAsDouble(code)));
        return Collections.unmodifiableMap(m);
    }

    /** Expected national two-party margin, D minus R, in points, from every cell's expected votes. */
    public double nationalMargin(double nationalSwing, ToDoubleFunction<String> localShift) {
        return groupResults(nationalSwing, localShift).nationalMargin();
    }

    /** Expected national two-party Democratic share, 0..1. */
    public double nationalDemShare(double nationalSwing, ToDoubleFunction<String> localShift) {
        return (nationalMargin(nationalSwing, localShift) / 100 + 1) / 2;
    }

    /** National margins by bloc, age and sex, for polling crosstabs. */
    public GroupResults groupResults(double nationalSwing, ToDoubleFunction<String> localShift) {
        int nb = cells.blocLabels.size(), na = cells.ageLabels.size(), nx = SEX_LABELS.size();
        double[] bd = new double[nb], bt = new double[nb], ad = new double[na], at = new double[na], xd = new double[nx], xt = new double[nx];
        double dv = 0, tv = 0;
        for (int s = 0; s < cells.codes.size(); s++) {
            double shift = (nationalSwing + localShift.applyAsDouble(cells.codes.get(s))) * k;
            for (int c = 0; c < weight[s].length; c++) {
                double w = weight[s][c], d = w * logistic(logit0[s][c] + cells.elasticity[s][c] * shift);
                bd[cells.bloc[c]] += d; bt[cells.bloc[c]] += w;
                ad[cells.age[c]] += d; at[cells.age[c]] += w;
                xd[cells.sex[c]] += d; xt[cells.sex[c]] += w;
                dv += d; tv += w;
            }
        }
        return new GroupResults(marginOf(dv, tv), groups(cells.blocLabels, bd, bt, tv), groups(cells.ageLabels, ad, at, tv),
                groups(SEX_LABELS, xd, xt, tv));
    }

    /**
     * A state's voters by bloc (the War room's Voters desk).
     *
     * @param share    the bloc's share of the state's expected votes
     * @param margin   its margin, D minus R, now; the blocs are shifted together so the state's total matches the poll
     * @param base     its margin at the 2024 baseline (no swing), to show how the campaign is doing with it
     * @param turnout  its base turnout
     * @param movable  how much a point of swing moves it, relative to the state as a whole (1 = average)
     */
    public record StateGroup(String label, double share, double margin, double base, double turnout, double movable) {}

    public List<StateGroup> stateGroups(String code, double nationalSwing, ToDoubleFunction<String> localShift, double pollMargin) {
        int s = cells.indexOf(code), nb = cells.blocLabels.size();
        double shift = (nationalSwing + localShift.applyAsDouble(code)) * k;
        double[] d = new double[nb], d0 = new double[nb], t = new double[nb], turn = new double[nb], mov = new double[nb];
        double dv = 0, tv = 0, movAll = 0;
        for (int c = 0; c < weight[s].length; c++) {
            double w = weight[s][c], p = logistic(logit0[s][c] + cells.elasticity[s][c] * shift), p0 = logistic(logit0[s][c]);
            int b = cells.bloc[c];
            d[b] += w * p; d0[b] += w * p0; t[b] += w;
            turn[b] += w * cells.turnout[s][c];
            double m = p * (1 - p) * cells.elasticity[s][c];
            mov[b] += w * m; movAll += w * m;
            dv += w * p; tv += w;
        }
        double offset = pollMargin - marginOf(dv, tv);
        double avgMov = tv > 0 ? movAll / tv : 1;
        List<StateGroup> out = new java.util.ArrayList<>();
        for (int b = 0; b < nb; b++) {
            if (t[b] <= 0) continue;
            out.add(new StateGroup(cells.blocLabels.get(b), t[b] / tv, marginOf(d[b], t[b]) + offset, marginOf(d0[b], t[b]),
                    turn[b] / t[b], avgMov > 0 ? mov[b] / t[b] / avgMov : 1));
        }
        return out;
    }

    /**
     * How much each age group counts in a state's swing: sum(w p(1-p) e) per age group over the state's cells, normalised.
     * A campaign term that differs by age (BG's digital persuasion) moves the state as its age-weighted average would.
     */
    public double[] ageSwingWeights(String code) {
        int s = cells.indexOf(code), na = cells.ageLabels.size();
        double[] out = new double[na];
        double t = 0;
        for (int c = 0; c < weight[s].length; c++) {
            double p = logistic(logit0[s][c]), v = weight[s][c] * p * (1 - p) * cells.elasticity[s][c];
            out[cells.age[c]] += v;
            t += v;
        }
        for (int a = 0; a < na; a++) out[a] = t > 0 ? out[a] / t : 1.0 / na;
        return out;
    }

    /**
     * The change in a state's margin, D minus R in points, from the two campaigns' turnout operations, as Battleground
     * 2028 models them (Vote.turnout): each side's enthusiasm scales its leaners' base turnout, then each side's
     * get-out-the-vote lift brings out a share of the cell's non-voters in proportion to that side's support there.
     * Evaluated at the state's baseline preferences, so it is a margin shift that can be added to the campaign's local shift.
     *
     * @param liftD the Democratic GOTV lift (BG's gotvSat of the GOTV stock, 0..0.12)
     * @param enD   the Democratic campaign's enthusiasm (0.5 = neutral)
     */
    public double turnoutShift(String code, double liftD, double liftR, double enD, double enR) {
        int s = cells.indexOf(code);
        double dv0 = 0, tv0 = 0, dv = 0, tv = 0;
        for (int c = 0; c < weight[s].length; c++) {
            double p = logistic(logit0[s][c]), eligible = composition == Composition.Y2024 ? cells.cvap2024[s][c] : cells.cvap2028[s][c];
            double base = cells.turnout[s][c];
            if (eligible <= 0 || base <= 0) continue;
            double w0 = eligible * Math.max(0.05, Math.min(0.97, base));
            double turn = base * (1 + 0.16 * (enD - 0.5) * 2 * p + 0.16 * (enR - 0.5) * 2 * (1 - p));
            turn += liftD * p * (1 - turn);
            turn += liftR * (1 - p) * (1 - turn);
            double w = eligible * Math.max(0.05, Math.min(0.97, turn));
            dv0 += w0 * p; tv0 += w0;
            dv += w * p; tv += w;
        }
        return marginOf(dv, tv) - marginOf(dv0, tv0);
    }

    public List<String> stateCodes() { return cells.codes; }

    /** BG's bloc names, by bloc index. */
    public List<String> blocLabels() { return cells.blocLabels; }

    /** BG's age-group names, by age index. */
    public List<String> ageLabels() { return cells.ageLabels; }

    /** By sex index: 0 men, 1 women. */
    public List<String> sexLabels() { return SEX_LABELS; }

    /** Logit per point of margin (k). */
    public double logitPerPoint() { return k; }

    /** The national turnout-weighted mean of the 2026 swing that was subtracted (logit); 0 when the swing is off. */
    public double swing2026Mean() { return swing2026Mean; }

    public Composition composition() { return composition; }

    public boolean with2026Swing() { return with2026Swing; }

    // ------------------------------------------------------------------------------------------------ helpers

    private static List<GroupMargin> groups(List<String> labels, double[] d, double[] t, double total) {
        List<GroupMargin> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) out.add(new GroupMargin(labels.get(i), marginOf(d[i], t[i]), t[i] / total));
        return out;
    }

    private static double marginOf(double dem, double total) { return total > 0 ? 100 * (2 * dem / total - 1) : 0; }

    private static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }

    /** The content file, parsed once. Initialised lazily and thread-safely by the class loader. */
    private static final class Cache {
        static final Cells CELLS = Cells.parse(Csv.readResource(RESOURCE));
    }

    /** Immutable parsed content: [state][cell] arrays, never exposed. */
    private static final class Cells {
        final List<String> codes, blocLabels, ageLabels;
        final Map<String, Integer> index;
        final int[] bloc, age, sex;
        final double[][] cvap2028, cvap2024, turnout, baseLogit, elasticity, swing2026;

        private Cells(List<String> codes, List<String> blocLabels, List<String> ageLabels, int[] bloc, int[] age, int[] sex,
                      double[][] cvap2028, double[][] cvap2024, double[][] turnout, double[][] baseLogit, double[][] elasticity,
                      double[][] swing2026) {
            this.codes = List.copyOf(codes);
            this.blocLabels = List.copyOf(blocLabels);
            this.ageLabels = List.copyOf(ageLabels);
            Map<String, Integer> ix = new HashMap<>();
            for (int i = 0; i < codes.size(); i++) ix.put(codes.get(i), i);
            this.index = Map.copyOf(ix);
            this.bloc = bloc; this.age = age; this.sex = sex;
            this.cvap2028 = cvap2028; this.cvap2024 = cvap2024; this.turnout = turnout; this.baseLogit = baseLogit;
            this.elasticity = elasticity; this.swing2026 = swing2026;
        }

        int cellCount() { return bloc.length; }

        int indexOf(String code) {
            Integer s = index.get(code);
            if (s == null) throw new IllegalArgumentException("No electorate cells for state " + code);
            return s;
        }

        /**
         * Columns: state, bloc, bloc_name, age, age_name, sex, cvap2028, cvap2024, turnout, base_logit, elasticity,
         * swing2026. Rows are grouped by state, and every state lists the same cells in the same order.
         */
        static Cells parse(List<String[]> rows) {
            int nb = 0, na = 0, nx = 0;
            for (String[] f : rows) {
                if (f.length != 12) throw new IllegalStateException(RESOURCE + ": expected 12 columns, got " + f.length);
                nb = Math.max(nb, Integer.parseInt(f[1]) + 1);
                na = Math.max(na, Integer.parseInt(f[3]) + 1);
                nx = Math.max(nx, Integer.parseInt(f[5]) + 1);
            }
            int nc = nb * na * nx;
            if (nx != SEX_LABELS.size() || nc == 0 || rows.size() % nc != 0)
                throw new IllegalStateException(RESOURCE + ": " + rows.size() + " rows do not form whole states of " + nc + " cells");
            int ns = rows.size() / nc;
            List<String> codes = new ArrayList<>();
            String[] blocLabels = new String[nb], ageLabels = new String[na];
            int[] bloc = new int[nc], age = new int[nc], sex = new int[nc];
            double[][] cv28 = new double[ns][nc], cv24 = new double[ns][nc], turn = new double[ns][nc], base = new double[ns][nc];
            double[][] el = new double[ns][nc], sw = new double[ns][nc];
            for (int r = 0; r < rows.size(); r++) {
                String[] f = rows.get(r);
                int s = r / nc, c = r % nc;
                if (c == 0) {
                    if (codes.contains(f[0])) throw new IllegalStateException(RESOURCE + ": state " + f[0] + " is not contiguous");
                    codes.add(f[0]);
                } else if (!f[0].equals(codes.get(s))) {
                    throw new IllegalStateException(RESOURCE + ": row " + (r + 1) + " breaks state " + codes.get(s));
                }
                int b = Integer.parseInt(f[1]), a = Integer.parseInt(f[3]), x = Integer.parseInt(f[5]);
                if (s == 0) {
                    bloc[c] = b; age[c] = a; sex[c] = x;
                    blocLabels[b] = f[2];
                    ageLabels[a] = f[4];
                } else if (bloc[c] != b || age[c] != a || sex[c] != x || !blocLabels[b].equals(f[2]) || !ageLabels[a].equals(f[4])) {
                    throw new IllegalStateException(RESOURCE + ": state " + f[0] + " lists its cells in a different order");
                }
                cv28[s][c] = Double.parseDouble(f[6]);
                cv24[s][c] = Double.parseDouble(f[7]);
                turn[s][c] = Double.parseDouble(f[8]);
                base[s][c] = Double.parseDouble(f[9]);
                el[s][c] = Double.parseDouble(f[10]);
                sw[s][c] = Double.parseDouble(f[11]);
            }
            for (int b = 0; b < nb; b++) if (blocLabels[b] == null) throw new IllegalStateException(RESOURCE + ": bloc " + b + " missing");
            for (int a = 0; a < na; a++) if (ageLabels[a] == null) throw new IllegalStateException(RESOURCE + ": age group " + a + " missing");
            return new Cells(codes, List.of(blocLabels), List.of(ageLabels), bloc, age, sex, cv28, cv24, turn, base, el, sw);
        }
    }
}
