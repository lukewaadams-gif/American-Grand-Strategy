package game.mandate.core.taiwan;

import game.mandate.core.data.Csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Taiwan war's tables ({@code /data/taiwan/*.csv}, docs/ISLAND_CHAIN.md): munitions, ship classes, submarine classes,
 * aircraft, land systems, the sea zones and the sea-distance matrix, bases, hubs, Taiwan's sectors, beaches and facilities,
 * the reference points the rules measure from, the countries and economies, the preparation programmes and the order of
 * battle by year. Positions are kilometres on a flat projection centred on Taiwan (computed offline), so every distance is
 * plain arithmetic. Immutable once loaded; the state refers to rows by their index.
 */
public final class TaiwanData {
    public static final int US = 0, PRC = 1;
    /** Speed classes of munitions. */
    public static final int SUBSONIC = 0, STEALTH = 1, SUPERSONIC = 2, BALLISTIC = 3, HYPERSONIC = 4;
    /** Aircraft role bits. */
    public static final int FTR = 1, STRIKE = 2, BMB = 4, AEW = 8, ASW = 16, TNK = 32, ISR = 64, LIFT = 128, EW = 256, HELO = 512;
    /** Base flags. */
    public static final int F_AIR = 1, F_NAVAL = 2, F_ARMY = 4, F_MISSILE = 8, F_PORT = 16, F_CIVIL = 32, F_C2 = 64, F_ENERGY = 128;
    /** Terrain and its defence multiplier (spec 6.1). */
    public static final String[] TERRAIN = {"urban", "plain", "mountain", "island"};
    public static final double[] TERRAIN_DEF = {2.0, 1.0, 2.6, 1.3};
    /** Ground formation types: lift tonnes per battalion and combat value per battalion (spec 3.1). */
    public static final String[] GTYPE = {"infantry", "mech", "armour", "marine", "airborne", "airassault", "reserve", "sof", "amphib", "heavy", "garrison"};
    public static final double[] GTONS = {1800, 3000, 3600, 2400, 1500, 1500, 1800, 900, 2800, 3600, 1800};
    public static final double[] GPOW = {0.9, 1.1, 1.25, 1.0, 0.85, 0.8, 0.5, 0.7, 1.1, 1.25, 0.6};
    /** Munition pools (spec 3.4). */
    public static final String[] POOLS = {"US_TH", "US_GL", "JP", "TW", "AU", "KR", "PH", "PRC"};
    public static final int P_US_TH = 0, P_US_GL = 1, P_JP = 2, P_TW = 3, P_AU = 4, P_KR = 5, P_PH = 6, P_PRC = 7;
    public static final String[] PLANS = {"invasion", "blockade", "quarantine", "islands", "strikes"};

    public record Munition(String id, String name, int side, String kind, int speed, double range, double power, boolean antiShip, boolean land,
                           boolean cluster, double cost) {
        /** Ballistic, glide and rocket weapons count as ballistic for interception. */
        public boolean ballistic() { return kind.equals("BM") || kind.equals("HGV") || kind.equals("ROCKET"); }
        public boolean mine() { return kind.equals("MINE"); }
    }

    public record ShipClass(String id, String name, String nation, String role, double hp, double knots, int fire, int samL, int samS, int ascm, int ascmType,
                            int lacm, int lacmType, int special, int specialType, double asw, double lift, int crew) {
        public boolean carrier() { return role.equals("carrier"); }
        public boolean amphib() { return role.equals("amphib"); }
        /** Weight in the blockade zone (spec 7.2): coast guard 1, militia 0.3, warships 1.8, others 0.4. */
        public double blockadeWeight() {
            return switch (role) {
                case "coastguard" -> 1.0;
                case "militia" -> 0.3;
                case "carrier", "cruiser", "destroyer", "frigate", "corvette" -> 1.8;
                default -> 0.4;
            };
        }
    }

    public record SubClass(String id, String name, String nation, boolean nuclear, double quiet, double sprint, double patrol, int torps, int missiles,
                           int missileType, double endurance, int crew) {}

    public record AirType(String id, String name, String nation, int roles, double quality, double radius, double kmh, double stealth, int load,
                          double sorties, int[] weapons) {
        public boolean is(int role) { return (roles & role) != 0; }
    }

    public record LandSys(String id, String name, String nation, boolean sam, int munition, int rounds, double reloadH, int channels, double reach,
                          double pkBallistic, double pkGlide, double pkCruise, double airReach) {}

    public record Zone(String id, String name, double lat, double lon, double r, double x, double y, boolean strait, boolean blockade, boolean taiwanAir) {}

