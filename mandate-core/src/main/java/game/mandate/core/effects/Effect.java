package game.mandate.core.effects;

import game.mandate.core.campaign.Issue;
import game.mandate.core.world.HotSpot;

/**
 * Everything content can do to the simulation. Events, bills, crises and decisions all express their
 * consequences as lists of effects, so new content never needs new engine code, every consequence can
 * be shown to the player before and after it happens, and balance tools can sum effects offline.
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

    private static String signed(String label, double v) { return label + " " + fmt(v); }

    private static String fmt(double v) {
        return (v >= 0 ? "+" : "") + (Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format("%.1f", v));
    }
}
