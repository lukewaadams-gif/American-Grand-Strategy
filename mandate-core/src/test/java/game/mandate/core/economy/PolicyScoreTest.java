package game.mandate.core.economy;

import game.mandate.core.effects.Effect;
import game.mandate.core.policy.PolicyCatalog;
import game.mandate.core.policy.PolicyTableEngine;
import game.mandate.core.policy.PolicyTableState;
import game.mandate.core.rng.DeterministicRandomManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static game.mandate.core.economy.EconomyModelTest.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy table's scores against the note's targets (docs/ECONOMY_MODEL.md, "Numbers the model must reproduce"),
 * the table's moves, draws and outcomes, and the credit each law keeps.
 */
class PolicyScoreTest {
    static final PolicyCatalog CATALOG = PolicyCatalog.shared();
    static final PolicyTableEngine TABLE = new PolicyTableEngine(CATALOG, EconomyModelTest.LOUD.nominalGdp());
    static final EconomyEngine ECON = new EconomyEngine(EconomyModelTest.LOUD);

    static Score.Range score(String id, int steps) {
        EconomyEngine e = ECON;
        EconomyState s = e.newEconomy();
        BudgetState b = e.newBudget();
        List<List<Effect.PolicyStep>> st = TABLE.scoring(TABLE.newTable(), id, steps);
        return e.score(s, b, st.get(0), st.get(1), st.get(2));
    }

    @Test
    void theTableIsResoluteDesksWithTheScores() {
        assertEquals(146, CATALOG.all().size());
        long scored = CATALOG.all().stream().filter(d -> d.score() != null).count();
        assertTrue(scored >= 95, "scored " + scored);
        PolicyCatalog.Def corp = CATALOG.get("corp_rate");
        assertEquals("21%", corp.levels().get(corp.level2029()));
        assertEquals(-100, corp.stepCost(1));
        assertEquals(FiscalClass.CORPORATE, corp.score().fiscalClass());
        for (PolicyCatalog.Def d : CATALOG.all()) if (d.score() != null) assertEquals(5, d.score().phase().length, d.id());
        assertTrue(CATALOG.get("medicaid").outcomes().get("unins") < 0);
    }

    @Test
    void aBillMovesTwoStepsAnOrderOne() {
        PolicyTableState s = TABLE.newTable();
        DeterministicRandomManager rng = new DeterministicRandomManager(4);
        var bill = TABLE.move(s, "min_wage", 3, PolicyCatalog.Route.BILL, "The Raise the Wage Act", LocalDate.of(2029, 5, 1), rng);
        assertEquals(3, s.level("min_wage"));
        assertEquals(2, bill.moves().size());
        // the public's reaction: support of 62% for a signature policy, two steps of a law
        Effect.Approval reaction = (Effect.Approval) bill.effects().stream().filter(e -> e instanceof Effect.Approval).findFirst().orElseThrow();
        assertTrue(bill.effects().stream().anyMatch(e -> e instanceof Effect.GroupStanding), "the groups it touches move too");
        within("minimum wage, two steps: the public's reaction", reaction.points(), 4.7, 4.9);
        assertTrue(bill.moves().stream().allMatch(m -> m.draw() >= -1 && m.draw() <= 1));
        var order = TABLE.move(s, "drilling", 2, PolicyCatalog.Route.ORDER, null, LocalDate.of(2029, 5, 1), rng);
        assertEquals(1, order.moves().size());
        var none = TABLE.move(s, "drilling", 1, PolicyCatalog.Route.ORDER, null, LocalDate.of(2029, 6, 1), rng);
        assertTrue(none.moves().isEmpty(), "drilling is already wide open");
        // and the same seed draws the same luck
        PolicyTableState t = TABLE.newTable();
        var again = TABLE.move(t, "min_wage", 3, PolicyCatalog.Route.BILL, "The Raise the Wage Act", LocalDate.of(2029, 5, 1), new DeterministicRandomManager(4));
        assertEquals(bill.moves().get(0).draw(), again.moves().get(0).draw());
    }

    @Test
    void outcomesPhaseInAfterTheirLag() {
        PolicyTableState s = TABLE.newTable();
        TABLE.move(s, "medicaid", 1, PolicyCatalog.Route.BILL, null, LocalDate.of(2029, 3, 1), new DeterministicRandomManager(1));
        double early = TABLE.outcomes(s, LocalDate.of(2029, 12, 1)).get("unins");
        double later = TABLE.outcomes(s, LocalDate.of(2032, 3, 1)).get("unins");
        assertTrue(early == 0, "nothing before Resolute Desk's lag of a year: " + early);
        assertTrue(later < -1.5 && later > -3.5, "uninsured down by about 2.5 points: " + later);
    }

