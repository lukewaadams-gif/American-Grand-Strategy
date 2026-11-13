package game.mandate.core.politics;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Public opinion, voter coalitions, parties and politicians, political capital and the opposition's posture.
 * Owned by {@link PoliticsEngine}, the shared political system.
 */
public final class PoliticsState {
    boolean governing;                      // coalition approval runs once there is a president
    Party presidentParty;
    final double[] level = new double[3];   // approval by coalition
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

    // ---- Resolute Desk's approval (opinion/Approval.java): the national number follows a target --------------------
    /** National approval, %. The coalitions' {@code level} and the states' approval are its breakdown. */
    double approval;
    /** The record of wins and missteps, scandal, a rally around the flag and the public mood: each fades week by week. */
    double perf, scandal, rally, mood;
    /** Lasting effects of the record on the national target (lasting coalition effects, weighted), in points. */
    double lasting;
    /**
     * The game's own events and laws on the national target, in points, fading 15% a week: the size and lifetime this
     * game's content was tuned for (Resolute Desk's record, {@code perf}, fades only 4% a week).
     */
    double events;
    /** The price level (1 = 2019 prices), for gas at today's prices against what people expect. */
    double priceLevel = 1;
    /** The president's own appeal, the difficulty's thumb on the scale, and how big the mandate was. Set at inauguration. */
    double personal, difficulty, mandate;
    /** The coalitions' breakdown: each coalition's fading shift (logit), and the national offset that makes them add up. */
    final double[] groupAdj = new double[3];
    double offset;
    /** Standing and command on each issue (fading; 0 to 100), and approval on it. */
    final Map<Issue, Double> issueAdj = new EnumMap<>(Issue.class), command = new EnumMap<>(Issue.class), issueApproval = new EnumMap<>(Issue.class);
    /** Each state's lean toward the president's party (points), its weight, the president's standing there (fading) and approval there. */
    final Map<String, Double> stateLean = new TreeMap<>(), stateWeight = new TreeMap<>(), stateBump = new TreeMap<>(), stateApproval = new TreeMap<>();
    /** What each state's own economy adds to the president's standing there (logit points), from the latest week. */
    final Map<String, Double> stateEconomy = new TreeMap<>();
    double stateOffset;

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
    public double nationalApproval() { return approval; }
    public double performance() { return perf; }
    public double scandal() { return scandal; }
    public double rally() { return rally; }
    public double mood() { return mood; }
    public double honeymoon() { return honeymoon; }
    public double lasting() { return lasting; }
    public double events() { return events; }
    public double mandate() { return mandate; }
    public double issueApproval(Issue i) { return issueApproval.getOrDefault(i, approval); }
    public Map<Issue, Double> issueApproval() { return java.util.Collections.unmodifiableMap(issueApproval); }
    public double command(Issue i) { return command.getOrDefault(i, 50.0); }
    public double standing(Issue i) { return issueAdj.getOrDefault(i, 0.0); }
    public Map<String, Double> stateApproval() { return java.util.Collections.unmodifiableMap(stateApproval); }
    public double stateStanding(String state) { return stateBump.getOrDefault(state, 0.0); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("governing", governing); m.put("presidentParty", presidentParty == null ? null : presidentParty.name());
        m.put("level", java.util.List.of(level[0], level[1], level[2]));
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
        Map<String, Object> rd = new LinkedHashMap<>();
        rd.put("approval", approval); rd.put("perf", perf); rd.put("scandal", scandal); rd.put("rally", rally); rd.put("mood", mood);
        rd.put("lasting", lasting); rd.put("events", events); rd.put("priceLevel", priceLevel); rd.put("personal", personal); rd.put("difficulty", difficulty); rd.put("mandate", mandate);
        rd.put("groupAdj", java.util.List.of(groupAdj[0], groupAdj[1], groupAdj[2])); rd.put("offset", offset); rd.put("stateOffset", stateOffset);
        rd.put("issueAdj", issues(issueAdj)); rd.put("command", issues(command)); rd.put("issueApproval", issues(issueApproval));
        rd.put("stateLean", new LinkedHashMap<String, Object>(stateLean)); rd.put("stateWeight", new LinkedHashMap<String, Object>(stateWeight));
        rd.put("stateBump", new LinkedHashMap<String, Object>(stateBump)); rd.put("stateApproval", new LinkedHashMap<String, Object>(stateApproval));
        rd.put("stateEconomy", new LinkedHashMap<String, Object>(stateEconomy));
        m.put("rd", rd);
        return m;
    }

