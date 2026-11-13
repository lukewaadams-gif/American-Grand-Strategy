package game.mandate.core.hormuz;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Strait of Hormuz crisis (docs/HORMUZ.md): the ladder Iran climbs, its two decision-makers, its arsenal at the
 * strait (coastal sites and minefields), the oil that still passes, what America and its allies do about it (escorts,
 * mine clearing, strikes, envoys, the reserves and the pipelines, an invasion), and the toll. Daily turns while it lasts.
 */
public final class HormuzState {
    public static final double NORMAL_FLOW = 20.5;      // million barrels a day through the strait
    public static final double SPR_START = 400;         // million barrels in the Strategic Petroleum Reserve

    /** One of Iran's coastal sites at the strait: anti-ship missiles, boat bases, drone sites, mine layers. */
    public static final class Site {
        String id, name, kind;
        double x, y, strength, strengthSeen;
        int struck;

        public String id() { return id; }
        public String name() { return name; }
        /** "missiles", "boats", "drones" or "mines". */
        public String kind() { return kind; }
        public double x() { return x; }
        public double y() { return y; }
        /** What is left of it, 0-100. */
        public double strength() { return strength; }
        /** The intelligence community's estimate, 0-100. */
        public double strengthSeen() { return strengthSeen; }
        public int struck() { return struck; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("kind", kind); m.put("d", List.of(x, y, strength, strengthSeen)); m.put("struck", struck);
            return m;
        }

        static Site fromJson(Map<String, Object> m) {
            Site s = new Site();
            s.id = (String) m.get("id"); s.name = (String) m.get("name"); s.kind = (String) m.get("kind");
            List<Object> d = Json.array(m.get("d"));
            s.x = Json.num(d.get(0)); s.y = Json.num(d.get(1)); s.strength = Json.num(d.get(2)); s.strengthSeen = Json.num(d.get(3));
            s.struck = Json.integer(m.get("struck"));
            return s;
        }
    }

    /** A stretch of the lanes or their approaches where mines may lie. */
    public static final class Field {
        String id, name;
        double x, y, mines, cleared;
        boolean known;

        public String id() { return id; }
        public String name() { return name; }
        public double x() { return x; }
        public double y() { return y; }
        /** Mines thought to lie there. */
        public double mines() { return mines; }
        public boolean known() { return known; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("d", List.of(x, y, mines, cleared)); m.put("known", known);
            return m;
        }

        static Field fromJson(Map<String, Object> m) {
            Field f = new Field();
            f.id = (String) m.get("id"); f.name = (String) m.get("name");
            List<Object> d = Json.array(m.get("d"));
            f.x = Json.num(d.get(0)); f.y = Json.num(d.get(1)); f.mines = Json.num(d.get(2)); f.cleared = Json.num(d.get(3));
            f.known = (Boolean) m.get("known");
            return f;
        }
    }

    boolean active;
    LocalDate started;
    int day, rung, peakRung, calmDays;
    String cause, outcome;
    // Iran: the government in Tehran and the Revolutionary Guards' navy (-1 de-escalate .. +1 escalate)
    double tehran, guards, stability = 60, arsenalBoats = 100, arsenalMines = 100, arsenalMissiles = 100;
    /** The Guards' standing appetite for a fight (set by the preset), and their anger at the latest blows (it fades). */
    double hawk = 0.45, anger;
    /** A deal has been struck through the back channel; the crisis winds down unless the Guards defy it. */
    boolean settled;
    /** The days the back channel and the great-power pressure were last used (an envoy takes days). */
    int lastTalks = -99, lastPress = -99;
    final List<Site> sites = new ArrayList<>();
    final List<Field> fields = new ArrayList<>();
    // the oil
    double flow = NORMAL_FLOW, bypass, spr, iea, sprLeft = SPR_START, insurance, premium;
    boolean reinsured, bypassAsked;
    // America and its allies
    int escorts, sweepers, alliesSweeping;
    boolean hostsConsent = true, israelRestrained = true;
    double talks, chinaPressure, gulfCalm = 50;
    int usKilled, shipsHit, tankersSeized, tankersHit, strikes;
    // an invasion: 0 none, 1 build-up, 2 the invasion, 3 occupation; option 0 full, 1 the islands, 2 the coast
    int invasion, invasionOption, invasionDays;
    /** The crisis's standing spending, % of GDP a year (escorts, reinsurance, strikes, a build-up): ended with the crisis (M8-F8). */
    double spending;
    double invasionProgress;
    final List<String> log = new ArrayList<>();
    String message;
    /** The economy's last weekly tick, in crisis days. */
    int lastTick;

