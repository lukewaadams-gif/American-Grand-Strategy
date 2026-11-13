package game.mandate.core.taiwan;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a side orders in the Taiwan crisis (docs/ISLAND_CHAIN.md): the theatre directives of spec 11.5 for both sides, the
 * crisis decisions (preparation programmes, diplomacy, economic measures), and unit orders. A directive the player gives is
 * taken from the staff until {@link Playbook} hands everything back.
 */
public sealed interface TaiwanOrder {

    // ---- both sides ---------------------------------------------------------------------------------------------------

    /** The rules of engagement: the side's rung on the escalation ladder, 0-6 (TaiwanState.RED_RUNG / BLUE_RUNG). */
    record Rung(int rung) implements TaiwanOrder {}
    /** Nuclear posture: 0 normal, 1 raise, 2 disperse, 3 threaten. */
    record Nuclear(int alert) implements TaiwanOrder {}
    /** Propose a ceasefire: 0 status quo ante, 1 ceasefire in place, 2 unification talks (China only). */
    record Ceasefire(int terms) implements TaiwanOrder {}
    /** Answer the other side's ceasefire offer. */
    record Answer(boolean accept) implements TaiwanOrder {}
    /** Start or halt a preparation programme (its id in programmes.csv). */
    record Programme(String id, boolean run) implements TaiwanOrder {}
    /** One diplomatic action toward one country a day (spec 8.2). US: consult, access, basing, combat, assurance, sanctions,
     *  airports, warn. China: reassure, coerce, threaten, support, neutrality, influence. */
    record Diplomacy(String country, String action) implements TaiwanOrder {}
    /** An economic measure (spec 7.6). US: sanctions1, sanctions2, sanctions3, swift, freeze, chips, distant. China: rareearths,
     *  boycott, treasury, tourism, countersanctions, seize. */
    record Economic(String measure) implements TaiwanOrder {}
    /** A unit order (the toggle of spec 11.5): unit is a unit's key; mission for a group "patrol", "screen", "port"; for a
     *  submarine "patrol", "port"; for a squadron "cap", "strike", "hold"; for a battery "fire", "hold"; "release" hands the
     *  unit back to the staff. target: a zone id, base id, sector id or contact unit id, as the mission needs. */
    record Unit(String unit, String mission, String target) implements TaiwanOrder {}
    /** The staff's directives for this turn: every directive goes back to the staff, and the staff's playbook is applied. */
    record Playbook() implements TaiwanOrder {}

    // ---- the United States ----------------------------------------------------------------------------------------------

    /** Strikes on the mainland: 0 none, 1 limited (ports, embarkation areas, air bases, coastal launchers: rung 4), 2 broad
     *  (missile forces, radars, command: rung 5). */
    record Mainland(int level) implements TaiwanOrder {}
    /** The carriers: 0 forward (Philippine Sea west), 1 stand-off (north and central), 2 withdrawn (Guam), 3 commander's choice. */
    record Fleet(int posture) implements TaiwanOrder {}
    /** Shares of strike-capable aircraft for anti-ship strikes, mainland strikes, CAP over Taiwan and fleet defence. */
    record AirPriority(double antiShip, double mainland, double taiwanCap, double fleetDefence) implements TaiwanOrder {}
    /** Attack submarines: 0 the Taiwan Strait, 1 approaches and chokepoints, 2 a distant blockade, 3 commander's choice. */
    record Submarines(int focus) implements TaiwanOrder {}
    /** Munitions: 0 conserve (smaller salvos, a quarter of LRASM and SM-6 kept back), 1 normal, 2 surge. */
    record MunitionsPolicy(int level) implements TaiwanOrder {}
    /** Escort shipping to Taiwan. */
    record Convoys(boolean on) implements TaiwanOrder {}
    /** An ally's role: 0 access only, 1 basing, 2 defend its own territory, 3 full combat (the staff asks, spec 8.2). */
    record AllyRole(String country, int role) implements TaiwanOrder {}
    /** Ask Taipei: "mobilise" its reserves, "ration" energy, "disperse" its navy and missiles. */
    record TaiwanAsk(String what) implements TaiwanOrder {}
    /** Order a pre-emptive strike in twelve hours (true), or cancel it (false). */
    record Preempt(boolean strike) implements TaiwanOrder {}

    // ---- China --------------------------------------------------------------------------------------------------------------

