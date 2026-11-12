package game.mandate.core.game;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.state.Party;

/**
 * Everything needed to start (and, with the command log, to replay) a game.
 *
 * @param length          how far the game runs: through the first hundred days (the vertical slice), or the full term
 * @param realRoster      whether Battleground 2028's real-person roster is available (opt-in; RISK_REGISTER R-02)
 * @param customCandidate a player-made candidate (its id is {@link Candidate#CUSTOM_ID}), or null
 */
public record GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId,
                        Scenario scenario, Difficulty difficulty, Length length, boolean realRoster, Candidate customCandidate) {

    public GameSetup {
        if (customCandidate != null && !customCandidate.id().equals(Candidate.CUSTOM_ID))
            throw new IllegalArgumentException("A custom candidate has the id " + Candidate.CUSTOM_ID);
    }

    /** A full-term game with the fictional cast (the foundation's signature). */
    public GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId, Scenario scenario, Difficulty difficulty) {
        this(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, Length.FULL_TERM, false, null);
    }

    public GameSetup withLength(Length l) {
        return new GameSetup(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, l, realRoster, customCandidate);
    }

    /** Plays as a player-made candidate. */
    public GameSetup withCustomCandidate(Candidate c) {
        return new GameSetup(seed, c.party(), c.id(), opponentCandidateId, scenario, difficulty, length, realRoster, c);
    }

    public GameSetup withRealRoster(boolean on) {
        return new GameSetup(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, length, on, customCandidate);
    }

    public GameSetup withSeed(long s) {
        return new GameSetup(s, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, length, realRoster, customCandidate);
    }

    public enum Length {
        /** The 2028 election, the transition and the first hundred days, ending with the 100-day report. */
        HUNDRED_DAYS,
        /** On through monthly governing, the midterms and re-election in 2032. */
        FULL_TERM
    }

    /**
     * How strong the opponent's campaign is: its money, and whether it reads the race and adapts (Story: a fixed,
     * predictable campaign; Standard and Hard: it changes strategy with the polls, its cash and the calendar).
     */
    public enum Difficulty {
        STORY(0.8, false), STANDARD(1.0, true), HARD(1.2, true);

        private final double opponentResources;
        private final boolean adaptive;

        Difficulty(double r, boolean adaptive) { opponentResources = r; this.adaptive = adaptive; }

        public double opponentResources() { return opponentResources; }
        public boolean adaptive() { return adaptive; }
    }
}
