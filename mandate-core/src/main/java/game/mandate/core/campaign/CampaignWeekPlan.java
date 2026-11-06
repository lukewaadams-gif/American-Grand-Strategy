package game.mandate.core.campaign;

import java.util.List;
import java.util.Map;

/**
 * One week of campaign decisions. The candidate has {@link #STOPS} stops: each is a rally in a state
 * or a fundraiser. Money goes to ads (fast, fades) or field (slow, lasts). The message is the issue
 * the campaign drives all week.
 *
 * <p>The decision this creates: time vs. money vs. durability. Fundraise now and you can afford ads
 * later; build field early and it compounds; rally where it is close, or where you can expand the map.
 */
public record CampaignWeekPlan(List<String> rallyStates, int fundraisers, Map<String, Double> adSpend,
                               Map<String, Double> fieldSpend, Issue message) {
    public static final int STOPS = 3;

    public CampaignWeekPlan {
        rallyStates = List.copyOf(rallyStates);
        adSpend = Map.copyOf(adSpend);
        fieldSpend = Map.copyOf(fieldSpend);
        if (fundraisers < 0) throw new IllegalArgumentException("fundraisers < 0");
        if (rallyStates.size() + fundraisers > STOPS) throw new IllegalArgumentException("At most " + STOPS + " stops a week");
        if (adSpend.values().stream().anyMatch(v -> v < 0) || fieldSpend.values().stream().anyMatch(v -> v < 0))
            throw new IllegalArgumentException("Negative spending");
    }

    public double totalSpend() {
        return adSpend.values().stream().mapToDouble(Double::doubleValue).sum()
                + fieldSpend.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
