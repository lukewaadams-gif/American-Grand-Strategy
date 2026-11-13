package game.mandate.core.taiwan;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Fmt;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.taiwan.TaiwanData.Oob;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Country;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Sub;
import game.mandate.core.taiwan.TaiwanState.Troop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The Taiwan crisis and war (docs/ISLAND_CHAIN.md), a reduced port of the Island Chain wargame. Crisis days, then war
 * turns of 1, 6 and 12 hours; inside, an hour at a time (air, missiles, ships, submarines, landings, the ground war every
 * six hours, shipping, intelligence, repairs) and a daily update at midnight (programmes, production, Taiwan, the economy,
 * politics, the allies, warning, nuclear risk, the end). The side the player does not command is the computer's; the
 * player's side follows its staff's playbook except where the player has given orders.
 */
public final class TaiwanEngine {
    public record Outcome(List<Effect> effects, List<News> news, List<String> log) {
        public static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); }
    }

    /** The source's ten outcomes (spec 8.9), by code. */
    public static final String[] OUTCOMES = {"Deterrence holds", "Taiwan defended", "Pyrrhic defence of Taiwan", "Stalemate", "Partial PRC success",
            "PRC victory", "Taiwan capitulates", "Nuclear catastrophe", "Negotiated ceasefire", "China stands down"};

    private final TaiwanData data;

    public TaiwanEngine() { this(TaiwanData.load()); }

    public TaiwanEngine(TaiwanData data) { this.data = data; }

    public TaiwanData data() { return data; }

    static int planIndex(String plan) {
        for (int i = 0; i < TaiwanData.PLANS.length; i++) if (TaiwanData.PLANS[i].equals(plan)) return i;
        return -1;
    }

    /** The next turn's length in hours (spec 1.3): 24 in the crisis, cut to end just after H-hour; then 1, 6 and 12. */
    static int turnLength(TaiwanState s) {
        if (s.over()) return 0;
        if (s.phase.equals("crisis")) {
            int t = 24 - s.hour % 24;
            if (s.launchHour >= s.hour && s.launchHour < s.hour + t) t = s.launchHour + 1 - s.hour;
            if (s.blueStrikeHour >= s.hour && s.blueStrikeHour < s.hour + t) t = Math.min(t, s.blueStrikeHour + 1 - s.hour);
            return Math.max(1, t);
        }
        int w = s.hour - s.dHour;
        return w < 24 ? 1 : w < 168 ? 6 : 12;
    }

    // =================================================================================================================
    //  A new crisis
    // =================================================================================================================

    public TaiwanState newCrisis(LocalDate date, TaiwanScenario sc, DeterministicRandomManager rng) {
        TaiwanState s = new TaiwanState();
        s.start = date;
        s.year = sc.year(); s.forcesYear = sc.year(); s.preset = sc.preset(); s.playerSide = "PRC".equals(sc.side()) ? PRC : US;
        s.fixedPlan = sc.plan(); s.deterrenceIn = sc.deterrence(); s.munitionsIn = sc.munitions(); s.japanBasingIn = sc.japanBasing();
        s.warningMonths = Math.max(0, sc.warningMonths()); s.commitment = sc.commitment().name(); s.competence = sc.defenseCompetence();
        s.assumptions = sc.assumptions();
        s.difficultyUS = s.competence < 40 ? 0 : s.competence > 70 ? 2 : 1;
        s.difficultyPRC = 1;
        presets(s);
        assumptions(s, sc.assumptions());
        s.planSeed = rng.nextLong("taiwan.setup");
        Ctx c = new Ctx(s, data, rng, Outcome.empty(), date);
        int nm = data.munitions.size();
        for (int i = 0; i < data.bases.size(); i++) s.bases.add(new TaiwanState.BaseState());
        for (int i = 0; i < data.sectors.size(); i++) { TaiwanState.SectorState ss = new TaiwanState.SectorState(); ss.km2 = data.sectors.get(i).km2(); s.sectors.add(ss); }
        for (int i = 0; i < data.facilities.size(); i++) s.facilities.add(new TaiwanState.FacilityState());
        s.pool = new double[TaiwanData.POOLS.length][nm];
        s.prod = new double[TaiwanData.POOLS.length][nm];
        s.expended = new double[2][nm];
        s.mines = new double[2][data.zones.size()];
        s.progPct = new double[][]{new double[data.programmes(US).size()], new double[data.programmes(PRC).size()]};
        s.progOn = new boolean[][]{new boolean[data.programmes(US).size()], new boolean[data.programmes(PRC).size()]};
        for (String n : NATIONS) s.losses.put(n, new Loss());
        for (TaiwanData.Country d : data.countries) {
            Country k = new Country();
            k.stance = d.stance0(); k.fear = d.fear(); k.trust = d.trust();
            s.countries.add(k);
        }
        Country jp = TaiwanWorld.country(c, "JPN");
        jp.stance = s.japanBasingIn ? 63 : 20; jp.basing = s.japanBasingIn; jp.overflight = true; jp.civilAirports = s.civilAirportsSet;
        jp.defensiveOnly = s.jsdfDefensive;
        if (s.japanEntry == 2) jp.stance = 82;
        Country au = TaiwanWorld.country(c, "AUS");
        au.stance = 62; au.basing = true; au.overflight = true;
        Country ph = TaiwanWorld.country(c, "PHL");
        ph.overflight = true;
        if (s.phlBasingSet) { ph.stance = 62; ph.basing = true; }
        TaiwanWorld.country(c, "KOR").overflight = true;
        for (String[] r : new String[][]{{"JPN", "3"}, {"KOR", "1"}, {"PHL", "3"}, {"AUS", "3"}, {"GBR", "3"}}) TaiwanWorld.country(c, r[0]).role = Integer.parseInt(r[1]);
        for (Country k : s.countries) k.reported = k.stance;
        build(c, "*");
        if (s.extraHAS) for (int i = 0; i < data.bases.size(); i++) {
            TaiwanData.Base b = data.bases.get(i);
            if (b.cc().equals("JPN") && b.has(TaiwanData.F_AIR) && !b.has(TaiwanData.F_CIVIL)) s.bases.get(i).hasLost = -20;
        }
        s.poolStart = new double[nm];
        for (int m = 0; m < nm; m++) s.poolStart[m] = s.pool[TaiwanData.P_US_TH][m] + s.pool[TaiwanData.P_US_GL][m];
        s.will = 64 + (s.preset == 1 ? 4 : 0) - (s.preset == 2 ? 6 : 0);
        s.deterrenceG = 0.4 * (s.deterrenceIn - 50);
        if (s.warningMonths > 0) warning(c);
        s.deterSeen = TaiwanAI.deterrence(c);
        c.log(date.getYear() + ". Tension across the Taiwan Strait is at its highest in decades.", true);
        return s;
    }

    static void presets(TaiwanState s) {
        s.japanEntry = 1; s.jsdfDefensive = false; s.civilAirportsSet = false; s.phlBasingSet = false; s.taiwanDelay = false; s.jassmMaritime = true;
        s.shipDefensePoor = false; s.usLate = false; s.subsWithheld = false; s.simultaneous = false; s.extraHAS = false; s.mainlandAllowed = true;
        s.prcMissiles = 1; s.plaAmphib = 1; s.twGround = 1; s.strikeJapanEnabled = true;
        if (s.preset == 1) { s.civilAirportsSet = true; s.extraHAS = true; s.japanEntry = 2; s.plaAmphib = 0.7; s.prcMissiles = 0.85; s.phlBasingSet = true; }
        else if (s.preset == 2) { s.jassmMaritime = false; s.taiwanDelay = true; s.twGround = 0.75; s.usLate = true; s.prcMissiles = 1.25; s.subsWithheld = true; s.simultaneous = true; }
    }

    /** Custom toggles over the preset: "key=value" pairs joined with ';' (docs/ISLAND_CHAIN.md lists the keys). */
    static void assumptions(TaiwanState s, String text) {
        if (text == null || text.isBlank()) return;
        for (String part : text.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("An assumption needs key=value: " + p);
            String k = p.substring(0, eq).trim(), v = p.substring(eq + 1).trim();
            boolean b = v.equals("true") || v.equals("1") || v.equals("yes");
            switch (k) {
                case "japanEntry" -> s.japanEntry = TMath.clampi(Integer.parseInt(v), 0, 2);
                case "jsdfDefensiveOnly" -> s.jsdfDefensive = b;
                case "civilAirports" -> s.civilAirportsSet = b;
                case "philippinesBasing" -> s.phlBasingSet = b;
                case "taiwanDelay" -> s.taiwanDelay = b;
                case "jassmMaritime" -> s.jassmMaritime = b;
                case "shipDefensePoor" -> s.shipDefensePoor = b;
                case "usMobilizationLate" -> s.usLate = b;
                case "usSubsWithheld" -> s.subsWithheld = b;
                case "simultaneousCrisis" -> s.simultaneous = b;
                case "extraShelters" -> s.extraHAS = b;
                case "mainlandAllowed" -> s.mainlandAllowed = b;
                case "strikeJapan" -> s.strikeJapanEnabled = b;
                case "prcMissiles" -> s.prcMissiles = TMath.clamp(Double.parseDouble(v), 0.5, 2);
                case "plaAmphib" -> s.plaAmphib = TMath.clamp(Double.parseDouble(v), 0.3, 1.5);
                case "taiwanGround" -> s.twGround = TMath.clamp(Double.parseDouble(v), 0.5, 1.5);
                case "forcesYear" -> s.forcesYear = TMath.clampi(Integer.parseInt(v), 2027, 2036);
                default -> throw new IllegalArgumentException("No such assumption: " + k);
            }
        }
    }

    /** Months of warning: the US programmes a warned administration would have started are that far along, and the intelligence
     *  picture already shows the build-up. */
    static void warning(Ctx c) {
        TaiwanState s = c.s;
        String[] early = {"readiness", "subs", "bombers", "munitions", "arms", "sealift", "declassify"};
        for (String id : early) {
            if (s.commitment.equals("STAND_ASIDE") && !id.equals("declassify")) continue;
            int i = TaiwanWorld.progIndex(c, US, id);
            double pct = Math.min(100, s.warningMonths * 30 * 100.0 / data(c).programmes(US).get(i).days());
            TaiwanWorld.start(c, US, id);
            s.progPct[US][i] = pct;
            if (pct >= 100) TaiwanWorld.done(c, US, id);
        }
        for (int k = 0; k < Math.min(8, 2 * s.warningMonths); k++) {
            TaiwanState.Indicator x = new TaiwanState.Indicator();
            x.prog = -1; x.day = 1; x.conf = 0.6; x.strength = 3;
            x.text = "Months of tension: PLA activity opposite Taiwan has been rising";
            s.indicatorsUS.add(x);
        }
    }

    static TaiwanData data(Ctx c) { return c.d; }

    // ---- the order of battle (spec 3.3, projected to the year as spec 10.2 proposes) ---------------------------------

    static boolean cond(TaiwanState s, String cond) {
        return switch (cond) {
            case "*" -> true;
            case "p0" -> s.preset == 0;
            case "p1" -> s.preset == 1;
            case "p2" -> s.preset == 2;
            case "!p0" -> s.preset != 0;
            case "!p1" -> s.preset != 1;
            case "!p2" -> s.preset != 2;
            case "withheld" -> s.subsWithheld;
            case "!withheld" -> !s.subsWithheld;
            default -> false;
        };
    }

    static boolean inYear(TaiwanState s, Oob o, int fromCol) {
        int from = o.i(fromCol), to = o.i(fromCol + 1);
        return s.forcesYear >= from && s.forcesYear <= to;
    }

    /** Builds the rows whose condition is {@code which} ("*": everything but the ro-ro programme's; "roro": those). */
    static void build(Ctx c, String which) {
        TaiwanState s = c.s;
        TaiwanData d = c.d;
        boolean roro = which.equals("roro");
        double trend = 0, trendMax = 0;
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Group g : s.groups) groups.put(g.key, g);
        for (Oob o : d.oob) {
            String kind = o.kind();
            switch (kind) {
                case "trend" -> { if (o.s(1).equals("tw_quality")) { trend = o.d(2); trendMax = o.d(3); } }
                case "group" -> {
                    if (!inYear(s, o, 7) || !(roro ? o.s(9).equals("roro") : cond(s, o.s(9)))) continue;
                    Group g = new Group();
                    g.id = s.nextId++; g.side = o.s(1).equals("PRC") ? PRC : US; g.nat = o.s(2); g.key = o.s(3); g.name = o.s(4); g.tag = o.s(6);
                    g.mag = new int[d.munitions.size()];
                    place(c, g, o.s(5));
                    if (g.tag.equals("roro")) g.kn = 18;
                    s.groups.add(g);
                    groups.put(g.key, g);
                }
                case "ship" -> {
                    Group g = groups.get(o.s(1));
                    if (g == null || !inYear(s, o, 6) || !(roro ? o.s(8).equals("roro") : cond(s, o.s(8)))) continue;
                    int cls = d.need("sh:" + o.s(2));
                    int n = TaiwanData.projected(o.d(3), o.d(4), o.d(5), o.i(6), s.forcesYear);
                    String role = d.ships.get(cls).role();
                    if (g.side == PRC && !roro && (role.equals("amphib") || role.equals("lst"))) n = (int) Math.floor(n * s.plaAmphib + 0.5);
                    TaiwanSea.addShips(c, g, cls, n);
                }
                case "wing" -> {
                    if (roro) continue;
                    Group g = groups.get(o.s(1));
                    if (g == null) continue;
                    String nm = o.s(2);
                    wing(c, g, nm + " strike fighters A", "FA18", 22);
                    wing(c, g, nm + " strike fighters B", "FA18", 22);
                    wing(c, g, nm + " fighters (F-35C)", "F35C", 10);
                    wing(c, g, nm + " early warning", "E2D", 5);
                    wing(c, g, nm + " electronic attack", "EA18", 5);
                }
                case "air" -> {
                    if (roro || !inYear(s, o, 11) || !cond(s, o.s(13))) continue;
                    int count = TaiwanData.projected(o.d(8), o.d(9), o.d(10), o.i(11), s.forcesYear);
                    String[] wheres = o.s(5).split("\\+");
                    for (int k = 0; k < count; k++) {
                        Squadron q = new Squadron();
                        q.id = s.nextId++; q.side = o.s(1).equals("PRC") ? PRC : US; q.nat = o.s(2);
                        q.key = count > 1 ? o.s(3) + "_" + (k + 1) : o.s(3);
                        q.name = count > 1 ? o.s(4) + " " + (k + 1) : o.s(4);
                        q.type = d.need("a:" + o.s(6));
                        q.n = q.n0 = o.i(7);
                        q.perSortie = Math.max(2, q.n / 2);
                        String w = wheres[k % wheres.length];
                        if (groups.containsKey(w)) { Group g = groups.get(w); q.carrier = g.id; g.wing.add(q.id); }
                        else if (w.startsWith("hub_")) { q.hub = d.need("h:" + w); q.dest = o.s(14).isEmpty() ? -1 : d.base(o.s(14)); }
                        else q.base = d.base(w);
                        s.squadrons.add(q);
                    }
                }
                case "sub" -> {
                    if (roro || !inYear(s, o, 10) || !cond(s, o.s(12))) continue;
                    int count = TaiwanData.projected(o.d(7), o.d(8), o.d(9), o.i(10), s.forcesYear);
                    for (int k = 0; k < count; k++) {
                        Sub x = new Sub();
                        x.id = s.nextId++; x.side = o.s(1).equals("PRC") ? PRC : US; x.nat = o.s(2);
                        x.key = o.s(3) + "_" + (k + 1);
                        x.name = o.s(4) + " " + (k + 1);
                        x.cls = d.need("su:" + o.s(6));
                        TaiwanData.SubClass sc = d.subs.get(x.cls);
                        x.torps = sc.torps(); x.missiles = sc.missiles();
                        x.home = d.base(o.s(13));
                        String w = o.s(5);
                        if (w.startsWith("hub_")) { x.hub = d.need("h:" + w); x.place = -1; }
                        else if (w.startsWith("b_")) { x.place = d.bases.get(d.base(w)).place(); x.mission = SM_PORT; x.home = d.base(w); }
                        else { int z = d.zone(w); x.place = z; x.zone = z; x.mission = SM_PATROL; }
                        s.subs.add(x);
                    }
                }
                case "land" -> {
                    if (roro || !inYear(s, o, 12) || !cond(s, o.s(14))) continue;
                    int count = TaiwanData.projected(o.d(8), o.d(9), o.d(10), o.i(12), s.forcesYear);
                    String[] wheres = o.s(5).split("\\+");
                    for (int k = 0; k < count; k++) {
                        String w = wheres[k % wheres.length];
                        Battery b = addBattery(c, o.s(1).equals("PRC") ? PRC : US, o.s(2), count > 1 ? o.s(3) + "_" + (k + 1) : o.s(3),
                                count > 1 ? o.s(4) + " " + (k + 1) : o.s(4), w.startsWith("hub_") ? -1 : d.base(w), d.need("l:" + o.s(6)), o.i(7), o.s(11).equals("true"));
                        if (w.startsWith("hub_")) b.hub = d.need("h:" + w);
                    }
                }
                case "troop" -> {
                    if (roro || !inYear(s, o, 11) || !cond(s, o.s(13))) continue;
                    int count = o.i(10);
                    String[] wheres = o.s(5).split("\\+");
                    double q = o.d(8);
                    if (o.s(2).equals("TWN")) q *= s.twGround * (1 + Math.min(trendMax, trend * Math.max(0, s.forcesYear - 2027)));
                    for (int k = 0; k < count; k++) {
                        Troop t = new Troop();
                        t.id = s.nextId++; t.side = o.s(1).equals("PRC") ? PRC : US; t.nat = o.s(2);
                        t.key = count > 1 ? o.s(3) + "_" + (k + 1) : o.s(3);
                        t.name = count > 1 ? o.s(4) + " " + (k + 1) : o.s(4);
                        t.type = TaiwanData.indexOf(TaiwanData.GTYPE, o.s(6));
                        t.bns = t.maxBns = o.d(7);
                        t.quality = q;
                        String w = wheres[k % wheres.length];
                        switch (o.s(9)) {
                            case "staging" -> { t.state = GS_STAGING; t.stage = d.base(w); }
                            case "reserve" -> {
                                t.state = GS_RESERVE;
                                if (w.startsWith("s_")) t.sector = d.sector(w); else t.stage = d.base(w);
                            }
                            case "embarked" -> { Group g = groups.get(w); t.state = GS_EMBARKED; t.ship = g.id; g.cargo.add(t.id); }
                            default -> { t.state = GS_READY; t.sector = d.sector(w); t.dug = 48; }
                        }
                        s.troops.add(t);
                    }
                }
                case "pool" -> {
                    if (roro || !cond(s, o.s(7))) continue;
                    int p = TaiwanEngine.poolIndex(o.s(1));
                    int m = d.munition(o.s(2));
                    double stock = o.d(3) + o.d(4) * Math.max(0, s.forcesYear - 2027);
                    if (o.d(5) > 0) stock = Math.min(stock, o.d(5));
                    String id = o.s(2);
                    if (p == TaiwanData.P_US_TH || p == TaiwanData.P_US_GL) stock *= s.munitionsIn;
                    if (p == TaiwanData.P_PRC && (id.equals("DF11") || id.equals("DF16") || id.equals("DF17") || id.equals("DF21C") || id.equals("DF21D")
                            || id.equals("DF26") || id.equals("CJ10"))) stock *= s.prcMissiles;
                    s.pool[p][m] += Math.floor(stock);
                    s.prod[p][m] = o.d(6);
                }
                default -> { }
            }
        }
        // the US forward submarines sail from their patrol zones; reserve units sit in their sectors
        for (Group g : s.groups) g.at = TaiwanSea.zoneOf(c, g);
        for (Sub x : s.subs) x.at = TaiwanSea.zoneOf(c, x);
    }

    static int poolIndex(String id) {
        for (int i = 0; i < TaiwanData.POOLS.length; i++) if (TaiwanData.POOLS[i].equals(id)) return i;
        throw new IllegalStateException("No pool " + id);
    }

    static void place(Ctx c, Group g, String where) {
        TaiwanData d = c.d;
        if (where.startsWith("hub_")) { g.hub = d.need("h:" + where); g.hubArrive = -1; g.mission = M_IDLE; return; }
        if (where.startsWith("b_")) { int b = d.base(where); g.port = b; g.homePort = b; g.place = d.bases.get(b).place(); g.mission = M_IDLE; return; }
        int z = d.zone(where);
        g.place = z; g.zone = z; g.mission = M_PATROL;
    }

    static void wing(Ctx c, Group g, String name, String type, int n) {
        TaiwanState s = c.s;
        Squadron q = new Squadron();
        q.id = s.nextId++; q.side = g.side; q.nat = g.nat; q.key = g.key + "_" + type.toLowerCase(Locale.ROOT) + "_" + (g.wing.size() + 1); q.name = name;
        q.type = c.d.need("a:" + type); q.n = q.n0 = n; q.perSortie = Math.max(2, n / 2); q.carrier = g.id;
        g.wing.add(q.id);
        s.squadrons.add(q);
    }

    static Battery addBattery(Ctx c, int side, String nat, String key, String name, int base, int sys, int launchers, boolean asbm) {
        TaiwanState s = c.s;
        Battery b = new Battery();
        b.id = s.nextId++; b.side = side; b.nat = nat; b.key = key; b.name = name; b.base = base; b.sys = sys;
        b.launchers = b.maxLaunchers = launchers;
        b.ready = launchers * c.d.land.get(sys).rounds();
        b.asbm = asbm;
        s.batteries.add(b);
        return b;
    }

    /** The ro-ro ferries, barge piers and lighters the requisition programme brings (spec 8.6). */
    static void buildRoro(Ctx c) { build(c, "roro"); c.log("Requisitioned ro-ro ferries, barge piers and lighters join the PLA's amphibious fleet.", true); }

    // =================================================================================================================
    //  A turn
    // =================================================================================================================

    /** Plays one turn of {@code s.turnHours()} hours: the staffs' directives and orders, then the hours, a day at midnight. */
    public Outcome turn(TaiwanState s, LocalDate date, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        if (s.over()) return out;
        Ctx c = new Ctx(s, data, rng, out, date);
        s.sitrep.clear();
        s.message = null;
        TaiwanAir.coverage(c);
        int other = 1 - s.playerSide;
        directives(c, s.playerSide, true);
        directives(c, other, false);
        TaiwanAI.staff(c);
        int hours = turnLength(s);
        String phase = s.phase;
        for (int h = 0; h < hours && !s.over(); h++) {
            tick(c);
            if (!s.phase.equals(phase)) break;
        }
        s.turns++;
        report(c);
        return out;
    }

    /** A side's directives for the turn: the computer's whole playbook, or the player's staff where the player has not ruled. */
    void directives(Ctx c, int side, boolean player) {
        TaiwanState s = c.s;
        if (s.over()) return;
        boolean proposals = !player || s.autoplay;
        for (TaiwanOrder o : TaiwanAI.playbook(c, side, proposals)) {
            if (player && heldKey(o) != null && s.held.contains(heldKey(o))) continue;
            try { applyOrder(c, side, o, false); } catch (IllegalArgumentException e) { /* the staff's request is refused: the next one */ }
            if (s.over()) return;
        }
        if (s.atWar()) {
            if (side == US && s.hour % 24 >= 9) s.aiDay0 = s.day();
            if (side == PRC && s.hour % 24 >= 5) s.aiDay1 = s.day();
            if (s.warHours() >= 24 * 5 && s.hour % 24 >= 12 && proposals) { if (side == US) s.peaceDay = s.day(); else s.peaceDayPRC = s.day(); }
        }
    }

    void tick(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        if (s.phase.equals("crisis")) {
            if (s.launchHour >= 0 && s.hour >= s.launchHour) TaiwanWorld.startWar(c, PRC);
            else if (s.blueStrikeHour >= 0 && s.hour >= s.blueStrikeHour) TaiwanWorld.startWar(c, US);
        }
        TaiwanSea.arrivals(c);
        TaiwanSea.move(c);
        TaiwanAir.hour(c);
        TaiwanFire.batteries(c);
        TaiwanSea.navalFires(c);
        TaiwanSea.submarines(c);
        TaiwanLand.hour(c);
        if (s.hour % 6 == 0) TaiwanLand.step(c);
        TaiwanSea.shipping(c);
        TaiwanIntel.hour(c);
        TaiwanSea.repairs(c);
        s.hour++;
        if (s.hour % 24 == 0) TaiwanWorld.daily(c);
    }

    /** What the presidency hears after a turn: Americans killed (the world counts them). */
    void report(Ctx c) {
        TaiwanState s = c.s;
        double killed = c.loss("USA").killed;
        int n = (int) Math.floor(killed - s.reportedKia);
        if (n >= 1) { c.effect(new Effect.WorldShift("kia", n)); s.reportedKia += n; }
    }

    // =================================================================================================================
    //  Orders
    // =================================================================================================================

    /** Applies the player's order; throws IllegalArgumentException with a reason a player can read when it is refused. */
    public Outcome apply(TaiwanState s, LocalDate date, TaiwanOrder o, DeterministicRandomManager rng) {
        if (s.over()) throw new IllegalArgumentException("The crisis is over");
        Outcome out = Outcome.empty();
        Ctx c = new Ctx(s, data, rng, out, date);
        TaiwanAir.coverage(c);
        if (o instanceof TaiwanOrder.Playbook) {
            s.held.clear();
            for (Group g : s.groups) if (g.side == s.playerSide) g.held = false;
            for (Sub x : s.subs) if (x.side == s.playerSide) x.held = false;
            for (Squadron q : s.squadrons) if (q.side == s.playerSide) q.held = false;
            for (Battery b : s.batteries) if (b.side == s.playerSide) b.held = false;
            for (TaiwanOrder p : TaiwanAI.playbook(c, s.playerSide, s.autoplay)) {
                try { applyOrder(c, s.playerSide, p, false); } catch (IllegalArgumentException e) { /* skipped */ }
                if (s.over()) break;
            }
            c.log("The staff takes back every directive.");
            s.message = "The staff takes back every directive and runs the theatre by its playbook.";
            return out;
        }
        int before = out.log().size();
        applyOrder(c, s.playerSide, o, true);
        report(c);
        List<String> said = new ArrayList<>();
        for (int i = before; i < out.log().size() && said.size() < 3; i++) {
            String line = out.log().get(i);
            int cut = line.indexOf(":00 - ");
            said.add(cut >= 0 ? line.substring(cut + 6) : line);
        }
        s.message = said.isEmpty() ? describe(o) : String.join(" ", said);
        return out;
    }

    /** What a directive does, in a sentence, for the crisis room when the order itself logs nothing. */
    String describe(TaiwanOrder o) {
        return switch (o) {
            case TaiwanOrder.Rung x -> "The rules of engagement are now: " + TaiwanState.BLUE_RUNG[Math.max(0, Math.min(6, x.rung()))].toLowerCase(java.util.Locale.ROOT) + ".";
            case TaiwanOrder.Mainland x -> pick(x.level(), "No strikes on the mainland.",
                    "Limited strikes on the mainland are authorised: ports, embarkation areas, air bases and coastal launchers.",
                    "Broad strikes on the mainland are authorised: missile forces, radars and command.");
            case TaiwanOrder.Fleet x -> pick(x.posture(), "The carriers move forward, into the western Philippine Sea.",
                    "The carriers stand off in the northern and central Philippine Sea.", "The carriers withdraw toward Guam.",
                    "Where the carriers go is the commander's choice.");
            case TaiwanOrder.Submarines x -> pick(x.focus(), "The attack submarines go into the Taiwan Strait, after the amphibious fleet.",
                    "The attack submarines guard the approaches and the chokepoints.", "The attack submarines blockade China's trade from a distance.",
                    "Where the submarines patrol is the commander's choice.");
            case TaiwanOrder.MunitionsPolicy x -> pick(x.level(), "Conserve munitions: smaller salvos, and a quarter of the long-range anti-ship missiles and SM-6s kept back.",
                    "Munitions are fired as the staff plans.", "Surge: whole squadrons fire, and the stocks are spent now.");
            case TaiwanOrder.AirPriority x -> Fmt.format("The strike fighters are divided: %.0f%% against ships, %.0f%% against the mainland, %.0f%% over Taiwan, %.0f%% guarding the fleet.",
                    x.antiShip() * 100, x.mainland() * 100, x.taiwanCap() * 100, x.fleetDefence() * 100);
            case TaiwanOrder.Convoys x -> x.on() ? "Escorts will shepherd the shipping to Taiwan." : "The convoys are stood down.";
            case TaiwanOrder.AllyRole x -> "The staff will ask " + countryName(x.country()) + " for " + pick(x.role(), "access and overflight", "basing", "the defence of its own territory", "full combat") + ".";
            case TaiwanOrder.Nuclear x -> "Nuclear posture: " + TaiwanState.ALERT[Math.max(0, Math.min(3, x.alert()))].toLowerCase(java.util.Locale.ROOT) + ".";
            default -> "Ordered.";
        };
    }

    private String countryName(String id) { int i = data.country(id); return i < 0 ? id : data.countries.get(i).name(); }

    private static String pick(int i, String... options) { return options[Math.max(0, Math.min(options.length - 1, i))]; }

    /** The key under which a directive is held from the staff, or null for a one-time decision. */
    static String heldKey(TaiwanOrder o) {
        return switch (o) {
            case TaiwanOrder.Rung x -> "rung";
            case TaiwanOrder.Nuclear x -> "nuclear";
            case TaiwanOrder.Programme x -> "programme:" + x.id();
            case TaiwanOrder.Mainland x -> "mainland";
            case TaiwanOrder.Fleet x -> "fleet";
            case TaiwanOrder.AirPriority x -> "air";
            case TaiwanOrder.Submarines x -> "subs";
            case TaiwanOrder.MunitionsPolicy x -> "munitions";
            case TaiwanOrder.Convoys x -> "convoys";
            case TaiwanOrder.AllyRole x -> "ally:" + x.country();
            case TaiwanOrder.Plan x -> "plan";
            case TaiwanOrder.DDay x -> "plan";
            case TaiwanOrder.StandDown x -> "plan";
            case TaiwanOrder.Preempt x -> "preempt";
            default -> null;
        };
    }

    static void usOnly(int side) { if (side != US) throw new IllegalArgumentException("That is Washington's decision"); }

    static void prcOnly(int side) { if (side != PRC) throw new IllegalArgumentException("That is Beijing's decision"); }

    void applyOrder(Ctx c, int side, TaiwanOrder o, boolean player) {
        TaiwanState s = c.s;
        if (s.over()) throw new IllegalArgumentException("The crisis is over");
        String msg = null;
        switch (o) {
            case TaiwanOrder.Rung x -> {
                if (x.rung() < 0 || x.rung() > 6) throw new IllegalArgumentException("The ladder runs from 0 to 6");
                if (side == US && x.rung() > TaiwanWorld.rungCap(s)) throw new IllegalArgumentException("Your commitment allows no more than " + BLUE_RUNG[TaiwanWorld.rungCap(s)].toLowerCase(Locale.ROOT));
                TaiwanWorld.setRung(c, side, x.rung());
                if (side == PRC && s.atWar() && x.rung() >= 2) s.blockade = Math.max(s.blockade, 2);
                if (side == PRC && s.atWar() && x.rung() >= 3 && (s.plan == 1 || s.plan == 2)) s.blockade = Math.max(s.blockade, 3);
            }
            case TaiwanOrder.Nuclear x -> {
                if (x.alert() == 4) {
                    if (!s.atWar()) throw new IllegalArgumentException("Nuclear weapons are not a crisis tool");
                    s.nukeUsed = true;
                    TaiwanWorld.end(c, 7, (side == US ? "The United States" : "China") + " used nuclear weapons. Whatever happens next, everyone has lost.");
                    return;
                }
                if (x.alert() < 0 || x.alert() > 3) throw new IllegalArgumentException("Nuclear alert runs from 0 (normal) to 3 (threaten)");
                TaiwanWorld.nuclearAlert(c, side, x.alert());
            }
            case TaiwanOrder.Ceasefire x -> {
                if (!s.atWar()) throw new IllegalArgumentException("There is no war to end yet");
                if (s.offerFrom >= 0) throw new IllegalArgumentException("A ceasefire offer is already on the table");
                if (x.terms() < 0 || x.terms() > 2 || x.terms() == 2 && side == US) throw new IllegalArgumentException("No such terms");
                TaiwanWorld.propose(c, side, x.terms());
            }
            case TaiwanOrder.Answer x -> {
                if (s.offerFrom < 0 || s.offerFrom == side) throw new IllegalArgumentException("There is no offer to answer");
                TaiwanWorld.respond(c, x.accept());
            }
            case TaiwanOrder.Programme x -> {
                int i = TaiwanWorld.progIndex(c, side, x.id());
                if (x.run()) {
                    if (s.progOn[side][i]) throw new IllegalArgumentException("That programme is already running");
                    if (s.progPct[side][i] >= 100) throw new IllegalArgumentException("That programme is complete");
                    if (side == US && s.commitment.equals("STAND_ASIDE") && !x.id().equals("declassify") && !x.id().equals("departure"))
                        throw new IllegalArgumentException("Standing aside, America prepares no forces");
                    TaiwanWorld.start(c, side, x.id());
                } else {
                    if (!s.progOn[side][i]) throw new IllegalArgumentException("That programme is not running");
                    TaiwanWorld.stop(c, side, x.id());
                }
            }
            case TaiwanOrder.Diplomacy x -> msg = TaiwanWorld.act(c, side, x.country(), x.action());
            case TaiwanOrder.Economic x -> {
                if (side == US && s.commitment.equals("STAND_ASIDE")) throw new IllegalArgumentException("Standing aside, America imposes no sanctions");
                boolean done = side == US ? TaiwanWorld.usMeasure(c, x.measure()) : TaiwanWorld.prcMeasure(c, x.measure());
                if (!done) throw new IllegalArgumentException("That measure is already in force");
            }
            case TaiwanOrder.Unit x -> msg = unit(c, side, x);
            case TaiwanOrder.Playbook x -> throw new IllegalArgumentException("The playbook is a list of orders");
            case TaiwanOrder.Mainland x -> {
                usOnly(side);
                if (x.level() < 0 || x.level() > 2) throw new IllegalArgumentException("Mainland strikes: 0 none, 1 limited, 2 broad");
                if (x.level() > 0 && !s.mainlandAllowed) throw new IllegalArgumentException("Strikes on the mainland are ruled out");
                int need = x.level() == 2 ? 5 : x.level() == 1 ? 4 : 0;
                if (need > TaiwanWorld.rungCap(s)) throw new IllegalArgumentException("Your commitment rules out strikes on the mainland");
                s.mainland = x.level();
                if (need > s.rung[US]) TaiwanWorld.setRung(c, US, need);
                if (x.level() == 0 && s.rung[US] > 3) TaiwanWorld.setRung(c, US, 3);
            }
            case TaiwanOrder.Fleet x -> { usOnly(side); if (x.posture() < 0 || x.posture() > 3) throw new IllegalArgumentException("No such posture"); s.fleet = x.posture(); }
            case TaiwanOrder.AirPriority x -> {
                usOnly(side);
                double t = x.antiShip() + x.mainland() + x.taiwanCap() + x.fleetDefence();
                if (t <= 0 || x.antiShip() < 0 || x.mainland() < 0 || x.taiwanCap() < 0 || x.fleetDefence() < 0) throw new IllegalArgumentException("The shares must be positive");
                s.airShares[0] = x.antiShip() / t; s.airShares[1] = x.mainland() / t; s.airShares[2] = x.taiwanCap() / t; s.airShares[3] = x.fleetDefence() / t;
            }
            case TaiwanOrder.Submarines x -> { usOnly(side); if (x.focus() < 0 || x.focus() > 3) throw new IllegalArgumentException("No such focus"); s.subFocus = x.focus(); }
            case TaiwanOrder.MunitionsPolicy x -> { usOnly(side); if (x.level() < 0 || x.level() > 2) throw new IllegalArgumentException("No such policy"); s.munitionsPolicy = x.level(); }
            case TaiwanOrder.Convoys x -> {
                usOnly(side);
                if (x.on() && TaiwanWorld.rungCap(s) < 1) throw new IllegalArgumentException("Standing aside, the Navy escorts no one");
                s.convoys = x.on();
            }
            case TaiwanOrder.AllyRole x -> {
                usOnly(side);
                if (x.role() < 0 || x.role() > 3) throw new IllegalArgumentException("Roles: 0 access, 1 basing, 2 defend its own territory, 3 full combat");
                Country k = TaiwanWorld.country(c, x.country());
                if (c.d.countries.get(c.d.country(x.country())).aligned()) throw new IllegalArgumentException("That country is not an ally");
                k.role = x.role();
                k.defensiveOnly = x.role() == 2 || x.country().equals("JPN") && s.jsdfDefensive;
            }
            case TaiwanOrder.TaiwanAsk x -> {
                usOnly(side);
                switch (x.what()) {
                    case "mobilise" -> { if (s.mobilized >= 1) throw new IllegalArgumentException("Taiwan is fully mobilised"); s.twMobiliseAsked = true; TaiwanWorld.mobilizeTaiwan(c, 1, "an American request"); }
                    case "partial" -> { if (s.mobilized >= 0.5) throw new IllegalArgumentException("Taiwan's reserves are already called up"); TaiwanWorld.mobilizeTaiwan(c, 0.5, "rising tension"); }
                    case "ration" -> { if (s.twRationAsked) throw new IllegalArgumentException("Taiwan is already rationing"); s.twRationAsked = true; if (s.ration < 1) s.ration = 1; }
                    case "disperse" -> {
                        if (s.twDisperseAsked) throw new IllegalArgumentException("Taiwan's forces are already dispersed");
                        s.twDisperseAsked = true;
                        for (Battery b : s.batteries) if (b.nat.equals("TWN")) b.dispersal = Math.max(b.dispersal, 0.85);
                    }
                    default -> throw new IllegalArgumentException("Taipei can be asked to mobilise, ration or disperse");
                }
            }
            case TaiwanOrder.Preempt x -> {
                usOnly(side);
                if (!s.phase.equals("crisis")) throw new IllegalArgumentException("The war has begun");
                if (x.strike()) {
                    if (!s.commitment.equals("INTERVENE")) throw new IllegalArgumentException("Your commitment rules out striking first");
                    s.blueStrikeHour = s.hour + 12;
                    c.alert("The president orders a pre-emptive strike in twelve hours.");
                } else s.blueStrikeHour = -1;
            }
            case TaiwanOrder.Plan x -> {
                prcOnly(side);
                if (!s.phase.equals("crisis")) throw new IllegalArgumentException("The war has begun");
                int p = planIndex(x.plan());
                if (p < 0) throw new IllegalArgumentException("No such plan: " + x.plan());
                if (s.fixedPlan != null && p != planIndex(s.fixedPlan) && player) throw new IllegalArgumentException("The plan is fixed: " + s.fixedPlan);
                s.plan = p; s.planChosen = true;
                s.optStrikeUS = x.strikeUS() && p != 2; s.optStrikeJapan = x.strikeJapan() && s.strikeJapanEnabled && p != 2;
                s.optCyber = x.cyber(); s.optDecap = x.decapitation() && p == 0; s.optPenghu = x.penghuFirst() && p == 0;
                s.region = TMath.clampi(x.region(), 0, 2); s.lethality = p == 1 ? TMath.clampi(x.lethality(), 0, 2) : 1;
                c.log("Beijing's course of action: " + TaiwanData.PLANS[p] + ".");
            }
            case TaiwanOrder.DDay x -> {
                prcOnly(side);
                if (!s.phase.equals("crisis")) throw new IllegalArgumentException("The war has begun");
                if (!s.planChosen) throw new IllegalArgumentException("Choose a course of action first");
                if (x.day() <= s.day() || x.day() > 110) throw new IllegalArgumentException("D-day must be after today and no later than day 110");
                if (s.dDay > 0 && x.day() > s.dDay) { s.postponed++; c.log("D-day is postponed to day " + x.day() + ".", side == s.playerSide); }
                s.dDay = x.day();
                s.launchHour = (s.dDay - 1) * 24 + 5;
                s.standDownDay = -1;
            }
            case TaiwanOrder.StandDown x -> {
                prcOnly(side);
                if (!s.phase.equals("crisis")) throw new IllegalArgumentException("The war has begun");
                s.dDay = -1; s.launchHour = -1; s.standDownDay = s.day();
                for (int i = 0; i < s.progOn[PRC].length; i++) s.progOn[PRC][i] = false;
                c.log("Beijing stands down: the operation is cancelled.", side == s.playerSide);
                c.news(News.Kind.GOOD, "The PLA ends its exercises around Taiwan", null, 3);
                c.effect(new Effect.Tension(HotSpot.TAIWAN_STRAIT, -10));
            }
            case TaiwanOrder.Prepare x -> {
                prcOnly(side);
                if (s.plan < 0) throw new IllegalArgumentException("Choose a course of action first");
                var red = c.d.programmes(PRC);
                for (int i = 0; i < red.size(); i++) if (red.get(i).needed(s.plan) && !s.progOn[PRC][i] && s.progPct[PRC][i] < 100) TaiwanWorld.start(c, PRC, red.get(i).id());
            }
            case TaiwanOrder.Deception x -> { prcOnly(side); msg = TaiwanIntel.deceive(c, x.what()); }
        }
        if (player) {
            String k = heldKey(o);
            if (k != null && !s.held.contains(k)) s.held.add(k);
            if (msg != null) c.log(msg);
        }
    }

    /** A unit order (the toggle of spec 11.5): the unit is held from the staff until released. */
    String unit(Ctx c, int side, TaiwanOrder.Unit x) {
        TaiwanState s = c.s;
        TaiwanData d = c.d;
        String m = x.mission(), t = x.target();
        for (Group g : s.groups) {
            if (!g.key.equals(x.unit())) continue;
            if (g.side != side) throw new IllegalArgumentException("That is not your unit");
            if (g.dead() || g.hub >= 0 || g.gone) throw new IllegalArgumentException(g.name + " cannot take orders now");
            switch (m) {
                case "release" -> { g.held = false; return g.name + " is back under the staff."; }
                case "patrol", "screen" -> { TaiwanSea.orderZone(c, g, d.zone(t), m.equals("patrol") ? M_PATROL : M_SCREEN); g.held = true; return g.name + " sails for " + d.zones.get(d.zone(t)).name() + "."; }
                case "port" -> {
                    int b = t == null || t.isEmpty() ? TaiwanSea.nearestPort(c, side, g.nat, TaiwanSea.gx(c, g), TaiwanSea.gy(c, g)) : d.base(t);
                    if (b < 0 || !TaiwanSea.portFor(c, b, side, g.nat)) throw new IllegalArgumentException("No friendly port there");
                    TaiwanSea.orderPort(c, g, b); g.held = true; return g.name + " returns to port.";
                }
                default -> throw new IllegalArgumentException("A group can patrol, screen or return to port");
            }
        }
        for (Sub u : s.subs) {
            if (!u.key.equals(x.unit())) continue;
            if (u.side != side) throw new IllegalArgumentException("That is not your unit");
            if (u.lost || u.hub >= 0) throw new IllegalArgumentException(u.name + " cannot take orders now");
            switch (m) {
                case "release" -> { u.held = false; return u.name + " is back under the staff."; }
                case "patrol" -> { TaiwanSea.orderSubZone(c, u, d.zone(t)); u.held = true; return u.name + " sails for " + d.zones.get(d.zone(t)).name() + "."; }
                case "port" -> { TaiwanSea.orderSubPort(c, u); u.held = true; return u.name + " returns to port."; }
                default -> throw new IllegalArgumentException("A submarine can patrol or return to port");
            }
        }
        for (Squadron q : s.squadrons) {
            if (!q.key.equals(x.unit())) continue;
            if (q.side != side) throw new IllegalArgumentException("That is not your unit");
            switch (m) {
                case "release" -> { q.held = false; return q.name + " is back under the staff."; }
                case "hold" -> { q.mission = AM_HOLD; q.held = true; return q.name + " stands on alert."; }
                case "cap" -> { q.mission = AM_CAP; q.zone = t.equals("TAIWAN") ? d.twZone : d.zone(t); q.held = true; return q.name + " flies combat air patrols."; }
                case "strike" -> {
                    int[] tk = target(c, side, t);
                    q.mission = AM_STRIKE; q.tKind = tk[0]; q.tId = tk[1]; q.weapon = -1; q.held = true;
                    if (!TaiwanFire.allowed(c, side, tk[0], tk[1], -1) && tk[0] != TK_GROUP) return q.name + " is tasked, but the rules of engagement do not permit the strike yet.";
                    return q.name + " is tasked to strike.";
                }
                default -> throw new IllegalArgumentException("A squadron can fly CAP, strike or hold");
            }
        }
        for (Battery b : s.batteries) {
            if (!b.key.equals(x.unit())) continue;
            if (b.side != side) throw new IllegalArgumentException("That is not your unit");
            switch (m) {
                case "release" -> { b.held = false; return b.name + " is back under the staff."; }
                case "hold" -> { b.tKind = TK_NONE; b.held = true; return b.name + " holds its fire."; }
                case "fire" -> {
                    int[] tk = target(c, side, t);
                    b.tKind = tk[0]; b.tId = tk[1]; b.salvo = 0; b.held = true;
                    return b.name + " is given a fire mission.";
                }
                default -> throw new IllegalArgumentException("A battery can fire or hold");
            }
        }
        throw new IllegalArgumentException("No such unit: " + x.unit());
    }

    static int[] target(Ctx c, int side, String t) {
        if (t == null || t.isEmpty()) throw new IllegalArgumentException("Choose a target");
        if (t.startsWith("b_")) return new int[]{TK_BASE, c.d.base(t)};
        if (t.startsWith("s_")) return new int[]{TK_SECTOR, c.d.sector(t)};
        if (t.startsWith("f_")) { for (int i = 0; i < c.d.facilities.size(); i++) if (c.d.facilities.get(i).id().equals(t)) return new int[]{TK_FACILITY, i}; }
        try {
            int id = Integer.parseInt(t);
            TaiwanState.Contact k = c.s.contacts.get(side).get(id);
            if (k == null) throw new IllegalArgumentException("No contact on that unit");
            return new int[]{k.kind, id};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("No such target: " + t);
        }
    }

    /** The staff's directives for the player's side this turn: what {@link TaiwanOrder.Playbook} would apply. */
    public List<TaiwanOrder> playbook(TaiwanState s) {
        Ctx c = new Ctx(s, data, null, Outcome.empty(), s.date());
        return TaiwanAI.playbook(c, s.playerSide, s.autoplay);
    }

    /** The presidency has run its week (the economy, the Fed, opinion) while the crisis pauses the weeks. */
    public void ticked(TaiwanState s) { s.tickHour = s.hour; }

    /** Both sides by the computer, ceasefire proposals included (the headless harness). */
    public void autopilot(TaiwanState s, boolean on) { s.autoplay = on; }

    // =================================================================================================================
    //  The result and the fallout (spec 11.6, 11.7)
    // =================================================================================================================

    public TaiwanResult result(TaiwanState s) {
        Ctx c = new Ctx(s, data, null, Outcome.empty(), s.date());
        Loss us = c.loss("USA");
        double held = TaiwanLand.heldKm2(s);
        TaiwanResult.Status st = s.over() ? TaiwanResult.status(s.outcome) : null;
        return new TaiwanResult(st, s.outcomeText, s.outcome, s.endReason, (int) us.killed, us.ships, us.carriers, us.air + us.ground, held, held / 36000.0,
                s.elec, s.chips, s.day(), s.warDays(), s.scoreUS, TaiwanResult.grade(s.scoreUS), s.over() ? fallout(c) : List.of());
    }

    /** The effects of the crisis on the presidency (spec 11.7), from the state as it ended. */
    static List<Effect> fallout(Ctx c) {
        TaiwanState s = c.s;
        List<Effect> l = new ArrayList<>();
        TaiwanResult.Status st = TaiwanResult.status(s.outcome);
        if (st == null) return l;
        Loss us = c.loss("USA");
        double wd = s.warDays();
        l.add(new Effect.Performance(round1((s.scoreUS - 50) / 10)));
        if (s.econDays > 0) {
            double gap = s.usGapSum / s.econDays, brent = s.brentSum / s.econDays;
            double growth = -gap * 100, inflation = 0.02 * Math.max(0, brent - 92) + 1.5 * s.chipShockMax;
            if (Math.abs(growth) >= 0.05 || inflation >= 0.05) l.add(new Effect.EconomyShock(round1(growth), round1(inflation), (int) Math.floor(wd / 30) + 6));
        }
        double drawdown = Math.max(0, 1 - s.equityLow);
        if (drawdown > 0.01) l.add(new Effect.FinancialShock(round1(-Math.min(15, 40 * drawdown))));
        double cost = s.costUS + us.ships * 2000 + us.carriers * 11000 + (s.usCombat ? (s.hour - s.usCombatHour) / 24.0 * 300 : 0);
        if (cost > 0) l.add(new Effect.Deficit(round2(cost / 310000.0)));
        if (TaiwanWorld.progDone(c, US, "mobilise")) l.add(new Effect.Munitions(0.3));
        switch (st) {
            case DETERRED -> { l.add(new Effect.Deterrence(5)); l.add(new Effect.Tension(HotSpot.TAIWAN_STRAIT, -15)); }
            case DEFENDED -> { l.add(new Effect.WarEnded(s.outcome == 2 ? 65 : 75)); l.add(new Effect.Tension(HotSpot.TAIWAN_STRAIT, -10)); l.add(new Effect.WorldShift("rel:TW", 15)); }
            case STALEMATE -> { l.add(new Effect.WarEnded(45)); l.add(new Effect.Tension(HotSpot.TAIWAN_STRAIT, 5)); }
            case FALLEN -> { l.add(new Effect.WarEnded(15)); l.add(new Effect.Tension(HotSpot.TAIWAN_STRAIT, 10)); l.add(new Effect.WorldShift("rel:TW", s.usCombat ? -10 : -40)); }
            case NUCLEAR -> { l.add(new Effect.WarEnded(5)); l.add(new Effect.Tension(HotSpot.TAIWAN_STRAIT, 20)); }
        }
        if (st == TaiwanResult.Status.FALLEN && !s.usCombat) for (String k : new String[]{"JP", "KR", "PH", "AU"}) l.add(new Effect.WorldShift("rel:" + k, -15));
        if (st == TaiwanResult.Status.DEFENDED && s.usCombat) for (String k : new String[]{"JP", "KR", "PH", "AU"}) l.add(new Effect.WorldShift("rel:" + k, 5));
        if (s.atWar() || s.dHour >= 0) {
            l.add(new Effect.Salience(Issue.CHINA, 2));
            if (s.chipShockMax > 0.1) l.add(new Effect.Salience(Issue.ECONOMY, round1(3 * s.chipShockMax)));
        }
        l.add(new Effect.PresidentStrain(round1(-Math.min(10, s.day() * 0.1)), round1(Math.min(20, wd * 0.5) + 10 * s.crisisCount)));
        double hurt = us.ships * 0.5 + (us.air + us.ground) / 20.0;
        if (hurt > 0) l.add(new Effect.Readiness(2, round1(-Math.min(10, hurt))));
        return l;
    }

    static double round1(double v) { return Math.floor(v * 10 + 0.5) / 10; }

    static double round2(double v) { return Math.floor(v * 100 + 0.5) / 100; }
}
