package game.mandate.core.congress;

import game.mandate.core.rng.RngStreams;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloc-level vote model. Each bloc's support is driven by ideological distance, party loyalty,
 * presidential approval, political capital spent, and deficit worries. The foundation resolves a bill
 * in one turn; the full committee / floor / conference path from Resolute Desk arrives in M3.
 */
public final class LegislativeEngine {

    public record Outcome(boolean passed, int houseYes, int senateYes, String explanation) {}

    public WhipCount whip(Bill bill, LegislativeStrategy strategy, Congress congress, Party president,
                          double approval, double capitalCommitted) {
        double ideology = effectiveIdeology(bill, strategy);
        List<WhipCount.BlocRead> house = new ArrayList<>(), senate = new ArrayList<>();
        for (Party p : Party.values()) {
            for (Bloc b : Bloc.defaultsFor(p)) {
                double support = support(b, bill, strategy, ideology, president, approval, capitalCommitted);
                int hm = (int) Math.round(congress.house(p) * b.share());
                int sm = (int) Math.round(congress.senate(p) * b.share());
                house.add(new WhipCount.BlocRead(b.name(), hm, hm * support));
                senate.add(new WhipCount.BlocRead(b.name(), sm, sm * support));
            }
        }
        int senateNeeded = senateThreshold(bill, strategy, congress);
        return new WhipCount(house, senate, Congress.HOUSE_MAJORITY, senateNeeded);
    }

    /** Senate votes needed (out of 100; the VP breaks a 50-50 tie for the president). */
    public int senateThreshold(Bill bill, LegislativeStrategy strategy, Congress congress) {
        if (strategy == LegislativeStrategy.RECONCILIATION || !congress.filibuster()) return 50;
        return Congress.CLOTURE;
    }

    public Outcome vote(Bill bill, LegislativeStrategy strategy, Congress congress, Party president, double approval,
                        double capitalCommitted, RngStreams rng) {
        WhipCount w = whip(bill, strategy, congress, president, approval, capitalCommitted);
        int houseYes = (int) Math.round(Math.max(0, Math.min(435, w.houseExpected() + 4.0 * rng.gaussian("congress"))));
        int senateYes = (int) Math.round(Math.max(0, Math.min(100, w.senateExpected() + 1.6 * rng.gaussian("congress"))));
        boolean houseOk = houseYes >= w.houseNeeded();
        boolean senateOk = senateYes >= w.senateNeeded();
        String why;
        if (houseOk && senateOk) why = "Passed the House " + houseYes + "-" + (435 - houseYes) + " and the Senate " + senateYes + "-" + (100 - senateYes) + ".";
        else if (!houseOk) why = "Failed in the House, " + houseYes + " votes of " + w.houseNeeded() + " needed.";
        else why = "Stalled in the Senate with " + senateYes + " votes; " + w.senateNeeded() + " needed.";
        return new Outcome(houseOk && senateOk, houseYes, senateYes, why);
    }

    static double effectiveIdeology(Bill bill, LegislativeStrategy s) {
        return s == LegislativeStrategy.BIPARTISAN_DEAL ? bill.ideology() * 0.45 : bill.ideology();
    }

    static double support(Bloc b, Bill bill, LegislativeStrategy strategy, double ideology, Party president,
                          double approval, double capital) {
        double base = 1.0 - 1.15 * Math.abs(ideology - b.ideology());
        boolean presidentsParty = b.party() == president;
        double party;
        if (presidentsParty) party = 0.40 * b.loyalty() + (approval - 45) * 0.006;
        else if (bill.bipartisan() || strategy == LegislativeStrategy.BIPARTISAN_DEAL) party = -0.10 + (approval - 50) * 0.004;
        else party = -0.45 * b.loyalty() + (approval - 50) * 0.003;
        double hawk = b.deficitHawk() && bill.deficitPctGdp() > 0.05 ? -0.25 * Math.min(1, bill.deficitPctGdp() / 0.4) : 0;
        // Capital buys wavering members; it matters most to blocs that are near the edge.
        double edge = 1.0 - Math.abs(base + party - 0.5) * 2;
        double bought = Math.max(0, edge) * capital * 0.006;
        return Math.max(0.0, Math.min(1.0, base + party + hawk + bought));
    }
}
