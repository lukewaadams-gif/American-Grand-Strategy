package game.mandate.core.world;

import game.mandate.core.rng.RngStreams;
import game.mandate.core.world.TheaterOutcome.TaiwanStatus;

/**
 * A one-roll model whose odds respond to the presidential decisions that came before the war:
 * deterrence, munitions, basing, warning time and the level of commitment. (In Resolute Desk, the
 * war odds ignored deterrence and posture entirely; fixing that is the reason to keep this layer.)
 *
 * <p>The coefficients are placeholders. Milestone M5 calibrates them by running the First Island
 * Chain engine headless across a grid of scenarios and fitting these curves to its outcomes.
 */
public final class AbstractTheaterResolver implements TheaterResolver {

    @Override
    public TheaterOutcome resolve(TheaterScenario s, RngStreams rng) {
        double strength = (s.deterrence() - 50) * 0.035 + (s.munitions() - 50) * 0.02
                + (s.japanBasing() ? 0.6 : -0.6) + Math.min(3, s.warningMonths()) * 0.15;
        double commitBonus = switch (s.commitment()) {
            case INTERVENE -> 1.2;
            case ARM_AND_SANCTION -> -0.6;
            case STAND_ASIDE -> -2.0;
        };
        double z = strength + commitBonus + 0.9 * rng.gaussian("theater");
        TaiwanStatus status = z > 0.5 ? TaiwanStatus.DEFENDED : z > -0.6 ? TaiwanStatus.STALEMATE : TaiwanStatus.FALLEN;

        boolean fighting = s.commitment() == TheaterScenario.Commitment.INTERVENE;
        int weeks = fighting ? 4 + (int) Math.round(Math.abs(rng.gaussian("theater")) * 6) : 0;
        int killed = fighting ? (int) Math.round((2500 + 4000 * rng.uniform("theater")) * (status == TaiwanStatus.FALLEN ? 1.4 : 1.0)) : 0;
        double growth = switch (status) { case DEFENDED -> -1.5; case STALEMATE -> -2.5; case FALLEN -> -3.5; };
        double inflation = switch (status) { case DEFENDED -> 1.0; case STALEMATE -> 1.8; case FALLEN -> 2.5; };
        double approval = switch (status) {
            case DEFENDED -> fighting ? 6 : 2;
            case STALEMATE -> fighting ? -3 : -4;
            case FALLEN -> fighting ? -10 : -7;
        };
        double deterrenceAfter = switch (status) { case DEFENDED -> 75; case STALEMATE -> 45; case FALLEN -> 15; };
        String summary = switch (status) {
            case DEFENDED -> "The invasion fails. Taiwan holds, at a terrible price.";
            case STALEMATE -> "The fighting settles into an armed standoff around a blockaded Taiwan.";
            case FALLEN -> "Taiwan's government accepts Beijing's terms. Allies question American guarantees.";
        };
        return new TheaterOutcome(status, weeks, killed, growth, inflation, approval, deterrenceAfter, summary);
    }
}
