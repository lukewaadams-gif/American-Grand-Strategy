package game.mandate.core.opening;

import game.mandate.core.data.Fmt;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.Bloc;
import game.mandate.core.congress.Congress;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.VoteModel;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyParams;
import game.mandate.core.economy.MarketContext;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.effects.Effect;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.executive.GovernmentState.SeatStatus;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Decision.Option;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.CrisisKind;
import game.mandate.core.opening.Types.CrisisMove;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.EconomicChallenge;
import game.mandate.core.opening.Types.FundingOffer;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.politics.Coalition;
import game.mandate.core.politics.OpinionInputs;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.state.Party;
import game.mandate.core.time.CampaignCalendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The first hundred days of a presidency, played weekly. The core loop:
 *
 * <pre>Decision -> Consequences -> New information -> Political reaction -> Revised strategy</pre>
 *
 * <p>The hundred days orchestrate six interlocking processes:
 * <ul>
 * <li>a flagship bill: designed, negotiated bloc by bloc, voted stage by stage;</li>
 * <li>three Senate-confirmed cabinet posts;</li>
 * <li>a stopgap funding deadline;</li>
 * <li>an economic challenge;</li>
 * <li>a multi-week foreign crisis with a hidden adversary;</li>
 * <li>the political reaction of party wings, the opposition and three voter coalitions.</li>
 * </ul>
 * They own only their process state ({@link HundredDaysState}): every number they change lives in a domain state and
 * changes through the {@link Host}'s transitions or the owning engine.
 */
public class HundredDays {
    public static final int FUNDING_DECISION_WEEK = 6, FUNDING_DEADLINE_WEEK = 8, ECONOMY_WEEK = 5;
    /** Escalations needed to reach an armed clash: three steps past the opening incident. */
    public static final int CLASH_RUNG = 3;
    /** Share of a live bill's deficit the bond market prices in before it passes. */
    static final double PENDING_DEFICIT_WEIGHT = 0.6;

    protected final Host h;
    protected final HundredDaysState st;
    private Consumer<OpeningAction> actor = this::apply;
    private String cause = "100 days";

    public HundredDays(Host host, HundredDaysState state) {
        this.h = host;
        this.st = state;
    }

    /** A fresh hundred days: draws the crisis and notes the starting point for the report. */
    public static HundredDaysState begin(Host h) {
        HundredDaysState s = new HundredDaysState();
        s.weeks = h.weeks();
        s.flagshipId = h.flagship().id();
        s.crisisKind = h.rng().chance("crisis.kind", 0.5) ? CrisisKind.TAIWAN : CrisisKind.GULF;
        s.crisisWeek = 3 + h.rng().nextInt("crisis.week", 3);
        s.resolve = 0.35 + 0.45 * h.rng().uniform("crisis.resolve");
        s.challenge = h.rng().chance("economy.challenge", 0.5) ? EconomicChallenge.INFLATION_SURGE : EconomicChallenge.JOBS_SLUMP;
        s.challengeWeek = ECONOMY_WEEK;
        s.startApproval = h.politicsEngine().approval(h.politics());
        s.startMisery = h.economy().misery();
        s.startDeficit = h.budget().policyDeficit();
        s.approvalByWeek.add(s.startApproval);
        s.log.add("Week 1. " + h.presidentName() + " takes the oath with " + h.congress().congress() + ". Approval " + Math.round(s.startApproval) + "%.");
        return s;
    }

    /** Players act through here, so a hosting game can record the action in its command log. */
    public void act(OpeningAction a) { actor.accept(a); }

    public void setActor(Consumer<OpeningAction> actor) { this.actor = actor; }

    public HundredDaysState state() { return st; }

    /** Grades the hundred days and keeps the report in the state (called once, on day 100). */
    public DayHundredReport finish(List<String> promises, List<String> laws) {
        st.finalReport = DayHundredReport.of(this, promises, laws);
        return st.finalReport;
    }

    // =============================================================================================
    //  Commands
    // =============================================================================================

    public static int cost(OpeningAction a) {
        return switch (a) {
            case OpeningAction.DraftBill d -> 1;
            case OpeningAction.AmendBill d -> 1;
            case OpeningAction.Negotiate n -> 1;
            case OpeningAction.GoPublic g -> 1;
            case OpeningAction.CallVote c -> 1;
            case OpeningAction.UseReconciliation r -> 0;
            case OpeningAction.Nominate n -> 1;
            case OpeningAction.MeetOpposition m -> 1;
            case OpeningAction.LeanOnFed l -> 1;
            case OpeningAction.ReleaseOilReserve r -> 1;
            case OpeningAction.Delegate d -> 0;
            case OpeningAction.Decide d -> 0;
            case OpeningAction.EndWeek e -> 0;
        };
    }

    /** Validates and applies one action. A refused action changes nothing. */
    public void apply(OpeningAction a) {
        if (over()) throw new IllegalStateException("The hundred days are over");
        int c = cost(a);
        if (attention() < c) throw new IllegalStateException("No attention left this week");
        cause = "100 days, week " + st.week + ": " + a.getClass().getSimpleName();
        switch (a) {
            case OpeningAction.DraftBill d -> draft(d.size(), d.payFor());
            case OpeningAction.AmendBill d -> amend(d.size(), d.payFor());
            case OpeningAction.Negotiate n -> negotiate(n.bloc(), n.concession(), 1.0);
            case OpeningAction.GoPublic g -> goPublic();
            case OpeningAction.CallVote v -> callVote(v.insist());
            case OpeningAction.UseReconciliation r -> useReconciliation();
            case OpeningAction.Nominate n -> nominate(n.post(), n.type());
            case OpeningAction.MeetOpposition m -> meetOpposition();
            case OpeningAction.LeanOnFed l -> leanOnFed();
            case OpeningAction.ReleaseOilReserve r -> releaseReserve();
            case OpeningAction.Delegate d -> { if (d.on()) st.delegated.add(d.domain()); else st.delegated.remove(d.domain()); }
            case OpeningAction.Decide d -> decide(d.decisionId(), d.option());
            case OpeningAction.EndWeek e -> endWeek();
        }
        if (c > 0) h.executiveEngine().spendAttention(h.government(), c);
    }

    // ---- legislation -----------------------------------------------------------------------------

    void draft(BillSize size, PayFor p) {
        require(st.stage == BillStage.NOT_DRAFTED, "The bill is already drafted");
        st.size = size;
        st.payFor = p;
        st.stage = BillStage.COMMITTEE;
        wingReact(size, p, +1);
        consequence("You send Congress the " + billName() + " (" + size.name().toLowerCase() + ", paid for by " + p.label.toLowerCase() + ").");
    }

    private void amend(BillSize size, PayFor p) {
        require(live(), "No live bill to amend");
        require(size != st.size || p != st.payFor, "That is the bill you already have");
        wingReact(st.size, st.payFor, -1);
        st.size = size;
        st.payFor = p;
        wingReact(size, p, +1);
        // Changing course mid-fight costs credibility with everyone a little (BG's flip-flop penalty).
        mood(0, -1.5);
        mood(1, -1.5);
        consequence("You rewrite the bill: now " + size.name().toLowerCase() + ", paid for by " + p.label.toLowerCase() + ". Both wings grumble about the change of course.");
    }

