package game.mandate.core.ai;

import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.CampaignStrategy.Orders;
import game.mandate.core.campaign.CampaignStrategy.SpendRate;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.congress.Bill;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The campaign opponent, and the staff that turns the player's standing orders into a weekly plan. It reads the
 * same polls the player sees, never the hidden truth. Readable heuristics are deliberate: the opponent says why it
 * runs the campaign it runs, so players can learn it and counter it.
 */
public final class CampaignAi {
    private CampaignAi() {}

    /** The opponent's standing orders this week, and why. */
    public record Adaptation(Orders orders, String reasoning) {}

    /**
     * The adaptive opponent: it reads the race (its lead in the polls, its cash, the calendar) and the player's
     * campaign (where the player is outspending it), and picks a preset. Targeting inside {@link #plan} then defends
     * the close states the player is contesting hardest.
     */
    public static Adaptation adapt(GameEngine g, CampaignSide side) {
        double lead = g.nationalPoll() * side.party().sign();
        LocalDate d = g.date();
        boolean late = !d.isBefore(LocalDate.of(d.getYear(), Month.SEPTEMBER, 15));
        boolean early = d.getMonth().compareTo(Month.JULY) <= 0;
        String standing = String.format(" (It reads the polls as %s by %.1f.)", lead >= 0 ? "ahead" : "behind", Math.abs(lead));
        if (side.cash() < 30 && !late)
            return new Adaptation(new Orders(CampaignStrategy.BANK_IT, SpendRate.CAREFUL, null),
                    String.format("Short of money ($%.0fM), it is raising for a late push.", side.cash()) + standing);
        if (late) {
            if (lead > 2.5) return new Adaptation(new Orders(CampaignStrategy.BATTLEGROUND, SpendRate.ALL_IN, null),
                    "Ahead in the final stretch, it spends everything defending its leads." + standing);
            if (lead < -2.5) return new Adaptation(new Orders(CampaignStrategy.AIR_WAR, SpendRate.ALL_IN, null),
                    "Behind with weeks to go, it floods the air with ads to change the race." + standing);
            return new Adaptation(new Orders(CampaignStrategy.BATTLEGROUND, SpendRate.ALL_IN, null),
                    "In a close race at the end, it puts everything into the tipping-point states." + standing);
        }
        if (early) {
            if (lead < -2.5) return new Adaptation(new Orders(CampaignStrategy.AIR_WAR, SpendRate.STEADY, null),
                    "Behind early, it buys ads to move the race fast." + standing);
            return new Adaptation(new Orders(CampaignStrategy.GROUND_GAME, SpendRate.STEADY, null),
                    "Early in the race, it is building field offices that pay off in November." + standing);
        }
        if (lead > 2.5) return new Adaptation(new Orders(CampaignStrategy.EXPAND_THE_MAP, SpendRate.STEADY, null),
                "Comfortably ahead, it reaches into states that lean your way." + standing);
        if (lead < -2.5) return new Adaptation(new Orders(CampaignStrategy.AIR_WAR, SpendRate.STEADY, null),
                "Behind, it is buying ads to change the race." + standing);
        return new Adaptation(new Orders(CampaignStrategy.BATTLEGROUND, SpendRate.STEADY, null),
                "In a close race, it concentrates on the battlegrounds." + standing);
    }

    /** One week's plan from standing orders. The same planner serves the player's staff and the opponent. */
    public static CampaignWeekPlan plan(GameEngine g, CampaignSide side, double resources, Orders orders) {
        Map<String, Double> polls = g.statePolls();
        int sign = side.party().sign();
        CampaignSide other = g.election().side(side.party().opponent());
        CampaignStrategy s = orders.strategy();
        List<UsState> targets = new ArrayList<>(g.states().all());
        targets.removeIf(x -> Math.abs(polls.get(x.code()) * sign + s.reach) > 9);
        targets.sort(Comparator.comparingDouble((UsState x) -> target(x, polls.get(x.code()) * sign + s.reach, side, other)).reversed());
        if (targets.isEmpty()) targets.add(g.states().get(side.candidate().homeState()));

        boolean lowCash = side.cash() < 40;
        int rallies = Math.min(s.rallies, lowCash ? 1 : s.rallies);
        int fundraisers = CampaignWeekPlan.STOPS - rallies;
        List<String> stops = new ArrayList<>();
        for (int i = 0; i < Math.min(rallies, targets.size()); i++) stops.add(targets.get(i).code());

        double rate = s == CampaignStrategy.BANK_IT ? Math.min(orders.rate().share, SpendRate.CAREFUL.share) : orders.rate().share;
        double budget = Math.max(0, side.cash()) * rate * resources;
        boolean late = g.date().getMonthValue() >= 9;
        double adShare = s == CampaignStrategy.GROUND_GAME ? s.adShare : Math.min(1.0, s.adShare + (late ? 0.15 : 0));
        Map<String, Double> ads = new TreeMap<>(), field = new TreeMap<>();
        List<UsState> top = targets.subList(0, Math.min(8, targets.size()));
        double total = top.stream().mapToDouble(x -> target(x, polls.get(x.code()) * sign + s.reach, side, other)).sum();
        for (UsState x : top) {
            double share = target(x, polls.get(x.code()) * sign + s.reach, side, other) / total;
            ads.put(x.code(), budget * share * adShare);
            field.put(x.code(), budget * share * (1 - adShare));
        }
        Issue message = orders.message() != null ? orders.message() : bestMessage(g, side);
        return new CampaignWeekPlan(stops, fundraisers, ads, field, message);
    }

    /** The old signature: the default orders. */
    public static CampaignWeekPlan plan(GameEngine g, CampaignSide side, double resources) { return plan(g, side, resources, Orders.DEFAULT); }

    /**
     * Value of contesting a state: electoral votes over distance from a narrow lead, raised by a third where the
     * other side is outspending this one on the air in a close race (it answers the player's offensives).
     */
    static double target(UsState s, double myMargin, CampaignSide me, CampaignSide other) {
        double v = value(s, myMargin);
        if (Math.abs(myMargin) < 4 && other.ads(s.code()) > me.ads(s.code()) * 1.2) v *= 1.33;
        return v;
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

    /** A campaign's promises: its party's two most popular bills (ties by id, so the choice is stable). */
    public static List<String> pledges(GameEngine g, CampaignSide side) {
        List<Bill> mine = new ArrayList<>(g.bills().all().stream().filter(b -> b.sponsor().orElse(null) == side.party()).toList());
        mine.sort(Comparator.comparingDouble((Bill b) -> -b.publicAppeal()).thenComparing(Bill::id));
        return mine.subList(0, Math.min(2, mine.size())).stream().map(Bill::id).toList();
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
