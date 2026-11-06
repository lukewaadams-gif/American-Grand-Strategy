package game.mandate.core.congress;

import game.mandate.core.state.Party;

/**
 * An ideological bloc within a party caucus. Three per party per chamber, as in Resolute Desk:
 * enough to make the whip count a puzzle, few enough to read at a glance.
 *
 * @param share    fraction of the party's caucus
 * @param loyalty  0..1, how much party and president pull against ideology
 * @param deficitHawk whether deficit-increasing bills cost support
 */
public record Bloc(String name, Party party, double ideology, double share, double loyalty, boolean deficitHawk) {

    public static Bloc[] defaultsFor(Party p) {
        if (p == Party.DEMOCRAT) return new Bloc[]{
                new Bloc("Progressives", p, -0.70, 0.35, 0.85, false),
                new Bloc("Mainstream Democrats", p, -0.40, 0.45, 0.92, false),
                new Bloc("Moderate Democrats", p, -0.12, 0.20, 0.70, true)};
        return new Bloc[]{
                new Bloc("Moderate Republicans", p, 0.20, 0.15, 0.70, false),
                new Bloc("Mainstream Republicans", p, 0.50, 0.50, 0.90, true),
                new Bloc("Freedom Caucus", p, 0.85, 0.35, 0.75, true)};
    }
}
