package game.mandate.web.api;

import game.mandate.core.data.Json;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.taiwan.TaiwanData;
import game.mandate.core.taiwan.TaiwanEngine;
import game.mandate.core.taiwan.TaiwanOrder;
import game.mandate.core.taiwan.TaiwanScenario;
import game.mandate.core.taiwan.TaiwanResult;
import game.mandate.core.taiwan.TaiwanState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Taiwan crisis room (docs/ISLAND_CHAIN.md), for the TypeScript client: the clock, both ladders, the warning picture,
 * the sea zones (the player's forces, and the enemy as the player's side sees it), Taiwan by sector, the directives, the
 * allies, the preparations, the economy, nuclear risk, the toll and the log. Inside the presidency the player is America;
 * the standalone scenario can be played as either side.
 */
public final class TaiwanViews {
    private TaiwanViews() {}

    // ---- Island Chain on its own: a crisis without a presidency (the start screen) ----------------------------------------

    /** The standalone crisis: its engine (loaded once), its state and its random streams. */
    static TaiwanEngine icEngine;
    static TaiwanState ic;
    static DeterministicRandomManager icRng;

    /** The standalone scenario's requests: start, view, order, turn, end. */
    public static Map<String, Object> islandChain(Map<String, Object> req) {
        String action = (String) req.get("action");
        switch (action == null ? "" : action) {
            case "start" -> {
                int year = req.get("year") == null ? 2030 : Json.integer(req.get("year"));
                int preset = req.get("preset") == null ? 0 : Json.integer(req.get("preset"));
                String side = "PRC".equals(req.get("side")) ? "PRC" : "US";
                long seed = req.get("seed") == null ? 20291L * year + preset : (long) Json.num(req.get("seed"));
                if (icEngine == null) icEngine = new TaiwanEngine();
                icRng = new DeterministicRandomManager(seed);
                ic = icEngine.newCrisis(LocalDate.of(year, 3, 1), TaiwanScenario.standalone(year, preset, side), icRng);
                return ok(Map.of());
            }
            case "view" -> { need(); return room(icEngine, ic, true); }
            case "order" -> {
                need();
                icEngine.apply(ic, ic.date(), TaiwanOrder.fromJson(Json.object(req.get("order"))), icRng);
                return ok(Map.of());
            }
            case "turn" -> {
                need();
                if (ic.over()) throw new IllegalArgumentException("The crisis is over");
                icEngine.turn(ic, ic.date(), icRng);
                return ok(Map.of());
            }
            case "end" -> { ic = null; icRng = null; return ok(Map.of()); }
            default -> throw new IllegalArgumentException("No Island Chain action called " + action);
        }
    }

    private static void need() { if (ic == null) throw new IllegalStateException("No Island Chain crisis is under way"); }

    private static Map<String, Object> ok(Map<String, Object> m) {
        Map<String, Object> r = new LinkedHashMap<>(m);
        r.put("ok", true);
        return r;
    }

    static final String[] LEVEL = {"Hostile", "Leans to China", "Neutral", "Sympathetic", "Grants access", "Grants basing", "At war"};
    static final String[] LOSS_LABEL = {"American", "Chinese", "Taiwanese", "Japanese", "Korean", "Philippine", "Australian", "British", "French", "Russian"};

    /** The Oval Office's summary while the crisis lasts. */
    public static Map<String, Object> summary(GameEngine g) {
        Map<String, Object> m = new LinkedHashMap<>();
        TaiwanState s = g.taiwan().orElse(null);
        if (s == null || s.over()) return null;
        m.put("day", s.day());
        m.put("phase", s.phase());
        m.put("hourOfDay", s.hourOfDay());
        m.put("turnHours", s.turnHours());
        m.put("chinaRung", s.chinaRung());
        m.put("chinaRungName", TaiwanState.RED_RUNG[s.chinaRung()]);
        m.put("usRung", s.usRung());
        m.put("warning", TaiwanState.WARNING[Math.max(0, Math.min(4, s.warningLevel(TaiwanState.US)))]);
        m.put("chance", round2(s.warning(TaiwanState.US)));
        m.put("electricity", round2(s.electricity()));
        m.put("killed", s.usKilled());
        m.put("territory", Math.round(s.territoryKm2()));
        m.put("outcome", s.outcome());
        return m;
    }

    /** The room inside the presidency: the player is America. */
    public static Map<String, Object> room(GameEngine g) {
        Map<String, Object> m;
        TaiwanState s = g.taiwan().orElse(null);
        if (s == null) {
            m = EngineApi.ok();
            m.put("date", g.date().toString());
            m.put("open", false);
            m.put("reason", "There is no crisis over Taiwan.");
            return m;
        }
        m = room(g.taiwanEngine(), s, false);
        m.put("date", g.date().toString());
        m.put("ok", true);
        return m;
    }

