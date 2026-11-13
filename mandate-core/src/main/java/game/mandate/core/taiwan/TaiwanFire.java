package game.mandate.core.taiwan;

import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.LandSys;
import game.mandate.core.taiwan.TaiwanData.Munition;
import game.mandate.core.taiwan.TaiwanData.ShipClass;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Contact;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.Ship;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Sub;
import game.mandate.core.taiwan.TaiwanState.Troop;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * Missiles (spec 5.5-5.6, 8.4): the rules of engagement, the munition pools, a salvo's flight to its target (resolved in
 * the hour it is fired), the ship-defence model after the CSIS wargame, ship damage, interception over land, damage to
 * bases, batteries, facilities and troops, and the land batteries' own fire.
 */
final class TaiwanFire {
    private TaiwanFire() {}

    // =================================================================================================================
    //  Pools (spec 3.4)
    // =================================================================================================================

    static int poolFor(int side, String nat) {
        if (side == PRC) return TaiwanData.P_PRC;
        return switch (nat) {
            case "JPN" -> TaiwanData.P_JP;
            case "TWN" -> TaiwanData.P_TW;
            case "AUS" -> TaiwanData.P_AU;
            case "KOR" -> TaiwanData.P_KR;
            case "PHL" -> TaiwanData.P_PH;
            default -> TaiwanData.P_US_TH;
        };
    }

    /** Draws up to {@code want} rounds; under the US "conserve" directive a quarter of the starting LRASM and SM-6 stock is kept back. */
    static int take(Ctx c, int pool, int m, int want) {
        if (want <= 0 || m < 0) return 0;
        double have = c.s.pool[pool][m];
        if (pool == TaiwanData.P_US_TH && !mayDraw(c, m)) have = Math.max(0, have - 0.25 * c.s.poolStart[m]);
        int got = (int) Math.min(Math.floor(have), want);
        if (got <= 0) return 0;
        c.s.pool[pool][m] -= got;
        return got;
    }

    /** False for LRASM and SM-6 while the US conserves munitions. */
    static boolean mayDraw(Ctx c, int m) {
        if (c.s.munitionsPolicy != 0) return true;
        String id = c.d.munitions.get(m).id();
        return !id.equals("LRASM") && !id.equals("SM6");
    }

    static void expend(Ctx c, int side, int m, int n) {
        if (n <= 0) return;
        c.s.expended[side][m] += n;
        if (side == US) c.s.costUS += n * c.d.munitions.get(m).cost();
    }

    /** The salvo caps of the munitions directive (spec 11.5): conserve halves them, surge adds half. */
    static int salvoCap(Ctx c, int side, int cap) {
        if (side != US) return cap;
        return c.s.munitionsPolicy == 0 ? Math.max(2, cap / 2) : c.s.munitionsPolicy == 2 ? cap * 3 / 2 : cap;
    }

    // =================================================================================================================
    //  Targets and the rules of engagement (spec 8.4)
    // =================================================================================================================

    static boolean weaponFits(Ctx c, int w, int kind) {
        if (w < 0) return false;
        Munition m = c.d.munitions.get(w);
        if (kind == TK_GROUP) return m.antiShip() && !m.mine() || m.id().equals("JASSM_ER") && c.s.jassmMaritime;
        if (kind == TK_ZONE) return m.mine();
        return m.land();
    }

    /** The best weapon a squadron carries for a target kind; mines are never fired at ships (the source did, spec 13.1). */
    static int autoWeapon(Ctx c, Squadron q, int kind) {
        TaiwanData.AirType at = c.d.aircraft.get(q.type);
        int p = poolFor(q.side, q.nat);
        int best = -1;
        double bs = -1;
        for (int w : at.weapons()) {
            if (!weaponFits(c, w, kind) || c.s.pool[p][w] < 1) continue;
            if (p == TaiwanData.P_US_TH && !mayDraw(c, w) && c.s.pool[p][w] <= 0.25 * c.s.poolStart[w]) continue;
            Munition m = c.d.munitions.get(w);
            double sc = m.range() * (kind == TK_GROUP && m.antiShip() ? 1.5 : 1) + c.s.pool[p][w] * 0.02;
            if (m.id().equals("JASSM_ER") && kind == TK_GROUP) sc *= 0.6;
            if (sc > bs) { bs = sc; best = w; }
        }
        return best;
    }

    /** Where a side believes a target is: contacts for mobile targets, truth for fixed ones (spec 9.1). */
    static double[] targetPos(Ctx c, int side, int kind, int id) {
        TaiwanData d = c.d;
        switch (kind) {
            case TK_BASE: return id >= 0 && id < d.bases.size() ? new double[]{d.bases.get(id).x(), d.bases.get(id).y()} : null;
            case TK_FACILITY: return id >= 0 && id < d.facilities.size() ? new double[]{d.facilities.get(id).x(), d.facilities.get(id).y()} : null;
            case TK_SECTOR: return id >= 0 && id < d.sectors.size() ? new double[]{d.sectors.get(id).x(), d.sectors.get(id).y()} : null;
            case TK_ZONE: return id >= 0 && id < d.zones.size() ? new double[]{d.zones.get(id).x(), d.zones.get(id).y()} : null;
            case TK_GROUP, TK_SUB, TK_LAND: {
                Contact k = c.s.contacts.get(side).get(id);
                if (k != null) return new double[]{k.x, k.y};
                if (kind == TK_LAND) {
                    Battery b = c.battery(id);
                    if (b != null && b.base >= 0) return new double[]{d.bases.get(b.base).x(), d.bases.get(b.base).y()};
                }
                return null;
            }
            default: return null;
        }
    }

