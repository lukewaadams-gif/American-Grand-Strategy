package game.mandate.core.taiwan;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Taiwan crisis and war (docs/ISLAND_CHAIN.md, after the Island Chain wargame): the clock, both sides' forces by zone,
 * Taiwan's sectors and energy, the blockade, munitions and losses, the allies, the warning picture, nuclear risk, the
 * economy channels and the outcome. Units refer to the rows of {@link TaiwanData} by index. Owned by {@link TaiwanEngine};
 * everything public here only reads.
 */
public final class TaiwanState {
    public static final int US = TaiwanData.US, PRC = TaiwanData.PRC;
    /** Group missions. */
    public static final int M_IDLE = 0, M_MOVE = 1, M_PATROL = 2, M_SCREEN = 3, M_BLOCKADE = 4, M_QUARANTINE = 5, M_CONVOY = 6, M_RETURN = 7,
            M_ASSAULT = 8, M_WITHDRAW = 9;
    public static final String[] MISSION = {"in port", "moving", "patrol", "screen", "blockade", "quarantine", "convoy escort", "returning to port",
            "landing", "withdrawing"};
    /** Submarine missions. */
    public static final int SM_PORT = 0, SM_TRANSIT = 1, SM_PATROL = 2, SM_RETURN = 3;
    public static final String[] SUB_MISSION = {"in port", "on passage", "on patrol", "returning to port"};
    /** Squadron missions. */
    public static final int AM_HOLD = 0, AM_CAP = 1, AM_SWEEP = 2, AM_STRIKE = 3, AM_ESCORT = 4, AM_ASW = 5, AM_AEW = 6, AM_ISR = 7, AM_TANK = 8,
            AM_CAS = 9, AM_AIRLIFT = 10, AM_AIRBORNE = 11, AM_MINE = 12;
    public static final String[] AIR_MISSION = {"on alert", "combat air patrol", "fighter sweep", "strike", "escort", "anti-submarine patrol",
            "airborne early warning", "reconnaissance", "tanker support", "ground support", "airlift", "airborne assault", "minelaying"};
    /** Target kinds. */
    public static final int TK_NONE = 0, TK_GROUP = 1, TK_BASE = 2, TK_LAND = 3, TK_SECTOR = 4, TK_FACILITY = 5, TK_SUB = 6, TK_ZONE = 7;
    /** Ground unit states. */
    public static final int GS_READY = 0, GS_MOVING = 1, GS_RESERVE = 2, GS_STAGING = 3, GS_EMBARKED = 4, GS_DEAD = 5;
    public static final String[] GROUND_STATE = {"ready", "moving", "in reserve", "staging", "embarked", "destroyed"};
    public static final String[] RED_RUNG = {"Grey zone", "Quarantine", "Blockade", "Force in the blockade zone", "Strikes on Taiwan",
            "Strikes on US and allied forces", "Strikes beyond the theatre"};
    public static final String[] BLUE_RUNG = {"Diplomatic only", "Presence and escort", "Combat near Taiwan", "Combat at sea anywhere",
            "Limited mainland strikes", "Broad mainland strikes", "Strategic strikes"};
    public static final String[] BLOCKADE = {"none", "quarantine", "declared blockade", "submarines and mines", "kinetic blockade"};
    public static final String[] WARNING = {"WATCH", "ELEVATED", "SIGNIFICANT", "HIGH", "IMMINENT"};
    public static final String[] ALERT = {"NORMAL", "RAISED", "DISPERSED", "THREATENING"};
    public static final String[] ECONOMIES = {"USA", "CHN", "TWN", "JPN", "KOR", "EU", "WORLD"};
    public static final int E_USA = 0, E_CHN = 1, E_TWN = 2, E_JPN = 3, E_KOR = 4, E_EU = 5, E_WORLD = 6;
    /** Nations whose losses are counted. */
    public static final String[] NATIONS = {"USA", "CHN", "TWN", "JPN", "KOR", "PHL", "AUS", "GBR", "FRA", "RUS"};

    // =================================================================================================================
    //  Units
    // =================================================================================================================

    /** A line of ships of one class in a group: intact ships as a count, or one damaged ship. */
    public static final class Ship {
        int cls, n;
        double dmg;
        boolean mk;

        Ship() {}
        Ship(int cls, int n) { this.cls = cls; this.n = n; }

        /** The ship class (index into {@link TaiwanData#shipClasses()}). */
        public int cls() { return cls; }
        /** How many ships (1 for a damaged one). */
        public int n() { return n; }
        /** Damage, 0-1. */
        public double damage() { return dmg; }
        /** Mission-killed: cannot fight, launch aircraft or count as fit. */
        public boolean missionKilled() { return mk; }

        List<Object> json() { return new W().i(cls).i(n).d(dmg).b(mk).l; }
        static Ship of(Object o) { R r = new R(o); Ship s = new Ship(); s.cls = r.i(); s.n = r.i(); s.dmg = r.d(); s.mk = r.b(); return s; }
    }

    /** A naval group: ships as counts by class, a magazine, and where it is. */
    public static final class Group {
        int id, side, mission = M_IDLE, zone = -1, at = -1, place = -1, from = -1, to = -1, port = -1, homePort = -1, hub = -1, beach = -1, fac = -1, focus = -1;
        int samL, samS, sunk, lKind, lId, lCount;
        String key, name, tag, nat;
        double depart, arrive, hubArrive = -1, readyAt = -1, rearmUntil = -1, lastFire = -99, daysAtSea, beachHours, offloadAcc, kn;
        boolean emcon, weaponsFree = true, held, decoy, gone, shuttling;
        final List<Ship> ships = new ArrayList<>();
        int[] mag;
        final List<Integer> cargo = new ArrayList<>(), wing = new ArrayList<>();

        public int id() { return id; }
        public String key() { return key; }
        public String name() { return name; }
        /** The staff's role tag (spec 11.3), e.g. "csg", "amph_south", "cg". */
        public String tag() { return tag; }
        public String nation() { return nat; }
        public int side() { return side; }
        public List<Ship> ships() { return Collections.unmodifiableList(ships); }
        /** Ships afloat. */
        public int alive() { int k = 0; for (Ship s : ships) k += s.n; return k; }
        /** Ships able to fight. */
        public int fit() { int k = 0; for (Ship s : ships) if (!s.mk) k += s.n; return k; }
        /** Ships sunk so far. */
        public int sunk() { return sunk; }
        /** The zone it is in now (index into {@link TaiwanData#zones()}), or -1 off the map. */
        public int zone() { return at; }
        /** The zone it was sent to (patrol, screen, blockade), or -1. */
        public int targetZone() { return zone; }
        public int mission() { return mission; }
        /** The base it is alongside, or -1 at sea. */
        public int port() { return port; }
        /** Sailing between places: the hour it arrives (game hours). */
        public boolean inTransit() { return place < 0 && hub < 0; }
        public double arrives() { return arrive; }
        /** Off the map at a hub (index into {@link TaiwanData#hubs()}), or -1. */
        public int hub() { return hub; }
        /** The hour it leaves its hub, or -1 if not yet released. */
        public double hubArrive() { return hubArrive; }
        /** Long- and short-range interceptors left. */
        public int samLong() { return samL; }
        public int samShort() { return samS; }
        /** Missiles in the magazine, by munition index. */
        public int magazine(int munition) { return mag[munition]; }
        public int beach() { return beach; }
        public int facility() { return fac; }
        /** Ground units aboard (troop ids). */
        public List<Integer> cargo() { return Collections.unmodifiableList(cargo); }
        public boolean emcon() { return emcon; }
        public boolean held() { return held; }
        public boolean decoy() { return decoy; }
        /** Withdrawn from the war for repairs. */
        public boolean gone() { return gone; }
        public boolean dead() { return alive() == 0; }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", new W().s(key).s(name).s(tag).s(nat).l);
            m.put("i", new W().i(id).i(side).i(mission).i(zone).i(at).i(place).i(from).i(to).i(port).i(homePort).i(hub).i(beach).i(fac).i(focus).i(samL).i(samS)
                    .i(sunk).i(lKind).i(lId).i(lCount).l);
            m.put("d", new W().d(depart).d(arrive).d(hubArrive).d(readyAt).d(rearmUntil).d(lastFire).d(daysAtSea).d(beachHours).d(offloadAcc).d(kn).l);
            m.put("b", new W().b(emcon).b(weaponsFree).b(held).b(decoy).b(gone).b(shuttling).l);
            List<Object> sh = new ArrayList<>();
            for (Ship s : ships) sh.add(s.json());
            m.put("ships", sh);
            m.put("mag", sparse(mag));
            m.put("cargo", new ArrayList<Object>(cargo));
            m.put("wing", new ArrayList<Object>(wing));
            return m;
        }

