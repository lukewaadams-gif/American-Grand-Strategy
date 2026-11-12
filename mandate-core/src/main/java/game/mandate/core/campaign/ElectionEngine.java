package game.mandate.core.campaign;

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
    private final ElectionCalculator calculator;

    public ElectionEngine(StateCatalog states, CandidateCatalog candidates, CampaignParams p) {
        this.states = states;
        this.candidates = candidates;
        this.p = p;
        this.campaigns = new CampaignEngine(states, p);
        this.calculator = new ElectionCalculator(states, new UniformSwingModel(), p.nationalErrorSd(), p.stateErrorSd());
    }

    public CampaignParams params() { return p; }
    public ElectionCalculator calculator() { return calculator; }

    public ElectionState newElection(Candidate mine, Candidate theirs, double opponentResources, DeterministicRandomManager rng) {
        ElectionState e = new ElectionState();
        e.player = new CampaignSide(mine.party(), mine, p.startCash());
        e.opponent = new CampaignSide(theirs.party(), theirs, p.startCash() * opponentResources);
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

    public void setPendingPlan(ElectionState e, CampaignWeekPlan plan) { e.pendingPlan = plan; }
    public void setPendingPosture(ElectionState e, DebatePosture posture) { e.pendingPosture = posture; }

    /**
     * One campaign week: both plans execute, the campaign swing fades, and a debate (if any) is fought.
     * @return the debate's swing toward the player, if a debate was held
     */
    public Optional<Double> campaignWeek(ElectionState e, CampaignWeekPlan mine, CampaignWeekPlan theirs, Map<game.mandate.core.campaign.Issue, Double> salience,
                                         Optional<DebatePosture> opponentPosture, DeterministicRandomManager rng) {
        campaigns.execute(e.player, mine, salience);
        campaigns.execute(e.opponent, theirs, salience);
        e.campaignSwingForPlayer *= p.campaignSwingDecay();
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
        e.pendingPlan = null;
        e.pendingPosture = null;
        return result;
    }

    /** National swing versus 2024, D minus R, in points. */
    public double nationalSwing(ElectionState e, ElectoralEnvironment env) {
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
        double campaign = campaigns.momentumSwing(e.side(Party.DEMOCRAT), e.side(Party.REPUBLICAN)) + e.campaignSwingForPlayer * e.player.party().sign();
        return environment + quality + campaign - p.margin2024();
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
        return out;
    }

    // ---- elections ----------------------------------------------------------------------------

    /** Presidential election, plus the House and the Senate class elected with it. */
    public record PresidentialOutcome(ElectionResult result, Effect.CongressElected congress) {}

    public PresidentialOutcome presidential(ElectionState e, int year, ElectoralEnvironment env, Congress current,
                                            boolean senateClass2028, DeterministicRandomManager rng) {
        ElectionResult r = calculator.resolve(year, nationalSwing(e, env), s -> localShift(e, s), e.systematicMiss, rng, "election");
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
            double m = s.baselineMargin() + nationalSwing * s.elasticity() + p.senateIncumbency() * holder.get().sign()
                    + p.senateNoise() * rng.gaussian("congress.elections");
            if (m >= 0) wonD++;
        }
        return wonD;
    }

    /** The re-election campaign: a fresh challenger, the incumbent's war chest, a new hidden polling miss. */
    public void beginReelection(ElectionState e, Candidate challenger, double capital, double opponentResources, DeterministicRandomManager rng) {
        e.player = new CampaignSide(e.player.party(), e.player.candidate(), p.reelectionBase() + capital * p.reelectionPerCapital());
        e.opponent = new CampaignSide(challenger.party(), challenger, p.challengerCash() * opponentResources);
        e.campaignSwingForPlayer = 0;
        e.systematicMiss = rng.gaussian("polls.miss") * p.pollMissSd();
    }

    public Candidate pickChallenger(ElectionState e, DeterministicRandomManager rng) {
        var field = candidates.forParty(e.opponent.party()).stream().filter(c -> !c.id().equals(e.opponent.candidate().id())).toList();
        return field.get(rng.nextInt("reelection", field.size()));
    }
}
