package game.mandate.core.taiwan;

import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.Sector;
import game.mandate.core.taiwan.TaiwanState.FacilityState;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.SectorState;
import game.mandate.core.taiwan.TaiwanState.Ship;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Troop;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The war on Taiwan itself (spec 6): embarkation, landings at beaches and captured ports, airborne drops and airlift, and
 * the six-hour ground step (movement, PLA supply, combat in each sector, captures).
 */
final class TaiwanLand {
    private TaiwanLand() {}

    static double weight(Troop t) { return TaiwanData.GTONS[t.type] / 2400.0; }

    static boolean isType(Troop t, String type) { return TaiwanData.GTYPE[t.type].equals(type); }

    static double cargoBns(Ctx c, Group g) { double v = 0; for (int id : g.cargo) { Troop t = c.troop(id); if (t != null) v += t.bns; } return v; }

    static double cargoBE(Ctx c, Group g) { double v = 0; for (int id : g.cargo) { Troop t = c.troop(id); if (t != null) v += t.bns * weight(t); } return v; }

    static double baseDist(Ctx c, int a, int b) {
        Base x = c.d.bases.get(a), y = c.d.bases.get(b);
        return TaiwanSea.dist(x.x(), x.y(), y.x(), y.y());
    }

    /** Troops waiting at (or near) a base. */
    static List<Troop> staged(Ctx c, int side, int base, double km) {
        List<Troop> out = new ArrayList<>();
        if (base < 0) return out;
        for (Troop t : c.s.troops)
            if (t.side == side && t.state == GS_STAGING && t.stage >= 0 && t.bns > 0.01 && (t.stage == base || baseDist(c, t.stage, base) <= km)) out.add(t);
        return out;
    }

    /** Splits off a detachment of {@code bns} battalions (or returns the unit itself). */
    static Troop split(Ctx c, Troop t, double bns) {
        if (bns >= t.bns - 0.01) return t;
        Troop d = new Troop();
        d.id = c.s.nextId++; d.key = t.key; d.side = t.side; d.nat = t.nat; d.type = t.type; d.name = t.name; d.quality = t.quality;
        d.supply = t.supply; d.state = t.state; d.sector = t.sector; d.stage = t.stage; d.dug = t.dug;
        d.bns = bns; d.maxBns = bns;
        t.bns -= bns; t.maxBns = Math.max(t.bns, t.maxBns - bns);
        c.s.troops.add(d);
        return d;
    }

    static int prio(Troop t) { return isType(t, "sof") || isType(t, "amphib") ? 0 : isType(t, "marine") ? 1 : 2; }

    /** Loads staged troops up to the group's lift (spec 6.2). Returns battalions loaded. */
    static double embark(Ctx c, Group g) {
        if (g.port < 0 || !TaiwanSea.amphib(c, g) && !TaiwanSea.hasRole(c, g, "roro")) return 0;
        double cap = TaiwanSea.lift(c, g) - cargoBE(c, g);
        List<Troop> list = staged(c, g.side, g.port, 420);
        // assault troops first, nearest first (a stable insertion sort: deterministic)
        for (int i = 1; i < list.size(); i++) {
            Troop t = list.get(i);
            int j = i - 1;
            while (j >= 0 && before(c, t, list.get(j), g.port)) { list.set(j + 1, list.get(j)); j--; }
            list.set(j + 1, t);
        }
        double loaded = 0, far = 0;
        for (Troop t : list) {
            if (cap < 0.05) break;
            if (isType(t, "airborne") || isType(t, "airassault")) continue;
            double w = weight(t);
            double take = Math.min(t.bns, cap / w);
            if (take < 0.05) continue;
            far = Math.max(far, baseDist(c, t.stage, g.port));
            Troop d = split(c, t, take);
            d.state = GS_EMBARKED; d.ship = g.id; d.sector = -1;
            g.cargo.add(d.id);
            cap -= take * w;
            loaded += take;
        }
        if (loaded > 0) {
            g.readyAt = c.s.hour + (g.side == PRC && !TaiwanWorld.progDone(c, PRC, "amphib") ? 24 : 8) + far / 40.0;
            g.homePort = g.port;
            c.log(g.name + " embarks " + one(loaded) + " battalions at " + c.d.bases.get(g.port).name() + ".");
        }
        return loaded;
    }

