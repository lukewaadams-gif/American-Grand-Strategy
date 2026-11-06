package game.mandate.core.opening;

import game.mandate.core.state.Party;

/**
 * @param unified whether the president's party holds both chambers (narrowly) at the start
 */
public record OpeningSetup(long seed, Party party, boolean unified) {}
