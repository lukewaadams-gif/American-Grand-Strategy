package game.mandate.core.opening;

import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.executive.Post;
import game.mandate.core.executive.GovernmentState.SeatStatus;
import game.mandate.core.state.Party;

import java.util.List;

/**
 * Scripted player archetypes. They are probes, not AI opponents: each plays one coherent philosophy
 * through the public action API, so comparing their outcomes tests whether the design rewards different
 * paths differently. They make no claim about how real players behave.
 */
public enum Strategy {
    /** Big bill on party terms, loyalists, pressure, reconciliation, hard line on the budget. */
    PARTISAN,
    /** Mid-sized bill moved to the centre, crossover picks, outreach, concessions, diplomacy. */
    DEALMAKER,
    /** Security first: experts, a small fiscally tight bill, force in a crisis, defense money. */
    HAWK,
    /** Economy first: experts, a paid-for bill, Fed independence, oil-reserve relief, cautious abroad. */
    TECHNOCRAT,
    /** Casual play: the chief of staff runs everything and the player only ends the week. */
    DELEGATOR,
    /** Answers what it must and does nothing else. The floor every real strategy should beat somewhere. */
    IDLE,
    /**
     * An approximation of an attentive expert: it reads the information the game shows (intelligence on
     * resolve, the shape of Congress, the opposition's mood) and revises its plan. It exists to test that
     * the information on screen is worth reading.
     */
    STRATEGIST;

    public static OpeningTerm play(OpeningSetup setup, Strategy s) {
        OpeningTerm t = new OpeningTerm(setup);
        while (!t.over()) s.playWeek(t);
        return t;
    }

    public void playWeek(HundredDays t) {
        if (this == DELEGATOR && t.week() == 1) for (Domain d : Domain.values()) t.act(new OpeningAction.Delegate(d, true));
        answerDecisions(t);
        switch (this) {
            case PARTISAN -> partisan(t);
            case DEALMAKER -> dealmaker(t);
            case HAWK -> hawk(t);
            case TECHNOCRAT -> technocrat(t);
            case DELEGATOR -> delegator(t);
            case IDLE -> { }
            case STRATEGIST -> strategist(t);
        }
        answerDecisions(t); // actions this week can raise follow-ups (a failed vote)
        t.act(new OpeningAction.EndWeek());
    }

    // ---- decisions ---------------------------------------------------------------------------------

    private void answerDecisions(HundredDays t) {
        for (Decision d : t.pending()) {
            if (ChiefOfStaff.handles(d.kind(), t.delegated())) continue;
            t.act(new OpeningAction.Decide(d.id(), choose(t, d)));
        }
    }

    private int choose(HundredDays t, Decision d) {
        return switch (d.kind()) {
            case CRISIS -> switch (this) {
                case PARTISAN -> 2;                                      // show of force
                case DEALMAKER -> 0;                                     // diplomacy
                case HAWK -> t.escalation() >= 2 ? 3 : 2;                // force, then a strike
                case TECHNOCRAT -> t.escalation() >= 2 ? 0 : 1;          // hold, then diplomacy
                case STRATEGIST -> t.intelEstimate() < 0.5 ? 2 : t.intelEstimate() > 0.65 ? 0 : 1;   // deter the weak-willed, talk to the determined
                default -> 1;
            };
            case FUNDING -> switch (this) {
                case PARTISAN -> 1;
                case DEALMAKER, TECHNOCRAT -> t.opposition() == OppositionStrategy.OBSTRUCT ? 2 : 0;
                case HAWK -> 3;
                case STRATEGIST -> t.opposition() == OppositionStrategy.OBSTRUCT ? 2 : t.opposition() == OppositionStrategy.COOPERATE ? 3 : 0;
                default -> 0;
            };
            case SHUTDOWN -> this == PARTISAN || this == HAWK || this == IDLE || (this == STRATEGIST && t.report().swingApproval() > 52) ? 0 : 1;
            case BILL_FAILED -> {
                if (this == PARTISAN) yield d.options().size() > 3 ? 3 : 1;
                yield 0;
            }
            case BOTTLED_IN_COMMITTEE -> this == PARTISAN && t.capital() >= 10 ? 0 : this == IDLE ? 2 : 1;
            case NOMINEE_REJECTED -> this == DEALMAKER ? 1 : 0;
            case FACTION_REVOLT -> (this == HAWK || this == TECHNOCRAT) ? 1 : (t.capital() >= 5 ? 0 : 1);
            case AFTERMATH -> switch (this) { case PARTISAN, HAWK -> 0; case STRATEGIST, DEALMAKER, TECHNOCRAT -> 1; default -> t.capital() >= 6 ? 2 : 1; };
            case ECONOMY -> economicResponse(t);
            case LEAK -> 0;
        };
    }

