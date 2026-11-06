package game.mandate.core.electorate;

import game.mandate.core.state.Party;

import java.util.Optional;

/**
 * Static facts about one state (or DC). Mutable per-game values live in the simulation, never here.
 *
 * @param baselineMargin 2024 presidential margin, D minus R, in points
 * @param elasticity     how strongly the state follows national swings (1.0 = average)
 */
public record UsState(String code, String name, int electoralVotes, double baselineMargin,
                      double elasticity, Optional<Party> senateUp2028, Optional<Party> senateUp2030) {

    /** Popular-vote weight: House seats (EV minus 2) as a turnout proxy; DC counts 1. */
    public double voteWeight() { return Math.max(1, electoralVotes - 2); }
}
