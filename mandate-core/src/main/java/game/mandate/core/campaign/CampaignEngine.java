package game.mandate.core.campaign;

import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.EnumMap;
import java.util.Map;

/**
 * Weekly campaign resolution. All effects have diminishing returns and are relative to the opponent:
 * outspending a rival 2:1 in Pennsylvania is worth much less than the first ten million, and money in
 * a state the opponent ignores goes further. Part of the election engine's domain.
 */
public final class CampaignEngine {
    private final StateCatalog states;
    private final CampaignParams p;

    public CampaignEngine(StateCatalog states, CampaignParams p) {
        this.states = states;
        this.p = p;
    }

    /** Apply one side's plan. Spending beyond available cash is scaled down, never refused. */
    public void execute(CampaignSide side, CampaignWeekPlan plan, Map<Issue, Double> salience) {
        side.decay(p);
        double income = p.baseWeeklyIncome() * (p.incomeFloor() + p.incomePerFundraising() * side.candidate().fundraising())
                + plan.fundraisers() * p.fundraiserYield() * (p.fundraiserFloor() + p.fundraiserPerRating() * side.candidate().fundraising())
                + Math.max(0, side.momentum()) * p.momentumIncome();
        side.cash += income;

        double spend = plan.totalSpend();
        double scale = spend <= side.cash || spend == 0 ? 1.0 : side.cash / spend;
        plan.adSpend().forEach((s, m) -> { requireState(s); side.adPressure.merge(s, m * scale, Double::sum); });
        plan.fieldSpend().forEach((s, m) -> { requireState(s); side.field.merge(s, m * scale, Double::sum); });
        side.cash -= spend * scale;
        for (String s : plan.rallyStates()) { requireState(s); side.visits.merge(s, 1.0, Double::sum); }

        side.setMessage(plan.message());
        double consistency = Math.min(p.consistencyCap(), 1.0 + p.consistencyStep() * (side.weeksOnMessage() - 1));
        // Salience is used as a share of total attention, so inflating one issue crowds out the others.
        double total = salience.values().stream().mapToDouble(Double::doubleValue).sum();
        double sal = salience.getOrDefault(plan.message(), plan.message().baseSalience()) / Math.max(1e-9, total);
        side.momentum += p.messageWeight() * sal * plan.message().advantageFor(side.party()) * consistency
                + p.charismaMomentum() * (side.candidate().charisma() - 5.5);
    }

    /** Local shift in a state, D minus R, from both campaigns' accumulated investment. */
    public double localShift(String stateCode, CampaignSide dem, CampaignSide rep) {
        UsState s = states.get(stateCode);
        double ref = p.refPerSeat() * s.voteWeight();
        double d = effort(dem, s, ref), r = effort(rep, s, ref);
        double home = (dem.candidate().homeState().equals(stateCode) ? p.homeState() : 0) - (rep.candidate().homeState().equals(stateCode) ? p.homeState() : 0);
        return d - r + home;
    }

    private double effort(CampaignSide side, UsState s, double ref) {
        double ads = p.adWeight() * Math.log1p(side.ads(s.code()) / ref);
        double field = p.fieldWeight() * Math.log1p(side.field(s.code()) / ref);
        double visits = p.visitWeight() * Math.log1p(side.visits(s.code())) * (p.visitCharismaFloor() + p.visitCharisma() * side.candidate().charisma());
        return ads + field + visits;
    }

    /** National effect of the two campaigns' momentum, D minus R. */
    public double momentumSwing(CampaignSide dem, CampaignSide rep) { return dem.momentum() - rep.momentum(); }

    /** Resolve one debate. Returns the national swing toward Democrats. */
    public double debate(CampaignSide dem, DebatePosture dp, CampaignSide rep, DebatePosture rp, DeterministicRandomManager rng) {
        double ds = score(dem.candidate(), dp, rp, rng), rs = score(rep.candidate(), rp, dp, rng);
        return Math.max(-p.debateCap(), Math.min(p.debateCap(), p.debateWeight() * (ds - rs)));
    }

    private double score(Candidate c, DebatePosture mine, DebatePosture theirs, DeterministicRandomManager rng) {
        return p.debateRating() * mine.rating(c) + (mine.counters(theirs) ? p.debateCounter() : 0) + mine.volatility() * rng.gaussian("debate");
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