        static Group of(Map<String, Object> m, int nMun) {
            Group g = new Group();
            R k = new R(m.get("k"));
            g.key = k.s(); g.name = k.s(); g.tag = k.s(); g.nat = k.s();
            R i = new R(m.get("i"));
            g.id = i.i(); g.side = i.i(); g.mission = i.i(); g.zone = i.i(); g.at = i.i(); g.place = i.i(); g.from = i.i(); g.to = i.i(); g.port = i.i();
            g.homePort = i.i(); g.hub = i.i(); g.beach = i.i(); g.fac = i.i(); g.focus = i.i(); g.samL = i.i(); g.samS = i.i(); g.sunk = i.i(); g.lKind = i.i();
            g.lId = i.i(); g.lCount = i.i();
            R d = new R(m.get("d"));
            g.depart = d.d(); g.arrive = d.d(); g.hubArrive = d.d(); g.readyAt = d.d(); g.rearmUntil = d.d(); g.lastFire = d.d(); g.daysAtSea = d.d();
            g.beachHours = d.d(); g.offloadAcc = d.d(); g.kn = d.d();
            R b = new R(m.get("b"));
            g.emcon = b.b(); g.weaponsFree = b.b(); g.held = b.b(); g.decoy = b.b(); g.gone = b.b(); g.shuttling = b.b();
            for (Object o : Json.array(m.get("ships"))) g.ships.add(Ship.of(o));
            g.mag = unsparse(m.get("mag"), nMun);
            for (Object o : Json.array(m.get("cargo"))) g.cargo.add(Json.integer(o));
            for (Object o : Json.array(m.get("wing"))) g.wing.add(Json.integer(o));
            return g;
        }
    }

    /** A submarine. */
    public static final class Sub {
        int id, side, cls, mission = SM_PORT, zone = -1, at = -1, place = -1, from = -1, to = -1, home = -1, hub = -1, torps, missiles, kills;
        String key, name, nat;
        double depart, arrive, hubArrive = -1, lastAttack = -99, rearmUntil = -1, daysAtSea;
        boolean lost, counterDetected, weaponsFree = true, held;

        public int id() { return id; }
        public String key() { return key; }
        public String name() { return name; }
        public String nation() { return nat; }
        public int side() { return side; }
        /** The class (index into {@link TaiwanData#subClasses()}). */
        public int cls() { return cls; }
        public int mission() { return mission; }
        /** The zone it is in now, or -1 off the map. */
        public int zone() { return at; }
        public int targetZone() { return zone; }
        public int hub() { return hub; }
        public double hubArrive() { return hubArrive; }
        public int torpedoes() { return torps; }
        public int missiles() { return missiles; }
        public int kills() { return kills; }
        public boolean lost() { return lost; }
        public boolean held() { return held; }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", new W().s(key).s(name).s(nat).l);
            m.put("i", new W().i(id).i(side).i(cls).i(mission).i(zone).i(at).i(place).i(from).i(to).i(home).i(hub).i(torps).i(missiles).i(kills).l);
            m.put("d", new W().d(depart).d(arrive).d(hubArrive).d(lastAttack).d(rearmUntil).d(daysAtSea).l);
            m.put("b", new W().b(lost).b(counterDetected).b(weaponsFree).b(held).l);
            return m;
        }

        static Sub of(Map<String, Object> m) {
            Sub s = new Sub();
            R k = new R(m.get("k"));
            s.key = k.s(); s.name = k.s(); s.nat = k.s();
            R i = new R(m.get("i"));
            s.id = i.i(); s.side = i.i(); s.cls = i.i(); s.mission = i.i(); s.zone = i.i(); s.at = i.i(); s.place = i.i(); s.from = i.i(); s.to = i.i();
            s.home = i.i(); s.hub = i.i(); s.torps = i.i(); s.missiles = i.i(); s.kills = i.i();
            R d = new R(m.get("d"));
            s.depart = d.d(); s.arrive = d.d(); s.hubArrive = d.d(); s.lastAttack = d.d(); s.rearmUntil = d.d(); s.daysAtSea = d.d();
            R b = new R(m.get("b"));
            s.lost = b.b(); s.counterDetected = b.b(); s.weaponsFree = b.b(); s.held = b.b();
            return s;
        }
    }

    /** A squadron of aircraft. */
    public static final class Squadron {
        int id, side, type, n, n0, base = -1, carrier = -1, hub = -1, dest = -1, mission = AM_HOLD, zone = -1, tKind, tId = -1, weapon = -1, perSortie,
                lostAir, lostGround, sorties, kills, escortOf = -1;
        String key, name, nat;
        double readiness = 0.9, hubArrive = -1, lastStrike = -99;
        boolean held;

        public int id() { return id; }
        public String key() { return key; }
        public String name() { return name; }
        public String nation() { return nat; }
        public int side() { return side; }
        /** The aircraft type (index into {@link TaiwanData#aircraft()}). */
        public int type() { return type; }
        /** Aircraft left, and at the start. */
        public int aircraft() { return n; }
        public int aircraftAtStart() { return n0; }
        /** Readiness 0-1. */
        public double readiness() { return readiness; }
        /** Its base (index into bases), or -1 aboard a carrier or off the map. */
        public int base() { return base; }
        /** The group it flies from, or -1. */
        public int carrier() { return carrier; }
        public int hub() { return hub; }
        public double hubArrive() { return hubArrive; }
        public int mission() { return mission; }
        /** The zone of its patrol, or the airspace over Taiwan. */
        public int zone() { return zone; }
        public int targetKind() { return tKind; }
        public int targetId() { return tId; }
        public int lostInTheAir() { return lostAir; }
        public int lostOnTheGround() { return lostGround; }
        public boolean held() { return held; }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", new W().s(key).s(name).s(nat).l);
            m.put("i", new W().i(id).i(side).i(type).i(n).i(n0).i(base).i(carrier).i(hub).i(dest).i(mission).i(zone).i(tKind).i(tId).i(weapon).i(perSortie)
                    .i(lostAir).i(lostGround).i(sorties).i(kills).i(escortOf).l);
            m.put("d", new W().d(readiness).d(hubArrive).d(lastStrike).b(held).l);
            return m;
        }

        static Squadron of(Map<String, Object> m) {
            Squadron q = new Squadron();
            R k = new R(m.get("k"));
            q.key = k.s(); q.name = k.s(); q.nat = k.s();
            R i = new R(m.get("i"));
            q.id = i.i(); q.side = i.i(); q.type = i.i(); q.n = i.i(); q.n0 = i.i(); q.base = i.i(); q.carrier = i.i(); q.hub = i.i(); q.dest = i.i(); q.mission = i.i();
            q.zone = i.i(); q.tKind = i.i(); q.tId = i.i(); q.weapon = i.i(); q.perSortie = i.i(); q.lostAir = i.i(); q.lostGround = i.i(); q.sorties = i.i();
            q.kills = i.i(); q.escortOf = i.i();
            R d = new R(m.get("d"));
            q.readiness = d.d(); q.hubArrive = d.d(); q.lastStrike = d.d(); q.held = d.b();
            return q;
        }
    }

    /** A missile or air-defence battery. */
    public static final class Battery {
        int id, side, sys, launchers, maxLaunchers, ready, base = -1, hub = -1, tKind, tId = -1, salvo, fired;
        String key, name, nat;
        double dispersal = 0.6, readyAt = -1, lastFire = -99, hubArrive = -1;
        boolean asbm, alive = true, held;

        public int id() { return id; }
        public String key() { return key; }
        public String name() { return name; }
        public String nation() { return nat; }
        public int side() { return side; }
        /** The system (index into {@link TaiwanData#landSystems()}). */
        public int system() { return sys; }
        public int launchers() { return launchers; }
        public int maxLaunchers() { return maxLaunchers; }
        /** Rounds ready to fire. */
        public int ready() { return ready; }
        public int base() { return base; }
        public int hub() { return hub; }
        public double hubArrive() { return hubArrive; }
        /** Dispersal 0-1 (dispersed launchers survive strikes better). */
        public double dispersal() { return dispersal; }
        public boolean antiShipRole() { return asbm; }
        public boolean alive() { return alive; }
        public int fired() { return fired; }
        public boolean held() { return held; }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", new W().s(key).s(name).s(nat).l);
            m.put("i", new W().i(id).i(side).i(sys).i(launchers).i(maxLaunchers).i(ready).i(base).i(hub).i(tKind).i(tId).i(salvo).i(fired).l);
            m.put("d", new W().d(dispersal).d(readyAt).d(lastFire).d(hubArrive).b(asbm).b(alive).b(held).l);
            return m;
        }

        static Battery of(Map<String, Object> m) {
            Battery b = new Battery();
            R k = new R(m.get("k"));
            b.key = k.s(); b.name = k.s(); b.nat = k.s();
            R i = new R(m.get("i"));
            b.id = i.i(); b.side = i.i(); b.sys = i.i(); b.launchers = i.i(); b.maxLaunchers = i.i(); b.ready = i.i(); b.base = i.i(); b.hub = i.i(); b.tKind = i.i();
            b.tId = i.i(); b.salvo = i.i(); b.fired = i.i();
            R d = new R(m.get("d"));
            b.dispersal = d.d(); b.readyAt = d.d(); b.lastFire = d.d(); b.hubArrive = d.d(); b.asbm = d.b(); b.alive = d.b(); b.held = d.b();
            return b;
        }
    }

    /** A ground unit: battalions of one formation type. */
    public static final class Troop {
        int id, side, type, state = GS_READY, sector = -1, moveTo = -1, stage = -1, ship = -1;
        String key, name, nat;
        double bns, maxBns, quality = 1, supply = 1, dug, progress, mobilizeAt = -1, lost;
        boolean held;

        public int id() { return id; }
        public String key() { return key; }
        public String name() { return name; }
        public String nation() { return nat; }
        public int side() { return side; }
        /** Formation type (index into {@link TaiwanData#GTYPE}). */
        public int type() { return type; }
        public int state() { return state; }
        /** Its sector on Taiwan, or -1 (staging, in reserve or embarked). */
        public int sector() { return sector; }
        public int movingTo() { return moveTo; }
        /** Its embarkation base while staging. */
        public int stage() { return stage; }
        public double battalions() { return bns; }
        public double quality() { return quality; }
        /** Supply 0-1. */
        public double supply() { return supply; }
        /** Hours dug in. */
        public double dugIn() { return dug; }
        public double lost() { return lost; }
        public boolean held() { return held; }
        double power() { return bns * TaiwanData.GPOW[type] * quality; }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", new W().s(key).s(name).s(nat).l);
            m.put("i", new W().i(id).i(side).i(type).i(state).i(sector).i(moveTo).i(stage).i(ship).l);
            m.put("d", new W().d(bns).d(maxBns).d(quality).d(supply).d(dug).d(progress).d(mobilizeAt).d(lost).b(held).l);
            return m;
        }

        static Troop of(Map<String, Object> m) {
            Troop t = new Troop();
            R k = new R(m.get("k"));
            t.key = k.s(); t.name = k.s(); t.nat = k.s();
            R i = new R(m.get("i"));
            t.id = i.i(); t.side = i.i(); t.type = i.i(); t.state = i.i(); t.sector = i.i(); t.moveTo = i.i(); t.stage = i.i(); t.ship = i.i();
            R d = new R(m.get("d"));
            t.bns = d.d(); t.maxBns = d.d(); t.quality = d.d(); t.supply = d.d(); t.dug = d.d(); t.progress = d.d(); t.mobilizeAt = d.d(); t.lost = d.d(); t.held = d.b();
            return t;
        }
    }

    /** A base's damage. */
    public static final class BaseState {
        int craters, hasLost, hits;
        double repairAcc, fuelDmg, portDmg, c2Dmg, dispersal = 1;
        boolean captured;

        public int craters() { return craters; }
        public int sheltersLost() { return hasLost; }
        public double fuelDamage() { return fuelDmg; }
        public double portDamage() { return portDmg; }
        public double commandDamage() { return c2Dmg; }
        public boolean captured() { return captured; }
        public int hits() { return hits; }

        List<Object> json() { return new W().i(craters).i(hasLost).i(hits).d(repairAcc).d(fuelDmg).d(portDmg).d(c2Dmg).d(dispersal).b(captured).l; }
        static BaseState of(Object o) {
            R r = new R(o); BaseState b = new BaseState();
            b.craters = r.i(); b.hasLost = r.i(); b.hits = r.i(); b.repairAcc = r.d(); b.fuelDmg = r.d(); b.portDmg = r.d(); b.c2Dmg = r.d(); b.dispersal = r.d(); b.captured = r.b();
            return b;
        }
    }

    /** A sector of Taiwan. */
    public static final class SectorState {
        double ctrl, civDamage, supplyIn, lastFight = -1, km2;
        boolean contested, lodgment;

        /** PLA control 0-1. */
        public double control() { return ctrl; }
        public boolean contested() { return contested; }
        public boolean lodgment() { return lodgment; }
        /** Civilian damage 0-1. */
        public double civilianDamage() { return civDamage; }
        /** Its area, km2. */
        public double km2() { return km2; }

        List<Object> json() { return new W().d(ctrl).d(civDamage).d(supplyIn).d(lastFight).d(km2).b(contested).b(lodgment).l; }
        static SectorState of(Object o) {
            R r = new R(o); SectorState s = new SectorState();
            s.ctrl = r.d(); s.civDamage = r.d(); s.supplyIn = r.d(); s.lastFight = r.d(); s.km2 = r.d(); s.contested = r.b(); s.lodgment = r.b();
            return s;
        }
    }

    /** A port or airfield on Taiwan. */
    public static final class FacilityState {
        int owner = US;
        double damage;
        boolean sabotaged;

        /** Who holds it: {@link #US} (Taiwan) or {@link #PRC}. */
        public int owner() { return owner; }
        public double damage() { return damage; }

        List<Object> json() { return new W().i(owner).d(damage).b(sabotaged).l; }
        static FacilityState of(Object o) { R r = new R(o); FacilityState f = new FacilityState(); f.owner = r.i(); f.damage = r.d(); f.sabotaged = r.b(); return f; }
    }

    /** What a side believes about an enemy unit (spec 9.1, at zone level). */
    public static final class Contact {
        int kind, id, zone, ident;
        double x, y, err, seen, off, first;
        boolean decoy;

        /** TK_GROUP, TK_SUB or TK_LAND. */
        public int kind() { return kind; }
        /** The enemy unit's id. */
        public int unit() { return id; }
        /** The zone it was seen in. */
        public int zone() { return zone; }
        /** 0 unknown, 1 type known, 2 positively identified. */
        public int ident() { return ident; }
        /** Position error when seen, km. */
        public double error() { return err; }
        /** The hour it was last seen. */
        public double seen() { return seen; }
        /** Reported as an amphibious group (a decoy) until positively identified. */
        public boolean decoy() { return decoy; }

        List<Object> json() { return new W().i(kind).i(id).i(zone).i(ident).d(x).d(y).d(err).d(seen).d(off).d(first).b(decoy).l; }
        static Contact of(Object o) {
            R r = new R(o); Contact c = new Contact();
            c.kind = r.i(); c.id = r.i(); c.zone = r.i(); c.ident = r.i(); c.x = r.d(); c.y = r.d(); c.err = r.d(); c.seen = r.d(); c.off = r.d(); c.first = r.d(); c.decoy = r.b();
            return c;
        }
    }

    /** A diplomatic actor's stance and permissions (spec 8.1). */
    public static final class Country {
        double stance, fear, trust, momentum, pressure, reported;
        boolean basing, overflight, belligerent, attacked, sanctions, civilAirports, forcesSent, defensiveOnly;
        int lastUS = -9, lastPRC = -9, role;

        /** -100 (with China) to 100 (with the United States). */
        public double stance() { return stance; }
        /** 0 hostile, 1 leans China, 2 neutral, 3 sympathetic, 4 access, 5 basing, 6 belligerent. */
        public int level() { return level(stance); }
        public double fear() { return fear; }
        public double trust() { return trust; }
        public boolean basing() { return basing; }
        public boolean overflight() { return overflight; }
        public boolean belligerent() { return belligerent; }
        public boolean attacked() { return attacked; }
        public boolean sanctions() { return sanctions; }
        public boolean civilAirports() { return civilAirports; }
        /** The role the United States asks of it: 0 access only, 1 basing, 2 defend its own territory, 3 full combat. */
        public int role() { return role; }

        static int level(double s) { return s < -60 ? 0 : s < -25 ? 1 : s < 15 ? 2 : s < 40 ? 3 : s < 60 ? 4 : s < 80 ? 5 : 6; }

        List<Object> json() {
            return new W().d(stance).d(fear).d(trust).d(momentum).d(pressure).d(reported).b(basing).b(overflight).b(belligerent).b(attacked).b(sanctions)
                    .b(civilAirports).b(forcesSent).b(defensiveOnly).i(lastUS).i(lastPRC).i(role).l;
        }
        static Country of(Object o) {
            R r = new R(o); Country c = new Country();
            c.stance = r.d(); c.fear = r.d(); c.trust = r.d(); c.momentum = r.d(); c.pressure = r.d(); c.reported = r.d(); c.basing = r.b(); c.overflight = r.b();
            c.belligerent = r.b(); c.attacked = r.b(); c.sanctions = r.b(); c.civilAirports = r.b(); c.forcesSent = r.b(); c.defensiveOnly = r.b(); c.lastUS = r.i();
            c.lastPRC = r.i(); c.role = r.i();
            return c;
        }
    }

    /** One intelligence report on the other side's preparations (spec 9.2). */
    public static final class Indicator {
        int prog, day, strength;
        double conf;
        boolean cover;
        String text;

        /** The programme it reveals (index in that side's list), -1 noise, -2 a sensitive source on China's plan. */
        public int programme() { return prog; }
        public int day() { return day; }
        public String text() { return text; }
        /** Confidence 0-1. */
        public double confidence() { return conf; }
        public int strength() { return strength; }
        /** Assessed as possibly part of an exercise. */
        public boolean exerciseCover() { return cover; }

        List<Object> json() { return new W().i(prog).i(day).i(strength).d(conf).b(cover).s(text).l; }
        static Indicator of(Object o) { R r = new R(o); Indicator x = new Indicator(); x.prog = r.i(); x.day = r.i(); x.strength = r.i(); x.conf = r.d(); x.cover = r.b(); x.text = r.s(); return x; }
    }

    /** Losses of one nation (spec 5.9). */
    public static final class Loss {
        int ships, carriers, majors, amphibs, merchants, subs, air, ground;
        double killed, wounded, prisoners;

        public int ships() { return ships; }
        public int carriers() { return carriers; }
        /** Cruisers, destroyers and frigates. */
        public int majorCombatants() { return majors; }
        public int amphibious() { return amphibs; }
        public int merchants() { return merchants; }
        public int submarines() { return subs; }
        public int aircraftInTheAir() { return air; }
        public int aircraftOnTheGround() { return ground; }
        public int aircraft() { return air + ground; }
        public double killed() { return killed; }
        public double wounded() { return wounded; }
        public double prisoners() { return prisoners; }

        List<Object> json() { return new W().i(ships).i(carriers).i(majors).i(amphibs).i(merchants).i(subs).i(air).i(ground).d(killed).d(wounded).d(prisoners).l; }
        static Loss of(Object o) {
            R r = new R(o); Loss l = new Loss();
            l.ships = r.i(); l.carriers = r.i(); l.majors = r.i(); l.amphibs = r.i(); l.merchants = r.i(); l.subs = r.i(); l.air = r.i(); l.ground = r.i();
            l.killed = r.d(); l.wounded = r.d(); l.prisoners = r.d();
            return l;
        }
    }

    // =================================================================================================================
    //  The crisis
    // =================================================================================================================

    // the scenario and its assumptions (spec 10.1)
    int year, forcesYear, preset, playerSide, difficultyUS = 1, difficultyPRC = 1;
    String fixedPlan, commitment = "INTERVENE", assumptions;
    double deterrenceIn = 50, munitionsIn = 1, competence = 55;
    boolean japanBasingIn = true, autoplay;
    int warningMonths;
    int japanEntry = 1;
    boolean civilAirportsSet, phlBasingSet, taiwanDelay, jassmMaritime = true, shipDefensePoor, usLate, subsWithheld, simultaneous, extraHAS,
            mainlandAllowed = true, jsdfDefensive, strikeJapanEnabled = true;
    double prcMissiles = 1, twGround = 1, plaAmphib = 1;
    long planSeed;
    LocalDate start;

    // the clock (spec 1)
    String phase = "crisis";
    int hour, dHour = -1, launchHour = -1, blueStrikeHour = -1, turns, endHour = -1;
    int aiDay0 = -1, aiDay1 = -1, peaceDay = -1, peaceDayPRC = -1, groundHour0 = -1, groundHour1 = -1;
    double seaState = 1;
    int seaDay = -1;

    // China's plan (spec 4.3)
    int plan = -1, region, lethality = 1, dDay = -1, postponed, standDownDay = -1;
    boolean optStrikeUS, optStrikeJapan, optCyber, optDecap, optPenghu, planChosen;

    // escalation (spec 8.4-8.8)
    final int[] rung = new int[2], alert = new int[2];
    boolean usCombat, provoked, blueFirst, guamStruck, japanStruck, nukeUsed;
    double usCombatHour = -1, mainlandHits, c2Hits, homelandHits, nukeRisk, deterrenceG, blueMobilized = -1, deterSeen;
    int crisisTurns = -1, crisisCount;

    // the US directives (spec 11.5): mainland 0 none 1 limited 2 broad; fleet 0 forward 1 stand-off 2 withdrawn 3 commander's choice;
    // subs 0 the strait 1 approaches and chokepoints 2 distant blockade 3 commander's choice; munitions 0 conserve 1 normal 2 surge
    int mainland = 1, fleet = 3, subFocus = 3, munitionsPolicy = 1;
    final double[] airShares = {0.5, 0, 0.25, 0.25};
    boolean twMobiliseAsked, twRationAsked, twDisperseAsked;
    /** Directives the player has taken from the staff (keys such as "rung", "programme:US:subs"). */
    final List<String> held = new ArrayList<>();

    // the blockade (spec 7)
    int blockade, merchantsLost, seized, amphSunk;
    boolean convoys;
    double recentLosses, accSunk, accSeized, minesLaid, minesCleared, throughput = 1, arrivalsToday, airliftKt;
    double[][] mines;

    // Taiwan (spec 6.6, 7.3, 7.4, 8.7)
    double lng = 630, coal = 7000, oil = 118, terminalDmg, elec = 1, imports = 1, arrivalsAvg = 1, chips = 1, chipLag = 1, industry = 1, c2 = 1, mobilized,
            reservesReady, will = 64, unity = 0.6, civKilled, civCas, lastKilled;
    int ration, cablesCut;
    boolean gensets;

    // the economy (spec 7.5, reduced to seven economies)
    final double[] gap = new double[7];
    double brent = 92, lngPrice = 15.5, warRisk = 0.05, usEquity = 1, cS, cC, cX, cE, blockadeChina;
    int sanctUS, sanctCN;
    boolean swift, freeze, chipEmbargo, fullEmbargo, distantBlockade, rareEarths, boycott, treasury, tourism;

    // politics (spec 8.7)
    double usSupport = 46, approval = 43, congress = 52, rally, weariness, prcStability = 72, nationalism = 62;
    boolean aumf;

    // the world
    final List<Country> countries = new ArrayList<>();
    final double[] dipCap = {30, 30};
    double[][] progPct;
    boolean[][] progOn;

    // intelligence (spec 9)
    final double[] satHealth = {1, 1}, dazzle = new double[2], warning = new double[2];
    final int[] warnLevel = new int[2];
    double humint = 0.5;
    final double[] planGuess = {0.2, 0.2, 0.2, 0.2, 0.2};
    final List<Indicator> indicatorsUS = new ArrayList<>(), indicatorsPRC = new ArrayList<>();
    final List<Map<Integer, Contact>> contacts = List.of(new LinkedHashMap<>(), new LinkedHashMap<>());

    // the forces
    final List<Group> groups = new ArrayList<>();
    final List<Sub> subs = new ArrayList<>();
    final List<Squadron> squadrons = new ArrayList<>();
    final List<Battery> batteries = new ArrayList<>();
    final List<Troop> troops = new ArrayList<>();
    final List<BaseState> bases = new ArrayList<>();
    final List<SectorState> sectors = new ArrayList<>();
    final List<FacilityState> facilities = new ArrayList<>();
    double[][] pool, prod, expended;
    double[] poolStart;
    int nextId = 1;
    final Map<String, Loss> losses = new LinkedHashMap<>();

    // ceasefire (spec 8.9)
    int offerFrom = -1, offerTerms;
    double offerHour = -1;

    // the end
    int outcome = -1, endDay;
    String outcomeText, endReason;
    double scoreUS, scorePRC;

    // the record: the log, a daily history, and what has been reported to the presidency
    final List<String> log = new ArrayList<>();
    final List<String> sitrep = new ArrayList<>();
    final List<double[]> history = new ArrayList<>();
    double reportedKia, costUS, usGapSum, brentSum, chipShockMax, equityLow = 1;
    int econDays;

    // =================================================================================================================
    //  Reading
    // =================================================================================================================

    public int year() { return year; }
    /** The year the forces are projected to (the crisis year, unless an assumption anchors them elsewhere). */
    public int forcesYear() { return forcesYear; }
    public int preset() { return preset; }
    /** The player's side, "US" or "PRC". */
    public String side() { return playerSide == US ? "US" : "PRC"; }
    public int playerSide() { return playerSide; }
    public LocalDate startDate() { return start; }
    /** "crisis", "war" or "over". */
    public String phase() { return phase; }
    public boolean over() { return phase.equals("over"); }
    public boolean atWar() { return phase.equals("war"); }
    /** Game hours since the crisis began (hour 0 is midnight of day 1). */
    public int hour() { return hour; }
    /** The day of the crisis, from 1. */
    public int day() { return hour / 24 + 1; }
    /** The hour of the day, 0-23. */
    public int hourOfDay() { return hour % 24; }
    /** The calendar date. */
    public LocalDate date() { return start.plusDays(hour / 24); }
    /** Hours since H-hour, or -1 before the war. */
    public int warHours() { return dHour < 0 ? -1 : hour - dHour; }
    /** Days of war (whole and part). */
    public double warDays() { return dHour < 0 ? 0 : (hour - dHour) / 24.0; }
    /** The length of the next turn in hours: 24 in the crisis (less if H-hour falls inside it), then 1 / 6 / 12 at war, 0 when over. */
    public int turnHours() { return TaiwanEngine.turnLength(this); }
    public int turns() { return turns; }

    /** China's plan, or "" while undecided (the player as China always sees it; the US sees it only through intelligence). */
    public String plan() { return plan < 0 ? "" : TaiwanData.PLANS[plan]; }
    /** D-day (a day of the crisis), or -1 if none is set (China stood down or has not chosen). */
    public int dDay() { return dDay; }
    /** The hour H-hour is set for, or -1. */
    public int launchHour() { return launchHour; }
    public int region() { return region; }
    public int lethality() { return lethality; }
    public boolean strikeUSAtHHour() { return optStrikeUS; }
    public boolean strikeJapanAtHHour() { return optStrikeJapan; }
    public boolean cyberAtHHour() { return optCyber; }
    public boolean decapitation() { return optDecap; }
    public boolean penghuFirst() { return optPenghu; }
    public boolean chinaStoodDown() { return standDownDay >= 0; }

    /** China's rung 0-6 ({@link #RED_RUNG}). */
    public int chinaRung() { return rung[PRC]; }
    /** America's rung 0-6 ({@link #BLUE_RUNG}). */
    public int usRung() { return rung[US]; }
    /** Nuclear alert 0-3 of a side. */
    public int nuclearAlert(int side) { return alert[side]; }
    /** Nuclear risk 0-100. */
    public double nuclearRisk() { return nukeRisk; }
    /** True while a nuclear crisis is open (risk above 70, for three days). */
    public boolean nuclearCrisis() { return crisisTurns >= 0; }
    public int nuclearCrises() { return crisisCount; }
    public boolean usFighting() { return usCombat; }
    /** China struck US forces first (the provoked entry). */
    public boolean provoked() { return provoked; }
    public boolean usStruckFirst() { return blueFirst; }
    public double mainlandHits() { return mainlandHits; }
    /** Beijing's view of deterrence, the number its go/no-go rule compares with 60 (spec 4.3). */
    public double deterrenceAsSeen() { return deterSeen; }
    /** The deterrence counter raised by US programmes and sanctions (the source's g.deterrence). */
    public double deterrenceCounter() { return deterrenceG; }

    // the US directives
    public int mainlandStrikes() { return mainland; }
    public int fleetPosture() { return fleet; }
    public int submarineFocus() { return subFocus; }
    public int munitionsPolicy() { return munitionsPolicy; }
    /** Shares of strike-capable fighters: anti-ship strikes, mainland strikes, CAP over Taiwan, fleet defence and escorts. */
    public double[] airPriority() { return airShares.clone(); }
    public boolean convoys() { return convoys; }
    /** Directives the player has taken from the staff. */
    public List<String> heldDirectives() { return Collections.unmodifiableList(held); }
    public String commitment() { return commitment; }
    /** Both sides run by the computer, ceasefire proposals included (the harness). */
    public boolean autoplay() { return autoplay; }

    // the blockade
    /** Blockade level 0-4 ({@link #BLOCKADE}). */
    public int blockadeLevel() { return blockade; }
    /** Shipping reaching Taiwan as a share of normal (1 = 26 large ships a day). */
    public double throughput() { return throughput; }
    public int merchantsSunk() { return merchantsLost; }
    public int merchantsSeized() { return seized; }
    /** PLA amphibious and landing ships sunk. */
    public int plaAmphibiousSunk() { return amphSunk; }
    /** PRC mines laid and cleared (the covert programme and air-laid mines). */
    public double minesLaid() { return minesLaid; }
    public double minesCleared() { return minesCleared; }
    /** Mines a side has laid in a zone. */
    public double mines(int side, int zone) { return mines[side][zone]; }

    // Taiwan
    /** Taiwan's electricity, share of normal supply 0-1. */
    public double electricity() { return elec; }
    /** Taiwan's chip output, share of normal 0-1. */
    public double chips() { return chips; }
    /** The chip shock the world feels (inventories lag the output): 0 none, 1 total. */
    public double chipShock() { return 1 - chipLag; }
    public double industry() { return industry; }
    /** Taiwan's will to resist, 0-100. */
    public double will() { return will; }
    /** Taiwan's command and control, 0-1. */
    public double command() { return c2; }
    /** Fuel stocks: LNG million cubic metres, coal thousand tonnes, oil million barrels. */
    public double lngStock() { return lng; }
    public double coalStock() { return coal; }
    public double oilStock() { return oil; }
    /** Imports (a smoothed share of normal arrivals). */
    public double imports() { return imports; }
    /** 0 none, 1 voluntary, 2 mandatory rationing. */
    public int rationing() { return ration; }
    /** Taiwan's reserves called up: 0, 0.5 partial, 1 full. */
    public double mobilization() { return mobilized; }
    public int cablesCut() { return cablesCut; }
    public double civiliansKilled() { return civKilled; }

    // the ground war
    public List<SectorState> sectors() { return Collections.unmodifiableList(sectors); }
    public List<FacilityState> facilities() { return Collections.unmodifiableList(facilities); }
    public List<Troop> troops() { return Collections.unmodifiableList(troops); }
    /** Square kilometres the PLA holds (a lodgement holds ground around it, spec 6.7). */
    public double territoryKm2() { return TaiwanLand.heldKm2(this); }
    /** PLA battalions ashore on Taiwan. */
    public double plaAshore() { double v = 0; for (Troop t : troops) if (t.side == PRC && t.sector >= 0 && t.state != GS_DEAD && t.state != GS_EMBARKED) v += t.bns; return v; }
    /** Taiwanese battalions ready or moving. */
    public double rocReady() { double v = 0; for (Troop t : troops) if (t.side == US && (t.state == GS_READY || t.state == GS_MOVING)) v += t.bns; return v; }

    // the forces
    public List<Group> groups() { return Collections.unmodifiableList(groups); }
    public List<Sub> subs() { return Collections.unmodifiableList(subs); }
    public List<Squadron> squadrons() { return Collections.unmodifiableList(squadrons); }
    public List<Battery> batteries() { return Collections.unmodifiableList(batteries); }
    public List<BaseState> bases() { return Collections.unmodifiableList(bases); }
    /** A side's groups in a zone (afloat, on the map). */
    public List<Group> groupsIn(int zone, int side) {
        List<Group> l = new ArrayList<>();
        for (Group g : groups) if (g.side == side && g.at == zone && g.hub < 0 && !g.gone && !g.dead()) l.add(g);
        return l;
    }
    /** What a side believes about the enemy: its contacts, in the order first seen. */
    public List<Contact> contacts(int side) { return List.copyOf(contacts.get(side).values()); }
    /** The player's contacts in a zone: the enemy as the player's side sees it. */
    public List<Contact> contactsIn(int zone) {
        List<Contact> l = new ArrayList<>();
        for (Contact c : contacts.get(playerSide).values()) if (c.zone == zone) l.add(c);
        return l;
    }
    /** Error of a contact now, km: it grows 28 km an hour (10 for submarines) since last seen. */
    public double contactError(Contact c) { return c.err + (hour - c.seen) * (c.kind == TK_SUB ? 10 : 28); }

    // munitions and losses
    /** Stock of a munition in a pool (pool index into {@link TaiwanData#POOLS}). */
    public double stock(int pool, int munition) { return this.pool[pool][munition]; }
    /** Munitions a side has fired, by munition index. */
    public double expended(int side, int munition) { return expended[side][munition]; }
    /** US munitions fired, by the ids of data/munitions.csv where they match (for the presidency's write-back). */
    public Map<String, Integer> usExpended(TaiwanData d) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < d.munitions.size(); i++) if (expended[US][i] > 0 && d.munitions.get(i).side() == US) m.put(d.munitions.get(i).id(), (int) expended[US][i]);
        return m;
    }
    /** Losses by nation ({@link #NATIONS}). */
    public Loss losses(String nation) { Loss l = losses.get(nation); return l == null ? new Loss() : l; }
    public Map<String, Loss> allLosses() { return Collections.unmodifiableMap(losses); }
    public int usKilled() { return (int) losses("USA").killed; }

    // the world
    public List<Country> countries() { return Collections.unmodifiableList(countries); }
    /** Diplomatic capital of a side (0-60). */
    public double diplomaticCapital(int side) { return dipCap[side]; }
    /** Progress of a side's programme, 0-100 (index in that side's list). */
    public double programme(int side, int i) { return progPct[side][i]; }
    public boolean programmeOn(int side, int i) { return progOn[side][i]; }

    // intelligence
    /** The warning estimate of a side, 0-1 (the US: chance of PRC military action within 30 days). */
    public double warning(int side) { return warning[side]; }
    /** US warning level 0-4 ({@link #WARNING}); China's view of US readiness 0-3. */
    public int warningLevel(int side) { return warnLevel[side]; }
    /** The US assessment of China's most likely course: shares over invasion, blockade, quarantine, islands, strikes. */
    public double[] planEstimate() { return planGuess.clone(); }
    public List<Indicator> indicators(int side) { return Collections.unmodifiableList(side == US ? indicatorsUS : indicatorsPRC); }
    public double satelliteHealth(int side) { return satHealth[side]; }

    // the economy
    /** Output gaps (fraction below the pre-crisis path) of {@link #ECONOMIES}. */
    public double gap(int economy) { return gap[economy]; }
    /** The five channels, each 0-1: strait shipping, chips, sanctions, energy price; war damage is per economy. */
    public double channelStrait() { return cS; }
    public double channelChips() { return cC; }
    public double channelSanctions() { return cX; }
    public double channelEnergy() { return cE; }
    /** Brent, $ a barrel; LNG, $ per million BTU; war-risk insurance, % of hull value. */
    public double brent() { return brent; }
    public double lngPrice() { return lngPrice; }
    public double warRiskInsurance() { return warRisk; }
    /** US stocks relative to their pre-crisis level (1 = unchanged). */
    public double usEquity() { return usEquity; }
    public int usSanctions() { return sanctUS; }
    public boolean swiftCut() { return swift; }
    public boolean assetFreeze() { return freeze; }
    public boolean chipEmbargo() { return chipEmbargo; }
    public boolean distantBlockade() { return distantBlockade; }
    public int chinaCounterSanctions() { return sanctCN; }
    public boolean rareEarthBan() { return rareEarths; }

    // politics
    /** US support for defending Taiwan, 5-95 (crisis-local; in the presidency AGS owns approval and Congress). */
    public double usSupport() { return usSupport; }
    /** The standalone scenario's own approval and Congress, as in the source. */
    public double approval() { return approval; }
    public double congress() { return congress; }
    public boolean warAuthorized() { return aumf; }
    public double prcStability() { return prcStability; }
    public double nationalism() { return nationalism; }

    // ceasefire and the end
    /** A pending ceasefire offer: the side that made it, or -1. */
    public int offerFrom() { return offerFrom; }
    /** Its terms: 0 status quo ante, 1 ceasefire in place, 2 unification talks. */
    public int offerTerms() { return offerTerms; }
    /** The source's outcome code (0-9, see {@link TaiwanEngine#OUTCOMES}), or -1 while it goes on. */
    public int outcomeCode() { return outcome; }
    /** The outcome's name ("Taiwan defended" ...), or null while it goes on. */
    public String outcome() { return outcomeText; }
    public String endReason() { return endReason; }
    /** Scores 0-100 (spec 8.9). */
    public double score(int side) { return side == US ? scoreUS : scorePRC; }

    /** The log, newest last (the last 300 lines). */
    public List<String> log() { return Collections.unmodifiableList(log); }
    /** What happened in the last turn: alerts, sinkings, landings, entries into the war. */
    public List<String> sitrep() { return Collections.unmodifiableList(sitrep); }
    /** One row a day: day, US killed, PRC killed, Taiwan killed (military and civilian), US ships lost, PRC ships lost, US aircraft
     *  lost, PRC aircraft lost, PLA battalions ashore, km2 held, electricity, throughput, nuclear risk, world gap, Taiwan's will,
     *  US support. */
    public List<double[]> history() { return Collections.unmodifiableList(history); }

    // the assumptions in force
    public boolean taiwanReactsLate() { return taiwanDelay; }
    public double prcMissileFactor() { return prcMissiles; }
    public double taiwanGroundFactor() { return twGround; }
    public String assumptions() { return assumptions; }

    // =================================================================================================================
    //  Saving
    // =================================================================================================================

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 1);
        m.put("start", start.toString());
        m.put("s", new W().s(phase).s(fixedPlan).s(commitment).s(assumptions).s(outcomeText).s(endReason).s(Long.toString(planSeed)).l);
        m.put("i", new W().i(year).i(forcesYear).i(preset).i(playerSide).i(difficultyUS).i(difficultyPRC).i(warningMonths).i(japanEntry).i(hour).i(dHour).i(launchHour)
                .i(blueStrikeHour).i(turns).i(endHour).i(aiDay0).i(aiDay1).i(peaceDay).i(peaceDayPRC).i(groundHour0).i(groundHour1).i(seaDay).i(plan).i(region)
                .i(lethality).i(dDay).i(postponed).i(standDownDay).i(rung[0]).i(rung[1]).i(alert[0]).i(alert[1]).i(crisisTurns).i(crisisCount).i(mainland)
                .i(fleet).i(subFocus).i(munitionsPolicy).i(blockade).i(merchantsLost).i(seized).i(ration).i(cablesCut).i(sanctUS).i(sanctCN).i(warnLevel[0])
                .i(warnLevel[1]).i(nextId).i(offerFrom).i(offerTerms).i(outcome).i(endDay).i(econDays).i(amphSunk).l);
        m.put("d", new W().d(deterrenceIn).d(munitionsIn).d(competence).d(prcMissiles).d(twGround).d(plaAmphib).d(seaState).d(usCombatHour).d(mainlandHits)
                .d(c2Hits).d(homelandHits).d(nukeRisk).d(deterrenceG).d(blueMobilized).d(airShares[0]).d(airShares[1]).d(airShares[2]).d(airShares[3])
                .d(recentLosses).d(accSunk).d(accSeized).d(minesLaid).d(minesCleared).d(throughput).d(arrivalsToday).d(airliftKt).d(lng).d(coal).d(oil)
                .d(terminalDmg).d(elec).d(imports).d(arrivalsAvg).d(chips).d(chipLag).d(industry).d(c2).d(mobilized).d(reservesReady).d(will).d(unity)
                .d(civKilled).d(civCas).d(lastKilled).d(brent).d(lngPrice).d(warRisk).d(usEquity).d(cS).d(cC).d(cX).d(cE).d(blockadeChina).d(usSupport)
                .d(approval).d(congress).d(rally).d(weariness).d(prcStability).d(nationalism).d(dipCap[0]).d(dipCap[1]).d(satHealth[0]).d(satHealth[1])
                .d(dazzle[0]).d(dazzle[1]).d(warning[0]).d(warning[1]).d(humint).d(offerHour).d(scoreUS).d(scorePRC).d(reportedKia).d(costUS).d(usGapSum)
                .d(brentSum).d(chipShockMax).d(equityLow).d(deterSeen).l);
        m.put("b", new W().b(japanBasingIn).b(civilAirportsSet).b(phlBasingSet).b(taiwanDelay).b(jassmMaritime).b(shipDefensePoor).b(usLate).b(subsWithheld)
                .b(simultaneous).b(extraHAS).b(mainlandAllowed).b(jsdfDefensive).b(strikeJapanEnabled).b(optStrikeUS).b(optStrikeJapan).b(optCyber).b(optDecap)
                .b(optPenghu).b(planChosen).b(usCombat).b(provoked).b(blueFirst).b(guamStruck).b(japanStruck).b(nukeUsed).b(twMobiliseAsked).b(twRationAsked)
                .b(twDisperseAsked).b(convoys).b(gensets).b(swift).b(freeze).b(chipEmbargo).b(fullEmbargo).b(distantBlockade).b(rareEarths).b(boycott)
                .b(treasury).b(tourism).b(aumf).b(autoplay).l);
        m.put("gap", nums(gap));
        m.put("guess", nums(planGuess));
        m.put("mines", List.of(nums(mines[0]), nums(mines[1])));
        m.put("prog", List.of(nums(progPct[0]), nums(progPct[1])));
        m.put("progOn", List.of(bools(progOn[0]), bools(progOn[1])));
        m.put("held", new ArrayList<Object>(held));
        m.put("countries", list(countries, Country::json));
        m.put("indUS", list(indicatorsUS, Indicator::json));
        m.put("indPRC", list(indicatorsPRC, Indicator::json));
        m.put("contactsUS", list(new ArrayList<>(contacts.get(US).values()), Contact::json));
        m.put("contactsPRC", list(new ArrayList<>(contacts.get(PRC).values()), Contact::json));
        m.put("groups", list(groups, Group::json));
        m.put("subs", list(subs, Sub::json));
        m.put("squadrons", list(squadrons, Squadron::json));
        m.put("batteries", list(batteries, Battery::json));
        m.put("troops", list(troops, Troop::json));
        m.put("bases", list(bases, BaseState::json));
        m.put("sectors", list(sectors, SectorState::json));
        m.put("facilities", list(facilities, FacilityState::json));
        List<Object> pools = new ArrayList<>();
        for (int p = 0; p < pool.length; p++) pools.add(List.of(sparse(pool[p]), sparse(prod[p])));
        m.put("pools", pools);
        m.put("expended", List.of(sparse(expended[0]), sparse(expended[1])));
        m.put("poolStart", sparse(poolStart));
        Map<String, Object> lo = new LinkedHashMap<>();
        for (Map.Entry<String, Loss> e : losses.entrySet()) lo.put(e.getKey(), e.getValue().json());
        m.put("losses", lo);
        m.put("log", new ArrayList<Object>(log));
        m.put("sitrep", new ArrayList<Object>(sitrep));
        List<Object> h = new ArrayList<>();
        for (double[] r : history) h.add(nums(r));
        m.put("history", h);
        return m;
    }

    public static TaiwanState fromJson(Map<String, Object> m) {
        TaiwanState s = new TaiwanState();
        s.start = LocalDate.parse((String) m.get("start"));
        R st = new R(m.get("s"));
        s.phase = st.s(); s.fixedPlan = st.s(); s.commitment = st.s(); s.assumptions = st.s(); s.outcomeText = st.s(); s.endReason = st.s();
        s.planSeed = Long.parseLong(st.s());
        R i = new R(m.get("i"));
        s.year = i.i(); s.forcesYear = i.i(); s.preset = i.i(); s.playerSide = i.i(); s.difficultyUS = i.i(); s.difficultyPRC = i.i(); s.warningMonths = i.i(); s.japanEntry = i.i();
        s.hour = i.i(); s.dHour = i.i(); s.launchHour = i.i(); s.blueStrikeHour = i.i(); s.turns = i.i(); s.endHour = i.i(); s.aiDay0 = i.i(); s.aiDay1 = i.i();
        s.peaceDay = i.i(); s.peaceDayPRC = i.i(); s.groundHour0 = i.i(); s.groundHour1 = i.i(); s.seaDay = i.i(); s.plan = i.i(); s.region = i.i();
        s.lethality = i.i(); s.dDay = i.i(); s.postponed = i.i(); s.standDownDay = i.i(); s.rung[0] = i.i(); s.rung[1] = i.i(); s.alert[0] = i.i();
        s.alert[1] = i.i(); s.crisisTurns = i.i(); s.crisisCount = i.i(); s.mainland = i.i(); s.fleet = i.i(); s.subFocus = i.i(); s.munitionsPolicy = i.i();
        s.blockade = i.i(); s.merchantsLost = i.i(); s.seized = i.i(); s.ration = i.i(); s.cablesCut = i.i(); s.sanctUS = i.i(); s.sanctCN = i.i();
        s.warnLevel[0] = i.i(); s.warnLevel[1] = i.i(); s.nextId = i.i(); s.offerFrom = i.i(); s.offerTerms = i.i(); s.outcome = i.i(); s.endDay = i.i();
        s.econDays = i.i(); s.amphSunk = i.i();
        R d = new R(m.get("d"));
        s.deterrenceIn = d.d(); s.munitionsIn = d.d(); s.competence = d.d(); s.prcMissiles = d.d(); s.twGround = d.d(); s.plaAmphib = d.d(); s.seaState = d.d();
        s.usCombatHour = d.d(); s.mainlandHits = d.d(); s.c2Hits = d.d(); s.homelandHits = d.d(); s.nukeRisk = d.d(); s.deterrenceG = d.d(); s.blueMobilized = d.d();
        s.airShares[0] = d.d(); s.airShares[1] = d.d(); s.airShares[2] = d.d(); s.airShares[3] = d.d(); s.recentLosses = d.d(); s.accSunk = d.d();
        s.accSeized = d.d(); s.minesLaid = d.d(); s.minesCleared = d.d(); s.throughput = d.d(); s.arrivalsToday = d.d(); s.airliftKt = d.d(); s.lng = d.d();
        s.coal = d.d(); s.oil = d.d(); s.terminalDmg = d.d(); s.elec = d.d(); s.imports = d.d(); s.arrivalsAvg = d.d(); s.chips = d.d(); s.chipLag = d.d();
        s.industry = d.d(); s.c2 = d.d(); s.mobilized = d.d(); s.reservesReady = d.d(); s.will = d.d(); s.unity = d.d(); s.civKilled = d.d(); s.civCas = d.d();
        s.lastKilled = d.d(); s.brent = d.d(); s.lngPrice = d.d(); s.warRisk = d.d(); s.usEquity = d.d(); s.cS = d.d(); s.cC = d.d(); s.cX = d.d(); s.cE = d.d();
        s.blockadeChina = d.d(); s.usSupport = d.d(); s.approval = d.d(); s.congress = d.d(); s.rally = d.d(); s.weariness = d.d(); s.prcStability = d.d();
        s.nationalism = d.d(); s.dipCap[0] = d.d(); s.dipCap[1] = d.d(); s.satHealth[0] = d.d(); s.satHealth[1] = d.d(); s.dazzle[0] = d.d(); s.dazzle[1] = d.d();
        s.warning[0] = d.d(); s.warning[1] = d.d(); s.humint = d.d(); s.offerHour = d.d(); s.scoreUS = d.d(); s.scorePRC = d.d(); s.reportedKia = d.d();
        s.costUS = d.d(); s.usGapSum = d.d(); s.brentSum = d.d(); s.chipShockMax = d.d(); s.equityLow = d.d(); s.deterSeen = d.d();
        R b = new R(m.get("b"));
        s.japanBasingIn = b.b(); s.civilAirportsSet = b.b(); s.phlBasingSet = b.b(); s.taiwanDelay = b.b(); s.jassmMaritime = b.b(); s.shipDefensePoor = b.b();
        s.usLate = b.b(); s.subsWithheld = b.b(); s.simultaneous = b.b(); s.extraHAS = b.b(); s.mainlandAllowed = b.b(); s.jsdfDefensive = b.b();
        s.strikeJapanEnabled = b.b(); s.optStrikeUS = b.b(); s.optStrikeJapan = b.b(); s.optCyber = b.b(); s.optDecap = b.b(); s.optPenghu = b.b();
        s.planChosen = b.b(); s.usCombat = b.b(); s.provoked = b.b(); s.blueFirst = b.b(); s.guamStruck = b.b(); s.japanStruck = b.b(); s.nukeUsed = b.b();
        s.twMobiliseAsked = b.b(); s.twRationAsked = b.b(); s.twDisperseAsked = b.b(); s.convoys = b.b(); s.gensets = b.b(); s.swift = b.b(); s.freeze = b.b();
        s.chipEmbargo = b.b(); s.fullEmbargo = b.b(); s.distantBlockade = b.b(); s.rareEarths = b.b(); s.boycott = b.b(); s.treasury = b.b(); s.tourism = b.b();
        s.aumf = b.b(); s.autoplay = b.b();
        unnums(m.get("gap"), s.gap);
        unnums(m.get("guess"), s.planGuess);
        List<Object> mi = Json.array(m.get("mines"));
        s.mines = new double[][]{dnums(mi.get(0)), dnums(mi.get(1))};
        List<Object> pr = Json.array(m.get("prog"));
        s.progPct = new double[][]{dnums(pr.get(0)), dnums(pr.get(1))};
        List<Object> po = Json.array(m.get("progOn"));
        s.progOn = new boolean[][]{bnums(po.get(0)), bnums(po.get(1))};
        for (Object o : Json.array(m.get("held"))) s.held.add((String) o);
        for (Object o : Json.array(m.get("countries"))) s.countries.add(Country.of(o));
        for (Object o : Json.array(m.get("indUS"))) s.indicatorsUS.add(Indicator.of(o));
        for (Object o : Json.array(m.get("indPRC"))) s.indicatorsPRC.add(Indicator.of(o));
        for (Object o : Json.array(m.get("contactsUS"))) { Contact c = Contact.of(o); s.contacts.get(US).put(c.id, c); }
        for (Object o : Json.array(m.get("contactsPRC"))) { Contact c = Contact.of(o); s.contacts.get(PRC).put(c.id, c); }
        List<Object> pools = Json.array(m.get("pools"));
        List<Object> exp = Json.array(m.get("expended"));
        s.poolStart = null;
        int nMun = Json.integer(Json.array(exp.get(0)).get(0));   // the munition count travels with the sparse arrays' declared length
        s.pool = new double[pools.size()][];
        s.prod = new double[pools.size()][];
        for (int p = 0; p < pools.size(); p++) {
            List<Object> pp = Json.array(pools.get(p));
            s.pool[p] = dsparse(pp.get(0));
            s.prod[p] = dsparse(pp.get(1));
        }
        s.expended = new double[][]{dsparse(exp.get(0)), dsparse(exp.get(1))};
        s.poolStart = dsparse(m.get("poolStart"));
        for (Object o : Json.array(m.get("groups"))) s.groups.add(Group.of(Json.object(o), nMun));
        for (Object o : Json.array(m.get("subs"))) s.subs.add(Sub.of(Json.object(o)));
        for (Object o : Json.array(m.get("squadrons"))) s.squadrons.add(Squadron.of(Json.object(o)));
        for (Object o : Json.array(m.get("batteries"))) s.batteries.add(Battery.of(Json.object(o)));
        for (Object o : Json.array(m.get("troops"))) s.troops.add(Troop.of(Json.object(o)));
        for (Object o : Json.array(m.get("bases"))) s.bases.add(BaseState.of(o));
        for (Object o : Json.array(m.get("sectors"))) s.sectors.add(SectorState.of(o));
        for (Object o : Json.array(m.get("facilities"))) s.facilities.add(FacilityState.of(o));
        for (Map.Entry<String, Object> e : Json.object(m.get("losses")).entrySet()) s.losses.put(e.getKey(), Loss.of(e.getValue()));
        for (Object o : Json.array(m.get("log"))) s.log.add((String) o);
        for (Object o : Json.array(m.get("sitrep"))) s.sitrep.add((String) o);
        for (Object o : Json.array(m.get("history"))) s.history.add(dnums(o));
        return s;
    }

    // ---- compact JSON helpers --------------------------------------------------------------------------------------

    /** Writes fields in order into a JSON array. */
    static final class W {
        final List<Object> l = new ArrayList<>();
        W d(double v) { l.add(v); return this; }
        W i(int v) { l.add(v); return this; }
        W b(boolean v) { l.add(v); return this; }
        W s(String v) { l.add(v); return this; }
    }

    /** Reads them back in the same order. */
    static final class R {
        final List<Object> l;
        int k;
        R(Object o) { l = Json.array(o); }
        double d() { return Json.num(l.get(k++)); }
        int i() { return Json.integer(l.get(k++)); }
        boolean b() { return (Boolean) l.get(k++); }
        String s() { return (String) l.get(k++); }
    }

    interface ToJson<T> { Object json(T t); }

    static <T> List<Object> list(List<T> l, ToJson<T> f) {
        List<Object> out = new ArrayList<>();
        for (T t : l) out.add(f.json(t));
        return out;
    }

    static List<Object> nums(double[] a) { List<Object> l = new ArrayList<>(); for (double v : a) l.add(v); return l; }
    static List<Object> bools(boolean[] a) { List<Object> l = new ArrayList<>(); for (boolean v : a) l.add(v); return l; }
    static void unnums(Object o, double[] into) { List<Object> l = Json.array(o); for (int i = 0; i < into.length; i++) into[i] = Json.num(l.get(i)); }
    static double[] dnums(Object o) { List<Object> l = Json.array(o); double[] a = new double[l.size()]; for (int i = 0; i < a.length; i++) a[i] = Json.num(l.get(i)); return a; }
    static boolean[] bnums(Object o) { List<Object> l = Json.array(o); boolean[] a = new boolean[l.size()]; for (int i = 0; i < a.length; i++) a[i] = (Boolean) l.get(i); return a; }

    /** A mostly-zero array as [length, index, value, index, value, ...]. */
    static List<Object> sparse(int[] a) {
        List<Object> l = new ArrayList<>();
        l.add(a.length);
        for (int i = 0; i < a.length; i++) if (a[i] != 0) { l.add(i); l.add(a[i]); }
        return l;
    }

    static List<Object> sparse(double[] a) {
        List<Object> l = new ArrayList<>();
        l.add(a.length);
        for (int i = 0; i < a.length; i++) if (a[i] != 0) { l.add(i); l.add(a[i]); }
        return l;
    }

    static int[] unsparse(Object o, int n) {
        List<Object> l = Json.array(o);
        int[] a = new int[Json.integer(l.get(0))];
        for (int k = 1; k + 1 < l.size(); k += 2) a[Json.integer(l.get(k))] = Json.integer(l.get(k + 1));
        return a;
    }

    static double[] dsparse(Object o) {
        List<Object> l = Json.array(o);
        double[] a = new double[Json.integer(l.get(0))];
        for (int k = 1; k + 1 < l.size(); k += 2) a[Json.integer(l.get(k))] = Json.num(l.get(k + 1));
        return a;
    }
}