    static boolean before(Ctx c, Troop a, Troop b, int port) {
        int pa = prio(a), pb = prio(b);
        if (pa != pb) return pa < pb;
        return baseDist(c, a.stage, port) < baseDist(c, b.stage, port);
    }

    static String one(double v) { return game.mandate.core.data.Fmt.format("%.1f", v); }

    // =================================================================================================================
    //  Landing (spec 6.3)
    // =================================================================================================================

    static double seaState(Ctx c) {
        TaiwanState s = c.s;
        int day = s.hour / 24;
        if (s.seaDay != day) {
            s.seaDay = day;
            double v = 0.55 + c.u(Ctx.R_SEA) * 0.45;
            int month = s.date().getMonthValue();
            if (month >= 6 && month <= 9 && c.u(Ctx.R_SEA) < 0.12) v *= 0.35;   // summer typhoon risk
            s.seaState = v;
        }
        return s.seaState;
    }

    static void hour(Ctx c) {
        TaiwanState s = c.s;
        if (c.beachHour != s.hour) { java.util.Arrays.fill(c.beachUsed, 0); c.beachHour = s.hour; }
        for (Group g : s.groups) {
            if (g.cargo.isEmpty() && g.mission != M_ASSAULT) continue;
            if (g.dead()) { lostWithShip(c, g); continue; }
            if (g.hub >= 0 || g.gone || g.mission != M_ASSAULT || g.place < 0) continue;
            g.beachHours++;
            if (g.beach >= 0) atBeach(c, g);
            else if (g.fac >= 0) atPort(c, g);
        }
    }

    static void lostWithShip(Ctx c, Group g) {
        for (int id : new ArrayList<>(g.cargo)) {
            Troop t = c.troop(id);
            if (t == null) continue;
            Loss l = c.loss(t.nat);
            l.killed += t.bns * 650 * 0.5;
            l.wounded += t.bns * 650 * 0.2;
            t.lost += t.bns;
            t.bns = 0;
            t.state = GS_DEAD;
        }
        g.cargo.clear();
    }

    static void atBeach(Ctx c, Group g) {
        TaiwanState s = c.s;
        int b = g.beach;
        TaiwanData.Beach bd = c.d.beaches.get(b);
        int sec = bd.sector();
        boolean pier = false;
        for (Group o : s.groups)
            if (o.side == g.side && !o.dead() && o.place == bd.place() && o.mission == M_ASSAULT && TaiwanSea.hasRole(c, o, "pier")) { pier = true; break; }
        double rate = 0;
        for (Ship sh : g.ships) {
            if (sh.mk) continue;
            TaiwanData.ShipClass k = TaiwanSea.sc(c, sh);
            switch (k.role()) {
                case "amphib" -> rate += 0.07 * k.lift() * sh.n;
                case "lst" -> rate += 0.16 * k.lift() * sh.n;
                case "roro" -> rate += (pier ? 0.07 : 0.006) * k.lift() * sh.n;
                default -> { }
            }
        }
        double sea = seaState(c);
        rate *= bd.quality() * sea;
        double room = Math.max(0, 0.45 * bd.quality() - c.beachUsed[b]);
        rate = Math.min(rate, room);
        if (g.cargo.isEmpty()) {
            if (g.side == PRC) { g.shuttling = true; s.sectors.get(sec).supplyIn += 0.03 * TaiwanSea.lift(c, g) * sea; }
            return;
        }
        c.beachUsed[b] += rate;
        g.offloadAcc += rate;
        if (g.offloadAcc < 0.1) return;
        double be = g.offloadAcc;
        g.offloadAcc = 0;
        landFrom(c, g, sec, be, beachDefense(c, sec, g.side), bd.name());
    }

