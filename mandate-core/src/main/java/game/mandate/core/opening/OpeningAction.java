package game.mandate.core.opening;

import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.NomineeType;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.Types.Post;

/**
 * Player commands in the prototype. As in the main game, the state changes only through these, so a
 * session is its setup plus its action log, and scripted strategies use exactly the player's API.
 * Attention costs are listed in {@link OpeningTerm#cost(OpeningAction)}.
 */
public sealed interface OpeningAction {
    record DraftBill(BillSize size, PayFor payFor) implements OpeningAction {}
    record AmendBill(BillSize size, PayFor payFor) implements OpeningAction {}
    record Negotiate(String bloc, Concession concession) implements OpeningAction {}
    record GoPublic() implements OpeningAction {}
    record CallVote(boolean insist) implements OpeningAction {}
    record UseReconciliation() implements OpeningAction {}
    record Nominate(Post post, NomineeType type) implements OpeningAction {}
    record MeetOpposition() implements OpeningAction {}
    record LeanOnFed() implements OpeningAction {}
    record ReleaseOilReserve() implements OpeningAction {}
    record Delegate(Domain domain, boolean on) implements OpeningAction {}
    record Decide(int decisionId, int option) implements OpeningAction {}
    record EndWeek() implements OpeningAction {}
}