    /** The room for a crisis state, in the presidency or standalone. */
    public static Map<String, Object> room(TaiwanEngine eng, TaiwanState s, boolean standalone) {
        TaiwanData d = eng.data();
        int me = s.playerSide(), foe = 1 - me;
        boolean us = me == TaiwanState.US;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", s.date().toString());
        m.put("open", true);
        m.put("active", !s.over());
        m.put("side", s.side());
        m.put("standalone", standalone);
        m.put("phase", s.phase());
        m.put("day", s.day());
        m.put("hourOfDay", s.hourOfDay());
        m.put("warDays", round2(s.warDays()));
        m.put("turnHours", s.turnHours());
        m.put("year", s.year());
        m.put("preset", s.preset());
        m.put("commitment", s.commitment());
        m.put("message", s.message());
        m.put("outcome", s.outcome());
        m.put("endReason", s.endReason());
        TaiwanResult.Status st = s.over() ? TaiwanResult.status(s.outcomeCode()) : null;
        m.put("status", st == null ? null : st.name());
        m.put("score", s.over() ? round1(s.score(me)) : null);
        m.put("grade", s.over() ? TaiwanResult.grade(s.score(me)) : null);
        m.put("rungs", Map.of("us", List.of(TaiwanState.BLUE_RUNG), "prc", List.of(TaiwanState.RED_RUNG)));
        m.put("rung", Map.of("us", s.usRung(), "prc", s.chinaRung()));
        m.put("maxRung", us ? switch (s.commitment()) { case "STAND_ASIDE" -> 0; case "ARM_AND_SANCTION" -> 1; default -> 6; } : 6);
        m.put("nuclear", Map.of("risk", Math.round(s.nuclearRisk()), "crisis", s.nuclearCrisis(), "us", s.nuclearAlert(TaiwanState.US),
                "prc", s.nuclearAlert(TaiwanState.PRC), "postures", List.of(TaiwanState.ALERT)));
        m.put("warning", warning(s));
        m.put("china", china(s, us));
        m.put("taiwan", taiwan(s));
        m.put("sectors", sectors(s, d));
        m.put("zones", zones(s, d, me, foe));
        m.put("directives", directives(s));
        m.put("allies", allies(s, d));
        m.put("capital", Math.round(s.diplomaticCapital(me)));
        m.put("programmes", programmes(s, d, me));
        m.put("economy", economy(s, standalone));
        m.put("losses", losses(s));
        m.put("offer", s.offerFrom() < 0 ? null : Map.of("from", s.offerFrom() == TaiwanState.US ? "US" : "PRC", "terms", s.offerTerms()));
        List<String> sit = new ArrayList<>(s.sitrep());
        java.util.Collections.reverse(sit);
        m.put("sitrep", sit);
        List<String> log = new ArrayList<>();
        List<String> l = s.log();
        for (int i = l.size() - 1; i >= 0 && log.size() < 40; i--) log.add(l.get(i));
        m.put("log", log);
        m.put("ok", true);
        return m;
    }

    static Map<String, Object> warning(TaiwanState s) {
        Map<String, Object> w = new LinkedHashMap<>();
        int lvl = Math.max(0, Math.min(4, s.warningLevel(TaiwanState.US)));
        w.put("level", lvl);
        w.put("name", TaiwanState.WARNING[lvl]);
        w.put("levels", List.of(TaiwanState.WARNING));
        w.put("chance", round2(s.warning(TaiwanState.US)));
        double[] p = s.planEstimate();
        List<Map<String, Object>> plans = new ArrayList<>();
        for (int i = 0; i < p.length && i < TaiwanData.PLANS.length; i++) plans.add(Map.of("plan", TaiwanData.PLANS[i], "share", round2(p[i])));
        w.put("plans", plans);
        List<Map<String, Object>> ind = new ArrayList<>();
        List<TaiwanState.Indicator> xs = s.indicators(TaiwanState.US);
        for (int i = xs.size() - 1; i >= 0 && ind.size() < 8; i--) {
            TaiwanState.Indicator x = xs.get(i);
            ind.add(Map.of("day", x.day(), "text", x.text(), "confidence", round2(x.confidence()), "cover", x.exerciseCover()));
        }
        w.put("indicators", ind);
        return w;
    }

