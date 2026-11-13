package game.mandate.core.taiwan;

import game.mandate.core.taiwan.TaiwanData.AirType;
import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.Zone;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.Squadron;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The air war (spec 5.1-5.4): who may fly and from where, the hour's coverage tables (CAP, ASW, early warning,
 * reconnaissance, tankers, ground support), surveillance aircraft shot down, CAP-versus-CAP battles every three hours,
 * readiness, and strike packages, resolved in the hour they launch: fighters and SAMs at the release point, then the
 * salvo, the drop, the airlift or the mines.
 */
final class TaiwanAir {
    private TaiwanAir() {}

    static AirType at(Ctx c, Squadron q) { return c.d.aircraft.get(q.type); }

    /** A nation's forces act only while it is in the war (spec 5.1). A Japan that defends only itself, or an ally asked only to
     *  defend its own territory, fights once attacked (the source's "JSDF defensive only" setting had no effect, spec 13.2). */
    static boolean nationActive(Ctx c, String nat, int side) {
        TaiwanState s = c.s;
        if (side == PRC) return nat.equals("CHN") && s.atWar() || nat.equals("RUS") && TaiwanWorld.country(c, "RUS").belligerent;
        switch (nat) {
            case "TWN": return s.atWar() || s.blockade > 0;
            case "USA": return s.usCombat;
            case "JPN": case "KOR": case "PHL": case "AUS": case "GBR": case "FRA": {
                TaiwanState.Country k = TaiwanWorld.natCountry(c, nat);
                if (k == null || !k.belligerent) return false;
                return !k.defensiveOnly || k.attacked;
            }
            default: return false;
        }
    }

    static boolean runwayOpen(Ctx c, int bi) {
        Base d = c.d.bases.get(bi);
        return d.runways() > 0 && c.s.bases.get(bi).craters < Math.max(2, d.runways() * 3);
    }

    static double baseFactor(Ctx c, Squadron q) {
        if (q.carrier >= 0) {
            Group cv = c.group(q.carrier);
            if (cv == null || cv.dead() || cv.gone || cv.hub >= 0) return 0;
            for (TaiwanState.Ship sh : cv.ships) {
                TaiwanData.ShipClass k = TaiwanSea.sc(c, sh);
                if (k.carrier() || k.amphib()) return sh.mk ? 0 : cv.port >= 0 ? 0.5 : 1;
            }
            return 0;
        }
        if (q.hub >= 0) {
            String h = c.d.hubs.get(q.hub).id();
            return (h.equals("hub_diego_garcia") || h.equals("hub_alaska") || h.equals("hub_hawaii")) && at(c, q).is(TaiwanData.BMB) ? 1 : 0;
        }
        if (q.base < 0 || !runwayOpen(c, q.base)) return 0;
        return TMath.clamp(1 - c.s.bases.get(q.base).fuelDmg, 0.1, 1);
    }

    static double avail(Ctx c, Squadron q) { return q.n <= 0 ? 0 : q.n * q.readiness * 0.8 * baseFactor(c, q); }

    /** Where a squadron flies from, or null. */
    static double[] origin(Ctx c, Squadron q) {
        if (q.carrier >= 0) { Group cv = c.group(q.carrier); return cv == null || cv.hub >= 0 || cv.gone ? null : new double[]{TaiwanSea.gx(c, cv), TaiwanSea.gy(c, cv)}; }
        if (q.hub >= 0) return new double[]{c.d.hubs.get(q.hub).x(), c.d.hubs.get(q.hub).y()};
        if (q.base >= 0) return new double[]{c.d.bases.get(q.base).x(), c.d.bases.get(q.base).y()};
        return null;
    }

    static boolean flies(Ctx c, Squadron q) { return q.n > 0 && (q.hub < 0 || c.d.hubs.get(q.hub).id().equals("hub_diego_garcia")); }

    static double stationFrac(Ctx c, Squadron q, double dist, double tankF) {
        double r = at(c, q).radius() * (1 + tankF);
        return 0.3 * Math.max(0, 1 - dist / (r * 1.15));
    }

    // =================================================================================================================
    //  Coverage (spec 5.2)
    // =================================================================================================================

