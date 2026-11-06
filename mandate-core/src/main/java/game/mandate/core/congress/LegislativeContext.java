package game.mandate.core.congress;

import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.state.Party;

/**
 * What the legislative process reads from the rest of the world for one bill. Assembled by the game engine from
 * the president, public opinion and the party; Congress never reaches into those systems' state itself.
 *
 * @param wingMood moods of the president's party wings [base, moderate]
 */
public record LegislativeContext(Party president, double approval, double swingApproval, OppositionStrategy opposition,
                                 double[] wingMood, boolean agendaItem) {}