    void negotiate(String bloc, Concession c, double efficiency) {
        Bloc b = Bloc.named(bloc);
        switch (c) {
            case PROJECT -> {
                require(capital() >= 4, "Not enough political capital");
                capital(-4);
                deficit(0.01);
                relate(b, 0.35 * efficiency);
                consequence("Projects back home for the " + b.name() + " (capital -4).");
            }
            case APPOINTMENT -> {
                require(st.appointmentTokens > 0, "No appointments left to promise");
                require(capital() >= 6, "Not enough political capital");
                capital(-6);
                st.appointmentTokens--;
                relate(b, 0.6 * efficiency);
                consequence("You promise one of the " + b.name() + " an appointment (capital -6, " + st.appointmentTokens + " left).");
            }
            case THREAT -> {
                require(b.party() == party(), "Threats only work on your own party");
                relate(b, -0.2);
                st.pressured.put(b.name(), 0.15);
                if (b.wing() >= 0) mood(b.wing(), -3); else { mood(0, -1.5); mood(1, -1.5); }
                consequence("You warn the " + b.name() + " that a primary is possible. They will vote with you this time, and they will remember.");
            }
        }
    }

    private void goPublic() {
        require(live(), "Nothing to campaign for");
        st.publicPressure += 1.0;
        double gain = h.politicsEngine().address(h.politics(), h.presidentCharisma(), 0, null);
        goodwill(-2);
        consequence(Fmt.format("You take the bill on the road. Pressure on wavering members rises; opposition leaders call it a campaign stunt (approval +%.1f among your voters).", gain));
    }

    void callVote(boolean insist) {
        require(!st.billActedThisWeek, "The bill has already moved this week");
        require(live(), "No vote to call");
        if (st.stage == BillStage.COMMITTEE) { st.billActedThisWeek = true; committee(); return; }
        double chance = passChance(st.stage);
        boolean refused = chance < 0.35;
        if (refused) {
            if (!insist) throw new IllegalStateException(Fmt.format(
                    "The leader will not call a vote expected to fail (%.0f%%). Insist for 8 capital, or work the votes first.", chance * 100));
            require(capital() >= 8, "Not enough political capital to insist");
        }
        st.billActedThisWeek = true;
        if (refused) {
            capital(-8);
            goodwill(-3);
        }
        boolean house = st.stage == BillStage.HOUSE_FLOOR;
        double expected = expectedYes(house);
        int yes = (int) Math.round(expected + votes().noise(house) * h.rng().gaussian("congress"));
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        st.pressured.clear();
        if (yes >= needed) {
            consequence((house ? "The House" : "The Senate") + " passes the " + billName() + ", " + yes + "-" + ((house ? 435 : 100) - yes) + ".");
            capital(4);
            if (house) st.stage = BillStage.SENATE_FLOOR;
            else sign();
        } else {
            consequence((house ? "The House" : "The Senate") + " rejects the " + billName() + ": " + yes + " votes of " + needed + " needed.");
            capital(-6);
            opinion(Coalition.SWING, -1.0);
            opinion(Coalition.BASE, -1.0);
            billFailed(house);
        }
    }

    private void committee() {
        boolean ownChair = congress().houseMajority() == party();
        double chance = ownChair ? 1.0 : switch (opposition()) { case COOPERATE -> 0.85; case SELECTIVE -> 0.4; case OBSTRUCT -> 0.0; };
        if (h.rng().chance("congress.committee", chance)) {
            st.stage = BillStage.HOUSE_FLOOR;
            consequence("The committee reports the bill to the House floor.");
        } else {
            consequence("The committee chairman, from the other party, refuses to hold a markup.");
            raise(Decision.Kind.BOTTLED_IN_COMMITTEE, "Bottled up in committee",
                    "The chairman sits on your bill. Opposition leaders are " + opposition().name().toLowerCase() + ".",
                    List.of(new Option("File a discharge petition", "Capital -10. Works only with 218 House supporters on the whip count."),
                            new Option("Negotiate with the chairman", "The bill shrinks one size; opposition goodwill +6; your base wing is unhappy."),
                            new Option("Wait a week", "Nothing changes unless the opposition's mood does.")));
        }
    }

    private void billFailed(boolean house) {
        List<Option> options = new ArrayList<>(List.of(
                new Option("Scale it back", "One size smaller: easier votes, less policy, your base wing is unhappy."),
                new Option("Blame the opposition", "Your base wing rallies (+5); opposition goodwill -8; swing voters shrug."),
                new Option("Shelve it", "Recover 5 capital; the bill is dead; your base wing is angry (-6).")));
        if (!house && canReconcile()) options.add(new Option("Switch to budget reconciliation", "51 votes instead of 60; the Byrd rule strips 15% of the policy; opposition goodwill -10."));
        raise(Decision.Kind.BILL_FAILED, "The " + billName() + " failed", "What now?", options);
    }

    void useReconciliation() {
        require(live(), "No live bill");
        require(canReconcile(), h.flagship().requires60() ? "The Byrd rule keeps this bill out of reconciliation"
                : "Reconciliation needs your party to control both chambers, and can be used once");
        st.reconciliation = true;
        goodwill(-10);
        consequence("You move the bill to budget reconciliation: a simple Senate majority, but the Byrd rule will strip 15% of it.");
    }

    public boolean canReconcile() {
        return congress().unified(party()) && !st.reconciliation && !h.flagship().requires60() && !h.congress().reconciliationUsed(weekStart(st.week).getYear());
    }

    private int senateNeeded() { return st.reconciliation || !congress().filibuster() ? 50 : Congress.CLOTURE; }

    private void sign() {
        st.stage = BillStage.LAW;
        st.weekSigned = st.week;
        double payoff = st.reconciliation ? 0.85 : 1.0;
        double impl = 0.6 + 0.4 * competence(Post.TREASURY) / 100.0;
        st.policyDelivered = st.size.value * payoff;
        double deficit = billDeficit();
        deficit(deficit);
        dispatch(new Effect.EconomyShock(billGrowth() * impl, h.flagship().inflationImpulse() * st.size.growthFactor, 12));
        opinion(Coalition.BASE, 2.0 * st.size.value);
        opinion(Coalition.SWING, 1.2 * st.size.value * impl - (st.payFor == PayFor.CUTS ? 1.5 : 0) + (st.payFor == PayFor.REVENUE ? 0.5 : 0));
        lasting(Coalition.BASE, 1.5 * st.policyDelivered);
        lasting(Coalition.SWING, 0.8 * st.policyDelivered * impl - (st.payFor == PayFor.CUTS ? 0.8 : 0));
        opinion(Coalition.OPPOSITION, st.payFor == PayFor.CUTS ? 1.0 : 0);
        capital(6);
        dispatch(h.congressEngine().recordLaw(h.congress(), h.policy(), h.flagship(), weekStart(st.week),
                st.reconciliation ? LegislativeStrategy.RECONCILIATION : LegislativeStrategy.PARTY_LINE, payoff));
        consequence(Fmt.format("You sign the %s. Deficit %+.2f%% of GDP; implementation quality %.0f%% (Treasury).", billName(), deficit, impl * 100));
    }

    // ---- cabinet ---------------------------------------------------------------------------------

