package game.mandate.core.campaign;

import game.mandate.core.state.Party;

/** A presidential candidate. Ratings are 1-10. Ideology runs from -1 (left) to +1 (right). */
public record Candidate(String id, Party party, String name, String homeState, int charisma, int debate,
                        int fundraising, int policy, int discipline, double ideology) {

    /** Id prefix of Battleground 2028's real-person roster (opt-in, not for commercial release: RISK_REGISTER R-02). */
    public static final String REAL_PREFIX = "bg_";
    /** Id of a player-made candidate. */
    public static final String CUSTOM_ID = "custom";
    /** The custom candidate's point budget across the five ratings: about the fictional cast's average (33). */
    public static final int POINT_BUDGET = 33;
    public static final int MIN_RATING = 2, MAX_RATING = 9;

    /** Overall quality on a roughly -1..+1 scale centred on an average (5.5) candidate. */
    public double quality() {
        return ((charisma + debate + fundraising + policy + discipline) / 5.0 - 5.5) / 4.5;
    }

    /** Distance from the median voter; the general electorate punishes extremes. */
    public double extremity() { return Math.abs(ideology); }

    /** A real person (the opt-in roster): the game never invents personal scandals about them. */
    public boolean isReal() { return id.startsWith(REAL_PREFIX); }

    public int ratingPoints() { return charisma + debate + fundraising + policy + discipline; }

    /**
     * A player-made candidate. Ratings are bought from {@link #POINT_BUDGET} points, each between {@link #MIN_RATING}
     * and {@link #MAX_RATING}; ideology must sit on the candidate's own side of the centre (at most 0.2 across it).
     */
    public static Candidate custom(Party party, String name, String homeState, int charisma, int debate, int fundraising, int policy,
                                   int discipline, double ideology) {
        Candidate c = new Candidate(CUSTOM_ID, party, name, homeState, charisma, debate, fundraising, policy, discipline, ideology);
        String why = c.customRefusal();
        if (why != null) throw new IllegalArgumentException(why);
        return c;
    }

    /** Why this would not be a legal custom candidate, or null. */
    public String customRefusal() {
        if (name == null || name.isBlank() || name.length() > 40) return "Give the candidate a name of up to 40 characters";
        for (int r : new int[]{charisma, debate, fundraising, policy, discipline})
            if (r < MIN_RATING || r > MAX_RATING) return "Each rating must be between " + MIN_RATING + " and " + MAX_RATING;
        if (ratingPoints() > POINT_BUDGET) return "The ratings cost " + ratingPoints() + " points; the budget is " + POINT_BUDGET;
        if (ideology * party.sign() > 0.2 || Math.abs(ideology) > 0.9) return "Ideology must sit on your party's side (at most 0.2 across the centre)";
        return null;
    }
}
