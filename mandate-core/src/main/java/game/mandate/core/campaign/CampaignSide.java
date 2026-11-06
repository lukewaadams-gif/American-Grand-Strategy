package game.mandate.core.campaign;

import game.mandate.core.state.Party;

import java.util.HashMap;
import java.util.Map;

/** One campaign's resources and accumulated investment in each state. */
public final class CampaignSide {
    private final Party party;
    private final Candidate candidate;
    private double cash;
    private double momentum;
    private Issue lastMessage;
    private int weeksOnMessage;
    private final Map<String, Double> adPressure = new HashMap<>();
    private final Map<String, Double> field = new HashMap<>();
    private final Map<String, Double> visits = new HashMap<>();

    public CampaignSide(Party party, Candidate candidate, double cash) {
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

    public void addCash(double m) { cash += m; }
    public void addMomentum(double m) { momentum += m; }

    void setMessage(Issue issue) {
        weeksOnMessage = issue == lastMessage ? weeksOnMessage + 1 : 1;
        lastMessage = issue;
    }

    void decay() {
        momentum *= 0.80;
        adPressure.replaceAll((k, v) -> v * 0.55);
        field.replaceAll((k, v) -> v * 0.97);
        visits.replaceAll((k, v) -> v * 0.60);
    }

    void addAds(String state, double m) { adPressure.merge(state, m, Double::sum); }
    void addField(String state, double m) { field.merge(state, m, Double::sum); }
    void addVisit(String state) { visits.merge(state, 1.0, Double::sum); }

    public double ads(String state) { return adPressure.getOrDefault(state, 0.0); }
    public double field(String state) { return field.getOrDefault(state, 0.0); }
    public double visits(String state) { return visits.getOrDefault(state, 0.0); }
}