    static boolean inZone(Ctx c, Group g) { return TaiwanSea.fromTaiwan(c, g) < 700; }

    /** May a side engage this target now? (spec 8.4, the source's War.allowed) */
    static boolean allowed(Ctx c, int side, int kind, int id, int m) {
        TaiwanState s = c.s;
        if (!s.atWar()) return false;
        if (side == PRC) {
            int r = s.rung[PRC];
            switch (kind) {
                case TK_BASE: {
                    Base b = c.d.bases.get(id);
                    String cc = b.cc();
                    if (cc.equals("TWN")) {
                        String reg = b.region();
                        boolean outlying = reg.equals("Kinmen") || reg.equals("Matsu") || reg.equals("Penghu") || reg.equals("Pratas");
                        return r >= 4 || outlying && r >= 3;
                    }
                    if (cc.equals("CHN")) return false;
                    if (cc.equals("USA") || cc.equals("PLW"))
                        return r >= 5 && (b.region().equals("Guam") || b.region().equals("Marianas") || b.region().equals("Palau") || r >= 6);
                    if (cc.equals("JPN")) return r >= 5;
                    TaiwanState.Country h = TaiwanWorld.host(c, cc);
                    if (h != null && (cc.equals("AUS") || cc.equals("IND"))) return r >= 6 || h.belligerent && r >= 5;
                    return h != null && r >= 5 && (h.belligerent || h.basing);
                }
                case TK_GROUP: {
                    Group g = c.group(id);
                    if (g == null) return r >= 3;
                    if (g.nat.equals("TWN")) return r >= 4 || r >= 3 && g.port < 0 && (m < 0 || !isBallisticOrGlide(c, m));
                    if (g.nat.equals("USA")) return r >= 5 || r >= 3 && s.usCombat && inZone(c, g);
                    TaiwanState.Country k = TaiwanWorld.natCountry(c, g.nat);
                    return k != null && (k.belligerent && r >= 3 && (inZone(c, g) || r >= 5) || r >= 5 && k.basing);
                }
                case TK_SUB: {
                    Sub x = c.sub(id);
                    if (x == null || x.nat.equals("TWN")) return r >= 3;
                    if (x.nat.equals("USA")) return r >= 5 || r >= 3 && s.usCombat && TaiwanSea.dist(TaiwanSea.sx(c, x), TaiwanSea.sy(c, x), 0, 0) < 700;
                    TaiwanState.Country k = TaiwanWorld.natCountry(c, x.nat);
                    return k != null && (k.belligerent && r >= 3 || r >= 5 && k.basing);
                }
                case TK_LAND: {
                    Battery b = c.battery(id);
                    if (b == null || b.nat.equals("TWN")) return r >= 4;
                    return r >= 5;
                }
                case TK_SECTOR: return c.d.sectors.get(id).terrain() == 3 ? r >= 3 : r >= 4;
                case TK_FACILITY: return r >= 4;
                case TK_ZONE: return s.blockade >= 3 || r >= 3;
                default: return false;
            }
        }
        int b = s.rung[US];
        boolean fighting = s.rung[PRC] >= 3 || b >= 2;
        switch (kind) {
            case TK_BASE: {
                Base bd = c.d.bases.get(id);
                if (!bd.cc().equals("CHN")) return bd.cc().equals("TWN") && s.bases.get(id).captured && fighting;
                if (!s.mainlandAllowed || s.mainland == 0) return false;
                boolean deep = bd.has(TaiwanData.F_C2) || bd.has(TaiwanData.F_MISSILE);
                if (deep && s.mainland < 2) return false;
                return deep ? b >= 5 : b >= 4;
            }
            case TK_GROUP: {
                if (!fighting) return false;
                Group g = c.group(id);
                if (g == null) return true;
                if (!g.nat.equals("CHN") && !TaiwanWorld.country(c, "RUS").belligerent) return false;
                if (g.port >= 0 && c.d.bases.get(g.port).cc().equals("CHN")) return b >= 4 && s.mainland > 0;
                if (TaiwanSea.coastGuard(c, g) && s.rung[PRC] < 3 && b < 3) return false;
                return inZone(c, g) || b >= 3 || s.rung[PRC] >= 5;
            }
            case TK_SUB: return fighting;
            case TK_LAND: {
                Battery bt = c.battery(id);
                if (bt == null) return b >= 4;
                if (bt.side == PRC && bt.nat.equals("CHN") && bt.base >= 0 && c.d.bases.get(bt.base).cc().equals("CHN")) {
                    if (s.mainland == 0) return false;
                    String sys = c.d.land.get(bt.sys).id();
                    return b >= 5 && s.mainland >= 2 || b >= 4 && (sys.equals("PHL16") || sys.equals("YJ12C") || sys.equals("SRBM"));
                }
                return fighting;
            }
            case TK_SECTOR, TK_FACILITY: return fighting;
            case TK_ZONE: return b >= 3 || s.rung[PRC] >= 3 && b >= 2;
            default: return false;
        }
    }

