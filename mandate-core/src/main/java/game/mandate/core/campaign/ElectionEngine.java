package game.mandate.core.campaign;

import game.mandate.core.data.Fmt;
import java.util.List;
import java.util.ArrayList;
import game.mandate.core.electorate.ElectorateModel;
import game.mandate.core.electorate.CellElectorateModel;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.Congress;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.ElectionCalculator;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UniformSwingModel;
import game.mandate.core.electorate.UsState;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The single authority for electoral calculations: the national swing (open seat or incumbent record), campaign
 * effects, polls, presidential results, House and Senate elections and midterms. Every election in the game,
 * presidential, congressional and midterm, runs through the same electorate model.
 */
public final class ElectionEngine {
    private final StateCatalog states;
    private final CandidateCatalog candidates;
    private final CampaignParams p;
    private final CampaignEngine campaigns;
    private final ElectorateModel model;
    private final ElectionCalculator calculator;

    public ElectionEngine(StateCatalog states, CandidateCatalog candidates, CampaignParams p) {
        this.states = states;
        this.candidates = candidates;
        this.p = p;
        this.campaigns = new CampaignEngine(states, p);
        this.model = p.cellElectorate() ? CellElectorateModel.load() : new UniformSwingModel();
        this.calculator = new ElectionCalculator(states, model, p.nationalErrorSd(), p.stateErrorSd());
    }

    public CampaignParams params() { return p; }
    public ElectionCalculator calculator() { return calculator; }
    public ElectorateModel model() { return model; }

    public CampaignEngine campaigns() { return campaigns; }
    public CampaignGeography geography() { return campaigns.geography(); }

    /** A new general election in mid-July: both nominees open with Battleground 2028's summer position (money, list, offices). */
    public ElectionState newElection(Candidate mine, Candidate theirs, double opponentResources, DeterministicRandomManager rng) {
        ElectionState e = new ElectionState();
        e.player = new CampaignSide(mine.party(), mine, 0, geography());
        e.opponent = new CampaignSide(theirs.party(), theirs, 0, geography());
        campaigns.openCampaign(e.player, 1.0);
        campaigns.openCampaign(e.opponent, opponentResources);
        e.systematicMiss = rng.gaussian("polls.miss") * p.pollMissSd();
        return e;
    }

    // ---- transitions --------------------------------------------------------------------------

    public boolean apply(ElectionState e, Effect x) {
        switch (x) {
            case Effect.CampaignSwing s -> e.campaignSwingForPlayer += s.points();
            case Effect.Approval a -> e.campaignSwingForPlayer += a.points() * p.approvalToSwing();
            case Effect.Cash c -> e.player.cash += c.millions();
            default -> { return false; }
        }
        return true;
    }

    // ---- campaigning --------------------------------------------------------------------------

    public void setPendingPosture(ElectionState e, DebatePosture posture) { e.pendingPosture = posture; }

    /**
     * One campaign turn (a week, or a day in the final stretch): both campaigns carry out their standing orders, the
     * campaign swing fades, and a debate (if any) is fought.
     * @param news lines for the player (rallies, shows, gaffes, ads pulled)
     * @return the debate's swing toward the player, if a debate was held
     */
    public Optional<Double> campaignTurn(ElectionState e, CampaignEngine.Turn t, double opponentResources, Optional<DebatePosture> opponentPosture,
                                         DeterministicRandomManager rng, List<String> news) {
        List<String> theirs = new ArrayList<>();
        campaigns.execute(e.player, e.opponent, t, 1.0, true, rng, news);
        campaigns.execute(e.opponent, e.player, t, opponentResources, false, rng, theirs);
        for (String line : theirs) if (line.startsWith("GAFFE")) news.add(line);
        e.campaignSwingForPlayer *= Math.pow(p.campaignSwingDecay(), t.fraction());
        Optional<Double> result = Optional.empty();
        if (opponentPosture.isPresent()) {
            DebatePosture theirs2 = opponentPosture.get();
            double swingD = e.player.party() == Party.DEMOCRAT
                    ? campaigns.debate(e.player, e.pendingPosture, e.opponent, theirs2, rng)
                    : campaigns.debate(e.opponent, theirs2, e.player, e.pendingPosture, rng);
            double forPlayer = swingD * e.player.party().sign();
            e.campaignSwingForPlayer += forPlayer;
            result = Optional.of(forPlayer);
        }
        e.pendingPosture = null;
        return result;
    }

    // ---- orders: the player's commands and the planners' plans --------------------------------------------------------