    public boolean active() { return active; }
    public LocalDate started() { return started; }
    public int day() { return day; }
    public int rung() { return rung; }
    public int peakRung() { return peakRung; }
    public String cause() { return cause; }
    public String outcome() { return outcome; }
    public double tehran() { return tehran; }
    public double guards() { return guards; }
    public double stability() { return stability; }
    public double arsenalBoats() { return arsenalBoats; }
    public double arsenalMines() { return arsenalMines; }
    public double arsenalMissiles() { return arsenalMissiles; }
    public List<Site> sites() { return Collections.unmodifiableList(sites); }
    public List<Field> fields() { return Collections.unmodifiableList(fields); }
    public double flow() { return flow; }
    public double bypass() { return bypass; }
    public double spr() { return spr; }
    public double iea() { return iea; }
    public double sprLeft() { return sprLeft; }
    /** The crisis's standing spending, % of GDP a year: ended with the crisis. */
    public double spending() { return spending; }
    public double insurance() { return insurance; }
    public boolean reinsured() { return reinsured; }
    public double hawk() { return hawk; }
    public double anger() { return anger; }
    public boolean settled() { return settled; }
    public int lastTalks() { return lastTalks; }
    public int lastPress() { return lastPress; }
    public double gulfCalm() { return gulfCalm; }
    public int sweepers() { return sweepers; }
    public boolean bypassAsked() { return bypassAsked; }
    /** The pipelines around the strait carry up to three million barrels a day once the Gulf states open them. */
    public double bypassTarget() { return bypassAsked && hostsConsent ? 3.0 : 0; }
    /** Dollars a barrel the crisis adds to the oil price. */
    public double premium() { return premium; }
    public int escorts() { return escorts; }
    public int alliesSweeping() { return alliesSweeping; }
    public boolean hostsConsent() { return hostsConsent; }
    public boolean israelRestrained() { return israelRestrained; }
    public double talks() { return talks; }
    public double chinaPressure() { return chinaPressure; }
    public int usKilled() { return usKilled; }
    public int shipsHit() { return shipsHit; }
    public int tankersSeized() { return tankersSeized; }
    public int tankersHit() { return tankersHit; }
    public int strikes() { return strikes; }
    public int invasion() { return invasion; }
    public int invasionOption() { return invasionOption; }
    public int invasionDays() { return invasionDays; }
    public double invasionProgress() { return invasionProgress; }
    public List<String> log() { return Collections.unmodifiableList(log); }
    public String message() { return message; }
    public int lastTick() { return lastTick; }

    static List<Object> nums(double... a) { List<Object> l = new ArrayList<>(); for (double v : a) l.add(v); return l; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", active); m.put("started", started == null ? null : started.toString());
        m.put("i", List.of(day, rung, peakRung, calmDays, escorts, sweepers, alliesSweeping, usKilled, shipsHit, tankersSeized, tankersHit, strikes, invasion,
                invasionOption, invasionDays, lastTick, lastTalks, lastPress));
        m.put("cause", cause); m.put("outcome", outcome);
        m.put("d", nums(tehran, guards, stability, arsenalBoats, arsenalMines, arsenalMissiles, flow, bypass, spr, iea, sprLeft, insurance, premium, talks,
                chinaPressure, gulfCalm, invasionProgress, hawk, anger));
        m.put("b", List.of(reinsured, hostsConsent, israelRestrained, bypassAsked, settled));
        m.put("spending", spending);
        List<Object> ss = new ArrayList<>();
        for (Site s : sites) ss.add(s.toJson());
        m.put("sites", ss);
        List<Object> fs = new ArrayList<>();
        for (Field f : fields) fs.add(f.toJson());
        m.put("fields", fs);
        m.put("log", new ArrayList<Object>(log)); m.put("message", message);
        return m;
    }

    public static HormuzState fromJson(Map<String, Object> m) {
        HormuzState s = new HormuzState();
        s.active = (Boolean) m.get("active");
        s.spending = m.get("spending") == null ? 0 : Json.num(m.get("spending"));   // older saves: none tracked
        s.started = m.get("started") == null ? null : LocalDate.parse((String) m.get("started"));
        List<Object> i = Json.array(m.get("i"));
        int k = 0;
        s.day = Json.integer(i.get(k++)); s.rung = Json.integer(i.get(k++)); s.peakRung = Json.integer(i.get(k++)); s.calmDays = Json.integer(i.get(k++));
        s.escorts = Json.integer(i.get(k++)); s.sweepers = Json.integer(i.get(k++)); s.alliesSweeping = Json.integer(i.get(k++)); s.usKilled = Json.integer(i.get(k++));
        s.shipsHit = Json.integer(i.get(k++)); s.tankersSeized = Json.integer(i.get(k++)); s.tankersHit = Json.integer(i.get(k++)); s.strikes = Json.integer(i.get(k++));
        s.invasion = Json.integer(i.get(k++)); s.invasionOption = Json.integer(i.get(k++)); s.invasionDays = Json.integer(i.get(k++)); s.lastTick = Json.integer(i.get(k++));
        s.lastTalks = Json.integer(i.get(k++)); s.lastPress = Json.integer(i.get(k));
        s.cause = (String) m.get("cause"); s.outcome = (String) m.get("outcome");
        List<Object> d = Json.array(m.get("d"));
        k = 0;
        s.tehran = Json.num(d.get(k++)); s.guards = Json.num(d.get(k++)); s.stability = Json.num(d.get(k++)); s.arsenalBoats = Json.num(d.get(k++));
        s.arsenalMines = Json.num(d.get(k++)); s.arsenalMissiles = Json.num(d.get(k++)); s.flow = Json.num(d.get(k++)); s.bypass = Json.num(d.get(k++));
        s.spr = Json.num(d.get(k++)); s.iea = Json.num(d.get(k++)); s.sprLeft = Json.num(d.get(k++)); s.insurance = Json.num(d.get(k++)); s.premium = Json.num(d.get(k++));
        s.talks = Json.num(d.get(k++)); s.chinaPressure = Json.num(d.get(k++)); s.gulfCalm = Json.num(d.get(k++)); s.invasionProgress = Json.num(d.get(k++));
        s.hawk = Json.num(d.get(k++)); s.anger = Json.num(d.get(k));
        List<Object> b = Json.array(m.get("b"));
        s.reinsured = (Boolean) b.get(0); s.hostsConsent = (Boolean) b.get(1); s.israelRestrained = (Boolean) b.get(2); s.bypassAsked = (Boolean) b.get(3); s.settled = (Boolean) b.get(4);
        for (Object o : Json.array(m.get("sites"))) s.sites.add(Site.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("fields"))) s.fields.add(Field.fromJson(Json.object(o)));
        for (Object o : Json.array(m.get("log"))) s.log.add((String) o);
        s.message = (String) m.get("message");
        return s;
    }
}
