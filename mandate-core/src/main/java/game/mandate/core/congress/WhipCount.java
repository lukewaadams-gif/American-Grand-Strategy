package game.mandate.core.congress;

import java.util.List;

/**
 * The whip's read before a vote: expected yes votes per bloc in each chamber, what is needed, and the pass
 * probability as computed by the {@link VoteModel}. Shown to the player before they commit, so a defeat is a
 * gamble they chose, not a surprise.
 */
public record WhipCount(List<BlocRead> house, List<BlocRead> senate, int houseNeeded, int senateNeeded, double passChance) {

    public record BlocRead(String bloc, int members, double expectedYes) {}

    public WhipCount {
        house = List.copyOf(house);
        senate = List.copyOf(senate);
    }

    public double houseExpected() { return house.stream().mapToDouble(BlocRead::expectedYes).sum(); }
    public double senateExpected() { return senate.stream().mapToDouble(BlocRead::expectedYes).sum(); }
}
