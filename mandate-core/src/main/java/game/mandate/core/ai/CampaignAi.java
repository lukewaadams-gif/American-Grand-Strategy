package game.mandate.core.ai;

import game.mandate.core.data.Fmt;
import game.mandate.core.campaign.Activity;
import game.mandate.core.campaign.AirWar;
import game.mandate.core.campaign.CampaignEngine;
import game.mandate.core.campaign.CampaignGeography;
import game.mandate.core.campaign.CampaignOrders;
import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.Show;
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
        String standing = Fmt.format(" (It reads the polls as %s by %.1f.)", lead >= 0 ? "ahead" : "behind", Math.abs(lead));
        if (side.cash() < 30 && !late)
            return new Adaptation(new Orders(CampaignStrategy.BANK_IT, SpendRate.CAREFUL, null),
                    Fmt.format("Short of money ($%.0fM), it is raising for a late push.", side.cash()) + standing);
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

    // =================================================================================================================
    //  Battleground 2028's planner: the schedule (AI.schedule), the budget (AI.budget) and the digital plan (Digital.aiPlan),
    //  Battleground2028.java l.5992-6114 and l.6957. The same planner serves the opponent and the player's staff; the
    //  standing orders (the brief) steer it. It reads the public polls, never the hidden truth (BG's reads the truth).
    // =================================================================================================================

    /** Spending pace by brief: BG's "a sustainable share of cash" times careful, steady or all-in. */
    static double rateMultiplier(SpendRate r) { return r == SpendRate.CAREFUL ? 0.6 : r == SpendRate.ALL_IN ? 1.8 : 1.0; }

    /** TV, digital and field shares of the weekly budget by brief (BG's general-election split is 55/20/20). */
    static double[] split(CampaignStrategy s) {
        return switch (s) {
            case GROUND_GAME -> new double[]{0.30, 0.20, 0.45};
            case AIR_WAR -> new double[]{0.65, 0.30, 0.05};
            default -> new double[]{0.55, 0.20, 0.20};
        };
    }

    /**
     * Next week's orders for a campaign: its standing orders with the chosen areas rebuilt. The schedule visits the six
     * most valuable states, a day each; the budget puts rating points where value per dollar is highest, digital and
     * organisers in proportion to value, and offices to hold the organisers.
     */
    public static CampaignOrders planOrders(GameEngine g, CampaignSide side, Orders brief, boolean schedule, boolean ads, boolean field) {
        CampaignEngine ce = g.campaignEngine();
        CampaignGeography geo = ce.geography();
        CampaignEngine.Turn t = g.campaignTurn();
        DeterministicRandomManager rng = g.rngForPlanning();
        CampaignOrders o = side.orders();
        int sign = side.party().sign();
        double reach = brief.strategy().reach;
        double[] v = ce.stateValue(t.polls(), t.nationalPoll(), sign, reach);
        if (schedule) schedule(g, side, brief, o, v, rng);
        if (ads || field) budget(g, side, brief, o, v, ads, field);
        o.message(brief.message() != null ? brief.message() : bestMessage(g, side));
        double burn = 4.2;
        o.hq(side.cash() < burn * 3 ? 0 : side.cash() > burn * 40 ? 2 : 1);
        return o;
    }

    /** BG AI.schedule (l.5992). */
    static void schedule(GameEngine g, CampaignSide side, Orders brief, CampaignOrders o, double[] v, DeterministicRandomManager rng) {
        CampaignGeography geo = g.campaignEngine().geography();
        int[] top = topStates(v, 6);
        o.clearSchedule(0);
        CampaignStrategy s = brief.strategy();
        boolean poor = side.cash() < CampaignEngine.hqWeek(side) * 4;
        int prep = debateSoon(g) ? 6 : 0;
        int money = poor || s == CampaignStrategy.BANK_IT ? 7 : 3;
        int rest = side.stamina() < 40 ? 4 : side.stamina() < 60 ? 2 : 1;
        int media = 2;
        boolean[] booked = new boolean[Show.ALL.size()];
        int home = geo.contains(side.candidate().homeState()) ? geo.index(side.candidate().homeState()) : 0;
        int ti = 0;
        for (int day = 0; day < CampaignOrders.DAYS; day++) {
            int st = top.length == 0 ? home : top[ti % top.length];
            if (v[st] <= 0) st = home;
            ti += rng.uniform("campaign.ai") < 0.6 ? 1 : 0;
            for (int s3 = 0; s3 < CampaignOrders.PER_DAY; s3++) {
                int slot = day * 3 + s3;
                Activity a;
                if (prep > 0 && s3 == 0) { a = Activity.PREP; prep--; }
                else if (rest > 0 && (day == 6 || day == 3) && s3 == 0) { a = Activity.REST; rest--; }
                else if (money > 0 && s3 == 2) { a = rng.uniform("campaign.ai") < 0.5 ? Activity.CALL_TIME : Activity.FUNDRAISER; money--; }
                else if (media > 0 && s3 == 1 && day % 3 == 1) {
                    a = rng.uniform("campaign.ai") < 0.5 + (side.ratings().pod() - 60) / 100.0 ? Activity.PODCAST : Activity.TV;
                    media--;
                }
                else a = pickEvent(s, s3, rng);
                int where = a == Activity.FUNDRAISER ? donorHub(geo, st, rng) : st;
                int param = a == Activity.PODCAST ? pickShow(g, side, booked, rng) : rng.nextInt("campaign.ai", 5);
                o.slot(slot, a, where, param);
            }
        }
    }

    /** BG AI.pickEvent (l.6031), general election; the ground-game brief leans on union halls, communities and campuses. */
    static Activity pickEvent(CampaignStrategy s, int s3, DeterministicRandomManager rng) {
        double r = rng.uniform("campaign.ai");
        if (s3 == 2) return r < 0.6 ? Activity.RALLY : Activity.TOWN_HALL;
        if (s == CampaignStrategy.GROUND_GAME) {
            if (r < 0.25) return Activity.RALLY;
            if (r < 0.40) return Activity.TOWN_HALL;
            if (r < 0.55) return Activity.UNION;
            if (r < 0.70) return Activity.COMMUNITY;
            if (r < 0.78) return Activity.CHURCH;
            if (r < 0.92) return Activity.CAMPUS;
            return Activity.RETAIL;
        }
        if (r < 0.35) return Activity.RALLY;
        if (r < 0.55) return Activity.TOWN_HALL;
        if (r < 0.65) return Activity.UNION;
        if (r < 0.75) return Activity.COMMUNITY;
        if (r < 0.82) return Activity.CHURCH;
        if (r < 0.90) return Activity.CAMPUS;
        return Activity.RETAIL;
    }

    private static final String[] HUBS = {"NY", "CA", "TX", "FL", "IL", "MA", "DC", "CT", "WA", "GA"};

    /** BG AI.donorHub: a fundraiser where the money is. */
    static int donorHub(CampaignGeography geo, int near, DeterministicRandomManager rng) {
        if (geo.state(near).donorHub()) return near;
        return geo.index(HUBS[rng.nextInt("campaign.ai", HUBS.length)]);
    }

    /** BG Podcasts.aiPick (l.7055): the biggest show that will book the candidate and fits, with some randomness. */
    static int pickShow(GameEngine g, CampaignSide side, boolean[] booked, DeterministicRandomManager rng) {
        int best = Show.LOCAL;
        double bv = -1e9;
        for (int i = 0; i < Show.ALL.size(); i++) {
            if (i == Show.LOCAL || booked[i] || g.campaignEngine().showRefusal(side, i, g.date()) != null) continue;
            Show s = Show.ALL.get(i);
            double fit = -Math.abs(s.lean() + side.party().sign() * 0.4) * 0.8;   // BG: lean - sign(k) * 0.4, BG's sign -1 for the left
            double val = Math.log(s.views()) + fit + rng.uniform("campaign.ai") * 0.8;
            if (val > bv) { bv = val; best = i; }
        }
        if (best != Show.LOCAL) booked[best] = true;
        return best;
    }

    static boolean debateSoon(GameEngine g) {
        LocalDate d0 = g.date();
        for (LocalDate d : g.campaignDebates()) if (!d.isBefore(d0) && d.isBefore(d0.plusWeeks(2))) return true;
        return false;
    }

    static int[] topStates(double[] v, int n) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> v[b] != v[a] ? Double.compare(v[b], v[a]) : Integer.compare(a, b));
        int[] out = new int[Math.min(n, v.length)];
        for (int i = 0; i < out.length; i++) out[i] = idx[i];
        return out;
    }

    /** BG AI.budget (l.6051) and Digital.aiPlan (l.6957). */
    static void budget(GameEngine g, CampaignSide side, Orders brief, CampaignOrders o, double[] v, boolean ads, boolean field) {
        CampaignEngine ce = g.campaignEngine();
        CampaignGeography geo = ce.geography();
        CampaignEngine.Turn t = g.campaignTurn();
        int left = Math.max(1, t.weeksToVote());
        double reserve = CampaignEngine.hqWeek(side) * 3;
        double budget = Math.max(0, side.cash() - reserve) / left * 1.25 * rateMultiplier(brief.rate())
                * (brief.strategy() == CampaignStrategy.BANK_IT ? 0.5 : 1);
        double vt = 0;
        for (double x : v) vt += x;
        if (ads) { o.clearAirtime(); o.clearDigital(); }
        if (vt <= 0 || budget < 0.05) return;
        double[] sp = split(brief.strategy());
        double tvB = budget * sp[0], digB = budget * sp[1], fieldB = budget * sp[2];
        double aggression = brief.strategy() == CampaignStrategy.AIR_WAR ? 0.7 : 0.5;
        if (ads) {
            double[] mv = new double[geo.marketCount()];
            double mt = 0;
            for (int m = 0; m < mv.length; m++) {
                CampaignGeography.Market mk = geo.market(m);
                double val = 0;
                for (int i = 0; i < mk.states().length; i++) val += v[mk.states()[i]] / vt * mk.shares()[i];
                double cost = ce.costPerPoint(m, t) * 1e6;
                mv[m] = val > 0.002 ? Math.pow(val, 1.4) / Math.pow(cost, 0.3) : 0;
                mt += mv[m];
            }
            AirWar.Tone tone = aggression > 0.6 ? AirWar.Tone.NEGATIVE : aggression > 0.4 ? AirWar.Tone.CONTRAST : AirWar.Tone.POSITIVE;
            if (mt > 0) for (int m = 0; m < mv.length; m++) {
                if (mv[m] <= 0) continue;
                double grp = tvB * mv[m] / mt / ce.costPerPoint(m, t);
                if (grp < 60) continue;
                o.tv(m, Math.min(1500, grp), tone);
            }
            for (int s = 0; s < geo.size(); s++) {
                double d = v[s] > 0 ? digB * v[s] / vt : 0;
                o.digital(s, d < 0.005 ? 0 : d);
            }
            digitalPlan(side, o, t, aggression);
            o.national(AirWar.Network.CABLE, t.bgWeek() >= 98 && side.ratings().big() > 70 ? 120 : 0);
            for (int s = 0; s < geo.size(); s++) o.redBox(s, t.bgWeek() >= 98 && v[s] > vt * 0.08);
        }
        if (field) {
            double maxTot = 2200 + 1200 * side.ratings().vol() / 100;
            double[] target = new double[geo.size()];
            double tsum = 0;
            for (int s = 0; s < geo.size(); s++) {
                double perOrg = ce.organizerWeek(s) + ce.officeWeek(s) / 7;
                target[s] = v[s] > 0 ? fieldB * v[s] / vt / perOrg : 0;
                target[s] = Math.min(target[s], geo.state(s).adults() / 18000.0);
                if (target[s] < 3) target[s] = 0;
                tsum += target[s];
            }
            double scale = tsum > maxTot ? maxTot / tsum : 1;
            for (int s = 0; s < geo.size(); s++) {
                String code = geo.code(s);
                double tg = target[s] * scale, cur = side.organizers(code), offices = side.offices(code);
                int org = 0, off = 0;
                if (tg > cur + 2) org = (int) Math.min(40, Math.round((tg - cur) * 0.5));
                else if (cur > tg * 1.3 + 2) org = -(int) Math.ceil((cur - tg) * 0.4);
                double after = cur + org;
                double offTarget = Math.ceil(after / 7.0);
                if (offTarget > offices) off = (int) Math.min(6, offTarget - offices);
                else if (offices > Math.ceil(after / 5.0) + 1) off = -(int) Math.max(1, (offices - Math.ceil(after / 5.0)) / 2);
                o.field(s, off, org);
            }
        }
    }

    /** BG Digital.aiPlan: a platform mix by audience and one message for the stage of the race. */
    static void digitalPlan(CampaignSide side, CampaignOrders o, CampaignEngine.Turn t, double aggression) {
        boolean young = Math.abs(side.candidate().ideology()) > 0.5 || side.ratings().vir() > 80;
        o.digitalMix(young ? new double[]{0.18, 0.20, 0.26, 0.18, 0.10, 0.08} : new double[]{0.32, 0.14, 0.22, 0.06, 0.18, 0.08});
        int left = t.weeksToVote();
        AirWar.DigitalMessage main = left <= 3 ? AirWar.DigitalMessage.GOTV : left <= 9 && aggression > 0.45 ? AirWar.DigitalMessage.ATTACK : AirWar.DigitalMessage.ISSUE;
        for (AirWar.Platform p : AirWar.Platform.values()) o.digitalMessage(p, main);
        o.digitalMessage(AirWar.Platform.SEARCH, left <= 3 ? AirWar.DigitalMessage.GOTV : AirWar.DigitalMessage.FUND);
        if (main == AirWar.DigitalMessage.ATTACK) o.digitalMessage(AirWar.Platform.INSTAGRAM, AirWar.DigitalMessage.BIO);
    }

    /** The opponent's campaign this week in a sentence, from its actual orders: where its money and its candidate are going. */
    public static String describePlan(GameEngine g, CampaignSide side) {
        CampaignGeography geo = g.campaignEngine().geography();
        CampaignOrders o = side.orders();
        CampaignEngine.Turn t = g.campaignTurn();
        double[] spend = new double[geo.size()];
        for (int m = 0; m < geo.marketCount(); m++) {
            if (o.tvGrp(m) <= 0) continue;
            CampaignGeography.Market mk = geo.market(m);
            double cost = o.tvGrp(m) * g.campaignEngine().costPerPoint(m, t);
            for (int i = 0; i < mk.states().length; i++) spend[mk.states()[i]] += cost * mk.shares()[i] / Math.max(0.01, mk.sumShare());
        }
        for (int s = 0; s < geo.size(); s++) spend[s] += o.digital(s);
        int[] top = topStates(spend, 3);
        List<String> ads = new ArrayList<>();
        for (int s : top) if (spend[s] > 0.05) ads.add(geo.code(s));
        java.util.LinkedHashSet<String> visits = new java.util.LinkedHashSet<>();
        for (int i = 0; i < CampaignOrders.SLOTS; i++) if (o.activity(i) == Activity.RALLY && o.location(i) >= 0) visits.add(geo.code(o.location(i)));
        StringBuilder b = new StringBuilder();
        if (!ads.isEmpty()) b.append("Its ads are heaviest in ").append(String.join(", ", ads)).append(". ");
        if (!visits.isEmpty()) b.append("Its candidate is rallying in ").append(String.join(", ", visits)).append(".");
        return b.toString().trim();
    }

    /** One week's plan from standing orders, in the original campaign layer's form (the CLI and the first web screen show it as a starting point). */
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
