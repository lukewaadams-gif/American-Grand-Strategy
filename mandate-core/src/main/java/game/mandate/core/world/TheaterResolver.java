package game.mandate.core.world;

import game.mandate.core.rng.RngStreams;

/** Fights the war. The abstract resolver ships in 1.0; a detailed operational engine is an expansion. */
public interface TheaterResolver {
    TheaterOutcome resolve(TheaterScenario scenario, RngStreams rng);
}
