package game.mandate.core.taiwan;

import game.mandate.core.news.News;
import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.Munition;
import game.mandate.core.taiwan.TaiwanData.ShipClass;
import game.mandate.core.taiwan.TaiwanData.SubClass;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Contact;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Ship;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Sub;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The sea (spec 2.3, 3.5, 5.6-5.8, 5.10, 7.2): zone-to-zone movement on the precomputed sea-distance matrix,
 * reinforcements from the hubs, repair and rearm, the surface groups' missile fire, submarine warfare, shipping to
 * Taiwan under blockade, and mines.
 */
final class TaiwanSea {
    private TaiwanSea() {}

    // =================================================================================================================
    //  Where things are
    // =================================================================================================================

    static double frac(Ctx c, double depart, double arrive) {
        if (arrive <= depart) return 1;
        return TMath.clamp((c.s.hour - depart) / (arrive - depart), 0, 1);
    }

    static double gx(Ctx c, Group g) {
        if (g.hub >= 0) return c.d.hubs.get(g.hub).x();
        if (g.place >= 0) return c.d.placeX[g.place];
        double f = frac(c, g.depart, g.arrive);
        return c.d.placeX[g.from] + f * (c.d.placeX[g.to] - c.d.placeX[g.from]);
    }

    static double gy(Ctx c, Group g) {
        if (g.hub >= 0) return c.d.hubs.get(g.hub).y();
        if (g.place >= 0) return c.d.placeY[g.place];
        double f = frac(c, g.depart, g.arrive);
        return c.d.placeY[g.from] + f * (c.d.placeY[g.to] - c.d.placeY[g.from]);
    }

    /** A group on station in a zone is spread over it (its radius); one in port, at a beach or under way is a point (0). */
    static double spread(Ctx c, Group g) { return g.place >= 0 && g.place < c.d.seaZones ? c.d.zones.get(g.place).r() : 0; }

    static double sx(Ctx c, Sub s) {
        if (s.hub >= 0) return c.d.hubs.get(s.hub).x();
        if (s.place >= 0) return c.d.placeX[s.place];
        double f = frac(c, s.depart, s.arrive);
        return c.d.placeX[s.from] + f * (c.d.placeX[s.to] - c.d.placeX[s.from]);
    }

    static double sy(Ctx c, Sub s) {
        if (s.hub >= 0) return c.d.hubs.get(s.hub).y();
        if (s.place >= 0) return c.d.placeY[s.place];
        double f = frac(c, s.depart, s.arrive);
        return c.d.placeY[s.from] + f * (c.d.placeY[s.to] - c.d.placeY[s.from]);
    }

    static double spread(Ctx c, Sub s) { return s.place >= 0 && s.place < c.d.seaZones ? c.d.zones.get(s.place).r() : 0; }

    static double dist(double x1, double y1, double x2, double y2) { double dx = x1 - x2, dy = y1 - y2; return Math.sqrt(dx * dx + dy * dy); }

    /**
     * The chance that two units are within r km of each other. The source compared positions; in zones a unit on station
     * is somewhere in its zone's disc, so two of them in one zone meet with (r / R)^2, the share of the disc within reach,
     * falling off with any distance beyond the disc. Two points compare their distance.
     */
    static double meet(double ax, double ay, double aR, double bx, double by, double bR, double r) {
        double d = dist(ax, ay, bx, by);
        if (aR <= 0 && bR <= 0) return d <= r ? 1 : 0;
        double big = Math.max(aR, bR);
        double base = Math.min(1, (r / big) * (r / big));
        double excess = d - big;
        if (excess <= 0) return base;
        return base * Math.max(0, 1 - excess / r);
    }

    static int zoneOf(Ctx c, Group g) {
        if (g.hub >= 0 || g.gone) return -1;
        if (g.place >= 0) return c.d.placeZone[g.place];
        return frac(c, g.depart, g.arrive) < 0.5 ? c.d.placeZone[g.from] : c.d.placeZone[g.to];
    }

    static int zoneOf(Ctx c, Sub s) {
        if (s.hub >= 0 || s.lost) return -1;
        if (s.place >= 0) return c.d.placeZone[s.place];
        return frac(c, s.depart, s.arrive) < 0.5 ? c.d.placeZone[s.from] : c.d.placeZone[s.to];
    }

    /** Distance from Taiwan's centre (the exclusion zone, the blockade zone). */
    static double fromTaiwan(Ctx c, Group g) { return dist(gx(c, g), gy(c, g), 0, 0); }

    // =================================================================================================================
    //  Group facts
    // =================================================================================================================

    static ShipClass sc(Ctx c, Ship sh) { return c.d.ships.get(sh.cls); }

    static boolean carrierGroup(Ctx c, Group g) { for (Ship sh : g.ships) if (sc(c, sh).carrier()) return true; return false; }

    static boolean amphib(Ctx c, Group g) {
        for (Ship sh : g.ships) { String r = sc(c, sh).role(); if (r.equals("amphib") || r.equals("lst")) return true; }
        return false;
    }

    static boolean coastGuard(Ctx c, Group g) {
        for (Ship sh : g.ships) { String r = sc(c, sh).role(); if (!r.equals("coastguard") && !r.equals("militia")) return false; }
        return !g.ships.isEmpty();
    }

    static boolean hasRole(Ctx c, Group g, String role) { for (Ship sh : g.ships) if (sc(c, sh).role().equals(role)) return true; return false; }

    static int countRole(Ctx c, Group g, String role) { int k = 0; for (Ship sh : g.ships) if (sc(c, sh).role().equals(role)) k += sh.n; return k; }