    /** Each archetype's answer to the economic challenge (options are listed in {@link HundredDays}). */
    private int economicResponse(HundredDays t) {
        boolean inflation = t.challenge() == Types.EconomicChallenge.INFLATION_SURGE;
        return switch (this) {
            case PARTISAN -> inflation ? 3 : 0;                                 // lean on the Fed / stimulus
            case DEALMAKER -> inflation ? 2 : 1;                                // tariff and gas-tax relief / targeted aid
            case HAWK -> inflation ? 0 : 2;                                     // fiscal discipline
            case TECHNOCRAT -> inflation ? 0 : 1;                               // back the Fed / targeted aid
            case STRATEGIST -> inflation ? (t.inflation() > 4.0 ? 0 : 2) : (t.unemployment() > 5.0 ? 0 : 1);
            default -> inflation ? 0 : 2;
        };
    }

    // ---- archetypes --------------------------------------------------------------------------------

    private static PayFor basePayFor(HundredDays t) { return t.party() == Party.DEMOCRAT ? PayFor.REVENUE : PayFor.CUTS; }

    /** The pay-for that moves the bill toward the centre. */
    private static PayFor centristPayFor(HundredDays t) { return t.party() == Party.DEMOCRAT ? PayFor.CUTS : PayFor.REVENUE; }

    private static void partisan(HundredDays t) {
        if (t.week() == 1) {
            act(t, new OpeningAction.DraftBill(BillSize.LARGE, basePayFor(t)));
            act(t, new OpeningAction.Nominate(Post.TREASURY, NomineeType.LOYALIST));
            act(t, new OpeningAction.Nominate(Post.DEFENSE, NomineeType.LOYALIST));
            return;
        }
        nominateVacant(t, NomineeType.LOYALIST);
        if (t.week() == 2 || t.week() == 8) act(t, new OpeningAction.LeanOnFed());
        if (t.stage() == BillStage.SENATE_FLOOR && t.canReconcile() && t.passChance(BillStage.SENATE_FLOOR) < 0.5)
            act(t, new OpeningAction.UseReconciliation());
        pushBill(t, true);
        while (t.attention() > 0 && live(t)) {
            if (!act(t, new OpeningAction.GoPublic())) break;
        }
    }

