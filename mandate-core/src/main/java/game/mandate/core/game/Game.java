package game.mandate.core.game;

import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignEngine;
import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.BillCatalog;
import game.mandate.core.congress.Congress;
import game.mandate.core.congress.LegislativeEngine;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.economy.ApprovalModel;
import game.mandate.core.economy.Economy;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.ElectionCalculator;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UniformSwingModel;
import game.mandate.core.electorate.UsState;
import game.mandate.core.events.EventCatalog;
import game.mandate.core.events.EventDefinition;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.rng.RngStreams;
import game.mandate.core.state.Party;
import game.mandate.core.time.Calendar2028;
import game.mandate.core.time.Phase;
import game.mandate.core.world.AbstractTheaterResolver;
import game.mandate.core.world.HotSpot;
import game.mandate.core.world.TheaterOutcome;
import game.mandate.core.world.TheaterResolver;
import game.mandate.core.world.TheaterScenario;
import game.mandate.core.world.World;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One playthrough: the 2028 general election, the 2029-2032 term, and the 2032 re-election.
 *
 * <p>The only way to change a game is {@link #apply(Command)}. Every command is appended to the
 * history, so {@link #replay(GameSetup, List)} reproduces a game exactly. Systems never call each
 * other directly; this class owns the turn order and moves values between them.
 */
public final class Game {
    /** The 2024 national presidential margin, D minus R: the zero point of every swing. */
    public static final double MARGIN_2024 = -1.5;
    public static final int ATTENTION_PER_MONTH = 3;
    public static final List<LocalDate> DEBATES_2028 = List.of(
            LocalDate.of(2028, 9, 25), LocalDate.of(2028, 10, 9), LocalDate.of(2028, 10, 23));
    public static final List<LocalDate> DEBATES_2032 = List.of(
            LocalDate.of(2032, 9, 27), LocalDate.of(2032, 10, 18));

    // ---- static content -------------------------------------------------------------------
    private final GameSetup setup;
    private final StateCatalog states;
    private final CandidateCatalog candidates;
    private final BillCatalog bills;
    private final List<EventDefinition> eventDefinitions;
    private final RngStreams rng;
    private final ElectionCalculator elections;
    private final CampaignEngine campaignEngine;
    private final LegislativeEngine legislature = new LegislativeEngine();
    private final TheaterResolver theater = new AbstractTheaterResolver();

    // ---- dynamic state ----------------------------------------------------------------------
    private final List<Command> history = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private Phase phase = Phase.GENERAL_CAMPAIGN_2028;
    private LocalDate date = Calendar2028.CAMPAIGN_START;
    private CampaignSide player;
    private CampaignSide opponent;
    private CampaignWeekPlan pendingPlan;
    private DebatePosture pendingPosture;
    private double campaignSwingForPlayer;        // debates and campaign events, decays weekly
    private double systematicMiss;                // hidden polling error for the current election
    private final Map<Issue, Double> salience = new EnumMap<>(CampaignEngine.baseSalience());
    private final List<PendingEvent> pendingEvents = new ArrayList<>();
    private final Set<String> firedEvents = new HashSet<>();
    private int nextEventId = 1;

    private ElectionResult result2028;
    private ElectionResult result2032;
    private Congress congress;
    private Economy economy;
    private ApprovalModel approval;
    private final World world = new World();
    private double capital;
    private int attention;
    private double recentAddresses;
    private final List<String> agenda = new ArrayList<>();
    private final List<String> laws = new ArrayList<>();
    private final Set<Integer> reconciliationYears = new HashSet<>();
    private boolean midtermsHeld;
    private Optional<TheaterOutcome> war = Optional.empty();
    private double startMisery;
    private Legacy legacy;

    public Game(GameSetup setup) {
        this.setup = setup;
        this.states = StateCatalog.load();
        this.candidates = CandidateCatalog.load();
        this.bills = BillCatalog.load();
        this.eventDefinitions = EventCatalog.standard();
        this.rng = new RngStreams(setup.seed());
        this.elections = new ElectionCalculator(states, new UniformSwingModel());
        this.campaignEngine = new CampaignEngine(states);

        Candidate mine = candidates.get(setup.playerCandidateId());
        Candidate theirs = candidates.get(setup.opponentCandidateId());
        if (mine.party() != setup.playerParty() || theirs.party() != setup.playerParty().opponent())
            throw new IllegalArgumentException("Candidates do not match the chosen party");
        player = new CampaignSide(mine.party(), mine, 150);
        opponent = new CampaignSide(theirs.party(), theirs, 150 * setup.difficulty().opponentResources());
        systematicMiss = rng.gaussian("polls.miss") * 1.5;
        congress = new Congress(setup.scenario().houseDemBefore(), setup.scenario().senateDemBefore());
        log("The general election begins: " + mine.name() + " (" + mine.party().code() + ") vs " + theirs.name()
                + " (" + theirs.party().code() + ").");
    }

    public static Game replay(GameSetup setup, List<Command> commands) {
        Game g = new Game(setup);
        for (Command c : commands) g.apply(c);
        return g;
    }

    // =========================================================================================
    //  Command entry point
    // =========================================================================================

    public void apply(Command command) {
        switch (command) {
            case Command.PlanCampaign c -> { requireCampaign(); pendingPlan = c.plan(); }
            case Command.ChooseDebatePosture c -> {
                requireCampaign();
                if (debateThisWeek().isEmpty()) throw new IllegalCommandException("No debate this week");
                pendingPosture = c.posture();
            }
            case Command.SetAgenda c -> setAgenda(c.billIds());
            case Command.PushBill c -> pushBill(c.billId(), c.strategy(), c.capital());
            case Command.PublicAddress c -> publicAddress(c.issue());
            case Command.BolsterDeterrence c -> bolsterDeterrence();
            case Command.Diplomacy c -> diplomacy(c.hotSpot());
            case Command.ResolveEvent c -> resolveEvent(c.instanceId(), c.optionIndex());
            case Command.EndTurn c -> endTurn();
        }
        history.add(command);
    }

    // =========================================================================================
    //  Turn resolution
    // =========================================================================================

    private void endTurn() {
        if (!pendingEvents.isEmpty()) throw new IllegalCommandException("Decide the pending events first");
        switch (phase) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaignWeek();
            case TRANSITION -> beginTerm();
            case GOVERNING -> governingMonth();
            case EPILOGUE -> phase = Phase.GAME_OVER;
            case GAME_OVER -> throw new IllegalCommandException("The game is over");
        }
    }

    private void campaignWeek() {
        if (debateThisWeek().isPresent() && pendingPosture == null)
            throw new IllegalCommandException("Choose a debate approach first");
        CampaignWeekPlan mine = pendingPlan != null ? pendingPlan : CampaignAi.plan(this, player, 1.0);
        CampaignWeekPlan theirs = CampaignAi.plan(this, opponent, setup.difficulty().opponentResources());
        campaignEngine.execute(player, mine, salience);
        campaignEngine.execute(opponent, theirs, salience);
        campaignSwingForPlayer *= 0.85;

        debateThisWeek().ifPresent(d -> {
            DebatePosture theirPosture = CampaignAi.debatePosture(opponent.candidate(), player.candidate(), rng);
            double swingD = player.party() == Party.DEMOCRAT
                    ? campaignEngine.debate(player, pendingPosture, opponent, theirPosture, rng)
                    : campaignEngine.debate(opponent, theirPosture, player, pendingPosture, rng);
            double forPlayer = swingD * player.party().sign();
            campaignSwingForPlayer += forPlayer;
            log("Debate: you chose \"" + pendingPosture.label() + "\", your opponent \"" + theirPosture.label() + "\". "
                    + (forPlayer > 0.3 ? "You won the night" : forPlayer < -0.3 ? "Your opponent won the night" : "A draw")
                    + String.format(" (%+.1f).", forPlayer));
        });
        pendingPlan = null;
        pendingPosture = null;
        rollEvents();
        date = date.plusWeeks(1);

        LocalDate electionDay = phase == Phase.GENERAL_CAMPAIGN_2028 ? Calendar2028.ELECTION_2028 : Calendar2028.ELECTION_2032;
        if (!date.isBefore(electionDay)) {
            date = electionDay;
            holdPresidentialElection();
        }
    }

    private void holdPresidentialElection() {
        boolean first = phase == Phase.GENERAL_CAMPAIGN_2028;
        ElectionResult r = elections.resolve(first ? 2028 : 2032, nationalSwing(), this::localShift, systematicMiss, rng, "election");
        Party winner = r.winner();
        log(String.format("Election Night %d: %s %d, %s %d. Popular vote %s%+.1f. Tipping point: %s.", r.year(),
                Party.DEMOCRAT.code(), r.demElectoralVotes(), Party.REPUBLICAN.code(), r.repElectoralVotes(),
                r.popularMargin() >= 0 ? "D" : "R", Math.abs(r.popularMargin()), r.tippingPoint()));
        if (first) {
            result2028 = r;
            electCongress2028(r);
            if (winner == player.party()) {
                log("You won. " + congress + ". Choose your agenda.");
                phase = Phase.TRANSITION;
                date = Calendar2028.INAUGURATION_2029.minusWeeks(6);
            } else {
                log("You lost the presidency.");
                finish();
            }
        } else {
            result2032 = r;
            log(winner == player.party() ? "You are re-elected." : "You are defeated for re-election.");
            finish();
        }
    }

    private void finish() {
        legacy = Legacy.score(this);
        log(legacy.headline());
        phase = Phase.EPILOGUE;
    }

    private void electCongress2028(ElectionResult r) {
        int houseD = (int) Math.round(218 + 5.5 * r.popularMargin() + 5 * rng.gaussian("congress.elections"));
        congress.setHouse(Party.DEMOCRAT, Math.max(170, Math.min(290, houseD)));
        int notUpD = congress.senate(Party.DEMOCRAT) - (int) states.all().stream().filter(s -> s.senateUp2028().orElse(null) == Party.DEMOCRAT).count();
        int wonD = senateRaces(true, r.popularMargin() - MARGIN_2024);
        congress.setSenate(Party.DEMOCRAT, notUpD + wonD);
    }

    /** Resolves one Senate class through the shared electorate model. Returns Democratic wins. */
    private int senateRaces(boolean class2028, double nationalSwing) {
        int wonD = 0;
        for (UsState s : states.all()) {
            Optional<Party> holder = class2028 ? s.senateUp2028() : s.senateUp2030();
            if (holder.isEmpty()) continue;
            double m = s.baselineMargin() + nationalSwing * s.elasticity()
                    + 3.0 * holder.get().sign() + 4.0 * rng.gaussian("congress.elections");
            if (m >= 0) wonD++;
        }
        return wonD;
    }

    private void setAgenda(List<String> ids) {
        if (phase != Phase.TRANSITION) throw new IllegalCommandException("The agenda is set during the transition");
        if (ids.isEmpty() || ids.size() > 3) throw new IllegalCommandException("Choose one to three priorities");
        for (String id : ids) {
            Bill b = bill(id);
            if (!b.availableTo(player.party())) throw new IllegalCommandException(b.name() + " belongs to the other party");
        }
        agenda.clear();
        agenda.addAll(ids);
    }

    private void beginTerm() {
        if (agenda.isEmpty()) throw new IllegalCommandException("Set your agenda first");
        phase = Phase.GOVERNING;
        date = Calendar2028.INAUGURATION_2029;
        economy = Economy.baseline2029();
        startMisery = economy.misery();
        int myEv = result2028.electoralVotes(player.party());
        approval = new ApprovalModel(49 + (myEv - 270) / 40.0, 7);
        capital = 40 + (myEv - 270) / 5.0 + (congress.unified(player.party()) ? 10 : 0);
        attention = ATTENTION_PER_MONTH;
        log("Inauguration Day. Approval " + Math.round(approval.approval()) + "%, political capital " + Math.round(capital) + ".");
    }

    private void governingMonth() {
        economy.stepMonth(rng);
        approval.stepMonth(economy, rng);
        for (World.Escalation e : world.stepMonth(rng)) queueEvent(EventCatalog.escalation(e));
        rollEvents();
        capital = Math.max(0, Math.min(150, capital + (approval.approval() - 45) * 0.15 + 1));
        recentAddresses *= 0.5;
        decaySalience();
        date = date.plusMonths(1);
        attention = ATTENTION_PER_MONTH;

        if (!midtermsHeld && !date.isBefore(Calendar2028.MIDTERMS_2030)) holdMidterms();
        if (!date.isBefore(Calendar2028.REELECTION_START)) beginReelection();
    }

    private void holdMidterms() {
        midtermsHeld = true;
        double presidentMargin = 0.6 * (approval.approval() - 48) + 0.5 * (economy.growth() - 2) - 2.5 + 2.0 * rng.gaussian("congress.elections");
        double marginD = presidentMargin * player.party().sign();
        int houseD = (int) Math.round(218 + 5.5 * marginD + 5 * rng.gaussian("congress.elections"));
        int before = congress.house(player.party());
        congress.setHouse(Party.DEMOCRAT, Math.max(165, Math.min(295, houseD)));
        int notUpD = congress.senate(Party.DEMOCRAT) - (int) states.all().stream().filter(s -> s.senateUp2030().orElse(null) == Party.DEMOCRAT).count();
        int senBefore = congress.senate(player.party());
        congress.setSenate(Party.DEMOCRAT, notUpD + senateRaces(false, marginD - MARGIN_2024));
        log(String.format("Midterms: your party %+d House seats, %+d Senate seats. %s.",
                congress.house(player.party()) - before, congress.senate(player.party()) - senBefore, congress));
    }

    private void beginReelection() {
        phase = Phase.REELECTION_CAMPAIGN_2032;
        date = Calendar2028.REELECTION_START;
        List<Candidate> field = candidates.forParty(opponent.party()).stream()
                .filter(c -> !c.id().equals(opponent.candidate().id())).toList();
        Candidate challenger = field.get(rng.stream("reelection").nextInt(field.size()));
        double warChest = 95 + capital * 0.3;
        player = new CampaignSide(player.party(), player.candidate(), warChest);
        opponent = new CampaignSide(opponent.party(), challenger, 140 * setup.difficulty().opponentResources());
        campaignSwingForPlayer = 0;
        systematicMiss = rng.gaussian("polls.miss") * 1.5;
        log("The re-election campaign begins against " + challenger.name() + ".");
    }

    // =========================================================================================
    //  Governing commands
    // =========================================================================================

    private void spendAttention(int cost) {
        if (phase != Phase.GOVERNING) throw new IllegalCommandException("Only while governing");
        if (attention < cost) throw new IllegalCommandException("Not enough attention left this month (" + attention + ")");
        attention -= cost;
    }

    public WhipCount whip(String billId, LegislativeStrategy strategy, double capitalCommitted) {
        return legislature.whip(bill(billId), strategy, congress, player.party(), currentApproval(), capitalCommitted + agendaBonus(billId));
    }

    /** Approval for formulas; before inauguration, the expected starting approval. */
    private double currentApproval() { return approval == null ? 49 : approval.approval(); }

    private double agendaBonus(String billId) { return agenda.contains(billId) ? 15 : 0; }

    private void pushBill(String id, LegislativeStrategy strategy, double capitalCommitted) {
        Bill b = bill(id);
        if (laws.contains(id)) throw new IllegalCommandException(b.name() + " is already law");
        if (!b.availableTo(player.party()) && strategy != LegislativeStrategy.BIPARTISAN_DEAL)
            throw new IllegalCommandException("You can only take up the other party's bill as a bipartisan deal");
        if (capitalCommitted < 0 || capitalCommitted > capital) throw new IllegalCommandException("Not enough political capital");
        if (strategy == LegislativeStrategy.RECONCILIATION) {
            if (b.requires60()) throw new IllegalCommandException("The Byrd rule keeps this bill out of reconciliation");
            if (!congress.unified(player.party())) throw new IllegalCommandException("Reconciliation needs your party in control of both chambers");
            if (reconciliationYears.contains(date.getYear())) throw new IllegalCommandException("Reconciliation is already used this year");
        }
        spendAttention(strategy == LegislativeStrategy.EXECUTIVE_ORDER ? 1 : 2);
        capital -= capitalCommitted;
        if (strategy == LegislativeStrategy.RECONCILIATION) reconciliationYears.add(date.getYear());

        if (strategy == LegislativeStrategy.EXECUTIVE_ORDER) {
            boolean blocked = rng.chance("courts", 0.35);
            if (blocked) { log("A federal judge blocks your executive order on " + b.issue().label().toLowerCase() + "."); approval.add(-1); return; }
            enact(b, strategy);
            return;
        }
        LegislativeEngine.Outcome o = legislature.vote(b, strategy, congress, player.party(), approval.approval(), capitalCommitted + agendaBonus(id), rng);
        log(b.name() + " (" + strategy.label() + "): " + o.explanation());
        if (o.passed()) { enact(b, strategy); capital += 5; }
        else { capital = Math.max(0, capital - 5); approval.add(-1.5); }
    }

    private void enact(Bill b, LegislativeStrategy s) {
        double k = s.payoff();
        laws.add(b.id());
        approval.add(b.publicAppeal() * k + (agenda.contains(b.id()) ? 1.0 : 0));
        economy.addShock(b.growthImpulse() * k, b.inflationImpulse() * k, 12);
        economy.addPolicyDeficit(b.deficitPctGdp() * k);
        bumpSalience(b.issue(), -0.02);
        log("Signed into law: " + b.name() + ".");
    }

    private void publicAddress(Issue issue) {
        spendAttention(1);
        // Overexposure: each recent address is worth half the one before it.
        double gain = (0.2 + 0.06 * player.candidate().charisma() + 0.05 * issue.advantageFor(player.party())) * Math.pow(0.5, recentAddresses);
        recentAddresses++;
        approval.add(Math.max(0.05, gain));
        bumpSalience(issue, 0.005);
        log("You address the nation on " + issue.label().toLowerCase() + ".");
    }

    private void bolsterDeterrence() {
        spendAttention(1);
        world.addDeterrence(6);
        world.addMunitions(5);
        world.addTension(HotSpot.TAIWAN_STRAIT, 2);
        economy.addPolicyDeficit(0.03);
        log("You order more ships, exercises and munitions for the Pacific.");
    }

    private void diplomacy(HotSpot h) {
        spendAttention(1);
        boolean ok = rng.chance("diplomacy", 0.55 + 0.03 * (player.candidate().policy() - 5));
        world.addTension(h, ok ? -10 : -2);
        log(ok ? "Your diplomacy on the " + h.label() + " eases tensions." : "Talks on the " + h.label() + " go nowhere.");
    }

    // =========================================================================================
    //  Events
    // =========================================================================================

    private void rollEvents() {
        for (EventDefinition d : eventDefinitions) {
            if (!d.phases().contains(phase)) continue;
            if (!d.repeatable() && firedEvents.contains(d.id())) continue;
            if (pendingEvents.stream().anyMatch(p -> p.definition().id().equals(d.id()))) continue;
            double chance = d.chancePerTurn().applyAsDouble(this);
            if (chance > 0 && rng.chance("events." + d.id(), chance)) queueEvent(d);
        }
    }

    private void queueEvent(EventDefinition d) {
        firedEvents.add(d.id());
        pendingEvents.add(new PendingEvent(nextEventId++, d));
        log("EVENT: " + d.title());
    }

    private void resolveEvent(int instanceId, int optionIndex) {
        PendingEvent p = pendingEvents.stream().filter(e -> e.instanceId() == instanceId).findFirst()
                .orElseThrow(() -> new IllegalCommandException("No pending event " + instanceId));
        List<EventOption> options = p.definition().options();
        if (optionIndex < 0 || optionIndex >= options.size()) throw new IllegalCommandException("No option " + optionIndex);
        EventOption o = options.get(optionIndex);
        pendingEvents.remove(p);
        boolean success = o.successChance() >= 1.0 || rng.chance("events.outcome", o.successChance());
        List<Effect> effects = success ? o.effects() : o.failureEffects();
        log(p.definition().title() + ": " + o.label() + (o.successChance() < 1 ? (success ? " (it works)" : " (it fails)") : ""));
        effects.forEach(this::applyEffect);
        o.hook().ifPresent(this::runHook);
    }

    void applyEffect(Effect e) {
        switch (e) {
            case Effect.Approval a -> { if (approval != null) approval.add(a.points()); else campaignSwingForPlayer += a.points() * 0.3; }
            case Effect.Capital c -> capital = Math.max(0, capital + c.points());
            case Effect.EconomyShock s -> { if (economy != null) economy.addShock(s.growthPoints(), s.inflationPoints(), s.months()); }
            case Effect.CampaignSwing s -> campaignSwingForPlayer += s.points();
            case Effect.Salience s -> bumpSalience(s.issue(), s.delta());
            case Effect.Tension t -> world.addTension(t.hotSpot(), t.delta());
            case Effect.Deterrence d -> world.addDeterrence(d.delta());
            case Effect.Cash c -> player.addCash(c.millions());
            case Effect.Deficit d -> { if (economy != null) economy.addPolicyDeficit(d.pctGdp()); }
        }
    }

    private void runHook(String hook) {
        if (!hook.startsWith("theater:")) throw new IllegalStateException("Unknown hook " + hook);
        TheaterScenario.Commitment c = TheaterScenario.Commitment.valueOf(hook.substring("theater:".length()));
        TheaterScenario s = new TheaterScenario(world.deterrence(), world.munitions(), world.japanBasing(), world.warningMonths(), c);
        TheaterOutcome o = theater.resolve(s, rng);
        war = Optional.of(o);
        world.endWar(o.deterrenceAfter());
        if (approval != null) approval.add(o.approvalShift());
        if (economy != null) economy.addShock(o.growthShock(), o.inflationShock(), 12);
        log("WAR IN THE TAIWAN STRAIT: " + o.summary() + (o.usKilled() > 0 ? " " + o.usKilled() + " Americans killed." : ""));
    }

    private void bumpSalience(Issue i, double delta) {
        salience.put(i, Math.max(0.01, Math.min(0.30, salience.get(i) + delta)));
    }

    private void decaySalience() {
        for (Issue i : Issue.values()) salience.merge(i, 0.0, (old, z) -> old + 0.2 * (i.baseSalience() - old));
    }

    // =========================================================================================
    //  Read-only view (UI, AI, events, tests)
    // =========================================================================================

    /** National swing versus 2024, D minus R, in points. */
    public double nationalSwing() {
        Candidate d = side(Party.DEMOCRAT).candidate(), r = side(Party.REPUBLICAN).candidate();
        double environment;
        if (phase == Phase.REELECTION_CAMPAIGN_2032 || (phase == Phase.EPILOGUE && result2032 != null)) {
            double forIncumbent = 0.55 * (approval.approval() - 48) + 0.7 * (economy.growth() - 2) - 0.5 * (economy.inflation() - 2.5)
                    + (war.isPresent() && war.get().status() == TheaterOutcome.TaiwanStatus.FALLEN ? -2 : 0)
                    - 0.4 * agenda.stream().filter(a -> !laws.contains(a)).count();
            environment = forIncumbent * player.party().sign();
        } else {
            environment = setup.scenario().environment();
        }
        double quality = 2.5 * (d.quality() - r.quality()) - 4.0 * (d.extremity() - r.extremity());
        double campaign = campaignEngine.momentumSwing(side(Party.DEMOCRAT), side(Party.REPUBLICAN)) + campaignSwingForPlayer * player.party().sign();
        return environment + quality + campaign - MARGIN_2024;
    }

    public double localShift(String state) {
        return campaignEngine.localShift(state, side(Party.DEMOCRAT), side(Party.REPUBLICAN));
    }

    /** Error-free state margins today (hidden from players; used by tests and debug views). */
    public Map<String, Double> expectedMargins() { return elections.expectedMargins(nationalSwing(), this::localShift); }

    /** The polls the player sees this turn (deterministic per turn so the view is stable). */
    public Map<String, Double> statePolls() {
        RngStreams view = new RngStreams(setup.seed() * 31 + history.size());
        Map<String, Double> out = new LinkedHashMap<>();
        expectedMargins().forEach((s, m) -> out.put(s, m - systematicMiss + 1.8 * view.gaussian("polls")));
        return out;
    }

    public CampaignSide side(Party p) { return player.party() == p ? player : opponent; }
    public CampaignSide player() { return player; }
    public CampaignSide opponent() { return opponent; }
    public Optional<LocalDate> debateThisWeek() {
        List<LocalDate> list = phase == Phase.GENERAL_CAMPAIGN_2028 ? DEBATES_2028 : phase == Phase.REELECTION_CAMPAIGN_2032 ? DEBATES_2032 : List.of();
        return list.stream().filter(d -> !d.isBefore(date) && d.isBefore(date.plusWeeks(1))).findFirst();
    }

    public GameSetup setup() { return setup; }
    public Phase phase() { return phase; }
    public LocalDate date() { return date; }
    public StateCatalog states() { return states; }
    public BillCatalog bills() { return bills; }
    public Bill bill(String id) { return bills.get(id); }
    public Map<Issue, Double> salience() { return Collections.unmodifiableMap(salience); }
    public List<PendingEvent> pendingEvents() { return List.copyOf(pendingEvents); }
    public List<String> log() { return Collections.unmodifiableList(log); }
    public List<Command> history() { return List.copyOf(history); }
    public Congress congress() { return congress; }
    public Optional<Economy> economy() { return Optional.ofNullable(economy); }
    public double approval() { return approval == null ? Double.NaN : approval.approval(); }
    public double capital() { return capital; }
    public int attention() { return attention; }
    public List<String> agenda() { return List.copyOf(agenda); }
    public List<String> laws() { return List.copyOf(laws); }
    public World world() { return world; }
    public Optional<TheaterOutcome> war() { return war; }
    public Optional<ElectionResult> result2028() { return Optional.ofNullable(result2028); }
    public Optional<ElectionResult> result2032() { return Optional.ofNullable(result2032); }
    public double startMisery() { return startMisery; }
    public Optional<Legacy> legacy() { return Optional.ofNullable(legacy); }
    public boolean over() { return phase == Phase.EPILOGUE || phase == Phase.GAME_OVER; }

    public boolean unifiedGovernment() { return congress != null && congress.unified(player.party()); }

    private void requireCampaign() {
        if (phase != Phase.GENERAL_CAMPAIGN_2028 && phase != Phase.REELECTION_CAMPAIGN_2032)
            throw new IllegalCommandException("Only during a campaign");
    }

    private void log(String s) { log.add(date + "  " + s); }
}