    public record Base(String id, String name, String cc, String region, double lat, double lon, int flags, int runways, int parking, int shelters,
                       int underground, double x, double y, int zone, int sector, int place) {
        public boolean has(int f) { return (flags & f) != 0; }
    }

    public record Hub(String id, String name, double shipDays, double airHours, double x, double y, int entryZone, int place) {}

    public record Sector(String id, String name, int terrain, double pop, double km2, double lat, double lon, double x, double y, int[] adj) {}

    public record Beach(String id, String name, int sector, double quality, double x, double y, int zone, String region, int place) {}

    public record Facility(String id, String name, int sector, boolean port, double kt, double x, double y, int place) {}

    public record Point(String id, double x, double y) {}

    public record Country(String id, String name, String ags, boolean aligned, double stance0, double fear, double trust, double exposure) {}

    public record Economy(String id, String name, double gdp, double g0, double[] w) {}

    public record Programme(int side, String id, String name, double days, double signal, int lead, int focus, String neededBy, String indicator) {
        public boolean needed(int plan) { return plan >= 0 && neededBy.indexOf("IBQSF".charAt(plan)) >= 0; }
    }

    /** One row of the order of battle: its kind and raw fields (see oob.csv's header). */
    public record Oob(String kind, String[] f) {
        public String s(int i) { return i < f.length ? f[i] : ""; }
        public double d(int i) { return i < f.length && !f[i].isEmpty() ? Double.parseDouble(f[i]) : 0; }
        public int i(int i) { return (int) d(i); }
    }

    final List<Munition> munitions = new ArrayList<>();
    final List<ShipClass> ships = new ArrayList<>();
    final List<SubClass> subs = new ArrayList<>();
    final List<AirType> aircraft = new ArrayList<>();
    final List<LandSys> land = new ArrayList<>();
    final List<Zone> zones = new ArrayList<>();
    final List<Base> bases = new ArrayList<>();
    final List<Hub> hubs = new ArrayList<>();
    final List<Sector> sectors = new ArrayList<>();
    final List<Beach> beaches = new ArrayList<>();
    final List<Facility> facilities = new ArrayList<>();
    final List<Point> points = new ArrayList<>();
    final List<Country> countries = new ArrayList<>();
    final List<Economy> economies = new ArrayList<>();
    final List<Programme> programmes = new ArrayList<>();
    final List<Oob> oob = new ArrayList<>();
    /** The sea places (zones, ports, beaches, hub edges), their positions and zones, and sea km between them. */
    final List<String> places = new ArrayList<>();
    double[] placeX, placeY;
    int[] placeZone, placeBase, placeBeach, placeHub;
    double[][] km;
    final Map<String, Integer> idx = new LinkedHashMap<>();
    /** Zone indexes: the 22 sea zones, then {@link #twZone} (the airspace over Taiwan). */
    int twZone;
    int seaZones;

    private TaiwanData() {}