    private static Map<String, Object> issues(Map<Issue, Double> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k.name(), v));
        return out;
    }

    public static PoliticsState fromJson(Map<String, Object> m) {
        PoliticsState s = new PoliticsState();
        s.governing = (Boolean) m.get("governing");
        s.presidentParty = m.get("presidentParty") == null ? null : Party.valueOf((String) m.get("presidentParty"));
        copy(Json.array(m.get("level")), s.level); copy(Json.array(m.get("record")), s.record);
        s.honeymoon = Json.num(m.get("honeymoon"));
        Json.object(m.get("salience")).forEach((k, v) -> s.salience.put(Issue.valueOf(k), Json.num(v)));
        s.capital = Json.num(m.get("capital")); s.goodwill = Json.num(m.get("goodwill"));
        s.opposition = OppositionStrategy.valueOf((String) m.get("opposition"));
        s.recentAddresses = Json.num(m.get("recentAddresses")); s.weeksGoverning = Json.num(m.get("weeksGoverning"));
        for (Object o : Json.array(m.get("parties"))) { PartyState p = PartyState.fromJson(Json.object(o)); s.parties.put(p.party, p); }
        Json.object(m.get("roles")).forEach((k, v) -> s.roles.put(k, Role.valueOf((String) v)));
        if (m.get("rd") != null) {
            Map<String, Object> rd = Json.object(m.get("rd"));
            s.approval = Json.num(rd.get("approval")); s.perf = Json.num(rd.get("perf")); s.scandal = Json.num(rd.get("scandal"));
            s.rally = Json.num(rd.get("rally")); s.mood = Json.num(rd.get("mood")); s.lasting = Json.num(rd.get("lasting")); s.events = Json.num(rd.get("events"));
            s.priceLevel = Json.num(rd.get("priceLevel")); s.personal = Json.num(rd.get("personal")); s.difficulty = Json.num(rd.get("difficulty"));
            s.mandate = Json.num(rd.get("mandate"));
            copy(Json.array(rd.get("groupAdj")), s.groupAdj); s.offset = Json.num(rd.get("offset")); s.stateOffset = Json.num(rd.get("stateOffset"));
            Json.object(rd.get("issueAdj")).forEach((k, v) -> s.issueAdj.put(Issue.valueOf(k), Json.num(v)));
            Json.object(rd.get("command")).forEach((k, v) -> s.command.put(Issue.valueOf(k), Json.num(v)));
            Json.object(rd.get("issueApproval")).forEach((k, v) -> s.issueApproval.put(Issue.valueOf(k), Json.num(v)));
            Json.object(rd.get("stateLean")).forEach((k, v) -> s.stateLean.put(k, Json.num(v)));
            Json.object(rd.get("stateWeight")).forEach((k, v) -> s.stateWeight.put(k, Json.num(v)));
            Json.object(rd.get("stateBump")).forEach((k, v) -> s.stateBump.put(k, Json.num(v)));
            Json.object(rd.get("stateApproval")).forEach((k, v) -> s.stateApproval.put(k, Json.num(v)));
            if (rd.containsKey("stateEconomy")) Json.object(rd.get("stateEconomy")).forEach((k, v) -> s.stateEconomy.put(k, Json.num(v)));
        }
        return s;
    }

    private static void copy(java.util.List<Object> from, double[] to) { for (int i = 0; i < to.length; i++) to[i] = Json.num(from.get(i)); }
}