    static boolean isBallisticOrGlide(Ctx c, int m) { String k = c.d.munitions.get(m).kind(); return k.equals("BM") || k.equals("HGV"); }

    // =================================================================================================================
    //  A salvo (spec 5.5): resolved in the hour it is fired
    // =================================================================================================================

    static double kmh(Munition m) {
        return switch (m.speed()) { case TaiwanData.BALLISTIC -> 9000; case TaiwanData.HYPERSONIC -> 7000; case TaiwanData.SUPERSONIC -> 2900; default -> 880; };
    }

    /**
     * Fires {@code count} rounds of munition {@code m} from (fx, fy) at a target. For a ship, {@code extraHours} is the flight
     * time of the launching aircraft (the target keeps moving meanwhile); the missile's own flight is added here.
     */
    static void salvo(Ctx c, int side, int m, int count, double fx, double fy, int tKind, int tId, String from, double extraHours) {
        if (count <= 0) return;
        expend(c, side, m, count);
        Munition mu = c.d.munitions.get(m);
        switch (tKind) {
            case TK_GROUP -> {
                double[] t = targetPos(c, side, TK_GROUP, tId);
                double flight = (t == null ? 0 : TaiwanSea.dist(fx, fy, t[0], t[1]) / kmh(mu)) + extraHours;
                atGroup(c, side, m, count, tId, from, flight);
            }
            case TK_BASE -> atBase(c, side, m, count, tId, from);
            case TK_LAND -> atLand(c, side, m, count, tId);
            case TK_FACILITY -> atFacility(c, side, m, count, tId);
            case TK_SECTOR -> atSector(c, side, m, count, tId);
            default -> { }
        }
    }

    /** Speed at which a group drifts away from where it was seen, km/h: none in port or at a beach, half its speed on station. */
    static double drift(Ctx c, Group g) {
        if (g.port >= 0 || g.place >= 0 && g.place == (g.beach >= 0 ? c.d.beaches.get(g.beach).place() : -2)) return 0;
        double v = TaiwanSea.kn(c, g) * 1.852;
        return g.place >= 0 ? v * 0.5 : v;
    }

    static void atGroup(Ctx c, int side, int m, int count, int tId, String from, double flight) {
        Group g = c.group(tId);
        if (g == null || g.dead() || g.hub >= 0 || g.gone) return;
        Munition mu = c.d.munitions.get(m);
        Contact k = c.s.contacts.get(side).get(tId);
        // finding the target: the aim point is where it was reported; the ship has moved on since (spec 11.3: contact age)
        double age = k == null ? 0 : c.s.hour - k.seen;
        double off = k == null ? 0 : k.off;
        double dr = drift(c, g) * (age + flight) * c.u(Ctx.R_SALVO);
        double dd = Math.sqrt(off * off + dr * dr);
        double basket = mu.speed() == TaiwanData.BALLISTIC || mu.speed() == TaiwanData.HYPERSONIC ? 22 : mu.speed() == TaiwanData.STEALTH ? 45 : 32;
        if (g.port >= 0) basket = 60;
        double pAcq = TMath.exp(-Math.max(0, dd - 5) / basket);
        if (c.u(Ctx.R_SALVO) > pAcq) {
            c.log(count + " " + mu.name() + " from " + from + " found nothing at the aim point.");
            return;
        }
        shipDefense(c, g, count, m, from, side);
    }

