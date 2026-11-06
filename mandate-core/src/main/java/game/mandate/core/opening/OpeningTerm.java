package game.mandate.core.opening;

import game.mandate.core.congress.Bloc;
import game.mandate.core.campaign.CampaignParams;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.ElectionEngine;
import game.mandate.core.congress.VoteModel;
import game.mandate.core.congress.VoteParams;
import game.mandate.core.data.Tuning;
import game.mandate.core.diplomacy.Country;
import game.mandate.core.diplomacy.DiplomacyEngine;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyParams;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.economy.MarketContext;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.politics.Coalition;
import game.mandate.core.politics.OpinionInputs;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.politics.PoliticsParams;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.time.CampaignCalendar;
import java.time.LocalDate;
import game.mandate.core.congress.Congress;
import game.mandate.core.opening.Decision.Option;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.CrisisKind;
import game.mandate.core.opening.Types.CrisisMove;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.FundingOffer;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.Types.SeatStatus;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The first twelve weeks of a presidency, played weekly. It exists to test the core loop:
 *
 * <pre>Decision -> Consequences -> New information -> Political reaction -> Revised strategy</pre>
 *
 * <p>Five systems interlock on purpose:
 * <ul>
 * <li><b>Legislation</b>: one flagship bill, designed (size, pay-for), negotiated bloc by bloc, voted stage by stage.</li>
 * <li><b>Cabinet</b>: three posts confirmed by the Senate; their competence drives markets, diplomacy and the military.</li>
 * <li><b>Budget and economy</b>: a stopgap funding deadline, two Fed meetings, weekly markets and monthly data releases.</li>
 * <li><b>Crisis and escalation</b>: a foreign crisis with a hidden adversary resolve and an escalation ladder.</li>
 * <li><b>Political reaction</b>: two wings of your party, an pol.opposition() that picks a strategy, and voters in three groups.</li>
 * </ul>
 * Each system reads the others: the pay-for moves bond yields, the crisis moves oil, gas and inflation, the Fed
 * responds to inflation and the deficit, mortgage rates move swing voters, the opposition's strategy follows your
 * bipartisanship and your approval, and your wings' mood sets how loyally your blocs vote.
 */
public final class OpeningTerm {
    public static final int WEEKS = 12;
    public static final int ATTENTION_PER_WEEK = 3;
    public static final int FUNDING_DECISION_WEEK = 6, FUNDING_DEADLINE_WEEK = 8;
    /** Week 1 starts the Monday after the inauguration; Fed meetings and data releases follow the calendar. */
    public static final LocalDate START = LocalDate.of(2029, 1, 22);
    /** Escalations needed to reach an armed clash: three steps past the opening incident. */
    public static final int CLASH_RUNG = 3;

    private final OpeningSetup setup;
    private final Party party;
    private final DeterministicRandomManager rng;
    private final Congress congress;
    private final List<OpeningAction> history = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final List<Briefing> briefings = new ArrayList<>();
    private final List<Decision> pending = new ArrayList<>();
    private final List<String> weekConsequences = new ArrayList<>();
    private final List<String> weekReactions = new ArrayList<>();
    private int nextDecisionId = 1;
    private final Set<Domain> delegated = EnumSet.noneOf(Domain.class);

    private int week = 1;
    private int attention = ATTENTION_PER_WEEK;

    // ---- authoritative systems: the prototype owns none of their formulas -----------------------
    private final PoliticsEngine politicsEngine;
    private final EconomyEngine economyEngine;
    private final DiplomacyEngine diplomacyEngine;
    private final VoteModel votes;
    private final CampaignCalendar calendar;
    private final ElectionEngine elections;
    private final double actingCompetence, actingLoyalty;
    private final PoliticsState pol;
    private final EconomyState econ;
    private final BudgetState budget;
    private final DiplomacyState dip;

    // ---- prototype-specific state ----------------------------------------------------------------
    private final boolean[] revolted = {false, false};
    private double publicPressure;
    private double oilShock, stressShock;               // crisis shocks fed to the economy as context
    private boolean reserveReleased;
    /** The prototype has no candidate; addresses use an average president's charisma. */
    static final double PRESIDENT_CHARISMA = 6.0;
    /** Share of a live bill's deficit the bond market prices in before it passes. */
    static final double PENDING_DEFICIT_WEIGHT = 0.6;

    // ---- the flagship bill -----------------------------------------------------------------------
    private BillStage stage = BillStage.NOT_DRAFTED;
    private BillSize size;
    private PayFor payFor;
    private boolean reconciliation;
    private boolean billActedThisWeek;
    private boolean insistedThisWeek;
    private final Map<String, Double> relationship = new LinkedHashMap<>();
    private final Map<String, Double> pressured = new LinkedHashMap<>();
    private int appointmentTokens = 2;
    private double policyDelivered;

    // ---- cabinet ---------------------------------------------------------------------------------
    private final Map<Post, NomineeType> nominee = new EnumMap<>(Post.class);
    private final Map<Post, SeatStatus> seat = new EnumMap<>(Post.class);
    private final Map<Post, Integer> nominatedWeek = new EnumMap<>(Post.class);
    private final Map<Post, Double> controversyBump = new EnumMap<>(Post.class);


    // ---- crisis and budget -----------------------------------------------------------------------
    private final CrisisKind crisisKind;
    private final int crisisWeek;
    private final double resolve;                       // hidden
    private CrisisOutcome crisis = CrisisOutcome.NONE;
    private int escalation;
    private int crisisWeeks;
    private double intelEstimate = Double.NaN;
    private double lastEscalationRisk;
    private FundingOffer fundingOffer;
    private boolean shutdown;
    private int shutdownWeeks;
    private boolean fundingResolved;

