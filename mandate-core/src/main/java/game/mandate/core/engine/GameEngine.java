package game.mandate.core.engine;

import game.mandate.core.data.Fmt;
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
import game.mandate.core.economy.Part;
import game.mandate.core.economy.Releases;
import game.mandate.core.economy.StateEconomy;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.economy.MarketContext;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
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
import game.mandate.core.news.AmbientNews;
import game.mandate.core.news.News;
import game.mandate.core.politics.Coalition;
import game.mandate.core.politics.OpinionInputs;
import game.mandate.core.politics.PoliticsEngine;
import game.mandate.core.politics.PoliticsParams;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.politics.Role;
import game.mandate.core.presidency.PresidencyOrder;
import game.mandate.core.presidency.PresidentEngine;
import game.mandate.core.presidency.PresidentState;
import game.mandate.core.presidency.WeekContext;
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
import java.util.TreeMap;

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
    private final StateEconomy stateEconomy = StateEconomy.shared();
    private final PoliticsEngine politicsEngine;
    private final CongressEngine congressEngine;
    private final ExecutiveEngine executiveEngine;
    private final DiplomacyEngine diplomacyEngine;
    private final MilitaryEngine militaryEngine;
    private final ElectionEngine electionEngine;
    private final DecisionResolver decisions = new DecisionResolver();
    private final PresidentEngine presidentEngine;
    private final AmbientNews ambient;

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
        this.presidentEngine = new PresidentEngine(states);
        this.ambient = new AmbientNews();
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
        planCampaignTurn();
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
        g.upgradeOldSnapshot();
        return g;
    }

    /**
     * A game saved in office before the presidency port had no president's week, monthly turns and the coalition model's
     * approval: it gets the week (Resolute Desk's routine), weekly turns from the next Monday, and Resolute Desk's
     * approval carrying on from where approval stood.
     */
    private void upgradeOldSnapshot() {
        economyEngine.upgrade(w.economy, w.budget);
        if (w.president != null || (w.phase != Phase.FIRST_100_DAYS && w.phase != Phase.GOVERNING) || !w.politics.governing()) return;
        politicsEngine.upgradeApproval(w.politics, approvalStart(), opinionInputs());
        w.president = presidentEngine.newPresident(player().candidate().id());
        presidentEngine.firstWeek(w.president, weekContext());
        if (w.phase == Phase.GOVERNING && w.clock.mode() == Granularity.MONTHLY) {
            w.clock.set(date().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
            w.clock.setMode(Granularity.WEEKLY);
        }
    }

    DeterministicRandomManager random() { return rng; }

    // =========================================================================================
    //  Commands
    // =========================================================================================

    public void apply(Command command) {
        pollMemo = null;
        switch (command) {
            case Command.PlanCampaign c -> { requireCampaign(); electionEngine.applyHandPlan(w.election, c.plan()); }
            case Command.SetCampaignOrders c -> {
                requireCampaign();
                electionEngine.setOrders(w.election, true, c.orders(), "");
                planPlayer(true);
            }
            case Command.Campaign c -> { requireCampaign(); campaignOrder(c.order()); }
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
            case Command.Presidency c -> presidencyOrder(c.order());
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
            case Effect.IssueWork x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Remarks x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.IssueCommand x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Performance x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.StateStanding x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Rally x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
            case Effect.Scandal x -> owner = governing && politicsEngine.apply(w.politics, x) ? "politics" : "none";
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
            case GOVERNING -> governingWeek();
            case EPILOGUE -> w.phase = Phase.GAME_OVER;
            case GAME_OVER -> throw new IllegalCommandException("The game is over");
        }
    }

    private void campaignWeek() {
        if (debateThisWeek().isPresent() && w.election.pendingPosture() == null)
            throw new IllegalCommandException("Choose a debate approach first");
        DebatePosture myPosture = w.election.pendingPosture();
        Optional<DebatePosture> theirPosture = debateThisWeek().map(d -> CampaignAi.debatePosture(w.election.opponent().candidate(), w.election.player().candidate(), rng));
        game.mandate.core.campaign.CampaignEngine.Turn turn = campaignTurn();
        pollMemo = null; // the turn is about to change the race
        List<String> news = new ArrayList<>();
        electionEngine.campaignTurn(w.election, turn, setup.difficulty().opponentResources(), theirPosture, rng, news).ifPresent(forPlayer ->
                log("Debate: you chose \"" + myPosture.label() + "\", your opponent \"" + theirPosture.get().label() + "\". "
                        + (forPlayer > 0.3 ? "You won the night" : forPlayer < -0.3 ? "Your opponent won the night" : "A draw")
                        + Fmt.format(" (%+.1f).", forPlayer)));
        for (String line : news) log(line);
        rollEvents();
        w.clock.advance();
        LocalDate electionDay = electionDay();
        if (w.clock.mode() == Granularity.WEEKLY && !date().isBefore(dailyStart(electionDay))) {
            w.clock.setMode(Granularity.DAILY);
            log("Two weeks to go. From now on every day counts: each turn is one day until Election Day.");
        }
        electionEngine.recordPoll(w.election, environment(), date().isAfter(electionDay) ? electionDay : date(), electionDay.getYear(), pollSeed());
        if (!date().isBefore(electionDay)) {
            w.clock.set(electionDay);
            w.clock.setMode(Granularity.WEEKLY);
            holdPresidentialElection();
        } else {
            planCampaignTurn();
        }
    }

    /** The final stretch runs a day at a time from the Monday two weeks before Election Day (BG DAILY_START). */
    static LocalDate dailyStart(LocalDate electionDay) {
        return game.mandate.core.campaign.CampaignEngine.Turn.monday(electionDay).minusWeeks(2);
    }

    /** Election Day of the campaign in progress. */
    public LocalDate electionDay() { return w.phase == Phase.REELECTION_CAMPAIGN_2032 ? calendar.election2032() : calendar.election2028(); }

    /** In the final stretch, the day of the week (0 Monday .. 6 Sunday); in weekly turns, -1. */
    public int campaignDay() { return w.clock.mode() == Granularity.DAILY ? w.clock.date().getDayOfWeek().getValue() - 1 : -1; }

    /** What the campaign engine needs for the coming turn: dates, salience and the public polls. */
    public game.mandate.core.campaign.CampaignEngine.Turn campaignTurn() {
        return new game.mandate.core.campaign.CampaignEngine.Turn(date(), electionDay(), campaignDay(), w.politics.salience(), statePolls(), nationalPoll());
    }

    public game.mandate.core.campaign.CampaignEngine campaignEngine() { return electionEngine.campaigns(); }

    /** The campaign's debates (the calendar's, for the campaign in progress). */
    public List<LocalDate> campaignDebates() {
        return w.phase == Phase.GENERAL_CAMPAIGN_2028 ? calendar.debates2028() : w.phase == Phase.REELECTION_CAMPAIGN_2032 ? calendar.debates2032() : List.of();
    }

    /** The planners draw from the game's random streams (their own names), so replays reproduce their plans. */
    public DeterministicRandomManager rngForPlanning() { return rng; }

    /**
     * The start of a campaign turn: the opponent reads the polls and sets its brief (its reasoning is shown); at the start of
     * each week both campaigns' staffs rebuild their orders (the player's only in the areas the player has handed them).
     */
    private void planCampaignTurn() {
        if (w.phase != Phase.GENERAL_CAMPAIGN_2028 && w.phase != Phase.REELECTION_CAMPAIGN_2032) return;
        CampaignAi.Adaptation a = setup.difficulty().adaptive() ? CampaignAi.adapt(this, w.election.opponent())
                : new CampaignAi.Adaptation(game.mandate.core.campaign.CampaignStrategy.Orders.DEFAULT, "It runs the same campaign every week: rallies and money in the closest states.");
        boolean changed = !a.orders().equals(w.election.opponentOrders()) || w.election.opponentReasoning().isEmpty();
        electionEngine.setOrders(w.election, false, a.orders(), a.reasoning());
        if (campaignDay() > 0) return;
        electionEngine.applyPlan(w.election, false, CampaignAi.planOrders(this, w.election.opponent(), a.orders(), true, true, true), true, true, true);
        String where = CampaignAi.describePlan(this, w.election.opponent());
        electionEngine.setOrders(w.election, false, a.orders(), a.reasoning() + (where.isEmpty() ? "" : " " + where));
        if (changed) log("Your opponent's campaign: " + a.orders().describe() + ". " + a.reasoning());
        planPlayer(false);
        pollMemo = null;
    }

    /** The player's staff plans the areas handed to it; the message follows the brief (a new brief replans at once). */
    private void planPlayer(boolean briefChanged) {
        var e = w.election;
        boolean sch = e.staffRuns(game.mandate.core.campaign.ElectionState.StaffArea.SCHEDULE);
        boolean ads = e.staffRuns(game.mandate.core.campaign.ElectionState.StaffArea.ADS);
        boolean fld = e.staffRuns(game.mandate.core.campaign.ElectionState.StaffArea.FIELD);
        if (!sch && !ads && !fld && !briefChanged) return;
        electionEngine.applyPlan(e, true, CampaignAi.planOrders(this, e.player(), e.playerOrders(), sch, ads, fld), sch, ads, fld);
    }

    /** The player's campaign orders (Command.Campaign). */
    private void campaignOrder(game.mandate.core.campaign.CampaignOrder order) {
        var geo = campaignEngine().geography();
        var e = w.election;
        java.util.function.Function<String, Integer> state = code -> {
            if (code == null || !geo.contains(code)) throw new IllegalCommandException("Unknown state " + code);
            return geo.index(code);
        };
        switch (order) {
            case game.mandate.core.campaign.CampaignOrder.Slot o -> {
                int from = campaignDay() < 0 ? 0 : campaignDay() * 3;
                if (o.slot() < from || o.slot() >= game.mandate.core.campaign.CampaignOrders.SLOTS)
                    throw new IllegalCommandException(o.slot() < from && o.slot() >= 0 ? "That day is over" : "No slot " + o.slot());
                int st = o.activity().local() ? state.apply(o.state() == null ? player().location() : o.state()) : -1;
                int choices = o.activity().parameterChoices();
                if (choices > 0 && (o.param() < 0 || o.param() >= choices)) throw new IllegalCommandException("No choice " + o.param() + " for " + o.activity().label());
                electionEngine.editOrders(e, x -> x.slot(o.slot(), o.activity(), st, o.param()));
            }
            case game.mandate.core.campaign.CampaignOrder.FillSchedule o -> {
                var current = player().orders();
                var plan = CampaignAi.planOrders(this, player(), e.playerOrders(), true, false, false);
                int from = campaignDay() < 0 ? 0 : campaignDay() * 3;
                electionEngine.editOrders(e, x -> {
                    for (int i = from; i < game.mandate.core.campaign.CampaignOrders.SLOTS; i++)
                        if (current.activity(i) == game.mandate.core.campaign.Activity.NONE) x.slot(i, plan.activity(i), plan.location(i), plan.parameter(i));
                });
            }
            case game.mandate.core.campaign.CampaignOrder.ClearSchedule o -> {
                int from = campaignDay() < 0 ? 0 : campaignDay() * 3;
                electionEngine.editOrders(e, x -> x.clearSchedule(from));
            }
            case game.mandate.core.campaign.CampaignOrder.Staff o -> electionEngine.setStaff(e, o.area(), o.on());
            case game.mandate.core.campaign.CampaignOrder.Airtime o -> {
                if (o.market() < 0 || o.market() >= geo.marketCount()) throw new IllegalCommandException("Unknown TV market " + o.market());
                if (o.grps() < 0 || o.grps() > 2000) throw new IllegalCommandException("A market takes 0 to 2,000 rating points a week");
                electionEngine.editOrders(e, x -> x.tv(o.market(), o.grps(), o.tone()));
            }
            case game.mandate.core.campaign.CampaignOrder.NationalTv o -> {
                if (o.grps() < 0 || o.grps() > 400) throw new IllegalCommandException("National TV takes 0 to 400 rating points a week");
                electionEngine.editOrders(e, x -> x.national(o.network(), o.grps()));
            }
            case game.mandate.core.campaign.CampaignOrder.Digital o -> {
                int st = state.apply(o.state());
                if (o.perWeek() < 0 || o.perWeek() > 5) throw new IllegalCommandException("Digital spending runs from $0 to $5M a week in a state");
                electionEngine.editOrders(e, x -> x.digital(st, o.perWeek()));
            }
            case game.mandate.core.campaign.CampaignOrder.DigitalMix o -> {
                if (o.shares().size() != game.mandate.core.campaign.AirWar.Platform.values().length || o.shares().stream().anyMatch(v -> v < 0)
                        || o.shares().stream().mapToDouble(Double::doubleValue).sum() <= 0)
                    throw new IllegalCommandException("The digital mix needs a non-negative share for each of the six platforms");
                double[] mix = o.shares().stream().mapToDouble(Double::doubleValue).toArray();
                electionEngine.editOrders(e, x -> x.digitalMix(mix));
            }
            case game.mandate.core.campaign.CampaignOrder.PlatformMessage o -> electionEngine.editOrders(e, x -> x.digitalMessage(o.platform(), o.message()));
            case game.mandate.core.campaign.CampaignOrder.Field o -> {
                int st = state.apply(o.state());
                if (player().offices(o.state()) + o.offices() < 0 || player().organizers(o.state()) + o.organizers() < 0)
                    throw new IllegalCommandException("Cannot close more offices or lay off more organisers than you have");
                electionEngine.editOrders(e, x -> x.field(st, o.offices(), o.organizers()));
            }
            case game.mandate.core.campaign.CampaignOrder.Transfer o -> {
                int st = state.apply(o.state());
                if (o.perWeek() < 0 || o.perWeek() > 5) throw new IllegalCommandException("Transfers run from $0 to $5M a week to a state party");
                electionEngine.editOrders(e, x -> x.transfer(st, o.perWeek()));
            }
            case game.mandate.core.campaign.CampaignOrder.RedBox o -> {
                int st = state.apply(o.state());
                electionEngine.editOrders(e, x -> x.redBox(st, o.on()));
            }
            case game.mandate.core.campaign.CampaignOrder.Headquarters o -> {
                if (o.tier() < 0 || o.tier() > 2) throw new IllegalCommandException("Headquarters tiers are 0, 1 and 2");
                electionEngine.editOrders(e, x -> x.hq(o.tier()));
            }
            case game.mandate.core.campaign.CampaignOrder.Message o -> {
                var brief = e.playerOrders();
                electionEngine.setOrders(e, true, new game.mandate.core.campaign.CampaignStrategy.Orders(brief.strategy(), brief.rate(), o.issue()), "");
                electionEngine.editOrders(e, x -> x.message(o.issue() != null ? o.issue() : CampaignAi.bestMessage(this, player())));
            }
            case game.mandate.core.campaign.CampaignOrder.PullAds o -> electionEngine.editOrders(e, x -> {
                if (o.tv()) x.clearAirtime();
                if (o.digital()) x.clearDigital();
            });
            case game.mandate.core.campaign.CampaignOrder.ProduceAd o -> {
                try { electionEngine.produceAd(e, o.tone(), o.issue(), o.group(), o.messenger(), w.politics.salience(), date(), rng); }
                catch (IllegalArgumentException x) { throw new IllegalCommandException(x.getMessage()); }
            }
            case game.mandate.core.campaign.CampaignOrder.TestAd o -> {
                try { electionEngine.testAd(e, o.index(), rng); }
                catch (IllegalArgumentException x) { throw new IllegalCommandException(x.getMessage()); }
            }
            case game.mandate.core.campaign.CampaignOrder.AirAd o -> {
                try { electionEngine.airAd(e, o.index()); }
                catch (IllegalArgumentException x) { throw new IllegalCommandException(x.getMessage()); }
            }
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
        log(Fmt.format("Election Night %d: %s %d, %s %d. Popular vote %s%+.1f. Tipping point: %s.", year,
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
        politicsEngine.beginApproval(w.politics, approvalStart(), opinionInputs());
        // Each campaign promise left off the agenda costs the president's base wing (the wings exist once there is a president).
        for (String promised : brokenPromises())
            dispatch(decision, "Broken promise " + promised, new Effect.WingMood(0, electionEngine.brokenPledgeMood()));
        log("Inauguration Day. " + player().candidate().name() + " takes the oath with " + myEv + " electoral votes and " + congress()
                + ". Approval " + Math.round(approval()) + "%, political capital " + Math.round(capital()) + ".");
        w.inaugurationCommand = history.size();      // this EndTurn is appended after it completes
        recordIndicators();
        w.clock.set(hundredDaysStart());
        w.hundredDays = HundredDays.begin(host);
        w.president = presidentEngine.newPresident(player().candidate().id());
        presidentEngine.firstWeek(w.president, weekContext());
        log("The first hundred days begin. Your flagship: the " + host.flagship().name() + ".");
    }

    private void recordIndicators() {
        EconomyState e = w.economy;
        BudgetState b = w.budget;
        double[] v = {e.core(), e.part(Part.SHELTER), e.part(Part.FOOD), e.part(Part.ENERGY), e.part(Part.MEDICAL), e.part(Part.GOODS), e.part(Part.SERVICES),
                e.housing(), e.manufacturing(), e.aiInvestment(), e.farmIncome(), e.energyProduction(), e.tourism(), e.sentiment(), e.stocks(), e.dollar(), e.oil(),
                e.mortgageRate(economyEngine.params()), e.payrolls(), e.outputGap(), e.potentialGrowth(), b.revenue(), b.outlays(), b.interest(), b.debt()};
        for (int i = 0; i < v.length; i++) v[i] = Math.round(v[i] * 100) / 100.0;
        w.indicators.add(new IndicatorPoint(date(), approval(), e.unemployment(), e.inflation(), e.growth(), economyEngine.gas(e), e.tenYear(), e.fedRate(),
                economyEngine.deficit(e, w.budget), w.diplomacy.deterrence(), v));
    }

    /** The Monday the first week of the hundred days starts. */
    public LocalDate hundredDaysStart() { return calendar.inauguration().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)); }

    /** One action of the first hundred days. A refused action changes nothing (the hundred days validate first). */
    private void act(OpeningAction a) {
        if (w.phase != Phase.FIRST_100_DAYS) throw new IllegalCommandException("Only during the first hundred days");
        int logged = w.hundredDays.log().size();
        host.decision = -1;
        if (a instanceof OpeningAction.EndWeek) {
            String blocked = new HundredDays(host, w.hundredDays).endWeekBlocker();
            if (blocked != null) throw new IllegalCommandException(blocked);
            presidentWeek();          // the week's slots play out first, as in Resolute Desk; then the economy and opinion
        }
        try {
            new HundredDays(host, w.hundredDays).apply(a);
        } catch (IllegalStateException | IllegalArgumentException refused) {
            throw new IllegalCommandException(refused.getMessage());
        }
        for (int i = logged; i < w.hundredDays.log().size(); i++) log(w.hundredDays.log().get(i));
        if (a instanceof OpeningAction.EndWeek) {
            w.clock.advance();
            recordIndicators();
            afterWeek();
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
        w.clock.setMode(Granularity.WEEKLY);       // Resolute Desk's weeks go on through the term
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

    /**
     * A week in office (Resolute Desk's {@code Week.resolve} order): the president's slots, then the economy (stepped a
     * week, with the Fed's meetings and the data releases that fall in it), then opinion; the world and the military
     * move on the first week of each month, as before; then events, at their monthly odds spread over the weeks.
     */
    private void governingWeek() {
        LocalDate from = date(), to = from.plusWeeks(1);
        presidentWeek();

        economyEngine.step(w.economy, w.budget, 1.0 / EconomyEngine.WEEKS_PER_MONTH, marketContext(), rng);
        EconomyEngine.Shock shock = economyEngine.lastShock(w.economy);
        if (shock != null) news(Releases.shock(from, shock));
        for (int i = 0; i < calendar.fomcMeetings(from, to); i++) news(Releases.fed(from, w.economy, economyEngine.fedMeeting(w.economy, w.budget)));
        for (int i = 0; i < CampaignCalendar.releases(from, to, true); i++) {
            politicsEngine.onRelease(w.politics, economyEngine.jobsReport(w.economy, rng));
            LocalDate released = CampaignCalendar.jobsReport(from.getYear(), from.getMonth());
            if (released.isBefore(from)) released = CampaignCalendar.jobsReport(to.getYear(), to.getMonth());
            news(Releases.jobs(from, released, w.economy));
        }
        for (int i = 0; i < CampaignCalendar.releases(from, to, false); i++) {
            politicsEngine.onRelease(w.politics, economyEngine.inflationReport(w.economy, rng));
            news(Releases.prices(from, w.economy));
        }
        for (int i = 0; i < CampaignCalendar.gdpReleases(from, to); i++) {
            economyEngine.gdpReport(w.economy, rng);
            news(Releases.gdp(from, w.economy));
        }

        politicsEngine.step(w.politics, 1.0, opinionInputs(), rng);

        boolean newMonth = to.getMonthValue() != from.getMonthValue();
        if (newMonth) {
            for (Escalation e : diplomacyEngine.stepMonth(w.diplomacy, rng)) queueEvent(EventCatalog.escalation(e));
            militaryEngine.stepMonth(w.military);
        }

        rollEvents();
        w.clock.advance();
        recordIndicators();
        if (newMonth) executiveEngine.newTurn(w.government);    // the old governing actions keep their monthly attention until Congress and the West Wing replace them
        afterWeek();

        if (!w.midtermsHeld && !date().isBefore(calendar.midterms())) holdMidterms();
        if (!date().isBefore(calendar.reelectionStart())) beginReelection();
    }

    // ---- the president's week (Resolute Desk) ---------------------------------------------------------------------

    private void presidentWeek() {
        PresidentEngine.Outcome out = presidentEngine.resolve(w.president, weekContext(), date(), rng);
        int decision = w.ledger.decide(date(), "The president's week");
        dispatchAll(decision, "The president's week", out.effects());
        for (News n : out.news()) news(n);
    }

    /** After a week: the era's news, then the chief of staff writes the next week if the calendar is delegated. */
    private void afterWeek() {
        LocalDate weekAgo = date().minusDays(7);
        double busy = 0;
        for (News n : w.news) if (n.section() == null && !n.date().isBefore(weekAgo)) busy += n.weight();
        for (String line : w.log) if (line.length() > 12 && line.substring(0, 10).compareTo(weekAgo.toString()) >= 0) busy += 0.5;
        List<String> places = states.all().stream().map(UsState::name).filter(n -> !n.equals("District of Columbia")).toList();
        for (AmbientNews.Run r : ambient.week(w.ambientUsed, busy, date().minusDays(1), this::newsVariable, places, rng)) {
            news(r.news());
            int decision = w.ledger.decide(date(), "In the news: " + r.news().head());
            dispatchAll(decision, "In the news", r.effects());
        }
        presidentEngine.afterWeek(w.president, weekContext());
    }

    /** The state of the country for an era story's conditions (Resolute Desk's {@code Stories.var}); NaN for what this game does not model. */
    private double newsVariable(String k) {
        EconomyState e = w.economy;
        return switch (k) {
            case "unemp" -> e.unemployment();
            case "gdp" -> e.growth();
            case "fed" -> e.fedRate();
            case "deficit" -> economyEngine.deficit(e, w.budget);
            case "month" -> date().getMonthValue();
            case "ai" -> 1;
            case "wars" -> w.military.operationActive() ? 1 : 0;
            case "kia" -> war().map(o -> (double) o.usKilled()).orElse(0.0);
            case "sent" -> e.sentiment();
            case "uchg" -> w.indicators.size() > 26 ? e.unemployment() - w.indicators.get(w.indicators.size() - 27).unemployment() : 0;
            case "tw" -> w.president == null ? 0 : w.president.week();
            case "dem" -> player().party() == Party.DEMOCRAT ? 1 : 0;
            case "elect" -> date().getYear() % 4 == 0 ? 1 : 0;
            case "midterm" -> date().getYear() % 4 == 2 ? 1 : 0;
            case "mortgage" -> e.mortgageRate(economyEngine.params());
            case "mkt" -> {   // the stock market's change over thirteen weeks, %
                double then = w.indicators.size() > 13 ? w.indicators.get(w.indicators.size() - 14).detail("stocks") : Double.NaN;
                yield Double.isNaN(then) || then <= 0 ? 0 : (e.stocks() / then - 1) * 100;
            }
            default -> Double.NaN;
        };
    }

    private void news(News n) {
        w.news.add(n);
        if (w.news.size() > 400) w.news.remove(0);
        if (n.section() == null && n.weight() >= 0.6) log(n.head());
    }

    private void presidencyOrder(PresidencyOrder o) {
        if (w.president == null || (w.phase != Phase.FIRST_100_DAYS && w.phase != Phase.GOVERNING)) throw new IllegalCommandException("Only in office");
        try {
            presidentEngine.apply(w.president, o, weekContext());
        } catch (IllegalArgumentException e) {
            throw new IllegalCommandException(e.getMessage());
        }
    }

    /** What the president's week reads: the president's gifts on Resolute Desk's scale, approval, the issues and the states. */
    public WeekContext weekContext() {
        Candidate p = player().candidate();
        String last = p.name().substring(p.name().lastIndexOf(' ') + 1);
        return new WeekContext(last, rdScale((p.charisma() + p.policy()) / 2.0), rdScale(p.charisma()), rdScale(p.fundraising()), 60,
                approval(), w.politics.salience(), w.politics.issueApproval(), w.politics.stateApproval(), midtermSeason(), 0.6);
    }

    /** A 1-10 rating on Resolute Desk's 0-100 scale (as the campaign port maps Battleground 2028's). */
    static double rdScale(double r) { return (r - 1) * 100 / 9; }

    /** The hundred days before the midterms: campaign stops for the party's candidates. */
    public boolean midtermSeason() {
        return !w.midtermsHeld && !date().isBefore(calendar.midterms().minusDays(100)) && date().isBefore(calendar.midterms());
    }

    /** What the approval model needs at the inauguration (Resolute Desk's {@code Approval.init}). */
    private PoliticsEngine.ApprovalStart approvalStart() {
        Candidate p = player().candidate();
        int sign = player().party().sign();
        ElectionResult r = w.election.result2028();
        Map<String, Double> lean = new TreeMap<>(), weight = new TreeMap<>();
        for (UsState u : states.all()) {
            lean.put(u.code(), r.stateMargins().getOrDefault(u.code(), u.baselineMargin()) * sign);
            weight.put(u.code(), u.voteWeight());
        }
        // Resolute Desk's personal term: crossover appeal and scandal baggage at its neutral values (60, 40), charm from charisma
        double personal = (rdScale(p.charisma()) - 60) * 0.03;
        double difficulty = switch (setup.difficulty()) { case STORY -> 2; case STANDARD -> 0; case HARD -> -2; };
        return new PoliticsEngine.ApprovalStart(personal, difficulty, r.marginFor(player().party()), lean, weight, 30 + rdScale(p.policy()) * 0.2);
    }

    /** What approval's target is made of, in points (Resolute Desk's model; see {@link PoliticsEngine#parts}). */
    public Map<String, Double> approvalParts() { return politicsEngine.parts(w.politics, opinionInputs()); }
    /** Approval's partisan floor and ceiling. */
    public double[] approvalBounds() { return politicsEngine.bounds(); }
    /** Consumer sentiment as Resolute Desk computes it. */
    public double sentiment() { return politicsEngine.sentiment(w.politics, opinionInputs()); }

    public PresidentEngine presidentEngine() { return presidentEngine; }
    public Optional<PresidentState> president() { return Optional.ofNullable(w.president); }
    public List<News> news() { return w.news(); }

    private void holdMidterms() {
        w.midtermsHeld = true;
        int before = congress().house(player().party()), senBefore = congress().senate(player().party());
        Effect.CongressElected result = electionEngine.midterm(w.election, player().party(), approval(), w.economy.growth(), congress(),
                calendar.midterms().getYear(), rng);
        int decision = w.ledger.decide(date(), "Midterms " + calendar.midterms().getYear());
        dispatch(decision, "Midterms", result);
        log(Fmt.format("Midterms: your party %+d House seats, %+d Senate seats. %s.",
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
        planCampaignTurn();
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
            w.clock.setMode(Granularity.WEEKLY);
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
        w.ledger.record(decision, date(), "Address", "politics", Fmt.format("Approval +%.2f (your voters and swing voters)", gain));
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
        // event odds are per campaign week, or per month in office: a day of the final stretch and a week in office take their share
        double turnShare = w.clock.mode() == Granularity.DAILY ? 1.0 / 7 : w.phase == Phase.GOVERNING ? 1.0 / EconomyEngine.WEEKS_PER_MONTH : 1.0;
        for (PendingEvent p : w.scheduler.roll(eventDefinitions, w.phase, this, rng, turnShare)) log("EVENT: " + p.definition().title());
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
        return new OpinionInputs(economyEngine.gas(e), economyEngine.normalGas(e), e.inflation(), e.unemployment(), e.mortgageRate(economyEngine.params()),
                e.sentiment(), e.growth(), stateEconomy.approvalTerms(e));
    }

    private ElectoralEnvironment environment() {
        boolean incumbent = w.phase == Phase.REELECTION_CAMPAIGN_2032 || (w.election.result2032() != null);
        int unfinished = (int) w.government.agenda().stream().filter(a -> !w.policy.inForce(a)).count();
        boolean fell = war().map(o -> o.status() == TheaterOutcome.TaiwanStatus.FALLEN).orElse(false);
        return new ElectoralEnvironment(tuning.d("scenario." + setup.scenario().name() + ".environment"), incumbent,
                w.politics.governing() ? approval() : 50, w.economy.growth(), w.economy.inflation(), w.economy.unemployment(), fell, unfinished,
                w.politics.salience());
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
    /** Each state's chance of going the player's way, from this week's polls and the published error model. */
    public Map<String, Double> stateWinChances() { return electionEngine.stateWinChances(statePolls(), player().party().sign()); }
    /** One standard deviation of the national polling error, for error bands. */
    public double pollErrorSd() { return electionEngine.nationalPollErrorSd(); }
    public game.mandate.core.campaign.ElectionState election() { return w.election; }
    public CandidateCatalog candidates() { return candidates; }
    private long pollSeed() { return setup.seed() * 31 + w.clock.turns(); }
    public CampaignSide side(Party p) { return w.election.side(p); }
    public CampaignSide player() { return w.election.player(); }
    public CampaignSide opponent() { return w.election.opponent(); }

    public Optional<LocalDate> debateThisWeek() {
        List<LocalDate> list = w.phase == Phase.GENERAL_CAMPAIGN_2028 ? calendar.debates2028() : w.phase == Phase.REELECTION_CAMPAIGN_2032 ? calendar.debates2032() : List.of();
        LocalDate d0 = date();
        LocalDate end = w.clock.mode() == Granularity.DAILY ? d0.plusDays(1) : d0.plusWeeks(1);
        return list.stream().filter(d -> !d.isBefore(d0) && d.isBefore(end)).findFirst();
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

    /** The economy state by state: unemployment and gas in each. */
    public StateEconomy stateEconomy() { return stateEconomy; }
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
