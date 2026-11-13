/*
 * BgExport: exports Battleground 2028's calibrated 96-cell electorate and its real-politician roster as
 * Mandate content files. A standalone developer tool; it is not part of the Maven build and nothing in the
 * game runs it.
 *
 * How the shipped files were produced (run from the Mandate repository root, OpenJDK 21):
 *
 *   javac -encoding UTF-8 -nowarn -d /tmp/bg-classes /path/to/Battleground2028/Battleground2028.java
 *   java -Djava.awt.headless=true tools/BgExport.java /tmp/bg-classes mandate-core/src/main/resources/data \
 *        b678c752db24a711b78b821af603302b26beeeba
 *
 * (The shipped files were produced with exactly these commands, except that the classes went to a private
 * scratch directory instead of /tmp/bg-classes; any directory works. Re-running is byte-for-byte reproducible.)
 *
 * Arguments: <compiled BG classes> [<output directory>, default mandate-core/src/main/resources/data]
 * [<BG commit, written into the file headers>]. The BG source is only read, never modified.
 *
 * What it does: loads the compiled classes in their own class loader, calls Battleground2028$Elect.init()
 * (state composition, overlays, opinions and the raking calibration to 2024), then reads the static arrays by
 * reflection and writes
 *   electorate_cells.csv  one row per (state, cell): 51 states x 16 blocs x 3 ages x 2 sexes = 4,896 rows
 *   candidates_bg.csv     BG's real Democratic and Republican candidates in the format of candidates.csv
 *
 * Candidate rating mapping (BG ratings are 0-100; Mandate ratings are 1-10, via r = round(1 + 9 * v / 100),
 * so BG's 0, 50 and 100 land on Mandate's 1, 5.5 (rounded to 6) and 10):
 *   charisma    <- A_CHA "Charisma"
 *   debate      <- A_DEB "Debating"
 *   fundraising <- max(A_SMD "Small-dollar fundraising", A_BIG "Big-donor network"): BG splits fundraising
 *                  by channel and a candidate raises through the stronger one; A_SELF (self-funding) is not
 *                  fundraising and is left out
 *   policy      <- A_INT "Intellect & policy depth"
 *   discipline  <- A_DIS "Message discipline" (BG has it, so the 100-minus-scandal fallback is not used)
 *   ideology    <- (econ + cult) / 2, clamped to -1..+1 and rounded to 0.01. econ and cult are BG's
 *                  two-axis position (-1 left .. +1 right): the faction's defaults, or the candidate's own
 *                  override where BG sets one with pos(). Equal weights: BG has no single left-right score.
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BgExport {
    private static final String MAIN = "Battleground2028";
    private static final int BG_DEM = 0, BG_REP = 1;

    private final ClassLoader loader;
    private final Class<?> main, elect;

    private BgExport(Path classes) throws Exception {
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, BgExport.class.getClassLoader());
        main = Class.forName(MAIN, true, loader);
        elect = Class.forName(MAIN + "$Elect", true, loader);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java -Djava.awt.headless=true tools/BgExport.java <bg-classes> [<out-dir>] [<bg-commit>]");
            System.exit(2);
        }
        System.setProperty("java.awt.headless", "true");
        Path classes = Path.of(args[0]);
        Path out = Path.of(args.length > 1 ? args[1] : "mandate-core/src/main/resources/data");
        String commit = args.length > 2 ? args[2] : "unrecorded";
        BgExport x = new BgExport(classes);
        x.invoke(x.elect, "init");
        Files.createDirectories(out);
        int rows = x.writeCells(out.resolve("electorate_cells.csv"), commit);
        int cands = x.writeCandidates(out.resolve("candidates_bg.csv"), commit);
        int markets = x.writeMarkets(out.resolve("media_markets.csv"), commit);
        int cstates = x.writeCampaignStates(out.resolve("campaign_states.csv"), commit);
        System.out.printf(Locale.ROOT, "electorate_cells.csv: %d rows%ncandidates_bg.csv: %d candidates%n", rows, cands);
        System.out.printf(Locale.ROOT, "media_markets.csv: %d markets%ncampaign_states.csv: %d states%n", markets, cstates);
        System.out.printf(Locale.ROOT, "BG calibration check, 2024 composition: national two-party D %.4f%n", x.calib2024());
    }

    // ---------------------------------------------------------------------------------------------- cells

    private int writeCells(Path file, String commit) throws Exception {
        int ns = intConst(main, "NS"), ncell = intConst(main, "NCELL");
        String[] bloc = (String[]) staticField(main, "BLOC"), age = (String[]) staticField(main, "AGE");
        Object[] states = (Object[]) staticField(main, "STATES");
        double[][] cvap = (double[][]) staticField(elect, "cvap"), cvap24 = (double[][]) staticField(elect, "cvap24");
        double[][] baseL = (double[][]) staticField(elect, "baseL"), turnB = (double[][]) staticField(elect, "turnB");
        double[][] elast = (double[][]) staticField(elect, "elast");
        int rows = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("# state,bloc,bloc_name,age,age_name,sex,cvap2028,cvap2024,turnout,base_logit,elasticity,swing2026");
            w.println("# Battleground 2028's calibrated 96-cell electorate (16 blocs x 3 ages x 2 sexes per state), exported by");
            w.println("# tools/BgExport.java from BG commit " + commit + " after Elect.init(). Do not edit by hand: re-run the tool.");
            w.println("# cvap2028 / cvap2024: eligible voters (persons) in the cell under BG's 2028 and 2024 composition (Elect.cvap, cvap24).");
            w.println("# turnout: BG base turnout of eligible voters (Elect.turnB), scaled per state to reproduce 2024 total votes.");
            w.println("# base_logit: calibrated Democratic two-party logit in the 2024 environment (Elect.baseL).");
            w.println("# elasticity: persuadability (Elect.elast = BLOC_ELAST x AGE_ELAST). swing2026: Elect.swing2026(c), logit.");
            w.println("# sex: 0 = men, 1 = women. age_name and bloc_name are BG's own AGE and BLOC labels.");
            for (int s = 0; s < ns; s++) {
                String code = (String) instanceField(states[s], "ab");
                for (int c = 0; c < ncell; c++) {
                    int b = (int) invoke(main, "cellBloc", c), a = (int) invoke(main, "cellAge", c), x = (int) invoke(main, "cellSex", c);
                    double sw = (double) invoke(elect, "swing2026", c);
                    w.println(String.join(",", code, Integer.toString(b), safe(bloc[b]), Integer.toString(a), safe(age[a]), Integer.toString(x),
                            fmt(cvap[s][c], 1), fmt(cvap24[s][c], 1), fmt(turnB[s][c], 6), fmt(baseL[s][c], 6), fmt(elast[s][c], 4), fmt(sw, 4)));
                    rows++;
                }
            }
        }
        return rows;
    }

    /** BG's own test calib: logistic(baseL) weighted by cvap24 x turnB (it prints 49.0 against the actual 49.2). */
    private double calib2024() throws Exception {
        double[][] cvap24 = (double[][]) staticField(elect, "cvap24"), baseL = (double[][]) staticField(elect, "baseL");
        double[][] turnB = (double[][]) staticField(elect, "turnB");
        double d = 0, t = 0;
        for (int s = 0; s < cvap24.length; s++)
            for (int c = 0; c < cvap24[s].length; c++) {
                double w = cvap24[s][c] * turnB[s][c];
                d += w / (1 + Math.exp(-baseL[s][c]));
                t += w;
            }
        return d / t;
    }

    // ---------------------------------------------------------------------------------------------- the campaign

    /** BG's TV markets after Elect.markets(): 103 real DMAs plus a 'Rest of <state>' market wherever coverage is short. */
    private int writeMarkets(Path file, String commit) throws Exception {
        String[] name = (String[]) staticField(elect, "marketName");
        double[] hh = (double[]) staticField(elect, "marketHH");
        double[][] share = (double[][]) staticField(elect, "dmaShare");
        int[][] in = (int[][]) staticField(elect, "marketStates");
        Object[] states = (Object[]) staticField(main, "STATES");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("# market,name,households_thousands,states");
            w.println("# Battleground 2028's broadcast TV markets (Elect.marketName, marketHH, dmaShare), exported by tools/BgExport.java");
            w.println("# from BG commit " + commit + " after Elect.init(). Do not edit by hand: re-run the tool.");
            w.println("# households_thousands: TV households in the market. states: space-separated CODE:share pairs, where share is the");
            w.println("# fraction of that STATE's TV households the market reaches (BG's dmaShare[m][s]); a buy's cost is split across");
            w.println("# its states in proportion to these shares and its rating points reach each state in proportion to them.");
            w.println("# Content CSVs cannot quote, so a comma in a market name is dropped (BG's \"Washington, DC\" is \"Washington DC\").");
            for (int m = 0; m < name.length; m++) {
                StringBuilder b = new StringBuilder();
                for (int s : in[m]) b.append(b.length() == 0 ? "" : " ").append((String) instanceField(states[s], "ab")).append(':').append(fmt(share[m][s], 6));
                w.println(String.join(",", Integer.toString(m), safe(name[m].replace(", ", " ")), fmt(hh[m], 1), b.toString()));
            }
        }
        return name.length;
    }

    /**
     * What BG's campaign reads about each state: income (event and staff costs), urban and rural shares (door-knocking
     * density, rally crowds), Hispanic share (Spanish-language TV), map position (travel), TV households, eligible
     * adults, donor hubs, and how much each of BG's 30 voter groups counts for in the state.
     */
    private int writeCampaignStates(Path file, String commit) throws Exception {
        Object[] states = (Object[]) staticField(main, "STATES");
        double[] tvhh = (double[]) staticField(main, "STATE_TVHH");
        double[][] cvap = (double[][]) staticField(elect, "cvap"), turnB = (double[][]) staticField(elect, "turnB");
        double[][] baseL = (double[][]) staticField(elect, "baseL"), elast = (double[][]) staticField(elect, "elast");
        String hubs = (String) staticField(Class.forName(MAIN + "$Week", true, loader), "DONOR_HUBS");
        String[] group = (String[]) staticField(main, "GROUP");
        Class<?> vote = Class.forName(MAIN + "$Vote", true, loader);
        Method member = vote.getDeclaredMethod("member", int.class, int.class, int.class);
        member.setAccessible(true);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            StringBuilder head = new StringBuilder("# code,income,urban,rural,hispanic,map_x,map_y,tv_households_thousands,adults,donor_hub");
            for (int g = 0; g < group.length; g++) head.append(",g").append(g);
            w.println(head);
            w.println("# Battleground 2028's per-state campaign inputs (STATES, STATE_TVHH, Week.DONOR_HUBS, Elect), exported by");
            w.println("# tools/BgExport.java from BG commit " + commit + " after Elect.init(). Do not edit by hand: re-run the tool.");
            w.println("# income: median household income (dollars); urban, rural, hispanic: percent; map_x, map_y: BG's map position");
            w.println("# (its travel rule calls two states more than 220 of these units apart a long flight); adults: eligible voters");
            w.println("# (sum of Elect.cvap); donor_hub: 1 where BG's fundraisers raise full value.");
            w.println("# g0..g29: the weight of each of BG's voter groups in the state's swing, sum(w p(1-p) e member) / sum(w p(1-p) e)");
            w.println("# over the state's cells (w = eligible x base turnout, p = 2024 Democratic share, e = elasticity), so that a");
            w.println("# group-image term of x logit moves the state as a uniform x * weight would. Groups, in order:");
            w.println("# " + String.join(" | ", group));
            for (int s = 0; s < states.length; s++) {
                String code = (String) instanceField(states[s], "ab");
                double adults = 0;
                for (double v : cvap[s]) adults += v;
                StringBuilder b = new StringBuilder(String.join(",", code, fmt((double) instanceField(states[s], "income"), 0),
                        fmt((double) instanceField(states[s], "urban"), 2), fmt((double) instanceField(states[s], "rural"), 2),
                        fmt((double) instanceField(states[s], "hisp"), 2), fmt((double) instanceField(states[s], "cx"), 1),
                        fmt((double) instanceField(states[s], "cy"), 1), fmt(tvhh[s], 1), fmt(adults, 0),
                        hubs.contains(code) ? "1" : "0"));
                double den = 0;
                double[] num = new double[group.length];
                for (int c = 0; c < cvap[s].length; c++) {
                    double p = 1 / (1 + Math.exp(-baseL[s][c])), wt = cvap[s][c] * turnB[s][c] * p * (1 - p) * elast[s][c];
                    den += wt;
                    for (int g = 0; g < group.length; g++) num[g] += wt * (double) member.invoke(null, g, s, c);
                }
                for (int g = 0; g < group.length; g++) b.append(',').append(fmt(den > 0 ? num[g] / den : 0, 4));
                w.println(b);
            }
        }
        return states.length;
    }

    // ---------------------------------------------------------------------------------------------- candidates

    private int writeCandidates(Path file, String commit) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> roster = (List<Object>) staticField(main, "ROSTER");
        int cha = intConst(main, "A_CHA"), deb = intConst(main, "A_DEB"), smd = intConst(main, "A_SMD"), big = intConst(main, "A_BIG");
        int intel = intConst(main, "A_INT"), dis = intConst(main, "A_DIS");
        List<String> lines = new ArrayList<>();
        for (Object c : roster) {
            int party = (int) instanceField(c, "party");
            if (party != BG_DEM && party != BG_REP) continue;
            if ((boolean) instanceField(c, "fictional") || (boolean) instanceField(c, "custom")) continue;
            int[] a = (int[]) instanceField(c, "a");
            double econ = (double) instanceField(c, "econ"), cult = (double) instanceField(c, "cult");
            double ideology = Math.round(Math.max(-1, Math.min(1, (econ + cult) / 2)) * 100) / 100.0;
            String name = instanceField(c, "first") + " " + instanceField(c, "last");
            lines.add(String.join(",", "bg_" + instanceField(c, "id"), party == BG_DEM ? "DEMOCRAT" : "REPUBLICAN", safe(name),
                    (String) instanceField(c, "homeAb"), rating(a[cha]), rating(a[deb]), rating(Math.max(a[smd], a[big])),
                    rating(a[intel]), rating(a[dis]), String.format(Locale.ROOT, "%.2f", ideology)));
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("# id,party,name,home_state,charisma,debate,fundraising,policy,discipline,ideology");
            w.println("# OPT-IN REAL-PERSON ROSTER. NOT FOR COMMERCIAL RELEASE (RISK_REGISTER.md R-02). Nothing loads this file by default;");
            w.println("# shipped builds use the fictional cast in candidates.csv.");
            w.println("# Provenance: Battleground 2028's candidate roster (commit " + commit + "), exported by tools/BgExport.java.");
            w.println("# These are the source game's estimates of real people (its own words: 'judgements for play, not facts'),");
            w.println("# rescaled, not statements about them. Only BG's real Democrats and Republicans are included.");
            w.println("# Ratings 1-10 = round(1 + 9 x BG rating / 100): charisma <- Charisma, debate <- Debating,");
            w.println("# fundraising <- max(Small-dollar fundraising, Big-donor network), policy <- Intellect & policy depth,");
            w.println("# discipline <- Message discipline. Ideology -1 (left) .. +1 (right) = mean of BG's econ and cult positions.");
            for (String l : lines) w.println(l);
        }
        return lines.size();
    }

    private static String rating(int bg) { return Integer.toString((int) Math.max(1, Math.min(10, Math.round(1 + 9 * bg / 100.0)))); }

    // ---------------------------------------------------------------------------------------------- reflection

    private static Object staticField(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static int intConst(Class<?> owner, String name) throws ReflectiveOperationException { return (int) staticField(owner, name); }

    private static Object instanceField(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private Object invoke(Class<?> owner, String name, int... args) throws ReflectiveOperationException {
        Method m = args.length == 0 ? owner.getDeclaredMethod(name) : owner.getDeclaredMethod(name, int.class);
        m.setAccessible(true);
        return args.length == 0 ? m.invoke(null) : m.invoke(null, args[0]);
    }

    private static String fmt(double v, int decimals) { return String.format(Locale.ROOT, "%." + decimals + "f", v); }

    /** Content CSVs cannot quote, so a comma in a name would corrupt the row. */
    private static String safe(String s) {
        if (s.contains(",")) throw new IllegalStateException("Comma in exported text: " + s);
        return s;
    }
}