    /** Interception of anti-ship missiles (spec 5.5, after the CSIS wargame). Returns hits. */
    static int shipDefense(Ctx c, Group g, int count, int m, String from, int attacker) {
        Munition mu = c.d.munitions.get(m);
        boolean poor = c.s.shipDefensePoor;
        int alive = 0;
        for (int i = 0; i < count; i++) if (c.u(Ctx.R_SALVO) >= 0.15) alive++;
        if (mu.id().equals("JASSM_ER")) alive = (int) Math.floor(alive * 0.6 + 0.5);
        boolean atkErr = c.u(Ctx.R_SALVO) < 0.05, defErr = c.u(Ctx.R_SALVO) < 0.05;
        if (atkErr) alive = 0;
        double pk;
        int opp;
        switch (mu.speed()) {
            case TaiwanData.SUBSONIC -> { pk = 0.70; opp = 2; }
            case TaiwanData.STEALTH -> { pk = 0.55; opp = 1; }
            case TaiwanData.SUPERSONIC -> { pk = 0.70; opp = 1; }
            case TaiwanData.BALLISTIC -> { pk = 0.45; opp = 1; }
            default -> { pk = 0.30; opp = 1; }
        }
        if (poor) pk *= 0.55;
        if (g.port >= 0) { opp = 1; pk *= 0.7; }
        boolean ballistic = mu.speed() == TaiwanData.BALLISTIC || mu.speed() == TaiwanData.HYPERSONIC;
        int intercepted = 0;
        if (!defErr && alive > 0) {
            int ch = Math.max(1, TaiwanSea.channels(c, g));
            for (int o = 0; o < opp && alive > 0; o++) {
                int engage = Math.min(alive, ch * 2), kills = 0;
                for (int i = 0; i < engage; i++) {
                    double p = pk;
                    int shots;
                    if (g.samL >= 2) { g.samL -= 2; shots = 2; }
                    else if (g.samS >= 2 && !ballistic) { g.samS -= 2; shots = 2; p = pk * 0.85; }
                    else if (g.samL + g.samS >= 1) { if (g.samL > 0) g.samL--; else g.samS--; shots = 1; }
                    else break;
                    double miss = shots == 2 ? (1 - p) * (1 - p) : 1 - p;
                    if (c.u(Ctx.R_SALVO) > miss) kills++;
                }
                alive -= kills;
                intercepted += kills;
            }
        }
        double term = mu.speed() == TaiwanData.BALLISTIC ? 0.35 : mu.speed() == TaiwanData.HYPERSONIC ? 0.25 : mu.speed() == TaiwanData.STEALTH ? 0.55 : 0.70;
        if (poor) term *= 0.5;
        if (defErr) term *= 0.3;
        int hits = 0;
        for (int i = 0; i < alive; i++) {
            if (c.u(Ctx.R_SALVO) < term) { intercepted++; continue; }
            if (c.u(Ctx.R_SALVO) < 0.10) continue;
            hits++;
        }
        if (hits > 0) applyHits(c, g, hits, mu.power(), ballistic, from, mu.name());
        else c.log(count + " " + mu.name() + " from " + from + " at " + g.name + ": " + intercepted + " intercepted, no hits.");
        return hits;
    }

    static void applyHits(Ctx c, Group g, int hits, double power, boolean bigFirst, String from, String what) {
        int sunk = 0, mk = 0;
        for (int h = 0; h < hits; h++) {
            Ship t = pickShip(c, g, bigFirst);
            if (t == null) break;
            int res = damageShip(c, g, t, power * (0.35 + c.u(Ctx.R_SALVO) * 0.3) / TaiwanSea.sc(c, t).hp());
            if (res == 2) sunk++; else if (res == 1) mk++;
        }
        c.log(hits + " " + what + " from " + from + " hit " + g.name + (sunk > 0 ? ": " + sunk + " sunk" : "") + (mk > 0 ? (sunk > 0 ? ", " : ": ") + mk + " disabled" : "") + ".",
                sunk > 0 && (TaiwanSea.carrierGroup(c, g) || TaiwanSea.amphib(c, g) || g.side == US));
    }

    /** Big ships first for ballistic weapons (each carrier or amphibious ship in turn with 65%), else weighted by hit points.
     *  Returns a line of one ship, split from its class's count if needed. */
    static Ship pickShip(Ctx c, Group g, boolean bigFirst) {
        if (g.ships.isEmpty()) return null;
        Ship pick = null;
        if (bigFirst) {
            outer:
            for (Ship sh : g.ships) {
                ShipClass k = TaiwanSea.sc(c, sh);
                if (!k.carrier() && !k.amphib()) continue;
                for (int i = 0; i < sh.n; i++) if (c.u(Ctx.R_SALVO) < 0.65) { pick = sh; break outer; }
            }
        }
        if (pick == null) {
            double tot = 0;
            for (Ship sh : g.ships) { ShipClass k = TaiwanSea.sc(c, sh); tot += (k.hp() + (k.carrier() ? 1.5 : 0)) * sh.n; }
            double x = c.u(Ctx.R_SALVO) * tot;
            for (Ship sh : g.ships) {
                ShipClass k = TaiwanSea.sc(c, sh);
                x -= (k.hp() + (k.carrier() ? 1.5 : 0)) * sh.n;
                if (x <= 0) { pick = sh; break; }
            }
            if (pick == null) pick = g.ships.get(g.ships.size() - 1);
        }
        if (pick.n > 1) {
            pick.n--;
            Ship one = new Ship(pick.cls, 1);
            g.ships.add(g.ships.indexOf(pick) + 1, one);
            return one;
        }
        return pick;
    }