    static void atPort(Ctx c, Group g) {
        FacilityState f = c.s.facilities.get(g.fac);
        TaiwanData.Facility fd = c.d.facilities.get(g.fac);
        if (f.owner != g.side) {
            c.log(g.name + " cannot unload: " + fd.name() + " is not in friendly hands.");
            g.mission = M_IDLE;
            return;
        }
        double cap = Math.max(0, 1 - f.damage);
        if (g.cargo.isEmpty()) {
            if (g.side == PRC) { g.shuttling = true; c.s.sectors.get(fd.sector()).supplyIn += 0.08 * TaiwanSea.lift(c, g) * cap; }
            return;
        }
        g.offloadAcc += 0.18 * TaiwanSea.lift(c, g) * cap;
        if (g.offloadAcc < 0.1) return;
        double be = g.offloadAcc;
        g.offloadAcc = 0;
        landFrom(c, g, fd.sector(), be, beachDefense(c, fd.sector(), g.side) * 0.3, fd.name());
    }

    /** Fraction of a landing wave lost to defending fire (spec 6.3). */
    static double beachDefense(Ctx c, int sec, int side) {
        TaiwanState s = c.s;
        int enemy = 1 - side;
        double t = 0, p = 0;
        for (Troop u : s.troops) {
            if (u.sector != sec || u.state != GS_READY) continue;
            if (u.side == enemy) t += u.power() * u.supply; else p += u.power();
        }
        double d = TMath.clamp(t / (8 + p * 0.6), 0, 1.6);
        if (side == PRC) {
            if (s.taiwanDelay && s.warHours() < 72) d *= 0.5;
            d *= 0.6 + 0.4 * s.c2;
            int r = TaiwanFire.take(c, TaiwanData.P_TW, c.d.munition("GMLRS"), 6);
            if (r > 0) { TaiwanFire.expend(c, US, c.d.munition("GMLRS"), r); d += 0.03 * r / 6.0; }
            d += c.casTW[US] * 0.004;
            d *= 1 - TMath.clamp(c.casTW[PRC] * 0.002, 0, 0.5);
        }
        return TMath.clamp(d * 0.11, 0, 0.3);
    }

    static void landFrom(Ctx c, Group g, int sec, double be, double lossFrac, String where) {
        TaiwanState s = c.s;
        double landed = 0, lostB = 0;
        for (int i = 0; i < g.cargo.size() && be > 0.01; ) {
            Troop t = c.troop(g.cargo.get(i));
            if (t == null || t.bns <= 0.01) { g.cargo.remove(i); continue; }
            double w = weight(t);
            double take = Math.min(t.bns, be / w);
            be -= take * w;
            double lost = take * lossFrac * (0.7 + c.u(Ctx.R_AMPHIB) * 0.6);
            Troop d = split(c, t, take);
            d.state = GS_READY; d.sector = sec; d.ship = -1; d.dug = 0; d.supply = Math.min(d.supply, 0.85);
            if (d == t) g.cargo.remove(i); else i++;
            if (lost > 0) hurtLanding(c, d, lost);
            landed += take - lost;
            lostB += lost;
            mergeInto(c, d);
        }
        SectorState ss = s.sectors.get(sec);
        if (g.side == PRC) { ss.lodgment = true; ss.contested = true; if (ss.ctrl < 0.05) ss.ctrl = 0.05; }
        if (landed + lostB < 0.05) return;
        c.log(g.name + " lands " + one(landed) + " battalions at " + where + (lostB > 0.05 ? " (" + one(lostB) + " lost on the beach)" : "") + ".", landed >= 1);
    }

