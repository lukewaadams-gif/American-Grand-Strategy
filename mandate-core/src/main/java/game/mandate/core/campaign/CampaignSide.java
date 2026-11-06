package game.mandate.core.campaign;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** One campaign's resources and accumulated investment in each state. Changed only by the election engine. */
public final class CampaignSide {
    private final Party party;
    private final Candidate candidate;
    double cash;
    double momentum;
    Issue lastMessage;
    int weeksOnMessage;
    final Map<String, Double> adPressure = new TreeMap<>();
    final Map<String, Double> field = new TreeMap<>();
    final Map<String, Double> visits = new TreeMap<>();

    CampaignSide(Party party, Candidate candidate, double cash) {
        this.party = party;
        this.candidate = candidate;
        this.cash = cash;
    }

    public Party party() { return party; }
    public Candidate candidate() { return candidate; }
    public double cash() { return cash; }
    public double momentum() { return momentum; }
    public Issue lastMessage() { return lastMessage; }
    public int weeksOnMessage() { return weeksOnMessage; }
    public double ads(String state) { return adPressure.getOrDefault(state, 0.0); }
    public double field(String state) { return field.getOrDefault(state, 0.0); }
    public double visits(String state) { return visits.getOrDefault(state, 0.0); }

    void setMessage(Issue issue) {
        weeksOnMessage = issue == lastMessage ? weeksOnMessage + 1 : 1;
        lastMessage = issue;
    }

    void decay(CampaignParams p) {
        momentum *= p.momentumDecay();
        adPressure.replaceAll((k, v) -> v * p.adDecay());
        field.replaceAll((k, v) -> v * p.fieldDecay());
        visits.replaceAll((k, v) -> v * p.visitDecay());
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("party", party); m.put("candidate", candidate.id()); m.put("cash", cash); m.put("momentum", momentum);
        m.put("lastMessage", lastMessage == null ? null : lastMessage.name()); m.put("weeksOnMessage", weeksOnMessage);
        m.put("ads", new LinkedHashMap<>(adPressure)); m.put("field", new LinkedHashMap<>(field)); m.put("visits", new LinkedHashMap<>(visits));
        return m;
    }

    static CampaignSide fromJson(Map<String, Object> m, CandidateCatalog catalog) {
        CampaignSide s = new CampaignSide(Party.valueOf((String) m.get("party")), catalog.get((String) m.get("candidate")), Json.num(m.get("cash")));
        s.momentum = Json.num(m.get("momentum"));
        s.lastMessage = m.get("lastMessage") == null ? null : Issue.valueOf((String) m.get("lastMessage"));
        s.weeksOnMessage = Json.integer(m.get("weeksOnMessage"));
        Json.object(m.get("ads")).forEach((k, v) -> s.adPressure.put(k, Json.num(v)));
        Json.object(m.get("field")).forEach((k, v) -> s.field.put(k, Json.num(v)));
        Json.object(m.get("visits")).forEach((k, v) -> s.visits.put(k, Json.num(v)));
        return s;
    }
}
