package game.mandate.core.opening;

import game.mandate.core.congress.Bloc;
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
import game.mandate.core.opening.Types.NomineeType;
import game.mandate.core.opening.Types.OppositionStrategy;
import game.mandate.core.opening.Types.PayFor;
import game.mandate.core.opening.Types.Post;
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
 * <li><b>Political reaction</b>: two wings of your party, an opposition that picks a strategy, and voters in three groups.</li>
 * </ul>
 * Each system reads the others: the pay-for moves bond yields, the crisis moves oil, gas and inflation, the Fed
 * responds to inflation and the deficit, mortgage rates move swing voters, the opposition's strategy follows your
 * bipartisanship and your approval, and your wings' mood sets how loyally your blocs vote.
 */
public final class OpeningTerm {
    public static final int WEEKS = 12;
    public static final int ATTENTION_PER_WEEK = 3;
    public static final int FUNDING_DECISION_WEEK = 6, FUNDING_DEADLINE_WEEK = 8;
    public static final Set<Integer> FED_WEEKS = Set.of(2, 8), JOBS_WEEKS = Set.of(2, 6, 10), CPI_WEEKS = Set.of(3, 7, 11);
    static final double ACTING_COMPETENCE = 45;
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
    private double capital;

    // ---- public opinion and party ---------------------------------------------------------------
    private double base = 85, swing = 52, opp = 14;
    private double modBase, modSwing, modOpp;          // decaying event effects, by group
    private double recordBase, recordSwing;            // lasting effects of what you have done (laws, crisis outcomes)
    private double honeymoon = 7;
    private final double[] wingMood = {60, 60};        // [0] = the base wing, [1] = the moderate wing
    private final boolean[] revolted = {false, false};
    private double goodwill;                           // opposition leaders' view of you, -50..50
    private OppositionStrategy opposition = OppositionStrategy.SELECTIVE;
    private double publicPressure;
    private double recentAddresses;

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

    // ---- economy ---------------------------------------------------------------------------------
    private double inflation = 2.8, unemployment = 4.4, fedRate = 3.75, tenYear = 4.10, oil = 75, confidence = 100;
    private double deficitChange, demandImpulse, fedCredibility = 1.0, sprRelief;
    private boolean leanedOnFed;
    private double reportedInflation = 2.8, reportedUnemployment = 4.4;

