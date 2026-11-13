package game.mandate.core.taiwan;

import game.mandate.core.taiwan.TaiwanData.Point;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Contact;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Indicator;
import game.mandate.core.taiwan.TaiwanState.Sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * Intelligence (spec 9): the hourly sensor picture (zone-level contacts with an error, an age and an identification) and
 * the daily strategic warning (indicators of the other side's preparations, sensitive sources, noise, exercise cover and
 * deception, and the US warning estimate).
 */
final class TaiwanIntel {
    private TaiwanIntel() {}

    static final String[] NOISE = {
            "The PLA's Eastern Theatre holds a routine live-fire drill off Fujian",
            "State media runs editorials on national reunification",
            "A Chinese coast guard patrol enters Kinmen's restricted waters",
            "PLA aircraft cross the median line in a routine pattern",
            "More Chinese fishing boats are active in the East China Sea",
            "A PLA hospital ship visits Zhoushan",
            "Taiwan detects balloons overhead",
            "Unusual Chinese purchases of soybeans and copper are reported",
    };

    // =================================================================================================================
    //  The hourly picture (spec 9.1)
    // =================================================================================================================

    static double[] shoreRadar(Ctx c, int side, double x, double y) {
        TaiwanState s = c.s;
        if (side == US) {
            double p = 0;
            if (TaiwanSea.dist(x, y, 0, 0) < 260) p = Math.max(p, 0.7 * (0.5 + 0.5 * s.c2));
            Point sk = c.d.point("sakishima"), ok = c.d.point("okinawa"), bt = c.d.point("batanes");
            if (TaiwanSea.dist(x, y, sk.x(), sk.y()) < 280 || TaiwanSea.dist(x, y, ok.x(), ok.y()) < 320) p = Math.max(p, 0.55);
            if (TaiwanSea.dist(x, y, bt.x(), bt.y()) < 250 && TaiwanWorld.country(c, "PHL").level() >= 4) p = Math.max(p, 0.45);
            return new double[]{p, 6};
        }
        Point fj = c.d.point("fujian"), gd = c.d.point("guangdong"), zj = c.d.point("zhejiang"), hn = c.d.point("hainan"), sp = c.d.point("spratly");
        double dfj = TaiwanSea.dist(x, y, fj.x(), fj.y()), dhn = TaiwanSea.dist(x, y, hn.x(), hn.y());
        if (dfj < 330 || TaiwanSea.dist(x, y, gd.x(), gd.y()) < 330 || TaiwanSea.dist(x, y, zj.x(), zj.y()) < 330 || dhn < 300) return new double[]{0.75, 6};
        if (TaiwanSea.dist(x, y, sp.x(), sp.y()) < 420) return new double[]{0.5, 8};
        double dd = Math.min(dfj, dhn);
        if (dd > 700 && dd < 2800) return new double[]{0.16 * (1 - s.dazzle[PRC] * 0.3), 30 + dd / 100.0};
        return new double[]{0, 0};
    }

    static void hour(Ctx c) { sense(c, US); sense(c, PRC); }

