package game.mandate.core.campaign;

import game.mandate.core.data.Json;
import game.mandate.core.electorate.ElectionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Campaigns and election results. Owned by {@link ElectionEngine}. */
public final class ElectionState {
    /** What a midterm did, from the president's side. */
    public record Midterm(int year, double marginForPresident, int houseChange, int senateChange) {}

    /** The parts of the player's campaign the staff can run (BG Staff.AREA: schedule, ads, field). */
    public enum StaffArea { SCHEDULE, ADS, FIELD, SURROGATES }

    CampaignSide player, opponent;
    DebatePosture pendingPosture;
    /** Which parts of the player's campaign the staff runs, rebuilding the orders at the start of each week. */
    final java.util.EnumMap<StaffArea, Boolean> staff = new java.util.EnumMap<>(StaffArea.class);
    double campaignSwingForPlayer;
    double systematicMiss;
    ElectionResult result2028, result2032;
    Midterm midterm;
    /** Standing orders: the streamlined campaign. A hand-built weekly plan overrides them for one week. */
    CampaignStrategy.Orders playerOrders = CampaignStrategy.Orders.DEFAULT, opponentOrders = CampaignStrategy.Orders.DEFAULT;
    /** Why the opponent runs the campaign it runs (the AI's reasoning, shown to the player as intelligence). */
    String opponentReasoning = "";
    /** Campaign promises (bill ids). The player's become the agenda's first priorities. */
    final List<String> pledges = new ArrayList<>(), opponentPledges = new ArrayList<>();
    /** The polling the player saw, week by week. */
    final List<PollPoint> polls = new ArrayList<>();
    /** Internal polls the player's campaign commissioned (the War room's Polls tab), newest last. */
    final List<InternalPoll> internalPolls = new ArrayList<>();

    /** An internal poll: of a state (its code) or the nation ("US"), the margin for the player, and its error (one sd). */
    public record InternalPoll(java.time.LocalDate date, String state, double margin, double sd) {}
    /** The race as it stood at the last poll, so the next poll can say what moved. */
    ElectionEngine.Snapshot lastSnapshot;

    ElectionState() { for (StaffArea a : StaffArea.values()) staff.put(a, true); }

