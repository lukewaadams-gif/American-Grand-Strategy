package game.mandate.core.effects;

import game.mandate.core.data.Fmt;
import game.mandate.core.campaign.Issue;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.politics.Coalition;
import game.mandate.core.state.Party;

/**
 * State transitions. Everything content can do to the simulation, and everything one engine asks of another,
 * is an effect: events, bills, crises, elections and wars express their consequences as effects, and the
 * {@code GameEngine} routes each one to the single engine that owns the state it changes. No engine writes
 * another engine's variables directly (state setters are package-private), so every cross-system consequence
 * passes through here and is recorded in the consequence ledger.
 */
public sealed interface Effect {

    /** One-line description for the player ("Approval +2"). */
    String describe();

    /** Change to presidential approval, decaying over months (governing only). */
    record Approval(double points) implements Effect {
        public String describe() { return signed("Approval", points); }
    }

    /** Political capital, spent to whip votes and broker deals. */
    record Capital(double points) implements Effect {
        public String describe() { return signed("Political capital", points); }
    }

    /** Demand / supply shock spread over the coming months. */
    record EconomyShock(double growthPoints, double inflationPoints, int months) implements Effect {
        public String describe() {
            return "Economy: growth " + fmt(growthPoints) + ", inflation " + fmt(inflationPoints) + " over " + months + " months";
        }
    }

    /** Campaign-season shift of the national margin toward the player's party (negative = away). */
    record CampaignSwing(double points) implements Effect {
        public String describe() { return signed("National polls (you)", points); }
    }

    record Salience(Issue issue, double delta) implements Effect {
        public String describe() { return issue.label() + (delta >= 0 ? " matters more to voters" : " matters less to voters"); }
    }

    record Tension(HotSpot hotSpot, double delta) implements Effect {
        public String describe() { return signed(hotSpot.label() + " tension", delta); }
    }

    /** Pacific deterrence: the single dial the abstract theater model reads. */
    record Deterrence(double delta) implements Effect {
        public String describe() { return signed("Deterrence in the Pacific", delta); }
    }

    /** Campaign cash for the player, in millions of dollars. */
    record Cash(double millions) implements Effect {
        public String describe() { return signed("Campaign cash ($M)", millions); }
    }

    /** Deficit change as percent of GDP (positive = bigger deficit). */
    record Deficit(double pctGdp) implements Effect {
        public String describe() { return signed("Deficit (% of GDP)", pctGdp); }
    }

    /** Approval of one voter coalition. {@code lasting} effects enter the record and do not fade. */
    record CoalitionApproval(Coalition coalition, double points, boolean lasting) implements Effect {
        public String describe() { return signed(coalition.label() + " approval" + (lasting ? " (lasting)" : ""), points); }
    }

    /** Opposition leaders' goodwill toward the president, which sets their strategy. */
    record Goodwill(double delta) implements Effect {
        public String describe() { return signed("Opposition goodwill", delta); }
    }

    /** The mood of one wing (0 = base wing, 1 = moderate wing) of the president's party. */
    record WingMood(int wing, double delta) implements Effect {
        public String describe() { return signed((wing == 0 ? "Base wing" : "Moderate wing") + " mood", delta); }
    }

    /** The president publicly leans on the Fed: credibility falls, a close call tilts toward a cut. */
    record FedPressure() implements Effect {
        public String describe() { return "Fed credibility falls"; }
    }

    /** Strategic Petroleum Reserve release: knocks this many dollars off the oil price, tapering weekly. */
    record OilRelease(double dollars) implements Effect {
        public String describe() { return signed("Oil price ($)", -dollars); }
    }

    /** A munitions production surge, as a fraction of the current rate. */
    record Munitions(double surge) implements Effect {
        public String describe() { return signed("Munitions production (%)", surge * 100); }
    }

    /** A war has ended; deterrence is reset to what the outcome left. */
    record WarEnded(double deterrenceAfter) implements Effect {
        public String describe() { return "Deterrence after the war: " + Math.round(deterrenceAfter); }
    }

    /** New seat counts produced by an election. */
    record CongressElected(int houseDem, int senateDem) implements Effect {
        public String describe() { return "Congress: House D " + houseDem + ", Senate D " + senateDem; }
    }

    /** A president takes office. */
    record PresidentInaugurated(String candidateId, Party party, int electoralVotes) implements Effect {
        public String describe() { return "A " + party.adjective() + " president takes office (" + electoralVotes + " electoral votes)"; }
    }

    // ---- the president's week (Resolute Desk): what each slot does to opinion ---------------------------------------

    /** Policy time on an issue, {@code slots} times this week: command of the issue and standing on it grow, saturating. */
    record IssueWork(Issue issue, double quality, int slots) implements Effect {
        public String describe() { return "Policy time on " + issue.label().toLowerCase() + (slots > 1 ? " (x" + slots + ")" : ""); }
    }

    /** White House remarks on an issue: it matters more, standing on it moves by {@code push} (plus the day's luck). */
    record Remarks(Issue issue, double push, double luck) implements Effect {
        public String describe() { return "Remarks on " + issue.label().toLowerCase(); }
    }

    /** Command of an issue, in points (working late on the top issue). */
    record IssueCommand(Issue issue, double points) implements Effect {
        public String describe() { return signed("Command of " + issue.label().toLowerCase(), points); }
    }

    /** The president's record of wins and missteps (Resolute Desk's {@code perf}): moves approval's target by 1.6 a point, fading. */
    record Performance(double points) implements Effect {
        public String describe() { return signed("Performance", points); }
    }

    /** Standing in one state (Resolute Desk's {@code stateBump}), from a visit or a campaign stop; fades 10% a week. */
    record StateStanding(String state, double points) implements Effect {
        public String describe() { return signed("Standing in " + state, points); }
    }

    /** A rally around the flag (passes through approval's partisan ceiling), fading. */
    record Rally(double points) implements Effect {
        public String describe() { return signed("Rally around the flag", points); }
    }

    /** Scandal, weighing on approval's target by 1.2 a point, fading. */
    record Scandal(double points) implements Effect {
        public String describe() { return signed("Scandal", points); }
    }

    private static String signed(String label, double v) { return label + " " + fmt(v); }

    private static String fmt(double v) {
        return (v >= 0 ? "+" : "") + (Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : Fmt.format("%.1f", v));
    }
}
