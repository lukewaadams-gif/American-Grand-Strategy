package game.mandate.core.congress;

import game.mandate.core.campaign.Issue;
import game.mandate.core.state.Party;

import java.util.Optional;

/**
 * A piece of legislation the player can push.
 *
 * @param sponsor     the party whose agenda it belongs to; empty = bipartisan
 * @param ideology    -1 (left) .. +1 (right)
 * @param publicAppeal net approval effect if signed (-5..+5)
 * @param requires60  cannot go through reconciliation (Byrd rule)
 */
public record Bill(String id, String name, Optional<Party> sponsor, double ideology, Issue issue, double annualCostBn,
                   double publicAppeal, double growthImpulse, double inflationImpulse, double deficitPctGdp,
                   boolean requires60) {

    public boolean bipartisan() { return sponsor.isEmpty(); }

    /** Whether a president of {@code party} can credibly champion this bill. */
    public boolean availableTo(Party party) { return sponsor.isEmpty() || sponsor.get() == party; }
}
