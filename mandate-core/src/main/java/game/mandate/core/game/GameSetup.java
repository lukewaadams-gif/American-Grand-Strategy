package game.mandate.core.game;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.state.Party;

/**
 * Everything needed to start (and, with the command log, to replay) a game.
 *
 * @param mode            which of the four games this is (docs/MODES.md); an individual crisis begins from a governing world
 *                        with {@code Command.IndividualCrisis}, so a new game's mode is one of the other three
 * @param realRoster      whether the world is real people (the default on the start screen) or the fictional cast
 * @param customCandidate a player-made candidate (its id is {@link Candidate#CUSTOM_ID}), or null
 */
public record GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId,
                        Scenario scenario, Difficulty difficulty, Mode mode, boolean realRoster, Candidate customCandidate) {

    public GameSetup {
        if (customCandidate != null && !customCandidate.id().equals(Candidate.CUSTOM_ID))
            throw new IllegalArgumentException("A custom candidate has the id " + Candidate.CUSTOM_ID);
        if (mode == null) throw new IllegalArgumentException("A game has a mode");
    }

    /** A full game with the fictional cast (the foundation's signature). */
    public GameSetup(long seed, Party playerParty, String playerCandidateId, String opponentCandidateId, Scenario scenario, Difficulty difficulty) {
        this(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, Mode.FULL_GAME, false, null);
    }

    public GameSetup withMode(Mode m) {
        return new GameSetup(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, m, realRoster, customCandidate);
    }

    /** Plays as a player-made candidate. */
    public GameSetup withCustomCandidate(Candidate c) {
        return new GameSetup(seed, c.party(), c.id(), opponentCandidateId, scenario, difficulty, mode, realRoster, c);
    }

    public GameSetup withRealRoster(boolean on) {
        return new GameSetup(seed, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, mode, on, customCandidate);
    }

    public GameSetup withSeed(long s) {
        return new GameSetup(s, playerParty, playerCandidateId, opponentCandidateId, scenario, difficulty, mode, realRoster, customCandidate);
    }

    /**
     * The four games, on one engine (docs/MODES.md). Full Game and Governing Only share one presidency: the first
     * hundred days as its opening chapter, the 2030 midterms, re-election in 2032 and, if re-elected, a second term to
     * January 20, 2037.
     */
    public enum Mode {
        /** The 2028 campaign; a win goes on to the presidency, a loss ends on election night. */
        FULL_GAME,
        /** The 2028 campaign alone: the game ends after election night, won or lost, with the campaign's assessment. */
        CAMPAIGN_ONLY,
        /** The presidency alone: from election night, November 7, 2028, with the administration the player chose. */
        GOVERNING_ONLY,
        /** One crisis, Taiwan or Hormuz, on the presidency's own systems: it ends at the crisis's outcome, with an after-action report. */
        CRISIS;

        /** A saved game's mode: older saves named a length, and the hundred days' slice is now the opening of the full game. */
        public static Mode fromSave(String mode, String length) {
            if (mode != null) return valueOf(mode);
            return FULL_GAME;   // "HUNDRED_DAYS", "FULL_TERM" or nothing: the full game (docs/BUG_FIX_REPORT.md, E-08)
        }
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