    static void hurtLanding(Ctx c, Troop t, double lost) {
        lost = Math.min(lost, t.bns);
        t.bns -= lost;
        t.lost += lost;
        Loss l = c.loss(t.nat);
        l.killed += lost * 650 * 0.35;
        l.wounded += lost * 650 * 0.4;
    }

    static void mergeInto(Ctx c, Troop d) {
        for (Troop t : c.s.troops) {
            if (t == d || t.side != d.side || t.sector != d.sector || t.state != GS_READY || !t.name.equals(d.name)) continue;
            t.bns += d.bns; t.maxBns += d.maxBns; t.lost += d.lost;
            t.supply = (t.supply + d.supply) / 2;
            t.dug = Math.min(t.dug, d.dug);
            c.s.troops.remove(d);
            return;
        }
    }

    // =================================================================================================================
    //  Airborne and airlift (spec 6.4)
    // =================================================================================================================

    static void airlift(Ctx c, Squadron q, int n) {
        TaiwanState s = c.s;
        boolean helo = c.d.aircraft.get(q.type).is(TaiwanData.HELO);
        double perAc = helo ? 0.012 : 0.05;
        double kt = n * perAc;
        if (q.side == PRC) {
            int sec = -1;
            boolean ok = false;
            if (q.tKind == TK_FACILITY && s.facilities.get(q.tId).owner == PRC) { sec = c.d.facilities.get(q.tId).sector(); ok = s.facilities.get(q.tId).damage < 0.7; }
            if (q.tKind == TK_BASE && s.bases.get(q.tId).captured) { sec = c.d.bases.get(q.tId).sector(); ok = TaiwanAir.runwayOpen(c, q.tId); }
            if (!ok || sec < 0) return;
            double beCap = kt / 2.4, moved = 0;
            for (Troop t : staged(c, PRC, q.base, 120)) {
                if (beCap <= 0.01) break;
                if (!isType(t, "airborne") && !isType(t, "airassault") && !isType(t, "sof") && !isType(t, "marine")) continue;
                double take = Math.min(t.bns, beCap / weight(t));
                Troop d = split(c, t, take);
                d.state = GS_READY; d.sector = sec; d.dug = 0; d.supply = 0.8;
                beCap -= take * weight(t);
                moved += take;
                mergeInto(c, d);
            }
            if (moved > 0) c.log(q.name + " air-lands " + one(moved) + " battalions on Taiwan.", true);
            if (beCap > 0.01) s.sectors.get(sec).supplyIn += beCap * 2.4;
        } else {
            boolean taiwan = q.tKind == TK_BASE && c.d.bases.get(q.tId).cc().equals("TWN") && !s.bases.get(q.tId).captured;
            s.airliftKt += kt;
            if (taiwan) {
                int pac = TaiwanFire.take(c, TaiwanData.P_US_TH, c.d.munition("PAC3"), (int) Math.floor(kt * 8 + 0.5));
                s.pool[TaiwanData.P_TW][c.d.munition("PAC3")] += pac;
                int gm = TaiwanFire.take(c, TaiwanData.P_US_GL, c.d.munition("GMLRS"), (int) Math.floor(kt * 40 + 0.5));
                s.pool[TaiwanData.P_TW][c.d.munition("GMLRS")] += gm + Math.floor(kt * 20 + 0.5);
                s.arrivalsToday += kt / 25.0;
            } else if (q.tKind == TK_BASE) {
                for (String id : new String[]{"PAC3", "SM6", "JASSM_ER", "LRASM"}) {
                    int m = c.d.munition(id);
                    s.pool[TaiwanData.P_US_TH][m] += TaiwanFire.take(c, TaiwanData.P_US_GL, m, (int) Math.floor(kt * 3 + 0.5));
                }
            }
        }
    }

