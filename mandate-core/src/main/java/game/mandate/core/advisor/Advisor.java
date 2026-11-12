package game.mandate.core.advisor;

import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.executive.GovernmentState.SeatStatus;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.ChiefOfStaff;
import game.mandate.core.opening.Decision;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.opening.WhipRead;
import game.mandate.core.politics.OppositionStrategy;
import game.mandate.core.time.Phase;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The chief adviser's brief: a short, ranked summary of what the player can already see, with the reasoning a
 * seasoned staffer would add. It reads only what the screens show: the polls (with their noise and hidden miss
 * still in them), the whip count, intelligence estimates, published indicators. It never reads true margins, the
 * hidden polling miss, an adversary's real resolve or any odds the player is not shown, so it cannot leak the
 * simulation's hidden state (checked by {@code AdvisorTest}).
 */
public final class Advisor {
    private Advisor() {}

    public enum Tone { URGENT, WARNING, OPPORTUNITY, INFO }

    /** The screen the advice is about, so a front end can link to it. */
    public enum Topic { CAMPAIGN, CONGRESS, CABINET, ECONOMY, SECURITY, POLITICS, AGENDA }

    /**
     * @param basis what the advice rests on, in the player's terms ("House whip count: 46% to pass")
     */
    public record Advice(Topic topic, Tone tone, String headline, String detail, String basis) {}

