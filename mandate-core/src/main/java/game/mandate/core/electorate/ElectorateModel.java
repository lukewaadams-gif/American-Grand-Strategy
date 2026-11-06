package game.mandate.core.electorate;

/**
 * Turns a national environment plus local campaign effects into a state's expected margin.
 *
 * <p>This is the single seam between the game and its voter model. The foundation ships
 * {@link UniformSwingModel}. Milestone M1 adds a port of the Battleground 2028 96-cell model behind
 * this interface; presidential races, Senate races, midterms and approval-by-state all call it, so the
 * game has exactly one electorate (the source games had two copies of it).
 */
public interface ElectorateModel {

    /**
     * @param nationalSwing change in the national margin versus 2024, D minus R, in points
     * @param localShift    campaign effects specific to this state, D minus R, in points
     * @return expected two-party margin in the state, D minus R, in points
     */
    double expectedMargin(UsState state, double nationalSwing, double localShift);
}