    public static TaiwanData load() {
        TaiwanData d = new TaiwanData();
        for (String[] r : Csv.readResource("/data/taiwan/munitions.csv"))
            d.munitions.add(new Munition(r[0], r[1], r[2].equals("PRC") ? PRC : US, r[3], speed(r[4]), num(r[5]), num(r[6]), bool(r[7]), bool(r[8]), bool(r[9]), num(r[10])));
        for (int i = 0; i < d.munitions.size(); i++) d.idx.put("m:" + d.munitions.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/ships.csv"))
            d.ships.add(new ShipClass(r[0], r[1], r[2], r[3], num(r[4]), num(r[5]), (int) num(r[6]), (int) num(r[7]), (int) num(r[8]), (int) num(r[9]), d.mun(r[10]),
                    (int) num(r[11]), d.mun(r[12]), (int) num(r[13]), d.mun(r[14]), num(r[15]), num(r[16]), (int) num(r[17])));
        for (String[] r : Csv.readResource("/data/taiwan/subs.csv"))
            d.subs.add(new SubClass(r[0], r[1], r[2], bool(r[3]), num(r[4]), num(r[5]), num(r[6]), (int) num(r[7]), (int) num(r[8]), d.mun(r[9]), num(r[10]), (int) num(r[11])));
        for (String[] r : Csv.readResource("/data/taiwan/aircraft.csv")) {
            int roles = 0;
            for (String x : r[3].split("\\+")) roles |= switch (x) {
                case "FTR" -> FTR; case "STRIKE" -> STRIKE; case "BMB" -> BMB; case "AEW" -> AEW; case "ASW" -> ASW; case "TNK" -> TNK;
                case "ISR" -> ISR; case "LIFT" -> LIFT; case "EW" -> EW; case "HELO" -> HELO;
                default -> throw new IllegalStateException("aircraft role " + x);
            };
            String[] w = r[10].isEmpty() ? new String[0] : r[10].split(";");
            int[] ws = new int[w.length];
            for (int i = 0; i < w.length; i++) ws[i] = d.mun(w[i]);
            d.aircraft.add(new AirType(r[0], r[1], r[2], roles, num(r[4]), num(r[5]), num(r[6]), num(r[7]), (int) num(r[8]), num(r[9]), ws));
        }
        for (String[] r : Csv.readResource("/data/taiwan/land.csv"))
            d.land.add(new LandSys(r[0], r[1], r[2], bool(r[3]), d.mun(r[4]), (int) num(r[5]), num(r[6]), (int) num(r[7]), num(r[8]), num(r[9]), num(r[10]),
                    num(r[11]), num(r[12])));
        // the places first, so that zones, bases, beaches and hubs can find theirs
        List<String[]> transit = Csv.readResource("/data/taiwan/transit.csv");
        String[] head = transit.get(0);
        for (int i = 1; i < head.length; i++) { d.places.add(head[i]); d.idx.put("p:" + head[i], i - 1); }
        int np = d.places.size();
        d.km = new double[np][np];
        for (int r = 1; r < transit.size(); r++) {
            String[] row = transit.get(r);
            int a = d.idx.get("p:" + row[0]);
            for (int c = 1; c < row.length; c++) d.km[a][c - 1] = num(row[c]);
        }
        d.placeX = new double[np]; d.placeY = new double[np]; d.placeZone = new int[np];
        for (String[] r : Csv.readResource("/data/taiwan/zones.csv"))
            d.zones.add(new Zone(r[0], r[1], num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), bool(r[7]), bool(r[8]), bool(r[9])));
        for (int i = 0; i < d.zones.size(); i++) d.idx.put("z:" + d.zones.get(i).id(), i);
        d.twZone = d.idx.get("z:TAIWAN");
        d.seaZones = d.twZone;
        for (int i = 0; i < d.seaZones; i++) {
            Zone z = d.zones.get(i);
            int p = d.idx.get("p:" + z.id());
            d.placeX[p] = z.x(); d.placeY[p] = z.y(); d.placeZone[p] = i;
        }
        for (String[] r : Csv.readResource("/data/taiwan/sectors.csv")) {
            int t = indexOf(TERRAIN, r[2]);
            d.sectors.add(new Sector(r[0], r[1], t, num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]), num(r[8]), null));
        }
        for (int i = 0; i < d.sectors.size(); i++) d.idx.put("s:" + d.sectors.get(i).id(), i);
        // neighbours, now that every sector has an index
        List<String[]> secRows = Csv.readResource("/data/taiwan/sectors.csv");
        for (int i = 0; i < secRows.size(); i++) {
            String[] r = secRows.get(i);
            String[] nb = r[9].isEmpty() ? new String[0] : r[9].split("\\+");
            int[] adj = new int[nb.length];
            for (int k = 0; k < nb.length; k++) adj[k] = d.idx.get("s:" + nb[k]);
            Sector s = d.sectors.get(i);
            d.sectors.set(i, new Sector(s.id(), s.name(), s.terrain(), s.pop(), s.km2(), s.lat(), s.lon(), s.x(), s.y(), adj));
        }
        for (String[] r : Csv.readResource("/data/taiwan/bases.csv")) {
            int flags = 0;
            for (String x : r[6].split("\\+")) flags |= switch (x) {
                case "air" -> F_AIR; case "naval" -> F_NAVAL; case "army" -> F_ARMY; case "missile" -> F_MISSILE; case "port" -> F_PORT;
                case "civil" -> F_CIVIL; case "c2" -> F_C2; case "energy" -> F_ENERGY; default -> 0;
            };
            int sector = r[14].isEmpty() ? -1 : d.sectorByName(r[14]);
            Integer place = d.idx.get("p:" + r[0]);
            int p = place == null ? -1 : place;
            d.bases.add(new Base(r[0], r[1], r[2], r[3], num(r[4]), num(r[5]), flags, (int) num(r[7]), (int) num(r[8]), (int) num(r[9]), (int) num(r[10]),
                    num(r[11]), num(r[12]), d.idx.get("z:" + r[13]), sector, p));
            if (p >= 0) { d.placeX[p] = num(r[11]); d.placeY[p] = num(r[12]); d.placeZone[p] = d.idx.get("z:" + r[13]); }
        }
        for (int i = 0; i < d.bases.size(); i++) d.idx.put("b:" + d.bases.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/hubs.csv")) {
            int p = d.idx.get("p:" + r[0]);
            d.hubs.add(new Hub(r[0], r[1], num(r[4]), num(r[5]), num(r[6]), num(r[7]), d.idx.get("z:" + r[8]), p));
            d.placeX[p] = num(r[6]); d.placeY[p] = num(r[7]); d.placeZone[p] = d.idx.get("z:" + r[8]);
        }
        for (int i = 0; i < d.hubs.size(); i++) d.idx.put("h:" + d.hubs.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/beaches.csv")) {
            int p = d.idx.get("p:" + r[0]);
            d.beaches.add(new Beach(r[0], r[1], d.sectorByName(r[2]), num(r[5]), num(r[6]), num(r[7]), d.idx.get("z:" + r[8]), r[9], p));
            d.placeX[p] = num(r[6]); d.placeY[p] = num(r[7]); d.placeZone[p] = d.idx.get("z:" + r[8]);
        }
        for (int i = 0; i < d.beaches.size(); i++) d.idx.put("bc:" + d.beaches.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/facilities.csv"))
            d.facilities.add(new Facility(r[0], r[1], d.sectorByName(r[2]), bool(r[3]), num(r[4]), num(r[7]), num(r[8]), r[9].isEmpty() ? -1 : d.idx.get("p:" + r[9])));
        for (String[] r : Csv.readResource("/data/taiwan/points.csv")) d.points.add(new Point(r[0], num(r[4]), num(r[5])));
        for (int i = 0; i < d.points.size(); i++) d.idx.put("pt:" + d.points.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/countries.csv"))
            d.countries.add(new Country(r[0], r[1], r[2], bool(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7])));
        for (int i = 0; i < d.countries.size(); i++) d.idx.put("c:" + d.countries.get(i).id(), i);
        for (String[] r : Csv.readResource("/data/taiwan/economies.csv"))
            d.economies.add(new Economy(r[0], r[1], num(r[2]), num(r[3]), new double[]{num(r[4]), num(r[5]), num(r[6]), num(r[7]), num(r[8])}));
        for (String[] r : Csv.readResource("/data/taiwan/programmes.csv"))
            d.programmes.add(new Programme(r[0].equals("PRC") ? PRC : US, r[1], r[2], num(r[3]), num(r[4]), (int) num(r[5]), (int) num(r[6]), r[7], r[8]));
        for (int i = 0; i < d.programmes.size(); i++) {
            Programme p = d.programmes.get(i);
            d.idx.put("g:" + (p.side() == US ? "US:" : "PRC:") + p.id(), d.progIndex(p.side(), i));
        }
        for (String[] r : Csv.readResource("/data/taiwan/oob.csv")) d.oob.add(new Oob(r[0], r));
        d.placeBase = new int[np]; d.placeBeach = new int[np]; d.placeHub = new int[np];
        java.util.Arrays.fill(d.placeBase, -1); java.util.Arrays.fill(d.placeBeach, -1); java.util.Arrays.fill(d.placeHub, -1);
        for (int i = 0; i < d.bases.size(); i++) if (d.bases.get(i).place() >= 0) d.placeBase[d.bases.get(i).place()] = i;
        for (int i = 0; i < d.beaches.size(); i++) d.placeBeach[d.beaches.get(i).place()] = i;
        for (int i = 0; i < d.hubs.size(); i++) d.placeHub[d.hubs.get(i).place()] = i;
        for (int i = 0; i < d.ships.size(); i++) d.idx.put("sh:" + d.ships.get(i).id(), i);
        for (int i = 0; i < d.subs.size(); i++) d.idx.put("su:" + d.subs.get(i).id(), i);
        for (int i = 0; i < d.aircraft.size(); i++) d.idx.put("a:" + d.aircraft.get(i).id(), i);
        for (int i = 0; i < d.land.size(); i++) d.idx.put("l:" + d.land.get(i).id(), i);
        return d;
    }

    /** The index of a programme within its side's list. */
    private int progIndex(int side, int global) {
        int k = 0;
        for (int i = 0; i < global; i++) if (programmes.get(i).side() == side) k++;
        return k;
    }

    static int speed(String s) {
        return switch (s) { case "subsonic" -> SUBSONIC; case "stealth" -> STEALTH; case "supersonic" -> SUPERSONIC; case "ballistic" -> BALLISTIC; default -> HYPERSONIC; };
    }

    static double num(String s) { return s.isEmpty() ? 0 : Double.parseDouble(s); }

    static boolean bool(String s) { return s.equals("true"); }

    static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        throw new IllegalStateException("No " + v);
    }

    int mun(String id) { return id.isEmpty() ? -1 : need("m:" + id); }

    int need(String key) {
        Integer i = idx.get(key);
        if (i == null) throw new IllegalStateException("Taiwan data: no " + key);
        return i;
    }

    int sectorByName(String name) {
        for (int i = 0; i < sectors.size(); i++) if (sectors.get(i).name().equals(name)) return i;
        throw new IllegalStateException("No sector " + name);
    }

    // ---- public reading -------------------------------------------------------------------------------------------

    public List<Munition> munitions() { return Collections.unmodifiableList(munitions); }
    public List<ShipClass> shipClasses() { return Collections.unmodifiableList(ships); }
    public List<SubClass> subClasses() { return Collections.unmodifiableList(subs); }
    public List<AirType> aircraft() { return Collections.unmodifiableList(aircraft); }
    public List<LandSys> landSystems() { return Collections.unmodifiableList(land); }
    /** The 22 sea zones, then the airspace over Taiwan ({@code TAIWAN}). */
    public List<Zone> zones() { return Collections.unmodifiableList(zones); }
    public List<Base> bases() { return Collections.unmodifiableList(bases); }
    public List<Hub> hubs() { return Collections.unmodifiableList(hubs); }
    public List<Sector> sectors() { return Collections.unmodifiableList(sectors); }
    public List<Beach> beaches() { return Collections.unmodifiableList(beaches); }
    public List<Facility> facilities() { return Collections.unmodifiableList(facilities); }
    public List<Country> countries() { return Collections.unmodifiableList(countries); }
    public List<Economy> economies() { return Collections.unmodifiableList(economies); }
    /** Both sides' programmes (each side's in its own order: the state's per-side arrays follow it). */
    public List<Programme> programmes() { return Collections.unmodifiableList(programmes); }
    public List<Programme> programmes(int side) { return programmes.stream().filter(p -> p.side() == side).toList(); }
    /** The sea places a ship can be at (zones, ports, beaches, hub edges), in the order of the transit matrix. */
    public List<String> places() { return Collections.unmodifiableList(places); }
    /** Sea distance in km between two places (the source's router, run once offline). */
    public double seaKm(int a, int b) { return km[a][b]; }
    /** Transit hours between two places at a speed in knots. */
    public double transitHours(int a, int b, double knots) { return km[a][b] / (knots * 1.852); }
    public double placeX(int p) { return placeX[p]; }
    public double placeY(int p) { return placeY[p]; }
    public int placeZone(int p) { return placeZone[p]; }
    public int taiwanZone() { return twZone; }
    public int seaZoneCount() { return seaZones; }

    public int munition(String id) { return need("m:" + id); }
    public int zone(String id) { return need("z:" + id); }
    public int base(String id) { return need("b:" + id); }
    public int sector(String id) { return need("s:" + id); }
    public int country(String id) { return need("c:" + id); }
    public int programme(int side, String id) { return need("g:" + (side == US ? "US:" : "PRC:") + id); }
    public Point point(String id) { return points.get(need("pt:" + id)); }

    /** Projected count of an order-of-battle line in a year: count + perYear x (year - from), clamped to max, rounded. */
    public static int projected(double count, double perYear, double max, int from, int year) {
        double v = count + perYear * Math.max(0, year - from);
        if (max > 0 && perYear > 0) v = Math.min(v, max);
        if (perYear < 0) v = Math.max(v, max);
        return (int) Math.max(0, Math.floor(v + 0.5));
    }

    /** US anti-ship munitions in a year as projected by the order of battle, relative to 2027 (the presidency's munitions index is
     *  relative to 2027): divide the presidency's index by this to get {@link TaiwanScenario#munitions()}. */
    public double usAntiShipProjection(int year) {
        double base = 0, now = 0;
        for (Oob o : oob) {
            if (!o.kind().equals("pool") || !(o.s(1).equals("US_TH") || o.s(1).equals("US_GL"))) continue;
            Munition m = munitions.get(mun(o.s(2)));
            if (!m.antiShip() || m.kind().equals("SAM")) continue;
            base += o.d(3);
            now += o.d(3) + o.d(4) * Math.max(0, year - 2027);
        }
        return base <= 0 ? 1 : now / base;
    }
}