    /** Adds damage to one ship (a fraction of it): 0 damaged, 1 mission-killed, 2 sunk (spec 5.5). */
    static int damageShip(Ctx c, Group g, Ship sh, double frac) {
        boolean wasMk = sh.mk;
        sh.dmg += frac;
        ShipClass k = TaiwanSea.sc(c, sh);
        Loss l = c.loss(g.nat);
        if (sh.dmg >= 1 && c.u(Ctx.R_SALVO) < 0.78) {
            g.ships.remove(sh);
            g.sunk++;
            l.ships++;
            String role = k.role();
            if (role.equals("carrier")) l.carriers++;
            else if (role.equals("cruiser") || role.equals("destroyer") || role.equals("frigate")) l.majors++;
            else if (role.equals("amphib") || role.equals("lst") || role.equals("roro")) l.amphibs++;
            else if (role.equals("merchant")) l.merchants++;
            double kf = role.equals("carrier") ? 0.06 + c.u(Ctx.R_SALVO) * 0.08 : role.equals("amphib") || role.equals("roro") ? 0.15 + c.u(Ctx.R_SALVO) * 0.15
                    : 0.18 + c.u(Ctx.R_SALVO) * 0.15;
            l.killed += k.crew() * kf;
            l.wounded += k.crew() * kf * 1.2;
            if (role.equals("carrier") || role.equals("amphib")) {
                for (int id : g.wing) {
                    Squadron q = c.squadron(id);
                    if (q != null && q.n > 0 && q.carrier == g.id) TaiwanAir.lose(c, q, (int) Math.floor(q.n * (0.6 + c.u(Ctx.R_SALVO) * 0.3) + 0.5), true);
                }
            }
            if (!g.cargo.isEmpty() && k.lift() > 0) {
                double share = k.lift() / Math.max(0.1, TaiwanSea.lift(c, g) + k.lift());
                for (int id : g.cargo) {
                    Troop t = c.troop(id);
                    if (t == null) continue;
                    double lost = t.bns * share * 0.9;
                    t.bns -= lost;
                    t.lost += lost;
                    Loss tl = c.loss(t.nat);
                    tl.killed += lost * 1000 * 0.45;
                    tl.wounded += lost * 1000 * 0.25;
                }
            }
            TaiwanWorld.onShipLost(c, g, k);
            if (g.dead()) TaiwanLand.lostWithShip(c, g);
            return 2;
        }
        if (sh.dmg >= 1) sh.dmg = 0.97;
        boolean newMk = sh.dmg >= 0.45 && !sh.mk;
        if (sh.dmg >= 0.45) sh.mk = true;
        l.killed += k.crew() * (newMk ? 0.025 : 0.008);
        l.wounded += k.crew() * (newMk ? 0.05 : 0.02);
        if (sh.mk && k.carrier()) for (int id : g.wing) { Squadron q = c.squadron(id); if (q != null && q.carrier == g.id) q.readiness = Math.min(q.readiness, 0.2); }
        return sh.mk && !wasMk ? 1 : 0;
    }

    // =================================================================================================================
    //  Over land: interception and damage (spec 5.5)
    // =================================================================================================================

    /** Missiles that leak through a side's defences at a point. {@code soil}: the country the point is in (allied air defences
     *  outside the war defend only their own soil; the source's test let every allied battery defend, spec 13.1). */
    static int intercept(Ctx c, int defSide, double x, double y, int count, int m, String soil) {
        Munition mu = c.d.munitions.get(m);
        int alive = count;
        boolean ballistic = mu.ballistic();
        for (Battery b : c.s.batteries) {
            if (alive <= 0) break;
            if (b.side != defSide || !b.alive || b.hub >= 0 || b.ready <= 0 || b.base < 0) continue;
            LandSys ls = c.d.land.get(b.sys);
            if (!ls.sam()) continue;
            if (defSide == US && !b.nat.equals("USA") && !b.nat.equals("TWN") && !TaiwanAir.nationActive(c, b.nat, US) && !b.nat.equals(soil)) continue;
            Base bb = c.d.bases.get(b.base);
            if (TaiwanSea.dist(x, y, bb.x(), bb.y()) > ls.reach()) continue;
            double pk = mu.kind().equals("HGV") ? ls.pkGlide() : ballistic ? ls.pkBallistic() : ls.pkCruise();
            if (mu.kind().equals("ROCKET")) pk *= 0.6;
            int engage = Math.min(alive, ls.channels() * Math.max(1, b.launchers) * 2), kills = 0;
            for (int i = 0; i < engage && b.ready > 0; i++) {
                int shots = Math.min(2, b.ready);
                b.ready -= shots;
                double miss = shots == 2 ? (1 - pk) * (1 - pk) : 1 - pk;
                if (c.u(Ctx.R_SALVO) > miss) kills++;
            }
            alive -= kills;
        }
        if (ballistic && alive > 0) {
            for (Group g : c.s.groups) {
                if (alive <= 0) break;
                if (g.side != defSide || g.dead() || g.hub >= 0 || g.gone || g.samL <= 0) continue;
                double meet = TaiwanSea.meet(x, y, 0, TaiwanSea.gx(c, g), TaiwanSea.gy(c, g), TaiwanSea.spread(c, g), 120);
                if (meet <= 0 || c.u(Ctx.R_SALVO) > meet) continue;
                int engage = Math.min(alive, TaiwanSea.channels(c, g)), kills = 0;
                double pk = mu.kind().equals("HGV") ? 0.3 : 0.5;
                for (int i = 0; i < engage && g.samL >= 2; i++) { g.samL -= 2; if (c.u(Ctx.R_SALVO) > (1 - pk) * (1 - pk)) kills++; }
                alive -= kills;
            }
        }
        if (!ballistic && mu.speed() == TaiwanData.SUBSONIC && alive > 0) {
            double ftr = 0;
            for (Squadron q : c.s.squadrons) {
                if (q.side != defSide || q.base < 0 || q.n <= 0 || q.mission != AM_HOLD || !c.d.aircraft.get(q.type).is(TaiwanData.FTR)) continue;
                Base bb = c.d.bases.get(q.base);
                if (TaiwanSea.dist(x, y, bb.x(), bb.y()) < 250) ftr += TaiwanAir.avail(c, q) * 0.15;
            }
            int kills = 0;
            for (int i = 0; i < alive && i < ftr; i++) if (c.u(Ctx.R_SALVO) < 0.5) kills++;
            alive -= kills;
        }
        return Math.max(0, alive);
    }

