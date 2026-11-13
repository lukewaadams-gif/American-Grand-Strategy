package game.mandate.web.api;

import game.mandate.core.command.Command;
import game.mandate.core.data.Fmt;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.foreign.ForeignData;
import game.mandate.core.foreign.ForeignEngine;
import game.mandate.core.foreign.ForeignOrder;
import game.mandate.core.foreign.ForeignState;
import game.mandate.core.presidency.WatchPlan;
import game.mandate.core.time.Phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static game.mandate.core.foreign.ForeignData.*;

/**
 * The Situation Room (Resolute Desk's {@code SitRoom} and {@code StatecraftUI}, presidency milestone 8): the world and
 * its countries, the President's Daily Brief, the hot spots, operations and covert action, trade, the alliances, wars,
 * and one country's statecraft; and the questions the world puts on the desk.
 */
final class ForeignViews {
    private ForeignViews() {}

    static boolean open(GameEngine g) { return g.foreign().isPresent() && g.phase() == Phase.GOVERNING; }

    static long r(double v) { return Math.round(v); }

    static Map<String, Object> room(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("date", g.date().toString());
        if (g.foreign().isEmpty()) {
            v.put("open", false);
            v.put("reason", "The Situation Room opens when the first hundred days are over and the national security team is in place.");
            return v;
        }
        ForeignState s = g.foreign().get();
        ForeignEngine e = g.foreignEngine();
        ForeignData d = e.data();
        ForeignEngine.Context c = g.foreignContext();
        boolean open = open(g) && !g.operationsActive();
        v.put("open", open);
        if (!open) v.put("reason", g.operationsActive() ? "The war in the Taiwan Strait has the room's full attention." : "The Situation Room is closed.");
        v.put("defcon", s.defcon());
        v.put("message", s.message());
        v.put("callsLeft", s.callsLeft());
        List<Object> countries = new ArrayList<>();
        for (int i = 0; i < NC; i++) {
            ForeignData.Country k = d.country(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.id()); m.put("name", k.name()); m.put("region", REGION[k.region()]); m.put("align", ALIGN[s.alignment(i)]);
            m.put("rel", r(s.relations(i))); m.put("power", k.power()); m.put("trade", k.trade()); m.put("balance", k.balance()); m.put("nuclear", k.nuclear());
            m.put("leader", s.leader(i)); m.put("title", s.leaderTitle(i)); m.put("flag", k.flag()); m.put("label", List.of(k.mapX(), k.mapY()));
            m.put("tariff", s.tariff(i)); m.put("sanction", s.sanction(i)); m.put("aid", s.aid(i)); m.put("visits", s.visits(i));
            m.put("visitIn", s.visitAt(i) < 0 ? -1 : s.visitAt(i) - s.week());
            m.put("atWar", e.atWar(s, i)); m.put("toppled", s.toppled(i)); m.put("flight", Math.round(k.flight() * 10) / 10.0);
            m.put("calledRecently", s.week() - s.lastCall(i) < 4);
            countries.add(m);
        }
        v.put("countries", countries);
        if (s.summitCountry() >= 0) v.put("summit", Map.of("country", d.country(s.summitCountry()).id(), "inWeeks", Math.max(0, s.summitWeek() - s.week()), "round", s.summitRound()));
        // the Daily Brief
        List<Object> brief = new ArrayList<>();
        for (String b : s.brief()) { String[] t = b.split("\\|", 2); brief.add(Map.of("priority", Integer.parseInt(t[0]), "text", t[1])); }
        v.put("brief", brief);
        // the hot spots, and the flashpoints that pulse on the map (Resolute Desk's WorldMap)
        v.put("hotspots", hotspots(s));
        List<Object> flash = new ArrayList<>();
        java.util.function.BiConsumer<String, String> at = (code, name) -> { ForeignData.Country k = d.country(d.index(code)); flash.add(Map.of("name", name, "x", k.mapX(), "y", k.mapY())); };
        if (s.taiwanWar() || s.twStage() >= 2) at.accept("TW", "Taiwan");
        if (s.uaStage() == 2) at.accept("UA", "Ukraine");
        if (s.gaza() > 60) at.accept("IL", "Gaza");
        if (s.iran() > 80) at.accept("IR", "Iran");
        if (s.redsea() > 60) flash.add(Map.of("name", "The Red Sea", "x", 610.0, "y", 200.0));
        if (s.nk() > 75) at.accept("KP", "North Korea");
        v.put("flash", flash);
        // operations and covert action
        List<Object> ops = new ArrayList<>();
        for (int i = 0; i < d.operations().size(); i++) {
            ForeignData.Operation op = d.operations().get(i);
            boolean ready = e.operationReady(s, c, i);
            ops.add(Map.of("index", i, "name", op.name(), "desc", op.desc(), "odds", r(100 * e.operationOdds(c, i)), "ready", ready && open,
                    "status", s.operationCooldown(i) > 0 ? "Forces resetting: " + s.operationCooldown(i) + (s.operationCooldown(i) == 1 ? " week" : " weeks") : ready ? "Ready" : "No target"));
        }
        v.put("operations", ops);
        List<Object> cov = new ArrayList<>();
        for (int i = 0; i < d.covert().size(); i++) {
            ForeignData.Covert cv = d.covert().get(i);
            cov.add(Map.of("index", i, "name", cv.name(), "desc", cv.desc(), "odds", r(100 * e.covertOdds(c, i)), "leak", r(100 * e.covertLeak(c, i)),
                    "ready", s.covertCooldown(i) == 0 && open, "status", s.covertCooldown(i) > 0 ? "Running: another in " + s.covertCooldown(i) + " weeks" : "Ready"));
        }
        v.put("covert", cov);
        v.put("counters", Map.of("operations", s.operations(), "covert", s.covertActions(), "exposed", s.exposed(), "crises", s.crisesFaced(), "deals", s.deals(),
                "kia", s.kia(), "ok", s.okCount(), "failed", s.failCount()));
        v.put("tariffs", List.of(TARIFF_OPT));
        v.put("levels", List.of(LEVEL));
        v.put("aidLevels", List.of(AID_LEVEL));
        // the alliances
        Map<String, Object> al = new LinkedHashMap<>();
        al.put("nato", r(s.nato())); al.put("pacific", r(s.pacific())); al.put("allySpend", Math.round(s.allySpend() * 10) / 10.0); al.put("deterrence", r(s.deter()));
        al.put("article5", s.week() - s.art5Week() >= 13 && open); al.put("press", s.week() - s.pressWeek() >= 13 && open); al.put("drills", s.week() - s.drillWeek() >= 13 && open);
        List<Object> posture = new ArrayList<>();
        for (int a = 0; a < 3; a++) posture.add(Map.of("area", POSTURE_AREA[a], "level", s.posture(a)));
        al.put("posture", posture);
        al.put("postureLevels", List.of(POSTURE));
        v.put("alliances", al);
        // wars
        List<Object> wars = new ArrayList<>();
        for (ForeignState.War x : s.wars()) wars.add(war(g, s, x));
        v.put("wars", wars);
        v.put("strategies", List.of(STRATEGY));
        v.put("strategyHelp", List.of(STRATEGY_DESC));
        List<String> rec = new ArrayList<>(s.record());
        java.util.Collections.reverse(rec);
        v.put("record", rec.subList(0, Math.min(14, rec.size())));
        return v;
    }

