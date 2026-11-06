package game.mandate.core.campaign;

import game.mandate.core.rng.RngStreams;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the player is allowed to see. Polls are the truth plus a hidden systematic miss (fixed at the
 * start of each election, revealed on election night) plus sampling noise. Players learn to read
 * polls skeptically, which is the point.
 */
public final class Polling {
    private Polling() {}

    public static Map<String, Double> statePolls(Map<String, Double> expected, double systematicMiss, RngStreams rng) {
        Map<String, Double> polls = new LinkedHashMap<>();
        expected.forEach((s, m) -> polls.put(s, m - systematicMiss + 1.8 * rng.gaussian("polls")));
        return polls;
    }

    public static double nationalPoll(double expectedNational, double systematicMiss, RngStreams rng) {
        return expectedNational - systematicMiss + 1.0 * rng.gaussian("polls");
    }
}