    static void atBase(Ctx c, int side, int m, int count, int bi, String from) {
        TaiwanState s = c.s;
        Base d = c.d.bases.get(bi);
        TaiwanState.BaseState b = s.bases.get(bi);
        int defSide = 1 - side;
        int leak = intercept(c, defSide, d.x(), d.y(), count, m, d.cc());
        int fails = 0;
        for (int i = 0; i < leak; i++) if (c.u(Ctx.R_SALVO) < 0.12) fails++;
        leak -= fails;
        b.hits += Math.max(0, leak);
        TaiwanWorld.onBaseStruck(c, bi, side, leak);
        Munition mu = c.d.munitions.get(m);
        if (leak <= 0) { c.log(count + " " + mu.name() + " at " + d.name() + ": all intercepted or failed."); return; }
        int destroyed = 0;
        if (d.has(TaiwanData.F_AIR)) {
            int sub = 0, uni = leak;
            if (mu.cluster()) { sub = (int) Math.floor(leak * 0.65 + 0.5); uni = leak - sub; }
            if (sub > 0) destroyed += parkedKills(c, bi, sub, mu.power());
            for (int i = 0; i < uni; i++) {
                double x = c.u(Ctx.R_SALVO);
                if (x < 0.55) b.craters++;
                else if (x < 0.8) destroyed += shelterHit(c, bi);
                else b.fuelDmg = Math.min(0.9, b.fuelDmg + 0.12);
            }
        }
        if (d.has(TaiwanData.F_NAVAL) || d.has(TaiwanData.F_PORT)) {
            int shipHits = d.has(TaiwanData.F_AIR) ? leak / 3 : leak;
            b.portDmg = Math.min(1, b.portDmg + 0.08 * shipHits);
            for (Group g : s.groups) {
                if (shipHits <= 0) break;
                if (g.port != bi || g.dead() || g.side == side) continue;
                int h = Math.min(shipHits, 2 + c.nextInt(Ctx.R_SALVO, 3));
                applyHits(c, g, h, mu.power(), false, from, mu.name());
                shipHits -= h;
            }
            if (d.has(TaiwanData.F_ENERGY)) TaiwanWorld.energyHit(c, leak);
        }
        if (d.has(TaiwanData.F_C2)) {
            b.c2Dmg = Math.min(1, b.c2Dmg + 0.15 * leak);
            // a command site on Taiwan: the island's command and control suffers (the source's fire plan aimed decapitation
            // strikes at such bases but only its separate C2 order reached Taiwan's command; here both do)
            if (d.cc().equals("TWN")) { s.c2 = Math.max(0.25, s.c2 - 0.06 * leak); s.civCas += leak * 8; s.civKilled += leak * 3; }
        }
        if (d.has(TaiwanData.F_MISSILE) || d.has(TaiwanData.F_ARMY)) {
            for (Battery bt : s.batteries) {
                if (bt.base != bi || !bt.alive || bt.side == side || bt.hub >= 0) continue;
                int kills = 0;
                for (int i = 0; i < leak; i++) if (c.u(Ctx.R_SALVO) < 0.18 * (1 - bt.dispersal)) kills++;
                bt.launchers = Math.max(0, bt.launchers - kills);
                if (bt.launchers == 0) { bt.alive = false; c.log(bt.name + " is destroyed."); }
            }
        }
        if (d.has(TaiwanData.F_ENERGY) && !d.has(TaiwanData.F_NAVAL) && !d.has(TaiwanData.F_PORT)) TaiwanWorld.energyHit(c, leak);
        String nat = d.cc().equals("TWN") ? "TWN" : d.cc().equals("CHN") ? "CHN" : d.cc().equals("JPN") ? "JPN" : "USA";
        Loss l = c.loss(nat);
        l.killed += leak * 0.7;
        l.wounded += leak * 2.2;
        if (d.cc().equals("TWN")) { s.civCas += leak * 4; s.civKilled += leak * 1.5; }
        c.log(count + " " + mu.name() + " strike " + d.name() + ": " + leak + " impacts" + (destroyed > 0 ? ", " + destroyed + " aircraft destroyed on the ground" : "")
                + (!TaiwanAir.runwayOpen(c, bi) && d.has(TaiwanData.F_AIR) ? ", runways closed" : "") + ".", destroyed >= 10);
    }