    static void coverage(Ctx c) {
        TaiwanState s = c.s;
        for (int side = 0; side < 2; side++) {
            java.util.Arrays.fill(c.cap[side], 0); java.util.Arrays.fill(c.asw[side], 0); java.util.Arrays.fill(c.aew[side], 0);
            java.util.Arrays.fill(c.isr[side], 0); java.util.Arrays.fill(c.capSector[side], 0);
            c.capTW[side] = 0; c.aewTW[side] = 0; c.casTW[side] = 0; c.tank[side] = 0;
        }
        for (Squadron q : s.squadrons) {
            if (!flies(c, q)) continue;
            AirType a = at(c, q);
            if (!a.is(TaiwanData.TNK) || q.mission != AM_TANK && q.mission != AM_HOLD) continue;
            if (!nationActive(c, q.nat, q.side) && s.atWar()) continue;
            c.tank[q.side] += avail(c, q) * 0.5;
        }
        for (int side = 0; side < 2; side++) c.tank[side] = Math.min(0.45, c.tank[side] / 30.0);
        TaiwanData.Point tw = c.d.point("taiwan");
        for (Squadron q : s.squadrons) {
            if (!flies(c, q)) continue;
            double av = avail(c, q);
            if (av <= 0) continue;
            double[] o = origin(c, q);
            if (o == null) continue;
            AirType a = at(c, q);
            boolean active = nationActive(c, q.nat, q.side) || !s.atWar() || q.mission == AM_ISR || q.mission == AM_AEW || q.mission == AM_ASW;
            if (!active) continue;
            switch (q.mission) {
                case AM_CAP, AM_SWEEP -> {
                    if (q.zone >= 0 && q.zone != c.d.twZone) {
                        Zone z = c.d.zones.get(q.zone);
                        double f = stationFrac(c, q, TaiwanSea.dist(o[0], o[1], z.x(), z.y()), c.tank[q.side]);
                        c.cap[q.side][q.zone] += av * f * a.quality();
                        if (z.taiwanAir()) c.capTW[q.side] += av * f * a.quality();
                    } else {
                        double f = stationFrac(c, q, TaiwanSea.dist(o[0], o[1], tw.x(), tw.y()), c.tank[q.side]);
                        c.capTW[q.side] += av * f * a.quality();
                    }
                }
                case AM_ASW -> {
                    if (q.zone >= 0) {
                        Zone z = c.d.zones.get(q.zone);
                        double f = stationFrac(c, q, TaiwanSea.dist(o[0], o[1], z.x(), z.y()), 0) * 1.6;
                        c.asw[q.side][q.zone] += av * f;
                        c.isr[q.side][q.zone] += av * f * 0.6;
                    }
                }
                case AM_AEW, AM_ISR -> {
                    if (q.zone >= 0) {
                        Zone z = c.d.zones.get(q.zone);
                        double f = stationFrac(c, q, TaiwanSea.dist(o[0], o[1], z.x(), z.y()), c.tank[q.side] * 0.5) * 1.8;
                        if (q.mission == AM_AEW) c.aew[q.side][q.zone] += av * f;
                        c.isr[q.side][q.zone] += av * f;
                        if (z.taiwanAir() && q.mission == AM_AEW) c.aewTW[q.side] += av * f;
                    }
                }
                case AM_CAS -> {
                    if (q.tKind == TK_SECTOR && q.tId >= 0 && TaiwanSea.dist(o[0], o[1], tw.x(), tw.y()) < a.radius() * 1.2) {
                        c.capSector[q.side][q.tId] += av * a.sorties() / 24.0 * 2;
                        c.casTW[q.side] += av * 0.05;
                    }
                }
                default -> { }
            }
        }
    }

    /** Will the enemy shoot at this side's aircraft under the rules of engagement? */
    static boolean shooting(Ctx c, int side, String nat) {
        TaiwanState s = c.s;
        if (!s.atWar()) return false;
        if (side == US) return s.rung[PRC] >= 3 && (nat.equals("TWN") || nationActive(c, nat, US)) || s.rung[PRC] >= 5;
        return s.rung[PRC] >= 3 || s.rung[US] >= 2;
    }

