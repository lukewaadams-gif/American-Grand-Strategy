package game.mandate.core.campaign;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The streamlined campaign: a standing strategy the staff turns into a weekly plan (stops, ads, field, message).
 * The player can keep it all campaign, change it any week, or replace one week with a hand-built plan
 * ({@code Command.PlanCampaign}); the opponent picks from the same presets. Every preset is a real trade-off
 * between time, money and durability, using the same campaign engine as a hand-built plan.
 */
public enum CampaignStrategy {
    BATTLEGROUND("Battleground blitz", "Rallies and money in the closest, biggest states.", 0.70, 2, 0.0),
    EXPAND_THE_MAP("Expand the map", "Contest states that lean against you: a higher ceiling, a thinner defence.", 0.60, 2, 3.5),
    GROUND_GAME("Ground game", "Field offices and organisers: slow to build, lasting, strongest when started early.", 0.25, 2, 0.0),
    AIR_WAR("Air war", "Television and digital ads: fast, expensive, fading within weeks.", 0.95, 2, 0.0),
    BANK_IT("Bank it", "Two fundraisers a week and light spending now, for a bigger push later.", 0.60, 1, 0.0);

    public final String label;
    public final String description;
    /** Share of spending that goes to ads (the rest is field). */
    public final double adShare;
    /** Rally stops a week; the rest of the three stops are fundraisers. */
    public final int rallies;
    /** Points of margin the targeting leans toward states behind you (0 = the closest states). */
    public final double reach;

    CampaignStrategy(String label, String description, double adShare, int rallies, double reach) {
        this.label = label;
        this.description = description;
        this.adShare = adShare;
        this.rallies = rallies;
        this.reach = reach;
    }

    /** How much of the war chest goes out each week. */
    public enum SpendRate {
        CAREFUL("Careful", 0.15), STEADY("Steady", 0.30), ALL_IN("All in", 0.60);

        public final String label;
        public final double share;

        SpendRate(String label, double share) {
            this.label = label;
            this.share = share;
        }
    }

    /**
     * A campaign's standing orders.
     * @param message the issue to drive every week, or null to let the staff pick the most favourable issue each week
     */
    public record Orders(CampaignStrategy strategy, SpendRate rate, Issue message) {
        public static final Orders DEFAULT = new Orders(BATTLEGROUND, SpendRate.STEADY, null);

        public String describe() {
            return strategy.label + ", " + rate.label.toLowerCase() + " spending, " + (message == null ? "message chosen weekly" : "message: " + message.label());
        }

        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("strategy", strategy.name()); m.put("rate", rate.name()); m.put("message", message == null ? null : message.name());
            return m;
        }

        public static Orders fromJson(Map<String, Object> m) {
            return new Orders(CampaignStrategy.valueOf((String) m.get("strategy")), SpendRate.valueOf((String) m.get("rate")),
                    m.get("message") == null ? null : Issue.valueOf((String) m.get("message")));
        }

        static Orders fromJsonOrDefault(Object o) { return o == null ? DEFAULT : fromJson(Json.object(o)); }
    }
}
