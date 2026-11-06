package game.mandate.core.campaign;

import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.rng.RngStreams;

import java.util.EnumMap;
import java.util.Map;

/**
 * Weekly campaign resolution. All effects have diminishing returns and are relative to the opponent:
 * outspending a rival 2:1 in Pennsylvania is worth much less than the first ten million, and money in
 * a state the opponent ignores goes further.
 */
public final class CampaignEngine {
    /** Weekly small-dollar and party money before fundraisers, $M, for an average fundraiser. */
    public static final double BASE_WEEKLY_INCOME = 28.0;
    public static final double FUNDRAISER_YIELD = 9.0;

    private final StateCatalog states;

    public CampaignEngine(StateCatalog states) { this.states = states; }

    /** Apply one side's plan. Spending beyond available cash is scaled down, never refused. */
    public void execute(CampaignSide side, CampaignWeekPlan plan, Map<Issue, Double> salience) {
        side.decay();
        double income = BASE_WEEKLY_INCOME * (0.7 + 0.06 * side.candidate().fundraising())
                + plan.fundraisers() * FUNDRAISER_YIELD * (0.6 + 0.08 * side.candidate().fundraising())
                + Math.max(0, side.momentum()) * 2.0;
        side.addCash(income);

        double spend = plan.totalSpend();
        double scale = spend <= side.cash() || spend == 0 ? 1.0 : side.cash() / spend;
        plan.adSpend().forEach((s, m) -> { requireState(s); side.addAds(s, m * scale); });
        plan.fieldSpend().forEach((s, m) -> { requireState(s); side.addField(s, m * scale); });
        side.addCash(-spend * scale);
        for (String s : plan.rallyStates()) { requireState(s); side.addVisit(s); }

        side.setMessage(plan.message());
        double consistency = Math.min(1.5, 1.0 + 0.1 * (side.weeksOnMessage() - 1));
        // Salience is used as a share of total attention, so inflating one issue crowds out the others.
        double total = salience.values().stream().mapToDouble(Double::doubleValue).sum();
        double sal = salience.getOrDefault(plan.message(), plan.message().baseSalience()) / Math.max(1e-9, total);
        side.addMomentum(0.35 * sal * plan.message().advantageFor(side.party()) * consistency
                + 0.04 * (side.candidate().charisma() - 5.5));
    }

    /** Local shift in a state, D minus R, from both campaigns' accumulated investment. */
    public double localShift(String stateCode, CampaignSide dem, CampaignSide rep) {
        UsState s = states.get(stateCode);
        double ref = 0.6 * s.voteWeight();
        double d = effort(dem, s, ref), r = effort(rep, s, ref);
        double home = (dem.candidate().homeState().equals(stateCode) ? 2.0 : 0) - (rep.candidate().homeState().equals(stateCode) ? 2.0 : 0);
        return d - r + home;
    }

    private static double effort(CampaignSide side, UsState s, double ref) {
        double ads = 1.3 * Math.log1p(side.ads(s.code()) / ref);
        double field = 1.0 * Math.log1p(side.field(s.code()) / ref);
        double visits = 0.45 * Math.log1p(side.visits(s.code())) * (0.6 + 0.08 * side.candidate().charisma());
        return ads + field + visits;
    }

    /** National effect of the two campaigns' momentum, D minus R. */
    public double momentumSwing(CampaignSide dem, CampaignSide rep) { return dem.momentum() - rep.momentum(); }

    /**
     * Resolve one debate. Returns the national swing toward Democrats.
     * Score = posture rating + counter bonus + volatility-scaled luck.
     */
    public double debate(CampaignSide dem, DebatePosture dp, CampaignSide rep, DebatePosture rp, RngStreams rng) {
        double ds = score(dem.candidate(), dp, rp, rng), rs = score(rep.candidate(), rp, dp, rng);
        return Math.max(-3.0, Math.min(3.0, 0.45 * (ds - rs)));
    }

    private static double score(Candidate c, DebatePosture mine, DebatePosture theirs, RngStreams rng) {
        return 0.4 * mine.rating(c) + (mine.counters(theirs) ? 1.6 : 0) + mine.volatility() * rng.gaussian("debate");
    }

    public static Map<Issue, Double> baseSalience() {
        Map<Issue, Double> m = new EnumMap<>(Issue.class);
        for (Issue i : Issue.values()) m.put(i, i.baseSalience());
        return m;
    }

    private void requireState(String code) {
        if (!states.contains(code)) throw new IllegalArgumentException("Unknown state " + code);
    }
}