    /** The hour in the air: coverage, surveillance losses, strikes, CAP battles every three hours, readiness. */
    static void hour(Ctx c) {
        TaiwanState s = c.s;
        coverage(c);
        if (s.atWar()) {
            for (Squadron q : s.squadrons) {
                if (q.n <= 0 || q.zone < 0 || q.hub >= 0 || q.mission != AM_ISR && q.mission != AM_AEW && q.mission != AM_ASW) continue;
                if (!shooting(c, q.side, q.nat)) continue;
                int e = 1 - q.side;
                boolean twAir = q.zone == c.d.twZone || c.d.zones.get(q.zone).taiwanAir();
                double cv = (q.zone == c.d.twZone ? 0 : c.cap[e][q.zone]) + (twAir ? c.capTW[e] * 0.5 : 0);
                if (cv < 0.5) continue;
                double lam = cv * 0.01 * (at(c, q).stealth() > 0.2 ? 0.4 : 1) * (q.mission == AM_AEW ? 0.6 : 1);
                int l = c.poisson(Ctx.R_AIR, lam);
                if (l > 0) { lose(c, q, l, false); c.log(q.name + " loses " + l + " aircraft to enemy fighters over " + c.d.zones.get(q.zone).name() + "."); }
            }
        }
        launches(c);
        if (s.atWar() && s.hour % 3 == 0) capBattles(c);
        for (Squadron q : s.squadrons) {
            q.readiness = Math.min(1, q.readiness + 0.004);
            if (q.mission == AM_CAP || q.mission == AM_STRIKE) q.readiness = Math.max(0.45, q.readiness - 0.003);
        }
    }

    static void capBattles(Ctx c) {
        TaiwanState s = c.s;
        if (!(s.rung[PRC] >= 3 || s.rung[US] >= 2)) return;
        for (int z = 0; z < c.d.seaZones; z++) {
            double b = c.cap[US][z], r = c.cap[PRC][z];
            if (b < 1 || r < 1) continue;
            fightCaps(c, z, b, r);
        }
        if (c.capTW[US] > 1 && c.capTW[PRC] > 1) fightCaps(c, -1, c.capTW[US], c.capTW[PRC]);
    }

    static void fightCaps(Ctx c, int zone, double b, double r) {
        TaiwanState s = c.s;
        double k = s.rung[PRC] <= 3 && s.rung[US] <= 2 ? 0.008 : 0.028;
        double rb = r * (zone < 0 ? 1.1 : 1.0) * (1 + Math.min(0.3, (zone < 0 ? c.aewTW[PRC] : c.aew[PRC][zone]) * 0.08));
        double bb = b * (1 + Math.min(0.3, (zone < 0 ? c.aewTW[US] : c.aew[US][zone]) * 0.08));
        int lb = c.poisson(Ctx.R_AIR, k * rb * rb / (rb + bb) / 1.6), lr = c.poisson(Ctx.R_AIR, k * bb * bb / (rb + bb) / 1.6);
        if (lb > 0) capLoss(c, US, zone, lb);
        if (lr > 0) capLoss(c, PRC, zone, lr);
        if (lb + lr > 0) c.log("Air battle over " + (zone >= 0 ? c.d.zones.get(zone).name() : "Taiwan") + ": coalition lose " + lb + ", PLA lose " + lr + " aircraft.");
    }

    static void capLoss(Ctx c, int side, int zone, int n) {
        List<Squadron> cand = new ArrayList<>();
        for (Squadron q : c.s.squadrons) {
            if (q.side != side || q.n <= 0 || q.mission != AM_CAP && q.mission != AM_SWEEP) continue;
            if (zone >= 0 && q.zone != zone) continue;
            if (zone < 0 && q.zone != c.d.twZone && (q.zone < 0 || !c.d.zones.get(q.zone).taiwanAir())) continue;
            cand.add(q);
        }
        if (cand.isEmpty()) return;
        for (int i = 0; i < n; i++) {
            double tot = 0;
            for (Squadron q : cand) if (q.n > 0) tot += q.n / Math.max(0.5, at(c, q).quality());
            if (tot <= 0) return;
            double x = c.u(Ctx.R_AIR) * tot;
            for (Squadron q : cand) {
                if (q.n <= 0) continue;
                x -= q.n / Math.max(0.5, at(c, q).quality());
                if (x <= 0) { lose(c, q, 1, false); break; }
            }
        }
    }

