package game.mandate.core.taiwan;

import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Sub;

import java.time.LocalDate;
import java.util.Locale;

/**
 * One call's working context: the state, the tables, the random streams, what the call reports, and the hour's air
 * coverage tables (rebuilt every hour from the state, never saved, as in the source).
 */
final class Ctx {
    static final String R_AIR = "taiwan.air", R_SALVO = "taiwan.salvo", R_SUB = "taiwan.sub", R_INTEL = "taiwan.intel", R_GROUND = "taiwan.ground",
            R_AMPHIB = "taiwan.amphib", R_SEA = "taiwan.sea", R_WORLD = "taiwan.world", R_DIPLO = "taiwan.diplo", R_STAFF = "taiwan.staff",
            R_SHIP = "taiwan.ship", R_PROD = "taiwan.production", R_POLITICS = "taiwan.politics";

    final TaiwanState s;
    final TaiwanData d;
    final DeterministicRandomManager rng;
    final TaiwanEngine.Outcome out;
    final LocalDate date;
    // the hour's coverage (spec 5.2)
    final double[][] cap, asw, aew, isr, capSector;
    final double[] capTW = new double[2], aewTW = new double[2], casTW = new double[2], tank = new double[2];
    final double[] beachUsed;
    /** Battalion-equivalents a side's lift squadrons may still drop this hour (a cache per tick). */
    int beachHour = -1;

    Ctx(TaiwanState s, TaiwanData d, DeterministicRandomManager rng, TaiwanEngine.Outcome out, LocalDate date) {
        this.s = s; this.d = d; this.rng = rng; this.out = out; this.date = date;
        int z = d.zones.size();
        cap = new double[2][z]; asw = new double[2][z]; aew = new double[2][z]; isr = new double[2][z];
        capSector = new double[2][d.sectors.size()];
        beachUsed = new double[d.beaches.size()];
    }

    double u(String stream) { return rng.uniform(stream); }

    boolean chance(String stream, double p) { return rng.uniform(stream) < p; }

    int nextInt(String stream, int bound) { return rng.nextInt(stream, bound); }

    /** Poisson(lambda), Knuth's method (normal approximation above 30, spec preamble), in exact arithmetic. */
    int poisson(String stream, double lam) {
        if (lam <= 0) return 0;
        if (lam > 30) {
            // a sum of twelve uniforms less six is close to a standard normal, and needs no transcendental function
            double z = -6;
            for (int i = 0; i < 12; i++) z += rng.uniform(stream);
            return (int) Math.max(0, Math.floor(lam + Math.sqrt(lam) * z + 0.5));
        }
        double l = TMath.exp(-lam), p = 1;
        int k = 0;
        do { k++; p *= rng.uniform(stream); } while (p > l);
        return k - 1;
    }

    Loss loss(String nat) { return s.losses.computeIfAbsent(nat, k -> new Loss()); }

    // the unit lists only ever grow at the end with rising ids (or lose members), so they stay sorted by id
    Group group(int id) { return find(s.groups, id, 0); }

    Sub sub(int id) { return find(s.subs, id, 1); }

    Squadron squadron(int id) { return find(s.squadrons, id, 2); }

    TaiwanState.Battery battery(int id) { return find(s.batteries, id, 3); }

    TaiwanState.Troop troop(int id) { return find(s.troops, id, 4); }

    static int idOf(Object o, int kind) {
        return switch (kind) {
            case 0 -> ((Group) o).id;
            case 1 -> ((Sub) o).id;
            case 2 -> ((Squadron) o).id;
            case 3 -> ((TaiwanState.Battery) o).id;
            default -> ((TaiwanState.Troop) o).id;
        };
    }

    static <T> T find(java.util.List<T> l, int id, int kind) {
        int lo = 0, hi = l.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = idOf(l.get(mid), kind);
            if (v < id) lo = mid + 1; else if (v > id) hi = mid - 1; else return l.get(mid);
        }
        return null;
    }

    // ---- the record -------------------------------------------------------------------------------------------------

    /** A line in the log (and the turn's report when it matters). */
    void log(String what, boolean important) {
        String line = stamp() + what;
        s.log.add(line);
        if (s.log.size() > 300) s.log.remove(0);
        if (important) { s.sitrep.add(line); if (s.sitrep.size() > 60) s.sitrep.remove(0); }
        out.log().add(line);
    }

    void log(String what) { log(what, false); }

    void alert(String what) { log(what, true); }

    void news(News.Kind kind, String head, String body, double weight) { out.news().add(new News(date, kind, head, body, weight, null)); }

    void effect(Effect e) { out.effects().add(e); }

    String stamp() {
        LocalDate dt = s.date();
        String m = dt.getMonth().name();
        int h = s.hour % 24;
        return "Day " + s.day() + " (" + m.charAt(0) + m.substring(1, 3).toLowerCase(Locale.ROOT) + " " + dt.getDayOfMonth() + ") " + (h < 10 ? "0" : "") + h + ":00 - ";
    }

    String sideName(int side) { return side == TaiwanState.US ? "the coalition" : "the PLA"; }
}
