package game.mandate.core.tutorial;

import game.mandate.core.campaign.PollPoint;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.time.Phase;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tutorial: it teaches by putting a decision in front of the player, not with pages of text. At each decision
 * point it offers at most one lesson, unseen before, about the choice actually on the table, using the game's own
 * numbers, and suggests something to try. It reads the game and never changes it; the front end remembers which
 * lessons were shown.
 */
public final class Coach {
    private Coach() {}

    /**
     * @param id         stable key, so a lesson is shown once
     * @param prompt     one or two sentences about the decision in front of the player
     * @param suggestion something to try now
     */
    public record Lesson(String id, String prompt, String suggestion) {}

    public static Optional<Lesson> next(GameEngine g, Set<String> seen) {
        for (Lesson l : candidates(g)) if (!seen.contains(l.id())) return Optional.of(l);
        return Optional.empty();
    }

    /** Every lesson that applies now and has not been seen, most pressing first (so an interface can show each where it belongs). */
    public static List<Lesson> available(GameEngine g, Set<String> seen) {
        return candidates(g).stream().filter(l -> !seen.contains(l.id())).toList();
    }

    private static List<Lesson> candidates(GameEngine g) {
        java.util.ArrayList<Lesson> out = new java.util.ArrayList<>();
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaign(g, out);
            case TRANSITION -> out.add(new Lesson("transition.agenda",
                    "Pick up to three priorities. The first becomes the flagship bill of your first hundred days; a promise left off costs your base.",
                    g.election().pledges().isEmpty() ? "Lead with the bill you can most plausibly pass." : "Keep your promises on the list, the one you can pass first."));
            case FIRST_100_DAYS -> g.hundredDays().ifPresent(hd -> hundredDays(hd, out));
            default -> { }
        }
        return out;
    }

    private static void campaign(GameEngine g, List<Lesson> out) {
        if (!g.pendingEvents().isEmpty())
            out.add(new Lesson("campaign.event", "Something has happened. Each answer shows what it costs; a gamble can pay off or backfire.",
                    "Read the options, then choose."));
        if (g.debateThisWeek().isPresent())
            out.add(new Lesson("campaign.debate", String.format("A debate this week. A posture that counters your opponent's usual style scores extra; your debate rating is %d of 10.",
                    g.player().candidate().debate()), "Choose a posture before you end the week."));
        if (g.election().pledges().isEmpty() && g.phase() == Phase.GENERAL_CAMPAIGN_2028)
            out.add(new Lesson("campaign.pledge", "Promises win a little support now and become your agenda: your first promise is your flagship bill after the inauguration.",
                    "Promise a popular bill you could pass."));
        out.add(new Lesson("campaign.orders", "Your staff runs the campaign from standing orders. In summer, field offices compound until November; ads fade within weeks.",
                "Set orders: a ground game at a steady rate, then revisit in September."));
        List<PollPoint> polls = g.polls();
        if (polls.size() >= 2) {
            PollPoint last = polls.get(polls.size() - 1);
            out.add(new Lesson("campaign.polls", String.format("The polls have you at %+.1f with %d electoral votes (a %.0f%% chance). The biggest mover: %s. Polls carry a hidden error of a point or two, so trust trends over single weeks.",
                    last.national(), last.electoralVotes(), 100 * last.winChance(), last.movers().get(0).toLowerCase()), "Check the trend before you change course."));
            if (last.national() < -2 && g.date().getMonthValue() >= 9)
                out.add(new Lesson("campaign.behind", String.format("You trail by %.1f. The tipping point is %s: money concentrated in the closest states, or a faster spending rate, are the usual answers. Banking money only helps if you spend it later.",
                        -last.national(), last.tippingPoint()), "Try a battleground blitz, or spend all in for the final weeks."));
        }
        java.time.LocalDate day = g.phase() == Phase.GENERAL_CAMPAIGN_2028 ? g.calendar().election2028() : g.calendar().election2032();
        long weeksLeft = java.time.temporal.ChronoUnit.WEEKS.between(g.date(), day);
        if (weeksLeft <= 5 && g.player().cash() > 60 && g.election().playerOrders().rate() != game.mandate.core.campaign.CampaignStrategy.SpendRate.ALL_IN)
            out.add(new Lesson("campaign.closing", String.format("You have $%.0fM in the bank and %d weeks left. Money left on Election Day buys nothing.", g.player().cash(), weeksLeft),
                    "Change your standing orders to spend all in."));
        if (g.player().cash() < 30)
            out.add(new Lesson("campaign.cash", String.format("You are down to $%.0fM. Spending you cannot cover is scaled down; 'Bank it' adds fundraisers for a later push.", g.player().cash()),
                    "Bank it for two or three weeks."));
    }

    private static void hundredDays(HundredDays hd, List<Lesson> out) {
        for (Decision d : hd.pending()) {
            switch (d.kind()) {
                case CRISIS -> out.add(new Lesson("days.crisis", "Intelligence estimates the other side's resolve. Force deters a weak-willed rival and provokes a determined one; diplomacy depends on your Secretary of State.",
                        "Compare the estimate with each option's odds."));
                case ECONOMY -> out.add(new Lesson("days.economy", "Every answer trades something: prices, jobs, the deficit, or one of your party's wings. The report will judge the misery index and what you borrowed.",
                        "Pick the trade you can defend at day 100."));
                case FUNDING -> out.add(new Lesson("days.funding", "The odds of each stopgap depend on the opposition's mood on the deadline, not today's.",
                        "Look at the opposition's strategy before you choose."));
                default -> { }
            }
        }
        if (hd.week() == 1)
            out.add(new Lesson("days.week1", String.format("You have %d attention points a week. A nominee needs hearings and a Senate vote (two weeks), and the flagship needs drafting: its size and pay-for set its odds.",
                    hd.attention()), "Nominate a Treasury secretary and draft the " + hd.billName() + "."));
        if (hd.stage() == BillStage.HOUSE_FLOOR || hd.stage() == BillStage.SENATE_FLOOR)
            out.add(new Lesson("days.whip", "The whip count says " + game.mandate.core.advisor.Advisor.pct(hd.passChance(hd.stage()))
                    + " to pass. Blocs near the middle are where a concession buys votes; the leader will not call a vote below 35% unless you insist.", "Negotiate with the largest persuadable bloc, then call the vote."));
        if (hd.week() >= 3 && hd.delegated().isEmpty())
            out.add(new Lesson("days.delegate", "You can hand a domain to the chief of staff: no attention spent, but the staff is cautious and works at half strength.",
                    "Delegate the budget if the bill needs all your attention."));
        if (hd.week() == hd.weeks() - 1)
            out.add(new Lesson("days.report", "Two weeks to day 100. The report grades five goals: the flagship, the cabinet, the economy, the crisis and your standing.",
                    "Finish what is closest to done."));
    }
}
