package game.mandate.core.electorate;

import game.mandate.core.rng.DeterministicRandomManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Resolves an election from expected margins with correlated error: a national error every state
 * shares, a regional-style error scaled by elasticity, and independent state noise. Correlated error
 * is what makes election night tense and the forecast honest: a 3-point lead in five states is not
 * five independent coin flips.
 */
public final class ElectionCalculator {
    private final StateCatalog states;
    private final ElectorateModel model;
    private final double nationalErrorSd, stateErrorSd;

    /** Error standard deviations are in points, roughly the size of historical polling misses (tuning: election.*ErrorSd). */
    public ElectionCalculator(StateCatalog states, ElectorateModel model, double nationalErrorSd, double stateErrorSd) {
        this.states = states;
        this.model = model;
        this.nationalErrorSd = nationalErrorSd;
        this.stateErrorSd = stateErrorSd;
    }

    /** Expected (error-free) margins: what a perfect poll would show today. */
    public Map<String, Double> expectedMargins(double nationalSwing, ToDoubleFunction<String> localShift) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (UsState s : states.all()) m.put(s.code(), model.expectedMargin(s, nationalSwing, localShift.applyAsDouble(s.code())));
        return m;
    }

    /** Draws a realised result. {@code systematicError} is an extra national error every state shares (the game passes 0: its polling miss lives in the polls). */
    public ElectionResult resolve(int year, double nationalSwing, ToDoubleFunction<String> localShift,
                                  double systematicError, DeterministicRandomManager rng, String stream) {
        double nationalNoise = rng.gaussian(stream) * nationalErrorSd + systematicError;
        Map<String, Double> realised = new LinkedHashMap<>();
        for (UsState s : states.all()) {
            double expected = model.expectedMargin(s, nationalSwing, localShift.applyAsDouble(s.code()));
            realised.put(s.code(), expected + nationalNoise * s.elasticity() + rng.gaussian(stream) * stateErrorSd);
        }
        return tally(year, realised);
    }

    public ElectionResult tally(int year, Map<String, Double> margins) {
        int dem = 0, rep = 0;
        double weighted = 0, weights = 0;
        for (UsState s : states.all()) {
            double m = margins.get(s.code());
            if (m >= 0) dem += s.electoralVotes(); else rep += s.electoralVotes();
            weighted += m * s.voteWeight();
            weights += s.voteWeight();
        }
        return new ElectionResult(year, java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(margins)), dem, rep, weighted / weights, tippingPoint(margins, dem >= rep));
    }

    /** Sort the winner's states from safest to closest and find the one that crosses 270. */
    private String tippingPoint(Map<String, Double> margins, boolean demWon) {
        List<UsState> won = new ArrayList<>();
        for (UsState s : states.all()) if ((margins.get(s.code()) >= 0) == demWon) won.add(s);
        int sign = demWon ? 1 : -1;
        won.sort(Comparator.comparingDouble((UsState s) -> margins.get(s.code()) * sign).reversed());
        int ev = 0;
        for (UsState s : won) {
            ev += s.electoralVotes();
            if (ev >= 270) return s.code();
        }
        return won.isEmpty() ? "-" : won.get(won.size() - 1).code();
    }
}