    static List<Object> hotspots(ForeignState s) {
        List<Object> l = new ArrayList<>();
        l.add(spot("Taiwan and China", s.taiwanWar() ? "War" : TW_STAGE[s.twStage()], List.of(gauge("Tension", s.twt(), false), gauge("Deterrence", s.deter(), true)),
                s.taiwanWar() ? "The war is being fought." : s.twStage() >= 3 ? "Beijing is squeezing the island. Deterrence holds the line; tension rises on its own."
                        : "Tension rises by itself; deterrence above 55 and better relations with China bring it down."));
        l.add(spot("Russia and Ukraine", UA_STAGE[s.uaStage()], List.of(gauge("Ukraine's strength", s.ukr(), true), gauge("Russian gains", s.ruGain(), false), gauge("Progress toward talks", s.talks(), true)),
                s.uaStage() == 2 ? "Aid, NATO's cohesion and our posture in Europe hold the line. Past 80 on talks, a ceasefire can come." : "The guns are quiet; talks keep it that way."));
        l.add(spot("The Middle East", s.iran() > 85 ? "Iran near a bomb" : s.gaza() > 70 ? "Gaza at war" : "Unsettled", List.of(gauge("Fighting in Gaza", s.gaza(), false),
                gauge("Iran's nuclear program", s.iran(), false), gauge("Red Sea attacks", s.redsea(), false), gauge("Attacks on US troops", s.proxy(), false)),
                "Sanctions slow Iran's program; our posture in the Middle East calms the Red Sea and the militias."));
        l.add(spot("The Korean Peninsula", s.nk() > 78 ? "A test is coming" : "Watchful", List.of(gauge("North Korean threat", s.nk(), false)), "It drifts up on its own."));
        l.add(spot("Terrorism and cyber", s.terror() > 55 ? "Elevated" : "Watchful", List.of(gauge("Terror threat", s.terror(), false), gauge("Cyber threat", s.cyber(), false)),
                "Good intelligence chiefs bring the terror threat down; a hostile Russia raises the cyber threat."));
        l.add(spot("The Western Hemisphere", s.migr() > 72 ? "A surge at the border" : "Steady", List.of(gauge("Migration pressure", s.migr(), false), gauge("Cartel strength", s.cartel(), false)),
                "Better relations with Mexico ease the border."));
        return l;
    }