    /** CBO's 2019 study of a $15 minimum: about 1.3 million fewer jobs (none to 3.7 million); about a million people out of poverty. */
    @Test
    void aFifteenDollarMinimumWage() {
        Score.Range r = score("min_wage", 1);
        double central = r.central().jobs()[2], lo = r.min(x -> x.jobs()[2]), hi = r.max(x -> x.jobs()[2]);
        System.out.printf("minimum wage: jobs after ten years %.2f million (%.2f to %.2f)%n", central, lo, hi);
        within("$15 minimum: jobs after ten years, millions", central, -1.8, -0.8);
        within("$15 minimum: the range's better end, millions", hi, -0.3, 0.01);
        within("$15 minimum: the range's worse end, millions", lo, -3.7, -1.8);
        double poverty = TABLE.outcomesOf("min_wage", 1).get("pov") / 100 * 340;
        within("$15 minimum: people out of poverty, millions", -poverty, 0.8, 1.3);
    }

    /** Zoning and permitting reform: starts up 5-15% over five years; rent inflation 0.5-1.5 points a year lower by years 3 to 5. */
    @Test
    void zoningReform() {
        EconomyModelTest.Run base = new EconomyModelTest.Run(EconomyModelTest.QUIET, 1);
        base.weeks(4);
        EconomyModelTest.Run with = base.copy(), without = base.copy();
        PolicyTableState t = TABLE.newTable();
        for (Effect.PolicyStep x : TABLE.scoring(t, "zoning", 2).get(1)) with.engine.enact(with.s, with.b, x);
        double starts5 = 0, lowest = 0, highest = -9;
        for (int w = 1; w <= 52 * 5; w++) {
            with.week(); without.week();
            if (w > 52 * 2) {
                double gap = with.s.part(Part.SHELTER) - without.s.part(Part.SHELTER);
                lowest = Math.min(lowest, gap); highest = Math.max(highest, gap);
            }
            if (w == 52 * 5) starts5 = (with.s.housing() / without.s.housing() - 1) * 100;
        }
        within("zoning: housing starts after five years, %", starts5, 5, 15);
        within("zoning: shelter inflation in years 3-5, most lowered by", -lowest, 0.5, 1.5);
        within("zoning: shelter inflation in years 3-5, least lowered by", -highest, 0.3, 1.5);
    }

    /** The corporate rate from 21% to 28% through the table: about $100 billion a year; output lower through investment. */
    @Test
    void theCorporateRateThroughTheTable() {
        Score.Range r = score("corp_rate", 1);
        within("corporate 21% to 28%: ten-year revenue, $ billions", -r.central().direct(), 1000, 1400);
        within("corporate 21% to 28%: with interest saved and the economy's response", -r.central().cost(), 1100, 1800);
        within("corporate 21% to 28%: output after ten years, %", r.central().output()[2], -0.6, -0.1);
        assertTrue(r.min(x -> x.output()[2]) < r.central().output()[2] && r.max(x -> x.output()[2]) > r.central().output()[2], "a range around the centre");
    }

    /** A stimulus scores higher in a slump than at full employment. */
    @Test
    void theSameLawScoresHigherInASlump() {
        List<List<Effect.PolicyStep>> st = TABLE.scoring(TABLE.newTable(), "child_credit", 1);
        EconomyModelTest.Run slump = EconomyModelTest.slump(), normal = new EconomyModelTest.Run(EconomyModelTest.QUIET, 1);
        normal.weeks(260);
        double inSlump = slump.engine.score(slump.s, slump.b, st.get(0), st.get(1), st.get(2)).central().output()[0];
        double inNormal = normal.engine.score(normal.s, normal.b, st.get(0), st.get(1), st.get(2)).central().output()[0];
        System.out.printf("child credit +1: output after a year %.3f%% in a slump, %.3f%% at full employment%n", inSlump, inNormal);
        assertTrue(inSlump > inNormal * 1.3, inSlump + " vs " + inNormal);
    }

    @Test
    void lawsKeepTheirCredit() {
        EconomyModelTest.Run r = new EconomyModelTest.Run(EconomyModelTest.QUIET, 1);
        PolicyTableState t = TABLE.newTable();
        for (Effect.PolicyStep x : TABLE.scoring(t, "defense", 1).get(1)) r.engine.enact(r.s, r.b, x);
        for (Effect.PolicyStep x : TABLE.scoring(t, "carbon_price", 1).get(1)) r.engine.enact(r.s, r.b, x);
        r.weeks(156);
        Law defense = r.s.laws().get(0), carbon = r.s.laws().get(1);
        assertTrue(defense.gapCredit() > 0.05, "defense spending added demand: " + defense.gapCredit());
        assertTrue(carbon.priceCredit() > 0.1, "a carbon price raised prices: " + carbon.priceCredit());
        assertTrue(r.b.programs().stream().anyMatch(p -> p.id().equals(defense.id())), "its money is a program with the same id");
    }

    @Test
    void scoringIsQuickEnough() {
        long t0 = System.nanoTime();
        for (int i = 0; i < 5; i++) score("child_credit", 1);
        double ms = (System.nanoTime() - t0) / 5e6;
        System.out.printf("a score takes %.1f ms%n", ms);
        assertTrue(ms < 400, ms + " ms");
    }
}
