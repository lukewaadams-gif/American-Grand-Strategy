package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;

import java.util.function.Predicate;

/** Shared helpers: deterministic setups and "play until" loops driven by the autopilot. */
final class EngineFixtures {
    private EngineFixtures() {}

    static GameSetup setup(long seed) {
        boolean dem = seed % 2 == 0;
        return new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? "d_reyes" : "r_marsh", dem ? "r_harlan" : "d_whitfield",
                Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD);
    }

    static GameEngine playUntil(GameEngine g, Predicate<GameEngine> stop) {
        int guard = 0;
        while (!g.over() && !stop.test(g) && guard++ < 2000) AutoPilot.playTurn(g);
        return g;
    }

    /** A game whose player won 2028 and has just been inaugurated. */
    static GameEngine governing(long fromSeed) {
        for (long seed = fromSeed; seed < fromSeed + 400; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed)), x -> x.phase() == Phase.GOVERNING);
            if (g.phase() == Phase.GOVERNING) return g;
        }
        throw new AssertionError("no seed reached governing");
    }

    static void answerAll(GameEngine g, int option) {
        while (!g.pendingEvents().isEmpty()) {
            var p = g.pendingEvents().get(0);
            g.apply(new game.mandate.core.command.Command.ResolveEvent(p.instanceId(), Math.min(option, p.definition().options().size() - 1)));
        }
    }
}
