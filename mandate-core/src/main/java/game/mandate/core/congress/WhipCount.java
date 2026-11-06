package game.mandate.core.congress;

import java.util.List;

/**
 * The whip's read before a vote: expected yes votes per bloc in each chamber and what is needed.
 * Shown to the player before they commit, so a defeat is a gamble they chose, not a surprise.
 */
public record WhipCount(List<BlocRead> house, List<BlocRead> senate, int houseNeeded, int senateNeeded) {

    public record BlocRead(String bloc, int members, double expectedYes) {}

    public double houseExpected() { return house.stream().mapToDouble(BlocRead::expectedYes).sum(); }
    public double senateExpected() { return senate.stream().mapToDouble(BlocRead::expectedYes).sum(); }

    /** Rough pass probability for display (normal approximation of the vote noise). */
    public double passChance() {
        return chance(houseExpected(), houseNeeded, 4.0) * chance(senateExpected(), senateNeeded, 1.6);
    }

    private static double chance(double expected, int needed, double sd) {
        double z = (expected - (needed - 0.5)) / sd;
        return 1.0 / (1.0 + Math.exp(-1.7 * z));
    }
}
