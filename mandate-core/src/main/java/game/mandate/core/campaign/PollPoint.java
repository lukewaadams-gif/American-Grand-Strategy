package game.mandate.core.campaign;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One week of the polling the player sees, from the player's side: the national average, the electoral-vote
 * projection from state polls, a win probability and the tipping-point state, plus the campaign analytics' reading
 * of what moved the race that week. Polls carry the hidden systematic miss; the analytics explain the movement, not
 * the miss.
 *
 * @param national      national poll margin for the player, in points
 * @param electoralVotes the player's electoral votes if every state poll were right
 * @param winChance     the player's chance of winning given the polls and their historical error
 * @param movers        what moved the race this week, largest first
 */
public record PollPoint(LocalDate date, double national, int electoralVotes, double winChance, String tippingPoint, List<String> movers) {

    public PollPoint { movers = List.copyOf(movers); }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString()); m.put("national", national); m.put("ev", electoralVotes); m.put("win", winChance);
        m.put("tipping", tippingPoint); m.put("movers", new ArrayList<>(movers));
        return m;
    }

    static PollPoint fromJson(Map<String, Object> m) {
        return new PollPoint(LocalDate.parse((String) m.get("date")), Json.num(m.get("national")), Json.integer(m.get("ev")), Json.num(m.get("win")),
                (String) m.get("tipping"), Json.array(m.get("movers")).stream().map(o -> (String) o).toList());
    }
}
