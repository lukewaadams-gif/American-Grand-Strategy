package game.mandate.core.congress;

import game.mandate.core.data.Tuning;
import game.mandate.core.effects.Effect;
import game.mandate.core.politics.Coalition;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The single authority for legislative processes: whip counts, floor votes, Senate thresholds, reconciliation,
 * executive orders in court, and what a law does to the rest of the world (returned as transitions for the
 * owning engines to apply, never applied here).
 */
public final class CongressEngine {
    public record Outcome(boolean passed, int houseYes, int senateYes, String explanation) {}

    private final VoteModel votes;
    private final double agendaCapital, courtBlockChance, passCapital, failCapital, failApproval, lastingShare, lawSalience;
    private final double goodwillDeal, goodwillPartyLine, goodwillReconciliation, goodwillOrder;
    private final double moodDealBase, moodDealModerate, moodPartyLineBase, moodOrderBase, moodOtherAgenda;
    private final int economicMonths;

    public CongressEngine(Tuning t) {
        this.votes = new VoteModel(VoteParams.from(t));
        agendaCapital = t.d("congress.agendaCapital");
        courtBlockChance = t.d("congress.courtBlockChance");
        passCapital = t.d("congress.passCapital");
        failCapital = t.d("congress.failCapital");
        failApproval = t.d("congress.failApproval");
        lastingShare = t.d("congress.lastingShare");
        lawSalience = t.d("congress.lawSalience");
        goodwillDeal = t.d("congress.goodwill.deal");
        goodwillPartyLine = t.d("congress.goodwill.partyLine");
        goodwillReconciliation = t.d("congress.goodwill.reconciliation");
        goodwillOrder = t.d("congress.goodwill.order");
        moodDealBase = t.d("congress.mood.dealBase");
        moodDealModerate = t.d("congress.mood.dealModerate");
        moodPartyLineBase = t.d("congress.mood.partyLineBase");
        moodOrderBase = t.d("congress.mood.orderBase");
        moodOtherAgenda = t.d("congress.mood.otherAgendaBase");
        economicMonths = t.i("congress.economicMonths");
    }

    public VoteModel votes() { return votes; }

    public CongressState newCongress(int houseDem, int senateDem) { return new CongressState(new Congress(houseDem, senateDem)); }

    public PolicyState newPolicy() { return new PolicyState(); }

    // ---- transitions --------------------------------------------------------------------------

    public boolean apply(CongressState s, Effect e) {
        if (e instanceof Effect.CongressElected x) {
            s.congress.setHouse(Party.DEMOCRAT, x.houseDem());
            s.congress.setSenate(Party.DEMOCRAT, x.senateDem());
            return true;
        }
        return false;
    }

    // ---- the whip and the vote ----------------------------------------------------------------

    public int senateNeeded(CongressState s, LegislativeStrategy strategy) {
        return strategy == LegislativeStrategy.RECONCILIATION || !s.congress.filibuster() ? 50 : Congress.CLOTURE;
    }

    public WhipCount whip(CongressState s, Bill bill, LegislativeStrategy strategy, LegislativeContext ctx, double capitalCommitted) {
        double ideology = votes.effectiveIdeology(bill.ideology(), strategy);
        boolean bipartisan = bill.bipartisan() || strategy == LegislativeStrategy.BIPARTISAN_DEAL;
        double capital = capitalCommitted + (ctx.agendaItem() ? agendaCapital : 0);
        List<WhipCount.BlocRead> house = new ArrayList<>(), senate = new ArrayList<>();
        for (Party side : Party.values()) {
            for (Bloc b : Bloc.defaultsFor(side)) {
                int w = b.wing();
                double mood = w < 0 ? (ctx.wingMood()[0] + ctx.wingMood()[1]) / 2 : ctx.wingMood()[w];
                double support = votes.support(b, new VoteModel.BillInputs(ctx.president(), ideology, bill.deficitPctGdp(), bipartisan,
                        ctx.opposition(), ctx.approval(), ctx.swingApproval(), 0, capital, mood, s.relationship(b.name()), 0,
                        !bill.availableTo(ctx.president())));
                int hm = (int) Math.round(s.congress.house(side) * b.share());
                int sm = (int) Math.round(s.congress.senate(side) * b.share());
                house.add(new WhipCount.BlocRead(b.name(), hm, hm * support));
                senate.add(new WhipCount.BlocRead(b.name(), sm, sm * support));
            }
        }
        int needed = senateNeeded(s, strategy);
        double he = house.stream().mapToDouble(WhipCount.BlocRead::expectedYes).sum(), se = senate.stream().mapToDouble(WhipCount.BlocRead::expectedYes).sum();
        double chance = votes.passChance(he, Congress.HOUSE_MAJORITY, true) * votes.passChance(se, needed, false);
        return new WhipCount(house, senate, Congress.HOUSE_MAJORITY, needed, chance);
    }

