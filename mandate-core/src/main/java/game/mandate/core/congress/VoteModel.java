package game.mandate.core.congress;

import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.state.Party;

/**
 * The single authority for how members of Congress vote, on bills and on nominations. Used by the main game's
 * {@link CongressEngine} and by the 12-week prototype; neither keeps a formula of its own.
 *
 * <p>Bill support (per bloc) = ideological fit + party pull (loyalty, wing mood, the opposition's strategy,
 * approval) + relationship + deficit-hawk penalty + public pressure on moderates + capital spent at the margin
 * + a one-vote threat bonus. Merged from Resolute Desk's {@code blocLogit}, the foundation's whip model and the
 * prototype's relationship/mood terms.
 */
public final class VoteModel {
    private final VoteParams p;

    public VoteModel(VoteParams p) { this.p = p; }

    public VoteParams params() { return p; }

    /**
     * Everything one bloc weighs.
     * @param wingMood    mood (0-100) of the president's-party wing this bloc belongs to; ignored for the other party
     * @param threatBonus one-vote bonus from a primary threat (prototype); 0 otherwise
     * @param opposingAgenda the bill belongs to the other party's agenda: the president's own members resist it
     */
    public record BillInputs(Party president, double ideology, double deficit, boolean bipartisan, OppositionStrategy opposition,
                             double approval, double swingApproval, double publicPressure, double capital, double wingMood,
                             double relationship, double threatBonus, boolean opposingAgenda) {}

    public double support(Bloc b, BillInputs in) {
        double fit = 1.0 - p.slope() * Math.abs(in.ideology() - b.ideology());
        double party;
        if (b.party() == in.president()) {
            party = p.ownPull() * b.loyalty() * (0.5 + in.wingMood() / 100.0) * (in.opposingAgenda() ? p.opposingAgendaPull() : 1.0)
                    + p.approvalOwn() * (in.approval() - p.approvalOwnPivot());
        } else {
            double pull = switch (in.opposition()) {
                case COOPERATE -> p.oppPullCooperate();
                case SELECTIVE -> p.oppPullSelective();
                case OBSTRUCT -> p.oppPullObstruct();
            };
            party = b.loyalty() * pull + (in.bipartisan() ? p.bipartisanRelief() : 0) + p.approvalOpp() * (in.approval() - p.approvalOppPivot());
        }
        double rel = p.relationshipWeight() * in.relationship();
        double hawk = b.deficitHawk() && in.deficit() > p.hawkThreshold() ? -p.hawkPenalty() * in.deficit() : 0;
        double moderate = Math.abs(b.ideology()) < p.moderateBand()
                ? p.pressureWeight() * in.publicPressure() * Math.max(0, (in.swingApproval() - p.pressureSwingPivot()) / p.pressureSwingScale()) : 0;
        double base = fit + party + rel + hawk + moderate + in.threatBonus();
        double edge = 1.0 - Math.abs(base - 0.5) * 2;
        double bought = Math.max(0, edge) * in.capital() * p.capitalWeight();
        return clamp01(base + bought);
    }

    /** Pass probability of an expected count (normal approximation of the floor noise). */
    public double passChance(double expected, int needed, boolean house) {
        double sd = house ? p.houseNoise() : p.senateNoise();
        double z = (expected - (needed - 0.5)) / sd;
        return 1.0 / (1.0 + Math.exp(-1.7 * z));
    }

    public double noise(boolean house) { return house ? p.houseNoise() : p.senateNoise(); }

    /** Bill ideology after the route: a bipartisan deal moves it toward the centre. */
    public double effectiveIdeology(double ideology, LegislativeStrategy s) {
        return s == LegislativeStrategy.BIPARTISAN_DEAL ? ideology * p.dealIdeologyFactor() : ideology;
    }

    /**
     * Expected Senate yes votes for a nominee (prototype model): own-party senators back routine picks, with
     * defections growing with controversy above a threshold; the other party follows its leaders' strategy,
     * except for crossover picks.
     */
    public double confirmationExpected(Party president, Congress congress, double controversy, boolean crossover,
                                       OppositionStrategy opposition, double moderateWingMood) {
        double total = 0;
        for (Party side : Party.values()) {
            for (Bloc b : Bloc.defaultsFor(side)) {
                double moderation = 1.0 - Math.min(1, Math.abs(b.ideology()) / 0.8);
                double s;
                if (side == president) {
                    s = p.confirmBase() - Math.max(0, controversy - p.confirmControversyThreshold()) * (p.confirmControversyBase() + p.confirmControversyModerate() * moderation)
                            - (moderateWingMood < p.confirmMoodLine() ? p.confirmMoodPenalty() * moderation : 0);
                } else {
                    double factor = switch (opposition) {
                        case COOPERATE -> p.confirmOppCooperate();
                        case SELECTIVE -> p.confirmOppSelective();
                        case OBSTRUCT -> p.confirmOppObstruct();
                    };
                    double base = crossover ? p.confirmCrossover() : (p.confirmOppBase() + p.confirmOppSpread() * (1 - controversy)) * factor;
                    s = base * (p.confirmOppModerateFloor() + (1 - p.confirmOppModerateFloor()) * moderation);
                }
                total += congress.senate(side) * b.share() * clamp01(s);
            }
        }
        return total;
    }

    public double confirmationNoise() { return p.confirmNoise(); }

    static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
