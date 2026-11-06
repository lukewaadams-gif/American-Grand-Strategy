package game.mandate.core.game;

import game.mandate.core.state.Party;

/** Everything needed to start (and, with the command log, to replay) a game. */
public record GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId,
                        Scenario scenario, Difficulty difficulty) {

    public enum Difficulty {
        STORY(0.8), STANDARD(1.0), HARD(1.2);

        private final double opponentResources;

        Difficulty(double r) { opponentResources = r; }

        public double opponentResources() { return opponentResources; }
    }
}