    static void airDrop(Ctx c, Squadron q, int n) {
        if (q.tKind != TK_SECTOR) return;
        TaiwanState s = c.s;
        int sec = q.tId;
        boolean helo = c.d.aircraft.get(q.type).is(TaiwanData.HELO);
        double cap = n * (helo ? 0.022 : 0.14), moved = 0;
        boolean hostile = false;
        for (Troop t : s.troops) if (t.sector == sec && t.side != q.side && t.state == GS_READY && t.bns > 0.2) { hostile = true; break; }
        double loss = hostile ? 0.18 : 0.06;
        for (Troop t : staged(c, q.side, q.base, 120)) {
            if (cap <= 0.01) break;
            if (!isType(t, "airborne") && !isType(t, "airassault") && !isType(t, "sof")) continue;
            double take = Math.min(t.bns, cap);
            Troop d = split(c, t, take);
            d.state = GS_READY; d.sector = sec; d.dug = 0; d.supply = 0.7;
            double lost = take * loss * (0.6 + c.u(Ctx.R_AMPHIB) * 0.8);
            hurtLanding(c, d, lost);
            mergeInto(c, d);
            cap -= take;
            moved += take - lost;
        }
        if (moved > 0) {
            SectorState ss = s.sectors.get(sec);
            ss.contested = true; ss.lodgment = true;
            if (ss.ctrl < 0.03) ss.ctrl = 0.03;
            c.log(q.name + " drops " + one(moved) + " battalions of " + (helo ? "air-assault" : "airborne") + " troops into " + c.d.sectors.get(sec).name() + ".", true);
        }
    }

    // =================================================================================================================
    //  The six-hour ground step (spec 6.5)
    // =================================================================================================================

    static void hurt(Ctx c, Troop t, double loss) {
        loss = Math.min(loss, t.bns);
        if (loss <= 0) return;
        t.bns -= loss;
        t.lost += loss;
        Loss l = c.loss(t.nat);
        l.killed += loss * 650 * 0.28;
        l.wounded += loss * 650 * 0.55;
        if (t.bns < 0.12) {
            l.prisoners += t.bns * 650 * 0.5;
            t.lost += t.bns;
            t.bns = 0;
            t.state = GS_DEAD;
        }
    }

    static double airSup(Ctx c, int side) {
        double a = c.capTW[side], b = c.capTW[1 - side];
        return TMath.clamp((a - b) / (a + b + 6), 0, 1);
    }

    static boolean adjacent(Ctx c, int a, int b) { for (int x : c.d.sectors.get(a).adj()) if (x == b) return true; return false; }

    static void step(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        for (Troop t : s.troops) {
            if (t.state == GS_RESERVE && t.mobilizeAt >= 0 && s.hour >= t.mobilizeAt) {
                if (t.side == PRC) { t.state = GS_STAGING; c.log(t.name + " reaches its embarkation area."); }
                else { t.state = GS_READY; t.dug = 0; }
            }
        }
        if (!s.atWar()) { for (Troop t : s.troops) if (t.state == GS_READY) t.dug += 6; return; }
        double airR = airSup(c, PRC), airB = airSup(c, US);
        for (Troop t : new ArrayList<>(s.troops)) {
            if (t.state != GS_READY && t.state != GS_MOVING) continue;
            if (t.moveTo < 0 || t.sector < 0) { t.dug += 6; continue; }
            if (!adjacent(c, t.sector, t.moveTo)) { t.moveTo = -1; t.state = GS_READY; continue; }
            t.state = GS_MOVING;
            int terr = c.d.sectors.get(t.moveTo).terrain();
            double need = 12 * (terr == 2 ? 2 : terr == 0 ? 1.4 : 1);
            need *= 1 + (t.side == US ? airR : airB) * 0.8;
            if (t.side == PRC && t.supply < 0.5) need *= 1.5;
            if (isType(t, "reserve")) need *= 1.3;
            t.progress += 6 / need;
            if (t.progress >= 1) {
                t.sector = t.moveTo; t.moveTo = -1; t.progress = 0; t.state = GS_READY; t.dug = 0;
                mergeInto(c, t);
            }
        }
        supply(c);
        for (int sec = 0; sec < s.sectors.size(); sec++) fight(c, sec, airR, airB);
        captures(c);
        for (SectorState ss : s.sectors) ss.supplyIn = 0;
    }