    static int channels(Ctx c, Group g) { int k = 0; for (Ship sh : g.ships) if (!sh.mk) k += sc(c, sh).fire() * sh.n; return k; }

    static double asw(Ctx c, Group g) { double k = 0; for (Ship sh : g.ships) if (!sh.mk) k += sc(c, sh).asw() * sh.n; return k; }

    static double lift(Ctx c, Group g) { double k = 0; for (Ship sh : g.ships) if (!sh.mk) k += sc(c, sh).lift() * sh.n; return k; }

    static double maxKn(Ctx c, Group g) {
        double k = 99;
        boolean any = false;
        for (Ship sh : g.ships) if (!sh.mk) { k = Math.min(k, sc(c, sh).knots()); any = true; }
        if (!any) for (Ship sh : g.ships) k = Math.min(k, sc(c, sh).knots());
        return k >= 99 ? 18 : k;
    }

    static double kn(Ctx c, Group g) {
        double k = Math.min(g.kn > 0 ? g.kn : 18, maxKn(c, g));
        if (g.mission == M_CONVOY) k = Math.min(k, 14);
        return Math.max(4, k);
    }

    /** Offensive missiles aboard (anti-ship and land-attack). */
    static int offensive(Ctx c, Group g) {
        int k = 0;
        for (int m = 0; m < g.mag.length; m++) if (g.mag[m] > 0) { Munition mu = c.d.munitions.get(m); if (mu.antiShip() && !mu.kind().equals("SAM") || mu.land()) k += g.mag[m]; }
        return k;
    }

    static void addShips(Ctx c, Group g, int cls, int n) {
        if (n <= 0) return;
        ShipClass k = c.d.ships.get(cls);
        boolean merged = false;
        for (Ship sh : g.ships) if (sh.cls == cls && sh.dmg == 0 && !sh.mk) { sh.n += n; merged = true; break; }
        if (!merged) g.ships.add(new Ship(cls, n));
        g.samL += k.samL() * n;
        g.samS += k.samS() * n;
        if (k.ascmType() >= 0) g.mag[k.ascmType()] += k.ascm() * n;
        if (k.lacmType() >= 0) g.mag[k.lacmType()] += k.lacm() * n;
        if (k.specialType() >= 0) g.mag[k.specialType()] += k.special() * n;
    }

    // =================================================================================================================
    //  Orders and movement
    // =================================================================================================================

    /** Sails a group to a place (a zone, port, beach or hub edge) on a mission, from wherever it is. */
    static void sail(Ctx c, Group g, int toPlace, int mission) {
        int from;
        double extra = 0;
        if (g.place >= 0) from = g.place;
        else {
            double f = frac(c, g.depart, g.arrive);
            double leg = c.d.km[g.from][g.to];
            if (f < 0.5) { from = g.from; extra = f * leg; } else { from = g.to; extra = (1 - f) * leg; }
        }
        g.mission = mission;
        g.port = -1;
        if (from == toPlace && extra <= 0) { arrive(c, g, toPlace); return; }
        g.from = from;
        g.to = toPlace;
        g.place = -1;
        g.depart = Math.max(c.s.hour, g.readyAt);
        g.arrive = g.depart + (c.d.km[from][toPlace] + extra) / (kn(c, g) * 1.852);
    }

    static void orderZone(Ctx c, Group g, int zone, int mission) {
        int p = zone;      // the first places are the sea zones, in order
        if (g.place == p) { g.zone = zone; g.mission = mission; return; }
        if (g.place < 0 && g.to == p) { g.zone = zone; g.mission = mission; return; }
        g.zone = zone;
        sail(c, g, p, mission);
    }

    static void orderPort(Ctx c, Group g, int base) {
        Base b = c.d.bases.get(base);
        if (b.place() < 0) return;
        g.zone = -1;
        g.beach = -1; g.fac = -1;
        if (g.port == base) { g.mission = M_IDLE; return; }
        sail(c, g, b.place(), M_RETURN);
    }

    static void orderBeach(Ctx c, Group g, int beach) {
        g.beach = beach; g.fac = -1; g.zone = -1; g.beachHours = 0; g.shuttling = false;
        sail(c, g, c.d.beaches.get(beach).place(), M_ASSAULT);
    }

    static void orderFacility(Ctx c, Group g, int fac) {
        g.fac = fac; g.beach = -1; g.zone = -1; g.beachHours = 0; g.shuttling = false;
        sail(c, g, c.d.facilities.get(fac).place(), M_ASSAULT);
    }

    /** A badly damaged US carrier or big-deck amphib sails for a real shipyard and leaves the war (spec 4.2). */
    static void withdraw(Ctx c, Group g) {
        g.zone = -1; g.held = false;
        sail(c, g, c.d.hubs.get(0).place(), M_WITHDRAW);
        c.alert(g.name + " is too badly damaged to fight on and withdraws to Pearl Harbor for repairs.");
    }

    static void arrive(Ctx c, Group g, int place) {
        g.place = place;
        g.from = -1; g.to = -1;
        int base = c.d.placeBase[place];
        switch (g.mission) {
            case M_RETURN, M_MOVE -> {
                if (base >= 0 && portUsable(c, g, base)) {
                    g.port = base;
                    if (g.mission == M_RETURN) g.rearmUntil = c.s.hour + (c.s.atWar() ? 36 : 24);
                    if (g.homePort < 0) g.homePort = base;
                }
                g.mission = M_IDLE;
            }
            case M_WITHDRAW -> {
                g.hub = 0; g.gone = true; g.hubArrive = -1; g.mission = M_IDLE; g.place = -1;
                c.log(g.name + " has left the theatre for repairs.");
            }
            default -> { if (base >= 0 && g.mission == M_IDLE) g.port = base; }
        }
        g.at = zoneOf(c, g);
    }

