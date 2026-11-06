package game.mandate.core.campaign;

import game.mandate.core.state.Party;

/** A presidential candidate. Ratings are 1-10. Ideology runs from -1 (left) to +1 (right). */
public record Candidate(String id, Party party, String name, String homeState, int charisma, int debate,
                        int fundraising, int policy, int discipline, double ideology) {

    /** Overall quality on a roughly -1..+1 scale centred on an average (5.5) candidate. */
    public double quality() {
        return ((charisma + debate + fundraising + policy + discipline) / 5.0 - 5.5) / 4.5;
    }

    /** Distance from the median voter; the general electorate punishes extremes. */
    public double extremity() { return Math.abs(ideology); }
}
