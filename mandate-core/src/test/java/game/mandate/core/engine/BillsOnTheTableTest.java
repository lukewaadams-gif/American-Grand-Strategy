package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.effects.Effect;
import game.mandate.core.policy.PolicyCatalog;
import game.mandate.core.policy.PolicyTableEngine;
import game.mandate.core.policy.PolicyTableState;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This game's bills as the policy steps they make (presidency milestone 4, step 4). */
class BillsOnTheTableTest {
    static final PolicyCatalog CATALOG = PolicyCatalog.shared();
    static final PolicyTableEngine TABLE = new PolicyTableEngine(CATALOG, 34500);

    @Test
    void everyBillIsOnTheTableAndEveryStepCanBeMade() {
        PolicyTableState s = TABLE.newTable();
        for (Bill b : game.mandate.core.congress.BillCatalog.load().all()) {
            Map<String, Integer> steps = CATALOG.bill(b.id());
            assertTrue(!steps.isEmpty(), b.id() + " is on the table");
            for (var e : steps.entrySet()) {
                PolicyCatalog.Def d = CATALOG.get(e.getKey());
                int to = s.level(d.id()) + e.getValue();
                assertTrue(to >= 0 && to <= d.top(), b.id() + ": " + d.id() + " cannot go from " + s.level(d.id()) + " to " + to);
                assertTrue(Math.abs(e.getValue()) <= 2, b.id() + ": a bill moves a policy at most two steps");
            }
        }
    }

    /** A pay-for covers its share of what the bill adds: most of it with taxes on the wealthy, less with cuts elsewhere. */
    @Test
    void aPayForCoversItsShare() {
        double borrowed = cost("BORROW"), revenue = cost("REVENUE"), cuts = cost("CUTS");
        assertTrue(borrowed > 0.2, "the tax cut adds to the deficit: " + borrowed);
        assertEquals(0.2 * borrowed, revenue, 1e-9);
        assertEquals(0.3 * borrowed, cuts, 1e-9);
    }

    private static double cost(String payFor) {
        PolicyTableEngine.Passed p = TABLE.enactBill(TABLE.newTable(), new Effect.LawPassed("tax_cut", "Middle-Class Tax Cut Extension", 1, 1, payFor, true, 0.3),
                LocalDate.of(2029, 4, 1), new DeterministicRandomManager(3));
        return p.cost();
    }

    /** The public reacts as Resolute Desk's rule says: each step's support against an even split. */
    @Test
    void theReactionIsResoluteDesks() {
        PolicyTableEngine.Passed p = TABLE.enactBill(TABLE.newTable(), new Effect.LawPassed("housing", "Build More Homes Act", 1, 1, "BORROW", true, 0.3),
                LocalDate.of(2029, 4, 1), new DeterministicRandomManager(3));
        // zoning: support 58%, not a signature policy, two steps; the housing credit one step at its own support
        double expected = PolicyTableEngine.reaction(CATALOG.get("zoning"), 2, PolicyCatalog.Route.BILL)
                + PolicyTableEngine.reaction(CATALOG.get("lihtc"), 1, PolicyCatalog.Route.BILL);
        assertEquals(expected, p.reaction(), 1e-9);
        assertEquals(3, p.moves().size());
    }

    /** The Build More Homes Act builds homes: starts are higher two years on than without it. */
    @Test
    void theBuildMoreHomesActBuildsHomes() {
        for (long seed = 1; seed < 200; seed += 8) {
            GameEngine base = EngineFixtures.governing(seed);
            if (base.laws().contains("housing")) continue;   // it was this seed's hundred-days flagship
            LegislativeStrategy route = null;
            double best = 0.3;
            for (LegislativeStrategy s : java.util.List.of(LegislativeStrategy.PARTY_LINE, LegislativeStrategy.BIPARTISAN_DEAL)) {
                double chance = base.whip("housing", s, 20).passChance();
                if (chance > best) { best = chance; route = s; }
            }
            if (route == null) continue;
            GameEngine with = SaveManager.load(SaveManager.save(base)), without = SaveManager.load(SaveManager.save(base));
            with.apply(new Command.PushBill("housing", route, 20));
            if (!with.laws().contains("housing")) continue;
            for (int wk = 0; wk < 104; wk++) {
                EngineFixtures.answerAll(with, 0); with.apply(new Command.EndTurn());
                EngineFixtures.answerAll(without, 0); without.apply(new Command.EndTurn());
            }
            double gain = with.state().economy().housing() / without.state().economy().housing() - 1;
            assertTrue(gain > 0.01, "housing starts two years on: " + gain);
            assertEquals(2, with.state().policyTable().level("zoning"));
            assertTrue(with.state().economy().laws().stream().anyMatch(l -> l.id().startsWith("zoning@")));
            return;
        }
        throw new AssertionError("no seed passed the Build More Homes Act");
    }
}