    void nominate(Post post, NomineeType type) {
        SeatStatus s = seat(post);
        require(s == SeatStatus.VACANT || s == SeatStatus.ACTING, post + " already has a nominee");
        String name = h.executiveEngine().nominate(h.government(), post, type, weekStart(st.week));
        switch (type) {
            case LOYALIST -> { mood(0, 4); mood(1, -2); goodwill(-3); }
            case EXPERT -> { mood(1, 2); goodwill(2); }
            case CROSSOVER -> { mood(0, -6); mood(1, 1); goodwill(8); }
        }
        consequence("You nominate " + name + " (" + (type == NomineeType.EXPERT ? "an " : "a ") + type.name().toLowerCase() + ") for "
                + postName(post) + ". Hearings next week, a Senate vote the week after.");
    }

    /** The week a pending nominee was sent to the Senate. */
    private int nominationWeek(Post p) {
        return (int) ChronoUnit.WEEKS.between(h.start(), h.government().seat(p).nominated()) + 1;
    }

    private void cabinetWeek() {
        for (Post p : Post.values()) {
            GovernmentState.Seat seat = h.government().seat(p);
            if (seat.status() == SeatStatus.NOMINATED && st.week == nominationWeek(p) + 1) {
                boolean stumble = h.rng().chance("cabinet.hearing", 0.15 + 0.35 * seat.type().controversy());
                h.executiveEngine().hearingsHeld(h.government(), p, stumble ? 0.10 : 0);
                consequence("Hearings for " + seat.name() + " (" + postName(p) + "): " + (stumble ? "a rough day; an old statement resurfaces." : "a steady performance."));
            } else if (seat.status() == SeatStatus.HEARINGS_DONE && st.week >= nominationWeek(p) + 2) {
                int yes = (int) Math.round(confirmationExpected(p) + votes().confirmationNoise() * h.rng().gaussian("cabinet.vote"));
                boolean ok = yes >= 50;
                h.executiveEngine().senateVerdict(h.government(), p, ok);
                if (ok) {
                    consequence("The Senate confirms " + seat.name() + " as " + postName(p) + ", " + yes + "-" + (100 - yes) + ".");
                    capital(2);
                } else {
                    capital(-6);
                    opinion(Coalition.SWING, -0.8);
                    consequence("The Senate rejects " + seat.name() + " for " + postName(p) + ", " + yes + "-" + (100 - yes) + ". A career official is acting.");
                    raise(Decision.Kind.NOMINEE_REJECTED, "Nominee rejected: " + postName(p),
                            "The department is run by an acting official (competence " + (int) h.executiveEngine().actingCompetence() + ").",
                            List.of(new Option("Send an expert", "Competent, easier to confirm. Two more weeks."),
                                    new Option("Send a crossover pick", "Confirms easily; your base wing is unhappy; opposition goodwill rises."),
                                    new Option("Leave the acting official", "No more fights; the department stays weak.")));
                }
            }
        }
    }

    public double confirmationExpected(Post p) {
        GovernmentState.Seat s = h.government().seat(p);
        if (s.type() == null) return 0;
        return votes().confirmationExpected(party(), congress(), h.executiveEngine().controversy(h.government(), p), s.type() == NomineeType.CROSSOVER,
                opposition(), wing(1));
    }

    public double competence(Post p) { return h.executiveEngine().competence(h.government(), p); }

    public double loyalty(Post p) { return h.executiveEngine().loyalty(h.government(), p); }

    // ---- other presidential actions --------------------------------------------------------------

    private void meetOpposition() {
        goodwill(5);
        mood(0, -1);
        consequence("You host opposition leaders at the White House. Goodwill +5; your base wing notices.");
    }

    private void leanOnFed() {
        require(!h.economy().fedPressured(), "You have already leaned on the Fed before this meeting");
        dispatch(new Effect.FedPressure());
        mood(0, 3);
        mood(1, -2);
        consequence("You publicly call for rate cuts. Your base cheers; bond traders worry about the Fed's independence.");
    }

    private void releaseReserve() {
        require(oil() > 80, "Oil is not high enough to justify a release");
        require(!st.reserveReleased, "The reserve is already being released");
        st.reserveReleased = true;
        dispatch(new Effect.OilRelease(8));
        deterrence(-2);
        consequence("You release oil from the Strategic Petroleum Reserve. Gas prices should ease; hawks warn the reserve is for emergencies.");
    }

    // ---- decisions -------------------------------------------------------------------------------

    private void raise(Decision.Kind kind, String title, String situation, List<Option> options) {
        st.pending.add(new Decision(st.nextDecisionId++, kind, title, situation, options));
    }

    void decide(int id, int option) {
        Decision d = st.pending.stream().filter(x -> x.id() == id).findFirst().orElseThrow(() -> new IllegalStateException("No decision " + id));
        require(option >= 0 && option < d.options().size(), "No such option");
        String was = cause;
        cause = "100 days, week " + st.week + ": " + d.title() + " -> " + d.options().get(option).label();
        int logAt = st.log.size();
        switch (d.kind()) {
            case CRISIS -> crisisMove(CrisisMove.values()[option]);
            case FUNDING -> fundingOffer(FundingOffer.values()[option]);
            case SHUTDOWN -> shutdownChoice(option);
            case BILL_FAILED -> billFollowUp(option);
            case BOTTLED_IN_COMMITTEE -> committeeFollowUp(option);
            case NOMINEE_REJECTED -> rejectedFollowUp(d, option);
            case FACTION_REVOLT -> revoltFollowUp(d, option);
            case AFTERMATH -> aftermath(option);
            case ECONOMY -> economicResponse(option);
            case LEAK -> { }
        }
        // Only after the handler succeeded, so a refused choice leaves no trace (handlers validate before they change anything).
        st.pending.remove(d);
        st.log.add(logAt, "Decision: " + d.title() + " -> " + d.options().get(option).label());
        cause = was;
    }

    private void billFollowUp(int option) {
        switch (option) {
            case 0 -> {
                BillSize smaller = st.size.smaller();
                if (smaller != st.size) { wingReact(st.size, st.payFor, -1); st.size = smaller; wingReact(st.size, st.payFor, +1); }
                consequence("You scale the bill back to " + st.size.name().toLowerCase() + ".");
            }
            case 1 -> { mood(0, 5); goodwill(-8); consequence("You blame the opposition. Your base wing rallies; the opposition digs in."); }
            case 2 -> { st.stage = BillStage.DEAD; capital(5); mood(0, -6); consequence("You shelve the bill."); }
            case 3 -> useReconciliation();
            default -> throw new IllegalStateException();
        }
    }

    private void committeeFollowUp(int option) {
        switch (option) {
            case 0 -> {
                require(capital() >= 10, "Not enough capital for a discharge petition");
                capital(-10);
                goodwill(-4);
                if (expectedYes(true) >= Congress.HOUSE_MAJORITY) { st.stage = BillStage.HOUSE_FLOOR; consequence("The discharge petition reaches 218 signatures. The bill goes to the floor."); }
                else consequence("The discharge petition falls short of 218 signatures.");
            }
            case 1 -> {
                wingReact(st.size, st.payFor, -1);
                st.size = st.size.smaller();
                wingReact(st.size, st.payFor, +1);
                goodwill(6);
                st.stage = BillStage.HOUSE_FLOOR;
                consequence("The chairman agrees to a markup of a smaller bill.");
            }
            case 2 -> consequence("You wait.");
            default -> throw new IllegalStateException();
        }
    }