    /**
     * Copies a planner's orders into a campaign, area by area (the schedule; the air war; the field), with the message. The
     * opponent's headquarters tier follows its planner; the player's is the player's own choice.
     */
    public void applyPlan(ElectionState e, boolean player, CampaignOrders plan, boolean schedule, boolean ads, boolean field) {
        CampaignSide side = player ? e.player : e.opponent;
        side.orders.copyFrom(plan, schedule, ads, field);
        side.orders.message = plan.message;
        if (!player) side.orders.hq = plan.hq;
    }

    /** Changes the player's standing orders: the edit runs on a copy, so an edit that fails part-way changes nothing. */
    public void editOrders(ElectionState e, java.util.function.Consumer<CampaignOrders> edit) {
        CampaignOrders o = e.player.orders.copy();
        edit.accept(o);
        e.player.orders.copyFrom(o, true, true, true);
        e.player.orders.message = o.message;
        e.player.orders.messageTone = o.messageTone;
        e.player.orders.hq = o.hq;
    }

    public void setStaff(ElectionState e, ElectionState.StaffArea area, boolean on) { e.staff.put(area, on); }

    /** Ad Studio: produce an ad (it joins the library, $0.12M). */
    public void produceAd(ElectionState e, int tone, Issue issue, int group, AirWar.Messenger messenger, Map<Issue, Double> salience,
                          java.time.LocalDate date, DeterministicRandomManager rng) {
        if (tone < 0 || tone > 2) throw new IllegalArgumentException("Tone must be 0, 1 or 2");
        if (group < -1 || group >= CampaignGeography.NG) throw new IllegalArgumentException("Unknown voter group " + group);
        campaigns.produceAd(e.player, tone, issue, group, messenger, salience, date, rng);
    }

    /** Ad Studio: show an ad to a focus group ($0.045M). */
    public void testAd(ElectionState e, int index, DeterministicRandomManager rng) {
        if (index < 0 || index >= e.player.ads.size()) throw new IllegalArgumentException("No ad " + index);
        campaigns.testAd(e.player, index, rng);
    }

    /** Ad Studio: put an ad on the air (-1 takes it off). */
    public void airAd(ElectionState e, int index) {
        if (index < -1 || index >= e.player.ads.size()) throw new IllegalArgumentException("No ad " + index);
        e.player.airing = index;
    }

    /**
     * A hand-built week from the original campaign screen (rallies, fundraisers, ad and field money, a message), turned into
     * Battleground 2028 orders for this week: the rallies on weekday afternoons, the fundraisers on those evenings in the
     * nearest donor hub, the ad money as digital in each state, the field money as organisers (and offices to hold them).
     */
    public void applyHandPlan(ElectionState e, CampaignWeekPlan plan) {
        CampaignGeography geo = geography();
        editOrders(e, o -> {
            o.clearSchedule(0);
            int[] afternoons = {1, 7, 13}, evenings = {2, 8, 14};
            int last = e.player.location;
            for (int i = 0; i < plan.rallyStates().size() && i < afternoons.length; i++) {
                int st = geo.index(plan.rallyStates().get(i));
                o.slot(afternoons[i], Activity.RALLY, st, 0);
                last = st;
            }
            for (int i = 0; i < plan.fundraisers() && i < evenings.length; i++) o.slot(evenings[i], Activity.FUNDRAISER, nearestHub(geo, last), 0);
            o.slot(18, Activity.REST, -1, 0);
            o.clearDigital();
            plan.adSpend().forEach((code, m) -> o.digital(geo.index(code), m));
            plan.fieldSpend().forEach((code, m) -> {
                int st = geo.index(code);
                int hires = (int) Math.floor(m / (CampaignEngine.organizerHireCost() + campaigns.organizerWeek(st) * 4));
                double after = e.player.organizers[st] + hires;
                int offices = (int) Math.max(0, Math.ceil(after / 7.0) - e.player.offices[st]);
                o.field(st, offices, hires);
            });
            o.message(plan.message());
        });
    }

