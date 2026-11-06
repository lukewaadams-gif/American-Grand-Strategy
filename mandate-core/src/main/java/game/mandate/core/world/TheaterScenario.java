package game.mandate.core.world;

/**
 * Everything a theater model needs from the presidential game when a war over Taiwan begins.
 * This record is the integration contract with the optional detailed wargame (the First Island
 * Chain engine, post-1.0): the presidential layer only ever produces this and consumes
 * {@link TheaterOutcome}, so the detailed engine can be swapped in without touching the core.
 *
 * @param deterrence      0-100, accumulated Pacific posture, alliances and readiness
 * @param munitions       0-100, long-range munitions stockpile relative to the 2027 baseline
 * @param japanBasing     whether Japan has agreed to US combat operations from its bases
 * @param warningMonths   months between the blockade and the shooting war (preparation time)
 * @param commitment      how far the president commits US forces
 */
public record TheaterScenario(double deterrence, double munitions, boolean japanBasing, int warningMonths,
                              Commitment commitment) {

    public enum Commitment {
        /** Full US intervention with air, naval and missile forces. */
        INTERVENE,
        /** Weapons, intelligence and sanctions; no US combat forces. */
        ARM_AND_SANCTION,
        /** Diplomatic protest only. */
        STAND_ASIDE
    }
}
