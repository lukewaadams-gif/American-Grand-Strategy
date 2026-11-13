package game.mandate.core.taiwan;

import game.mandate.core.data.Fmt;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays a Taiwan scenario computer against computer to the end, headless (spec 11.9 step 6): for tests and for the
 * calibration against the source's own harness (spec Appendix B). Run {@code main} with the number of games per cell.
 */
public final class TaiwanHarness {
    private TaiwanHarness() {}

    /** The start date of the source's scenario: 1 March (the PLA's D-day window falls in April). */
    public static final LocalDate START = LocalDate.of(2030, 3, 1);

    /** Plays one game to the end and returns the final state. */
    public static TaiwanState play(TaiwanEngine engine, TaiwanScenario sc, long seed) {
        DeterministicRandomManager rng = new DeterministicRandomManager(seed);
        LocalDate start = LocalDate.of(sc.year(), 3, 1);
        TaiwanState s = engine.newCrisis(start, sc, rng);
        engine.autopilot(s, true);
        int guard = 0;
        while (!s.over() && guard++ < 1000) engine.turn(s, s.date(), rng);
        return s;
    }

    /** One line of statistics over a batch of games. */
    public record Row(String label, int games, String outcomes, double warDays, double usKilled, double usShips, double usCarriers, double usAircraft,
                      double prcShips, double prcAmphib, double prcKilled, double amphibSunkShare, double km2, double gapUS, double gapCN, double gapTW,
                      double gapWorld, double lrasm, double jassmEr, double asbm) {
        public String line() {
            return Fmt.format("%-26s %3d  %-58s war %5.1f d  US killed %6.0f  US ships %4.1f (%3.1f CV)  US aircraft %4.0f  PRC ships %5.1f (%4.1f amph)  PRC killed %6.0f"
                            + "  amph sunk %3.0f%%  km2 %5.0f  gaps %3.1f/%3.1f/%4.1f/%3.1f  LRASM %4.0f JASSM-ER %5.0f ASBM %5.0f",
                    label, games, outcomes, warDays, usKilled, usShips, usCarriers, usAircraft, prcShips, prcAmphib, prcKilled, amphibSunkShare * 100, km2,
                    gapUS * 100, gapCN * 100, gapTW * 100, gapWorld * 100, lrasm, jassmEr, asbm);
        }
    }

    public static Row batch(TaiwanEngine engine, String label, TaiwanScenario sc, long seed0, int games) {
        TaiwanData d = engine.data();
        int[] counts = new int[TaiwanEngine.OUTCOMES.length];
        double wd = 0, kia = 0, ships = 0, cv = 0, air = 0, pships = 0, pamph = 0, pkia = 0, share = 0, km = 0, gu = 0, gc = 0, gt = 0, gw = 0, lr = 0, je = 0, as = 0;
        for (int g = 0; g < games; g++) {
            TaiwanState s = play(engine, sc, seed0 + g * 7919L);
            if (s.outcome >= 0) counts[s.outcome]++;
            TaiwanState.Loss us = s.losses("USA"), cn = s.losses("CHN");
            wd += s.warDays(); kia += us.killed; ships += us.ships; cv += us.carriers; air += us.air + us.ground;
            pships += cn.ships; pamph += cn.amphibs; pkia += cn.killed;
            int alive = 0;
            for (TaiwanState.Group x : s.groups) if (x.side == TaiwanState.PRC) for (TaiwanState.Ship sh : x.ships) { String r = d.ships.get(sh.cls).role(); if (r.equals("amphib") || r.equals("lst")) alive += sh.n; }
            share += s.amphSunk + alive == 0 ? 0 : s.amphSunk / (double) (s.amphSunk + alive);
            km += s.territoryKm2();
            gu += s.gap[0]; gc += s.gap[1]; gt += s.gap[2]; gw += s.gap[6];
            lr += s.expended[TaiwanState.US][d.munition("LRASM")]; je += s.expended[TaiwanState.US][d.munition("JASSM_ER")];
            as += s.expended[TaiwanState.PRC][d.munition("DF21D")] + s.expended[TaiwanState.PRC][d.munition("DF26")];
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) if (counts[i] > 0) parts.add(counts[i] + " " + TaiwanEngine.OUTCOMES[i]);
        double n = games;
        return new Row(label, games, String.join(", ", parts), wd / n, kia / n, ships / n, cv / n, air / n, pships / n, pamph / n, pkia / n, share / n, km / n,
                gu / n, gc / n, gt / n, gw / n, lr / n, je / n, as / n);
    }

    public static void main(String[] args) {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int year = args.length > 1 ? Integer.parseInt(args[1]) : 2030;
        String forces = args.length > 2 ? "forcesYear=" + args[2] : null;
        TaiwanEngine e = new TaiwanEngine();
        String[][] cells = {{"invasion", "0"}, {"invasion", "1"}, {"invasion", "2"}, {"blockade", "0"}, {"islands", "0"}, {"strikes", "0"}, {"quarantine", "0"}, {null, "0"}};
        for (String[] cell : cells) {
            TaiwanScenario sc = TaiwanScenario.standalone(year, Integer.parseInt(cell[1]), "US").withPlan(cell[0]).withAssumptions(forces);
            Row r = batch(e, (cell[0] == null ? "planner's choice" : cell[0]) + " / " + new String[]{"base", "optimistic", "pessimistic"}[Integer.parseInt(cell[1])], sc, 1000, games);
            System.out.println(r.line());
        }
    }
}
