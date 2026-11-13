package game.mandate.core.taiwan;

/**
 * How a Taiwan crisis starts (docs/ISLAND_CHAIN.md). The presidency fills it from its own state (the inputs mirror
 * {@code military/TheaterScenario}); the standalone scenario uses {@link #standalone}.
 *
 * @param year              the crisis year, 2029-2036: forces are projected to it
 * @param preset            0 base case, 1 optimistic, 2 pessimistic (the source's assumption presets)
 * @param side              "US" or "PRC": the side the player commands; the other is the computer's
 * @param plan              China's course of action if already fixed ("invasion", "blockade", "quarantine", "islands",
 *                          "strikes"), or null to let the PRC planner choose (including not to go to war)
 * @param deterrence        0-100, the presidency's Pacific deterrence (50 is neutral)
 * @param munitions         the US munition stocks as a fraction of the year's projection (1 = as projected); the presidency
 *                          passes {@code munitionsIndex / 100 / TaiwanData.usAntiShipProjection(year)}
 * @param japanBasing       whether Japan allows US combat operations from its bases
 * @param warningMonths     months of warning before the crisis: US preparation programmes start that far along
 * @param commitment        how far the president commits US forces
 * @param defenseCompetence 0-100, the competence of the Defense secretary: the US staff's skill
 * @param assumptions       custom toggles over the preset ("key=value" pairs joined with ';'), or null; see docs/ISLAND_CHAIN.md
 */
public record TaiwanScenario(int year, int preset, String side, String plan, double deterrence, double munitions, boolean japanBasing,
                             int warningMonths, Commitment commitment, double defenseCompetence, String assumptions) {

    /** How far the president commits US forces (mirrors {@code TheaterScenario.Commitment}). */
    public enum Commitment {
        /** Full US intervention: the rules of engagement are the president's to set. */
        INTERVENE,
        /** Weapons, intelligence and sanctions, presence and escort (rung 1 at most); no US combat. */
        ARM_AND_SANCTION,
        /** Diplomatic protest only (rung 0). */
        STAND_ASIDE
    }

    public TaiwanScenario {
        if (year < 2029 || year > 2036) throw new IllegalArgumentException("The crisis year must be 2029-2036");
        if (preset < 0 || preset > 2) throw new IllegalArgumentException("The preset must be 0, 1 or 2");
        if (!"US".equals(side) && !"PRC".equals(side)) throw new IllegalArgumentException("The side must be US or PRC");
        if (plan != null && TaiwanEngine.planIndex(plan) < 0) throw new IllegalArgumentException("No such plan: " + plan);
        if (commitment == null) commitment = Commitment.INTERVENE;
    }

    /** The standalone scenario: neutral deterrence, the year's projected munitions, Japan's basing as the preset says. */
    public static TaiwanScenario standalone(int year, int preset, String side) {
        return new TaiwanScenario(year, preset, side, null, 50, 1, true, 0, Commitment.INTERVENE, 55, null);
    }

    public TaiwanScenario withPlan(String p) {
        return new TaiwanScenario(year, preset, side, p, deterrence, munitions, japanBasing, warningMonths, commitment, defenseCompetence, assumptions);
    }

    public TaiwanScenario withAssumptions(String a) {
        return new TaiwanScenario(year, preset, side, plan, deterrence, munitions, japanBasing, warningMonths, commitment, defenseCompetence, a);
    }
}
