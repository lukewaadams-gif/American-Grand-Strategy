package game.mandate.core.campaign;

/**
 * What an election reads from the rest of the world, assembled by the game engine. For an open-seat election the
 * scenario sets the environment; for a re-election the incumbent's record does.
 *
 * @param scenarioEnvironment expected national margin (D minus R) before candidates, for an open seat
 * @param incumbent           whether the player's president is on the ballot
 * @param approval            national approval of the incumbent
 * @param unfinishedAgenda    agenda priorities not enacted
 * @param salience            how much each issue weighs with voters (the campaign's credibility on issues counts by it)
 */
public record ElectoralEnvironment(double scenarioEnvironment, boolean incumbent, double approval, double growth, double inflation, double unemployment,
                                   boolean taiwanFell, int unfinishedAgenda, java.util.Map<Issue, Double> salience) {
    public ElectoralEnvironment {
        // an EnumMap, never Map.copyOf: sums over a map must run in a fixed order or results differ in the last bit between runs
        salience = java.util.Collections.unmodifiableMap(salience == null ? CampaignEngine.baseSalience() : new java.util.EnumMap<>(salience));
    }
}