    /** The course of action: a plan ("invasion", "blockade", "quarantine", "islands", "strikes"), the H-hour options, the main
     *  landing region (0 north, 1 centre, 2 south) and the blockade's lethality (0 board, 1 submarines and mines, 2 sink). */
    record Plan(String plan, boolean strikeUS, boolean strikeJapan, boolean cyber, boolean decapitation, boolean penghuFirst, int region,
                int lethality) implements TaiwanOrder {}
    /** Set D-day (a day of the crisis, at least tomorrow, at most day 110): H-hour is 05:00 that day. */
    record DDay(int day) implements TaiwanOrder {}
    /** Stand down: no war; every programme halts. */
    record StandDown() implements TaiwanOrder {}
    /** Start every programme the plan needs. */
    record Prepare() implements TaiwanOrder {}
    /** Deception: "reports" (feed false reports) or "decoy" (send a militia flotilla dressed as an amphibious group). */
    record Deception(String what) implements TaiwanOrder {}

    static Map<String, Object> toJson(TaiwanOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Rung x -> m.put("rung", x.rung());
            case Nuclear x -> m.put("alert", x.alert());
            case Ceasefire x -> m.put("terms", x.terms());
            case Answer x -> m.put("accept", x.accept());
            case Programme x -> { m.put("id", x.id()); m.put("run", x.run()); }
            case Diplomacy x -> { m.put("country", x.country()); m.put("action", x.action()); }
            case Economic x -> m.put("measure", x.measure());
            case Unit x -> { m.put("unit", x.unit()); m.put("mission", x.mission()); m.put("target", x.target()); }
            case Playbook x -> { }
            case Mainland x -> m.put("level", x.level());
            case Fleet x -> m.put("posture", x.posture());
            case AirPriority x -> { m.put("antiShip", x.antiShip()); m.put("mainland", x.mainland()); m.put("taiwanCap", x.taiwanCap()); m.put("fleetDefence", x.fleetDefence()); }
            case Submarines x -> m.put("focus", x.focus());
            case MunitionsPolicy x -> m.put("level", x.level());
            case Convoys x -> m.put("on", x.on());
            case AllyRole x -> { m.put("country", x.country()); m.put("role", x.role()); }
            case TaiwanAsk x -> m.put("what", x.what());
            case Preempt x -> m.put("strike", x.strike());
            case Plan x -> {
                m.put("plan", x.plan()); m.put("strikeUS", x.strikeUS()); m.put("strikeJapan", x.strikeJapan()); m.put("cyber", x.cyber());
                m.put("decapitation", x.decapitation()); m.put("penghuFirst", x.penghuFirst()); m.put("region", x.region()); m.put("lethality", x.lethality());
            }
            case DDay x -> m.put("day", x.day());
            case StandDown x -> { }
            case Prepare x -> { }
            case Deception x -> m.put("what", x.what());
        }
        return m;
    }

    static TaiwanOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Rung" -> new Rung(Json.integer(m.get("rung")));
            case "Nuclear" -> new Nuclear(Json.integer(m.get("alert")));
            case "Ceasefire" -> new Ceasefire(Json.integer(m.get("terms")));
            case "Answer" -> new Answer((Boolean) m.get("accept"));
            case "Programme" -> new Programme((String) m.get("id"), (Boolean) m.get("run"));
            case "Diplomacy" -> new Diplomacy((String) m.get("country"), (String) m.get("action"));
            case "Economic" -> new Economic((String) m.get("measure"));
            case "Unit" -> new Unit((String) m.get("unit"), (String) m.get("mission"), (String) m.get("target"));
            case "Playbook" -> new Playbook();
            case "Mainland" -> new Mainland(Json.integer(m.get("level")));
            case "Fleet" -> new Fleet(Json.integer(m.get("posture")));
            case "AirPriority" -> new AirPriority(Json.num(m.get("antiShip")), Json.num(m.get("mainland")), Json.num(m.get("taiwanCap")), Json.num(m.get("fleetDefence")));
            case "Submarines" -> new Submarines(Json.integer(m.get("focus")));
            case "MunitionsPolicy" -> new MunitionsPolicy(Json.integer(m.get("level")));
            case "Convoys" -> new Convoys((Boolean) m.get("on"));
            case "AllyRole" -> new AllyRole((String) m.get("country"), Json.integer(m.get("role")));
            case "TaiwanAsk" -> new TaiwanAsk((String) m.get("what"));
            case "Preempt" -> new Preempt((Boolean) m.get("strike"));
            case "Plan" -> new Plan((String) m.get("plan"), (Boolean) m.get("strikeUS"), (Boolean) m.get("strikeJapan"), (Boolean) m.get("cyber"),
                    (Boolean) m.get("decapitation"), (Boolean) m.get("penghuFirst"), Json.integer(m.get("region")), Json.integer(m.get("lethality")));
            case "DDay" -> new DDay(Json.integer(m.get("day")));
            case "StandDown" -> new StandDown();
            case "Prepare" -> new Prepare();
            case "Deception" -> new Deception((String) m.get("what"));
            default -> throw new IllegalArgumentException("Unknown Taiwan order " + m.get("type"));
        };
    }
}