    public CampaignSide player() { return player; }
    public CampaignSide opponent() { return opponent; }
    public CampaignSide side(game.mandate.core.state.Party p) { return player.party() == p ? player : opponent; }
    public boolean staffRuns(StaffArea a) { return staff.get(a); }
    public DebatePosture pendingPosture() { return pendingPosture; }
    public double campaignSwingForPlayer() { return campaignSwingForPlayer; }
    public double systematicMiss() { return systematicMiss; }
    public ElectionResult result2028() { return result2028; }
    public ElectionResult result2032() { return result2032; }
    public Midterm midterm() { return midterm; }
    public CampaignStrategy.Orders playerOrders() { return playerOrders; }
    public CampaignStrategy.Orders opponentOrders() { return opponentOrders; }
    public String opponentReasoning() { return opponentReasoning; }
    public List<String> pledges() { return List.copyOf(pledges); }
    public List<String> opponentPledges() { return List.copyOf(opponentPledges); }
    public List<PollPoint> polls() { return List.copyOf(polls); }
    public List<InternalPoll> internalPolls() { return List.copyOf(internalPolls); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("player", player.toJson()); m.put("opponent", opponent.toJson());
        Map<String, Object> st = new LinkedHashMap<>();
        staff.forEach((k, v) -> st.put(k.name(), v));
        m.put("staff", st);
        m.put("pendingPosture", pendingPosture == null ? null : pendingPosture.name());
        m.put("campaignSwing", campaignSwingForPlayer); m.put("systematicMiss", systematicMiss);
        m.put("result2028", result2028 == null ? null : result2028.toJson());
        m.put("result2032", result2032 == null ? null : result2032.toJson());
        if (midterm != null) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("year", midterm.year()); x.put("margin", midterm.marginForPresident()); x.put("house", midterm.houseChange()); x.put("senate", midterm.senateChange());
            m.put("midterm", x);
        } else m.put("midterm", null);
        m.put("playerOrders", playerOrders.toJson()); m.put("opponentOrders", opponentOrders.toJson()); m.put("opponentReasoning", opponentReasoning);
        m.put("pledges", new ArrayList<>(pledges)); m.put("opponentPledges", new ArrayList<>(opponentPledges));
        if (!internalPolls.isEmpty()) {
            List<Object> ip = new ArrayList<>();
            for (InternalPoll x : internalPolls) ip.add(java.util.Arrays.asList(x.date().toString(), x.state(), x.margin(), x.sd()));
            m.put("internalPolls", ip);
        }
        List<Object> ps = new ArrayList<>();
        for (PollPoint x : polls) ps.add(x.toJson());
        m.put("polls", ps);
        m.put("lastSnapshot", lastSnapshot == null ? null : lastSnapshot.toJson());
        return m;
    }

    public static ElectionState fromJson(Map<String, Object> m, CandidateCatalog catalog) {
        ElectionState e = new ElectionState();
        CampaignGeography geo = CampaignGeography.load();
        e.player = CampaignSide.fromJson(Json.object(m.get("player")), catalog, geo);
        e.opponent = CampaignSide.fromJson(Json.object(m.get("opponent")), catalog, geo);
        if (m.get("staff") != null) Json.object(m.get("staff")).forEach((k, v) -> e.staff.put(StaffArea.valueOf(k), (Boolean) v));
        e.pendingPosture = m.get("pendingPosture") == null ? null : DebatePosture.valueOf((String) m.get("pendingPosture"));
        e.campaignSwingForPlayer = Json.num(m.get("campaignSwing")); e.systematicMiss = Json.num(m.get("systematicMiss"));
        e.result2028 = m.get("result2028") == null ? null : ElectionResult.fromJson(Json.object(m.get("result2028")));
        e.result2032 = m.get("result2032") == null ? null : ElectionResult.fromJson(Json.object(m.get("result2032")));
        if (m.get("midterm") != null) {
            Map<String, Object> x = Json.object(m.get("midterm"));
            e.midterm = new Midterm(Json.integer(x.get("year")), Json.num(x.get("margin")), Json.integer(x.get("house")), Json.integer(x.get("senate")));
        }
        // Saves from before the campaign layer have none of these; they load with the defaults.
        e.playerOrders = CampaignStrategy.Orders.fromJsonOrDefault(m.get("playerOrders"));
        e.opponentOrders = CampaignStrategy.Orders.fromJsonOrDefault(m.get("opponentOrders"));
        e.opponentReasoning = m.get("opponentReasoning") == null ? "" : (String) m.get("opponentReasoning");
        if (m.get("pledges") != null) for (Object o : Json.array(m.get("pledges"))) e.pledges.add((String) o);
        if (m.get("internalPolls") != null) for (Object o : Json.array(m.get("internalPolls"))) {
            List<Object> l = Json.array(o);
            e.internalPolls.add(new InternalPoll(java.time.LocalDate.parse((String) l.get(0)), (String) l.get(1), Json.num(l.get(2)), Json.num(l.get(3))));
        }
        if (m.get("opponentPledges") != null) for (Object o : Json.array(m.get("opponentPledges"))) e.opponentPledges.add((String) o);
        if (m.get("polls") != null) for (Object o : Json.array(m.get("polls"))) e.polls.add(PollPoint.fromJson(Json.object(o)));
        e.lastSnapshot = m.get("lastSnapshot") == null ? null : ElectionEngine.Snapshot.fromJson(Json.object(m.get("lastSnapshot")));
        return e;
    }

    public static Map<String, Object> planJson(CampaignWeekPlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rallies", p.rallyStates()); m.put("fundraisers", p.fundraisers());
        m.put("ads", new java.util.TreeMap<>(p.adSpend())); m.put("field", new java.util.TreeMap<>(p.fieldSpend())); m.put("message", p.message().name());
        return m;
    }

    public static CampaignWeekPlan planFromJson(Map<String, Object> m) {
        List<String> rallies = Json.array(m.get("rallies")).stream().map(o -> (String) o).toList();
        Map<String, Double> ads = new LinkedHashMap<>(), field = new LinkedHashMap<>();
        Json.object(m.get("ads")).forEach((k, v) -> ads.put(k, Json.num(v)));
        Json.object(m.get("field")).forEach((k, v) -> field.put(k, Json.num(v)));
        return new CampaignWeekPlan(rallies, Json.integer(m.get("fundraisers")), ads, field, Issue.valueOf((String) m.get("message")));
    }
}
