package game.mandate.core;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterminismTest {
    static GameSetup setup(long seed) {
        return new GameSetup(seed, Party.DEMOCRAT, "d_reyes", "r_marsh", Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD);
    }

    static GameEngine playOut(long seed) {
        GameEngine g = new GameEngine(setup(seed));
        int guard = 0;
        while (!g.over() && guard++ < 1200) AutoPilot.playTurn(g);
        assertTrue(g.over(), "game should finish");
        return g;
    }

    @Test
    void sameSeedSameGame() {
        assertEquals(playOut(42).log(), playOut(42).log());
    }

    @Test
    void differentSeedsDiverge() {
        assertNotEquals(playOut(42).log(), playOut(43).log());
    }

    @Test
    void replayingTheCommandLogReproducesTheGame() {
        GameEngine original = playOut(7);
        GameEngine replayed = GameEngine.replay(setup(7), original.history());
        assertEquals(original.log(), replayed.log());
        assertEquals(original.legacy(), replayed.legacy());
    }
}
