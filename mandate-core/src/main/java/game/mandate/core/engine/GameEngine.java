package game.mandate.core.engine;

import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignParams;
import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.ElectionEngine;
import game.mandate.core.campaign.ElectoralEnvironment;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.BillCatalog;
import game.mandate.core.congress.Congress;
import game.mandate.core.congress.CongressEngine;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.PolicyState;
import game.mandate.core.congress.LegislativeContext;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.data.Json;
import game.mandate.core.data.Tuning;
import game.mandate.core.diplomacy.Country;
import game.mandate.core.diplomacy.DiplomacyEngine;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.diplomacy.Escalation;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyEngine;
import game.mandate.core.economy.EconomyParams;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.economy.MarketContext;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.events.DecisionResolver;
import game.mandate.core.events.EventCatalog;
import game.mandate.core.events.EventDefinition;
import game.mandate.core.events.EventScheduler;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.events.WorldView;
import game.mandate.core.executive.CabinetPool;
import game.mandate.core.executive.ExecutiveEngine;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.executive.Post;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Legacy;
import game.mandate.core.military.MilitaryCatalog;
import game.mandate.core.military.MilitaryEngine;
import game.mandate.core.military.MilitaryParams;
import game.mandate.core.military.TheaterOutcome;
import game.mandate.core.military.TheaterScenario;
import game.mandate.core.opening.Host;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.DayHundredReport;
import game.mandate.core.politics.Coalition;
import game.mandate.core.politics.OpinionInputs;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.politics.PoliticsParams;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.politics.Role;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.time.CampaignCalendar;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock;
import game.mandate.core.time.SimulationClock.Granularity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The game engine: owns turn order, routes every state transition to the engine that owns the state it changes,
 * and records each one in the {@link ConsequenceLedger}. It holds no domain variables of its own; everything
 * persistent is in the {@link WorldState}.
 *
 * <p>The only way to change a game is {@link #apply(Command)}. Every command is appended to the history, so
 * {@link #replay(GameSetup, List)} reproduces a game exactly, and the save manager can also restore the world
 * directly from a snapshot. (This class was called {@code Game} in the foundation; its public API is kept.)
 */
public final class GameEngine implements WorldView {
    private final GameSetup setup;
    private final Tuning tuning;
    private final StateCatalog states;
    private final CandidateCatalog candidates;
    private final BillCatalog bills;
    private final CampaignCalendar calendar;
    private final List<EventDefinition> eventDefinitions;
    private final DeterministicRandomManager rng;

    private final EconomyEngine economyEngine;
    private final PoliticsEngine politicsEngine;
    private final CongressEngine congressEngine;
    private final ExecutiveEngine executiveEngine;
    private final DiplomacyEngine diplomacyEngine;
    private final MilitaryEngine militaryEngine;
    private final ElectionEngine electionEngine;
    private final DecisionResolver decisions = new DecisionResolver();

    private final double premiumPerMiddleEast, middleEastPivot, premiumPerRung, stressPerRung, stressWar;
    private final double deterrenceOrder, munitionsOrder, tensionOrder, deficitOrder;
    private final int hundredDaysWeeks;
    private final EngineHost host = new EngineHost();

    private WorldState w;
    private final List<Command> history = new ArrayList<>();
    /** This week's state polls, computed once between changes to the world (cleared by every command and transition). */
    private Map<String, Double> pollMemo;

    public GameEngine(GameSetup setup) { this(setup, Tuning.defaults()); }

    public GameEngine(GameSetup setup, Tuning tuning) {
        this.setup = setup;
        this.tuning = tuning;
        this.states = StateCatalog.load();
        this.candidates = CandidateCatalog.load(setup.realRoster()).with(setup.customCandidate());
        this.bills = BillCatalog.load();
        this.calendar = CampaignCalendar.load();
        this.eventDefinitions = EventCatalog.standard();
        this.rng = new DeterministicRandomManager(setup.seed());
        this.economyEngine = new EconomyEngine(EconomyParams.from(tuning));
        this.politicsEngine = new PoliticsEngine(PoliticsParams.from(tuning));
        this.congressEngine = new CongressEngine(tuning);
        this.executiveEngine = new ExecutiveEngine(tuning, CabinetPool.load());
        this.diplomacyEngine = new DiplomacyEngine(tuning, Country.load());
        this.militaryEngine = new MilitaryEngine(MilitaryParams.from(tuning), MilitaryCatalog.load());
        this.electionEngine = new ElectionEngine(states, candidates, CampaignParams.from(tuning));
        premiumPerMiddleEast = tuning.d("markets.premium.middleEast");
        middleEastPivot = tuning.d("markets.premium.middleEastPivot");
        premiumPerRung = tuning.d("markets.premium.taiwanRung");
        stressPerRung = tuning.d("markets.stress.taiwanRung");
        stressWar = tuning.d("markets.stress.war");
        deterrenceOrder = tuning.d("orders.deterrence.deterrence");
        munitionsOrder = tuning.d("orders.deterrence.munitionsSurge");
        tensionOrder = tuning.d("orders.deterrence.tension");
        deficitOrder = tuning.d("orders.deterrence.deficit");
        hundredDaysWeeks = tuning.i("hundredDays.weeks");

        validateConfiguration(tuning);

        Candidate mine = candidates.get(setup.playerCandidateId());
        Candidate theirs = candidates.get(setup.opponentCandidateId());
        if (mine.party() != setup.playerParty() || theirs.party() != setup.playerParty().opponent())
            throw new IllegalArgumentException("Candidates do not match the chosen party");
        String sc = "scenario." + setup.scenario().name() + ".";

        w = new WorldState();
        w.clock = new SimulationClock(calendar.campaignStart(), Granularity.WEEKLY);
        w.phase = Phase.GENERAL_CAMPAIGN_2028;
        w.election = electionEngine.newElection(mine, theirs, setup.difficulty().opponentResources(), rng);
        w.economy = economyEngine.newEconomy();
        w.budget = economyEngine.newBudget();
        w.politics = politicsEngine.newPolitics(candidates, 0);
        politicsEngine.setLeader(w.politics, mine.party(), mine.id());
        politicsEngine.setLeader(w.politics, theirs.party(), theirs.id());
        w.congress = congressEngine.newCongress(tuning.i(sc + "houseDem"), tuning.i(sc + "senateDem"));
        w.policy = congressEngine.newPolicy();
        w.government = executiveEngine.newGovernment();
        w.diplomacy = diplomacyEngine.newDiplomacy();
        w.military = militaryEngine.newMilitary();
        w.scheduler = new EventScheduler();
        w.ledger = new ConsequenceLedger();
        electionEngine.setOpponentPledges(w.election, CampaignAi.pledges(this, w.election.opponent()));
        electionEngine.markBaseline(w.election, environment());
        electionEngine.recordPoll(w.election, environment(), date(), calendar.election2028().getYear(), pollSeed());
        log("The general election begins: " + mine.name() + " (" + mine.party().code() + ") vs " + theirs.name() + " (" + theirs.party().code() + ").");
    }

    /** Fails fast on a broken tuning file: every scenario and every legacy rule must be readable at start-up. */
    static void validateConfiguration(Tuning t) {
        for (game.mandate.core.game.Scenario sc : game.mandate.core.game.Scenario.values())
            for (String k : List.of("environment", "houseDem", "senateDem")) t.d("scenario." + sc.name() + "." + k);
        Legacy.validate(t);
    }

    public static GameEngine replay(GameSetup setup, List<Command> commands) {
        GameEngine g = new GameEngine(setup);
        for (Command c : commands) g.apply(c);
        return g;
    }

    /** Rebuilds a game from a snapshot: world state, random state and command history. No replay. */
    static GameEngine restore(GameSetup setup, Map<String, Object> world, Map<String, Long> random, List<Command> commands) {
        GameEngine g = new GameEngine(setup);
        g.w = WorldState.fromJson(world, g.candidates);
        g.rng.restore(random);
        g.history.addAll(commands);
        g.pollMemo = null;
        return g;
    }

    DeterministicRandomManager random() { return rng; }

    // =========================================================================================
    //  Commands
    // =========================================================================================

    public void apply(Command command) {
        pollMemo = null;
        switch (command) {
            case Command.PlanCampaign c -> { requireCampaign(); electionEngine.setPendingPlan(w.election, c.plan()); }
            case Command.SetCampaignOrders c -> { requireCampaign(); electionEngine.setOrders(w.election, true, c.orders(), ""); }
            case Command.Pledge c -> pledge(c.billId());
            case Command.ChooseDebatePosture c -> {
                requireCampaign();
                if (debateThisWeek().isEmpty()) throw new IllegalCommandException("No debate this week");
                electionEngine.setPendingPosture(w.election, c.posture());
            }
            case Command.SetAgenda c -> setAgenda(c.billIds());
            case Command.PushBill c -> pushBill(c.billId(), c.strategy(), c.capital());
            case Command.PublicAddress c -> publicAddress(c.issue());
            case Command.BolsterDeterrence c -> bolsterDeterrence();
            case Command.Diplomacy c -> diplomacy(c.hotSpot());
            case Command.ResolveEvent c -> resolveEvent(c.instanceId(), c.optionIndex());
            case Command.Act c -> act(c.action());
            case Command.EndTurn c -> endTurn();
        }
        history.add(command);
        w.commandsApplied++;
    }

    // =========================================================================================
    //  Transitions: every cross-system change passes through here
    // =========================================================================================

    /** Routes one transition to the engine(s) owning the state it changes and records it in the ledger. */
    void dispatch(int decisionId, String cause, Effect e) {
        pollMemo = null;
        boolean governing = w.politics.governing();
        String owner;
        switch (e) {
            case Effect.Approval x -> {
                if (governing) { politicsEngine.apply(w.politics, x); owner = "politics"; }
                else { electionEngine.apply(w.election, x); owner = "election"; }
            }
            case Effect.CoalitionApproval x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Capital x -> owner = politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Salience x -> owner = politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Goodwill x -> owner = politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.WingMood x -> owner = politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.EconomyShock x -> owner = governing && economyEngine.apply(w.economy, w.budget, x) ? "economy" : "none";
            case Effect.Deficit x -> owner = governing && economyEngine.apply(w.economy, w.budget, x) ? "economy" : "none";
            case Effect.FedPressure x -> owner = governing && economyEngine.apply(w.economy, w.budget, x) ? "economy" : "none";
            case Effect.OilRelease x -> owner = governing && economyEngine.apply(w.economy, w.budget, x) ? "economy" : "none";
            case Effect.CampaignSwing x -> owner = electionEngine.apply(w.election, x) ? "election" : "none";
            case Effect.Cash x -> owner = electionEngine.apply(w.election, x) ? "election" : "none";
            case Effect.Tension x -> owner = diplomacyEngine.apply(w.diplomacy, x) ? "diplomacy" : "none";
            case Effect.Deterrence x -> owner = diplomacyEngine.apply(w.diplomacy, x) ? "diplomacy" : "none";
            case Effect.WarEnded x -> owner = diplomacyEngine.apply(w.diplomacy, x) ? "diplomacy" : "none";
            case Effect.Munitions x -> owner = militaryEngine.apply(w.military, x) ? "military" : "none";
            case Effect.CongressElected x -> owner = congressEngine.apply(w.congress, x) ? "congress" : "none";
            case Effect.PresidentInaugurated x -> {
                executiveEngine.apply(w.government, x, date());
                politicsEngine.apply(w.politics, x);
                owner = "executive+politics";
            }
        }
        w.ledger.record(decisionId, date(), cause, owner, e.describe());
    }

    private void dispatchAll(int decisionId, String cause, List<Effect> effects) {
        for (Effect e : effects) dispatch(decisionId, cause, e);
    }

    // =========================================================================================
    //  Turn resolution
    // =========================================================================================

    private void endTurn() {
        if (w.scheduler.hasPending()) throw new IllegalCommandException("Decide the pending events first");
        if (w.military.operationActive()) { operationalTurn(); return; }
        switch (w.phase) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaignWeek();
            case TRANSITION -> beginTerm();
            case FIRST_100_DAYS -> act(new OpeningAction.EndWeek());
            case GOVERNING -> governingMonth();
            case EPILOGUE -> w.phase = Phase.GAME_OVER;
            case GAME_OVER -> throw new IllegalCommandException("The game is over");
        }
    }

    private void campaignWeek() {
        if (debateThisWeek().isPresent() && w.election.pendingPosture() == null)
            throw new IllegalCommandException("Choose a debate approach first");
        // The opponent reads the same polls the player sees and adapts its standing orders; its reasoning is shown.
        CampaignAi.Adaptation a = setup.difficulty().adaptive() ? CampaignAi.adapt(this, w.election.opponent())
                : new CampaignAi.Adaptation(game.mandate.core.campaign.CampaignStrategy.Orders.DEFAULT, "It runs the same campaign every week: rallies and money in the closest states.");
        if (!a.orders().equals(w.election.opponentOrders()) || w.election.opponentReasoning().isEmpty())
            log("Your opponent's campaign: " + a.orders().describe() + ". " + a.reasoning());
        electionEngine.setOrders(w.election, false, a.orders(), a.reasoning());
        CampaignWeekPlan mine = w.election.pendingPlan() != null ? w.election.pendingPlan()
                : CampaignAi.plan(this, w.election.player(), 1.0, w.election.playerOrders());
        CampaignWeekPlan theirs = CampaignAi.plan(this, w.election.opponent(), setup.difficulty().opponentResources(), w.election.opponentOrders());
        DebatePosture myPosture = w.election.pendingPosture();
        Optional<DebatePosture> theirPosture = debateThisWeek().map(d -> CampaignAi.debatePosture(w.election.opponent().candidate(), w.election.player().candidate(), rng));
        pollMemo = null; // the plans are about to change the race
        electionEngine.campaignWeek(w.election, mine, theirs, w.politics.salience(), theirPosture, rng).ifPresent(forPlayer ->
                log("Debate: you chose \"" + myPosture.label() + "\", your opponent \"" + theirPosture.get().label() + "\". "
                        + (forPlayer > 0.3 ? "You won the night" : forPlayer < -0.3 ? "Your opponent won the night" : "A draw")
                        + String.format(" (%+.1f).", forPlayer)));
        rollEvents();
        w.clock.advance();
        LocalDate electionDay = w.phase == Phase.GENERAL_CAMPAIGN_2028 ? calendar.election2028() : calendar.election2032();
        electionEngine.recordPoll(w.election, environment(), date().isAfter(electionDay) ? electionDay : date(), electionDay.getYear(), pollSeed());
        if (!date().isBefore(electionDay)) {
            w.clock.set(electionDay);
            holdPresidentialElection();
        }
    }

    private void holdPresidentialElection() {
        boolean first = w.phase == Phase.GENERAL_CAMPAIGN_2028;
        int year = first ? 2028 : 2032;
        ElectionEngine.PresidentialOutcome o = electionEngine.presidential(w.election, year, environment(), w.congress.congress(), first, rng);
        ElectionResult r = o.result();
        int decision = w.ledger.decide(date(), "Election " + year);
        dispatch(decision, "Election " + year, o.congress());
        Party winner = r.winner();
        log(String.format("Election Night %d: %s %d, %s %d. Popular vote %s%+.1f. Tipping point: %s.", year,
                Party.DEMOCRAT.code(), r.demElectoralVotes(), Party.REPUBLICAN.code(), r.repElectoralVotes(),
                r.popularMargin() >= 0 ? "D" : "R", Math.abs(r.popularMargin()), r.tippingPoint()));
        String loserId = (winner == player().party() ? opponent() : player()).candidate().id();
        politicsEngine.setRole(w.politics, loserId, Role.DEFEATED);
        if (first) {
            if (winner == player().party()) {
                log("You won. " + congress() + ". Choose your agenda.");
                w.phase = Phase.TRANSITION;
                w.clock.set(calendar.inauguration().minusWeeks(calendar.transitionWeeks()));
            } else {
                log("You lost the presidency.");
                finish();
            }
        } else {
            log(winner == player().party() ? "You are re-elected." : "You are defeated for re-election.");
            finish();
        }
    }

    private void finish() {
        w.legacy = Legacy.score(this);
        log(w.legacy.headline());
        w.phase = Phase.EPILOGUE;
    }

    private void pledge(String id) {
        requireCampaign();
        if (w.phase != Phase.GENERAL_CAMPAIGN_2028) throw new IllegalCommandException("Promises are made in the 2028 campaign");
        Bill b = bill(id);
        String refusal = electionEngine.pledgeRefusal(w.election, b);
        if (refusal != null) throw new IllegalCommandException(refusal);
        int decision = w.ledger.decide(date(), "Promise: " + b.name());
        dispatchAll(decision, "Promise " + id, electionEngine.pledge(w.election, b));
        log("You promise to pass the " + b.name() + ".");
    }

    /** The transition: the agenda. The first priority is the flagship of the first hundred days; each dropped promise costs the base. */
    private void setAgenda(List<String> ids) {
        if (w.phase != Phase.TRANSITION) throw new IllegalCommandException("The agenda is set during the transition");
        if (ids.isEmpty() || ids.size() > 3) throw new IllegalCommandException("Choose one to three priorities");
        if (ids.stream().distinct().count() != ids.size()) throw new IllegalCommandException("Each priority once");
        for (String id : ids) {
            Bill b = bill(id);
            if (!b.availableTo(player().party())) throw new IllegalCommandException(b.name() + " belongs to the other party");
        }
        w.ledger.decide(date(), "Agenda: " + String.join(", ", ids));
        executiveEngine.setAgenda(w.government, ids);
        for (String promised : brokenPromises()) log("Your agenda leaves out your promise to pass the " + bill(promised).name() + ". Your base will notice.");
    }

    /** Campaign promises the agenda leaves out. */
    public List<String> brokenPromises() { return w.election.pledges().stream().filter(id -> !w.government.agenda().contains(id)).toList(); }

    /** Campaign promises kept so far: promised, on the agenda and law. */
    public long promisesKept() { return w.election.pledges().stream().filter(w.policy::inForce).count(); }

    /**
     * Inauguration: the election's result becomes the next administration. The mandate (electoral votes) sets the
     * opening capital and swing-voter goodwill, Congress comes from the same election, and the first agenda priority
     * becomes the flagship bill of the first hundred days. The cabinet starts empty: appointing it is the player's job.
     */
    private void beginTerm() {
        if (w.government.agenda().isEmpty()) throw new IllegalCommandException("Set your agenda first");
        w.phase = Phase.FIRST_100_DAYS;
        w.clock.set(calendar.inauguration());
        int myEv = w.election.result2028().electoralVotes(player().party());
        int decision = w.ledger.decide(date(), "Inauguration");
        dispatch(decision, "Inauguration", new Effect.PresidentInaugurated(player().candidate().id(), player().party(), myEv));
        politicsEngine.setInitialTerms(w.politics, unifiedGovernment());
        // Each campaign promise left off the agenda costs the president's base wing (the wings exist once there is a president).
        for (String promised : brokenPromises())
            dispatch(decision, "Broken promise " + promised, new Effect.WingMood(0, electionEngine.brokenPledgeMood()));
        log("Inauguration Day. " + player().candidate().name() + " takes the oath with " + myEv + " electoral votes and " + congress()
                + ". Approval " + Math.round(approval()) + "%, political capital " + Math.round(capital()) + ".");
        w.inaugurationCommand = history.size();      // this EndTurn is appended after it completes
        recordIndicators();
        w.clock.set(hundredDaysStart());
        w.hundredDays = HundredDays.begin(host);
        log("The first hundred days begin. Your flagship: the " + host.flagship().name() + ".");
    }

    private void recordIndicators() {
        EconomyState e = w.economy;
        w.indicators.add(new IndicatorPoint(date(), approval(), e.unemployment(), e.inflation(), e.growth(), economyEngine.gas(e), e.tenYear(), e.fedRate(),
                economyEngine.deficit(e, w.budget), w.diplomacy.deterrence()));
    }

    /** The Monday the first week of the hundred days starts. */
    public LocalDate hundredDaysStart() { return calendar.inauguration().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)); }

    /** One action of the first hundred days. A refused action changes nothing (the hundred days validate first). */
    private void act(OpeningAction a) {
        if (w.phase != Phase.FIRST_100_DAYS) throw new IllegalCommandException("Only during the first hundred days");
        int logged = w.hundredDays.log().size();
        host.decision = -1;
        try {
            new HundredDays(host, w.hundredDays).apply(a);
        } catch (IllegalStateException | IllegalArgumentException refused) {
            throw new IllegalCommandException(refused.getMessage());
        }
        for (int i = logged; i < w.hundredDays.log().size(); i++) log(w.hundredDays.log().get(i));
        if (a instanceof OpeningAction.EndWeek) {
            w.clock.advance();
            recordIndicators();
            if (w.hundredDays.week() > w.hundredDays.weeks()) endHundredDays();
        }
    }

    /** Day 100: the report; then the slice ends, or the term goes on in monthly turns with the cabinet filled. */
    private void endHundredDays() {
        DayHundredReport report = new HundredDays(host, w.hundredDays).finish(w.election.pledges(), w.policy.laws());
        log("Day 100: " + report.outcome().label + " (" + report.score() + " of 10). " + report.headline());
        if (setup.length() == GameSetup.Length.HUNDRED_DAYS) {
            w.phase = Phase.EPILOGUE;
            log("The first hundred days are over.");
            return;
        }
        for (String line : executiveEngine.formCabinet(w.government, w.congress.congress(), congressEngine.votes(), w.politics.opposition(),
                w.politics.party(player().party()).wingMood(1), rng)) log(line);
        w.phase = Phase.GOVERNING;
        w.clock.set(date().withDayOfMonth(1).plusMonths(1));
        w.clock.setMode(Granularity.MONTHLY);
        executiveEngine.newTurn(w.government);
        // The world did not stand still: the flashpoints drift for the months the hundred days covered. (During the
        // hundred days their own crisis is the foreign-policy story, so standing escalations wait until now.)
        for (long m = ChronoUnit.MONTHS.between(calendar.inauguration(), date()); m > 0; m--) {
            for (Escalation e : diplomacyEngine.stepMonth(w.diplomacy, rng)) queueEvent(EventCatalog.escalation(e));
            militaryEngine.stepMonth(w.military);
        }
    }

    /** The day-100 report, once the hundred days are over. */
    public Optional<DayHundredReport> dayHundredReport() {
        return w.hundredDays == null ? Optional.empty() : Optional.ofNullable(w.hundredDays.finalReport());
    }

    /** What went wrong, after a lost presidential election. */
    public List<String> postMortem() {
        ElectionResult r = w.election.result2032() != null ? w.election.result2032() : w.election.result2028();
        if (r == null || r.winner() == player().party()) return List.of();
        return electionEngine.postMortem(w.election, r);
    }

    /** A new game from the same start: the same seed, so the same opponent, polling miss and world, for a different strategy. */
    public GameEngine restart() { return new GameEngine(setup); }

    /** The same game rewound to the inauguration: the same election, Congress and mandate, to govern differently. */
    public Optional<GameEngine> replayFromInauguration() {
        if (w.inaugurationCommand < 0) return Optional.empty();
        return Optional.of(replay(setup, history.subList(0, w.inaugurationCommand + 1)));
    }

    /**
     * The first hundred days, for the UI and the AI. Every action taken through this view is applied as a
     * {@link Command.Act}, so it is validated, logged in the history and saved like any other command.
     */
    public Optional<HundredDays> hundredDays() {
        if (w.hundredDays == null) return Optional.empty();
        return Optional.of(new HundredDays(host, w.hundredDays) {
            @Override public void apply(OpeningAction a) { GameEngine.this.apply(new Command.Act(a)); }
        });
    }

    /** The world as the hundred days see it: the authoritative states and engines, and the engine's router. */
    private final class EngineHost implements Host {
        int decision = -1;

        @Override public Party party() { return player().party(); }
        @Override public String presidentName() { return player().candidate().name(); }
        @Override public double presidentCharisma() { return player().candidate().charisma(); }
        @Override public Bill flagship() { return bill(w.government.agenda().get(0)); }
        @Override public LocalDate start() { return hundredDaysStart(); }
        @Override public int weeks() { return hundredDaysWeeks; }
        @Override public PoliticsState politics() { return w.politics; }
        @Override public PoliticsEngine politicsEngine() { return politicsEngine; }
        @Override public EconomyState economy() { return w.economy; }
        @Override public BudgetState budget() { return w.budget; }
        @Override public EconomyEngine economyEngine() { return economyEngine; }
        @Override public DiplomacyState diplomacy() { return w.diplomacy; }
        @Override public DiplomacyEngine diplomacyEngine() { return diplomacyEngine; }
        @Override public CongressState congress() { return w.congress; }
        @Override public PolicyState policy() { return w.policy; }
        @Override public CongressEngine congressEngine() { return congressEngine; }
        @Override public GovernmentState government() { return w.government; }
        @Override public ExecutiveEngine executiveEngine() { return executiveEngine; }
        @Override public ElectionEngine elections() { return electionEngine; }
        @Override public CampaignCalendar calendar() { return calendar; }
        @Override public DeterministicRandomManager rng() { return rng; }
        @Override public Tuning tuning() { return tuning; }

        /** One ledger decision per command, opened by its first transition (so a refused command records nothing). */
        @Override
        public void dispatch(String cause, Effect effect) {
            if (decision < 0) decision = w.ledger.decide(date(), cause);
            GameEngine.this.dispatch(decision, cause, effect);
        }
    }

    private void governingMonth() {
        LocalDate from = date(), to = from.plusMonths(1);
        double weeks = ChronoUnit.DAYS.between(from, to) / 7.0;

        // 1. The economy, which reads diplomacy (oil risk) and the executive (Treasury) through a context.
        economyEngine.step(w.economy, w.budget, 1.0, marketContext(), rng);
        for (int i = 0; i < calendar.fomcMeetings(from, to); i++) {
            double change = economyEngine.fedMeeting(w.economy, w.budget);
            if (change != 0) log(String.format("The Fed %s rates to %.2f%%.", change > 0 ? "raises" : "cuts", w.economy.fedRate()));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, true); i++) politicsEngine.onRelease(w.politics, economyEngine.jobsReport(w.economy, rng));
        for (int i = 0; i < CampaignCalendar.releases(from, to, false); i++) politicsEngine.onRelease(w.politics, economyEngine.inflationReport(w.economy, rng));

        // 2. Public opinion, which reads the economy.
        politicsEngine.step(w.politics, weeks, opinionInputs(), rng);

        // 3. The world and the military.
        for (Escalation e : diplomacyEngine.stepMonth(w.diplomacy, rng)) queueEvent(EventCatalog.escalation(e));
        militaryEngine.stepMonth(w.military);

        rollEvents();
        w.clock.advance();
        recordIndicators();
        executiveEngine.newTurn(w.government);

        if (!w.midtermsHeld && !date().isBefore(calendar.midterms())) holdMidterms();
        if (!date().isBefore(calendar.reelectionStart())) beginReelection();
    }

    private void holdMidterms() {
        w.midtermsHeld = true;
        int before = congress().house(player().party()), senBefore = congress().senate(player().party());
        Effect.CongressElected result = electionEngine.midterm(w.election, player().party(), approval(), w.economy.growth(), congress(),
                calendar.midterms().getYear(), rng);
        int decision = w.ledger.decide(date(), "Midterms " + calendar.midterms().getYear());
        dispatch(decision, "Midterms", result);
        log(String.format("Midterms: your party %+d House seats, %+d Senate seats. %s.",
                congress().house(player().party()) - before, congress().senate(player().party()) - senBefore, congress()));
    }

    private void beginReelection() {
        w.phase = Phase.REELECTION_CAMPAIGN_2032;
        w.clock.set(calendar.reelectionStart());
        w.clock.setMode(Granularity.WEEKLY);
        Candidate challenger = electionEngine.pickChallenger(w.election, rng);
        electionEngine.beginReelection(w.election, challenger, capital(), setup.difficulty().opponentResources(), rng);
        politicsEngine.setLeader(w.politics, challenger.party(), challenger.id());
        electionEngine.markBaseline(w.election, environment());
        electionEngine.recordPoll(w.election, environment(), date(), calendar.election2032().getYear(), pollSeed());
        log("The re-election campaign begins against " + challenger.name() + ".");
    }

    // ---- operational military turns -----------------------------------------------------------

    private void startWar(TheaterScenario.Commitment commitment, int decisionId) {
        TheaterScenario s = new TheaterScenario(w.diplomacy.deterrence(), militaryEngine.munitionsIndex(w.military), w.diplomacy.japanBasing(),
                w.diplomacy.warningMonths(), commitment, executiveEngine.competence(w.government, Post.DEFENSE));
        militaryEngine.startOperation(w.military, s, rng);
        w.clock.setMode(Granularity.OPERATIONAL);
        w.ledger.record(decisionId, date(), "War", "military", "War begins: " + commitment);
        log("WAR IN THE TAIWAN STRAIT. The clock moves to operational turns.");
    }

    private void operationalTurn() {
        double hours = militaryEngine.nextTurnHours(w.military);
        Optional<TheaterOutcome> out = militaryEngine.stepOperation(w.military, rng);
        w.clock.advanceHours(hours);
        out.ifPresent(o -> {
            w.clock.setMode(Granularity.MONTHLY);
            int decision = w.ledger.decide(date(), "War ends: " + o.status());
            dispatchAll(decision, "War", militaryEngine.consequences(o));
            log("WAR ENDS: " + o.summary() + (o.usKilled() > 0 ? " " + o.usKilled() + " Americans killed; " + o.shipsLost() + " ships, "
                    + o.carriersLost() + " carriers and " + o.aircraftLost() + " aircraft lost." : ""));
        });
    }

    // =========================================================================================
    //  Governing commands
    // =========================================================================================

    private void requireGoverning() {
        if (w.phase != Phase.GOVERNING) throw new IllegalCommandException("Only while governing");
        if (w.military.operationActive()) throw new IllegalCommandException("The war has the president's full attention");
    }

    /** The chance an executive order survives the courts. An order needs no vote. */
    public double executiveOrderSurvival() { return 1 - congressEngine.courtBlockChance(); }

    public WhipCount whip(String billId, LegislativeStrategy strategy, double capitalCommitted) {
        return congressEngine.whip(w.congress, bill(billId), strategy, legislativeContext(billId), capitalCommitted);
    }

    private LegislativeContext legislativeContext(String billId) {
        var party = w.politics.party(player().party());
        double appr = w.politics.governing() ? approval() : tuning.d("congress.preInaugurationApproval");
        double swing = w.politics.governing() ? w.politics.approval(Coalition.SWING) : 50;
        return new LegislativeContext(player().party(), appr, swing, w.politics.opposition(), new double[]{party.wingMood(0), party.wingMood(1)},
                w.government.agenda().contains(billId));
    }

    private void pushBill(String id, LegislativeStrategy strategy, double capitalCommitted) {
        Bill b = bill(id);
        requireGoverning();
        String refusal = congressEngine.refusal(w.congress, w.policy, b, strategy, player().party(), date());
        if (refusal != null) throw new IllegalCommandException(refusal);
        if (capitalCommitted < 0 || capitalCommitted > capital()) throw new IllegalCommandException("Not enough political capital");
        executiveEngine.spendAttention(w.government, strategy == LegislativeStrategy.EXECUTIVE_ORDER ? 1 : 2);
        int decision = w.ledger.decide(date(), "Push " + id + " (" + strategy + ", capital " + capitalCommitted + ")");
        dispatch(decision, "Push " + id, new Effect.Capital(-capitalCommitted));
        CongressEngine.Passage p = congressEngine.push(w.congress, w.policy, b, strategy, legislativeContext(id), capitalCommitted, date(), rng);
        log(b.name() + " (" + strategy.label() + "): " + p.explanation());
        if (p.enacted()) log("Signed into law: " + b.name() + ".");
        dispatchAll(decision, "Law " + id, p.consequences());
    }

    private void publicAddress(Issue issue) {
        requireGoverning();
        executiveEngine.spendAttention(w.government, 1);
        int decision = w.ledger.decide(date(), "Address on " + issue);
        double gain = politicsEngine.address(w.politics, player().candidate().charisma(), issue.advantageFor(player().party()), issue);
        w.ledger.record(decision, date(), "Address", "politics", String.format("Approval +%.2f (your voters and swing voters)", gain));
        log("You address the nation on " + issue.label().toLowerCase() + ".");
    }

    private void bolsterDeterrence() {
        requireGoverning();
        executiveEngine.spendAttention(w.government, 1);
        int decision = w.ledger.decide(date(), "Bolster Pacific deterrence");
        dispatchAll(decision, "Deterrence", List.of(new Effect.Deterrence(deterrenceOrder), new Effect.Munitions(munitionsOrder),
                new Effect.Tension(HotSpot.TAIWAN_STRAIT, tensionOrder), new Effect.Deficit(deficitOrder)));
        log("You order more ships, exercises and munitions for the Pacific.");
    }

    private void diplomacy(HotSpot h) {
        requireGoverning();
        executiveEngine.spendAttention(w.government, 1);
        int decision = w.ledger.decide(date(), "Diplomacy: " + h);
        boolean ok = diplomacyEngine.talks(w.diplomacy, h, player().candidate().policy(), executiveEngine.competence(w.government, Post.STATE), rng);
        w.ledger.record(decision, date(), "Diplomacy", "diplomacy", ok ? "Tension eases" : "Talks go nowhere");
        log(ok ? "Your diplomacy on the " + h.label() + " eases tensions." : "Talks on the " + h.label() + " go nowhere.");
    }

    // =========================================================================================
    //  Events and decisions
    // =========================================================================================

    private void rollEvents() {
        for (PendingEvent p : w.scheduler.roll(eventDefinitions, w.phase, this, rng)) log("EVENT: " + p.definition().title());
    }

    void queueEvent(EventDefinition d) {
        w.scheduler.queue(d);
        log("EVENT: " + d.title());
    }

    private void resolveEvent(int instanceId, int optionIndex) {
        DecisionResolver.Resolution r = decisions.resolve(w.scheduler, instanceId, optionIndex, rng);
        log(r.text());
        int decision = w.ledger.decide(date(), r.text());
        dispatchAll(decision, "Event " + r.event().definition().id(), r.effects());
        r.hook().ifPresent(h -> {
            if (!h.startsWith("theater:")) throw new IllegalStateException("Unknown hook " + h);
            startWar(TheaterScenario.Commitment.valueOf(h.substring("theater:".length())), decision);
        });
    }

    // =========================================================================================
    //  Contexts: what one system reads from the others, assembled in one place
    // =========================================================================================

    private MarketContext marketContext() {
        double premium = Math.max(0, w.diplomacy.tension(HotSpot.MIDDLE_EAST) - middleEastPivot) * premiumPerMiddleEast
                + (w.diplomacy.rung() == null || w.diplomacy.warOver() ? 0 : premiumPerRung * (w.diplomacy.rung().ordinal() + 1));
        double stress = (w.diplomacy.rung() == null || w.diplomacy.warOver() ? 0 : stressPerRung * (w.diplomacy.rung().ordinal() + 1))
                + (w.military.operationActive() ? stressWar : 0);
        return new MarketContext(premium, 0, executiveEngine.competence(w.government, Post.TREASURY), stress);
    }

    private OpinionInputs opinionInputs() {
        EconomyState e = w.economy;
        EconomyParams p = economyEngine.params();
        return new OpinionInputs(economyEngine.gas(e), p.gas(p.oilBase()), e.inflation(), e.unemployment(), e.mortgageRate(p), e.confidence(), e.growth());
    }

    private ElectoralEnvironment environment() {
        boolean incumbent = w.phase == Phase.REELECTION_CAMPAIGN_2032 || (w.election.result2032() != null);
        int unfinished = (int) w.government.agenda().stream().filter(a -> !w.policy.inForce(a)).count();
        boolean fell = war().map(o -> o.status() == TheaterOutcome.TaiwanStatus.FALLEN).orElse(false);
        return new ElectoralEnvironment(tuning.d("scenario." + setup.scenario().name() + ".environment"), incumbent,
                w.politics.governing() ? approval() : 50, w.economy.growth(), w.economy.inflation(), w.economy.unemployment(), fell, unfinished);
    }

    // =========================================================================================
    //  Read-only view (UI, AI, events, tests)
    // =========================================================================================

    public WorldState state() { return w; }
    public double nationalSwing() { return electionEngine.nationalSwing(w.election, environment()); }
    public double localShift(String state) { return electionEngine.localShift(w.election, state); }
    public Map<String, Double> expectedMargins() { return electionEngine.expectedMargins(w.election, environment()); }
    /** The state polls the player sees this week (stable within a week: looking never changes them). */
    public Map<String, Double> statePolls() {
        if (pollMemo == null) pollMemo = electionEngine.statePolls(w.election, environment(), pollSeed());
        return pollMemo;
    }
    /** National poll margin, D minus R, implied by this week's state polls. */
    public double nationalPoll() { return electionEngine.calculator().tally(date().getYear(), statePolls()).popularMargin(); }
    public Optional<game.mandate.core.electorate.CellElectorateModel.GroupResults> crosstabs() {
        return electionEngine.crosstabs(w.election, environment(), pollSeed());
    }
    public List<game.mandate.core.campaign.PollPoint> polls() { return w.election.polls(); }
    public game.mandate.core.campaign.ElectionState election() { return w.election; }
    public CandidateCatalog candidates() { return candidates; }
    private long pollSeed() { return setup.seed() * 31 + w.clock.turns(); }
    public CampaignSide side(Party p) { return w.election.side(p); }
    public CampaignSide player() { return w.election.player(); }
    public CampaignSide opponent() { return w.election.opponent(); }

    public Optional<LocalDate> debateThisWeek() {
        List<LocalDate> list = w.phase == Phase.GENERAL_CAMPAIGN_2028 ? calendar.debates2028() : w.phase == Phase.REELECTION_CAMPAIGN_2032 ? calendar.debates2032() : List.of();
        LocalDate d0 = date();
        return list.stream().filter(d -> !d.isBefore(d0) && d.isBefore(d0.plusWeeks(1))).findFirst();
    }

    public GameSetup setup() { return setup; }
    public Tuning tuning() { return tuning; }
    public Phase phase() { return w.phase; }
    @Override public LocalDate date() { return w.clock.date(); }
    public SimulationClock clock() { return w.clock; }
    public CampaignCalendar calendar() { return calendar; }
    public StateCatalog states() { return states; }
    public BillCatalog bills() { return bills; }
    public Bill bill(String id) { return bills.get(id); }
    public Map<Issue, Double> salience() { return w.politics.salience(); }
    public List<PendingEvent> pendingEvents() { return w.scheduler.pending(); }
    public List<String> log() { return w.log(); }
    public List<Command> history() { return List.copyOf(history); }
    public Congress congress() { return w.congress.congress(); }
    public Optional<EconomyState> economy() { return w.politics.governing() ? Optional.of(w.economy) : Optional.empty(); }
    public double approval() { return politicsEngine.approval(w.politics); }
    public double capital() { return w.politics.capital(); }
    public int attention() { return w.government.attention(); }
    public List<String> agenda() { return w.government.agenda(); }
    public List<String> laws() { return w.policy.laws(); }
    public DiplomacyState diplomacy() { return w.diplomacy; }
    public Optional<TheaterOutcome> war() { return w.military.wars().isEmpty() ? Optional.empty() : Optional.of(w.military.wars().get(w.military.wars().size() - 1)); }
    public boolean operationsActive() { return w.military.operationActive(); }
    public double munitionsIndex() { return militaryEngine.munitionsIndex(w.military); }
    public Optional<ElectionResult> result2028() { return Optional.ofNullable(w.election.result2028()); }
    public Optional<ElectionResult> result2032() { return Optional.ofNullable(w.election.result2032()); }
    public double startMisery() { return w.economy.startMisery(); }
    public Optional<Legacy> legacy() { return Optional.ofNullable(w.legacy); }
    public boolean over() { return w.phase == Phase.EPILOGUE || w.phase == Phase.GAME_OVER; }
    public double competence(Post p) { return executiveEngine.competence(w.government, p); }
    public double deficit() { return economyEngine.deficit(w.economy, w.budget); }
    /** Gas, dollars a gallon, from the economy as it stands (also before the inauguration). */
    public double gas() { return economyEngine.gas(w.economy); }
    public double mortgageRate() { return w.economy.mortgageRate(economyEngine.params()); }
    public List<Country> countries() { return diplomacyEngine.countries(); }
    /** The next scheduled Fed meeting on or after today, if the calendar has one. */
    public Optional<LocalDate> nextFedMeeting() { return calendar.fomc().stream().filter(d -> !d.isBefore(date())).findFirst(); }
    public List<IndicatorPoint> indicators() { return w.indicators(); }

    public boolean unifiedGovernment() { return w.congress != null && congress().unified(player().party()); }
    @Override public int playerDiscipline() { return player().candidate().discipline(); }
    @Override public boolean playerIsRealPerson() { return player().candidate().isReal(); }
    @Override public double tension(HotSpot h) { return w.diplomacy.tension(h); }

    /** A stable fingerprint of the whole world, for regression tests. */
    public String fingerprint() {
        String json = Json.write(w.toJson()) + Json.write(new java.util.TreeMap<>(rng.snapshot()));
        return Integer.toHexString(json.hashCode()) + "-" + json.length();
    }

    private void requireCampaign() {
        if (w.phase != Phase.GENERAL_CAMPAIGN_2028 && w.phase != Phase.REELECTION_CAMPAIGN_2032)
            throw new IllegalCommandException("Only during a campaign");
    }

    private void log(String s) { w.log.add(date() + "  " + s); }
}
