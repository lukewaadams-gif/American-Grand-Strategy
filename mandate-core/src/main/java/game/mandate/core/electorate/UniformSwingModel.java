package game.mandate.core.electorate;

/** Baseline plus elastic national swing plus local effects. Deliberately simple and transparent. */
public final class UniformSwingModel implements ElectorateModel {
    @Override
    public double expectedMargin(UsState state, double nationalSwing, double localShift) {
        return state.baselineMargin() + nationalSwing * state.elasticity() + localShift;
    }
}
