package game.mandate.core.engine;

import game.mandate.core.command.Command;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Taiwan crisis turns on the president's orders (docs/BALANCE_TAIWAN.md; BUG_FIX_REPORT M-01). The balance itself is
 * measured by {@link TaiwanBalanceRun}; these are the properties a change must keep.
 */
class TaiwanOutcomeTest {

    static final long[] SEEDS = {7920, 15839, 23758, 31677};

    /** The same crisis, seed for seed, ends differently by the orders given: the patient president holds Taiwan, the one who
     *  will not use force loses it more often than not. */
    @Test
    void theOrdersDecideTheOutcome() {
        String base = ModesTest.governingSave(1);
        int patientWon = 0, diplomatLost = 0, differ = 0;
        for (long seed : SEEDS) {
            String patient = TaiwanBalanceRun.play(base, 0, TaiwanBalanceRun.Style.PATIENT, seed);
            String diplomat = TaiwanBalanceRun.play(base, 0, TaiwanBalanceRun.Style.DIPLOMAT, seed);
            if (patient.startsWith("WON")) patientWon++;
            if (diplomat.startsWith("LOST")) diplomatLost++;
            if (!patient.split(":")[0].equals(diplomat.split(":")[0])) differ++;
        }
        assertTrue(patientWon >= 3, "the patient president holds Taiwan: " + patientWon + " of " + SEEDS.length);
        assertTrue(diplomatLost >= 2, "diplomacy alone usually loses on the base case: " + diplomatLost + " of " + SEEDS.length);
        assertTrue(differ >= 2, "the orders change the result");
    }

    /** A new Individual Crisis differs by its seed (China's plan and the war's draws), and the save replays it exactly. */
    @Test
    void theSeedVariesTheCrisisAndTheSaveReplaysIt() {
        String base = ModesTest.governingSave(1);
        Set<String> plans = new HashSet<>();
        for (long seed : SEEDS) {
            GameEngine g = SaveManager.load(base);
            g.apply(new Command.IndividualCrisis("taiwan", 0, seed));
            for (int i = 0; i < 3; i++) { EngineFixtures.answerAll(g, 0); g.apply(new Command.EndTurn()); }
            plans.add(g.taiwan().orElseThrow().plan() + "/" + g.fingerprint());
            String saved = SaveManager.save(g);
            assertTrue(SaveManager.consistent(saved), "the save replays to the same crisis");
            assertEquals(g.fingerprint(), SaveManager.load(saved).fingerprint());
        }
        assertEquals(SEEDS.length, plans.size(), "each seed gives its own crisis");
        // seed 0 is the crisis the save itself gives: the same as before seeds existed
        GameEngine a = SaveManager.load(base), b = SaveManager.load(base);
        a.apply(new Command.IndividualCrisis("taiwan", 0));
        b.apply(new Command.IndividualCrisis("taiwan", 0, 0));
        assertEquals(a.fingerprint(), b.fingerprint());
    }
}