    static void sense(Ctx c, int side) {
        TaiwanState s = c.s;
        int enemy = 1 - side;
        double sat = (side == US ? 0.08 : 0.10) * s.satHealth[side] * (1 - s.dazzle[side]);
        if (side == PRC && TaiwanWorld.progDone(c, PRC, "space")) sat *= 1.25;
        List<Group> own = new ArrayList<>();
        for (Group o : s.groups) if (o.side == side && !o.dead() && o.hub < 0 && !o.gone) own.add(o);
        List<Sub> ownSubs = new ArrayList<>();
        for (Sub o : s.subs) if (o.side == side && !o.lost && o.hub < 0 && o.mission != SM_PORT) ownSubs.add(o);
        double[] ox = new double[own.size()], oy = new double[own.size()], or = new double[own.size()];
        for (int i = 0; i < own.size(); i++) { ox[i] = TaiwanSea.gx(c, own.get(i)); oy[i] = TaiwanSea.gy(c, own.get(i)); or[i] = TaiwanSea.spread(c, own.get(i)); }
        for (Group g : s.groups) {
            if (g.side != enemy || g.dead() || g.hub >= 0 || g.gone) continue;
            double gx = TaiwanSea.gx(c, g), gy = TaiwanSea.gy(c, g), gr = TaiwanSea.spread(c, g);
            double kmh = g.place < 0 ? TaiwanSea.kn(c, g) * 1.852 : 4;
            boolean seen = false;
            double err = 99;
            int ident = 0;
            double ps = g.port >= 0 ? Math.min(0.7, sat * 3) : sat;
            if (c.u(Ctx.R_INTEL) < ps) { seen = true; err = g.port >= 0 ? 2 : 10 + kmh * 1.3; ident = 1; }
            double[] sr = shoreRadar(c, side, gx, gy);
            double psr = sr[0] * (g.emcon ? 0.75 : 1);
            if (psr > 0 && c.u(Ctx.R_INTEL) < psr) { seen = true; err = Math.min(err, sr[1] + kmh * 0.3); if (sr[1] < 10) ident = Math.max(ident, 1); }
            int a = g.at;
            if (a >= 0) {
                double isr = c.isr[side][a] + c.aew[side][a] * 0.6;
                if (isr > 0) {
                    double contested = 1 + c.cap[enemy][a] * 0.15 + (c.d.zones.get(a).taiwanAir() ? c.capTW[enemy] * 0.1 : 0);
                    if (c.u(Ctx.R_INTEL) < (1 - TMath.exp(-isr * 0.22)) / contested) { seen = true; err = Math.min(err, 4); ident = 2; }
                }
            }
            if (!g.emcon && c.u(Ctx.R_INTEL) < 0.06) { seen = true; err = Math.min(err, 40); }
            for (int i = 0; i < own.size(); i++) {
                double m110 = TaiwanSea.meet(ox[i], oy[i], or[i], gx, gy, gr, 110);
                if (m110 > 0 && c.u(Ctx.R_INTEL) < 0.7 * m110) { seen = true; err = Math.min(err, 5); ident = 2; continue; }
                if (g.emcon) continue;
                double m380 = TaiwanSea.meet(ox[i], oy[i], or[i], gx, gy, gr, 380);
                if (m380 > 0 && c.u(Ctx.R_INTEL) < 0.35 * m380) { seen = true; err = Math.min(err, 25); ident = Math.max(ident, 1); }
            }
            for (Sub o : ownSubs) {
                double m = TaiwanSea.meet(TaiwanSea.sx(c, o), TaiwanSea.sy(c, o), TaiwanSea.spread(c, o), gx, gy, gr, 90);
                if (m > 0 && c.u(Ctx.R_INTEL) < 0.3 * m) { seen = true; err = Math.min(err, 8); ident = Math.max(ident, 1); }
            }
            if (seen) report(c, side, TK_GROUP, g.id, g.at, gx, gy, err, ident, g.decoy);
        }
        for (Sub x : s.subs) {
            if (x.side != enemy || x.lost || x.hub >= 0) continue;
            double p = 0;
            double xx = TaiwanSea.sx(c, x), xy = TaiwanSea.sy(c, x), xr = TaiwanSea.spread(c, x);
            double q = c.d.subs.get(x.cls).quiet();
            if (x.mission == SM_PORT) p = Math.min(0.6, sat * 2.5);
            else {
                int a = x.at;
                if (a >= 0) {
                    p += c.asw[side][a] * 0.012 * (1.25 - q);
                    if (side == US && c.d.zones.get(a).strait() && !c.d.zones.get(a).id().startsWith("TS_")) p += 0.06 * (1.3 - q);
                }
                for (int i = 0; i < own.size(); i++) {
                    double m = TaiwanSea.meet(ox[i], oy[i], or[i], xx, xy, xr, 40);
                    if (m > 0) p += TaiwanSea.asw(c, own.get(i)) * 0.01 * (1.25 - q) * m;
                }
            }
            if (p > 0 && c.u(Ctx.R_INTEL) < p) report(c, side, TK_SUB, x.id, x.at, xx, xy, x.mission == SM_PORT ? 2 : 18, x.mission == SM_PORT ? 2 : 1, false);
        }
        for (Battery b : s.batteries) {
            if (b.side != enemy || !b.alive || b.hub >= 0 || b.base < 0) continue;
            if (s.hour - b.lastFire <= 1) report(c, side, TK_LAND, b.id, c.d.bases.get(b.base).zone(), c.d.bases.get(b.base).x(), c.d.bases.get(b.base).y(), 4, 2, false);
        }
        Map<Integer, Contact> cs = s.contacts.get(side);
        List<Integer> drop = new ArrayList<>();
        for (Contact k : cs.values()) {
            double age = s.hour - k.seen;
            if (k.kind == TK_SUB && age > 10 || k.kind == TK_GROUP && age > 48 || k.kind == TK_LAND && age > 24) drop.add(k.id);
            else if (k.kind == TK_GROUP) { Group g = c.group(k.id); if ((g == null || g.dead() || g.gone) && age > 2) drop.add(k.id); }
            else if (k.kind == TK_SUB) { Sub x = c.sub(k.id); if (x == null || x.lost) drop.add(k.id); }
            else if (k.kind == TK_LAND) { Battery b = c.battery(k.id); if (b == null || !b.alive) drop.add(k.id); }
        }
        for (Integer id : drop) cs.remove(id);
    }

