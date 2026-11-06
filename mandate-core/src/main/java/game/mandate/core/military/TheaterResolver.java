package game.mandate.core.military;

import game.mandate.core.rng.DeterministicRandomManager;

/** Fights the war. The abstract resolver ships in 1.0; a detailed operational engine is an expansion. */
public interface TheaterResolver {
    TheaterOutcome resolve(TheaterScenario scenario, DeterministicRandomManager rng);
}
