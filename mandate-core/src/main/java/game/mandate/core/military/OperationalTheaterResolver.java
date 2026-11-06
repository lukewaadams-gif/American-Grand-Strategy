package game.mandate.core.military;

import game.mandate.core.rng.DeterministicRandomManager;

import java.util.Optional;

/**
 * Fights a whole war at once with the military engine's operational model, on a fresh 2027 force. Used where a
 * caller wants an outcome without a world (tests, tools); the game itself steps the war turn by turn.
 */
public final class OperationalTheaterResolver implements TheaterResolver {
    private final MilitaryEngine engine;

    public OperationalTheaterResolver(MilitaryEngine engine) { this.engine = engine; }

    @Override
    public TheaterOutcome resolve(TheaterScenario scenario, DeterministicRandomManager rng) {
        MilitaryState s = engine.newMilitary();
        engine.startOperation(s, scenario, rng);
        Optional<TheaterOutcome> out = Optional.empty();
        while (out.isEmpty()) out = engine.stepOperation(s, rng);
        return out.get();
    }
}