    static void report(Ctx c, int side, int kind, int id, int zone, double x, double y, double err, int ident, boolean decoy) {
        TaiwanState s = c.s;
        Contact k = s.contacts.get(side).get(id);
        boolean fresh = k == null;
        if (k == null) { k = new Contact(); k.kind = kind; k.id = id; k.first = s.hour; s.contacts.get(side).put(id, k); }
        k.off = err * (0.3 + c.u(Ctx.R_INTEL) * 0.7);
        k.x = x; k.y = y; k.zone = zone;
        k.err = err;
        k.ident = Math.max(k.ident, ident);
        k.seen = s.hour;
        k.decoy = decoy && k.ident < 2;
        if (fresh && kind == TK_GROUP && s.atWar() && side == s.playerSide && ident >= 1) {
            Group g = c.group(id);
            if (g != null && (TaiwanSea.carrierGroup(c, g) || TaiwanSea.amphib(c, g) || decoy))
                c.log("New contact: " + label(c, k) + " in " + (zone >= 0 ? c.d.zones.get(zone).name() : "open water") + ".", true);
        }
    }

    /** How a side's analysts describe a contact (spec 9.1). */
    static String label(Ctx c, Contact k) {
        if (k.kind == TK_SUB) return "Submarine contact";
        if (k.kind == TK_LAND) { Battery b = c.battery(k.id); return b == null ? "Missile battery" : b.name; }
        Group g = c.group(k.id);
        if (g == null) return "Surface contact";
        if (k.ident >= 2) return g.name;
        int n = g.alive();
        String size = n <= 2 ? "small" : n <= 6 ? "" : "large";
        if (k.ident == 1) {
            if (k.decoy) return "Amphibious group (probable)";
            if (TaiwanSea.carrierGroup(c, g)) return "Carrier group";
            if (TaiwanSea.amphib(c, g)) return "Amphibious group";
            if (TaiwanSea.coastGuard(c, g)) return "Coast guard or militia flotilla";
            return (size.isEmpty() ? "S" : Character.toUpperCase(size.charAt(0)) + size.substring(1) + " s") + "urface action group";
        }
        return "Unidentified surface contact" + (size.isEmpty() ? "" : " (" + size + ")");
    }

    // =================================================================================================================
    //  Strategic warning (spec 9.2)
    // =================================================================================================================

    static double collection(Ctx c, int side) { return 0.45 + 0.25 * c.s.satHealth[side] + 0.2 * c.s.humint; }