    static int nearestHub(CampaignGeography geo, int from) {
        int best = from;
        double bd = Double.MAX_VALUE;
        for (int s = 0; s < geo.size(); s++) {
            if (!geo.state(s).donorHub()) continue;
            double d = geo.distance(from, s);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** National swing versus 2024, D minus R, in points. */
    public double nationalSwing(ElectionState e, ElectoralEnvironment env) {
        Parts x = parts(e, env);
        return x.environment + x.quality + x.momentum + x.campaign - p.margin2024();
    }

    /** The national swing's parts, D minus R: the environment (for a re-election, the president's record), candidate
     *  quality, campaign momentum, and news, debates and promises. */
    public record Parts(double environment, double quality, double momentum, double campaign) {}

    public Parts parts(ElectionState e, ElectoralEnvironment env) {
        Candidate d = e.side(Party.DEMOCRAT).candidate(), r = e.side(Party.REPUBLICAN).candidate();
        double environment;
        if (env.incumbent()) {
            double forIncumbent = p.incApproval() * (env.approval() - p.incApprovalPivot()) + p.incGrowth() * (env.growth() - p.incGrowthPivot())
                    - p.incInflation() * (env.inflation() - p.incInflationPivot())
                    - p.incUnemployment() * (env.unemployment() - p.incUnemploymentPivot()) - (env.taiwanFell() ? p.incFallen() : 0)
                    - p.incUnfinished() * env.unfinishedAgenda();
            environment = forIncumbent * e.player.party().sign();
        } else {
            environment = env.scenarioEnvironment();
        }
        double quality = p.qualityWeight() * (d.quality() - r.quality()) - p.extremityWeight() * (d.extremity() - r.extremity());
        // a re-election is a referendum: the record sets the race, and the campaign moves it less (docs/MODES.md, "The 2032 campaign")
        double campaignWeight = env.incumbent() ? p.incCampaign() : 1.0;
        return new Parts(environment, quality, campaignWeight * campaigns.momentumSwing(e.side(Party.DEMOCRAT), e.side(Party.REPUBLICAN), env.salience()),
                campaignWeight * e.campaignSwingForPlayer * e.player.party().sign());
    }

    public double localShift(ElectionState e, String state) {
        return campaigns.localShift(state, e.side(Party.DEMOCRAT), e.side(Party.REPUBLICAN));
    }

    public Map<String, Double> expectedMargins(ElectionState e, ElectoralEnvironment env) {
        return calculator.expectedMargins(nationalSwing(e, env), s -> localShift(e, s));
    }

    /** The polls the player sees: truth minus the hidden miss plus noise from a throwaway stream (viewing never changes the game). */
    public Map<String, Double> statePolls(ElectionState e, ElectoralEnvironment env, long viewSeed) {
        DeterministicRandomManager view = new DeterministicRandomManager(viewSeed);
        Map<String, Double> out = new LinkedHashMap<>();
        expectedMargins(e, env).forEach((s, m) -> out.put(s, m - e.systematicMiss + p.statePollNoise() * view.gaussian("polls")));
        return java.util.Collections.unmodifiableMap(out);
    }

    // ---- elections ----------------------------------------------------------------------------

    /** Presidential election, plus the House and the Senate class elected with it. */
    public record PresidentialOutcome(ElectionResult result, Effect.CongressElected congress) {}

    public PresidentialOutcome presidential(ElectionState e, int year, ElectoralEnvironment env, Congress current,
                                            boolean senateClass2028, DeterministicRandomManager rng) {
        // The hidden miss lives in the polls (they show the truth minus the miss), so the result itself carries no
        // miss term. (Until the vertical slice it was added here as well, so polls were wrong by twice the miss.)
        // the result is computed exactly once: a second call would draw a different night from the same polls
        if (year == 2028 ? e.result2028 != null : e.result2032 != null) throw new IllegalStateException("The " + year + " election has already been held");
        ElectionResult r = calculator.resolve(year, nationalSwing(e, env), s -> localShift(e, s), 0, rng, "election");
        if (year == 2028) e.result2028 = r; else e.result2032 = r;
        int houseD = (int) Math.round(p.houseBase() + p.housePerPoint() * r.popularMargin() + p.houseNoise() * rng.gaussian("congress.elections"));
        houseD = (int) Math.max(p.houseMin(), Math.min(p.houseMax(), houseD));
        int senateD = current.senate(Party.DEMOCRAT);
        if (senateClass2028) {
            int notUpD = senateD - (int) states.all().stream().filter(s -> s.senateUp2028().orElse(null) == Party.DEMOCRAT).count();
            senateD = notUpD + senateRaces(true, r.popularMargin() - p.margin2024(), rng);
        }
        return new PresidentialOutcome(r, new Effect.CongressElected(houseD, senateD));
    }

    /** Midterms: the president's party usually loses ground; Class I Senate seats run through the electorate model. */
    public Effect.CongressElected midterm(ElectionState e, Party president, double approval, double growth, Congress current, int year,
                                          DeterministicRandomManager rng) {
        double presidentMargin = p.midApproval() * (approval - p.midApprovalPivot()) + p.midGrowth() * (growth - p.midGrowthPivot())
                + p.midPenalty() + p.midNoise() * rng.gaussian("congress.elections");
        double marginD = presidentMargin * president.sign();
        int houseD = (int) Math.round(p.houseBase() + p.housePerPoint() * marginD + p.houseNoise() * rng.gaussian("congress.elections"));
        houseD = (int) Math.max(p.midHouseMin(), Math.min(p.midHouseMax(), houseD));
        int notUpD = current.senate(Party.DEMOCRAT) - (int) states.all().stream().filter(s -> s.senateUp2030().orElse(null) == Party.DEMOCRAT).count();
        int senateD = notUpD + senateRaces(false, marginD - p.margin2024(), rng);
        int houseBefore = current.house(president), senateBefore = current.senate(president);
        int houseAfter = president == Party.DEMOCRAT ? houseD : Congress.HOUSE_SIZE - houseD;
        int senateAfter = president == Party.DEMOCRAT ? senateD : Congress.SENATE_SIZE - senateD;
        e.midterm = new ElectionState.Midterm(year, presidentMargin, houseAfter - houseBefore, senateAfter - senateBefore);
        return new Effect.CongressElected(houseD, senateD);
    }

    /** Expected House seat change for the president's party at a midterm held today (no noise): the same formula as {@link #midterm}. */
    public int midtermSeatOutlook(double approval, double growth) {
        double presidentMargin = p.midApproval() * (approval - p.midApprovalPivot()) + p.midGrowth() * (growth - p.midGrowthPivot()) + p.midPenalty();
        return (int) Math.round(p.housePerPoint() * presidentMargin);
    }

    /** Resolves one Senate class through the shared electorate model. Returns Democratic wins. */
    private int senateRaces(boolean class2028, double nationalSwing, DeterministicRandomManager rng) {
        int wonD = 0;
        for (UsState s : states.all()) {
            Optional<Party> holder = class2028 ? s.senateUp2028() : s.senateUp2030();
            if (holder.isEmpty()) continue;
            double m = model.expectedMargin(s, nationalSwing, 0) + p.senateIncumbency() * holder.get().sign()
                    + p.senateNoise() * rng.gaussian("congress.elections");
            if (m >= 0) wonD++;
        }
        return wonD;
    }

    // ---- the streamlined campaign, promises and the polling the player sees ------------------------

    public void setOrders(ElectionState e, boolean player, CampaignStrategy.Orders orders, String reasoning) {
        if (player) e.playerOrders = orders;
        else { e.opponentOrders = orders; e.opponentReasoning = reasoning; }
    }

    /** Why a pledge is refused, or null. */
    public String pledgeRefusal(ElectionState e, Bill b) {
        if (e.pledges.contains(b.id())) return "You have already promised the " + b.name();
        if (e.pledges.size() >= p.pledgeMax()) return "You have made " + p.pledgeMax() + " promises; more would not be believed";
        if (!b.availableTo(e.player.party())) return "The " + b.name() + " is the other party's bill";
        return null;
    }

    /** Records a campaign promise; returns its campaign-season effects (a little swing, and the issue's salience). */
    public List<Effect> pledge(ElectionState e, Bill b) {
        e.pledges.add(b.id());
        return List.of(new Effect.CampaignSwing(p.pledgeSwingPerAppeal() * b.publicAppeal()), new Effect.Salience(b.issue(), p.pledgeSalience()));
    }

    public void setOpponentPledges(ElectionState e, List<String> ids) {
        e.opponentPledges.clear();
        e.opponentPledges.addAll(ids);
    }

    /** How many promises a campaign can make and be believed. */
    public int pledgeMax() { return p.pledgeMax(); }

    /** The penalty for each promise left off the agenda at the transition. */
    public double brokenPledgeMood() { return p.pledgeBrokenMood(); }

    /** What the race is made of right now, from the player's side: the start of a week's explanation. */
    public Snapshot snapshot(ElectionState e, ElectoralEnvironment env) {
        Parts x = parts(e, env);
        int sign = e.player.party().sign();
        Map<String, Double> local = new LinkedHashMap<>();
        for (UsState s : states.all()) local.put(s.code(), localShift(e, s.code()) * sign);
        return new Snapshot(x.environment * sign, x.momentum * sign, x.campaign * sign, e.player.momentum(), -e.opponent.momentum(), local);
    }

    /** The race from the player's side: national parts and each state's campaign effect, in points toward the player. */
    public record Snapshot(double environment, double momentum, double campaign, double playerMomentum, double opponentMomentum,
                           Map<String, Double> local) {
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("environment", environment); m.put("momentum", momentum); m.put("campaign", campaign);
            m.put("playerMomentum", playerMomentum); m.put("opponentMomentum", opponentMomentum); m.put("local", new LinkedHashMap<>(local));
            return m;
        }

        static Snapshot fromJson(Map<String, Object> m) {
            Map<String, Double> local = new LinkedHashMap<>();
            game.mandate.core.data.Json.object(m.get("local")).forEach((k, v) -> local.put(k, game.mandate.core.data.Json.num(v)));
            return new Snapshot(game.mandate.core.data.Json.num(m.get("environment")), game.mandate.core.data.Json.num(m.get("momentum")),
                    game.mandate.core.data.Json.num(m.get("campaign")), game.mandate.core.data.Json.num(m.get("playerMomentum")),
                    game.mandate.core.data.Json.num(m.get("opponentMomentum")), local);
        }
    }

    /** Marks where the race stands, so the next recorded poll explains the movement from here. */
    public void markBaseline(ElectionState e, ElectoralEnvironment env) { e.lastSnapshot = snapshot(e, env); }

    /**
     * Records this week's poll: the state polls the player sees (truth, minus the hidden miss, plus noise from a
     * throwaway stream), the projection and forecast they imply, and what moved since {@code before}.
     */
    public PollPoint recordPoll(ElectionState e, ElectoralEnvironment env, java.time.LocalDate date, int year, long viewSeed) {
        Snapshot before = e.lastSnapshot == null ? snapshot(e, env) : e.lastSnapshot;
        int sign = e.player.party().sign();
        Map<String, Double> polls = statePolls(e, env, viewSeed);
        ElectionResult projected = calculator.tally(year, polls);
        Snapshot now = snapshot(e, env);
        Map<String, Double> seen = new java.util.TreeMap<>();
        polls.forEach((k, v) -> seen.put(k, Math.round(v * 100) / 100.0));
        PollPoint point = new PollPoint(date, projected.popularMargin() * sign, projected.electoralVotes(e.player.party()),
                winChance(polls, sign), projected.tippingPoint(), movers(e, before, now, polls), seen);
        e.polls.add(point);
        e.lastSnapshot = now;
        return point;
    }

    /**
     * The player's chance of winning given the polls and their historical error: the national error (plus the
     * polling miss), scaled by each state's elasticity, is integrated on a grid; given it, states are independent
     * with state-level noise, and the electoral-vote total is taken as normal. Deterministic: no random stream.
     */
    double winChance(Map<String, Double> polls, int sign) {
        // Polls miss the result by the hidden miss plus the election-day national error, and each state poll by its
        // own noise plus the state-level error.
        double nationalSd = Math.hypot(p.nationalErrorSd(), p.pollMissSd()), stateSd = Math.hypot(p.stateErrorSd(), p.statePollNoise());
        int n = p.forecastGrid();
        double total = 0, weights = 0;
        for (int i = 0; i < n; i++) {
            double z = -4.0 + 8.0 * i / (n - 1), w = Math.exp(-0.5 * z * z);
            double mean = 0, var = 0;
            for (UsState s : states.all()) {
                double q = phi((polls.get(s.code()) + z * nationalSd * s.elasticity()) * sign / stateSd);
                mean += s.electoralVotes() * q;
                var += s.electoralVotes() * s.electoralVotes() * q * (1 - q);
            }
            total += w * (var < 1e-9 ? (mean >= 269.5 ? 1 : 0) : phi((mean - 269.5) / Math.sqrt(var)));
            weights += w;
        }
        return total / weights;
    }

    /**
     * Each state's chance of going the player's way, from the polls alone and the same published error model as
     * {@link #winChance}: the national error (with the polling miss), scaled by the state's elasticity, integrated on
     * the same grid, plus the state's own error. It reads only the polls the player sees.
     */
    public Map<String, Double> stateWinChances(Map<String, Double> polls, int sign) {
        double nationalSd = Math.hypot(p.nationalErrorSd(), p.pollMissSd()), stateSd = Math.hypot(p.stateErrorSd(), p.statePollNoise());
        int n = p.forecastGrid();
        Map<String, Double> out = new java.util.TreeMap<>();
        for (UsState s : states.all()) {
            double total = 0, weights = 0;
            for (int i = 0; i < n; i++) {
                double z = -4.0 + 8.0 * i / (n - 1), w = Math.exp(-0.5 * z * z);
                total += w * phi((polls.get(s.code()) + z * nationalSd * s.elasticity()) * sign / stateSd);
                weights += w;
            }
            out.put(s.code(), total / weights);
        }
        return out;
    }

    /** The typical error of a national polling average against the result (one standard deviation, points). */
    public double nationalPollErrorSd() { return Math.hypot(p.nationalErrorSd(), p.pollMissSd()); }

    /** Standard normal CDF (Abramowitz and Stegun 7.1.26; error below 2e-7). */
    static double phi(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x) / Math.sqrt(2));
        double erf = 1 - t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429)))) * Math.exp(-x * x / 2);
        return x >= 0 ? (1 + erf) / 2 : (1 - erf) / 2;
    }

    private List<String> movers(ElectionState e, Snapshot a, Snapshot b, Map<String, Double> polls) {
        record Mover(double size, String text) {}
        List<Mover> out = new ArrayList<>();
        double mine = b.playerMomentum() - a.playerMomentum(), theirs = b.opponentMomentum() - a.opponentMomentum();
        Issue myMessage = e.player.lastMessage(), theirMessage = e.opponent.lastMessage();
        if (Math.abs(mine) >= 0.05) out.add(new Mover(mine, Fmt.format("Your campaign's momentum%s: %+.1f", myMessage == null ? ""
                : " (message: " + myMessage.label().toLowerCase() + ", week " + e.player.weeksOnMessage() + ")", mine)));
        if (Math.abs(theirs) >= 0.05) out.add(new Mover(theirs, Fmt.format("Your opponent's momentum%s: %+.1f", theirMessage == null ? ""
                : " (message: " + theirMessage.label().toLowerCase() + ")", theirs)));
        double news = b.campaign() - a.campaign();
        if (Math.abs(news) >= 0.05) out.add(new Mover(news, Fmt.format("News, debates and promises, net of fading: %+.1f", news)));
        double env = b.environment() - a.environment();
        if (Math.abs(env) >= 0.05) out.add(new Mover(env, Fmt.format("The national environment (approval and the economy): %+.1f", env)));
        // The ground war: the biggest changes among the states within 6 points in the polls, weighted by electoral votes.
        List<UsState> close = new ArrayList<>(states.all());
        close.removeIf(s -> Math.abs(polls.get(s.code())) > 6);
        close.sort(java.util.Comparator.comparingDouble((UsState s) -> -Math.abs(b.local().get(s.code()) - a.local().get(s.code())) * s.electoralVotes()));
        for (UsState s : close.subList(0, Math.min(3, close.size()))) {
            double d = b.local().get(s.code()) - a.local().get(s.code());
            if (Math.abs(d) >= 0.05)
                out.add(new Mover(d * s.electoralVotes() / 20.0, Fmt.format("Ads, field and visits in %s: %+.1f there", s.code(), d)));
        }
        out.sort(java.util.Comparator.comparingDouble((Mover m) -> -Math.abs(m.size())));
        List<String> lines = new ArrayList<>();
        for (Mover m : out) lines.add(m.text());
        if (lines.isEmpty()) lines.add("Little moved this week.");
        return lines;
    }

    /** Poll crosstabs by voter group (bloc, age, sex), when the electorate model has groups. Same noise as the national poll. */
    /** What an internal poll costs, $M: a state's, and the nation's. */
    public static final double STATE_POLL_COST = 0.12, NATIONAL_POLL_COST = 0.3;

    /**
     * Commissions an internal poll (the War room's Polls tab): the true margin for the player, with an error of about a
     * point, against the public polls' miss and noise. Paid from the campaign's cash; one of each a week.
     */
    public ElectionState.InternalPoll internalPoll(ElectionState e, ElectoralEnvironment env, String state, java.time.LocalDate date,
                                                  DeterministicRandomManager rng) {
        boolean nation = state.equals("US");
        if (!nation && !calculator.expectedMargins(nationalSwing(e, env), s -> localShift(e, s)).containsKey(state))
            throw new IllegalArgumentException("Unknown state " + state);
        for (ElectionState.InternalPoll x : e.internalPolls)
            if (x.state().equals(state) && !x.date().isBefore(date.minusDays(6))) throw new IllegalArgumentException("Already polled this week");
        double cost = nation ? NATIONAL_POLL_COST : STATE_POLL_COST;
        if (e.player.cash < cost) throw new IllegalArgumentException("Not enough cash for the poll");
        int sign = e.player.party().sign();
        double truth = nation ? calculator.tally(date.getYear(), expectedMargins(e, env)).popularMargin() : expectedMargins(e, env).get(state);
        double sd = nation ? 0.8 : 1.1;
        double margin = truth * sign + sd * rng.gaussian("campaign.internalPoll");
        CampaignEngine.pay(e.player, cost, -1, 0);
        ElectionState.InternalPoll poll = new ElectionState.InternalPoll(date, state, margin, sd);
        e.internalPolls.add(poll);
        if (e.internalPolls.size() > 60) e.internalPolls.remove(0);
        return poll;
    }

    /** A state's voters by bloc (the War room's Voters desk), as the polls see them: their size, lean, turnout and how movable. */
    public List<CellElectorateModel.StateGroup> stateGroups(ElectionState e, ElectoralEnvironment env, String state, double pollMargin) {
        if (!(model instanceof CellElectorateModel cells)) return List.of();
        return cells.stateGroups(state, nationalSwing(e, env), s -> localShift(e, s), pollMargin);
    }

    public Optional<CellElectorateModel.GroupResults> crosstabs(ElectionState e, ElectoralEnvironment env, long viewSeed) {
        if (!(model instanceof CellElectorateModel cells)) return Optional.empty();
        DeterministicRandomManager view = new DeterministicRandomManager(viewSeed);
        double noise = p.statePollNoise() * view.gaussian("crosstabs");
        return Optional.of(cells.groupResults(nationalSwing(e, env) - e.systematicMiss + noise, s -> localShift(e, s)));
    }

    /**
     * Why the player lost, in facts the game holds: the result, the closest path to 270, how far the polls were off
     * (the hidden miss, revealed), money left unspent, the ground war in the decisive states, and the opponent's plan.
     */
    /** A campaign's verdict (Campaign Only's end, and any 2028 campaign's): a grade from the electoral votes, and what decided it. */
    public record Assessment(boolean won, int electoralVotes, double popularMargin, String grade, String headline, List<String> lines) {}

    /** The grade a campaign's electoral votes earn, from a rout to a landslide. */
    public static String grade(int ev) {
        if (ev >= 375) return "A landslide";
        if (ev >= 320) return "A decisive win";
        if (ev >= 290) return "A clear win";
        if (ev >= 270) return "A narrow win";
        if (ev >= 250) return "A narrow loss";
        if (ev >= 200) return "A clear loss";
        return "A rout";
    }

    /** The assessment of a campaign, won or lost: the margin, the states that decided it, the polls, the money and the ground war. */
    public Assessment assessment(ElectionState e, ElectionResult r) {
        Party me = e.player.party();
        int sign = me.sign(), mine = r.electoralVotes(me);
        boolean won = r.winner() == me;
        String grade = grade(mine);
        String headline = Fmt.format("%s: %d electoral votes to %d, the popular vote %s by %.1f points.", grade, mine, 538 - mine,
                r.popularMargin() * sign >= 0 ? "yours" : "theirs", Math.abs(r.popularMargin()));
        if (!won) return new Assessment(false, mine, r.popularMargin() * sign, grade, headline, postMortem(e, r));
        List<String> out = new ArrayList<>();
        out.add(Fmt.format("You won %d: %d electoral votes to %d; the popular vote %s by %.1f.", r.year(), mine, 538 - mine,
                r.popularMargin() * sign >= 0 ? "went your way" : "went against you", Math.abs(r.popularMargin())));
        // the margin of safety: the states you won most narrowly, and how many of their votes you could have lost
        List<UsState> held = new ArrayList<>(states.all());
        held.removeIf(s -> r.stateMargins().get(s.code()) * sign <= 0);
        held.sort(java.util.Comparator.comparingDouble((UsState s) -> r.stateMargins().get(s.code()) * sign));
        int spare = mine - 270;
        List<String> close = new ArrayList<>();
        for (UsState s : held.subList(0, Math.min(4, held.size()))) close.add(Fmt.format("%s by %.1f", s.code(), r.stateMargins().get(s.code()) * sign));
        out.add(Fmt.format("Your margin of safety: %d electoral votes to spare. The tipping point was %s; your closest wins were %s.", spare, r.tippingPoint(),
                String.join(", ", close)));
        if (!e.polls.isEmpty()) {
            PollPoint last = e.polls.get(e.polls.size() - 1);
            double result = r.popularMargin() * sign, miss = result - last.national();
            out.add(Fmt.format("The final polls had you at %+.1f nationally (a %.0f%% chance); the result was %+.1f. %s", last.national(),
                    100 * last.winChance(), result, Math.abs(miss) < 1 ? "The polls were about right." : miss < 0
                            ? Fmt.format("The polls flattered you by %.1f points.", -miss) : Fmt.format("The polls underrated you by %.1f points.", miss)));
        }
        if (e.player.cash() > 20) out.add(Fmt.format("You finished with $%.0fM unspent.", e.player.cash()));
        List<UsState> decisive = held.subList(0, Math.min(3, held.size()));
        double myField = 0, theirField = 0, myAds = 0, theirAds = 0;
        for (UsState s : decisive) {
            myField += e.player.field(s.code()); theirField += e.opponent.field(s.code());
            myAds += e.player.ads(s.code()); theirAds += e.opponent.ads(s.code());
        }
        if (!decisive.isEmpty())
            out.add(Fmt.format("In %s, your field organisation stood at %.0f against their %.0f, and your ad weight at %.0f against %.0f.",
                    String.join(", ", decisive.stream().map(UsState::code).toList()), myField, theirField, myAds, theirAds));
        if (!e.opponentReasoning.isEmpty()) out.add("Your opponent's closing campaign: " + e.opponentOrders.describe() + ". " + e.opponentReasoning);
        return new Assessment(true, mine, r.popularMargin() * sign, grade, headline, out);
    }

    public List<String> postMortem(ElectionState e, ElectionResult r) {
        List<String> out = new ArrayList<>();
        Party me = e.player.party();
        int sign = me.sign(), mine = r.electoralVotes(me);
        out.add(Fmt.format("You lost %d: %d electoral votes to %d; the popular vote %s by %.1f.", r.year(), mine, 538 - mine,
                r.popularMargin() * sign >= 0 ? "went your way" : "went against you", Math.abs(r.popularMargin())));
        List<UsState> lost = new ArrayList<>(states.all());
        lost.removeIf(s -> r.stateMargins().get(s.code()) * sign >= 0);
        lost.sort(java.util.Comparator.comparingDouble((UsState s) -> -r.stateMargins().get(s.code()) * sign));
        int need = 270 - mine, gained = 0;
        List<String> path = new ArrayList<>();
        double swingNeeded = 0;
        for (UsState s : lost) {
            if (gained >= need) break;
            gained += s.electoralVotes();
            double m = -r.stateMargins().get(s.code()) * sign;
            swingNeeded = Math.max(swingNeeded, m);
            path.add(Fmt.format("%s by %.1f", s.code(), m));
        }
        out.add(Fmt.format("You were %d electoral votes short. The closest path ran through %s: about %.1f points of swing in those states.",
                need, String.join(", ", path), swingNeeded));
        if (!e.polls.isEmpty()) {
            PollPoint last = e.polls.get(e.polls.size() - 1);
            double result = r.popularMargin() * sign, miss = result - last.national();
            out.add(Fmt.format("The final polls had you at %+.1f nationally (a %.0f%% chance); the result was %+.1f. %s", last.national(),
                    100 * last.winChance(), result, Math.abs(miss) < 1 ? "The polls were about right." : miss < 0
                            ? Fmt.format("The polls flattered you by %.1f points.", -miss) : Fmt.format("The polls underrated you by %.1f points.", miss)));
        }
        if (e.player.cash() > 20) out.add(Fmt.format("You finished with $%.0fM unspent.", e.player.cash()));
        List<UsState> decisive = lost.subList(0, Math.min(3, lost.size()));
        double myField = 0, theirField = 0, myAds = 0, theirAds = 0;
        for (UsState s : decisive) {
            myField += e.player.field(s.code()); theirField += e.opponent.field(s.code());
            myAds += e.player.ads(s.code()); theirAds += e.opponent.ads(s.code());
        }
        if (!decisive.isEmpty())
            out.add(Fmt.format("In %s, your field organisation stood at %.0f against their %.0f, and your ad weight at %.0f against %.0f.",
                    String.join(", ", decisive.stream().map(UsState::code).toList()), myField, theirField, myAds, theirAds));
        if (!e.opponentReasoning.isEmpty()) out.add("Your opponent's closing campaign: " + e.opponentOrders.describe() + ". " + e.opponentReasoning);
        return out;
    }

    /** The re-election campaign: a fresh challenger, the incumbent's war chest, a new hidden polling miss. */
    public void beginReelection(ElectionState e, Candidate challenger, double capital, double opponentResources, DeterministicRandomManager rng) {
        e.player = new CampaignSide(e.player.party(), e.player.candidate(), 0, geography());
        e.opponent = new CampaignSide(challenger.party(), challenger, 0, geography());
        // Battleground 2028's opening position, with the incumbent's war chest set against the challenger's as before
        campaigns.openCampaign(e.player, (p.reelectionBase() + capital * p.reelectionPerCapital()) / p.challengerCash());
        campaigns.openCampaign(e.opponent, opponentResources);
        e.campaignSwingForPlayer = 0;
        e.systematicMiss = rng.gaussian("polls.miss") * p.pollMissSd();
    }

    public Candidate pickChallenger(ElectionState e, DeterministicRandomManager rng) {
        var field = candidates.forParty(e.opponent.party()).stream()
                .filter(c -> !c.id().equals(e.opponent.candidate().id()) && !c.id().equals(Candidate.CUSTOM_ID)).toList();
        // with real people, the challenger is one of them (the fictional cast is not part of that world)
        if (field.stream().anyMatch(Candidate::isReal)) field = field.stream().filter(Candidate::isReal).toList();
        return field.get(rng.nextInt("reelection", field.size()));
    }
}