    static boolean portUsable(Ctx c, Group g, int base) {
        Base b = c.d.bases.get(base);
        if (g.side == PRC) return b.cc().equals("CHN") || c.s.bases.get(base).captured;
        return !b.cc().equals("CHN") && !c.s.bases.get(base).captured;
    }

    static void move(Ctx c) {
        TaiwanState s = c.s;
        for (Group g : s.groups) {
            if (g.hub >= 0 || g.gone || g.dead()) { g.at = -1; continue; }
            if (g.port >= 0 && g.mission == M_IDLE) g.daysAtSea = 0;
            else g.daysAtSea += 1 / 24.0;
            if (g.place < 0 && g.arrive <= s.hour + 1) arrive(c, g, g.to);
            g.at = zoneOf(c, g);
        }
        for (Sub x : s.subs) {
            if (x.hub >= 0 || x.lost) { x.at = -1; continue; }
            if (x.mission == SM_PORT) { x.daysAtSea = 0; x.at = zoneOf(c, x); continue; }
            x.daysAtSea += 1 / 24.0;
            if (x.place < 0 && x.arrive <= s.hour + 1) {
                x.place = x.to; x.from = -1; x.to = -1;
                if (x.mission == SM_TRANSIT) x.mission = SM_PATROL;
                else if (x.mission == SM_RETURN) { x.mission = SM_PORT; x.rearmUntil = s.hour + 72; }
            }
            SubClass k = c.d.subs.get(x.cls);
            if (x.mission != SM_RETURN && x.mission != SM_PORT && x.daysAtSea > k.endurance()) orderSubPort(c, x);
            x.at = zoneOf(c, x);
        }
    }

    static void sailSub(Ctx c, Sub x, int toPlace, int mission) {
        int from;
        double extra = 0;
        if (x.place >= 0) from = x.place;
        else {
            double f = frac(c, x.depart, x.arrive);
            double leg = c.d.km[x.from][x.to];
            if (f < 0.5) { from = x.from; extra = f * leg; } else { from = x.to; extra = (1 - f) * leg; }
        }
        x.mission = mission;
        SubClass k = c.d.subs.get(x.cls);
        double kn = k.nuclear() ? 16 : 9;
        if (from == toPlace && extra <= 0) {
            x.place = toPlace;
            if (mission == SM_TRANSIT) x.mission = SM_PATROL;
            if (mission == SM_RETURN) { x.mission = SM_PORT; x.rearmUntil = c.s.hour + 72; }
            return;
        }
        x.from = from; x.to = toPlace; x.place = -1;
        x.depart = c.s.hour;
        x.arrive = x.depart + (c.d.km[from][toPlace] + extra) / (kn * 1.852);
    }

    static void orderSubZone(Ctx c, Sub x, int zone) { x.zone = zone; sailSub(c, x, zone, SM_TRANSIT); }

    /** Back to the nearest friendly port (the source ignored a chosen port, spec 13.1: here the home port is kept if usable). */
    static void orderSubPort(Ctx c, Sub x) {
        int home = x.home >= 0 && c.d.bases.get(x.home).place() >= 0 && !c.s.bases.get(x.home).captured ? x.home : nearestPort(c, x.side, x.nat, sx(c, x), sy(c, x));
        if (home < 0) home = x.home;
        x.home = home;
        x.zone = -1;
        sailSub(c, x, c.d.bases.get(home).place(), SM_RETURN);
    }

