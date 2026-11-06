package game.mandate.core.politics;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public opinion, voter coalitions, parties and politicians, political capital and the opposition's posture.
 * Owned by {@link PoliticsEngine}, the shared political system.
 */
public final class PoliticsState {
    boolean governing;                      // coalition approval runs once there is a president
    Party presidentParty;
    final double[] level = new double[3];   // approval by coalition
    final double[] modifier = new double[3];// fading event effects
    final double[] record = new double[3];  // lasting effects of the record
    double honeymoon;
    final Map<Issue, Double> salience = new EnumMap<>(Issue.class);
    double capital;
    double goodwill;
    OppositionStrategy opposition = OppositionStrategy.SELECTIVE;
    double recentAddresses;
    double weeksGoverning;
    final Map<Party, PartyState> parties = new EnumMap<>(Party.class);
    final Map<String, Role> roles = new LinkedHashMap<>();

    PoliticsState() {}

    public boolean governing() { return governing; }
    public Party presidentParty() { return presidentParty; }
    public double approval(Coalition c) { return level[c.ordinal()]; }
    public double record(Coalition c) { return record[c.ordinal()]; }
    public double salience(Issue i) { return salience.get(i); }
    public Map<Issue, Double> salience() { return java.util.Collections.unmodifiableMap(salience); }
    public double capital() { return capital; }
    public double goodwill() { return goodwill; }
    public OppositionStrategy opposition() { return opposition; }
    public double recentAddresses() { return recentAddresses; }
    public PartyState party(Party p) { return parties.get(p); }
    public Role role(String politicianId) { return roles.getOrDefault(politicianId, Role.BENCH); }
    public Map<String, Role> roles() { return java.util.Collections.unmodifiableMap(roles); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("governing", governing); m.put("presidentParty", presidentParty == null ? null : presidentParty.name());
        m.put("level", java.util.List.of(level[0], level[1], level[2]));
        m.put("modifier", java.util.List.of(modifier[0], modifier[1], modifier[2]));
        m.put("record", java.util.List.of(record[0], record[1], record[2]));
        m.put("honeymoon", honeymoon);
        Map<String, Object> sal = new LinkedHashMap<>();
        salience.forEach((k, v) -> sal.put(k.name(), v));
        m.put("salience", sal);
        m.put("capital", capital); m.put("goodwill", goodwill); m.put("opposition", opposition);
        m.put("recentAddresses", recentAddresses); m.put("weeksGoverning", weeksGoverning);
        java.util.List<Object> ps = new java.util.ArrayList<>();
        parties.values().forEach(p -> ps.add(p.toJson()));
        m.put("parties", ps);
        Map<String, Object> r = new LinkedHashMap<>();
        roles.forEach((k, v) -> r.put(k, v.name()));
        m.put("roles", r);
        return m;
    }

    public static PoliticsState fromJson(Map<String, Object> m) {
        PoliticsState s = new PoliticsState();
        s.governing = (Boolean) m.get("governing");
        s.presidentParty = m.get("presidentParty") == null ? null : Party.valueOf((String) m.get("presidentParty"));
        copy(Json.array(m.get("level")), s.level); copy(Json.array(m.get("modifier")), s.modifier); copy(Json.array(m.get("record")), s.record);
        s.honeymoon = Json.num(m.get("honeymoon"));
        Json.object(m.get("salience")).forEach((k, v) -> s.salience.put(Issue.valueOf(k), Json.num(v)));
        s.capital = Json.num(m.get("capital")); s.goodwill = Json.num(m.get("goodwill"));
        s.opposition = OppositionStrategy.valueOf((String) m.get("opposition"));
        s.recentAddresses = Json.num(m.get("recentAddresses")); s.weeksGoverning = Json.num(m.get("weeksGoverning"));
        for (Object o : Json.array(m.get("parties"))) { PartyState p = PartyState.fromJson(Json.object(o)); s.parties.put(p.party, p); }
        Json.object(m.get("roles")).forEach((k, v) -> s.roles.put(k, Role.valueOf((String) v)));
        return s;
    }

    private static void copy(java.util.List<Object> from, double[] to) { for (int i = 0; i < to.length; i++) to[i] = Json.num(from.get(i)); }
}
