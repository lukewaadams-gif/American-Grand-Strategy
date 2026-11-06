package game.mandate.core.economy;

/**
 * What the economy reads from other systems for one step. The caller (the game engine or a scenario)
 * assembles it from the world state; the economy never reaches into other systems itself.
 *
 * @param oilPremium         crisis risk premium on crude, dollars (from diplomacy)
 * @param pendingDeficit     deficit of a live bill weighted by its odds, % GDP (from Congress)
 * @param treasuryCompetence competence of the Treasury secretary, 0-100 (from the executive)
 * @param stress             confidence drag from shutdowns, crises, wars (index points)
 */
public record MarketContext(double oilPremium, double pendingDeficit, double treasuryCompetence, double stress) {
    public static MarketContext calm() { return new MarketContext(0, 0, 70, 0); }
}