    /** Validation shared by the engine and the UI: why this bill cannot be pushed this way, or null if it can. */
    public String refusal(CongressState s, PolicyState policy, Bill b, LegislativeStrategy strategy, Party president, LocalDate date) {
        if (policy.inForce(b.id())) return b.name() + " is already law";
        if (!b.availableTo(president) && strategy != LegislativeStrategy.BIPARTISAN_DEAL)
            return "You can only take up the other party's bill as a bipartisan deal";
        if (strategy == LegislativeStrategy.RECONCILIATION) {
            if (b.requires60()) return "The Byrd rule keeps this bill out of reconciliation";
            if (!s.congress.unified(president)) return "Reconciliation needs your party in control of both chambers";
            if (s.reconciliationYears.contains(date.getYear())) return "Reconciliation is already used this year";
        }
        return null;
    }

    public record Passage(boolean enacted, String explanation, List<Effect> consequences) {}

    /**
     * Runs the legislative process for one bill and records it. Returns what happened and the transitions the
     * rest of the world must apply (approval, economy, deficit, salience, capital, goodwill, wing moods).
     */
    public Passage push(CongressState s, PolicyState policy, Bill b, LegislativeStrategy strategy, LegislativeContext ctx,
                        double capitalCommitted, LocalDate date, DeterministicRandomManager rng) {
        if (strategy == LegislativeStrategy.RECONCILIATION) s.reconciliationYears.add(date.getYear());
        List<Effect> out = new ArrayList<>();
        if (strategy == LegislativeStrategy.EXECUTIVE_ORDER) {
            out.add(new Effect.Goodwill(goodwillOrder));
            if (rng.chance("courts", courtBlockChance)) {
                policy.ordersBlocked++;
                out.add(new Effect.Approval(-1));
                return new Passage(false, "A federal judge blocks your executive order on " + b.issue().label().toLowerCase() + ".", out);
            }
            out.addAll(enact(policy, b, strategy, ctx, date));
            out.add(new Effect.WingMood(0, moodOrderBase));
            return new Passage(true, "Executive order signed.", out);
        }
        WhipCount w = whip(s, b, strategy, ctx, capitalCommitted);
        int houseYes = (int) Math.round(Math.max(0, Math.min(435, w.houseExpected() + votes.noise(true) * rng.gaussian("congress"))));
        int senateYes = (int) Math.round(Math.max(0, Math.min(100, w.senateExpected() + votes.noise(false) * rng.gaussian("congress"))));
        boolean houseOk = houseYes >= w.houseNeeded(), senateOk = senateYes >= w.senateNeeded();
        String why;
        if (houseOk && senateOk) why = "Passed the House " + houseYes + "-" + (435 - houseYes) + " and the Senate " + senateYes + "-" + (100 - senateYes) + ".";
        else if (!houseOk) why = "Failed in the House, " + houseYes + " votes of " + w.houseNeeded() + " needed.";
        else why = "Stalled in the Senate with " + senateYes + " votes; " + w.senateNeeded() + " needed.";
        switch (strategy) {
            case BIPARTISAN_DEAL -> { out.add(new Effect.Goodwill(goodwillDeal)); out.add(new Effect.WingMood(0, moodDealBase)); out.add(new Effect.WingMood(1, moodDealModerate)); }
            case RECONCILIATION -> out.add(new Effect.Goodwill(goodwillReconciliation));
            default -> out.add(new Effect.Goodwill(goodwillPartyLine));
        }
        if (houseOk && senateOk) {
            out.addAll(enact(policy, b, strategy, ctx, date));
            if (!b.availableTo(ctx.president())) out.add(new Effect.WingMood(0, moodOtherAgenda));
            out.add(new Effect.Capital(passCapital));
            if (strategy == LegislativeStrategy.PARTY_LINE || strategy == LegislativeStrategy.RECONCILIATION) out.add(new Effect.WingMood(0, moodPartyLineBase));
            return new Passage(true, why, out);
        }
        policy.billsFailed++;
        out.add(new Effect.Capital(-failCapital));
        out.add(new Effect.Approval(-failApproval));
        return new Passage(false, why, out);
    }

    private List<Effect> enact(PolicyState policy, Bill b, LegislativeStrategy s, LegislativeContext ctx, LocalDate date) {
        double k = s.payoff();
        policy.enacted.add(new PolicyState.Enacted(b.id(), date, s, k));
        List<Effect> out = new ArrayList<>();
        double appeal = b.publicAppeal() * k + (ctx.agendaItem() ? 1.0 : 0);
        out.add(new Effect.Approval(appeal));
        out.add(new Effect.CoalitionApproval(Coalition.SWING, lastingShare * appeal, true));
        out.add(new Effect.EconomyShock(b.growthImpulse() * k, b.inflationImpulse() * k, economicMonths));
        out.add(new Effect.Deficit(b.deficitPctGdp() * k));
        out.add(new Effect.Salience(b.issue(), lawSalience));
        return out;
    }
}