    /** At most {@code max} items, the most pressing first. */
    public static List<Advice> brief(GameEngine g, int max) {
        List<Advice> out = new ArrayList<>();
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> campaign(g, out);
            case TRANSITION -> transition(g, out);
            case FIRST_100_DAYS -> g.hundredDays().ifPresent(h -> hundredDays(g, h, out));
            case GOVERNING -> governing(g, out);
            default -> { }
        }
        out.sort(Comparator.comparingInt(a -> a.tone().ordinal()));
        return out.subList(0, Math.min(max, out.size()));
    }

    // ---- the campaign -------------------------------------------------------------------------

    private static void campaign(GameEngine g, List<Advice> out) {
        List<PollPoint> polls = g.polls();
        LocalDate day = g.phase() == Phase.GENERAL_CAMPAIGN_2028 ? g.calendar().election2028() : g.calendar().election2032();
        long weeks = Math.max(0, ChronoUnit.WEEKS.between(g.date(), day));
        if (!g.pendingEvents().isEmpty())
            out.add(new Advice(Topic.CAMPAIGN, Tone.URGENT, "An event needs your answer", g.pendingEvents().get(0).definition().title()
                    + ". Each answer lists what it costs.", "The campaign's event desk"));
        g.debateThisWeek().ifPresent(d -> {
            DebatePosture theirs = DebatePosture.tendencyOf(g.opponent().candidate());
            DebatePosture counter = null;
            for (DebatePosture p : DebatePosture.values()) if (p.counters(theirs)) counter = p;
            out.add(new Advice(Topic.CAMPAIGN, Tone.URGENT, "Debate this week", "Your opponent tends to \"" + theirs.label().toLowerCase() + "\""
                    + (counter == null ? "." : "; \"" + counter.label().toLowerCase() + "\" counters it."), "Debate prep notes"));
        });
        if (!polls.isEmpty()) {
            PollPoint last = polls.get(polls.size() - 1);
            out.add(new Advice(Topic.CAMPAIGN, last.winChance() < 0.35 ? Tone.WARNING : Tone.INFO,
                    String.format("You are %s by %.1f", last.national() >= 0 ? "ahead" : "behind", Math.abs(last.national())),
                    String.format("%d electoral votes if every state poll is right; the race turns on %s.", last.electoralVotes(), last.tippingPoint()),
                    "This week's polls (a polls-only forecast gives you " + pct(last.winChance()) + ")"));
            if (polls.size() >= 4) {
                double change = last.national() - polls.get(polls.size() - 4).national();
                if (Math.abs(change) >= 1.0)
                    out.add(new Advice(Topic.CAMPAIGN, change < 0 ? Tone.WARNING : Tone.OPPORTUNITY,
                            String.format("You have %s %.1f points in three weeks", change < 0 ? "lost" : "gained", Math.abs(change)),
                            "Biggest recent mover: " + last.movers().get(0).toLowerCase() + ".", "The last four weekly polls"));
            }
            Map<String, Double> statePolls = g.statePolls();
            int sign = g.player().party().sign();
            String tip = last.tippingPoint();
            if (statePolls.containsKey(tip)) {
                double m = statePolls.get(tip) * sign;
                if (Math.abs(m) < 3)
                    out.add(new Advice(Topic.CAMPAIGN, Tone.OPPORTUNITY, tip + " is within reach either way",
                            String.format("You poll %+.1f there. A battleground blitz concentrates rallies and money on states like it.", m), "State polls"));
            }
        }
        double cash = g.player().cash();
        CampaignStrategy.Orders orders = g.election().playerOrders();
        if (weeks <= 5 && cash > 60 && orders.rate() != CampaignStrategy.SpendRate.ALL_IN)
            out.add(new Advice(Topic.CAMPAIGN, Tone.WARNING, String.format("$%.0fM in the bank, %d weeks left", cash, weeks),
                    "Money left on Election Day buys nothing. Consider spending all in.", "Your war chest and the calendar"));
        else if (cash < 30)
            out.add(new Advice(Topic.CAMPAIGN, Tone.WARNING, String.format("Down to $%.0fM", cash),
                    "Spending you cannot cover is scaled down. Banking money for two weeks rebuilds it.", "Your war chest"));
        if (!g.election().opponentReasoning().isEmpty())
            out.add(new Advice(Topic.CAMPAIGN, Tone.INFO, "Your opponent: " + g.election().opponentOrders().strategy().label.toLowerCase(),
                    g.election().opponentReasoning(), "What their campaign is doing in public"));
        if (g.phase() == Phase.GENERAL_CAMPAIGN_2028 && g.election().pledges().isEmpty())
            out.add(new Advice(Topic.AGENDA, Tone.OPPORTUNITY, "You have made no promises",
                    "A promise wins a little support now and becomes your agenda; the first is your flagship if you win.", "Your platform"));
    }

    // ---- the transition ------------------------------------------------------------------------

    private static void transition(GameEngine g, List<Advice> out) {
        out.add(new Advice(Topic.AGENDA, Tone.URGENT, "Choose your agenda", "Up to three priorities; the first becomes the flagship bill of your first hundred days.",
                "The transition"));
        out.add(new Advice(Topic.CONGRESS, Tone.INFO, g.unifiedGovernment() ? "Your party holds both chambers" : "Divided government",
                g.unifiedGovernment() ? "Budget reconciliation can pass a bill with 51 Senate votes, once a year, if the Byrd rule allows it."
                        : "Expect to need the other party's votes: a bill closer to the centre has better odds.", g.congress().toString()));
        if (!g.election().pledges().isEmpty())
            out.add(new Advice(Topic.AGENDA, Tone.WARNING, "Keep your promises on the list",
                    "Each promise left off costs your base wing at the inauguration.", "Your campaign promises"));
    }

    // ---- the first hundred days ------------------------------------------------------------------

    private static void hundredDays(GameEngine g, HundredDays h, List<Advice> out) {
        long open = h.pending().stream().filter(d -> !ChiefOfStaff.handles(d.kind(), h.delegated())).count();
        if (open > 0) {
            Decision first = h.pending().stream().filter(d -> !ChiefOfStaff.handles(d.kind(), h.delegated())).findFirst().orElseThrow();
            out.add(new Advice(topicOf(first.kind()), Tone.URGENT, open == 1 ? "A decision is waiting" : open + " decisions are waiting",
                    first.title() + ". The week cannot end until it is answered.", "Your desk"));
        }
        if (h.stage() == BillStage.NOT_DRAFTED)
            out.add(new Advice(Topic.CONGRESS, Tone.URGENT, "Your flagship is not drafted", "The " + h.billName()
                    + " has to go through committee, the House and the Senate before day 100. Size and pay-for set its odds.", "The legislative calendar"));
        else if (h.live() && h.stage() != BillStage.COMMITTEE) {
            boolean house = h.stage() == BillStage.HOUSE_FLOOR;
            double chance = h.passChance(h.stage());
            if (chance >= 0.6 && !h.billActedThisWeek())
                out.add(new Advice(Topic.CONGRESS, Tone.OPPORTUNITY, "The votes look there: " + pct(chance) + " in the " + (house ? "House" : "Senate"),
                        "Whip counts drift with your approval and the opposition's mood. Calling the vote now locks this in.",
                        String.format("%s whip count", house ? "House" : "Senate")));
            else if (chance < 0.6) {
                WhipRead target = ChiefOfStaff.bestTarget(h, house);
                out.add(new Advice(Topic.CONGRESS, chance < 0.35 ? Tone.WARNING : Tone.INFO, "Short of votes: " + pct(chance) + " in the " + (house ? "House" : "Senate"),
                        target == null ? "No bloc is persuadable at the margin; the bill itself may need to change." : "The " + target.bloc() + " ("
                                + Math.round(target.expectedYes()) + " of " + target.members() + " yes) want " + target.wants() + ".",
                        String.format("%s whip count", house ? "House" : "Senate")));
                if (h.stage() == BillStage.SENATE_FLOOR && h.canReconcile())
                    out.add(new Advice(Topic.CONGRESS, Tone.OPPORTUNITY, "Reconciliation is available", "51 votes instead of 60, at the cost of 15% of the bill and opposition goodwill.",
                            "Senate rules"));
            }
        }
        for (Post p : Post.values()) {
            SeatStatus s = h.seat(p);
            if (s == SeatStatus.VACANT)
                out.add(new Advice(Topic.CABINET, h.week() >= 4 ? Tone.WARNING : Tone.INFO, "No " + HundredDays.postName(p) + " yet",
                        "A nomination takes two weeks: hearings, then the Senate vote. Until then an acting official (competence 45) runs the department.",
                        "The cabinet"));
            else if (s == SeatStatus.HEARINGS_DONE && h.confirmationExpected(p) < 53)
                out.add(new Advice(Topic.CABINET, Tone.WARNING, h.nomineeName(p) + " may not be confirmed",
                        String.format("The Senate count is close: about %.0f votes, 50 needed.", h.confirmationExpected(p)), "The Senate whip on the nomination"));
        }
        if (h.crisis() == CrisisOutcome.ACTIVE && !Double.isNaN(h.intelEstimate())) {
            double e = h.intelEstimate();
            out.add(new Advice(Topic.SECURITY, Tone.WARNING, "Crisis in the " + h.crisisKind().label,
                    e > 0.65 ? "Intelligence reads their resolve as high: force is more likely to provoke than deter."
                            : e < 0.45 ? "Intelligence reads their resolve as low: a show of strength may make them stand down."
                            : "Intelligence reads their resolve as moderate: neither force nor talks is a safe bet.",
                    String.format("Intelligence estimate %.0f%%, deterrence %.0f", 100 * e, h.deterrence())));
        }
        if (h.challenge() != null && h.week() >= 5 && h.challengeResponse() < 0
                && h.pending().stream().noneMatch(d -> d.kind() == Decision.Kind.ECONOMY))
            out.add(new Advice(Topic.ECONOMY, Tone.INFO, h.challenge().title, h.challenge().situation, "Economic briefing"));
        if (h.week() >= 6 && h.week() <= 8 && h.fundingOffer().isEmpty())
            out.add(new Advice(Topic.CONGRESS, Tone.WARNING, "Government funding runs out at the end of week 8",
                    "The odds of each stopgap depend on the opposition's mood on the deadline.", "The budget calendar"));
        for (int w = 0; w < 2; w++)
            if (h.wingMood(w) < 40)
                out.add(new Advice(Topic.POLITICS, Tone.WARNING, String.format("The %s are restless (%.0f)", h.wingName(w), h.wingMood(w)),
                        "Below 32 they revolt and vote against you. Projects, appointments or a change to the bill can win them back.", "Party whip"));
        if (h.opposition() == OppositionStrategy.OBSTRUCT)
            out.add(new Advice(Topic.POLITICS, Tone.INFO, "The opposition is obstructing", "Meeting their leaders raises goodwill; a cleaner stopgap is likelier to pass.",
                    String.format("Opposition goodwill %.0f", h.goodwill())));
        if (h.attention() > 0 && open == 0)
            out.add(new Advice(Topic.POLITICS, Tone.INFO, h.attention() + " attention left this week", "Unused attention does not carry over.", "Your schedule"));
        double start = g.state().hundredDays().startApproval();
        if (h.approval() < start - 4)
            out.add(new Advice(Topic.POLITICS, Tone.WARNING, String.format("Approval down %.0f points since the inauguration", start - h.approval()),
                    "The report judges your standing against where you started.", "Weekly approval"));
    }

    private static Topic topicOf(Decision.Kind k) {
        return switch (k) {
            case CRISIS, AFTERMATH -> Topic.SECURITY;
            case ECONOMY -> Topic.ECONOMY;
            case NOMINEE_REJECTED -> Topic.CABINET;
            case FUNDING, SHUTDOWN, BILL_FAILED, BOTTLED_IN_COMMITTEE -> Topic.CONGRESS;
            case FACTION_REVOLT, LEAK -> Topic.POLITICS;
        };
    }

    // ---- monthly governing ----------------------------------------------------------------------

    private static void governing(GameEngine g, List<Advice> out) {
        if (!g.pendingEvents().isEmpty())
            out.add(new Advice(Topic.POLITICS, Tone.URGENT, "An event needs your answer", g.pendingEvents().get(0).definition().title() + ".", "Your desk"));
        if (g.attention() >= 2)
            out.add(new Advice(Topic.CONGRESS, Tone.INFO, g.attention() + " attention this month", "Pushing a bill takes 2; an address, deterrence or diplomacy takes 1.", "Your schedule"));
        double taiwan = g.diplomacy().tension(game.mandate.core.diplomacy.HotSpot.TAIWAN_STRAIT);
        if (taiwan > 55)
            out.add(new Advice(Topic.SECURITY, Tone.WARNING, String.format("Taiwan Strait tension %.0f", taiwan),
                    "Bolstering deterrence lowers the chance a crisis climbs the ladder.", "Published tension index"));
        for (String id : g.agenda()) {
            if (g.laws().contains(id)) continue;
            out.add(new Advice(Topic.CONGRESS, Tone.OPPORTUNITY, "Unfinished: " + g.bill(id).name(), "Agenda items still undone weigh on the 2032 campaign.", "Your agenda"));
            break;
        }
    }

    /** A probability as the player reads it; never "0%" or "100%", since a whip count or a poll is never certain. */
    public static String pct(double p) {
        if (p < 0.005) return "under 1%";
        if (p > 0.995) return "over 99%";
        return String.format("%.0f%%", 100 * p);
    }
}