    /** Aircraft lost (spec 5.9): crew killed in the air, ground crew on the ground. */
    static void lose(Ctx c, Squadron q, int n, boolean ground) {
        n = Math.min(n, q.n);
        if (n <= 0) return;
        q.n -= n;
        if (ground) q.lostGround += n; else q.lostAir += n;
        Loss l = c.loss(q.nat);
        if (ground) l.ground += n; else l.air += n;
        AirType a = at(c, q);
        double crew = a.is(TaiwanData.BMB) ? 4 : a.is(TaiwanData.AEW) || a.is(TaiwanData.ASW) || a.is(TaiwanData.TNK) ? 6 : a.is(TaiwanData.LIFT) ? 4 : 1;
        if (!ground) { l.killed += n * crew * 0.55; l.wounded += n * crew * 0.1; }
        else { l.killed += n * 0.15; l.wounded += n * 0.5; }
        if (q.side == US && q.nat.equals("USA")) c.s.costUS += n * (a.is(TaiwanData.BMB) ? 500 : a.quality() >= 2.4 ? 110 : 80);
    }

    // =================================================================================================================
    //  Strikes (spec 5.4), resolved in the hour they launch
    // =================================================================================================================

    static void launches(Ctx c) {
        TaiwanState s = c.s;
        TaiwanData.Point twCap = c.d.point("taiwan_cap");
        for (Squadron q : s.squadrons) {
            if (!flies(c, q)) continue;
            int m = q.mission;
            if (m != AM_STRIKE && m != AM_AIRLIFT && m != AM_AIRBORNE && m != AM_MINE) continue;
            if (!s.atWar() && m != AM_AIRLIFT) continue;
            if (m != AM_AIRLIFT && !nationActive(c, q.nat, q.side)) continue;
            AirType a = at(c, q);
            double interval = Math.max(6, 24.0 / Math.max(0.3, a.sorties()));
            if (s.hour - q.lastStrike < interval) continue;
            double av = avail(c, q);
            if (av < 1) continue;
            double[] o = origin(c, q);
            if (o == null) continue;
            double[] t = TaiwanFire.targetPos(c, q.side, q.tKind, q.tId);
            if (t == null) { if (m == AM_STRIKE) q.mission = AM_HOLD; continue; }
            int n = (int) Math.max(1, Math.min(av, q.perSortie > 0 ? q.perSortie : av));
            double dist = TaiwanSea.dist(o[0], o[1], t[0], t[1]);
            int weapon = -1, wpn = 0, escorts = 0;
            Squadron esc = null;
            double release = 0;
            int p = TaiwanFire.poolFor(q.side, q.nat);
            if (m == AM_STRIKE) {
                int w = q.weapon >= 0 ? q.weapon : TaiwanFire.autoWeapon(c, q, q.tKind);
                if (w < 0 || !TaiwanFire.weaponFits(c, w, q.tKind) || !TaiwanFire.allowed(c, q.side, q.tKind, q.tId, w)) { q.mission = AM_HOLD; continue; }
                TaiwanData.Munition mu = c.d.munitions.get(w);
                if (dist > a.radius() * (1 + c.tank[q.side]) + mu.range() * 0.9) { q.mission = AM_HOLD; continue; }
                int load = Math.max(1, a.load());
                int got = TaiwanFire.take(c, p, w, n * load);
                if (got <= 0) {
                    int alt = TaiwanFire.autoWeapon(c, q, q.tKind);
                    if (alt >= 0 && alt != w) { q.weapon = alt; continue; }
                    q.mission = AM_HOLD;
                    continue;
                }
                n = Math.max(1, Math.min(n, (int) Math.ceil(got / (double) load)));
                weapon = w; wpn = got;
                release = Math.min(dist - 5, mu.range() * 0.85);
                for (Squadron e : s.squadrons) {
                    if (e.side != q.side || e.mission != AM_ESCORT || e.escortOf != q.id || e.n <= 0) continue;
                    double ea = avail(c, e);
                    if (ea < 1) continue;
                    escorts += (int) Math.min(ea, n * 1.5);
                    esc = e;
                }
            } else if (m == AM_MINE) {
                int qs = c.d.munition("QUICKSTRIKE");
                int got = TaiwanFire.take(c, p, qs, n * 8);
                if (got <= 0) { q.mission = AM_HOLD; continue; }
                weapon = qs; wpn = got;
            }
            double rx = t[0], ry = t[1];
            if (release > 1 && dist > 0) { double f = Math.max(0, dist - release) / dist; rx = o[0] + (t[0] - o[0]) * f; ry = o[1] + (t[1] - o[1]) * f; }
            double flightKm = TaiwanSea.dist(o[0], o[1], rx, ry);
            q.lastStrike = s.hour;
            q.sorties += n;
            // fighters defending the release area
            int enemy = 1 - q.side;
            double def = 0;
            for (int z = 0; z < c.d.seaZones; z++) {
                if (c.cap[enemy][z] <= 0) continue;
                Zone zz = c.d.zones.get(z);
                if (TaiwanSea.dist(rx, ry, zz.x(), zz.y()) < zz.r() + 120) def += c.cap[enemy][z];
            }
            if (TaiwanSea.dist(rx, ry, twCap.x(), twCap.y()) < 330) def += c.capTW[enemy];
            for (Squadron f : s.squadrons) {
                if (f.side != enemy || f.n <= 0 || f.mission != AM_HOLD || f.carrier >= 0 || f.base < 0 || !at(c, f).is(TaiwanData.FTR)) continue;
                if (!nationActive(c, f.nat, enemy)) continue;
                Base fb = c.d.bases.get(f.base);
                double dd = TaiwanSea.dist(rx, ry, fb.x(), fb.y());
                if (dd < 350) def += avail(c, f) * 0.12 * at(c, f).quality() * (1 - dd / 350);
            }
            if (def > 0.5) {
                double eq = esc != null ? at(c, esc).quality() : 1;
                double escQ = escorts * eq;
                double onStrikers = def * (escQ > 0 ? Math.max(0.3, 1 - escQ / (escQ + def)) : 1);
                double hard = a.stealth() > 0.8 ? 3.5 : a.quality() >= 1.4 ? 1.6 : a.is(TaiwanData.BMB) ? 0.8 : 1.0;
                double pS = 1 - TMath.exp(-onStrikers * 0.022 / hard);
                double pE = escQ > 0 ? 1 - TMath.exp(-def * 0.02 / eq) : 0;
                double kills = escQ * 0.03 + n * a.quality() * 0.004;
                int lS = 0, lE = 0;
                for (int i = 0; i < n; i++) if (c.u(Ctx.R_AIR) < pS) lS++;
                lS = Math.min(lS, (int) Math.floor(onStrikers * 0.3 / hard + c.u(Ctx.R_AIR)));
                for (int i = 0; i < escorts; i++) if (c.u(Ctx.R_AIR) < pE) lE++;
                int ld = c.poisson(Ctx.R_AIR, kills * Math.min(1, def / 6.0));
                if (lS > 0) { lose(c, q, lS, false); wpn = (int) (wpn * (n - lS) / (double) Math.max(1, n)); n -= lS; }
                if (lE > 0 && esc != null) lose(c, esc, lE, false);
                if (ld > 0) defenderLoss(c, enemy, rx, ry, ld);
                if (lS + lE + ld > 0) c.log(q.name + " intercepted: " + (lS + lE) + " aircraft lost, " + ld + " defenders shot down.");
                if (n <= 0) continue;
            }
            double sam = TaiwanFire.samThreat(c, enemy, rx, ry);
            if (sam > 0) {
                double pl = (a.stealth() > 0.8 ? 0.01 : a.is(TaiwanData.BMB) || a.is(TaiwanData.LIFT) ? 0.09 : a.quality() >= 1.4 ? 0.035 : 0.055) * Math.min(3, sam);
                int l = 0;
                for (int i = 0; i < n; i++) if (c.u(Ctx.R_AIR) < pl) l++;
                if (l > 0) {
                    lose(c, q, l, false);
                    wpn = (int) (wpn * (n - l) / (double) Math.max(1, n));
                    n -= l;
                    c.log(q.name + " loses " + l + " aircraft to surface-to-air missiles.");
                }
                if (n <= 0) continue;
            }
            if (m == AM_STRIKE && wpn > 0) TaiwanFire.salvo(c, q.side, weapon, wpn, rx, ry, q.tKind, q.tId, q.name, flightKm / a.kmh());
            else if (m == AM_MINE && wpn > 0) {
                TaiwanFire.expend(c, q.side, weapon, wpn);
                if (q.tKind == TK_ZONE) TaiwanSea.layMines(c, q.side, q.tId, wpn);
            } else if (m == AM_AIRLIFT) TaiwanLand.airlift(c, q, n);
            else if (m == AM_AIRBORNE) TaiwanLand.airDrop(c, q, n);
        }
    }