    static Map<String, Object> china(TaiwanState s, boolean us) {
        Map<String, Object> c = new LinkedHashMap<>();
        boolean known = !us || s.atWar() || s.over();
        c.put("plan", known ? s.plan() : "");
        c.put("known", known && !s.plan().isEmpty());
        c.put("dDay", us ? -1 : s.dDay());
        c.put("stoodDown", s.chinaStoodDown());
        c.put("deterrence", round1(s.deterrenceAsSeen()));
        c.put("region", us ? 0 : s.region());
        c.put("lethality", us ? 1 : s.lethality());
        c.put("options", Map.of("strikeUS", !us && s.strikeUSAtHHour(), "strikeJapan", !us && s.strikeJapanAtHHour(), "cyber", !us && s.cyberAtHHour(),
                "decapitation", !us && s.decapitation(), "penghuFirst", !us && s.penghuFirst()));
        return c;
    }

    static Map<String, Object> taiwan(TaiwanState s) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("electricity", round2(s.electricity()));
        t.put("chips", round2(s.chips()));
        t.put("will", round1(s.will()));
        t.put("command", round2(s.command()));
        t.put("throughput", round2(s.throughput()));
        t.put("blockade", s.blockadeLevel());
        t.put("blockadeName", cap(TaiwanState.BLOCKADE[Math.max(0, Math.min(4, s.blockadeLevel()))]));
        t.put("lng", round1(s.lngStock()));
        t.put("coal", Math.round(s.coalStock()));
        t.put("oil", round1(s.oilStock()));
        t.put("rationing", s.rationing());
        t.put("mobilization", round2(s.mobilization()));
        t.put("territory", Math.round(s.territoryKm2()));
        t.put("plaAshore", round1(s.plaAshore()));
        t.put("rocReady", round1(s.rocReady()));
        t.put("civilians", Math.round(s.civiliansKilled()));
        t.put("asked", Map.of("mobilise", s.taiwanAsked("mobilise"), "ration", s.taiwanAsked("ration"), "disperse", s.taiwanAsked("disperse")));
        return t;
    }

    static List<Map<String, Object>> sectors(TaiwanState s, TaiwanData d) {
        List<Map<String, Object>> l = new ArrayList<>();
        List<TaiwanState.SectorState> ss = s.sectors();
        for (int i = 0; i < ss.size(); i++) {
            TaiwanData.Sector sd = d.sectors().get(i);
            TaiwanState.SectorState x = ss.get(i);
            l.add(Map.of("name", sd.name(), "terrain", TaiwanData.TERRAIN[sd.terrain()], "prc", round2(x.control()), "contested", x.contested(), "lodgment", x.lodgment()));
        }
        return l;
    }

    static List<Map<String, Object>> zones(TaiwanState s, TaiwanData d, int me, int foe) {
        int nz = d.zones().size();
        int[] ships = new int[nz], groups = new int[nz], subs = new int[nz], carriers = new int[nz], amph = new int[nz];
        int[] eShips = new int[nz], eSubs = new int[nz], eCarriers = new int[nz], eAmph = new int[nz], eContacts = new int[nz], eKnown = new int[nz], eAge = new int[nz];
        List<List<String>> names = new ArrayList<>();
        for (int z = 0; z < nz; z++) names.add(new ArrayList<>());
        for (TaiwanState.Group g : s.groups()) {
            if (g.side() != me || g.dead() || g.gone() || g.hub() >= 0 || g.zone() < 0 || g.zone() >= nz) continue;
            int z = g.zone();
            groups[z]++;
            for (TaiwanState.Ship sh : g.ships()) {
                ships[z] += sh.n();
                TaiwanData.ShipClass c = d.shipClasses().get(sh.cls());
                if (c.carrier()) carriers[z] += sh.n();
                if (c.amphib()) amph[z] += sh.n();
            }
            if (names.get(z).size() < 6) names.get(z).add(g.name());
        }
        for (TaiwanState.Sub x : s.subs()) if (x.side() == me && !x.lost() && x.hub() < 0 && x.zone() >= 0 && x.zone() < nz) subs[x.zone()]++;
        Map<Integer, TaiwanState.Group> enemyGroups = new LinkedHashMap<>();
        for (TaiwanState.Group g : s.groups()) if (g.side() == foe) enemyGroups.put(g.id(), g);
        for (TaiwanState.Contact c : s.contacts(me)) {
            int z = c.zone();
            if (z < 0 || z >= nz) continue;
            if (c.kind() == TaiwanState.TK_SUB) { eSubs[z]++; eContacts[z]++; if (c.ident() > 0) eKnown[z]++; }
            else if (c.kind() == TaiwanState.TK_GROUP) {
                eContacts[z]++;
                eAge[z] = Math.max(eAge[z], (int) (s.hour() - c.seen()));
                TaiwanState.Group g = enemyGroups.get(c.unit());
                if (c.ident() == 0 || g == null) continue;
                eKnown[z]++;
                if (c.decoy() && c.ident() < 2) { eAmph[z] += 6; continue; }
                for (TaiwanState.Ship sh : g.ships()) {
                    eShips[z] += sh.n();
                    TaiwanData.ShipClass k = d.shipClasses().get(sh.cls());
                    if (k.carrier()) eCarriers[z] += sh.n();
                    if (k.amphib()) eAmph[z] += sh.n();
                }
            }
        }
        List<Map<String, Object>> l = new ArrayList<>();
        for (int z = 0; z < nz; z++) {
            TaiwanData.Zone zd = d.zones().get(z);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", zd.id());
            m.put("name", zd.name());
            m.put("lon", zd.lon());
            m.put("lat", zd.lat());
            m.put("km", zd.r());
            m.put("strait", zd.strait());
            m.put("near", zd.blockade() || zd.taiwanAir());
            m.put("own", Map.of("ships", ships[z], "groups", groups[z], "subs", subs[z], "carriers", carriers[z], "amphibious", amph[z], "names", names.get(z)));
            m.put("enemy", Map.of("ships", eShips[z], "subs", eSubs[z], "carriers", eCarriers[z], "amphibious", eAmph[z], "contacts", eContacts[z], "known", eKnown[z], "age", eAge[z]));
            m.put("mines", round1(s.mines(foe, z)));
            l.add(m);
        }
        return l;
    }

    static Map<String, Object> directives(TaiwanState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mainland", s.mainlandStrikes());
        m.put("fleet", s.fleetPosture());
        m.put("subs", s.submarineFocus());
        m.put("munitions", s.munitionsPolicy());
        List<Double> air = new ArrayList<>();
        for (double x : s.airPriority()) air.add(round2(x));
        m.put("air", air);
        m.put("convoys", s.convoys());
        m.put("held", s.heldDirectives());
        m.put("preempt", s.preemptOrdered());
        return m;
    }

    static List<Map<String, Object>> allies(TaiwanState s, TaiwanData d) {
        List<Map<String, Object>> l = new ArrayList<>();
        List<TaiwanState.Country> cs = s.countries();
        for (int i = 0; i < cs.size(); i++) {
            TaiwanData.Country cd = d.countries().get(i);
            TaiwanState.Country c = cs.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", cd.id());
            m.put("name", cd.name());
            m.put("level", c.level());
            m.put("levelName", LEVEL[Math.max(0, Math.min(6, c.level()))]);
            m.put("basing", c.basing());
            m.put("overflight", c.overflight());
            m.put("belligerent", c.belligerent());
            m.put("attacked", c.attacked());
            m.put("sanctions", c.sanctions());
            m.put("role", c.role());
            m.put("aligned", cd.aligned());
            l.add(m);
        }
        return l;
    }

    static List<Map<String, Object>> programmes(TaiwanState s, TaiwanData d, int side) {
        List<Map<String, Object>> l = new ArrayList<>();
        List<TaiwanData.Programme> ps = d.programmes(side);
        for (int i = 0; i < ps.size(); i++) {
            TaiwanData.Programme p = ps.get(i);
            l.add(Map.of("id", p.id(), "name", p.name(), "pct", round1(s.programme(side, i)), "on", s.programmeOn(side, i), "days", (int) Math.ceil(p.days())));
        }
        return l;
    }

    static Map<String, Object> economy(TaiwanState s, boolean standalone) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("brent", round1(s.brent()));
        e.put("lng", round1(s.lngPrice()));
        e.put("warRisk", round2(s.warRiskInsurance()));
        e.put("equity", round2(s.usEquity()));
        e.put("gapUS", round3(s.gap(TaiwanState.E_USA)));
        e.put("gapWorld", round3(s.gap(TaiwanState.E_WORLD)));
        e.put("chipShock", round2(s.chipShock()));
        e.put("sanctions", s.usSanctions());
        e.put("measures", s.measuresInForce());
        e.put("support", round1(s.usSupport()));
        e.put("approval", standalone ? round1(s.approval()) : null);
        e.put("congress", standalone ? round1(s.congress()) : null);
        return e;
    }

    static List<Map<String, Object>> losses(TaiwanState s) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (int i = 0; i < TaiwanState.NATIONS.length; i++) {
            TaiwanState.Loss x = s.losses(TaiwanState.NATIONS[i]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nation", TaiwanState.NATIONS[i]);
            m.put("label", LOSS_LABEL[i]);
            m.put("killed", Math.round(x.killed()));
            m.put("ships", x.ships());
            m.put("carriers", x.carriers());
            m.put("amphibious", x.amphibious());
            m.put("submarines", x.submarines());
            m.put("aircraft", x.aircraft());
            l.add(m);
        }
        return l;
    }

    static String cap(String x) { return x.isEmpty() ? x : Character.toUpperCase(x.charAt(0)) + x.substring(1); }
    static double round1(double v) { return Math.round(v * 10) / 10.0; }
    static double round2(double v) { return Math.round(v * 100) / 100.0; }
    static double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