    /** Submunitions on aircraft parked in the open (the density model of spec 5.5). Dispersal counts once (the source counted
     *  the US dispersal programme twice, spec 13.1). */
    static int parkedKills(Ctx c, int bi, int missiles, double power) {
        TaiwanState.BaseState b = c.s.bases.get(bi);
        Base d = c.d.bases.get(bi);
        int shelters = Math.max(0, d.shelters() - b.hasLost) + d.underground();
        int total = 0;
        List<Squadron> here = new ArrayList<>();
        for (Squadron q : c.s.squadrons) if (q.base == bi && q.hub < 0 && q.carrier < 0 && q.n > 0) { here.add(q); total += q.n; }
        if (total == 0) return 0;
        int open = Math.max(0, total - shelters);
        if (open <= 0) return 0;
        double area = Math.max(20, d.parking()) * b.dispersal;
        double lethal = 7.5 * power;
        double surv = TMath.pow(Math.max(0.02, 1 - lethal / area), missiles);
        int kills = (int) Math.floor(open * (1 - surv) + 0.5);
        int done = 0;
        for (Squadron q : here) {
            int share = (int) Math.floor(kills * q.n / (double) total + 0.5);
            share = Math.min(share, Math.max(0, q.n - (int) Math.floor(q.n * shelters / (double) total + 0.5)));
            if (share > 0) { TaiwanAir.lose(c, q, share, true); done += share; }
        }
        return done;
    }

    static int shelterHit(Ctx c, int bi) {
        TaiwanState.BaseState b = c.s.bases.get(bi);
        if (c.d.bases.get(bi).shelters() - b.hasLost <= 0) { b.fuelDmg = Math.min(0.9, b.fuelDmg + 0.05); return 0; }
        b.hasLost++;
        if (c.u(Ctx.R_SALVO) < 0.55) {
            for (Squadron q : c.s.squadrons) if (q.base == bi && q.hub < 0 && q.carrier < 0 && q.n > 0) { TaiwanAir.lose(c, q, 1, true); return 1; }
        }
        return 0;
    }

    static void atLand(Ctx c, int side, int m, int count, int id) {
        Battery b = c.battery(id);
        if (b == null || !b.alive || b.base < 0) return;
        Base bb = c.d.bases.get(b.base);
        int leak = intercept(c, b.side, bb.x(), bb.y(), count, m, bb.cc());
        Contact k = c.s.contacts.get(side).get(id);
        double dd = k == null ? 0 : k.off;
        double pAcq = TMath.exp(-dd / 15.0);
        boolean sam = c.d.land.get(b.sys).sam();
        int kills = 0;
        for (int i = 0; i < leak; i++) if (c.u(Ctx.R_SALVO) < pAcq * (sam ? 0.35 : 0.22) * (1 - b.dispersal * 0.6)) kills++;
        b.launchers = Math.max(0, b.launchers - kills);
        if (b.launchers == 0) { b.alive = false; c.log(b.name + " is destroyed."); }
        else if (kills > 0) c.log(b.name + " loses " + kills + " launchers.");
    }

    static void atFacility(Ctx c, int side, int m, int count, int f) {
        TaiwanState.FacilityState fs = c.s.facilities.get(f);
        TaiwanData.Facility fd = c.d.facilities.get(f);
        int leak = intercept(c, fs.owner, fd.x(), fd.y(), count, m, fs.owner == US ? "TWN" : "CHN");
        fs.damage = Math.min(1, fs.damage + leak * 0.07 * c.d.munitions.get(m).power());
    }

    static void atSector(Ctx c, int side, int m, int count, int sec) {
        TaiwanState s = c.s;
        Munition mu = c.d.munitions.get(m);
        int enemy = 1 - side;
        double kill = count * (mu.kind().equals("ROCKET") ? 0.006 : 0.012) * mu.power();
        double tot = 0;
        for (Troop t : s.troops) if (t.side == enemy && t.sector == sec && t.state != GS_DEAD && t.state != GS_EMBARKED) tot += t.bns;
        if (tot > 0) for (Troop t : new ArrayList<>(s.troops)) {
            if (t.side != enemy || t.sector != sec || t.state == GS_DEAD || t.state == GS_EMBARKED) continue;
            TaiwanLand.hurt(c, t, kill * t.bns / tot * (t.dug > 24 ? 0.6 : 1.0));
        }
        TaiwanData.Sector sd = c.d.sectors.get(sec);
        if (sd.terrain() == 0 || sd.pop() > 1) {
            double civ = count * (mu.kind().equals("ROCKET") ? 3 : 6) * Math.min(2, sd.pop());
            s.civCas += civ;
            s.civKilled += civ * 0.35;
            s.sectors.get(sec).civDamage = Math.min(1, s.sectors.get(sec).civDamage + count * 0.002);
        }
    }

