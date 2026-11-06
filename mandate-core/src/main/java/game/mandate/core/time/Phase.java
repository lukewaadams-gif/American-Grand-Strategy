package game.mandate.core.time;

/**
 * The arc of one playthrough. Each phase has its own turn length and its own legal commands.
 * The order here is the order of play.
 */
public enum Phase {
    GENERAL_CAMPAIGN_2028(TurnLength.WEEK),
    TRANSITION(TurnLength.ONCE),
    GOVERNING(TurnLength.MONTH),
    REELECTION_CAMPAIGN_2032(TurnLength.WEEK),
    EPILOGUE(TurnLength.ONCE),
    GAME_OVER(TurnLength.ONCE);

    private final TurnLength turnLength;

    Phase(TurnLength turnLength) { this.turnLength = turnLength; }

    public TurnLength turnLength() { return turnLength; }

    public enum TurnLength { WEEK, MONTH, ONCE }
}