    static void defenderLoss(Ctx c, int side, double x, double y, int n) {
        List<Squadron> cand = new ArrayList<>();
        for (Squadron q : c.s.squadrons) {
            if (q.side != side || q.n <= 0 || !at(c, q).is(TaiwanData.FTR)) continue;
            if (q.mission == AM_CAP || q.mission == AM_SWEEP) {
                if (q.zone >= 0 && q.zone != c.d.twZone) { Zone z = c.d.zones.get(q.zone); if (TaiwanSea.dist(x, y, z.x(), z.y()) < z.r() + 150) cand.add(q); }
                else cand.add(q);
            } else if (q.mission == AM_HOLD && q.base >= 0) {
                Base b = c.d.bases.get(q.base);
                if (TaiwanSea.dist(x, y, b.x(), b.y()) < 350) cand.add(q);
            }
        }
        if (cand.isEmpty()) return;
        for (int i = 0; i < n; i++) {
            Squadron q = cand.get(c.nextInt(Ctx.R_AIR, cand.size()));
            if (q.n > 0) lose(c, q, 1, false);
        }
    }

    // =================================================================================================================
    //  Bases (spec 5.1)
    // =================================================================================================================

    static boolean combatUse(Ctx c, int bi, int side, String nat) {
        TaiwanState s = c.s;
        Base d = c.d.bases.get(bi);
        if (s.bases.get(bi).captured) return side == PRC;
        if (side == PRC) return d.cc().equals("CHN") || d.cc().equals("RUS") && TaiwanWorld.country(c, "RUS").belligerent;
        String cc = d.cc();
        if (cc.equals("USA") || cc.equals("PLW") || cc.equals(nat)) return true;
        if (cc.equals("TWN")) return nat.equals("TWN") || s.usCombat;
        TaiwanState.Country host = TaiwanWorld.host(c, cc);
        if (host == null) return false;
        if (d.has(TaiwanData.F_CIVIL) && cc.equals("JPN") && !host.civilAirports) return false;
        return host.basing || host.belligerent;
    }