    private static void dealmaker(HundredDays t) {
        if (t.week() == 1) {
            act(t, new OpeningAction.DraftBill(BillSize.MEDIUM, centristPayFor(t)));
            act(t, new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT));
            act(t, new OpeningAction.MeetOpposition());
            return;
        }
        if (t.week() == 2) {
            act(t, new OpeningAction.Nominate(Post.STATE, NomineeType.CROSSOVER));
            act(t, new OpeningAction.Nominate(Post.DEFENSE, NomineeType.CROSSOVER));
        }
        nominateVacant(t, NomineeType.CROSSOVER);
        pushBill(t, false);
        if (t.attention() > 0 && t.goodwill() < 15) act(t, new OpeningAction.MeetOpposition());
    }

    private static void hawk(HundredDays t) {
        if (t.week() == 1) {
            act(t, new OpeningAction.Nominate(Post.DEFENSE, NomineeType.EXPERT));
            act(t, new OpeningAction.Nominate(Post.STATE, NomineeType.EXPERT));
            act(t, new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT));
            return;
        }
        if (t.stage() == BillStage.NOT_DRAFTED) act(t, new OpeningAction.DraftBill(BillSize.SMALL, PayFor.CUTS));
        nominateVacant(t, NomineeType.EXPERT);
        pushBill(t, false);
    }

    private static void technocrat(HundredDays t) {
        if (t.week() == 1) {
            act(t, new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT));
            act(t, new OpeningAction.DraftBill(BillSize.MEDIUM, basePayFor(t)));
            act(t, new OpeningAction.Nominate(Post.STATE, NomineeType.EXPERT));
            return;
        }
        nominateVacant(t, NomineeType.EXPERT);
        if (t.oil() > 80) act(t, new OpeningAction.ReleaseOilReserve());
        if (t.stage() == BillStage.SENATE_FLOOR && t.canReconcile() && t.passChance(BillStage.SENATE_FLOOR) < 0.3)
            act(t, new OpeningAction.UseReconciliation());
        pushBill(t, false);
    }

    /** A casual player: everything delegated, no personal actions at all. */
    private static void delegator(HundredDays t) { }

    private static void strategist(HundredDays t) {
        boolean unified = t.congress().unified(t.party());
        if (t.week() == 1) {
            act(t, new OpeningAction.DraftBill(BillSize.MEDIUM, unified ? basePayFor(t) : centristPayFor(t)));
            act(t, new OpeningAction.Nominate(Post.TREASURY, NomineeType.EXPERT));
            act(t, new OpeningAction.Nominate(Post.STATE, NomineeType.EXPERT));
            return;
        }
        nominateVacant(t, unified ? NomineeType.EXPERT : NomineeType.CROSSOVER);
        if (t.oil() > 82) act(t, new OpeningAction.ReleaseOilReserve());
        if (t.stage() == BillStage.SENATE_FLOOR && t.canReconcile() && t.passChance(BillStage.SENATE_FLOOR) < 0.3)
            act(t, new OpeningAction.UseReconciliation());
        if (!unified && t.goodwill() < 8 && t.attention() > 1) act(t, new OpeningAction.MeetOpposition());
        pushBill(t, false);
        if (t.attention() > 0 && t.goodwill() < 0) act(t, new OpeningAction.MeetOpposition());
    }

    // ---- shared tactics ----------------------------------------------------------------------------

    private static boolean live(HundredDays t) {
        BillStage s = t.stage();
        return s == BillStage.COMMITTEE || s == BillStage.HOUSE_FLOOR || s == BillStage.SENATE_FLOOR;
    }

    private static void nominateVacant(HundredDays t, NomineeType type) {
        for (Post p : Post.values()) if (t.attention() > 0 && t.seat(p) == SeatStatus.VACANT) act(t, new OpeningAction.Nominate(p, type));
    }

    /** Work the votes, then call the vote when the odds are good enough. Partisans threaten; others pay. */
    private static void pushBill(HundredDays t, boolean partisan) {
        if (!live(t) || t.billActedThisWeek()) return;
        if (t.stage() == BillStage.COMMITTEE) { act(t, new OpeningAction.CallVote(false)); return; }
        boolean house = t.stage() == BillStage.HOUSE_FLOOR;
        for (int i = 0; i < 2 && t.attention() > 1 && t.passChance(t.stage()) < 0.6; i++) {
            WhipRead target = ChiefOfStaff.bestTarget(t, house);
            if (target == null) break;
            Concession c = partisan && target.party() == t.party() ? Concession.THREAT
                    : t.appointmentTokens() > 0 && target.members() >= 20 && t.capital() >= 12 ? Concession.APPOINTMENT : Concession.PROJECT;
            if (!act(t, new OpeningAction.Negotiate(target.bloc(), c))) break;
        }
        if (t.attention() > 0 && (t.passChance(t.stage()) >= 0.5 || (partisan && t.week() >= 10)))
            act(t, new OpeningAction.CallVote(partisan && t.capital() >= 8));
    }

    /** Apply if legal; strategies probe the rules exactly as a player clicking buttons would. */
    private static boolean act(HundredDays t, OpeningAction a) {
        try {
            t.act(a);
            answerFollowUps(t);
            return true;
        } catch (IllegalStateException | game.mandate.core.command.IllegalCommandException refused) {
            return false;
        }
    }

    private static void answerFollowUps(HundredDays t) { /* follow-ups are answered at the end of playWeek */ }

    public static List<Strategy> active() { return List.of(PARTISAN, DEALMAKER, HAWK, TECHNOCRAT, DELEGATOR); }
}
