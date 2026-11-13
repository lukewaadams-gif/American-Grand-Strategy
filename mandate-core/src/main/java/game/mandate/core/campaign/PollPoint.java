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
 * @param states        that week's state polls as the player saw them, D minus R, to the hundredth of a point (empty in
 *                      saves made before they were kept)
 */
public record PollPoint(LocalDate date, double national, int electoralVotes, double winChance, String tippingPoint, List<String> movers,
                        Map<String, Double> states) {

    public PollPoint {
        movers = List.copyOf(movers);
        states = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(states));
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString()); m.put("national", national); m.put("ev", electoralVotes); m.put("win", winChance);
        m.put("tipping", tippingPoint); m.put("movers", new ArrayList<>(movers));
        if (!states.isEmpty()) m.put("states", new LinkedHashMap<String, Object>(states));
        return m;
    }

    static PollPoint fromJson(Map<String, Object> m) {
        Map<String, Double> states = new LinkedHashMap<>();
        if (m.get("states") != null) Json.object(m.get("states")).forEach((k, v) -> states.put(k, Json.num(v)));
        return new PollPoint(LocalDate.parse((String) m.get("date")), Json.num(m.get("national")), Json.integer(m.get("ev")), Json.num(m.get("win")),
                (String) m.get("tipping"), Json.array(m.get("movers")).stream().map(o -> (String) o).toList(), states);
    }
}
