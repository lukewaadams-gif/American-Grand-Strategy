package game.mandate.core.opening;

import game.mandate.core.state.Party;

/**
 * One bloc's position in one chamber, with what would move it. The "wants" line is the information that
 * turns the whip count from a number into a plan (RD: "each swing vote wants something different").
 */
public record WhipRead(String bloc, Party party, String chamber, int members, double expectedYes, double relationship, String wants) {
    public double share() { return members == 0 ? 0 : expectedYes / members; }
}
