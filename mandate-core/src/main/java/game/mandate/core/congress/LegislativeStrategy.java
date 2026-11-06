package game.mandate.core.congress;

/** How the president tries to pass a bill. Each is a different trade-off, not a difficulty setting. */
public enum LegislativeStrategy {
    /** Full bill, own party only. Big payoff, fragile in a narrow or divided Congress. */
    PARTY_LINE("Party line", 1.0),
    /** Bill moved toward the centre: easier votes, smaller payoff, and your base notices. */
    BIPARTISAN_DEAL("Bipartisan deal", 0.6),
    /** Simple Senate majority, once a year, budget measures only, needs both chambers. */
    RECONCILIATION("Budget reconciliation", 0.85),
    /** No vote at all: weaker, and the courts may block it. */
    EXECUTIVE_ORDER("Executive order", 0.4);

    private final String label;
    private final double payoff;

    LegislativeStrategy(String label, double payoff) {
        this.label = label;
        this.payoff = payoff;
    }

    public String label() { return label; }

    /** Fraction of the bill's policy effect that survives this route. */
    public double payoff() { return payoff; }
}