    static void supply(Ctx c) {
        TaiwanState s = c.s;
        int n = s.sectors.size();
        double[] need = new double[n], have = new double[n];
        for (Troop t : s.troops) {
            if (t.side != PRC || t.state == GS_DEAD || t.sector < 0 || t.state == GS_EMBARKED) continue;
            need[t.sector] += t.bns * 0.03 * (s.sectors.get(t.sector).contested ? 1.0 : 0.5);
        }
        for (int i = 0; i < n; i++) have[i] = s.sectors.get(i).supplyIn;
        int ships = 0, subs = 0;
        for (Group g : s.groups) if (g.side == PRC && !g.dead() && !g.gone) ships += TaiwanSea.countRole(c, g, "roro") + TaiwanSea.countRole(c, g, "lst") + TaiwanSea.countRole(c, g, "amphib") * 2;
        for (TaiwanState.Sub x : s.subs)
            if (x.side == US && !x.lost && x.hub < 0 && x.mission == SM_PATROL && x.at >= 0 && c.d.zones.get(x.at).id().startsWith("TS_")) subs++;
        double shipping = TMath.clamp(0.03 + ships / 40.0, 0.03, 1) * (1 - Math.min(0.6, subs * 0.12));
        for (int f = 0; f < s.facilities.size(); f++) {
            FacilityState fs = s.facilities.get(f);
            if (fs.owner != PRC) continue;
            TaiwanData.Facility fd = c.d.facilities.get(f);
            have[fd.sector()] += fd.kt() / 4.0 * Math.max(0, 1 - fs.damage) * (fd.port() ? shipping : 0.6);
            fs.damage = Math.max(0, fs.damage - 0.025);
        }
        double needMain = 0, haveMain = 0;
        for (int i = 0; i < n; i++) if (c.d.sectors.get(i).terrain() != 3) { needMain += need[i]; haveMain += have[i]; }
        for (Troop t : s.troops) {
            if (t.state == GS_DEAD || t.sector < 0 || t.state == GS_EMBARKED) continue;
            if (t.side == US) {
                double base = s.warHours() > 24 * 45 ? 0.75 + 0.25 * s.imports : 1.0;
                t.supply = TMath.clamp(0.8 * t.supply + 0.2 * base, 0.3, 1);
                continue;
            }
            double ratio = c.d.sectors.get(t.sector).terrain() == 3 ? (need[t.sector] > 0 ? have[t.sector] / need[t.sector] : 1) : (needMain > 0 ? haveMain / needMain : 1);
            t.supply = TMath.clamp(t.supply * 0.86 + 0.14 * TMath.clamp(ratio, 0, 1.2), 0.12, 1);
        }
    }

