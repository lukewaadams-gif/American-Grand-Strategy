package game.mandate.core.game;

import game.mandate.core.state.Party;

/**
 * Everything needed to start (and, with the command log, to replay) a game.
 *
 * @param length how far the game runs: through the first hundred days (the vertical slice), or the full term
 */
public record GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId,
                        Scenario scenario, Difficulty difficulty, Length length) {

    /** A full-term game (the foundation's signature). */
    public GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId, Scenario scenario, Difficulty difficulty) {
        this(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, Length.FULL_TERM);
    }

    public GameSetup withLength(Length l) { return new GameSetup(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, l); }

    public enum Length {
        /** The 2028 election, the transition and the first hundred days, ending with the 100-day report. */
        HUNDRED_DAYS,
        /** On through monthly governing, the midterms and re-election in 2032. */
        FULL_TERM
    }

    public enum Difficulty {
        STORY(0.8), STANDARD(1.0), HARD(1.2);

        private final double opponentResources;

        Difficulty(double r) { opponentResources = r; }

        public double opponentResources() { return opponentResources; }
    }
}