    // ---- crisis and budget -----------------------------------------------------------------------
    private final CrisisKind crisisKind;
    private final int crisisWeek;
    private final double resolve;                       // hidden
    private CrisisOutcome crisis = CrisisOutcome.NONE;
    private int escalation;
    private int crisisWeeks;
    private double deterrence = 50;
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
        this.capital = setup.unified() ? 40 : 30;
        this.goodwill = setup.unified() ? 0 : -5;
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
        wingMood[0] -= 1.5;
        wingMood[1] -= 1.5;
        consequence("You rewrite the bill: now " + s.name().toLowerCase() + ", paid for by " + p.label.toLowerCase() + ". Both wings grumble about the change of course.");
    }

    void negotiate(String bloc, Concession c, double efficiency) {
        Bloc b = bloc(bloc);
        switch (c) {
            case PROJECT -> {
                require(capital >= 4, "Not enough political capital");
                capital -= 4;
                deficitChange += 0.01;
                relationship.merge(b.name(), 0.35 * efficiency, Double::sum);
                consequence("Projects back home for the " + b.name() + " (capital -4).");
            }
            case APPOINTMENT -> {
                require(appointmentTokens > 0, "No appointments left to promise");
                require(capital >= 6, "Not enough political capital");
                capital -= 6;
                appointmentTokens--;
                relationship.merge(b.name(), 0.6 * efficiency, Double::sum);
                consequence("You promise one of the " + b.name() + " an appointment (capital -6, " + appointmentTokens + " left).");
            }
            case THREAT -> {
                require(b.party() == party, "Threats only work on your own party");
                relationship.merge(b.name(), -0.2, Double::sum);
                pressured.put(b.name(), 0.15);
                if (wingOf(b) >= 0) wingMood[wingOf(b)] -= 3; else { wingMood[0] -= 1.5; wingMood[1] -= 1.5; }
                consequence("You warn the " + b.name() + " that a primary is possible. They will vote with you this time, and they will remember.");
            }
        }
        relationship.computeIfPresent(b.name(), (k, v) -> Math.max(-1, Math.min(1, v)));
    }

    private void goPublic() {
        require(stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR, "Nothing to campaign for");
        publicPressure += 1.0;
        double gain = 0.8 * Math.pow(0.5, recentAddresses);
        recentAddresses += 1;
        modSwing += gain * 0.5;
        modBase += gain * 0.5;
        goodwill -= 2;
        consequence(String.format("You take the bill on the road. Pressure on wavering members rises; opposition leaders call it a campaign stunt (approval +%.1f among your voters).", gain * 0.5));
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
            require(capital >= 8, "Not enough political capital to insist");
        }
        billActedThisWeek = true;
        if (refused) {
            capital -= 8;
            goodwill -= 3;
            insistedThisWeek = true;
        }
        boolean house = stage == BillStage.HOUSE_FLOOR;
        double expected = expectedYes(house);
        int yes = (int) Math.round(expected + (house ? 4.0 : 1.6) * rng.gaussian("congress"));
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        pressured.clear();
        if (yes >= needed) {
            consequence((house ? "The House" : "The Senate") + " passes the " + billName() + ", " + yes + "-" + ((house ? 435 : 100) - yes) + ".");
            capital += 4;
            if (house) stage = BillStage.SENATE_FLOOR;
            else sign();
        } else {
            consequence((house ? "The House" : "The Senate") + " rejects the " + billName() + ": " + yes + " votes of " + needed + " needed.");
            capital = Math.max(0, capital - 6);
            modSwing -= 1.0;
            modBase -= 1.0;
            billFailed(house);
        }
    }

    private void committee() {
        boolean ownChair = congress.houseMajority() == party;
        double chance = ownChair ? 1.0 : switch (opposition) { case COOPERATE -> 0.85; case SELECTIVE -> 0.4; case OBSTRUCT -> 0.0; };
        if (rng.chance("congress.committee", chance)) {
            stage = BillStage.HOUSE_FLOOR;
            consequence("The committee reports the bill to the House floor.");
        } else {
            consequence("The committee chairman, from the other party, refuses to hold a markup.");
            raise(Decision.Kind.BOTTLED_IN_COMMITTEE, "Bottled up in committee",
                    "The chairman sits on your bill. Opposition leaders are " + opposition.name().toLowerCase() + ".",
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
        goodwill -= 10;
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
        deficitChange += deficit;
        demandImpulse += size.growth * payFor.growthFactor * impl;
        modBase += 2.0 * size.value;
        modSwing += 1.2 * size.value * impl - (payFor == PayFor.CUTS ? 1.5 : 0) + (payFor == PayFor.REVENUE ? 0.5 : 0);
        recordBase += 1.5 * policyDelivered;
        recordSwing += 0.8 * policyDelivered * impl - (payFor == PayFor.CUTS ? 0.8 : 0);
        modOpp += payFor == PayFor.CUTS ? 1.0 : 0;
        capital += 6;
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
            case LOYALIST -> { wingMood[0] += 4; wingMood[1] -= 2; goodwill -= 3; }
            case EXPERT -> { wingMood[1] += 2; goodwill += 2; }
            case CROSSOVER -> { wingMood[0] -= 6; wingMood[1] += 1; goodwill += 8; }
        }
        consequence("You nominate " + (type == NomineeType.EXPERT ? "an " : "a ") + type.name().toLowerCase() + " for " + postName(post) + ". Hearings next week, a Senate vote the week after.");
    }

    private void cabinetWeek() {
        for (Post p : Post.values()) {
            if (seat.get(p) == SeatStatus.NOMINATED && week == nominatedWeek.get(p) + 1) {
                NomineeType t = nominee.get(p);
                boolean stumble = rng.chance("cabinet.hearing", 0.15 + 0.35 * t.controversy);
                if (stumble) controversyBump.put(p, 0.10);
                seat.put(p, SeatStatus.HEARINGS_DONE);
                consequence("Hearings for " + postName(p) + ": " + (stumble ? "a rough day; an old statement resurfaces." : "a steady performance."));
            } else if (seat.get(p) == SeatStatus.HEARINGS_DONE && week >= nominatedWeek.get(p) + 2) {
                double expected = confirmationExpected(p);
                int yes = (int) Math.round(expected + 1.2 * rng.gaussian("cabinet.vote"));
                if (yes >= 50) {
                    seat.put(p, SeatStatus.CONFIRMED);
                    consequence("The Senate confirms your " + postName(p) + ", " + yes + "-" + (100 - yes) + ".");
                    capital += 2;
                } else {
                    seat.put(p, SeatStatus.ACTING);
                    capital = Math.max(0, capital - 6);
                    modSwing -= 0.8;
                    consequence("The Senate rejects your nominee for " + postName(p) + ", " + yes + "-" + (100 - yes) + ". A career official is acting.");
                    raise(Decision.Kind.NOMINEE_REJECTED, "Nominee rejected: " + postName(p), "The department is run by an acting official (competence " + (int) ACTING_COMPETENCE + ").",
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
        double controversy = t.controversy + controversyBump.getOrDefault(p, 0.0);
        double total = 0;
        for (Party side : Party.values()) {
            Bloc[] blocs = Bloc.defaultsFor(side);
            for (Bloc b : blocs) {
                double s;
                double moderation = 1.0 - Math.min(1, Math.abs(b.ideology()) / 0.8);  // moderates weigh controversy most
                if (side == party) {
                    // Own-party senators back routine picks; defections grow with controversy above a threshold.
                    s = 0.99 - Math.max(0, controversy - 0.15) * (0.1 + 0.35 * moderation) - (wingMood[1] < 40 ? 0.1 * moderation : 0);
                } else {
                    double baseOpp = t == NomineeType.CROSSOVER ? 0.85
                            : (0.05 + 0.5 * (1 - controversy)) * switch (opposition) { case COOPERATE -> 0.7; case SELECTIVE -> 0.35; case OBSTRUCT -> 0.08; };
                    s = baseOpp * (0.3 + 0.7 * moderation);
                }
                total += congress.senate(side) * b.share() * clamp01(s);
            }
        }
        return total;
    }

    public double competence(Post p) {
        return seat.get(p) == SeatStatus.CONFIRMED ? nominee.get(p).competence : ACTING_COMPETENCE;
    }

    public double loyalty(Post p) {
        return seat.get(p) == SeatStatus.CONFIRMED ? nominee.get(p).loyalty : 70;
    }

    // ---- other presidential actions --------------------------------------------------------------

    private void meetOpposition() {
        goodwill += 5;
        wingMood[0] -= 1;
        consequence("You host opposition leaders at the White House. Goodwill +5; your base wing notices.");
    }

    private void leanOnFed() {
        require(!leanedOnFed, "You have already leaned on the Fed before this meeting");
        leanedOnFed = true;
        fedCredibility -= 0.12;
        wingMood[0] += 3;
        wingMood[1] -= 2;
        consequence("You publicly call for rate cuts. Your base cheers; bond traders worry about the Fed's independence.");
    }

    private void releaseReserve() {
        require(oil > 80, "Oil is not high enough to justify a release");
        require(sprRelief == 0, "The reserve is already being released");
        sprRelief = 8;
        deterrence -= 2;
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
            case 1 -> { wingMood[0] += 5; goodwill -= 8; consequence("You blame the opposition. Your base wing rallies; the opposition digs in."); }
            case 2 -> { stage = BillStage.DEAD; capital += 5; wingMood[0] -= 6; consequence("You shelve the bill."); }
            case 3 -> useReconciliation();
            default -> throw new IllegalStateException();
        }
    }

    private void committeeFollowUp(int option) {
        switch (option) {
            case 0 -> {
                require(capital >= 10, "Not enough capital for a discharge petition");
                capital -= 10;
                goodwill -= 4;
                if (expectedYes(true) >= Congress.HOUSE_MAJORITY) { stage = BillStage.HOUSE_FLOOR; consequence("The discharge petition reaches 218 signatures. The bill goes to the floor."); }
                else consequence("The discharge petition falls short of 218 signatures.");
            }
            case 1 -> {
                wingReact(size, payFor, -1, "the chairman's deal");
                size = size.smaller();
                wingReact(size, payFor, +1, "the chairman's deal");
                goodwill += 6;
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
            require(capital >= 5, "Not enough capital");
            capital -= 5;
            wingMood[w] += 12;
            goodwill -= 3;
            consequence("You make amends with the " + wingName(w) + ".");
        } else {
            wingMood[w] -= 5;
            modSwing += 1.0;
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
                    deterrence -= 3;
                    consequence("The back channel goes quiet. " + (crisisKind == CrisisKind.TAIWAN ? "Beijing" : "Tehran") + " reads hesitation.");
                }
                wingMood[0] += 2;
                wingMood[1] -= 2;
                push = -0.05;
            }
            case HOLD -> {
                deterrence += 2;
                confidence -= 1;
                push = 0.05;
                consequence("You coordinate sanctions and warnings with allies. Markets dip.");
            }
            case SHOW_FORCE -> {
                deterrence += 5;
                deficitChange += 0.02;
                oil += crisisKind == CrisisKind.GULF ? 4 : 1;
                wingMood[0] -= 2;
                wingMood[1] += 2;
                push = 0.08 + (competence(Post.DEFENSE) - 60) / 400.0;
                consequence("A carrier group moves into the " + crisisKind.label + ". Oil jumps on the risk.");
            }
            case STRIKE -> {
                deterrence += 12;
                wingMood[0] -= 5;
                wingMood[1] += 1;
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
                modSwing -= 1.5;
                modBase -= 0.5;
                reaction("LEAK: your " + postName(p) + " tells reporters the decision was a mistake.");
            }
        }
    }

    /**
     * The adversary weighs pressure against resolve. Force works on a rival with low resolve and provokes one
     * with high resolve (a security dilemma), so the intelligence estimate, not a fixed rule, decides the right move.
     */
    private void adversaryResponds(double push, CrisisMove m) {
        double pressure = (deterrence - 50) / 100.0 + push - (resolve - 0.5) + 0.15 * rng.gaussian("crisis.adversary");
        double forcefulness = switch (m) { case DE_ESCALATE -> 0.0; case HOLD -> 0.1; case SHOW_FORCE -> 0.35; case STRIKE -> 0.6; };
        double escalateChance = clamp(0.04 + 0.9 * (resolve - 0.5) + forcefulness * (resolve - 0.35) * 2.0 - (deterrence - 50) / 200.0, 0.02, 0.85);
        lastEscalationRisk = escalateChance;
        if (pressure > 0.22 && !(forcefulness > 0.3 && rng.chance("crisis.provoked", Math.max(0, resolve - 0.55) * 1.5))) {
            endCrisis(CrisisOutcome.BACKED_DOWN, "The other side stands down. The " + crisisKind.label + " crisis is over.");
        } else if (rng.chance("crisis.escalate", escalateChance)) {
            escalation++;
            if (escalation >= CLASH_RUNG) {
                endCrisis(CrisisOutcome.CLASH, "An armed clash in the " + crisisKind.label + ": American sailors are killed.");
                modBase += 1.5;
                modSwing += 1.0;   // a brief rally, outweighed by the lasting cost in the record, markets and oil
                oil += crisisKind == CrisisKind.GULF ? 18 : 6;
                confidence -= 8;
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
        if (o == CrisisOutcome.BACKED_DOWN) { modSwing += 2; modBase += 2; deterrence += 5; recordSwing += 1.0; }
        if (o == CrisisOutcome.DEAL) { modSwing += 1; recordSwing += 1.0; }
        if (o == CrisisOutcome.STALEMATE) { deterrence -= 5; modSwing -= 1; recordSwing -= 0.5; }
        if (o == CrisisOutcome.CLASH) { recordSwing -= 2.5; recordBase -= 1.0; }
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
                        intelEstimate > 0.65 ? "high" : intelEstimate > 0.45 ? "moderate" : "low", intelEstimate * 100, noise * 100, deterrence),
                options);
    }

    private double ceasefireChance() { return clamp01(0.25 + 0.5 * competence(Post.STATE) / 100.0); }

    private void aftermath(int option) {
        switch (option) {
            case 0 -> { deterrence += 10; oil += crisisKind == CrisisKind.GULF ? 5 : 2; modBase += 2; wingMood[1] += 3; wingMood[0] -= 4;
                        modSwing -= 1; deficitChange += 0.05; consequence("You retaliate. The other side absorbs the blow; the region braces."); }
            case 1 -> {
                if (rng.chance("crisis.ceasefire", ceasefireChance())) { recordSwing += 1.5; oil -= 4; consequence("Allies broker a ceasefire. Markets exhale."); }
                else { deterrence -= 5; modSwing -= 1; consequence("The ceasefire talks collapse before they begin."); }
            }
            case 2 -> { require(capital >= 6, "Not enough capital"); capital -= 6; goodwill += 6; modSwing += 1; modOpp += 1.5;
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
            case PRIORITIES -> { wingMood[0] += 4; goodwill -= 6; }
            case ACCEPT_CUTS -> { wingMood[0] -= 6; wingMood[1] += 2; goodwill += 8; deficitChange -= 0.05; modSwing -= 0.5; }
            case DEFENSE_DEAL -> { deterrence += 10; deficitChange += 0.05; wingMood[0] -= 3; wingMood[1] += 3; goodwill += 3; }
        }
        consequence("Your stopgap offer: " + o.label.toLowerCase() + ".");
    }

    public double fundingPassChance(FundingOffer o) {
        int i = opposition.ordinal();
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
                    fundingPassChance(o) * 100, opposition.name().toLowerCase())));
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
            boolean blamed = goodwill < -10;
            modSwing -= blamed ? 2.0 : 1.0;
            confidence -= 3;
            unemployment += 0.03;
            reaction("Shutdown, week " + shutdownWeeks + (blamed ? ": voters mostly blame you." : ": voters blame both sides."));
            raise(Decision.Kind.SHUTDOWN, "The shutdown continues", "Week " + shutdownWeeks + " of the shutdown.",
                    List.of(new Option("Hold firm", "The opposition may cave (more likely while swing voters back you)."),
                            new Option("Accept the opposition's cuts to reopen", "Ends it now; your base wing is angry; deficit falls.")));
        }
    }

    private void shutdownChoice(int option) {
        if (option == 1) {
            shutdown = false;
            wingMood[0] -= 6;
            goodwill += 6;
            deficitChange -= 0.05;
            consequence("You accept cuts. The government reopens.");
        } else if (rng.chance("budget.cave", 0.2 + Math.max(0, swing - 48) * 0.03)) {
            shutdown = false;
            wingMood[0] += 4;
            goodwill -= 4;
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
        capital = Math.max(0, Math.min(120, capital + (approval() - 45) * 0.2 + 1));

        Briefing b = new Briefing(week, headline(), weekConsequences, weekReactions, intelligenceLines());
        briefings.add(b);
        weekConsequences.clear();
        weekReactions.clear();

        week++;
        attention = ATTENTION_PER_WEEK;
        billActedThisWeek = false;
        insistedThisWeek = false;
        publicPressure *= 0.8;
        recentAddresses *= 0.5;
        if (FED_WEEKS.contains(week - 1)) leanedOnFed = false;
        if (week <= WEEKS) {
            crisisWeek();
            fundingWeek();
        }
    }

    private void economyWeek() {
        // Oil follows the crisis; gas follows oil.
        double premium = crisis == CrisisOutcome.ACTIVE ? (crisisKind == CrisisKind.GULF ? 6 : 2) * (escalation + 1) : 0;
        double oilTarget = 75 + premium - sprRelief;
        oil += 0.4 * (oilTarget - oil) + 1.2 * rng.gaussian("economy.oil");
        if (sprRelief > 0) sprRelief = Math.max(0, sprRelief - 1);
        double gas = gas();

        // Inflation and jobs move slowly; markets move fast.
        double expectedInflation = 2.0 + 0.4 * (inflation - 2.0) + (1 - fedCredibility) * 1.5;
        double inflationTarget = 2.55 + 0.25 * (gas - 3.2) + 0.3 * demandImpulse + (1 - fedCredibility) * 0.8;
        inflation += 0.06 * (inflationTarget - inflation) + 0.02 * rng.gaussian("economy.prices");
        double uTarget = 4.4 - 0.3 * demandImpulse + (shutdown ? 0.1 : 0) + (crisis == CrisisOutcome.CLASH ? 0.15 : 0);
        unemployment += 0.06 * (uTarget - unemployment) + 0.015 * rng.gaussian("economy.jobs");
        double treasury = (70 - competence(Post.TREASURY)) * 0.004;
        // Bond markets price a live bill's deficit before it passes, weighted by the odds it becomes law.
        boolean live = stage == BillStage.COMMITTEE || stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR;
        double pendingDeficit = live ? size.deficit * payFor.deficitShare * 0.6 : 0;
        double tenTarget = 0.55 * fedRate + 0.45 * (1.5 + expectedInflation) + 0.6 * Math.max(0, deficitChange) + 0.6 * pendingDeficit + treasury
                + (1 - fedCredibility) * 0.6;
        tenYear += 0.3 * (tenTarget - tenYear) + 0.03 * rng.gaussian("economy.bonds");
        double confTarget = 100 - 6 * (gas - 3.2) - 4 * (inflation - 2.8) - (shutdown ? 3 : 0) - 2 * (crisis == CrisisOutcome.ACTIVE ? escalation + 1 : 0);
        confidence += 0.3 * (confTarget - confidence);

        // The Fed meets twice: a Taylor-style rule on the data it sees.
        if (FED_WEEKS.contains(week)) {
            double target = 0.6 + reportedInflation + 0.5 * (reportedInflation - 2.0) + (4.4 - reportedUnemployment) + 0.5 * deficitChange;
            double threshold = leanedOnFed ? 0.15 : 0.2; // pressure nudges a close call, and costs credibility
            double before = fedRate;
            if (target - fedRate > threshold) fedRate += 0.25;
            else if (fedRate - target > threshold) fedRate -= 0.25;
            consequence(fedRate > before ? String.format("The Fed raises rates to %.2f%%, citing inflation and the deficit.", fedRate)
                    : fedRate < before ? String.format("The Fed cuts rates to %.2f%%.", fedRate)
                    : String.format("The Fed holds at %.2f%%.", fedRate));
        }
        if (JOBS_WEEKS.contains(week)) {
            double was = reportedUnemployment;
            reportedUnemployment = Math.round((unemployment + 0.08 * rng.gaussian("economy.release")) * 10) / 10.0;
            modSwing -= 2.0 * (reportedUnemployment - was);
            consequence(String.format("Jobs report: unemployment %.1f%% (was %.1f%%).", reportedUnemployment, was));
        }
        if (CPI_WEEKS.contains(week)) {
            double was = reportedInflation;
            reportedInflation = Math.round((inflation + 0.08 * rng.gaussian("economy.release")) * 10) / 10.0;
            modSwing -= 2.0 * (reportedInflation - was);
            consequence(String.format("Inflation report: %.1f%% over the year (was %.1f%%).", reportedInflation, was));
        }
    }

    private void politicalReaction() {
        // Voters in three groups.
        honeymoon *= 0.85;
        modBase *= 0.85;
        modSwing *= 0.85;
        modOpp *= 0.85;
        double gas = gas(), mortgage = tenYear + 1.9;
        double econ = -4 * (gas - 3.2) - 2 * (inflation - 2.8) - 2 * (unemployment - 4.4) - 1.5 * (mortgage - 6.0) + 0.05 * (confidence - 100);
        double avgMood = (wingMood[0] + wingMood[1]) / 2;
        double baseTarget = 82 + (avgMood - 60) * 0.4 + 0.3 * econ + modBase + recordBase;
        double swingTarget = 50 + honeymoon + econ + goodwill * 0.08 + modSwing + recordSwing;
        double oppTarget = 12 + goodwill * 0.12 + 0.3 * econ + modOpp;
        base += 0.3 * (clamp(baseTarget, 40, 97) - base) + 0.5 * rng.gaussian("opinion");
        swing += 0.3 * (clamp(swingTarget, 15, 85) - swing) + 0.8 * rng.gaussian("opinion");
        opp += 0.3 * (clamp(oppTarget, 2, 50) - opp) + 0.5 * rng.gaussian("opinion");

        // Party wings drift back toward neutral slowly, and revolt once if pushed too far.
        for (int w = 0; w < 2; w++) {
            wingMood[w] = clamp(wingMood[w] + 0.05 * (60 - wingMood[w]), 0, 100);
            if (wingMood[w] < 32 && !revolted[w] && week < WEEKS) {
                revolted[w] = true;
                reaction("The " + wingName(w) + " are in open revolt.");
                raise(Decision.Kind.FACTION_REVOLT, "Revolt: the " + wingName(w),
                        "Members aligned with the " + wingName(w) + " threaten to oppose your agenda.",
                        List.of(new Option("Make amends", "Capital -5; wing mood +12; opposition goodwill -3."),
                                new Option("Face them down", "Wing mood -5; their blocs' relationship falls; independents approve (+1).")));
            }
        }

        // The opposition chooses a strategy (RD), with hysteresis so it does not flip-flop weekly.
        // Events outside the president's control (primaries, scandals elsewhere) jostle the opposition a little.
        goodwill = clamp(goodwill * 0.96 + 2.0 * rng.gaussian("opposition.mood"), -50, 50);
        double score = goodwill + 0.5 * (swing - 50) - 4 + (week <= 3 ? 8 : 0);
        OppositionStrategy before = opposition;
        double coopLine = opposition == OppositionStrategy.COOPERATE ? 5 : 10;
        double obstructLine = opposition == OppositionStrategy.OBSTRUCT ? -5 : -10;
        opposition = score > coopLine ? OppositionStrategy.COOPERATE : score < obstructLine ? OppositionStrategy.OBSTRUCT : OppositionStrategy.SELECTIVE;
        if (opposition != before) reaction("Opposition leaders shift to " + opposition.name().toLowerCase()
                + (opposition == OppositionStrategy.OBSTRUCT ? ": they will fight everything." : opposition == OppositionStrategy.COOPERATE ? ": they will deal." : ": they will pick their fights."));
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
        double fit = 1.0 - 0.9 * Math.abs(ideology - b.ideology());
        double party;
        if (b.party() == this.party) {
            double mood = wingOf(b) < 0 ? (wingMood[0] + wingMood[1]) / 2 : wingMood[wingOf(b)];
            party = 0.5 * b.loyalty() * (0.5 + mood / 100.0);
        } else {
            party = b.loyalty() * switch (opposition) { case COOPERATE -> -0.10; case SELECTIVE -> -0.30; case OBSTRUCT -> -0.50; };
        }
        double rel = 0.25 * relationship.get(b.name());
        double deficit = size.deficit * payFor.deficitShare;
        double hawk = b.deficitHawk() && deficit > 0.1 ? -0.6 * deficit : 0;
        double moderate = Math.abs(b.ideology()) < 0.3 ? 0.04 * publicPressure * Math.max(0, (swing - 40) / 10.0) : 0;
        return clamp01(fit + party + rel + hawk + moderate + pressured.getOrDefault(b.name(), 0.0));
    }

    private String wants(Bloc b, double ideology) {
        boolean own = b.party() == party;
        if (b.deficitHawk() && size.deficit * payFor.deficitShare > 0.1) return "a smaller deficit: a different pay-for or a smaller bill";
        if (own && wingOf(b) == 0) return size == BillSize.LARGE ? "projects back home" : "a bigger bill";
        if (!own && opposition == OppositionStrategy.OBSTRUCT) return "nothing you can offer while their leaders obstruct";
        if (Math.abs(ideology - b.ideology()) > 0.5) return own ? "a more moderate bill" : "a bill closer to their views";
        return "projects or an appointment";
    }

    public double expectedYes(boolean house) { return whip(house).stream().mapToDouble(WhipRead::expectedYes).sum(); }

    /** Pass probability for the vote at {@code s} (normal approximation of the floor noise). */
    public double passChance(BillStage s) {
        boolean house = s == BillStage.HOUSE_FLOOR;
        double expected = expectedYes(house);
        int needed = house ? Congress.HOUSE_MAJORITY : senateNeeded();
        double sd = house ? 4.0 : 1.6;
        double z = (expected - (needed - 0.5)) / sd;
        return 1.0 / (1.0 + Math.exp(-1.7 * z));
    }

    private List<String> intelligenceLines() {
        List<String> lines = new ArrayList<>();
        if (stage == BillStage.HOUSE_FLOOR || stage == BillStage.SENATE_FLOOR)
            lines.add(String.format("Whip: %.0f%% to pass the %s (%s).", passChance(stage) * 100, stage == BillStage.HOUSE_FLOOR ? "House" : "Senate",
                    stage == BillStage.SENATE_FLOOR ? senateNeeded() + " votes needed" : "218 needed"));
        lines.add(String.format("Approval %.0f%% (your voters %.0f, swing %.0f, opposition voters %.0f). Opposition: %s.",
                approval(), base, swing, opp, opposition.name().toLowerCase()));
        lines.add(String.format("Gas $%.2f, 10-year %.2f%%, mortgages %.2f%%, Fed %.2f%%.", gas(), tenYear, tenYear + 1.9, fedRate));
        return lines;
    }

    private String headline() {
        if (!weekReactions.isEmpty() && weekReactions.get(0).startsWith("CRISIS")) return weekReactions.get(0);
        if (!weekConsequences.isEmpty()) return weekConsequences.get(weekConsequences.size() - 1);
        return "A quiet week in Washington.";
    }

    public Report report() {
        int confirmed = (int) seat.values().stream().filter(s -> s == SeatStatus.CONFIRMED).count();
        double avgComp = (competence(Post.TREASURY) + competence(Post.DEFENSE) + competence(Post.STATE)) / 3.0;
        double midtermMargin = 0.6 * (approval() - 48) - 2.5;
        double partial = stage == BillStage.SENATE_FLOOR ? 0.25 * size.value : 0;
        return new Report(approval(), base, swing, opp, stage, policyDelivered + partial, confirmed, avgComp, inflation, unemployment,
                tenYear, gas(), deficitChange, crisis, deterrence, wingMood[0], wingMood[1], opposition, goodwill, capital, shutdownWeeks,
                (int) Math.round(5.5 * midtermMargin));
    }

    // =============================================================================================
    //  Accessors and helpers
    // =============================================================================================

    public double approval() { return 0.33 * base + 0.34 * swing + 0.33 * opp; }
    public double gas() { return 1.3 + 0.0255 * oil; }
    public boolean over() { return week > WEEKS; }
    public int week() { return week; }
    public int attention() { return attention; }
    public double capital() { return capital; }
    public Party party() { return party; }
    public OpeningSetup setup() { return setup; }
    public Congress congress() { return congress; }
    public BillStage stage() { return stage; }
    public Optional<BillSize> size() { return Optional.ofNullable(size); }
    public Optional<PayFor> payFor() { return Optional.ofNullable(payFor); }
    public boolean reconciliation() { return reconciliation; }
    public SeatStatus seat(Post p) { return seat.get(p); }
    public Optional<NomineeType> nominee(Post p) { return Optional.ofNullable(nominee.get(p)); }
    public double wingMood(int w) { return wingMood[w]; }
    public String wingName(int w) {
        if (party == Party.DEMOCRAT) return w == 0 ? "Progressives" : "Moderates";
        return w == 0 ? "Populists" : "Traditionalists";
    }
    public OppositionStrategy opposition() { return opposition; }
    public double goodwill() { return goodwill; }
    public CrisisOutcome crisis() { return crisis; }
    public CrisisKind crisisKind() { return crisisKind; }
    public int escalation() { return escalation; }
    public double deterrence() { return deterrence; }
    public double intelEstimate() { return intelEstimate; }
    public double lastEscalationRisk() { return lastEscalationRisk; }
    double hiddenResolve() { return resolve; }
    public double oil() { return oil; }
    public double tenYear() { return tenYear; }
    public double inflation() { return inflation; }
    public double fedRate() { return fedRate; }
    public double fedCredibility() { return fedCredibility; }
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
        wingMood[0] += sign * a;
        wingMood[1] += sign * bm;
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