    static void fight(Ctx c, int sec, double airR, double airB) {
        TaiwanState s = c.s;
        SectorState ss = s.sectors.get(sec);
        Sector sd = c.d.sectors.get(sec);
        double p = 0, t = 0, pb = 0, tb = 0, prepB = 0, dugR = 0, supR = 0;
        for (Troop u : s.troops) {
            if (u.sector != sec || u.state != GS_READY) continue;
            if (u.side == PRC) { p += u.power() * TMath.pow(u.supply, 0.7); pb += u.bns; dugR += u.bns * Math.min(u.dug, 48); supR += u.bns * u.supply; }
            else {
                double prep = u.dug >= 48 ? 1.25 : u.dug >= 24 ? 1.1 : 1;
                t += u.power() * u.supply;
                prepB += prep * u.bns;
                tb += u.bns;
            }
        }
        if (pb <= 0.01) {
            if (ss.ctrl > 0) { ss.ctrl = Math.max(0, ss.ctrl - 0.25); if (ss.ctrl == 0) { ss.contested = false; ss.lodgment = false; } }
            return;
        }
        p *= 1 + 0.3 * airR;
        p += c.capSector[PRC][sec] * 0.05;
        double c2 = 0.6 + 0.4 * s.c2;
        t *= c2 * (1 + 0.2 * airB);
        if (s.taiwanDelay && s.warHours() < 72) t *= 0.6;
        t += c.capSector[US][sec] * 0.05;
        if (tb >= 2.5) t += 2.2 * c2 * (1 - 0.4 * airR) * Math.min(1, s.reservesReady + 0.6);
        double terr = TaiwanData.TERRAIN_DEF[sd.terrain()];
        if (tb <= 0.01 || t <= 0.01) {
            ss.ctrl = Math.min(1, ss.ctrl + 0.22 / Math.sqrt(terr));
            ss.contested = ss.ctrl < 0.95;
            return;
        }
        ss.contested = true;
        ss.lastFight = s.hour;
        double terrB = ss.ctrl < 0.5 ? terr : Math.sqrt(terr);
        double terrR = ss.ctrl >= 0.5 ? TMath.pow(terr, 0.7) : 1;
        double protB = terrB * (prepB / tb);
        double protR = terrR * (dugR / pb >= 24 ? 1.5 : 1.0) * Math.max(0.4, Math.sqrt(supR / pb));
        double effR = p / protB, effB = t / protR;
        double r = effR / effB;
        double intensity = r > 1.4 || r < 1 / 1.4 ? 1.0 : 0.45;
        double k = 0.009 * intensity;
        double lossR = k * effB, lossB = k * effR;
        if (ss.ctrl < 0.08 && r < 0.6) lossR *= 1.6;
        distribute(c, sec, PRC, lossR, pb);
        distribute(c, sec, US, lossB, tb);
        ss.ctrl = TMath.clamp(ss.ctrl + 0.08 * TMath.tanhLn(r) * intensity / Math.sqrt(terr), 0.02, 1);
        if (sd.terrain() == 0) {
            double civ = (lossR + lossB) * 120 * Math.min(2, sd.pop());
            s.civCas += civ; s.civKilled += civ * 0.3;
            ss.civDamage = Math.min(1, ss.civDamage + 0.01);
        }
        if (c.chance(Ctx.R_GROUND, 0.35)) c.log("Fighting in " + sd.name() + ": PLA " + one(pb) + " battalions against ROC " + one(tb) + ", PLA control " + (int) Math.floor(ss.ctrl * 100 + 0.5) + "%.");
    }

    static void distribute(Ctx c, int sec, int side, double loss, double tot) {
        if (tot <= 0) return;
        for (Troop u : new ArrayList<>(c.s.troops)) {
            if (u.sector != sec || u.side != side || u.state != GS_READY) continue;
            hurt(c, u, loss * u.bns / tot);
        }
    }

