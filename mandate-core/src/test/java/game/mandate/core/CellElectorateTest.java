package game.mandate.core;

import game.mandate.core.data.Csv;
import game.mandate.core.electorate.CellElectorateModel;
import game.mandate.core.electorate.CellElectorateModel.Composition;
import game.mandate.core.electorate.CellElectorateModel.GroupMargin;
import game.mandate.core.electorate.CellElectorateModel.GroupResults;
import game.mandate.core.electorate.ElectionCalculator;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.rng.DeterministicRandomManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ported Battleground 2028 cell electorate (not yet used by the game). */
class CellElectorateTest {
    private final StateCatalog states = StateCatalog.load();
    private final CellElectorateModel game = CellElectorateModel.load();
    private final CellElectorateModel calib2024 = CellElectorateModel.of(Composition.Y2024, false);

    @Test
    void contentHas96CellsForEachOf51States() {
        List<String[]> rows = Csv.readResource(CellElectorateModel.RESOURCE);
        assertEquals(51 * 96, rows.size());
        assertEquals(51, game.stateCodes().size());
        for (UsState s : states.all()) assertTrue(game.stateCodes().contains(s.code()), "no cells for " + s.code());
        assertEquals(16, game.blocLabels().size());
        assertEquals("White evangelicals (no degree)", game.blocLabels().get(0));
        assertEquals("Black", game.blocLabels().get(8));
        assertEquals(List.of("18-29", "30-64", "65+"), game.ageLabels());
        assertEquals(List.of("Men", "Women"), game.sexLabels());
    }

    /** BG's {@code test calib}: 2024 composition, no 2026 swing, gives two-party D 49.0 against the actual 49.2. */
    @Test
    void zeroSwingWith2024CompositionReproducesBgCalibration() {
        assertEquals(0.492, calib2024.nationalDemShare(0, s -> 0), 0.004);
    }

    @Test
    void the2028ElectorateMovesTheNationalMarginOnlySlightly() {
        double m24 = calib2024.nationalMargin(0, s -> 0), m28 = game.nationalMargin(0, s -> 0);
        assertTrue(Math.abs(m28 - m24) < 1.5, "2028 composition + recentred 2026 swing moved the national margin " + (m28 - m24));
        // The recentred 2026 term reorders groups and states; on its own it barely moves the nation.
        double noSwing = CellElectorateModel.of(Composition.Y2028, false).nationalMargin(0, s -> 0);
        assertTrue(Math.abs(m28 - noSwing) < 0.3, "recentred 2026 swing moved the national margin " + (m28 - noSwing));
    }

    @Test
    void onePointOfNationalSwingIsOnePointOfNationalMargin() {
        double k = game.logitPerPoint();
        assertTrue(k > 0.015 && k < 0.035, "logit per point " + k);
        assertEquals(1.0, game.nationalMargin(1, s -> 0) - game.nationalMargin(0, s -> 0), 0.02);
        assertEquals(-1.0, game.nationalMargin(-1, s -> 0) - game.nationalMargin(0, s -> 0), 0.02);
    }

    @Test
    void moreSwingMeansAHigherMarginInEveryState() {
        for (UsState s : states.all()) {
            double prev = Double.NEGATIVE_INFINITY;
            for (int swing = -15; swing <= 15; swing++) {
                double m = game.expectedMargin(s, swing, 0);
                assertTrue(m > prev, s.code() + " not monotone at swing " + swing);
                prev = m;
            }
            assertTrue(game.expectedMargin(s, 0, 2) > game.expectedMargin(s, 0, 0), s.code() + " ignores local shift");
        }
    }

    @Test
    void electoralCollegeAtZeroSwingIsACloseRepublicanWin() {
        ElectionCalculator calc = new ElectionCalculator(states, game, 2.6, 2.5);
        ElectionResult r = calc.tally(2028, calc.expectedMargins(0, s -> 0));
        System.out.printf(Locale.ROOT, "cell electorate, zero swing, 2028 composition: D %d - R %d, tipping point %s, national %+.2f%n",
                r.demElectoralVotes(), r.repElectoralVotes(), r.tippingPoint(), game.nationalMargin(0, s -> 0));
        assertEquals(538, r.demElectoralVotes() + r.repElectoralVotes());
        assertTrue(r.repElectoralVotes() >= 270 && r.repElectoralVotes() <= 330, "R " + r.repElectoralVotes());
        // The calibrated 2024 electorate reproduces the 2024 map (district electors not split, as in the foundation).
        ElectionCalculator calc24 = new ElectionCalculator(states, calib2024, 2.6, 2.5);
        ElectionResult r24 = calc24.tally(2024, calc24.expectedMargins(0, s -> 0));
        assertEquals(226, r24.demElectoralVotes());
        assertEquals(312, r24.repElectoralVotes());
    }

    @Test
    void groupResultsAreSensible() {
        GroupResults g = game.groupResults(0, s -> 0);
        Map<String, Double> bloc = new java.util.HashMap<>();
        for (GroupMargin m : g.byBloc()) bloc.put(m.label(), m.margin());
        assertTrue(bloc.get("Black") > 50, "Black " + bloc.get("Black"));
        assertTrue(bloc.get("White evangelicals (no degree)") < -40, "evangelicals " + bloc.get("White evangelicals (no degree)"));
        assertTrue(bloc.get("White evangelicals (degree)") < -40, "evangelicals (degree) " + bloc.get("White evangelicals (degree)"));
        assertTrue(bloc.get("Other whites (degree)") > bloc.get("Other whites (no degree)"), "education gap");
        assertTrue(g.bySex().get(1).margin() > g.bySex().get(0).margin(), "gender gap");
        assertTrue(g.byAge().get(0).margin() > g.byAge().get(2).margin(), "age gap");
        for (List<GroupMargin> tab : List.of(g.byBloc(), g.byAge(), g.bySex()))
            assertEquals(1.0, tab.stream().mapToDouble(GroupMargin::share).sum(), 1e-9);
        assertEquals(game.nationalMargin(0, s -> 0), g.nationalMargin(), 1e-9);
        // A local shift in one state moves the crosstabs, a national swing moves every group.
        GroupResults up = game.groupResults(3, s -> 0);
        for (int b = 0; b < g.byBloc().size(); b++) assertTrue(up.byBloc().get(b).margin() > g.byBloc().get(b).margin());
        assertTrue(game.groupResults(0, s -> s.equals("CA") ? 5 : 0).nationalMargin() > g.nationalMargin());
    }

    @Test
    void electionCalculatorWorksWithTheCellModel() {
        ElectionCalculator calc = new ElectionCalculator(states, game, 2.6, 2.5);
        Map<String, Double> expected = calc.expectedMargins(1.5, s -> s.equals("PA") ? 1 : 0);
        assertEquals(game.stateMargins(1.5, s -> s.equals("PA") ? 1 : 0), expected);
        ElectionResult a = calc.resolve(2028, 1.5, s -> 0, 0.5, new DeterministicRandomManager(42), "election");
        ElectionResult b = calc.resolve(2028, 1.5, s -> 0, 0.5, new DeterministicRandomManager(42), "election");
        assertEquals(a, b);
        assertEquals(51, a.stateMargins().size());
        assertEquals(538, a.demElectoralVotes() + a.repElectoralVotes());
        assertTrue(states.contains(a.tippingPoint()), "tipping point " + a.tippingPoint());
        assertTrue(a.stateMargins().get("DC") > 50 && a.stateMargins().get("WY") < -20, "safe states stay safe");
    }
}