    static int nearestPort(Ctx c, int side, String nat, double px, double py) {
        int best = -1;
        double bd = 1e18;
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base b = c.d.bases.get(i);
            if (!b.has(TaiwanData.F_NAVAL) || b.place() < 0 || c.s.bases.get(i).captured) continue;
            if (!portFor(c, i, side, nat)) continue;
            double dd = dist(px, py, b.x(), b.y());
            if (dd < bd) { bd = dd; best = i; }
        }
        return best;
    }

    static boolean portFor(Ctx c, int base, int side, String nat) {
        Base b = c.d.bases.get(base);
        if (side == PRC) return b.cc().equals("CHN");
        if (b.cc().equals("CHN") || b.cc().equals("RUS") || b.cc().equals("PRK")) return false;
        if (b.cc().equals("TWN") && !nat.equals("TWN")) return false;
        return b.cc().equals(nat) || b.cc().equals("USA") || TaiwanAir.combatUse(c, base, side, nat);
    }

    /** Where a group should rearm: away from the densest missile threat if it can get there (spec 4.2). */
    static int rearmPort(Ctx c, Group g) {
        int best = -1;
        double bs = 1e18;
        double gx = gx(c, g), gy = gy(c, g);
        TaiwanData.Point fujian = c.d.point("fujian");
        int alive = g.alive();
        double eff = 0;
        for (Ship sh : g.ships) if (!sh.mk) eff += sh.n;
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base b = c.d.bases.get(i);
            if (!b.has(TaiwanData.F_NAVAL) || b.place() < 0 || c.s.bases.get(i).captured) continue;
            if (!portFor(c, i, g.side, g.nat)) continue;
            double dd = dist(gx, gy, b.x(), b.y());
            double threat = g.side == US ? Math.max(0, 1600 - dist(b.x(), b.y(), fujian.x(), fujian.y())) * 3 : 0;
            double hurt = eff < alive * 0.6 ? 1.6 : 1;
            double big = carrierGroup(c, g) || alive >= 3 ? 1 : 0.4;
            double sc = dd * hurt + threat * big + c.s.bases.get(i).portDmg * 2000;
            if (sc < bs) { bs = sc; best = i; }
        }
        return best;
    }

    // =================================================================================================================
    //  Reinforcements (spec 3.5)
    // =================================================================================================================

    static void arrivals(Ctx c) {
        TaiwanState s = c.s;
        for (Squadron q : s.squadrons) {
            if (q.hub < 0 || q.hubArrive < 0 || s.hour < q.hubArrive || q.carrier >= 0) continue;
            int dest = q.dest;
            if (dest < 0 && q.hub == hubIndex(c, "hub_diego_garcia")) { q.hubArrive = -2; continue; }
            if (dest < 0 || !TaiwanAir.canHost(c, dest, q)) dest = TaiwanAir.bestBaseFor(c, q, c.d.hubs.get(q.hub).x(), c.d.hubs.get(q.hub).y());
            if (dest < 0) { q.hubArrive = s.hour + 24; continue; }
            q.base = dest; q.hub = -1; q.hubArrive = -1; q.mission = AM_HOLD; q.readiness = 0.75;
            c.log(q.name + " arrives at " + c.d.bases.get(dest).name() + ".");
        }
        for (Group g : s.groups) {
            if (g.hub < 0 || g.gone || g.hubArrive < 0 || s.hour < g.hubArrive) continue;
            TaiwanData.Hub h = c.d.hubs.get(g.hub);
            g.hub = -1; g.hubArrive = -1; g.place = h.place();
            int to = g.nat.equals("AUS") || carrierGroup(c, g) ? c.d.need("z:PS_C") : c.d.need("z:GUAM");
            sail(c, g, to, M_MOVE);
            c.log(g.name + " enters the theatre.", true);
        }
        for (Sub x : s.subs) {
            if (x.hub < 0 || x.hubArrive < 0 || s.hour < x.hubArrive) continue;
            TaiwanData.Hub h = c.d.hubs.get(x.hub);
            x.hub = -1; x.hubArrive = -1; x.place = h.place();
            orderSubZone(c, x, c.d.need(x.side == US ? "z:PS_W" : "z:EAST"));
        }
        for (Battery b : s.batteries) {
            if (b.hub < 0 || b.hubArrive < 0 || s.hour < b.hubArrive) continue;
            int dest = landDestination(c, b);
            if (dest < 0) { b.hubArrive = s.hour + 48; continue; }
            b.base = dest; b.hub = -1; b.hubArrive = -1; b.readyAt = s.hour + 12;
            c.log(b.name + " deploys to " + c.d.bases.get(dest).name() + ".");
        }
    }

    static int hubIndex(Ctx c, String id) { return c.d.need("h:" + id); }

    static int landDestination(Ctx c, Battery b) {
        TaiwanData.LandSys ls = c.d.land.get(b.sys);
        String[] prefs = ls.sam() ? new String[]{"b_kadena_ab", "b_andersen_afb", "b_misawa_ab", "b_mcas_iwakuni", "b_yokota_ab", "b_basa_ab"}
                : ls.id().equals("NMESIS") ? new String[]{"b_basco_batanes", "b_ishigaki_garrison", "b_miyako_garrison", "b_camilo_osias_naval_base", "b_camp_hansen"}
                : new String[]{"b_laoag", "b_camp_hansen", "b_camp_kengun_kumamoto", "b_andersen_afb"};
        for (String p : prefs) {
            int bi = c.d.need("b:" + p);
            String cc = c.d.bases.get(bi).cc();
            if (cc.equals("USA")) return bi;
            TaiwanState.Country h = TaiwanWorld.host(c, cc);
            if (h != null && (h.basing || h.belligerent || ls.sam() && h.level() >= 4)) return bi;
        }
        return -1;
    }

    /** US mobilisation: the off-map reinforcement timetable starts (spec 3.5). A late US mobilisation (the pessimistic preset)
     *  adds 14 days, as the source's setting intended but never applied (spec 13.2). */
    static void mobilize(Ctx c, String why) {
        TaiwanState s = c.s;
        if (s.blueMobilized >= 0) return;
        s.blueMobilized = s.hour;
        double late = (s.simultaneous ? 120 : 0) + (s.usLate ? 336 : 0);
        for (Sub x : s.subs) {
            if (x.side != US || x.hub < 0 || x.hubArrive >= 0 || !x.nat.equals("USA")) continue;
            x.hubArrive = s.hour + 24 + c.d.hubs.get(x.hub).shipDays() * 24 * 0.75 + late * 0.5;
        }
        for (Group g : s.groups) {
            if (g.side != US || g.hub < 0 || g.gone || g.hubArrive >= 0 || !g.nat.equals("USA")) continue;
            g.hubArrive = s.hour + 72 + c.d.hubs.get(g.hub).shipDays() * 24 + late;
        }
        for (Battery b : s.batteries) {
            if (b.side != US || b.hub < 0 || b.hubArrive >= 0 || !b.nat.equals("USA")) continue;
            if (c.d.land.get(b.sys).id().equals("NMESIS") && c.d.hubs.get(b.hub).id().equals("hub_hawaii") && !TaiwanWorld.progDone(c, US, "littoral")) continue;
            b.hubArrive = s.hour + (c.d.hubs.get(b.hub).id().equals("hub_hawaii") ? 72 : 168) + late;
        }
        c.alert("The United States begins mobilising forces for the Western Pacific (" + why + ").");
        c.news(News.Kind.WORLD, "Pentagon orders forces to the Western Pacific", null, 2.5);
    }

    static void releaseBombers(Ctx c) {
        double late = c.s.usLate ? 336 : 0;
        for (Squadron q : c.s.squadrons) {
            if (q.side != US || q.hub < 0 || q.hubArrive >= 0 || !q.nat.equals("USA") || !c.d.aircraft.get(q.type).is(TaiwanData.BMB)) continue;
            q.hubArrive = c.s.hour + 30 + c.d.hubs.get(q.hub).airHours() + late;
        }
    }

    static boolean releaseNextSquadron(Ctx c) {
        Squadron best = null;
        for (Squadron q : c.s.squadrons) {
            if (q.side != US || q.hub < 0 || q.hubArrive >= 0 || q.carrier >= 0 || !q.nat.equals("USA")) continue;
            if (c.d.aircraft.get(q.type).is(TaiwanData.BMB)) continue;
            if (best == null || c.d.hubs.get(q.hub).airHours() < c.d.hubs.get(best.hub).airHours()) best = q;
        }
        if (best == null) return false;
        best.hubArrive = c.s.hour + 36 + c.d.hubs.get(best.hub).airHours() + (c.s.simultaneous ? 72 : 0) + (c.s.usLate ? 336 : 0);
        return true;
    }

    static void releaseAllies(Ctx c, String nat, boolean combat) {
        TaiwanState s = c.s;
        for (Squadron q : s.squadrons)
            if (q.nat.equals(nat) && q.hub >= 0 && q.hubArrive < 0 && q.carrier < 0 && (combat || !c.d.aircraft.get(q.type).is(TaiwanData.FTR)))
                q.hubArrive = s.hour + 24 + c.d.hubs.get(q.hub).airHours();
        if (!combat) return;
        for (Group g : s.groups) if (g.nat.equals(nat) && g.hub >= 0 && !g.gone && g.hubArrive < 0) g.hubArrive = s.hour + 48 + c.d.hubs.get(g.hub).shipDays() * 24;
        for (Sub x : s.subs) if (x.nat.equals(nat) && x.hub >= 0 && x.hubArrive < 0) x.hubArrive = s.hour + 48 + c.d.hubs.get(x.hub).shipDays() * 24;
    }

    // =================================================================================================================
    //  Repair and rearm (spec 5.10)
    // =================================================================================================================

    static void repairs(Ctx c) {
        TaiwanState s = c.s;
        for (TaiwanState.BaseState b : s.bases) {
            if (b.craters > 0) {
                b.repairAcc += 1 / 3.0;
                while (b.repairAcc >= 1 && b.craters > 0) { b.craters--; b.repairAcc -= 1; }
            }
            if (b.fuelDmg > 0) b.fuelDmg = Math.max(0, b.fuelDmg - 0.004);
            if (b.portDmg > 0) b.portDmg = Math.max(0, b.portDmg - 0.002);
        }
        for (Group g : s.groups) {
            if (g.port < 0 || g.dead() || g.hub >= 0) continue;
            if (g.rearmUntil >= 0 && s.hour >= g.rearmUntil) { rearm(c, g); g.rearmUntil = -1; }
            boolean merge = false;
            for (Ship sh : g.ships) {
                if (sh.dmg > 0) { sh.dmg = Math.max(0, sh.dmg - (sh.dmg > 0.35 ? 0.0004 : 0.0015)); if (sh.dmg == 0 && !sh.mk) merge = true; }
                if (sh.mk && sh.dmg < 0.3 && c.chance(Ctx.R_SHIP, 0.01)) { sh.mk = false; if (sh.dmg == 0) merge = true; }
            }
            if (merge) mergeLines(g);
        }
        for (Sub x : s.subs) {
            if (x.mission == SM_PORT && x.rearmUntil >= 0 && s.hour >= x.rearmUntil && !x.lost) {
                SubClass k = c.d.subs.get(x.cls);
                int p = TaiwanFire.poolFor(x.side, x.nat);
                int tm = c.d.munition(x.side == PRC ? "YU6" : "MK48");
                x.torps += TaiwanFire.take(c, p, tm, k.torps() - x.torps);
                if (k.missileType() >= 0) x.missiles += TaiwanFire.take(c, p, k.missileType(), k.missiles() - x.missiles);
                x.rearmUntil = -1; x.daysAtSea = 0;
            }
        }
        for (Battery b : s.batteries) {
            if (!b.alive || b.hub >= 0) continue;
            TaiwanData.LandSys ls = c.d.land.get(b.sys);
            int capacity = b.launchers * ls.rounds();
            if (b.ready < capacity && s.hour - b.lastFire >= ls.reloadH()) {
                int need = Math.min(capacity - b.ready, Math.max(1, (int) Math.ceil(capacity / Math.max(1, ls.reloadH()))));
                b.ready += TaiwanFire.take(c, TaiwanFire.poolFor(b.side, b.nat), ls.munition(), need);
            }
            // launchers that stop firing scatter again (the source never restored dispersal, spec 13.1)
            double rest = restDispersal(c, b);
            if (b.dispersal < rest && s.hour - b.lastFire >= 6) b.dispersal = Math.min(rest, b.dispersal + 0.02);
        }
    }

    static double restDispersal(Ctx c, Battery b) {
        TaiwanState s = c.s;
        if (b.side == PRC && !c.d.land.get(b.sys).sam() && TaiwanWorld.progDone(c, PRC, "rocket")) return 0.85;
        if (b.nat.equals("TWN") && s.atWar()) return s.twDisperseAsked ? 0.85 : 0.75;
        return 0.6;
    }

    static void mergeLines(Group g) {
        for (int i = 0; i < g.ships.size(); i++) {
            Ship a = g.ships.get(i);
            if (a.dmg != 0 || a.mk) continue;
            for (int j = g.ships.size() - 1; j > i; j--) {
                Ship b = g.ships.get(j);
                if (b.cls == a.cls && b.dmg == 0 && !b.mk) { a.n += b.n; g.ships.remove(j); }
            }
        }
    }

    /** Refills from the pool (spec 5.10). */
    static void rearm(Ctx c, Group g) {
        int p = TaiwanFire.poolFor(g.side, g.nat);
        int capL = 0, capS = 0;
        int[] cap = new int[c.d.munitions.size()];
        for (Ship sh : g.ships) {
            ShipClass k = sc(c, sh);
            capL += k.samL() * sh.n; capS += k.samS() * sh.n;
            if (k.ascmType() >= 0) cap[k.ascmType()] += k.ascm() * sh.n;
            if (k.lacmType() >= 0) cap[k.lacmType()] += k.lacm() * sh.n;
            if (k.specialType() >= 0) cap[k.specialType()] += k.special() * sh.n;
        }
        if (g.side == PRC) {
            g.samL += TaiwanFire.take(c, p, c.d.munition("HQ9"), capL - g.samL);
            g.samS += Math.max(0, capS - g.samS);
        } else {
            int needL = capL - g.samL;
            int sm2 = c.d.munition("SM2"), sm6 = c.d.munition("SM6");
            int got = TaiwanFire.take(c, p, sm2, needL / 2) + TaiwanFire.take(c, p, sm6, needL / 2);
            if (got < needL) got += TaiwanFire.take(c, p, sm2, needL - got);
            g.samL += got;
            int needS = capS - g.samS;
            if (needS > 0) g.samS += Math.min(needS, TaiwanFire.take(c, p, c.d.munition("ESSM"), needS) + needS / 3);
        }
        for (int m = 0; m < cap.length; m++) if (cap[m] > g.mag[m]) g.mag[m] += TaiwanFire.take(c, p, m, cap[m] - g.mag[m]);
    }

    // =================================================================================================================
    //  Surface groups fire (spec 5.6)
    // =================================================================================================================

    static void navalFires(Ctx c) {
        TaiwanState s = c.s;
        if (!s.atWar()) return;
        for (Group g : s.groups) {
            if (g.hub >= 0 || g.gone || g.dead() || g.port >= 0 && g.mission == M_IDLE && g.rearmUntil >= 0) continue;
            if (!TaiwanAir.nationActive(c, g.nat, g.side)) continue;
            double gx = gx(c, g), gy = gy(c, g);
            if (g.lKind != TK_NONE && g.lCount > 0) {
                int w = -1;
                for (int m = 0; m < g.mag.length; m++) {
                    Munition mu = c.d.munitions.get(m);
                    if (g.mag[m] > 0 && mu.land() && mu.kind().equals("LACM") && (w < 0 || mu.range() > c.d.munitions.get(w).range())) w = m;
                }
                double[] t = TaiwanFire.targetPos(c, g.side, g.lKind, g.lId);
                if (w >= 0 && t != null && dist(gx, gy, t[0], t[1]) <= c.d.munitions.get(w).range() && TaiwanFire.allowed(c, g.side, g.lKind, g.lId, w)) {
                    int n = Math.min(g.lCount, g.mag[w]);
                    g.mag[w] -= n;
                    TaiwanFire.salvo(c, g.side, w, n, gx, gy, g.lKind, g.lId, g.name, 0);
                }
                g.lKind = TK_NONE; g.lCount = 0;
            }
            if (!g.weaponsFree || s.hour - g.lastFire < 4) continue;
            int best = -1;
            double range = 0;
            for (int m = 0; m < g.mag.length; m++) {
                Munition mu = c.d.munitions.get(m);
                if (g.mag[m] <= 0 || !mu.antiShip()) continue;
                if (mu.range() > range) { range = mu.range(); best = m; }
            }
            if (best < 0) continue;
            Group tgt = null;
            Contact tc = null;
            double tv = 0;
            for (Contact k : s.contacts.get(g.side).values()) {
                if (k.kind != TK_GROUP || s.hour - k.seen > 2 || k.decoy) continue;
                Group e = c.group(k.id);
                if (e == null || e.dead() || e.side == g.side || e.hub >= 0 || e.gone) continue;
                double dd = dist(gx, gy, k.x, k.y);
                if (dd > range) continue;
                if (coastGuard(c, e) && s.rung[PRC] < 3) continue;
                if (g.focus >= 0 && e.id != g.focus) continue;
                double v = (carrierGroup(c, e) ? 5 : amphib(c, e) ? 4.5 : e.alive() > 2 ? 2.5 : 1.5) * (1 - dd / range * 0.3);
                if (v > tv) { tv = v; tgt = e; tc = k; }
            }
            if (tgt == null || !TaiwanFire.allowed(c, g.side, TK_GROUP, tgt.id, best)) continue;
            int count = Math.min(g.mag[best], TaiwanFire.salvoCap(c, g.side, carrierGroup(c, tgt) || amphib(c, tgt) ? 24 : 12));
            if (g.side == US && !TaiwanFire.mayDraw(c, best)) continue;
            if (count <= 0) continue;
            g.mag[best] -= count;
            g.lastFire = s.hour;
            TaiwanFire.salvo(c, g.side, best, count, gx, gy, TK_GROUP, tgt.id, g.name, dist(gx, gy, tc.x, tc.y));
        }
    }

    // =================================================================================================================
    //  Submarines (spec 5.7)
    // =================================================================================================================

    static void submarines(Ctx c) {
        TaiwanState s = c.s;
        boolean war = s.atWar();
        for (Sub x : s.subs) {
            if (x.lost || x.hub >= 0 || x.mission == SM_PORT) continue;
            int enemy = 1 - x.side;
            SubClass k = c.d.subs.get(x.cls);
            int zone = x.at;
            double px = sx(c, x), py = sy(c, x), pr = spread(c, x);
            double threat = 0;
            if (zone >= 0) threat += c.asw[enemy][zone] * 0.010;
            for (Group g : s.groups) {
                if (g.side != enemy || g.dead() || g.hub >= 0 || g.gone) continue;
                double m = meet(px, py, pr, gx(c, g), gy(c, g), spread(c, g), 40);
                if (m > 0) threat += asw(c, g) * 0.006 * m;
            }
            if (x.side == PRC && zone >= 0 && c.d.zones.get(zone).strait() && !c.d.zones.get(zone).id().startsWith("TS_")) threat += 0.01;
            double stealth = 1.25 - k.quiet();
            if (war && TaiwanFire.allowed(c, enemy, TK_SUB, x.id, -1) && c.u(Ctx.R_SUB) < threat * stealth && c.u(Ctx.R_SUB) < 0.45) {
                killSub(c, x, "located and sunk by anti-submarine forces");
                continue;
            }
            if (!war || !x.weaponsFree || !TaiwanAir.nationActive(c, x.nat, x.side)) continue;
            if (x.mission != SM_PATROL && x.mission != SM_TRANSIT) continue;
            if (s.hour - x.lastAttack < 3) continue;
            double r = k.nuclear() ? 75 : 50;
            Group best = null;
            double bv = 0;
            for (Group g : s.groups) {
                if (g.side != enemy || g.dead() || g.hub >= 0 || g.gone || g.port >= 0) continue;
                double m = meet(px, py, pr, gx(c, g), gy(c, g), spread(c, g), r);
                if (m <= 0) continue;
                if (coastGuard(c, g) && s.rung[PRC] < 3) continue;
                if (!TaiwanFire.allowed(c, x.side, TK_GROUP, g.id, -1)) continue;
                boolean am = amphib(c, g), cv = carrierGroup(c, g);
                double noise = am ? 1.4 : cv ? 1.1 : 0.9;
                // the target must also be within reach this hour: the share of the zone within the submarine's range
                if (c.u(Ctx.R_SUB) > m) continue;
                double v = noise * (am ? 3 : cv ? 2.5 : 1) * (1 - 0.5 * c.u(Ctx.R_SUB));
                if (v > bv) { bv = v; best = g; }
            }
            if (best == null) {
                for (Sub e : s.subs) {
                    if (e.side != enemy || e.lost || e.hub >= 0 || e.mission == SM_PORT) continue;
                    double m = meet(px, py, pr, sx(c, e), sy(c, e), spread(c, e), 30);
                    if (m <= 0 || c.u(Ctx.R_SUB) > m) continue;
                    if (!TaiwanFire.allowed(c, x.side, TK_SUB, e.id, -1)) continue;
                    SubClass ek = c.d.subs.get(e.cls);
                    double p = 0.04 * k.quiet() / Math.max(0.3, ek.quiet());
                    if (c.u(Ctx.R_SUB) < p && x.torps >= 2) {
                        x.torps -= 2;
                        TaiwanFire.expend(c, x.side, c.d.munition(x.side == PRC ? "YU6" : "MK48"), 2);
                        if (c.u(Ctx.R_SUB) < 0.5 * k.quiet() / (k.quiet() + ek.quiet()) * 2) killSub(c, e, "sunk by " + (x.side == US ? "a coalition" : "a PLA") + " submarine");
                        x.lastAttack = s.hour;
                    }
                    break;
                }
                continue;
            }
            double detect = 0.28 * k.quiet() * (amphib(c, best) ? 1.4 : 1.0) * (1 - Math.min(0.6, asw(c, best) * 0.05));
            if (c.u(Ctx.R_SUB) > detect) continue;
            attack(c, x, best);
        }
    }

    static void attack(Ctx c, Sub x, Group g) {
        TaiwanState s = c.s;
        SubClass k = c.d.subs.get(x.cls);
        x.lastAttack = s.hour;
        int torps = Math.min(x.torps, 4);
        boolean missile = false;
        if (torps < 2 && x.missiles > 0 && k.missileType() >= 0 && c.d.munitions.get(k.missileType()).antiShip()) {
            int n = Math.min(6, x.missiles);
            x.missiles -= n;
            TaiwanFire.expend(c, x.side, k.missileType(), n);
            TaiwanFire.shipDefense(c, g, n, k.missileType(), x.name, x.side);
            missile = true;
        }
        if (!missile) {
            if (torps < 2) { orderSubPort(c, x); return; }
            x.torps -= torps;
            TaiwanFire.expend(c, x.side, c.d.munition(x.side == PRC ? "YU6" : "MK48"), torps);
            double ph = amphib(c, g) || coastGuard(c, g) ? 0.72 : 0.55;
            ph *= 1 - Math.min(0.4, asw(c, g) * 0.03);
            int hits = 0;
            for (int i = 0; i < torps; i++) if (c.u(Ctx.R_SUB) < ph) hits++;
            if (hits > 0) {
                int sunk = 0, dis = 0;
                for (int i = 0; i < hits; i++) {
                    Ship t = TaiwanFire.pickShip(c, g, true);
                    if (t == null) break;
                    int res = TaiwanFire.damageShip(c, g, t, 1.45 * (0.7 + c.u(Ctx.R_SUB) * 0.5) / sc(c, t).hp());
                    if (res == 2) { sunk++; x.kills++; } else if (res == 1) dis++;
                }
                c.log("Submarine attack on " + g.name + ": " + (sunk > 0 ? sunk + " sunk" : dis > 0 ? dis + " disabled" : hits + " torpedo hits") + ".", sunk > 0);
            }
        }
        double cd = 0.15 + asw(c, g) * 0.06;
        if (c.u(Ctx.R_SUB) < Math.min(0.85, cd) * (1.3 - k.quiet()) && c.u(Ctx.R_SUB) < 0.5) { killSub(c, x, "sunk by " + g.name + " after attacking"); return; }
        if (x.torps < 2 && x.missiles <= 0) orderSubPort(c, x);
    }

    static void killSub(Ctx c, Sub x, String how) {
        x.lost = true;
        TaiwanState.Loss l = c.loss(x.nat);
        l.subs++;
        l.killed += c.d.subs.get(x.cls).crew();
        c.log(x.name + " " + how + ".", true);
    }

    // =================================================================================================================
    //  Shipping, the blockade and mines (spec 5.8, 7.2)
    // =================================================================================================================

    static final double NORMAL_SHIPS = 26;

    static void shipping(Ctx c) {
        TaiwanState s = c.s;
        double red = 0, blue = 0;
        for (Group g : s.groups) {
            if (g.hub >= 0 || g.gone || g.dead() || g.port >= 0) continue;
            boolean inZone = g.at >= 0 && c.d.zones.get(g.at).blockade() || fromTaiwan(c, g) < 260;
            if (!inZone) continue;
            double w = 0;
            for (Ship sh : g.ships) if (!sh.mk) w += sc(c, sh).blockadeWeight() * sh.n;
            if (g.side == PRC && (g.mission == M_BLOCKADE || g.mission == M_QUARANTINE || g.mission == M_PATROL || g.mission == M_SCREEN)) red += w;
            if (g.side == US && (g.mission == M_CONVOY || g.mission == M_PATROL)) blue += w;
        }
        if (s.blockade >= 3) for (Sub x : s.subs)
            if (!x.lost && x.hub < 0 && x.side == PRC && x.mission == SM_PATROL && dist(sx(c, x), sy(c, x), 0, 0) < 450) red += 2.5;
        if (s.blockade >= 3) red += Math.min(20, (s.minesLaid - s.minesCleared) / 150.0);
        blue += c.capTW[US] * 0.05;
        double base = NORMAL_SHIPS / 24.0, arrivals;
        if (s.blockade == 0 && s.rung[PRC] < 3) arrivals = base;
        else if (s.blockade <= 1 && s.rung[PRC] < 4) {
            arrivals = base * (s.rung[US] >= 1 ? 0.95 : 0.88);
            if (s.hour % 24 == 0 && red > 2) s.seized += c.nextInt(Ctx.R_SEA, 3);
        } else {
            double shuttle = 4.5 + (TaiwanWorld.progDone(c, US, "sealift") ? 5 : TaiwanWorld.prog(c, US, "sealift") / 25.0)
                    + (TaiwanWorld.country(c, "JPN").level() >= 4 ? 2.5 : 0) + (s.usCombat ? 3 : 0) + s.mobilized * 3;
            s.recentLosses *= 0.996;
            shuttle *= TMath.exp(-s.recentLosses / 12.0);
            if (s.rung[PRC] >= 4) shuttle *= 0.35;
            if (s.convoys) shuttle *= 1.25;
            shuttle /= 24.0;
            double i = red / (red + blue * 1.4 + 4);
            if (s.blockade <= 2 && !s.atWar()) i *= 0.8;
            double lost = shuttle * i;
            arrivals = shuttle - lost;
            if (s.blockade >= 3 || s.atWar()) {
                s.accSunk += lost * (s.blockade >= 4 ? 0.5 : 0.3);
                while (s.accSunk >= 1) {
                    s.accSunk -= 1;
                    s.merchantsLost++;
                    s.recentLosses++;
                    TaiwanState.Loss l = c.loss("TWN");
                    l.merchants++;
                    l.killed += 14;
                    if (c.chance(Ctx.R_SEA, 0.25)) c.log("A merchant ship is sunk running the blockade to Taiwan.");
                }
            } else {
                s.accSeized += lost;
                while (s.accSeized >= 1) { s.accSeized -= 1; s.seized++; }
            }
        }
        s.throughput = arrivals / base;
        s.arrivalsToday += arrivals;
        // mines in transit areas hurt moving groups
        for (Group g : s.groups) {
            if (g.hub >= 0 || g.gone || g.dead() || g.place >= 0 || g.at < 0) continue;
            double dens = s.mines[1 - g.side][g.at] + (g.side == US && c.d.zones.get(g.at).blockade() ? (s.minesLaid - s.minesCleared) * 0.02 : 0);
            if (dens <= 0) continue;
            if (c.u(Ctx.R_SEA) < Math.min(0.08, dens * 0.0002)) {
                Ship t = TaiwanFire.pickShip(c, g, false);
                if (t != null) {
                    int res = TaiwanFire.damageShip(c, g, t, (0.6 + c.u(Ctx.R_SEA) * 0.6) / sc(c, t).hp());
                    c.log("A ship of " + g.name + " strikes a mine" + (res == 2 ? " and sinks." : "."), res == 2);
                }
            }
        }
        if (s.minesLaid > s.minesCleared) s.minesCleared += 0.6;
    }

    /** Mines laid in a zone by a side (aircraft or the covert programme). */
    static void layMines(Ctx c, int side, int zone, double n) {
        c.s.mines[side][zone] += n;
        if (side == PRC) c.s.minesLaid += n;
        c.log((side == US ? "Coalition" : "PLA") + " aircraft lay " + (int) n + " mines in " + c.d.zones.get(zone).name() + ".");
    }
}