    private void rejectedFollowUp(Decision d, int option) {
        Post p = postFromTitle(d.title());
        switch (option) {
            case 0 -> nominate(p, NomineeType.EXPERT);
            case 1 -> nominate(p, NomineeType.CROSSOVER);
            case 2 -> consequence("An acting official stays on at " + postName(p) + ".");
            default -> throw new IllegalStateException();
        }
    }

    private void revoltFollowUp(Decision d, int option) {
        int w = d.title().contains(wingName(0)) ? 0 : 1;
        if (option == 0) {
            require(capital() >= 5, "Not enough capital");
            capital(-5);
            mood(w, 12);
            goodwill(-3);
            consequence("You make amends with the " + wingName(w) + ".");
        } else {
            mood(w, -5);
            opinion(Coalition.SWING, 1.0);
            for (Bloc b : Bloc.defaultsFor(party())) if (b.wing() == w) relate(b, -0.3);
            consequence("You face down the " + wingName(w) + ". Independents notice; the wing does not forget.");
        }
    }

    // ---- the economic challenge ------------------------------------------------------------------

    private void economyChallengeWeek() {
        if (st.week != st.challengeWeek || st.challenge == null || st.challengeResponse >= 0) return;
        if (st.pending.stream().anyMatch(d -> d.kind() == Decision.Kind.ECONOMY)) return;
        cause = "100 days, week " + st.week + ": " + st.challenge.title;
        st.challengeStartInflation = h.economy().inflation();
        st.challengeStartUnemployment = h.economy().unemployment();
        List<Option> options;
        if (st.challenge == EconomicChallenge.INFLATION_SURGE) {
            // Front-loaded, so it is felt before day 100: about +1 point of inflation within two months if nothing is done.
            dispatch(new Effect.EconomyShock(0, 1.6, 2));
            st.oilShock += 8;
            options = List.of(
                    new Option("Back the Fed and hold the line on spending", "Cools prices fastest (inflation -0.7 over three months) at a cost in growth; swing voters feel the squeeze now (-1); deficit -0.05; opposition goodwill +2."),
                    new Option("Send targeted relief checks", "Voters feel helped (+1.5 swing, +2 your base); deficit +0.15; the extra demand keeps inflation up (+0.4)."),
                    new Option("Suspend tariffs and the gas tax", "Prices ease (inflation -0.6 over three months); deficit +0.05; moderates approve; your base wing grumbles."),
                    new Option("Lean on the Fed to keep rates low", "Your base cheers; Fed credibility falls and long rates rise; inflation stays sticky."));
        } else {
            // About half a point of unemployment within three months if nothing is done.
            dispatch(new Effect.EconomyShock(-4.5, -0.3, 3));
            options = List.of(
                    new Option("Emergency stimulus", "Growth +3.0 over three months, the strongest answer to job losses; deficit +0.4; your base wing rallies, deficit hawks revolt (moderate wing -4)."),
                    new Option("Targeted aid and tax rebates", "Growth +1.5 over three months; deficit +0.2; moderates approve."),
                    new Option("Hold the line on spending", "No new deficit; opposition goodwill +3; swing voters feel abandoned (-1.5 now, -0.5 lasting)."),
                    new Option("Pressure the Fed for cuts", "Your base cheers; Fed credibility falls; any cut comes at the next meeting, if at all."));
        }
        reaction("ECONOMY: " + st.challenge.title + ". " + st.challenge.situation);
        raise(Decision.Kind.ECONOMY, st.challenge.title, st.challenge.situation, options);
    }

    private void economicResponse(int option) {
        require(option >= 0 && option < 4, "No such option");
        if (st.challenge == EconomicChallenge.INFLATION_SURGE) {
            switch (option) {
                case 0 -> { deficit(-0.05); dispatch(new Effect.EconomyShock(-0.6, -0.7, 3)); opinion(Coalition.SWING, -1.0); goodwill(2);
                            consequence("You back the Fed and hold the line on spending. Prices should cool; families feel the squeeze first."); }
                case 1 -> { deficit(0.15); dispatch(new Effect.EconomyShock(0.6, 0.4, 3)); opinion(Coalition.SWING, 1.5); opinion(Coalition.BASE, 2);
                            consequence("Relief checks go out. Voters feel helped; economists warn the extra demand will keep prices up."); }
                case 2 -> { deficit(0.05); dispatch(new Effect.EconomyShock(0.1, -0.6, 3)); mood(1, 2); mood(0, -2);
                            consequence("You suspend tariffs and the gas tax. Prices at the pump and in the shops ease."); }
                case 3 -> { if (!h.economy().fedPressured()) dispatch(new Effect.FedPressure()); mood(0, 3); mood(1, -2);
                            consequence("You tell the Fed to keep rates low. Bond markets flinch; inflation expectations drift up."); }
                default -> throw new IllegalStateException();
            }
        } else {
            switch (option) {
                case 0 -> { deficit(0.4); dispatch(new Effect.EconomyShock(3.0, 0.4, 3)); mood(0, 3); mood(1, -4);
                            consequence("You sign an emergency stimulus. Hiring should pick up within weeks; deficit hawks are furious."); }
                case 1 -> { deficit(0.2); dispatch(new Effect.EconomyShock(1.5, 0.15, 3)); mood(1, 1);
                            consequence("Targeted aid and tax rebates go out to the hardest-hit states."); }
                case 2 -> { goodwill(3); opinion(Coalition.SWING, -1.5); lasting(Coalition.SWING, -0.5);
                            consequence("You hold the line on spending. Deficit hawks applaud; laid-off workers do not."); }
                case 3 -> { if (!h.economy().fedPressured()) dispatch(new Effect.FedPressure()); mood(0, 3); mood(1, -2);
                            consequence("You call on the Fed to cut rates. Markets worry about its independence."); }
                default -> throw new IllegalStateException();
            }
        }
        st.challengeResponse = option;
    }

    // ---- crisis ----------------------------------------------------------------------------------

    private void crisisMove(CrisisMove m) {
        require(m != CrisisMove.STRIKE || st.escalation >= 2, "A strike is only on the table once the crisis has escalated");
        double push;
        switch (m) {
            case DE_ESCALATE -> {
                double success = diplomacyChance(st.resolve);
                if (h.rng().chance("crisis.diplomacy", success)) {
                    if (st.escalation == 0) { endCrisis(CrisisOutcome.DEAL, "A back-channel deal ends the " + st.crisisKind.label + " crisis."); }
                    else { st.escalation--; consequence("Diplomacy lowers the temperature: " + rung() + "."); }
                } else {
                    deterrence(-3);
                    consequence("The back channel goes quiet. " + (st.crisisKind == CrisisKind.TAIWAN ? "Beijing" : "Tehran") + " reads hesitation.");
                }
                mood(0, 2);
                mood(1, -2);
                push = -0.05;
            }
            case HOLD -> {
                deterrence(2);
                st.stressShock += 1;
                push = 0.05;
                consequence("You coordinate sanctions and warnings with allies. Markets dip.");
            }
            case SHOW_FORCE -> {
                deterrence(5);
                deficit(0.02);
                st.oilShock += st.crisisKind == CrisisKind.GULF ? 4 : 1;
                mood(0, -2);
                mood(1, 2);
                push = 0.08 + (competence(Post.DEFENSE) - 60) / 400.0;
                consequence("A carrier group moves into the " + st.crisisKind.label + ". Oil jumps on the risk.");
            }
            case STRIKE -> {
                deterrence(12);
                mood(0, -5);
                mood(1, 1);
                push = 0.22 + (competence(Post.DEFENSE) - 60) / 300.0;
                consequence("You order a limited strike. The world waits for the response.");
            }
            default -> throw new IllegalStateException();
        }
        overruleCheck(m);
        if (st.crisis == CrisisOutcome.ACTIVE) adversaryResponds(push, m);
    }