    static void captures(Ctx c) {
        TaiwanState s = c.s;
        for (int f = 0; f < s.facilities.size(); f++) {
            FacilityState fs = s.facilities.get(f);
            TaiwanData.Facility fd = c.d.facilities.get(f);
            double ctrl = s.sectors.get(fd.sector()).ctrl;
            if (fs.owner == US && ctrl >= 0.6) {
                fs.owner = PRC;
                if (!fs.sabotaged) { fs.damage = Math.max(fs.damage, 0.55 + c.u(Ctx.R_GROUND) * 0.3); fs.sabotaged = true; }
                c.alert("PLA forces capture " + fd.name() + (fs.sabotaged ? " (demolished by the retreating defenders)" : "") + ".");
            } else if (fs.owner == PRC && ctrl < 0.3) {
                fs.owner = US;
                c.alert("Taiwanese forces retake " + fd.name() + ".");
            }
        }
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base bd = c.d.bases.get(i);
            if (!bd.cc().equals("TWN") || bd.sector() < 0) continue;
            TaiwanState.BaseState b = s.bases.get(i);
            double ctrl = s.sectors.get(bd.sector()).ctrl;
            if (!b.captured && ctrl >= 0.65) {
                b.captured = true;
                for (Squadron q : s.squadrons) {
                    if (q.base != i || q.side != US || q.hub >= 0) continue;
                    int nb = TaiwanAir.bestBaseFor(c, q, bd.x(), bd.y());
                    if (nb >= 0 && nb != i) { q.base = nb; c.log(q.name + " evacuates to " + c.d.bases.get(nb).name() + "."); }
                    else TaiwanAir.lose(c, q, q.n, true);
                }
                for (TaiwanState.Battery bt : s.batteries) if (bt.base == i && bt.side == US && bt.alive) { bt.alive = false; c.log(bt.name + " is overrun."); }
                c.alert(bd.name() + " falls to the PLA.");
            } else if (b.captured && ctrl < 0.3) {
                b.captured = false;
                c.alert(bd.name() + " is back in Taiwanese hands.");
            }
        }
    }

    // =================================================================================================================
    //  Totals (spec 6.7)
    // =================================================================================================================

    static double bns(TaiwanState s, int sec, int side) {
        double v = 0;
        for (Troop t : s.troops) if (t.sector == sec && t.side == side && (t.state == GS_READY || t.state == GS_MOVING)) v += t.bns;
        return v;
    }

    static double power(TaiwanState s, int sec, int side) {
        double v = 0;
        for (Troop t : s.troops) if (t.sector == sec && t.side == side && (t.state == GS_READY || t.state == GS_MOVING)) v += t.power() * t.supply;
        return v;
    }

    /** km2 the PLA holds: control times area, and at least what a lodgement holds around it. */
    static double heldKm2(TaiwanState s) {
        double km = 0;
        for (int i = 0; i < s.sectors.size(); i++) {
            SectorState ss = s.sectors.get(i);
            double held = ss.ctrl * ss.km2;
            double b = bns(s, i, PRC);
            if (b > 0.2) held = Math.max(held, Math.min(ss.km2 * 0.8, b * 70));
            km += held;
        }
        return km;
    }

    /** km2 of the main island the PLA controls (no lodgement floor, no outlying islands): the "squeezed" test of spec 8.9. */
    static double mainHeld(Ctx c) {
        double km = 0;
        for (int i = 0; i < c.s.sectors.size(); i++) if (c.d.sectors.get(i).terrain() != 3) km += c.s.sectors.get(i).ctrl * c.s.sectors.get(i).km2;
        return km;
    }

    static int hottest(TaiwanState s) {
        int best = -1;
        double bv = 0.3;
        for (int i = 0; i < s.sectors.size(); i++) { double v = bns(s, i, PRC); if (v > bv) { bv = v; best = i; } }
        return best;
    }

    static int hottestRed(TaiwanState s) {
        int best = -1;
        double bv = 0.2;
        for (int i = 0; i < s.sectors.size(); i++) {
            if (bns(s, i, PRC) < 0.3) continue;
            double v = power(s, i, US);
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }

    static double objectiveValue(Ctx c, int sec) {
        Sector sd = c.d.sectors.get(sec);
        double v = 0.4 + sd.pop() * 0.15;
        if (sd.id().equals("s_taipei")) v += 3;
        if (sd.id().equals("s_new_taipei")) v += 1.5;
        for (int f = 0; f < c.d.facilities.size(); f++)
            if (c.d.facilities.get(f).sector() == sec && c.s.facilities.get(f).owner == US) v += c.d.facilities.get(f).port() ? 1.2 : 0.6;
        if (sd.terrain() == 2) v *= 0.3;
        return v;
    }
}
