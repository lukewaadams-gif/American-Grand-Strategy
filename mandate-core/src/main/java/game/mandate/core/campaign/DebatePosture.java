package game.mandate.core.campaign;

/**
 * Debate approach. Each draws on a different rating and has a different risk profile, and each
 * counters one other posture: ATTACK beats GUARDED, GUARDED beats POLICY, POLICY beats RELATABLE,
 * RELATABLE beats ATTACK. Opponents lean toward their best rating, which debate prep reveals,
 * so the choice is a read of the opponent, not a guess.
 */
public enum DebatePosture {
    ATTACK("Go on the attack", 1.6),
    POLICY("Win on substance", 0.9),
    RELATABLE("Talk to people at home", 1.1),
    GUARDED("Stay safe, avoid mistakes", 0.5);

    private final String label;
    private final double volatility;

    DebatePosture(String label, double volatility) {
        this.label = label;
        this.volatility = volatility;
    }

    public String label() { return label; }
    public double volatility() { return volatility; }

    public boolean counters(DebatePosture other) {
        return switch (this) {
            case ATTACK -> other == GUARDED;
            case GUARDED -> other == POLICY;
            case POLICY -> other == RELATABLE;
            case RELATABLE -> other == ATTACK;
        };
    }

    public int rating(Candidate c) {
        return switch (this) {
            case ATTACK -> c.debate();
            case POLICY -> c.policy();
            case RELATABLE -> c.charisma();
            case GUARDED -> c.discipline();
        };
    }

    /** The posture this candidate naturally favours (what debate prep reveals). */
    public static DebatePosture tendencyOf(Candidate c) {
        DebatePosture best = GUARDED;
        for (DebatePosture p : values()) if (p.rating(c) > best.rating(c)) best = p;
        return best;
    }
}