    /** RD's "advisers take sides": overruling a disloyal secretary risks a leak. */
    private void overruleCheck(CrisisMove chosen) {
        for (Post p : List.of(Post.STATE, Post.DEFENSE)) {
            boolean advocatesOther = (p == Post.STATE) != (chosen == CrisisMove.DE_ESCALATE);
            if (seat(p) == SeatStatus.CONFIRMED && advocatesOther && chosen != CrisisMove.HOLD && loyalty(p) < 55
                    && h.rng().chance("cabinet.leak", 0.3)) {
                opinion(Coalition.SWING, -1.5);
                opinion(Coalition.BASE, -0.5);
                reaction("LEAK: your " + postName(p) + " tells reporters the decision was a mistake.");
            }
        }
    }

    /**
     * The adversary weighs pressure against resolve. Force works on a rival with low resolve and provokes one
     * with high resolve (a security dilemma), so the intelligence estimate, not a fixed rule, decides the right move.
     */
    private void adversaryResponds(double push, CrisisMove m) {
        double pressure = (deterrence() - 50) / 100.0 + push - (st.resolve - 0.5) + 0.15 * h.rng().gaussian("crisis.adversary");
        double forcefulness = switch (m) { case DE_ESCALATE -> 0.0; case HOLD -> 0.1; case SHOW_FORCE -> 0.35; case STRIKE -> 0.6; };
        double escalateChance = clamp(0.04 + 0.9 * (st.resolve - 0.5) + forcefulness * (st.resolve - 0.35) * 2.0 - (deterrence() - 50) / 200.0, 0.02, 0.85);
        st.lastEscalationRisk = escalateChance;
        if (pressure > 0.22 && !(forcefulness > 0.3 && h.rng().chance("crisis.provoked", Math.max(0, st.resolve - 0.55) * 1.5))) {
            endCrisis(CrisisOutcome.BACKED_DOWN, "The other side stands down. The " + st.crisisKind.label + " crisis is over.");
        } else if (h.rng().chance("crisis.escalate", escalateChance)) {
            st.escalation++;
            if (st.escalation >= CLASH_RUNG) {
                endCrisis(CrisisOutcome.CLASH, "An armed clash in the " + st.crisisKind.label + ": American sailors are killed.");
                opinion(Coalition.BASE, 1.5);
                opinion(Coalition.SWING, 1.0);   // a brief rally, outweighed by the lasting cost in the record, markets and oil
                st.oilShock += st.crisisKind == CrisisKind.GULF ? 18 : 6;
                st.stressShock += 8;
                raise(Decision.Kind.AFTERMATH, "After the clash in the " + st.crisisKind.label,
                        "Americans are dead. The country, the markets and the other side are waiting for your next move.",
                        List.of(new Option("Retaliate and hold the line", "Deterrence +10, oil up, your hawks and base rally, swing voters worry, deficit +0.05."),
                                new Option("Seek a ceasefire through allies", Fmt.format("About %.0f%% to succeed (State competence). Failure looks weak.", 100 * ceasefireChance())),
                                new Option("Ask Congress for an authorization", "Capital -6. Shares the decision: opposition goodwill +6 and a unity bump, but it takes time.")));
            } else {
                reaction("The other side escalates: " + rung() + ".");
            }
        } else {
            reaction("The other side holds its ground: " + rung() + ".");
        }
    }

    private void endCrisis(CrisisOutcome o, String text) {
        st.crisis = o;
        if (o == CrisisOutcome.BACKED_DOWN) { opinion(Coalition.SWING, 2); opinion(Coalition.BASE, 2); deterrence(5); lasting(Coalition.SWING, 1.0); }
        if (o == CrisisOutcome.DEAL) { opinion(Coalition.SWING, 1); lasting(Coalition.SWING, 1.0); }
        if (o == CrisisOutcome.STALEMATE) { deterrence(-5); opinion(Coalition.SWING, -1); lasting(Coalition.SWING, -0.5); }
        if (o == CrisisOutcome.CLASH) { lasting(Coalition.SWING, -2.5); lasting(Coalition.BASE, -1.0); }
        // The region remembers: the outcome carries into the world's standing tensions after the hundred days.
        double tension = switch (o) { case CLASH -> 12; case STALEMATE -> 4; case BACKED_DOWN -> -6; case DEAL -> -10; default -> 0; };
        dispatch(new Effect.Tension(st.crisisKind == CrisisKind.TAIWAN ? HotSpot.TAIWAN_STRAIT : HotSpot.MIDDLE_EAST, tension));
        consequence(text);
    }

    private String rung() { return st.crisisKind.rungs[st.escalation >= CLASH_RUNG ? st.crisisKind.rungs.length - 1 : st.escalation]; }

    private void crisisTick() {
        if (st.crisis == CrisisOutcome.NONE && st.week == st.crisisWeek) {
            st.crisis = CrisisOutcome.ACTIVE;
            st.escalation = 1;
            reaction("CRISIS: " + st.crisisKind.rungs[0] + " gives way to " + st.crisisKind.rungs[1].toLowerCase() + " in the " + st.crisisKind.label + ".");
        }
        if (st.crisis != CrisisOutcome.ACTIVE) return;
        st.crisisWeeks++;
        if (st.crisisWeeks > 5) { endCrisis(CrisisOutcome.STALEMATE, "The " + st.crisisKind.label + " crisis settles into a tense stalemate."); return; }
        double noise = intelNoise();
        st.intelEstimate = clamp01(st.resolve + noise * h.rng().gaussian("crisis.intel"));
        List<Option> options = new ArrayList<>();
        for (CrisisMove m : CrisisMove.values()) {
            if (m == CrisisMove.STRIKE && st.escalation < 2) continue;
            String who = m.advocate == null ? "the NSC staff" : "your " + postName(m.advocate);
            options.add(new Option(m.label, "Recommended by " + who + ". " + crisisPreview(m)));
        }
        raise(Decision.Kind.CRISIS, "Crisis in the " + st.crisisKind.label + ": " + rung(),
                Fmt.format("Intelligence rates the other side's resolve %s (%.0f%%, +/- %.0f). Deterrence %.0f.",
                        st.intelEstimate > 0.65 ? "high" : st.intelEstimate > 0.45 ? "moderate" : "low", st.intelEstimate * 100, noise * 100, deterrence()),
                options);
    }

    private double ceasefireChance() { return clamp01(0.25 + 0.5 * competence(Post.STATE) / 100.0); }