    static int aircraftAt(Ctx c, int bi) {
        int n = 0;
        for (Squadron q : c.s.squadrons) if (q.base == bi && q.hub < 0 && q.carrier < 0) n += q.n;
        return n;
    }

    static boolean canHost(Ctx c, int bi, Squadron q) {
        Base d = c.d.bases.get(bi);
        if (!d.has(TaiwanData.F_AIR)) return false;
        if (!combatUse(c, bi, q.side, q.nat) && !d.cc().equals(q.nat)) return false;
        if (c.s.bases.get(bi).captured && q.side == US) return false;
        return aircraftAt(c, bi) + q.n <= (d.parking() + d.shelters() + d.underground()) * 1.15;
    }

    static int bestBaseFor(Ctx c, Squadron q, double fx, double fy) {
        int best = -1;
        double bs = -1e18;
        for (int i = 0; i < c.d.bases.size(); i++) {
            if (!canHost(c, i, q)) continue;
            Base b = c.d.bases.get(i);
            double dd = TaiwanSea.dist(fx, fy, b.x(), b.y());
            double toTw = TaiwanSea.dist(0, 0, b.x(), b.y());
            double sc = -toTw * 0.6 - dd * 0.05 - aircraftAt(c, i) * 3;
            if (toTw > at(c, q).radius() * 1.6) sc -= 2000;
            if (sc > bs) { bs = sc; best = i; }
        }
        return best;
    }
}