    static void daily(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        int day = s.day();
        var red = c.d.programmes(PRC);
        boolean cover = TaiwanWorld.progDone(c, PRC, "exercise") && TaiwanWorld.progOn(c, PRC, "exercise");
        int before = s.indicatorsUS.size();
        for (int i = 0; i < red.size(); i++) {
            if (!s.progOn[PRC][i]) continue;
            boolean exercise = red.get(i).id().equals("exercise");
            double sig = red.get(i).signal() * (s.progPct[PRC][i] >= 100 ? 0.6 : 1);
            if (cover && !exercise) sig *= 0.62;
            double col = collection(c, US);
            if (c.u(Ctx.R_INTEL) < sig * col) {
                Indicator x = new Indicator();
                x.prog = i; x.day = day; x.conf = TMath.clamp(0.35 + 0.5 * c.u(Ctx.R_INTEL) * col, 0.2, 0.95);
                x.strength = (int) Math.floor(1 + sig * 3 + 0.5);
                x.cover = cover && !exercise;
                x.text = red.get(i).indicator() + (x.cover ? " (assessed: may be part of the exercise)" : "");
                s.indicatorsUS.add(x);
            }
        }
        if (c.u(Ctx.R_INTEL) < 0.22) {
            Indicator x = new Indicator();
            x.prog = -1; x.day = day; x.text = NOISE[c.nextInt(Ctx.R_INTEL, NOISE.length)]; x.conf = 0.3 + c.u(Ctx.R_INTEL) * 0.3; x.strength = 1;
            s.indicatorsUS.add(x);
        }
        var blue = c.d.programmes(US);
        for (int i = 0; i < blue.size(); i++) {
            if (!s.progOn[US][i]) continue;
            if (c.u(Ctx.R_INTEL) < blue.get(i).signal() * collection(c, PRC) * 1.2) {
                Indicator x = new Indicator();
                x.prog = i; x.day = day; x.conf = TMath.clamp(0.5 + 0.4 * c.u(Ctx.R_INTEL), 0, 0.95); x.strength = (int) Math.floor(1 + blue.get(i).signal() * 3 + 0.5);
                x.text = blue.get(i).name();
                s.indicatorsPRC.add(x);
            }
        }
        trim(s.indicatorsUS, day);
        trim(s.indicatorsPRC, day);
        if (s.plan >= 0 && s.phase.equals("crisis") && s.standDownDay < 0 && c.u(Ctx.R_INTEL) < 0.035 * s.humint * 1.3) {
            boolean truth = c.u(Ctx.R_INTEL) < (TaiwanWorld.progDone(c, PRC, "influence") ? 0.55 : 0.75);
            int shown = truth ? s.plan : c.nextInt(Ctx.R_INTEL, 5);
            Indicator x = new Indicator();
            x.prog = -2; x.day = day; x.conf = 0.45; x.strength = 3;
            x.text = "A sensitive source reports that Beijing has approved a plan for " + PLAN_TEXT[shown]
                    + (s.launchHour >= 0 && truth ? ", to be carried out within " + Math.max(1, (s.launchHour - s.hour) / 24 + c.nextInt(Ctx.R_INTEL, 7) - 3) + " days" : "") + ".";
            s.indicatorsUS.add(x);
        }
        assess(c);
        if (s.phase.equals("crisis") && (s.indicatorsUS.size() > before || day % 3 == 0)) {
            int best = 0;
            for (int i = 1; i < 5; i++) if (s.planGuess[i] > s.planGuess[best]) best = i;
            int fresh = 0;
            for (Indicator x : s.indicatorsUS) if (x.day == day) fresh++;
            c.log("Intelligence summary: warning " + WARNING[s.warnLevel[US]] + " (" + (int) Math.floor(s.warning[US] * 100 + 0.5) + "% chance of PRC military action within 30 days); "
                    + fresh + " new indicator" + (fresh == 1 ? "" : "s") + "; most likely course " + PLAN_TEXT[best] + ".", s.playerSide == US && fresh > 0);
        }
    }