    private void aftermath(int option) {
        switch (option) {
            case 0 -> { deterrence(10); st.oilShock += st.crisisKind == CrisisKind.GULF ? 5 : 2; opinion(Coalition.BASE, 2); mood(1, 3); mood(0, -4);
                        opinion(Coalition.SWING, -1); deficit(0.05); consequence("You retaliate. The other side absorbs the blow; the region braces."); }
            case 1 -> {
                if (h.rng().chance("crisis.ceasefire", ceasefireChance())) { lasting(Coalition.SWING, 1.5); st.oilShock -= 4; consequence("Allies broker a ceasefire. Markets exhale."); }
                else { deterrence(-5); opinion(Coalition.SWING, -1); consequence("The ceasefire talks collapse before they begin."); }
            }
            case 2 -> { require(capital() >= 6, "Not enough capital"); capital(-6); goodwill(6); opinion(Coalition.SWING, 1); opinion(Coalition.OPPOSITION, 1.5);
                        consequence("Congress debates an authorization. For a week, Washington looks united."); }
            default -> throw new IllegalStateException();
        }
    }

    private double diplomacyChance(double r) {
        return clamp01(0.22 + 0.40 * competence(Post.STATE) / 100.0 - 0.6 * (r - 0.55));
    }

    private String crisisPreview(CrisisMove m) {
        return switch (m) {
            case DE_ESCALATE -> Fmt.format("Each attempt succeeds about %.0f%% of the time (State competence, their resolve); a deal needs the crisis back at its first rung. Failure reads as weakness.",
                    100 * diplomacyChance(st.intelEstimate));
            case HOLD -> "Low risk, slow pressure; markets dip.";
            case SHOW_FORCE -> "Raises deterrence; oil rises. Deters a rival with low resolve; provokes one with high resolve.";
            case STRIKE -> "Likeliest to end it against a weak-willed rival; against a determined one, it invites a clash.";
        };
    }

    // ---- funding deadline --------------------------------------------------------------------------

    private void fundingOffer(FundingOffer o) {
        st.fundingOffer = o;
        switch (o) {
            case CLEAN -> { }
            case PRIORITIES -> { mood(0, 4); goodwill(-6); }
            case ACCEPT_CUTS -> { mood(0, -6); mood(1, 2); goodwill(8); deficit(-0.05); opinion(Coalition.SWING, -0.5); }
            case DEFENSE_DEAL -> { deterrence(10); deficit(0.05); mood(0, -3); mood(1, 3); goodwill(3); }
        }
        consequence("Your stopgap offer: " + o.label.toLowerCase() + ".");
    }

    public double fundingPassChance(FundingOffer o) {
        int i = opposition().ordinal();
        double[] p = switch (o) {
            case CLEAN -> new double[]{0.9, 0.6, 0.25};
            case PRIORITIES -> new double[]{0.5, 0.3, 0.05};
            case ACCEPT_CUTS -> new double[]{0.97, 0.95, 0.9};
            case DEFENSE_DEAL -> new double[]{0.85, 0.65, 0.35};
        };
        return p[i];
    }

    private void fundingWeek() {
        if (st.week == FUNDING_DECISION_WEEK && st.fundingOffer == null) {
            List<Option> options = new ArrayList<>();
            for (FundingOffer o : FundingOffer.values()) options.add(new Option(o.label, Fmt.format("Passes about %.0f%% of the time while the opposition is %s.",
                    fundingPassChance(o) * 100, opposition().name().toLowerCase())));
            raise(Decision.Kind.FUNDING, "Government funding runs out at the end of week " + FUNDING_DEADLINE_WEEK,
                    "Choose what you will send Congress. The odds are re-rolled against the opposition's mood on the deadline.", options);
        }
        if (st.week == FUNDING_DEADLINE_WEEK + 1 && !st.fundingResolved) {
            st.fundingResolved = true;
            FundingOffer o = st.fundingOffer == null ? FundingOffer.CLEAN : st.fundingOffer;
            if (h.rng().chance("budget.deadline", fundingPassChance(o))) consequence("Congress passes the stopgap. The government stays open.");
            else { st.shutdown = true; consequence("Funding lapses. The government shuts down."); }
        }
        if (st.shutdown) {
            st.shutdownWeeks++;
            boolean blamed = goodwill() < -10;
            opinion(Coalition.SWING, -(blamed ? 2.0 : 1.0));
            st.stressShock += 3;
            dispatch(new Effect.EconomyShock(-0.15, 0, 1));
            reaction("Shutdown, week " + st.shutdownWeeks + (blamed ? ": voters mostly blame you." : ": voters blame both sides."));
            raise(Decision.Kind.SHUTDOWN, "The shutdown continues", "Week " + st.shutdownWeeks + " of the shutdown.",
                    List.of(new Option("Hold firm", "The opposition may cave (more likely while swing voters back you)."),
                            new Option("Accept the opposition's cuts to reopen", "Ends it now; your base wing is angry; deficit falls.")));
        }
    }

    private void shutdownChoice(int option) {
        if (option == 1) {
            st.shutdown = false;
            mood(0, -6);
            goodwill(6);
            deficit(-0.05);
            consequence("You accept cuts. The government reopens.");
        } else if (h.rng().chance("budget.cave", 0.2 + Math.max(0, h.politics().approval(Coalition.SWING) - 48) * 0.03)) {
            st.shutdown = false;
            mood(0, 4);
            goodwill(-4);
            consequence("The opposition caves. The government reopens on your terms.");
        } else {
            consequence("You hold firm. The shutdown goes on.");
        }
    }

    // =============================================================================================
    //  The week's resolution: consequences -> new information -> political reaction
    // =============================================================================================

    private void endWeek() {
        for (Decision d : st.pending) require(ChiefOfStaff.handles(d.kind(), st.delegated), "Decide the open questions first: " + d.title());
        ChiefOfStaff.act(this, st.delegated);
        if (!st.pending.isEmpty()) throw new IllegalStateException("The chief of staff could not settle: " + st.pending.get(0).title());
        cause = "100 days, week " + st.week + ": the week's events";

        cabinetWeek();
        economyWeek();
        politicalReaction();

        st.briefings.add(new Briefing(st.week, headline(), st.weekConsequences, st.weekReactions, intelligenceLines()));
        st.weekConsequences.clear();
        st.weekReactions.clear();
        st.approvalByWeek.add(approval());

        st.week++;
        h.executiveEngine().newTurn(h.government());
        st.billActedThisWeek = false;
        st.publicPressure *= 0.8;
        if (st.week <= st.weeks) {
            cause = "100 days, week " + st.week + ": the week's events";
            crisisTick();
            fundingWeek();
            economyChallengeWeek();
        }
    }