    static Map<String, Object> spot(String name, String stage, List<Object> gauges, String note) {
        return Map.of("name", name, "stage", stage, "gauges", gauges, "note", note);
    }

    static Map<String, Object> gauge(String name, double v, boolean highIsGood) {
        long x = Math.round(v);
        String tone = highIsGood ? (x >= 55 ? "good" : x >= 40 ? "mid" : "bad") : (x >= 70 ? "bad" : x >= 45 ? "mid" : "good");
        return Map.of("name", name, "value", x, "tone", tone);
    }

    static Map<String, Object> war(GameEngine g, ForeignState s, ForeignState.War x) {
        ForeignData d = g.foreignEngine().data();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.id()); m.put("name", x.name()); m.put("enemy", d.country(x.enemy()).name()); m.put("kind", WK_NAME[x.kind()]);
        m.put("stage", WAR_STAGE[x.stage()]); m.put("fighting", x.fighting()); m.put("weeks", x.weeks());
        m.put("front", r(x.front())); m.put("support", r(x.support())); m.put("talks", r(x.talks()));
        m.put("troops", r(x.troops() * 1000)); m.put("need", r(x.need() * 1000)); m.put("kia", r(x.kia()));
        m.put("cost", Math.round(x.troops() * x.cost() * 100) / 100.0); m.put("strategy", x.strategy()); m.put("authorized", x.authorized());
        m.put("timetable", x.timetable() >= 0 ? x.timetable() - s.week() : -1); m.put("handoff", x.handoff() >= 0 ? x.handoff() - s.week() : -1);
        m.put("unrest", r(s.unrest(x.enemy())));
        return m;
    }

    /** Statecraft for one country: where things stand, its objectives, the tools, covert programs and force. */
    static Map<String, Object> country(GameEngine g, String code) {
        Map<String, Object> v = EngineApi.ok();
        if (g.foreign().isEmpty()) { v.put("open", false); return v; }
        ForeignState s = g.foreign().get();
        ForeignEngine e = g.foreignEngine();
        ForeignData d = e.data();
        ForeignEngine.Context c = g.foreignContext();
        int i = d.index(code);
        if (i < 0) return EngineApi.refusal("No such country");
        ForeignData.Country k = d.country(i);
        boolean open = open(g) && !g.operationsActive(), war = e.atWar(s, i);
        v.put("open", open);
        v.put("id", k.id()); v.put("name", k.name()); v.put("nameIn", e.nameIn(i)); v.put("leader", s.leader(i)); v.put("title", s.leaderTitle(i));
        v.put("align", ALIGN[s.alignment(i)]); v.put("region", REGION[k.region()]); v.put("rel", r(s.relations(i))); v.put("nuclear", k.nuclear()); v.put("power", k.power());
        v.put("ties", s.ties(i)); v.put("tiesLevels", List.of(TIES)); v.put("unrest", r(s.unrest(i))); v.put("toppled", s.toppled(i)); v.put("atWar", war);
        v.put("trade", k.trade()); v.put("balance", k.balance()); v.put("tariff", s.tariff(i)); v.put("sanction", s.sanction(i)); v.put("aid", s.aid(i));
        v.put("milAid", s.milAid(i)); v.put("milAidLevels", List.of(MIL_AID)); v.put("arms", s.arms(i)); v.put("armsLevels", List.of(ARMS));
        int step = 0;
        for (int t = 0; t < TROOP_STEP.length; t++) if (Math.abs(TROOP_STEP[t] - s.troopsIn(i)) < Math.abs(TROOP_STEP[step] - s.troopsIn(i))) step = t;
        v.put("troops", r(s.troopsIn(i) * 1000)); v.put("troopStep", step); v.put("troopSteps", List.of(TROOP_NAME));
        v.put("peacekeepers", s.peacekeepers(i)); v.put("canKeepPeace", s.unrest(i) >= 35 && !war);
        v.put("pact", s.pact(i));
        v.put("tradeOdds", r(100 * e.tradeOdds(s, c, i)));
        boolean treatyOk = s.alignment(i) != AL_TREATY && s.alignment(i) != AL_ADVERSARY && s.relations(i) >= 40;
        v.put("treaty", s.alignment(i) == AL_TREATY ? "A treaty ally" : treatyOk ? "About " + r(100 * e.treatyOdds(s, c, i)) + "% to be ratified (two-thirds of the Senate)."
                : s.alignment(i) == AL_ADVERSARY ? "Not with an adversary." : "Relations must be 40 or better first.");
        v.put("treatyReady", treatyOk && open);
        // covert programs
        List<Object> cv = new ArrayList<>();
        for (int kind = 0; kind < 4; kind++) {
            String no = s.covertProgram(i) >= 0 ? "A program is already running here." : kind >= 2 && s.alignment(i) == AL_TREATY ? "The CIA won't arm rebels against a treaty ally." : war ? "Not while at war." : null;
            cv.add(Map.of("kind", kind, "name", CV2_NAME[kind], "desc", CV2_DESC[kind], "odds", r(100 * e.covertOdds(s, c, i, kind)), "leak", r(100 * e.covertLeakOdds(c, kind)),
                    "weeks", CV2_WEEKS[kind], "ready", no == null && open, "reason", no == null ? "" : no));
        }
        v.put("covert", cv);
        if (s.covertProgram(i) >= 0) v.put("program", CV2_NAME[s.covertProgram(i)] + ": results due in " + Math.max(0, s.covertDue(i) - s.week()) + " weeks.");
        // force
        List<Object> force = new ArrayList<>();
        for (int kind = 0; kind <= WK_INVASION; kind++) {
            String no = e.cannotWar(s, i, kind);
            force.add(Map.of("kind", kind, "name", WK_NAME[kind], "desc", WK_DESC[kind], "troops", r(e.need(i, kind) * 1000), "expect", e.expect(s, i, kind),
                    "ready", no == null && open, "reason", no == null ? "" : no));
        }
        v.put("force", force);
        v.put("authorized", e.authorized(s, i));
        v.put("authorizeOdds", r(100 * e.authorizeOdds(s, c, i)));
        // objectives
        List<Object> obs = new ArrayList<>();
        for (ForeignState.Objective o : s.objectives()) {
            if (o.country() != i) continue;
            ForeignData.ObjectiveDef def = e.definition(o);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id()); m.put("name", e.fill(def.name(), i)); m.put("why", e.fill(def.why(), i));
            m.put("steps", def.steps().size()); m.put("step", o.step()); m.put("status", o.status() == 1 ? "Achieved" : o.status() == 2 ? "Stalled" : "Open");
            if (o.status() != 1) {
                ForeignData.Step st = def.steps().get(Math.min(o.step(), def.steps().size() - 1));
                m.put("next", e.fill(st.label(), i) + (st.cost().isBlank() ? "" : " (costs something)"));
                String no = e.cannot(s, o);
                m.put("reason", no == null ? "" : no);
                double q = e.stepOdds(s, c, o);
                m.put("odds", q >= 0.99 ? "Certain to work: everything it needs is in place" : "About " + r(100 * q) + "% to work");
                m.put("ready", no == null && open);
            }
            obs.add(m);
        }
        v.put("objectives", obs);
        return v;
    }

    // ---- the questions on the desk ----

    static List<Object> decisions(GameEngine g) {
        List<Object> out = new ArrayList<>();
        if (!open(g)) return out;
        ForeignState s = g.foreign().get();
        for (ForeignState.Question q : s.questions()) {
            List<Object> options = new ArrayList<>();
            for (int i = 0; i < q.options().size(); i++) {
                String adv = i < q.advice().size() ? q.advice().get(i) : "";
                options.add(EngineApi.option(q.options().get(i), adv, i == q.recommended() && !q.kind().equals("info") ? "Advised" : null, null,
                        new Command.Foreign(new ForeignOrder.Answer(q.id(), i))));
            }
            String eyebrow = switch (q.kind()) {
                case "crisis" -> "The Situation Room";
                case "summit" -> "A summit";
                case "gsummit" -> "Summitry";
                case "warpowers" -> "Congress";
                default -> "The world";
            };
            Map<String, Object> m = EngineApi.decision("foreign", eyebrow, q.title(), q.body(), options);
            m.put("recommended", q.recommended());
            m.put("stop", q.kind().equals("crisis") || q.kind().equals("warpowers") ? WatchPlan.STOP_WORLD : -1);
            out.add(m);
        }
        return out;
    }

    static int count(GameEngine g) { return decisions(g).size(); }

    static String pct(double v) { return Fmt.format("%.0f%%", v * 100); }
}