    // =================================================================================================================
    //  Land batteries fire (spec 5.6)
    // =================================================================================================================

    static void batteries(Ctx c) {
        TaiwanState s = c.s;
        if (!s.atWar()) return;
        for (Battery b : s.batteries) {
            if (!b.alive || b.hub >= 0 || b.base < 0 || b.ready <= 0 || b.launchers <= 0) continue;
            LandSys ls = c.d.land.get(b.sys);
            if (ls.sam() || s.hour < b.readyAt || !TaiwanAir.nationActive(c, b.nat, b.side)) continue;
            if (b.tKind == TK_NONE && !autoTarget(c, b)) continue;
            double[] t = targetPos(c, b.side, b.tKind, b.tId);
            Base bb = c.d.bases.get(b.base);
            if (t == null || TaiwanSea.dist(bb.x(), bb.y(), t[0], t[1]) > c.d.munitions.get(ls.munition()).range()
                    || !weaponFits(c, ls.munition(), b.tKind) || !allowed(c, b.side, b.tKind, b.tId, ls.munition())) { b.tKind = TK_NONE; b.salvo = 0; continue; }
            double rate = b.side == PRC ? 0.3 + 0.7 * TaiwanWorld.prog(c, PRC, "rocket") / 100.0 : 1;
            int per = (int) Math.max(1, Math.floor(b.launchers * rate + 0.5));
            int n = Math.min(b.ready, b.salvo > 0 ? Math.min(b.salvo, per * ls.rounds()) : per);
            if (n <= 0) continue;
            b.ready -= n;
            b.fired += n;
            b.lastFire = s.hour;
            int tk = b.tKind, ti = b.tId;
            b.tKind = TK_NONE; b.salvo = 0;
            b.dispersal = Math.max(0.3, b.dispersal - 0.1);
            salvo(c, b.side, ls.munition(), n, bb.x(), bb.y(), tk, ti, b.name, 0);
        }
    }

    /** Coastal anti-ship batteries engage enemy ships in range on their own. */
    static boolean autoTarget(Ctx c, Battery b) {
        TaiwanState s = c.s;
        LandSys ls = c.d.land.get(b.sys);
        Munition mu = c.d.munitions.get(ls.munition());
        if (!mu.antiShip()) return false;
        if ((mu.id().equals("DF26") || mu.id().equals("DF21D")) && !b.asbm) return false;
        double range = mu.range();
        Base bb = c.d.bases.get(b.base);
        Group best = null;
        double bs = 0;
        for (Contact k : s.contacts.get(b.side).values()) {
            if (k.kind != TK_GROUP || s.hour - k.seen > 3) continue;
            Group g = c.group(k.id);
            if (g == null || g.dead() || g.side == b.side || g.hub >= 0 || g.gone) continue;
            double dd = TaiwanSea.dist(bb.x(), bb.y(), k.x, k.y);
            if (dd > range) continue;
            boolean cg = TaiwanSea.coastGuard(c, g);
            if (cg && s.rung[PRC] < 3) continue;
            double v = TaiwanSea.amphib(c, g) ? 5 : TaiwanSea.carrierGroup(c, g) ? 4 : cg ? 0.6 : 2;
            v *= 1 - dd / range * 0.4;
            if (v > bs) { bs = v; best = g; }
        }
        if (best == null || !allowed(c, b.side, TK_GROUP, best.id, ls.munition())) return false;
        b.tKind = TK_GROUP; b.tId = best.id;
        b.salvo = Math.max(4, Math.min(b.ready, salvoCap(c, b.side, TaiwanSea.amphib(c, best) ? 12 : 8)));
        return true;
    }

    /** Surface-to-air threat to aircraft at a point (spec 5.4). */
    static double samThreat(Ctx c, int side, double x, double y) {
        double t = 0;
        for (Group g : c.s.groups) {
            if (g.side != side || g.dead() || g.hub >= 0 || g.gone || g.samL <= 0) continue;
            double m = TaiwanSea.meet(x, y, 0, TaiwanSea.gx(c, g), TaiwanSea.gy(c, g), TaiwanSea.spread(c, g), 160);
            if (m > 0) t += Math.min(1.5, g.samL / 60.0) * m;
        }
        for (Battery b : c.s.batteries) {
            if (b.side != side || !b.alive || b.hub >= 0 || b.base < 0 || b.ready <= 0) continue;
            LandSys ls = c.d.land.get(b.sys);
            if (!ls.sam()) continue;
            Base bb = c.d.bases.get(b.base);
            if (TaiwanSea.dist(x, y, bb.x(), bb.y()) < ls.airReach()) t += 0.7;
        }
        return t;
    }
}