    /** The authoritative economy, stepped one week, with the crisis and the bill fed in as context. */
    private void economyWeek() {
        LocalDate from = weekStart(st.week), to = from.plusWeeks(1);
        double premium = (st.crisis == CrisisOutcome.ACTIVE ? (st.crisisKind == CrisisKind.GULF ? 6 : 2) * (st.escalation + 1) : 0) + st.oilShock;
        double stress = (st.shutdown ? 3 : 0) + 2 * (st.crisis == CrisisOutcome.ACTIVE ? st.escalation + 1 : 0) + st.stressShock;
        double pendingDeficit = live() ? billDeficit() * PENDING_DEFICIT_WEIGHT : 0;
        EconomyEngine ee = h.economyEngine();
        ee.step(h.economy(), h.budget(), 1.0 / EconomyEngine.WEEKS_PER_MONTH, new MarketContext(premium, pendingDeficit, competence(Post.TREASURY), stress), h.rng());
        st.oilShock *= 0.6;
        st.stressShock *= 0.7;
        for (int i = 0; i < h.calendar().fomcMeetings(from, to); i++) {
            double change = ee.fedMeeting(h.economy(), h.budget());
            consequence(change > 0 ? Fmt.format("The Fed raises rates to %.2f%%, citing inflation and the deficit.", h.economy().fedRate())
                    : change < 0 ? Fmt.format("The Fed cuts rates to %.2f%%.", h.economy().fedRate())
                    : Fmt.format("The Fed holds at %.2f%%.", h.economy().fedRate()));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, true); i++) {
            double change = ee.jobsReport(h.economy(), h.rng());
            h.politicsEngine().onRelease(h.politics(), change);
            consequence(Fmt.format("Jobs report: unemployment %.1f%% (was %.1f%%).", h.economy().reportedUnemployment(), h.economy().reportedUnemployment() - change));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, false); i++) {
            double change = ee.inflationReport(h.economy(), h.rng());
            h.politicsEngine().onRelease(h.politics(), change);
            consequence(Fmt.format("Inflation report: %.1f%% over the year (was %.1f%%).", h.economy().reportedInflation(), h.economy().reportedInflation() - change));
        }
    }

    /** The authoritative public-opinion step, then the revolt and reaction lines. */
    private void politicalReaction() {
        OppositionStrategy before = opposition();
        EconomyParams ep = h.economyEngine().params();
        h.politicsEngine().step(h.politics(), 1.0, new OpinionInputs(gas(), ep.gas(ep.oilBase()), h.economy().inflation(), h.economy().unemployment(),
                h.economy().mortgageRate(ep), h.economy().confidence(), h.economy().growth()), h.rng());
        for (int w = 0; w < 2; w++) {
            if (wing(w) < 32 && !st.revolted[w] && st.week < st.weeks) {
                st.revolted[w] = true;
                reaction("The " + wingName(w) + " are in open revolt.");
                raise(Decision.Kind.FACTION_REVOLT, "Revolt: the " + wingName(w),
                        "Members aligned with the " + wingName(w) + " threaten to oppose your agenda.",
                        List.of(new Option("Make amends", "Capital -5; wing mood +12; opposition goodwill -3."),
                                new Option("Face them down", "Wing mood -5; their blocs' relationship falls; independents approve (+1).")));
            }
        }
        if (opposition() != before) reaction("Opposition leaders shift to " + opposition().name().toLowerCase()
                + (opposition() == OppositionStrategy.OBSTRUCT ? ": they will fight everything." : opposition() == OppositionStrategy.COOPERATE ? ": they will deal." : ": they will pick their fights."));
    }

    // =============================================================================================
    //  Information
    // =============================================================================================

    /** The whip count for the current bill, bloc by bloc, in one chamber. */
    public List<WhipRead> whip(boolean house) {
        List<WhipRead> out = new ArrayList<>();
        if (st.size == null) return out;
        double ideology = billIdeology();
        for (Party side : Party.values()) {
            for (Bloc b : Bloc.defaultsFor(side)) {
                int members = (int) Math.round((house ? congress().house(side) : congress().senate(side)) * b.share());
                double s = support(b, ideology);
                out.add(new WhipRead(b.name(), side, house ? "House" : "Senate", members, members * s, relationship(b.name()), wants(b, ideology)));
            }
        }
        return out;
    }

    double support(Bloc b, double ideology) {
        double mood = b.party() != party() || b.wing() < 0 ? (wing(0) + wing(1)) / 2 : wing(b.wing());
        return votes().support(b, new VoteModel.BillInputs(party(), ideology, billDeficit(), false, opposition(),
                approval(), h.politics().approval(Coalition.SWING), st.publicPressure, 0, mood, relationship(b.name()),
                st.pressured.getOrDefault(b.name(), 0.0), false));
    }

    private String wants(Bloc b, double ideology) {
        boolean own = b.party() == party();
        if (b.deficitHawk() && billDeficit() > 0.1) return "a smaller deficit: a different pay-for or a smaller bill";
        if (own && b.wing() == 0) return st.size == BillSize.LARGE ? "projects back home" : "a bigger bill";
        if (!own && opposition() == OppositionStrategy.OBSTRUCT) return "nothing you can offer while their leaders obstruct";
        if (Math.abs(ideology - b.ideology()) > 0.5) return own ? "a more moderate bill" : "a bill closer to their views";
        return "projects or an appointment";
    }

    public double expectedYes(boolean house) { return whip(house).stream().mapToDouble(WhipRead::expectedYes).sum(); }

    /** Pass probability for the vote at {@code s} (normal approximation of the floor noise). */
    public double passChance(BillStage s) {
        boolean house = s == BillStage.HOUSE_FLOOR;
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        return votes().passChance(expectedYes(house), needed, house);
    }

    private List<String> intelligenceLines() {
        List<String> lines = new ArrayList<>();
        if (st.stage == BillStage.HOUSE_FLOOR || st.stage == BillStage.SENATE_FLOOR)
            lines.add(Fmt.format("Whip: %.0f%% to pass the %s (%s).", passChance(st.stage) * 100, st.stage == BillStage.HOUSE_FLOOR ? "House" : "Senate",
                    st.stage == BillStage.SENATE_FLOOR ? senateNeeded() + " votes needed" : "218 needed"));
        lines.add(Fmt.format("Approval %.0f%% (your voters %.0f, swing %.0f, opposition voters %.0f). Opposition: %s.",
                approval(), h.politics().approval(Coalition.BASE), h.politics().approval(Coalition.SWING), h.politics().approval(Coalition.OPPOSITION),
                opposition().name().toLowerCase()));
        lines.add(Fmt.format("Gas $%.2f, 10-year %.2f%%, mortgages %.2f%%, Fed %.2f%%, unemployment %.1f%%, inflation %.1f%%.", gas(), h.economy().tenYear(),
                h.economy().mortgageRate(h.economyEngine().params()), h.economy().fedRate(), h.economy().unemployment(), h.economy().inflation()));
        return lines;
    }

    private String headline() {
        for (String r : st.weekReactions) if (r.startsWith("CRISIS") || r.startsWith("ECONOMY")) return r;
        if (!st.weekConsequences.isEmpty()) return st.weekConsequences.get(st.weekConsequences.size() - 1);
        return "A quiet week in Washington.";
    }

    public Report report() {
        int confirmed = (int) java.util.Arrays.stream(Post.values()).filter(p -> seat(p) == SeatStatus.CONFIRMED).count();
        double avgComp = (competence(Post.TREASURY) + competence(Post.DEFENSE) + competence(Post.STATE)) / 3.0;
        double partial = st.stage == BillStage.SENATE_FLOOR ? 0.25 * st.size.value : 0;
        return new Report(approval(), h.politics().approval(Coalition.BASE), h.politics().approval(Coalition.SWING), h.politics().approval(Coalition.OPPOSITION),
                st.stage, st.policyDelivered + partial, confirmed, avgComp, h.economy().inflation(), h.economy().unemployment(), h.economy().tenYear(), gas(),
                h.budget().policyDeficit() - st.startDeficit, st.crisis, deterrence(), wing(0), wing(1), opposition(), goodwill(), capital(), st.shutdownWeeks,
                h.elections().midtermSeatOutlook(approval(), h.economy().growth()));
    }

    // =============================================================================================
    //  Transitions into the authoritative systems
    // =============================================================================================

    private void dispatch(Effect e) { h.dispatch(cause, e); }
    private void mood(int w, double d) { dispatch(new Effect.WingMood(w, d)); }
    private void goodwill(double d) { dispatch(new Effect.Goodwill(d)); }
    private void capital(double d) { dispatch(new Effect.Capital(d)); }
    private void opinion(Coalition c, double d) { if (d != 0) dispatch(new Effect.CoalitionApproval(c, d, false)); }
    private void lasting(Coalition c, double d) { if (d != 0) dispatch(new Effect.CoalitionApproval(c, d, true)); }
    private void deficit(double d) { dispatch(new Effect.Deficit(d)); }
    private void deterrence(double d) { dispatch(new Effect.Deterrence(d)); }
    private void relate(Bloc b, double d) { h.congressEngine().adjustRelationship(h.congress(), b.name(), d); }
    private double wing(int w) { return h.politics().party(party()).wingMood(w); }
    private VoteModel votes() { return h.congressEngine().votes(); }

    /** The date a week starts (week 1 = the Monday after the inauguration). */
    public LocalDate weekStart(int w) { return h.start().plusWeeks(w - 1); }

    // =============================================================================================
    //  Accessors and helpers
    // =============================================================================================

    public double approval() { return h.politicsEngine().approval(h.politics()); }
    public double gas() { return h.economyEngine().gas(h.economy()); }
    public boolean over() { return st.week > st.weeks; }
    public int week() { return st.week; }
    public int weeks() { return st.weeks; }
    public int attention() { return h.government().attention(); }
    public double capital() { return h.politics().capital(); }
    public Party party() { return h.party(); }
    public Congress congress() { return h.congress().congress(); }
    public Bill flagship() { return h.flagship(); }
    public BillStage stage() { return st.stage; }
    public Optional<BillSize> size() { return Optional.ofNullable(st.size); }
    public Optional<PayFor> payFor() { return Optional.ofNullable(st.payFor); }
    public boolean reconciliation() { return st.reconciliation; }
    public SeatStatus seat(Post p) { return h.government().seat(p).status(); }
    public Optional<NomineeType> nominee(Post p) { return Optional.ofNullable(h.government().seat(p).type()); }
    public String nomineeName(Post p) { return h.government().seat(p).name(); }
    public double wingMood(int w) { return wing(w); }
    public String wingName(int w) { return h.politics().party(party()).wingName(w); }
    public OppositionStrategy opposition() { return h.politics().opposition(); }
    public double goodwill() { return h.politics().goodwill(); }
    public CrisisOutcome crisis() { return st.crisis; }
    public CrisisKind crisisKind() { return st.crisisKind; }
    public int crisisWeek() { return st.crisisWeek; }
    public int escalation() { return st.escalation; }
    public double deterrence() { return h.diplomacy().deterrence(); }
    public double intelEstimate() { return st.intelEstimate; }
    /** The intelligence estimate's typical error (one standard deviation), narrower with an able Secretary of State. */
    public double intelNoise() { return 0.05 + 0.25 * (1 - competence(Post.STATE) / 100.0); }
    public double lastEscalationRisk() { return st.lastEscalationRisk; }
    double hiddenResolve() { return st.resolve; }
    public double oil() { return h.economy().oil(); }
    public double tenYear() { return h.economy().tenYear(); }
    public double inflation() { return h.economy().inflation(); }
    public double unemployment() { return h.economy().unemployment(); }
    public double fedRate() { return h.economy().fedRate(); }
    public double fedCredibility() { return h.economy().credibility(); }
    public boolean shutdown() { return st.shutdown; }
    public int shutdownWeeks() { return st.shutdownWeeks; }
    public Optional<FundingOffer> fundingOffer() { return Optional.ofNullable(st.fundingOffer); }
    public EconomicChallenge challenge() { return st.challenge; }
    public int challengeResponse() { return st.challengeResponse; }
    public Set<Domain> delegated() { return Collections.unmodifiableSet(st.delegated); }
    public List<Decision> pending() { return List.copyOf(st.pending); }
    public List<Briefing> briefings() { return List.copyOf(st.briefings); }
    public List<String> log() { return Collections.unmodifiableList(st.log); }
    public double relationship(String bloc) { return h.congress().relationship(bloc); }
    public int appointmentTokens() { return st.appointmentTokens; }
    public boolean billActedThisWeek() { return st.billActedThisWeek; }
    public int weekSigned() { return st.weekSigned; }
    public boolean live() { return st.stage == BillStage.COMMITTEE || st.stage == BillStage.HOUSE_FLOOR || st.stage == BillStage.SENATE_FLOOR; }

    public String billName() { return h.flagship().name(); }

    /** The flagship's position: the catalog bill's, scaled by size and shifted by the pay-for. */
    public double billIdeology() { return designIdeology(h.flagship(), st.size, st.payFor); }

    /** A design's position, -1 (left) to +1 (right): the catalog bill's, scaled by size and shifted by the pay-for. */
    public static double designIdeology(Bill b, BillSize size, PayFor payFor) { return b.ideology() * size.ideologyFactor + payFor.ideologyShift; }

    /** A design's deficit if enacted, % of GDP: a cost is scaled by the pay-for; a bill that already cuts the deficit is not. */
    public static double designDeficit(Bill b, BillSize size, PayFor payFor) {
        double base = b.deficitPctGdp() * size.deficitFactor;
        return base > 0 ? base * payFor.deficitShare : base;
    }

    /**
     * Deficit if enacted, % of GDP: the catalog bill's (a medium, borrowed bill), scaled by size and pay-for. A bill
     * that already cuts the deficit needs no paying for, so the pay-for scales only a cost.
     */
    public double billDeficit() { return st.size == null ? 0 : designDeficit(h.flagship(), st.size, st.payFor); }

    private double billGrowth() { return h.flagship().growthImpulse() * st.size.growthFactor * st.payFor.growthFactor; }

    /** How each wing feels about a bill design (BG promises / RD factions). sign +1 = adopting, -1 = abandoning. */
    private void wingReact(BillSize s, PayFor p, int sign) {
        double[] sizeA = {-4, 1, 5}, sizeB = {2, 1, -3};
        double a = sizeA[s.ordinal()], bm = sizeB[s.ordinal()];
        if (party() == Party.DEMOCRAT) {
            a += switch (p) { case REVENUE -> 4; case BORROW -> 0; case CUTS -> -6; };
            bm += switch (p) { case REVENUE -> -1; case BORROW -> -3; case CUTS -> 2; };
        } else {
            a += switch (p) { case CUTS -> 2; case BORROW -> 0; case REVENUE -> -4; };
            bm += switch (p) { case CUTS -> 3; case BORROW -> -4; case REVENUE -> -2; };
        }
        mood(0, sign * a);
        mood(1, sign * bm);
    }

    public static String postName(Post p) {
        return switch (p) { case TREASURY -> "Treasury secretary"; case DEFENSE -> "Defense secretary"; case STATE -> "Secretary of State"; };
    }

    private static Post postFromTitle(String title) {
        for (Post p : Post.values()) if (title.endsWith(postName(p))) return p;
        throw new IllegalStateException(title);
    }

    void consequence(String s) { st.weekConsequences.add(s); log("Week " + st.week + ": " + s); }
    void reaction(String s) { st.weekReactions.add(s); log("Week " + st.week + ": " + s); }
    private void log(String s) { st.log.add(s); }

    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double clamp01(double v) { return clamp(v, 0, 1); }
}
