package game.mandate.core;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.game.Game;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guard rails, not tuning targets: they fail loudly if a change breaks the basic balance. */
class BalanceTest {
    @Test
    void tossUpIsWinnableFromEitherSide() {
        String[] dems = {"d_reyes", "d_whitfield", "d_okafor", "d_lindqvist"};
        String[] reps = {"r_calloway", "r_marsh", "r_benton", "r_harlan"};
        int demWins = 0, n = 240;
        for (int i = 0; i < n; i++) {
            Party p = i % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
            String d = dems[i % 4], r = reps[(i / 4) % 4];
            Game g = new Game(new GameSetup(500 + i, p, p == Party.DEMOCRAT ? d : r, p == Party.DEMOCRAT ? r : d,
                    Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD));
            while (!g.over()) AutoPilot.playTurn(g);
            if (g.result2028().orElseThrow().winner() == Party.DEMOCRAT) demWins++;
        }
        double rate = demWins / (double) n;
        assertTrue(rate > 0.35 && rate < 0.65, "Democratic 2028 win rate " + rate);
    }
}
