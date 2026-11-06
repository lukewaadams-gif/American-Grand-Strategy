package game.mandate.core.opening;

import game.mandate.core.opening.Decision.Kind;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Types.SeatStatus;
import game.mandate.core.state.Party;

import java.util.Set;

/**
 * Delegation for casual play (RD: "hand off planning your week, working the votes, writing the budget...").
 * Delegated work costs no presidential attention, but the staff is cautious and generic: it negotiates at
 * 50% effectiveness, never threatens or goes public, always nominates experts, and holds in a crisis.
 * That is the trade-off: time back, at the price of control and of the bolder paths.
 */
public final class ChiefOfStaff {
    static final double EFFICIENCY = 0.5;

    private ChiefOfStaff() {}

    public static boolean handles(Kind kind, Set<Domain> delegated) {
        return switch (kind) {
            case CRISIS, AFTERMATH -> delegated.contains(Domain.CRISIS);
            case FUNDING, SHUTDOWN -> delegated.contains(Domain.BUDGET);
            case BILL_FAILED, BOTTLED_IN_COMMITTEE, FACTION_REVOLT -> delegated.contains(Domain.LEGISLATION);
            case NOMINEE_REJECTED -> delegated.contains(Domain.CABINET);
            case LEAK -> true;
        };
    }

    static void act(OpeningTerm t, Set<Domain> delegated) {
        settle(t, delegated);
        if (delegated.contains(Domain.CABINET)) {
            for (Post p : Post.values()) {
                if (t.seat(p) == SeatStatus.VACANT) t.nominate(p, NomineeType.EXPERT);
            }
        }
        if (delegated.contains(Domain.LEGISLATION)) legislate(t);
        settle(t, delegated); // a failed vote raises a follow-up the staff must also answer
    }

    private static void settle(OpeningTerm t, Set<Domain> delegated) {
        for (Decision d : t.pending()) {
            if (!handles(d.kind(), delegated)) continue;
            int option = switch (d.kind()) {
                case CRISIS -> t.escalation() >= 3 ? 0 : 1;
                case FUNDING -> t.opposition() == OppositionStrategy.OBSTRUCT ? 3 : 0;
                case SHUTDOWN -> 1;
                case BILL_FAILED -> 0;
                case BOTTLED_IN_COMMITTEE -> 1;
                case FACTION_REVOLT -> t.capital() >= 5 ? 0 : 1;
                case NOMINEE_REJECTED -> 0;
                case AFTERMATH -> 1;
                case LEAK -> 0;
            };
            t.decide(d.id(), option);
        }
    }

    private static void legislate(OpeningTerm t) {
        if (t.stage() == BillStage.NOT_DRAFTED) t.draft(BillSize.MEDIUM, t.party() == Party.DEMOCRAT ? PayFor.REVENUE : PayFor.CUTS);
        if (t.billActedThisWeek() || !t.pending().isEmpty()) return;
        BillStage s = t.stage();
        if (s == BillStage.COMMITTEE) { t.callVote(false); return; }
        if (s != BillStage.HOUSE_FLOOR && s != BillStage.SENATE_FLOOR) return;
        if (s == BillStage.SENATE_FLOOR && t.passChance(s) < 0.3 && t.canReconcile()) t.useReconciliation();
        if (t.passChance(s) >= 0.6) { t.callVote(false); return; }
        if (t.capital() >= 10) {
            WhipRead best = bestTarget(t, s == BillStage.HOUSE_FLOOR);
            if (best != null) t.negotiate(best.bloc(), Concession.PROJECT, EFFICIENCY);
        }
    }

    /** The bloc where a concession buys the most votes: many members, persuadable, not hopeless. */
    static WhipRead bestTarget(OpeningTerm t, boolean house) {
        WhipRead best = null;
        double bestGain = 0;
        for (WhipRead w : t.whip(house)) {
            if (w.relationship() >= 0.95) continue;
            double s = w.share();
            if (s > 0.95 || s < 0.05) continue;
            double gain = w.members() * Math.min(1 - s, 0.09);
            if (gain > bestGain) { bestGain = gain; best = w; }
        }
        return best;
    }
}
