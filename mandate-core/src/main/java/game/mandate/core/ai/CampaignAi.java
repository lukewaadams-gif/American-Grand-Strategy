package game.mandate.core.ai;

import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The campaign opponent (and the player's autopilot). It reads the same polls the player sees, never
 * the hidden truth, and targets states by value = electoral votes / (1 + distance from the tipping line).
 * Readable heuristics are deliberate: players should be able to learn and counter the AI.
 */
public final class CampaignAi {
    private CampaignAi() {}

    public static CampaignWeekPlan plan(GameEngine g, CampaignSide side, double resources) {
        Map<String, Double> polls = g.statePolls();
        int sign = side.party().sign();
        List<UsState> targets = new ArrayList<>(g.states().all());
        targets.removeIf(s -> Math.abs(polls.get(s.code())) > 9);
        targets.sort(Comparator.comparingDouble((UsState s) -> value(s, polls.get(s.code()) * sign)).reversed());
        if (targets.isEmpty()) targets.add(g.states().get(side.candidate().homeState()));

        boolean lowCash = side.cash() < 40;
        int fundraisers = lowCash ? 2 : 1;
        List<String> rallies = new ArrayList<>();
        for (int i = 0; i < Math.min(CampaignWeekPlan.STOPS - fundraisers, targets.size()); i++) rallies.add(targets.get(i).code());

        double budget = Math.max(0, side.cash()) * 0.30 * resources;
        Map<String, Double> ads = new java.util.TreeMap<>(), field = new java.util.TreeMap<>();
        List<UsState> top = targets.subList(0, Math.min(8, targets.size()));
        double total = top.stream().mapToDouble(s -> value(s, polls.get(s.code()) * sign)).sum();
        boolean early = g.date().getMonthValue() <= 8;
        for (UsState s : top) {
            double share = value(s, polls.get(s.code()) * sign) / total;
            ads.put(s.code(), budget * share * (early ? 0.55 : 0.85));
            field.put(s.code(), budget * share * (early ? 0.45 : 0.15));
        }
        return new CampaignWeekPlan(rallies, fundraisers, ads, field, bestMessage(g, side));
    }

    /** Value of contesting a state given my margin there (positive = I lead). */
    static double value(UsState s, double myMargin) {
        return s.electoralVotes() / (1.0 + Math.abs(myMargin - 1.0));
    }

    public static Issue bestMessage(GameEngine g, CampaignSide side) {
        Issue best = Issue.ECONOMY;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Issue i : Issue.values()) {
            double score = g.salience().get(i) * i.advantageFor(side.party()) + (i == side.lastMessage() ? 0.05 : 0);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** Usually plays to its strength; sometimes counters the opponent's tendency instead. */
    public static DebatePosture debatePosture(Candidate self, Candidate opponent, DeterministicRandomManager rng) {
        if (rng.chance("ai.debate", 0.35)) {
            DebatePosture theirs = DebatePosture.tendencyOf(opponent);
            for (DebatePosture p : DebatePosture.values()) if (p.counters(theirs)) return p;
        }
        return DebatePosture.tendencyOf(self);
    }
}