    public OpeningTerm(OpeningSetup setup) {
        this.setup = setup;
        this.party = setup.party();
        this.rng = new DeterministicRandomManager(setup.seed());
        this.congress = setup.unified()
                ? new Congress(party == Party.DEMOCRAT ? 222 : 213, party == Party.DEMOCRAT ? 51 : 49)
                : new Congress(party == Party.DEMOCRAT ? 208 : 227, party == Party.DEMOCRAT ? 48 : 52);
        Tuning tuning = Tuning.defaults();
        this.politicsEngine = new PoliticsEngine(PoliticsParams.from(tuning));
        this.economyEngine = new EconomyEngine(EconomyParams.from(tuning));
        this.diplomacyEngine = new DiplomacyEngine(tuning, Country.load());
        this.votes = new VoteModel(VoteParams.from(tuning));
        this.calendar = CampaignCalendar.load();
        this.elections = new ElectionEngine(StateCatalog.load(), CandidateCatalog.load(), CampaignParams.from(tuning));
        this.actingCompetence = tuning.d("executive.actingCompetence");
        this.actingLoyalty = tuning.d("executive.actingLoyalty");
        this.pol = politicsEngine.newPolitics(CandidateCatalog.load(), 0);
        this.econ = economyEngine.newEconomy();
        this.budget = economyEngine.newBudget();
        this.dip = diplomacyEngine.newDiplomacy();
        politicsEngine.apply(pol, new Effect.PresidentInaugurated("prototype-president", party, 270));
        politicsEngine.setInitialTerms(pol, congress.unified(party));
        for (Party p : Party.values()) for (Bloc b : Bloc.defaultsFor(p)) relationship.put(b.name(), 0.0);
        for (Post p : Post.values()) seat.put(p, SeatStatus.VACANT);
        this.crisisKind = rng.chance("crisis.kind", 0.5) ? CrisisKind.TAIWAN : CrisisKind.GULF;
        this.crisisWeek = 3 + rng.nextInt("crisis.week", 3);
        this.resolve = 0.35 + 0.45 * rng.uniform("crisis.resolve");
        log("Week 1. You take the oath with " + congress + ". Approval " + Math.round(approval()) + "%.");
    }

    public static OpeningTerm replay(OpeningSetup setup, List<OpeningAction> actions) {
        OpeningTerm t = new OpeningTerm(setup);
        for (OpeningAction a : actions) t.apply(a);
        return t;
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

    public void apply(OpeningAction a) {
        if (over()) throw new IllegalStateException("The twelve weeks are over");
        int c = cost(a);
        if (attention < c) throw new IllegalStateException("No attention left this week");
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
            case OpeningAction.Delegate d -> { if (d.on()) delegated.add(d.domain()); else delegated.remove(d.domain()); }
            case OpeningAction.Decide d -> decide(d.decisionId(), d.option());
            case OpeningAction.EndWeek e -> endWeek();
        }
        attention -= c;
        history.add(a);
    }

    // ---- legislation -----------------------------------------------------------------------------

    void draft(BillSize s, PayFor p) {
        require(stage == BillStage.NOT_DRAFTED, "The bill is already drafted");
        size = s;
        payFor = p;
        stage = BillStage.COMMITTEE;
        wingReact(s, p, +1, "drafting");
        consequence("You send Congress the " + billName() + " (" + s.name().toLowerCase() + ", paid for by " + p.label.toLowerCase() + ").");
    }

    private void amend(BillSize s, PayFor p) {
        require(stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR, "No live bill to amend");
        require(s != size || p != payFor, "That is the bill you already have");
        wingReact(size, payFor, -1, "amending");
        size = s;
        payFor = p;
        wingReact(s, p, +1, "amending");
        // Changing course mid-fight costs credibility with everyone a little (BG's flip-flop penalty).
        mood(0, -(1.5));
        mood(1, -(1.5));
        consequence("You rewrite the bill: now " + s.name().toLowerCase() + ", paid for by " + p.label.toLowerCase() + ". Both wings grumble about the change of course.");
    }

    void negotiate(String bloc, Concession c, double efficiency) {
        Bloc b = bloc(bloc);
        switch (c) {
            case PROJECT -> {
                require(pol.capital() >= 4, "Not enough political capital");
                capital(-(4));
                deficit(0.01);
                relationship.merge(b.name(), 0.35 * efficiency, Double::sum);
                consequence("Projects back home for the " + b.name() + " (capital -4).");
            }
            case APPOINTMENT -> {
                require(appointmentTokens > 0, "No appointments left to promise");
                require(pol.capital() >= 6, "Not enough political capital");
                capital(-(6));
                appointmentTokens--;
                relationship.merge(b.name(), 0.6 * efficiency, Double::sum);
                consequence("You promise one of the " + b.name() + " an appointment (capital -6, " + appointmentTokens + " left).");
            }
            case THREAT -> {
                require(b.party() == party, "Threats only work on your own party");
                relationship.merge(b.name(), -0.2, Double::sum);
                pressured.put(b.name(), 0.15);
                if (wingOf(b) >= 0) mood(wingOf(b), -(3)); else { mood(0, -(1.5)); mood(1, -(1.5)); }
                consequence("You warn the " + b.name() + " that a primary is possible. They will vote with you this time, and they will remember.");
            }
        }
        relationship.computeIfPresent(b.name(), (k, v) -> Math.max(-1, Math.min(1, v)));
    }

    private void goPublic() {
        require(stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR, "Nothing to campaign for");
        publicPressure += 1.0;
        double gain = politicsEngine.address(pol, PRESIDENT_CHARISMA, 0, null);
        goodwill(-(2));
        consequence(String.format("You take the bill on the road. Pressure on wavering members rises; opposition leaders call it a campaign stunt (approval +%.1f among your voters).", gain));
    }

    void callVote(boolean insist) {
        require(!billActedThisWeek, "The bill has already moved this week");
        require(stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR, "No vote to call");
        if (stage == BillStage.COMMITTEE) { billActedThisWeek = true; committee(); return; }
        double chance = passChance(stage);
        boolean refused = chance < 0.35;
        if (refused) {
            if (!insist) throw new IllegalStateException(String.format(
                    "The leader will not call a vote expected to fail (%.0f%%). Insist for 8 capital, or work the votes first.", chance * 100));
            require(pol.capital() >= 8, "Not enough political capital to insist");
        }
        billActedThisWeek = true;
        if (refused) {
            capital(-(8));
            goodwill(-(3));
            insistedThisWeek = true;
        }
        boolean house = stage == BillStage.HOUSE_FLOOR;
        double expected = expectedYes(house);
        int yes = (int) Math.round(expected + votes.noise(house) * rng.gaussian("congress"));
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        pressured.clear();
        if (yes >= needed) {
            consequence((house ? "The House" : "The Senate") + " passes the " + billName() + ", " + yes + "-" + ((house ? 435 : 100) - yes) + ".");
            capital(4);
            if (house) stage = BillStage.SENATE_FLOOR;
            else sign();
        } else {
            consequence((house ? "The House" : "The Senate") + " rejects the " + billName() + ": " + yes + " votes of " + needed + " needed.");
            capital(-6);
            opinion(Coalition.SWING, -(1.0));
            opinion(Coalition.BASE, -(1.0));
            billFailed(house);
        }
    }

