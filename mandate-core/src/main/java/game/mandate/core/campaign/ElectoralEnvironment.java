package game.mandate.core.campaign;

/**
 * What an election reads from the rest of the world, assembled by the game engine. For an open-seat election the
 * scenario sets the environment; for a re-election the incumbent's record does.
 *
 * @param scenarioEnvironment expected national margin (D minus R) before candidates, for an open seat
 * @param incumbent           whether the player's president is on the ballot
 * @param approval            national approval of the incumbent
 * @param unfinishedAgenda    agenda priorities not enacted
 */
public record ElectoralEnvironment(double scenarioEnvironment, boolean incumbent, double approval, double growth, double inflation,
                                   boolean taiwanFell, int unfinishedAgenda) {}
