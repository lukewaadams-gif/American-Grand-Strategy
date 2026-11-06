package game.mandate.core.politics;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.LinkedHashMap;
import java.util.Map;

/** One party: its two wings and their mood, and its current leader (nominee or president). */
public final class PartyState {
    final Party party;
    final double[] wingMood = new double[2];
    String leaderId;

    PartyState(Party party, double mood, String leaderId) {
        this.party = party;
        wingMood[0] = mood;
        wingMood[1] = mood;
        this.leaderId = leaderId;
    }

    public Party party() { return party; }
    public double wingMood(int wing) { return wingMood[wing]; }
    public double averageMood() { return (wingMood[0] + wingMood[1]) / 2; }
    public String leaderId() { return leaderId; }

    public String wingName(int w) {
        if (party == Party.DEMOCRAT) return w == 0 ? "Progressives" : "Moderates";
        return w == 0 ? "Populists" : "Traditionalists";
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("party", party); m.put("wing0", wingMood[0]); m.put("wing1", wingMood[1]); m.put("leader", leaderId);
        return m;
    }

    static PartyState fromJson(Map<String, Object> m) {
        PartyState p = new PartyState(Party.valueOf((String) m.get("party")), 0, (String) m.get("leader"));
        p.wingMood[0] = Json.num(m.get("wing0"));
        p.wingMood[1] = Json.num(m.get("wing1"));
        return p;
    }
}