    private void committee() {
        boolean ownChair = congress.houseMajority() == party;
        double chance = ownChair ? 1.0 : switch (pol.opposition()) { case COOPERATE -> 0.85; case SELECTIVE -> 0.4; case OBSTRUCT -> 0.0; };
        if (rng.chance("congress.committee", chance)) {
            stage = BillStage.HOUSE_FLOOR;
            consequence("The committee reports the bill to the House floor.");
        } else {
            consequence("The committee chairman, from the other party, refuses to hold a markup.");
            raise(Decision.Kind.BOTTLED_IN_COMMITTEE, "Bottled up in committee",
                    "The chairman sits on your bill. Opposition leaders are " + pol.opposition().name().toLowerCase() + ".",
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
        require(stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR || stage == BillStage.COMMITTEE, "No live bill");
        require(canReconcile(), "Reconciliation needs your party to control both chambers, and can be used once");
        reconciliation = true;
        goodwill(-(10));
        consequence("You move the bill to budget reconciliation: a simple Senate majority, but the Byrd rule will strip 15% of it.");
    }

    public boolean canReconcile() { return congress.unified(party) && !reconciliation; }

    private int senateNeeded() { return reconciliation || !congress.filibuster() ? 50 : Congress.CLOTURE; }

    private void sign() {
        stage = BillStage.LAW;
        double payoff = reconciliation ? 0.85 : 1.0;
        double impl = 0.6 + 0.4 * competence(Post.TREASURY) / 100.0;
        policyDelivered = size.value * payoff;
        double deficit = size.deficit * payFor.deficitShare;
        deficit(deficit);
        economyEngine.apply(econ, budget, new Effect.EconomyShock(size.growth * payFor.growthFactor * impl, 0, 12));
        opinion(Coalition.BASE, 2.0 * size.value);
        opinion(Coalition.SWING, 1.2 * size.value * impl - (payFor == PayFor.CUTS ? 1.5 : 0) + (payFor == PayFor.REVENUE ? 0.5 : 0));
        lasting(Coalition.BASE, 1.5 * policyDelivered);
        lasting(Coalition.SWING, 0.8 * policyDelivered * impl - (payFor == PayFor.CUTS ? 0.8 : 0));
        opinion(Coalition.OPPOSITION, payFor == PayFor.CUTS ? 1.0 : 0);
        capital(6);
        consequence(String.format("You sign the %s. Deficit %+.2f%% of GDP; implementation quality %.0f%% (Treasury).", billName(), deficit, impl * 100));
    }

    // ---- cabinet ---------------------------------------------------------------------------------

    void nominate(Post post, NomineeType type) {
        SeatStatus s = seat.get(post);
        require(s == SeatStatus.VACANT || s == SeatStatus.ACTING, post + " already has a nominee");
        nominee.put(post, type);
        seat.put(post, SeatStatus.NOMINATED);
        nominatedWeek.put(post, week);
        controversyBump.put(post, 0.0);
        switch (type) {
            case LOYALIST -> { mood(0, 4); mood(1, -(2)); goodwill(-(3)); }
            case EXPERT -> { mood(1, 2); goodwill(2); }
            case CROSSOVER -> { mood(0, -(6)); mood(1, 1); goodwill(8); }
        }
        consequence("You nominate " + (type == NomineeType.EXPERT ? "an " : "a ") + type.name().toLowerCase() + " for " + postName(post) + ". Hearings next week, a Senate vote the week after.");
    }

    private void cabinetWeek() {
        for (Post p : Post.values()) {
            if (seat.get(p) == SeatStatus.NOMINATED && week == nominatedWeek.get(p) + 1) {
                NomineeType t = nominee.get(p);
                boolean stumble = rng.chance("cabinet.hearing", 0.15 + 0.35 * t.controversy());
                if (stumble) controversyBump.put(p, 0.10);
                seat.put(p, SeatStatus.HEARINGS_DONE);
                consequence("Hearings for " + postName(p) + ": " + (stumble ? "a rough day; an old statement resurfaces." : "a steady performance."));
            } else if (seat.get(p) == SeatStatus.HEARINGS_DONE && week >= nominatedWeek.get(p) + 2) {
                double expected = confirmationExpected(p);
                int yes = (int) Math.round(expected + 1.2 * rng.gaussian("cabinet.vote"));
                if (yes >= 50) {
                    seat.put(p, SeatStatus.CONFIRMED);
                    consequence("The Senate confirms your " + postName(p) + ", " + yes + "-" + (100 - yes) + ".");
                    capital(2);
                } else {
                    seat.put(p, SeatStatus.ACTING);
                    capital(-6);
                    opinion(Coalition.SWING, -(0.8));
                    consequence("The Senate rejects your nominee for " + postName(p) + ", " + yes + "-" + (100 - yes) + ". A career official is acting.");
                    raise(Decision.Kind.NOMINEE_REJECTED, "Nominee rejected: " + postName(p), "The department is run by an acting official (competence " + (int) actingCompetence + ").",
                            List.of(new Option("Send an expert", "Competent, easier to confirm. Two more weeks."),
                                    new Option("Send a crossover pick", "Confirms easily; your base wing is unhappy; opposition goodwill rises."),
                                    new Option("Leave the acting official", "No more fights; the department stays weak.")));
                }
            }
        }
    }

    public double confirmationExpected(Post p) {
        NomineeType t = nominee.get(p);
        if (t == null) return 0;
        return votes.confirmationExpected(party, congress, t.controversy() + controversyBump.getOrDefault(p, 0.0), t == NomineeType.CROSSOVER,
                pol.opposition(), wing(1));
    }

    public double competence(Post p) {
        return seat.get(p) == SeatStatus.CONFIRMED ? nominee.get(p).competence() : actingCompetence;
    }

    public double loyalty(Post p) {
        return seat.get(p) == SeatStatus.CONFIRMED ? nominee.get(p).loyalty() : actingLoyalty;
    }

    // ---- other presidential actions --------------------------------------------------------------

    private void meetOpposition() {
        goodwill(5);
        mood(0, -(1));
        consequence("You host opposition leaders at the White House. Goodwill +5; your base wing notices.");
    }

    private void leanOnFed() {
        require(!econ.fedPressured(), "You have already leaned on the Fed before this meeting");
        economyEngine.apply(econ, budget, new Effect.FedPressure());
        mood(0, 3);
        mood(1, -(2));
        consequence("You publicly call for rate cuts. Your base cheers; bond traders worry about the Fed's independence.");
    }

    private void releaseReserve() {
        require(oil() > 80, "Oil is not high enough to justify a release");
        require(!reserveReleased, "The reserve is already being released");
        reserveReleased = true;
        economyEngine.apply(econ, budget, new Effect.OilRelease(8));
        deterrence(-(2));
        consequence("You release oil from the Strategic Petroleum Reserve. Gas prices should ease; hawks warn the reserve is for emergencies.");
    }

    // ---- decisions -------------------------------------------------------------------------------

    private void raise(Decision.Kind kind, String title, String situation, List<Option> options) {
        pending.add(new Decision(nextDecisionId++, kind, title, situation, options));
    }

    void decide(int id, int option) {
        Decision d = pending.stream().filter(x -> x.id() == id).findFirst().orElseThrow(() -> new IllegalStateException("No decision " + id));
        require(option >= 0 && option < d.options().size(), "No such option");
        log("Decision: " + d.title() + " -> " + d.options().get(option).label());
        switch (d.kind()) {
            case CRISIS -> crisisMove(CrisisMove.values()[option]);
            case FUNDING -> fundingOffer(FundingOffer.values()[option]);
            case SHUTDOWN -> shutdownChoice(option);
            case BILL_FAILED -> billFollowUp(option);
            case BOTTLED_IN_COMMITTEE -> committeeFollowUp(option);
            case NOMINEE_REJECTED -> rejectedFollowUp(d, option);
            case FACTION_REVOLT -> revoltFollowUp(d, option);
            case AFTERMATH -> aftermath(option);
            case LEAK -> { }
        }
        pending.remove(d); // only after the handler succeeded, so a refused choice leaves the decision open
    }

    private void billFollowUp(int option) {
        switch (option) {
            case 0 -> {
                BillSize smaller = size.smaller();
                if (smaller != size) { wingReact(size, payFor, -1, "scaling back"); size = smaller; wingReact(size, payFor, +1, "scaling back"); }
                consequence("You scale the bill back to " + size.name().toLowerCase() + ".");
            }
            case 1 -> { mood(0, 5); goodwill(-(8)); consequence("You blame the opposition. Your base wing rallies; the opposition digs in."); }
            case 2 -> { stage = BillStage.DEAD; capital(5); mood(0, -(6)); consequence("You shelve the bill."); }
            case 3 -> useReconciliation();
            default -> throw new IllegalStateException();
        }
    }

    private void committeeFollowUp(int option) {
        switch (option) {
            case 0 -> {
                require(pol.capital() >= 10, "Not enough capital for a discharge petition");
                capital(-(10));
                goodwill(-(4));
                if (expectedYes(true) >= Congress.HOUSE_MAJORITY) { stage = BillStage.HOUSE_FLOOR; consequence("The discharge petition reaches 218 signatures. The bill goes to the floor."); }
                else consequence("The discharge petition falls short of 218 signatures.");
            }
            case 1 -> {
                wingReact(size, payFor, -1, "the chairman's deal");
                size = size.smaller();
                wingReact(size, payFor, +1, "the chairman's deal");
                goodwill(6);
                stage = BillStage.HOUSE_FLOOR;
                consequence("The chairman agrees to a markup of a smaller bill.");
            }
            case 2 -> consequence("You wait.");
            default -> throw new IllegalStateException();
        }
    }

    private void rejectedFollowUp(Decision d, int option) {
        Post p = Post.valueOf(postFromTitle(d.title()));
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
            require(pol.capital() >= 5, "Not enough capital");
            capital(-(5));
            mood(w, 12);
            goodwill(-(3));
            consequence("You make amends with the " + wingName(w) + ".");
        } else {
            mood(w, -(5));
            opinion(Coalition.SWING, 1.0);
            for (Bloc b : Bloc.defaultsFor(party)) if (wingOf(b) == w) relationship.merge(b.name(), -0.3, Double::sum);
            consequence("You face down the " + wingName(w) + ". Independents notice; the wing does not forget.");
        }
    }

    // ---- crisis ----------------------------------------------------------------------------------

    private void crisisMove(CrisisMove m) {
        require(m != CrisisMove.STRIKE || escalation >= 2, "A strike is only on the table once the crisis has escalated");
        double push;
        switch (m) {
            case DE_ESCALATE -> {
                double success = diplomacyChance(resolve);
                if (rng.chance("crisis.diplomacy", success)) {
                    if (escalation == 0) { endCrisis(CrisisOutcome.DEAL, "A back-channel deal ends the " + crisisKind.label + " crisis."); }
                    else { escalation--; consequence("Diplomacy lowers the temperature: " + rung() + "."); }
                } else {
                    deterrence(-(3));
                    consequence("The back channel goes quiet. " + (crisisKind == CrisisKind.TAIWAN ? "Beijing" : "Tehran") + " reads hesitation.");
                }
                mood(0, 2);
                mood(1, -(2));
                push = -0.05;
            }
            case HOLD -> {
                deterrence(2);
                stressShock += 1;
                push = 0.05;
                consequence("You coordinate sanctions and warnings with allies. Markets dip.");
            }
            case SHOW_FORCE -> {
                deterrence(5);
                deficit(0.02);
                oilShock += crisisKind == CrisisKind.GULF ? 4 : 1;
                mood(0, -(2));
                mood(1, 2);
                push = 0.08 + (competence(Post.DEFENSE) - 60) / 400.0;
                consequence("A carrier group moves into the " + crisisKind.label + ". Oil jumps on the risk.");
            }
            case STRIKE -> {
                deterrence(12);
                mood(0, -(5));
                mood(1, 1);
                push = 0.22 + (competence(Post.DEFENSE) - 60) / 300.0;
                consequence("You order a limited strike. The world waits for the response.");
            }
            default -> throw new IllegalStateException();
        }
        overruleCheck(m);
        if (crisis == CrisisOutcome.ACTIVE) adversaryResponds(push, m);
    }

    /** RD's "advisers take sides": overruling a disloyal secretary risks a leak. */
    private void overruleCheck(CrisisMove chosen) {
        for (Post p : List.of(Post.STATE, Post.DEFENSE)) {
            boolean advocatesOther = (p == Post.STATE) != (chosen == CrisisMove.DE_ESCALATE);
            if (seat.get(p) == SeatStatus.CONFIRMED && advocatesOther && chosen != CrisisMove.HOLD && loyalty(p) < 55
                    && rng.chance("cabinet.leak", 0.3)) {
                opinion(Coalition.SWING, -(1.5));
                opinion(Coalition.BASE, -(0.5));
                reaction("LEAK: your " + postName(p) + " tells reporters the decision was a mistake.");
            }
        }
    }

    /**
     * The adversary weighs pressure against resolve. Force works on a rival with low resolve and provokes one
     * with high resolve (a security dilemma), so the intelligence estimate, not a fixed rule, decides the right move.
     */
    private void adversaryResponds(double push, CrisisMove m) {
        double pressure = (deterrence() - 50) / 100.0 + push - (resolve - 0.5) + 0.15 * rng.gaussian("crisis.adversary");
        double forcefulness = switch (m) { case DE_ESCALATE -> 0.0; case HOLD -> 0.1; case SHOW_FORCE -> 0.35; case STRIKE -> 0.6; };
        double escalateChance = clamp(0.04 + 0.9 * (resolve - 0.5) + forcefulness * (resolve - 0.35) * 2.0 - (deterrence() - 50) / 200.0, 0.02, 0.85);
        lastEscalationRisk = escalateChance;
        if (pressure > 0.22 && !(forcefulness > 0.3 && rng.chance("crisis.provoked", Math.max(0, resolve - 0.55) * 1.5))) {
            endCrisis(CrisisOutcome.BACKED_DOWN, "The other side stands down. The " + crisisKind.label + " crisis is over.");
        } else if (rng.chance("crisis.escalate", escalateChance)) {
            escalation++;
            if (escalation >= CLASH_RUNG) {
                endCrisis(CrisisOutcome.CLASH, "An armed clash in the " + crisisKind.label + ": American sailors are killed.");
                opinion(Coalition.BASE, 1.5);
                opinion(Coalition.SWING, 1.0);   // a brief rally, outweighed by the lasting cost in the record, markets and oil
                oilShock += crisisKind == CrisisKind.GULF ? 18 : 6;
                stressShock += 8;
                raise(Decision.Kind.AFTERMATH, "After the clash in the " + crisisKind.label,
                        "Americans are dead. The country, the markets and the other side are waiting for your next move.",
                        List.of(new Option("Retaliate and hold the line", "Deterrence +10, oil up, your hawks and base rally, swing voters worry, deficit +0.05."),
                                new Option("Seek a ceasefire through allies", String.format("About %.0f%% to succeed (State competence). Failure looks weak.", 100 * ceasefireChance())),
                                new Option("Ask Congress for an authorization", "Capital -6. Shares the decision: opposition goodwill +6 and a unity bump, but it takes time.")));
            } else {
                reaction("The other side escalates: " + rung() + ".");
            }
        } else {
            reaction("The other side holds its ground: " + rung() + ".");
        }
    }

    private void endCrisis(CrisisOutcome o, String text) {
        crisis = o;
        if (o == CrisisOutcome.BACKED_DOWN) { opinion(Coalition.SWING, 2); opinion(Coalition.BASE, 2); deterrence(5); lasting(Coalition.SWING, 1.0); }
        if (o == CrisisOutcome.DEAL) { opinion(Coalition.SWING, 1); lasting(Coalition.SWING, 1.0); }
        if (o == CrisisOutcome.STALEMATE) { deterrence(-(5)); opinion(Coalition.SWING, -(1)); lasting(Coalition.SWING, -(0.5)); }
        if (o == CrisisOutcome.CLASH) { lasting(Coalition.SWING, -(2.5)); lasting(Coalition.BASE, -(1.0)); }
        consequence(text);
    }

    private String rung() { return crisisKind.rungs[escalation >= CLASH_RUNG ? crisisKind.rungs.length - 1 : escalation]; }

    private void crisisWeek() {
        if (crisis == CrisisOutcome.NONE && week == crisisWeek) {
            crisis = CrisisOutcome.ACTIVE;
            escalation = 1;
            reaction("CRISIS: " + crisisKind.rungs[0] + " gives way to " + crisisKind.rungs[1].toLowerCase() + " in the " + crisisKind.label + ".");
        }
        if (crisis != CrisisOutcome.ACTIVE) return;
        crisisWeeks++;
        if (crisisWeeks > 5) { endCrisis(CrisisOutcome.STALEMATE, "The " + crisisKind.label + " crisis settles into a tense stalemate."); return; }
        double noise = 0.05 + 0.25 * (1 - competence(Post.STATE) / 100.0);
        intelEstimate = clamp01(resolve + noise * rng.gaussian("crisis.intel"));
        List<Option> options = new ArrayList<>();
        for (CrisisMove m : CrisisMove.values()) {
            if (m == CrisisMove.STRIKE && escalation < 2) continue;
            String who = m.advocate == null ? "the NSC staff" : "your " + postName(m.advocate);
            options.add(new Option(m.label, "Recommended by " + who + ". " + crisisPreview(m)));
        }
        raise(Decision.Kind.CRISIS, "Crisis in the " + crisisKind.label + ": " + rung(),
                String.format("Intelligence rates the other side's resolve %s (%.0f%%, +/- %.0f). Deterrence %.0f.",
                        intelEstimate > 0.65 ? "high" : intelEstimate > 0.45 ? "moderate" : "low", intelEstimate * 100, noise * 100, deterrence()),
                options);
    }

    private double ceasefireChance() { return clamp01(0.25 + 0.5 * competence(Post.STATE) / 100.0); }

    private void aftermath(int option) {
        switch (option) {
            case 0 -> { deterrence(10); oilShock += crisisKind == CrisisKind.GULF ? 5 : 2; opinion(Coalition.BASE, 2); mood(1, 3); mood(0, -(4));
                        opinion(Coalition.SWING, -(1)); deficit(0.05); consequence("You retaliate. The other side absorbs the blow; the region braces."); }
            case 1 -> {
                if (rng.chance("crisis.ceasefire", ceasefireChance())) { lasting(Coalition.SWING, 1.5); oilShock -= 4; consequence("Allies broker a ceasefire. Markets exhale."); }
                else { deterrence(-(5)); opinion(Coalition.SWING, -(1)); consequence("The ceasefire talks collapse before they begin."); }
            }
            case 2 -> { require(pol.capital() >= 6, "Not enough capital"); capital(-(6)); goodwill(6); opinion(Coalition.SWING, 1); opinion(Coalition.OPPOSITION, 1.5);
                        consequence("Congress debates an authorization. For a week, Washington looks united."); }
            default -> throw new IllegalStateException();
        }
    }

    private double diplomacyChance(double r) {
        return clamp01(0.22 + 0.40 * competence(Post.STATE) / 100.0 - 0.6 * (r - 0.55));
    }

    private String crisisPreview(CrisisMove m) {
        return switch (m) {
            case DE_ESCALATE -> String.format("Each attempt succeeds about %.0f%% of the time (State competence, their resolve); a deal needs the crisis back at its first rung. Failure reads as weakness.",
                    100 * diplomacyChance(intelEstimate));
            case HOLD -> "Low risk, slow pressure; markets dip.";
            case SHOW_FORCE -> "Raises deterrence; oil rises. Deters a rival with low resolve; provokes one with high resolve.";
            case STRIKE -> "Likeliest to end it against a weak-willed rival; against a determined one, it invites a clash.";
        };
    }

    // ---- funding deadline --------------------------------------------------------------------------

    private void fundingOffer(FundingOffer o) {
        fundingOffer = o;
        switch (o) {
            case CLEAN -> { }
            case PRIORITIES -> { mood(0, 4); goodwill(-(6)); }
            case ACCEPT_CUTS -> { mood(0, -(6)); mood(1, 2); goodwill(8); deficit(-(0.05)); opinion(Coalition.SWING, -(0.5)); }
            case DEFENSE_DEAL -> { deterrence(10); deficit(0.05); mood(0, -(3)); mood(1, 3); goodwill(3); }
        }
        consequence("Your stopgap offer: " + o.label.toLowerCase() + ".");
    }

    public double fundingPassChance(FundingOffer o) {
        int i = pol.opposition().ordinal();
        double[] p = switch (o) {
            case CLEAN -> new double[]{0.9, 0.6, 0.25};
            case PRIORITIES -> new double[]{0.5, 0.3, 0.05};
            case ACCEPT_CUTS -> new double[]{0.97, 0.95, 0.9};
            case DEFENSE_DEAL -> new double[]{0.85, 0.65, 0.35};
        };
        return p[i];
    }

    private void fundingWeek() {
        if (week == FUNDING_DECISION_WEEK && fundingOffer == null) {
            List<Option> options = new ArrayList<>();
            for (FundingOffer o : FundingOffer.values()) options.add(new Option(o.label, String.format("Passes about %.0f%% of the time while the opposition is %s.",
                    fundingPassChance(o) * 100, pol.opposition().name().toLowerCase())));
            raise(Decision.Kind.FUNDING, "Government funding runs out at the end of week " + FUNDING_DEADLINE_WEEK,
                    "Choose what you will send Congress. The odds are re-rolled against the opposition's mood on the deadline.", options);
        }
        if (week == FUNDING_DEADLINE_WEEK + 1 && !fundingResolved) {
            fundingResolved = true;
            FundingOffer o = fundingOffer == null ? FundingOffer.CLEAN : fundingOffer;
            if (rng.chance("budget.deadline", fundingPassChance(o))) consequence("Congress passes the stopgap. The government stays open.");
            else { shutdown = true; consequence("Funding lapses. The government shuts down."); }
        }
        if (shutdown) {
            shutdownWeeks++;
            boolean blamed = pol.goodwill() < -10;
            opinion(Coalition.SWING, -(blamed ? 2.0 : 1.0));
            stressShock += 3;
            economyEngine.apply(econ, budget, new Effect.EconomyShock(-0.15, 0, 1));
            reaction("Shutdown, week " + shutdownWeeks + (blamed ? ": voters mostly blame you." : ": voters blame both sides."));
            raise(Decision.Kind.SHUTDOWN, "The shutdown continues", "Week " + shutdownWeeks + " of the shutdown.",
                    List.of(new Option("Hold firm", "The opposition may cave (more likely while swing voters back you)."),
                            new Option("Accept the opposition's cuts to reopen", "Ends it now; your base wing is angry; deficit falls.")));
        }
    }

    private void shutdownChoice(int option) {
        if (option == 1) {
            shutdown = false;
            mood(0, -(6));
            goodwill(6);
            deficit(-(0.05));
            consequence("You accept cuts. The government reopens.");
        } else if (rng.chance("budget.cave", 0.2 + Math.max(0, pol.approval(Coalition.SWING) - 48) * 0.03)) {
            shutdown = false;
            mood(0, 4);
            goodwill(-(4));
            consequence("The opposition caves. The government reopens on your terms.");
        } else {
            consequence("You hold firm. The shutdown goes on.");
        }
    }

    // =============================================================================================
    //  The week's resolution: consequences -> new information -> political reaction
    // =============================================================================================

    private void endWeek() {
        for (Decision d : pending) require(ChiefOfStaff.handles(d.kind(), delegated), "Decide the open questions first: " + d.title());
        ChiefOfStaff.act(this, delegated);
        if (!pending.isEmpty()) throw new IllegalStateException("The chief of staff could not settle: " + pending.get(0).title());

        cabinetWeek();
        economyWeek();
        politicalReaction();

        Briefing b = new Briefing(week, headline(), weekConsequences, weekReactions, intelligenceLines());
        briefings.add(b);
        weekConsequences.clear();
        weekReactions.clear();

        week++;
        attention = ATTENTION_PER_WEEK;
        billActedThisWeek = false;
        insistedThisWeek = false;
        publicPressure *= 0.8;
        if (week <= WEEKS) {
            crisisWeek();
            fundingWeek();
        }
    }

    /** The authoritative economy, stepped one week, with the crisis and the bill fed in as context. */
    private void economyWeek() {
        LocalDate from = weekStart(week), to = from.plusWeeks(1);
        double premium = (crisis == CrisisOutcome.ACTIVE ? (crisisKind == CrisisKind.GULF ? 6 : 2) * (escalation + 1) : 0) + oilShock;
        double stress = (shutdown ? 3 : 0) + 2 * (crisis == CrisisOutcome.ACTIVE ? escalation + 1 : 0) + stressShock;
        boolean live = stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR;
        double pendingDeficit = live ? size.deficit * payFor.deficitShare * PENDING_DEFICIT_WEIGHT : 0;
        economyEngine.step(econ, budget, 1.0 / EconomyEngine.WEEKS_PER_MONTH, new MarketContext(premium, pendingDeficit, competence(Post.TREASURY), stress), rng);
        oilShock *= 0.6;
        stressShock *= 0.7;
        for (int i = 0; i < calendar.fomcMeetings(from, to); i++) {
            double change = economyEngine.fedMeeting(econ, budget);
            consequence(change > 0 ? String.format("The Fed raises rates to %.2f%%, citing inflation and the deficit.", econ.fedRate())
                    : change < 0 ? String.format("The Fed cuts rates to %.2f%%.", econ.fedRate())
                    : String.format("The Fed holds at %.2f%%.", econ.fedRate()));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, true); i++) {
            double change = economyEngine.jobsReport(econ, rng);
            politicsEngine.onRelease(pol, change);
            consequence(String.format("Jobs report: unemployment %.1f%% (was %.1f%%).", econ.reportedUnemployment(), econ.reportedUnemployment() - change));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, false); i++) {
            double change = economyEngine.inflationReport(econ, rng);
            politicsEngine.onRelease(pol, change);
            consequence(String.format("Inflation report: %.1f%% over the year (was %.1f%%).", econ.reportedInflation(), econ.reportedInflation() - change));
        }
    }

    /** The authoritative public-opinion step, then the prototype's revolt and reaction lines. */
    private void politicalReaction() {
        OppositionStrategy before = pol.opposition();
        EconomyParams ep = economyEngine.params();
        politicsEngine.step(pol, 1.0, new OpinionInputs(economyEngine.gas(econ), ep.gas(ep.oilBase()), econ.inflation(), econ.unemployment(),
                econ.mortgageRate(ep), econ.confidence(), econ.growth()), rng);

        // Party wings drift back toward neutral slowly, and revolt once if pushed too far.
        for (int w = 0; w < 2; w++) {
            if (wing(w) < 32 && !revolted[w] && week < WEEKS) {
                revolted[w] = true;
                reaction("The " + wingName(w) + " are in open revolt.");
                raise(Decision.Kind.FACTION_REVOLT, "Revolt: the " + wingName(w),
                        "Members aligned with the " + wingName(w) + " threaten to oppose your agenda.",
                        List.of(new Option("Make amends", "Capital -5; wing mood +12; opposition goodwill -3."),
                                new Option("Face them down", "Wing mood -5; their blocs' relationship falls; independents approve (+1).")));
            }
        }

        if (pol.opposition() != before) reaction("Opposition leaders shift to " + pol.opposition().name().toLowerCase()
                + (pol.opposition() == OppositionStrategy.OBSTRUCT ? ": they will fight everything." : pol.opposition() == OppositionStrategy.COOPERATE ? ": they will deal." : ": they will pick their fights."));
    }

    // =============================================================================================
    //  Information
    // =============================================================================================

    /** The whip count for the current bill, bloc by bloc, in one chamber. */
    public List<WhipRead> whip(boolean house) {
        List<WhipRead> out = new ArrayList<>();
        if (size == null) return out;
        double ideology = billIdeology();
        for (Party side : Party.values()) {
            for (Bloc b : Bloc.defaultsFor(side)) {
                int members = (int) Math.round((house ? congress.house(side) : congress.senate(side)) * b.share());
                double s = support(b, ideology);
                out.add(new WhipRead(b.name(), side, house ? "House" : "Senate", members, members * s, relationship.get(b.name()), wants(b, ideology)));
            }
        }
        return out;
    }

    double support(Bloc b, double ideology) {
        double mood = wingOf(b) < 0 ? (wing(0) + wing(1)) / 2 : wing(wingOf(b));
        return votes.support(b, new VoteModel.BillInputs(party, ideology, size.deficit * payFor.deficitShare, false, pol.opposition(),
                approval(), pol.approval(Coalition.SWING), publicPressure, 0, mood, relationship.get(b.name()), pressured.getOrDefault(b.name(), 0.0), false));
    }

    private String wants(Bloc b, double ideology) {
        boolean own = b.party() == party;
        if (b.deficitHawk() && size.deficit * payFor.deficitShare > 0.1) return "a smaller deficit: a different pay-for or a smaller bill";
        if (own && wingOf(b) == 0) return size == BillSize.LARGE ? "projects back home" : "a bigger bill";
        if (!own && pol.opposition() == OppositionStrategy.OBSTRUCT) return "nothing you can offer while their leaders obstruct";
        if (Math.abs(ideology - b.ideology()) > 0.5) return own ? "a more moderate bill" : "a bill closer to their views";
        return "projects or an appointment";
    }

    public double expectedYes(boolean house) { return whip(house).stream().mapToDouble(WhipRead::expectedYes).sum(); }

    /** Pass probability for the vote at {@code s} (normal approximation of the floor noise). */
    public double passChance(BillStage s) {
        boolean house = s == BillStage.HOUSE_FLOOR;
        double expected = expectedYes(house);
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        return votes.passChance(expected, needed, house);
    }

    private List<String> intelligenceLines() {
        List<String> lines = new ArrayList<>();
        if (stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR)
            lines.add(String.format("Whip: %.0f%% to pass the %s (%s).", passChance(stage) * 100, stage == BillStage.HOUSE_FLOOR ? "House" : "Senate",
                    stage == BillStage.SENATE_FLOOR ? senateNeeded() + " votes needed" : "218 needed"));
        lines.add(String.format("Approval %.0f%% (your voters %.0f, swing %.0f, opposition voters %.0f). Opposition: %s.",
                approval(), pol.approval(Coalition.BASE), pol.approval(Coalition.SWING), pol.approval(Coalition.OPPOSITION), pol.opposition().name().toLowerCase()));
        lines.add(String.format("Gas $%.2f, 10-year %.2f%%, mortgages %.2f%%, Fed %.2f%%.", gas(), econ.tenYear(), econ.mortgageRate(economyEngine.params()), econ.fedRate()));
        return lines;
    }

    private String headline() {
        if (!weekReactions.isEmpty() && weekReactions.get(0).startsWith("CRISIS")) return weekReactions.get(0);
        if (!weekConsequences.isEmpty()) return weekConsequences.get(weekConsequences.size() - 1);
        return "A quiet week in Washington.";
    }

    public Report report() {
        int confirmed = (int) seat.values().stream().filter(st -> st == SeatStatus.CONFIRMED).count();
        double avgComp = (competence(Post.TREASURY) + competence(Post.DEFENSE) + competence(Post.STATE)) / 3.0;
        double partial = stage == BillStage.SENATE_FLOOR ? 0.25 * size.value : 0;
        return new Report(approval(), pol.approval(Coalition.BASE), pol.approval(Coalition.SWING), pol.approval(Coalition.OPPOSITION), stage,
                policyDelivered + partial, confirmed, avgComp, econ.inflation(), econ.unemployment(), econ.tenYear(), gas(), budget.policyDeficit(),
                crisis, deterrence(), wing(0), wing(1), pol.opposition(), pol.goodwill(), pol.capital(), shutdownWeeks,
                elections.midtermSeatOutlook(approval(), econ.growth()));
    }

    // =============================================================================================
    //  Transitions into the authoritative systems
    // =============================================================================================

    private void mood(int w, double d) { politicsEngine.apply(pol, new Effect.WingMood(w, d)); }
    private void goodwill(double d) { politicsEngine.apply(pol, new Effect.Goodwill(d)); }
    private void capital(double d) { politicsEngine.apply(pol, new Effect.Capital(d)); }
    private void opinion(Coalition c, double d) { politicsEngine.apply(pol, new Effect.CoalitionApproval(c, d, false)); }
    private void lasting(Coalition c, double d) { politicsEngine.apply(pol, new Effect.CoalitionApproval(c, d, true)); }
    private void deficit(double d) { economyEngine.apply(econ, budget, new Effect.Deficit(d)); }
    private void deterrence(double d) { diplomacyEngine.apply(dip, new Effect.Deterrence(d)); }
    private double wing(int w) { return pol.party(party).wingMood(w); }
    public double deterrence() { return dip.deterrence(); }

    /** The date a prototype week starts (week 1 = the Monday after the inauguration). */
    public LocalDate weekStart(int w) { return START.plusWeeks(w - 1); }

    // =============================================================================================
    //  Accessors and helpers
    // =============================================================================================

    public double approval() { return politicsEngine.approval(pol); }
    public double gas() { return economyEngine.gas(econ); }
    public boolean over() { return week > WEEKS; }
    public int week() { return week; }
    public int attention() { return attention; }
    public double capital() { return pol.capital(); }
    public Party party() { return party; }
    public OpeningSetup setup() { return setup; }
    public Congress congress() { return congress; }
    public BillStage stage() { return stage; }
    public Optional<BillSize> size() { return Optional.ofNullable(size); }
    public Optional<PayFor> payFor() { return Optional.ofNullable(payFor); }
    public boolean reconciliation() { return reconciliation; }
    public SeatStatus seat(Post p) { return seat.get(p); }
    public Optional<NomineeType> nominee(Post p) { return Optional.ofNullable(nominee.get(p)); }
    public double wingMood(int w) { return wing(w); }
    public String wingName(int w) {
        if (party == Party.DEMOCRAT) return w == 0 ? "Progressives" : "Moderates";
        return w == 0 ? "Populists" : "Traditionalists";
    }
    public OppositionStrategy opposition() { return pol.opposition(); }
    public double goodwill() { return pol.goodwill(); }
    public CrisisOutcome crisis() { return crisis; }
    public CrisisKind crisisKind() { return crisisKind; }
    public int escalation() { return escalation; }

    public double intelEstimate() { return intelEstimate; }
    public double lastEscalationRisk() { return lastEscalationRisk; }
    double hiddenResolve() { return resolve; }
    public double oil() { return econ.oil(); }
    public double tenYear() { return econ.tenYear(); }
    public double inflation() { return econ.inflation(); }
    public double fedRate() { return econ.fedRate(); }
    public double fedCredibility() { return econ.credibility(); }
    public boolean shutdown() { return shutdown; }
    public Optional<FundingOffer> fundingOffer() { return Optional.ofNullable(fundingOffer); }
    public Set<Domain> delegated() { return Collections.unmodifiableSet(delegated); }
    public List<Decision> pending() { return List.copyOf(pending); }
    public List<Briefing> briefings() { return List.copyOf(briefings); }
    public List<String> log() { return Collections.unmodifiableList(log); }
    public List<OpeningAction> history() { return List.copyOf(history); }
    public double relationship(String bloc) { return relationship.get(bloc); }
    public int appointmentTokens() { return appointmentTokens; }
    public boolean billActedThisWeek() { return billActedThisWeek; }

    public String billName() { return party == Party.DEMOCRAT ? "Families First Act" : "Working America Act"; }

    public double billIdeology() {
        double center = switch (size) { case SMALL -> 0.30; case MEDIUM -> 0.45; case LARGE -> 0.60; };
        return party.sign() * -center + payFor.ideologyShift;
    }

    Bloc bloc(String name) {
        for (Party p : Party.values()) for (Bloc b : Bloc.defaultsFor(p)) if (b.name().equals(name)) return b;
        throw new IllegalStateException("No bloc " + name);
    }

    /** 0 = the base wing (most ideological bloc), 1 = the moderate wing, -1 = the mainstream bloc. */
    int wingOf(Bloc b) {
        if (b.party() != party) return -1;
        Bloc[] bs = Bloc.defaultsFor(party);
        Bloc extreme = bs[0], moderate = bs[0];
        for (Bloc x : bs) {
            if (Math.abs(x.ideology()) > Math.abs(extreme.ideology())) extreme = x;
            if (Math.abs(x.ideology()) < Math.abs(moderate.ideology())) moderate = x;
        }
        return b.name().equals(extreme.name()) ? 0 : b.name().equals(moderate.name()) ? 1 : -1;
    }

    /** How each wing feels about a bill design (BG promises / RD factions). sign +1 = adopting, -1 = abandoning. */
    private void wingReact(BillSize s, PayFor p, int sign, String why) {
        double[] sizeA = {-4, 1, 5}, sizeB = {2, 1, -3};
        double a = sizeA[s.ordinal()], bm = sizeB[s.ordinal()];
        if (party == Party.DEMOCRAT) {
            a += switch (p) { case REVENUE -> 4; case BORROW -> 0; case CUTS -> -6; };
            bm += switch (p) { case REVENUE -> -1; case BORROW -> -3; case CUTS -> 2; };
        } else {
            a += switch (p) { case CUTS -> 2; case BORROW -> 0; case REVENUE -> -4; };
            bm += switch (p) { case CUTS -> 3; case BORROW -> -4; case REVENUE -> -2; };
        }
        mood(0, sign * a);
        mood(1, sign * bm);
    }

    String postName(Post p) {
        return switch (p) { case TREASURY -> "Treasury secretary"; case DEFENSE -> "Defense secretary"; case STATE -> "Secretary of State"; };
    }

    private String postFromTitle(String title) {
        for (Post p : Post.values()) if (title.endsWith(postName(p))) return p.name();
        throw new IllegalStateException(title);
    }

    void consequence(String s) { weekConsequences.add(s); log("Week " + week + ": " + s); }
    void reaction(String s) { weekReactions.add(s); log("Week " + week + ": " + s); }
    private void log(String s) { log.add(s); }

    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double clamp01(double v) { return clamp(v, 0, 1); }
}