    static final String[] PLAN_TEXT = {"an amphibious invasion", "a blockade", "a quarantine", "seizing the outlying islands", "firepower strikes"};

    static void trim(List<Indicator> l, int day) { for (int i = l.size() - 1; i >= 0; i--) if (day - l.get(i).day > 45) l.remove(i); }

    static void assess(Ctx c) {
        TaiwanState s = c.s;
        int day = s.day();
        double score = 0;
        double[] pg = new double[5];
        var red = c.d.programmes(PRC);
        for (Indicator x : s.indicatorsUS) {
            double w = x.strength * x.conf * TMath.exp(-(day - x.day) / 18.0) * (x.cover ? 0.7 : 1);
            if (x.prog >= 0) {
                score += w * red.get(x.prog).signal();
                switch (red.get(x.prog).id()) {
                    case "roro", "amphib", "followon" -> pg[0] += w * 1.4;
                    case "militia" -> { pg[1] += w * 0.5; pg[2] += w * 0.9; pg[3] += w * 0.4; }
                    case "mines", "subs" -> { pg[1] += w; pg[0] += w * 0.3; }
                    case "rocket", "air" -> { pg[4] += w * 0.8; pg[0] += w * 0.5; }
                    default -> { }
                }
            } else if (x.prog == -2) score += w * 0.6;
        }
        s.warning[US] = s.phase.equals("crisis") ? 1 - TMath.exp(-score / 9.0) : 1;
        double w = s.warning[US];
        s.warnLevel[US] = w < 0.15 ? 0 : w < 0.35 ? 1 : w < 0.6 ? 2 : w < 0.8 ? 3 : 4;
        double tot = 1e-6;
        for (double v : pg) tot += v;
        if (tot < 0.5) { java.util.Arrays.fill(pg, 0.2); tot = 1; }
        for (int i = 0; i < 5; i++) s.planGuess[i] = pg[i] / tot;
        double us = 0;
        for (Indicator x : s.indicatorsPRC) if (x.prog >= 0) us += x.strength * x.conf * TMath.exp(-(day - x.day) / 20.0);
        s.warning[PRC] = 1 - TMath.exp(-us / 8.0);
        double r = s.warning[PRC];
        s.warnLevel[PRC] = r < 0.2 ? 0 : r < 0.45 ? 1 : r < 0.7 ? 2 : 3;
    }

    /** China's deception (spec 9.2): false reports cost 4 diplomatic capital; a decoy sends a militia flotilla dressed as an
     *  amphibious group to patrol the northern strait. */
    static String deceive(Ctx c, String what) {
        TaiwanState s = c.s;
        if (!s.phase.equals("crisis") && what.equals("reports")) throw new IllegalArgumentException("False reports only work before the war");
        if (what.equals("reports")) {
            if (s.dipCap[PRC] < 4) throw new IllegalArgumentException("Not enough diplomatic capital");
            s.dipCap[PRC] -= 4;
            Indicator x = new Indicator();
            x.prog = -2; x.day = s.day(); x.conf = 0.5; x.strength = 1;
            x.text = "A sensitive source reports that Beijing has shelved its military options for now.";
            s.indicatorsUS.add(x);
            s.humint = Math.max(0.2, s.humint - 0.05);
            for (Indicator y : s.indicatorsUS) if (s.day() - y.day <= 10) y.conf *= 0.9;
            return "False reports are fed into American channels.";
        }
        if (what.equals("decoy")) {
            for (Group g : s.groups) {
                if (g.side != PRC || g.decoy || g.dead() || g.hub >= 0 || !g.tag.equals("cg") || !TaiwanSea.hasRole(c, g, "militia")) continue;
                g.decoy = true;
                g.held = true;
                TaiwanSea.orderZone(c, g, c.d.zone("TS_N"), M_PATROL);
                return g.name + " sails for the northern strait, dressed as an amphibious group.";
            }
            throw new IllegalArgumentException("No militia flotilla is free to act as a decoy");
        }
        throw new IllegalArgumentException("No such deception: " + what);
    }
}
