package game.mandate.core.taiwan;

import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.ShipClass;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Country;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Loss;
import game.mandate.core.taiwan.TaiwanState.SectorState;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Troop;

import java.util.Locale;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The world around the war, day by day (spec 1.5, 7-8): preparation programmes, munitions production, Taiwan's energy,
 * chips and will, the economy channels, politics, the allies, nuclear risk and how it ends; and the moments that change
 * the war: H-hour, America's entry, a rung climbed, a ship sunk, a base struck.
 */
final class TaiwanWorld {
    private TaiwanWorld() {}

    // =================================================================================================================
    //  Lookups
    // =================================================================================================================

    static Country country(Ctx c, String id) { return c.s.countries.get(c.d.country(id)); }

    /** The country hosting bases with this country code (Singapore's are ASEAN's). */
    static Country host(Ctx c, String cc) {
        return switch (cc) {
            case "JPN", "KOR", "PHL", "AUS", "IND" -> country(c, cc);
            case "VNM" -> country(c, "VNM");
            case "SGP" -> country(c, "ASEAN");
            default -> null;
        };
    }

    static Country natCountry(Ctx c, String nat) {
        return switch (nat) {
            case "JPN", "KOR", "PHL", "AUS", "GBR", "FRA", "RUS", "PRK", "IND", "VNM" -> country(c, nat);
            default -> null;
        };
    }

    static int progIndex(Ctx c, int side, String id) { return c.d.programme(side, id); }

    static double prog(Ctx c, int side, String id) { return c.s.progPct[side][progIndex(c, side, id)]; }

    static boolean progDone(Ctx c, int side, String id) { return prog(c, side, id) >= 100; }

    static boolean progOn(Ctx c, int side, String id) { return c.s.progOn[side][progIndex(c, side, id)]; }

    static double amphibLostFrac(Ctx c) {
        int alive = 0;
        for (Group g : c.s.groups) if (g.side == PRC) alive += TaiwanSea.countRole(c, g, "amphib") + TaiwanSea.countRole(c, g, "lst");
        int tot = alive + c.s.amphSunk;
        return tot == 0 ? 0 : c.s.amphSunk / (double) tot;
    }

    static void deterrence(Ctx c, double delta) {
        c.s.deterrenceG += delta;
        if (!c.s.atWar() && !c.s.over()) c.effect(new Effect.Deterrence(delta));
    }

    // =================================================================================================================
    //  The day (spec 1.5)
    // =================================================================================================================

    static void daily(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        programmes(c);
        logistics(c);
        taiwan(c);
        economy(c);
        politics(c);
        diplomacy(c);
        TaiwanIntel.daily(c);
        nuclear(c);
        s.dipCap[US] = Math.min(60, s.dipCap[US] + 3);
        s.dipCap[PRC] = Math.min(60, s.dipCap[PRC] + 3);
        s.deterSeen = TaiwanAI.deterrence(c);
        record(c);
        outcome(c);
    }

    static void record(Ctx c) {
        TaiwanState s = c.s;
        Loss us = c.loss("USA"), cn = c.loss("CHN"), tw = c.loss("TWN");
        s.history.add(new double[]{s.day(), us.killed, cn.killed, tw.killed + s.civKilled, us.ships, cn.ships, us.air + us.ground, cn.air + cn.ground,
                s.plaAshore(), TaiwanLand.heldKm2(s), s.elec, s.throughput, s.nukeRisk, s.gap[E_WORLD], s.will, s.usSupport});
        if (s.history.size() > 400) s.history.remove(0);
    }

    // =================================================================================================================
    //  Programmes (spec 8.6)
    // =================================================================================================================

    static void programmes(Ctx c) {
        TaiwanState s = c.s;
        for (int side = 0; side < 2; side++) {
            var list = c.d.programmes(side);
            for (int i = 0; i < list.size(); i++) {
                double before = s.progPct[side][i];
                if (s.progOn[side][i]) {
                    if (side == US && list.get(i).id().equals("arms") && s.blockade >= 2) continue;   // no deliveries through a blockade
                    if (before < 100) s.progPct[side][i] = Math.min(100, before + 100.0 / list.get(i).days());
                    if (side == US && list.get(i).id().equals("fighters") && before < 100 && s.day() % 2 == 0) TaiwanSea.releaseNextSquadron(c);
                } else if (before > 0 && before < 100) s.progPct[side][i] = Math.max(0, before - (side == PRC ? 4 : 5));
                if (before < 100 && s.progPct[side][i] >= 100) done(c, side, list.get(i).id());
            }
        }
    }

    static void start(Ctx c, int side, String id) {
        TaiwanState s = c.s;
        int i = progIndex(c, side, id);
        if (s.progOn[side][i]) return;
        s.progOn[side][i] = true;
        TaiwanData.Programme p = c.d.programmes(side).get(i);
        if (s.progPct[side][i] < 100) c.log((side == US ? "US" : "PRC") + " programme started: " + p.name() + ".");
        if (side == US) {
            switch (id) {
                case "showforce" -> c.news(News.Kind.WORLD, "American carriers head for waters near Taiwan", null, 2.5);
                case "mobilise" -> { c.news(News.Kind.WORLD, "The president declares a national emergency over Taiwan", null, 3); s.approval += 2; c.effect(new Effect.Approval(2)); }
                case "departure" -> { c.news(News.Kind.WORLD, "Americans are ordered to leave Taiwan", null, 2.5); s.will -= 2; }
                case "advisers" -> s.nationalism += 3;
                default -> { }
            }
            if (id.equals("mobilise") || id.equals("subs") || id.equals("bombers")) TaiwanSea.mobilize(c, p.name().toLowerCase(Locale.ROOT));
            if (id.equals("bombers")) TaiwanSea.releaseBombers(c);
            if (id.equals("subs")) {
                for (TaiwanState.Sub x : s.subs)
                    if (x.side == US && x.nat.equals("USA") && x.hub >= 0 && x.hubArrive < 0)
                        x.hubArrive = s.hour + 24 + c.d.hubs.get(x.hub).shipDays() * 24 * 0.75 + (s.usLate ? 168 : 0);
            }
        } else if (id.equals("exercise")) c.news(News.Kind.WORLD, "The PLA announces a joint exercise around Taiwan", null, 2.5);
    }

    static void stop(Ctx c, int side, String id) { c.s.progOn[side][progIndex(c, side, id)] = false; }

    static void done(Ctx c, int side, String id) {
        TaiwanState s = c.s;
        c.log((side == US ? "US" : "PRC") + " programme complete: " + c.d.programmes(side).get(progIndex(c, side, id)).name() + ".");
        if (side == PRC) {
            switch (id) {
                case "rocket" -> { for (Battery b : s.batteries) if (b.side == PRC && !c.d.land.get(b.sys).sam()) b.dispersal = 0.85; }
                case "roro" -> TaiwanEngine.buildRoro(c);
                case "air" -> { for (Squadron q : s.squadrons) if (q.side == PRC) q.readiness = 1; }
                case "mines" -> {
                    // the mines come out of the PLA's stock (the source counted them without drawing any, spec 13.2)
                    int got = TaiwanFire.take(c, TaiwanData.P_PRC, c.d.munition("MINES"), 350);
                    s.minesLaid += got;
                    TaiwanFire.expend(c, PRC, c.d.munition("MINES"), got);
                }
                case "followon" -> { for (Troop t : s.troops) if (t.side == PRC && t.state == GS_RESERVE) t.mobilizeAt = s.hour + 24 + c.nextInt(Ctx.R_WORLD, 96); }
                case "space" -> s.satHealth[PRC] = Math.min(1.2, s.satHealth[PRC] + 0.15);
                case "influence" -> { s.will -= 5; s.unity = Math.max(0.3, s.unity - 0.1); }
                default -> { }
            }
            return;
        }
        switch (id) {
            case "readiness" -> { for (Squadron q : s.squadrons) if (q.side == US) q.readiness = 1; deterrence(c, 3); }
            case "disperse" -> { for (int i = 0; i < s.bases.size(); i++) if (!c.d.bases.get(i).cc().equals("CHN") && !s.bases.get(i).captured) s.bases.get(i).dispersal = 1.4; }
            case "munitions" -> {
                String[][] add = {{"JASSM_ER", "160"}, {"SM6", "80"}, {"PAC3", "120"}, {"TLAM", "250"}, {"LRASM", "30"}, {"SM2", "150"}, {"PRSM", "40"}, {"THAAD", "24"}};
                for (String[] a : add) s.pool[TaiwanData.P_US_GL][c.d.munition(a[0])] += Double.parseDouble(a[1]);
                country(c, "EU").stance -= 1;
            }
            case "arms" -> {
                double[] tw = s.pool[TaiwanData.P_TW];
                tw[c.d.munition("HARPOON")] += 64; tw[c.d.munition("PAC3")] += 60; tw[c.d.munition("GMLRS")] += 500; tw[c.d.munition("ATACMS")] += 40;
                TaiwanEngine.addBattery(c, US, "TWN", "tw_hcds_rushed", "Harpoon coastal battalion (rushed)", c.d.base("b_port_of_kaohsiung"), c.d.need("l:HCDS"), 8, false);
                for (Troop t : s.troops) if (t.nat.equals("TWN")) t.quality *= 1.06;
                s.will += 3;
            }
            case "declassify" -> {
                for (String k : new String[]{"JPN", "AUS", "PHL", "GBR", "EU", "KOR"}) country(c, k).stance += 5;
                s.will += 3; s.usSupport += 3;
                deterrence(c, 4);
                c.news(News.Kind.WORLD, "US intelligence: China is preparing military action against Taiwan", null, 3);
            }
            case "showforce" -> { deterrence(c, 7); s.nationalism += 2; }
            case "littoral" -> {
                for (Battery b : s.batteries)
                    if (b.side == US && b.hub >= 0 && b.hubArrive < 0 && c.d.land.get(b.sys).id().equals("NMESIS")) b.hubArrive = s.hour + 48;
                deterrence(c, 3);
            }
            case "mobilise" -> {
                while (TaiwanSea.releaseNextSquadron(c)) { /* everything that can come, comes */ }
                for (int m = 0; m < s.prod[TaiwanData.P_US_GL].length; m++) s.prod[TaiwanData.P_US_GL][m] *= 1.3;
                deterrence(c, 4);
            }
            case "advisers" -> { for (Troop t : s.troops) if (t.nat.equals("TWN")) t.quality *= 1.08; s.will += 5; deterrence(c, 3); }
            default -> { }
        }
    }

    // =================================================================================================================
    //  Munitions production and flow (spec 3.4)
    // =================================================================================================================

    static void logistics(Ctx c) {
        TaiwanState s = c.s;
        for (int p = 0; p < s.pool.length; p++)
            for (int m = 0; m < s.pool[p].length; m++) {
                double per = s.prod[p][m] / 30.0;
                if (per <= 0) continue;
                int whole = (int) per;
                if (c.u(Ctx.R_PROD) < per - whole) whole++;
                s.pool[p][m] += whole;
            }
        if (s.blueMobilized >= 0 && s.hour - s.blueMobilized > 24 * 10) {
            double f = progDone(c, US, "munitions") ? 0.045 : 0.022;
            if (s.rung[PRC] >= 5 && s.guamStruck) f *= 0.8;
            for (int m = 0; m < s.pool[TaiwanData.P_US_GL].length; m++) {
                if (c.d.munitions.get(m).side() != US) continue;
                double gl = s.pool[TaiwanData.P_US_GL][m];
                int mv = (int) Math.floor(gl * f + 0.5);
                if (mv <= 0 && gl >= 1 && c.u(Ctx.R_PROD) < 0.3) mv = 1;
                mv = (int) Math.min(mv, Math.floor(gl));
                s.pool[TaiwanData.P_US_GL][m] -= mv;
                s.pool[TaiwanData.P_US_TH][m] += mv;
            }
        }
        if (country(c, "AUS").belligerent) s.pool[TaiwanData.P_AU][c.d.munition("JASSM_ER")] += TaiwanFire.take(c, TaiwanData.P_US_GL, c.d.munition("JASSM_ER"), 2);
    }

    // =================================================================================================================
    //  Taiwan (spec 6.6, 7.3, 7.4, 8.7)
    // =================================================================================================================

    static final double LNG_DAY = 57, COAL_DAY = 156, OIL_DAY = 0.81;

    static void mobilizeTaiwan(Ctx c, double level, String why) {
        TaiwanState s = c.s;
        if (s.mobilized >= level) return;
        s.mobilized = level;
        int n = 0;
        for (Troop t : s.troops) {
            if (!t.nat.equals("TWN") || t.state != GS_RESERVE) continue;
            if (level < 1 && n >= 10) break;
            if (t.mobilizeAt < 0) { t.mobilizeAt = s.hour + 60 + c.nextInt(Ctx.R_WORLD, level >= 1 ? 96 : 140); n++; }
        }
        c.log("Taiwan orders " + (level >= 1 ? "full" : "partial") + " mobilisation of its reserves (" + why + ").", true);
        s.gap[E_TWN] += level >= 1 ? 0.01 : 0.004;
        s.nationalism += 1;
    }

    static void onWarTaiwan(Ctx c) {
        TaiwanState s = c.s;
        mobilizeTaiwan(c, 1, "attack");
        if (progDone(c, PRC, "influence")) s.will -= 4;
        if (s.taiwanDelay) s.c2 = Math.max(0.4, s.c2 - 0.12);
        if (s.plan == 0 || s.plan == 4) s.will += 5;
        for (Battery b : s.batteries) if (b.nat.equals("TWN")) b.dispersal = s.twDisperseAsked ? 0.85 : 0.75;
    }

    static void taiwan(Ctx c) {
        TaiwanState s = c.s;
        double arr = TMath.clamp(s.arrivalsToday / TaiwanSea.NORMAL_SHIPS, 0, 1.15);
        s.arrivalsToday = 0;
        s.arrivalsAvg = s.arrivalsAvg * 0.7 + arr * 0.3;
        s.imports = TMath.clamp(s.arrivalsAvg, 0, 1.1);
        double lngDays = s.lng / LNG_DAY;
        if (s.blockade >= 2 || s.atWar()) {
            if (lngDays < 5 || s.coal < 2500) s.ration = 2;
            else if (s.ration < 1) s.ration = 1;
            s.gensets = true;
        }
        if (s.twRationAsked && s.ration < 1) s.ration = 1;
        double cons = s.ration == 2 ? 0.75 : s.ration == 1 ? 0.9 : 1.0;
        s.lng = Math.min(900, s.lng + LNG_DAY * arr * (1 - s.terminalDmg) * (s.convoys ? 1.1 : 1));
        s.coal = Math.min(9000, s.coal + COAL_DAY * arr);
        s.oil = Math.min(140, s.oil + OIL_DAY * arr * 1.1);
        double gasUse = Math.min(s.lng, LNG_DAY * cons), coalUse = Math.min(s.coal, COAL_DAY * cons), oilUse = Math.min(s.oil, OIL_DAY * cons);
        s.lng -= gasUse; s.coal -= coalUse; s.oil -= oilUse;
        double gas = 0.45 * gasUse / (LNG_DAY * cons) * (1 - s.terminalDmg * 0.6);
        double coal = 0.37 * coalUse / (COAL_DAY * cons);
        double oil = (s.gensets ? 0.06 : 0.05) * Math.min(1, s.oil / 20 + 0.3);
        double grid = Math.min(1, s.c2 + 0.25);
        s.elec = TMath.clamp((gas + coal + 0.13 + oil) * grid / cons, 0, 1);
        s.terminalDmg = Math.max(0, s.terminalDmg - 0.008);
        if (s.mobilized > 0) s.reservesReady = Math.min(1, s.reservesReady + 0.03 * s.mobilized);
        s.c2 = Math.min(1, s.c2 + 0.01);
        if (s.cablesCut > 0 && s.blockade < 3 && c.chance(Ctx.R_WORLD, 0.08)) s.cablesCut--;
        double fight = 0;
        for (int i = 0; i < s.sectors.size(); i++) if (s.sectors.get(i).contested) fight += c.d.sectors.get(i).pop();
        SectorState hc = s.sectors.get(c.d.sector("s_hsinchu")), ty = s.sectors.get(c.d.sector("s_taoyuan"));
        double north = hc.ctrl + ty.ctrl + hc.civDamage;
        double strikes = s.atWar() && s.rung[PRC] >= 4 ? 0.15 : 0;
        s.chips = TMath.clamp(TMath.pow(s.elec, 2.2) * (1 - strikes) * (1 - Math.min(0.9, north)) * (1 - 0.03 * fight) * (1 - 0.2 * s.cablesCut / 24.0), 0, 1);
        s.industry = TMath.clamp(s.elec * (0.6 + 0.4 * s.imports) * (1 - 0.04 * fight), 0, 1);
        will(c);
    }

    static void will(Ctx c) {
        TaiwanState s = c.s;
        double target = 62 + (s.unity - 0.6) * 20;
        if (s.usCombat) target += 12;
        if (country(c, "JPN").belligerent) target += 4;
        boolean existential = s.rung[PRC] >= 4 || s.blockade >= 3 || s.plan == 0;
        if (s.atWar() && existential && !s.usCombat && s.warHours() > 72) target -= Math.min(22, (s.warHours() - 72) / 24.0 * 1.2);
        target -= (1 - s.elec) * 30;
        target -= (1 - s.imports) * 8;
        target -= TaiwanLand.heldKm2(s) / 36000.0 * 70;
        if (s.sectors.get(c.d.sector("s_taipei")).ctrl > 0.6) target -= 20;
        double killed = s.civKilled + c.loss("TWN").killed;
        double newly = Math.max(0, killed - s.lastKilled);
        s.lastKilled = killed;            // kept in the state (the source's static counter was lost on loading, spec 13.1)
        target -= Math.min(15, TMath.ln(1 + killed / 400.0) * 4);
        if (amphibLostFrac(c) > 0.5 && s.plan == 0) target += 6;
        target -= s.alert[PRC] * 4;
        s.will = TMath.clamp(s.will + (target - s.will) * 0.12 - newly / 2000.0, 0, 100);
    }

    static void energyHit(Ctx c, int leak) {
        c.s.terminalDmg = Math.min(0.9, c.s.terminalDmg + 0.05 * leak);
        c.s.lng = Math.max(0, c.s.lng - 18 * leak);
    }

    // =================================================================================================================
    //  The economy (spec 7.5), seven economies
    // =================================================================================================================

    static double straitChannel(Ctx c) {
        TaiwanState s = c.s;
        double v = 0;
        if (s.blockade >= 1) v = 0.15;
        if (s.blockade >= 2) v = 0.45;
        if (s.blockade >= 3) v = 0.6;
        if (s.atWar() && (s.rung[PRC] >= 3 || s.rung[US] >= 2)) v = Math.max(v, 0.75);
        if (s.rung[PRC] >= 5) v = 1.0;
        if (s.phase.equals("crisis")) v = Math.max(v, s.warning[US] * 0.08 + (progOn(c, PRC, "exercise") ? 0.03 : 0));
        if (s.distantBlockade) v = Math.min(1, v + 0.15);
        return v;
    }

    static double sanctionChannel(Ctx c) {
        TaiwanState s = c.s;
        double v = 0.5 * s.sanctUS / 3.0;
        if (country(c, "EU").sanctions) v += 0.15;
        if (country(c, "JPN").sanctions) v += 0.08;
        if (country(c, "GBR").sanctions) v += 0.03;
        if (country(c, "AUS").sanctions) v += 0.03;
        if (s.swift) v += 0.12;
        if (s.freeze) v += 0.08;
        if (s.chipEmbargo) v += 0.05;
        v += s.sanctCN / 3.0 * 0.2;
        if (s.rareEarths) v += 0.08;
        if (s.boycott) v += 0.04;
        if (s.treasury) v += 0.04;
        if (s.tourism) v += 0.01;
        return TMath.clamp(v, 0, 1.1);
    }

    static double warChannel(Ctx c, int e) {
        TaiwanState s = c.s;
        if (!s.atWar()) return 0;
        switch (e) {
            case E_TWN: {
                double v = s.rung[PRC] >= 4 ? 0.45 : s.blockade >= 3 ? 0.15 : 0.05, civ = 0;
                for (int i = 0; i < s.sectors.size(); i++) civ += s.sectors.get(i).civDamage * c.d.sectors.get(i).pop();
                v += Math.min(0.55, civ / 10.0) + TaiwanLand.heldKm2(s) / 36000.0;
                return TMath.clamp(v, 0, 1.2);
            }
            case E_CHN: return TMath.clamp((s.rung[PRC] >= 3 ? 0.4 : 0.1) + s.mainlandHits / 1500.0, 0, 1.2);
            case E_USA: return s.usCombat ? 0.8 + (s.homelandHits > 0 ? 0.3 : 0) : 0.1;
            case E_JPN: return country(c, "JPN").belligerent || s.japanStruck ? 1 : s.usCombat ? 0.35 : 0.1;
            case E_KOR: return country(c, "KOR").belligerent ? 1 : 0.15;
            case E_WORLD: return s.usCombat ? 1 : 0.4;
            default: return 0;
        }
    }

    static void economy(Ctx c) {
        TaiwanState s = c.s;
        s.chipLag += (s.chips - s.chipLag) * 0.06;     // kept in the state (the source's static was lost on loading, spec 13.1)
        s.cS = straitChannel(c); s.cC = 1 - s.chipLag; s.cX = sanctionChannel(c);
        // prices move a quarter of the way each day, without the source's daily noise (spec 11.4)
        double brent = 92 + s.cS * 48 + (s.distantBlockade ? 22 : 0) + (s.rung[PRC] >= 5 ? 12 : 0) + s.nukeRisk * 0.15;
        s.brent += (brent - s.brent) * 0.25;
        double lng = 15.5 + s.cS * 24 + (s.blockade >= 2 ? 6 : 0);
        s.lngPrice += (lng - s.lngPrice) * 0.25;
        s.cE = TMath.clamp((s.brent - 92) / 150 + (s.lngPrice - 15.5) / 60, 0, 1);
        s.blockadeChina = s.distantBlockade ? TMath.clamp(0.25 + 0.5 * country(c, "ASEAN").stance / 100.0 + (country(c, "IND").belligerent ? 0.15 : 0), 0.15, 0.8) : 0;
        for (int k = 0; k < 7; k++) {
            double[] w = c.d.economies.get(k).w();
            double sx = s.cX;
            if (k == E_CHN) {
                if (progDone(c, PRC, "economy")) sx *= 0.8;
                if (progDone(c, PRC, "recall")) sx *= 0.9;
                if (country(c, "RUS").stance < -70) sx *= 0.92;
            }
            double t = w[0] * s.cS + w[1] * s.cC + w[2] * sx + w[3] * warChannel(c, k) + w[4] * s.cE;
            if (k == E_CHN) {
                t += 3.0 * s.blockadeChina;
                for (int i = 0; i < s.progOn[PRC].length; i++) if (s.progOn[PRC][i] && s.progPct[PRC][i] < 100) t += 0.04;
                if (prog(c, PRC, "roro") > 0) t += 0.15;
            }
            if (k == E_USA && progDone(c, US, "mobilise")) t -= 0.4;
            if (k == E_TWN) t += (1 - s.elec) * 12 + (1 - s.imports) * 4 + s.mobilized * 1.2;
            double target = t / 100.0;
            s.gap[k] += (target - s.gap[k]) / (target > s.gap[k] ? 18 : 40);
        }
        s.gap[E_WORLD] = Math.max(0, s.gap[E_WORLD]);
        double risk = s.warning[US] * 0.05 + s.nukeRisk / 100.0 * 0.25 + (s.atWar() ? 0.06 : 0);
        double fair = 1 - 2.4 * s.gap[E_USA] - risk;
        s.usEquity += (fair - s.usEquity) * 0.3;
        s.warRisk = TMath.clamp(0.05 + s.cS * 1.2 + (s.blockade >= 3 ? 1 : 0), 0.05, 5);
        s.econDays++;
        s.usGapSum += s.gap[E_USA];
        s.brentSum += s.brent;
        s.chipShockMax = Math.max(s.chipShockMax, 1 - s.chipLag);
        s.equityLow = Math.min(s.equityLow, s.usEquity);
    }

    /** A US economic measure (spec 7.6). Returns false if already in force. */
    static boolean usMeasure(Ctx c, String id) {
        TaiwanState s = c.s;
        switch (id) {
            case "sanctions1" -> { if (s.sanctUS >= 1) return false; s.sanctUS = 1; }
            case "sanctions2" -> { if (s.sanctUS >= 2) return false; s.sanctUS = 2; }
            case "sanctions3" -> { if (s.sanctUS >= 3) return false; s.sanctUS = 3; s.fullEmbargo = true; }
            case "swift" -> { if (s.swift) return false; s.swift = true; }
            case "freeze" -> { if (s.freeze) return false; s.freeze = true; }
            case "chips" -> { if (s.chipEmbargo) return false; s.chipEmbargo = true; }
            case "distant" -> {
                if (s.distantBlockade) return false;
                if (s.rung[US] < 3) throw new IllegalArgumentException("A distant blockade needs authority for combat at sea anywhere (rung 3)");
                s.distantBlockade = true;
                country(c, "ASEAN").stance -= 8;
                country(c, "IND").stance -= 2;
            }
            default -> throw new IllegalArgumentException("No such measure: " + id);
        }
        String[] names = {"sanctions1", "targeted sanctions", "sanctions2", "broad sanctions and export bans", "sanctions3", "a full embargo", "swift",
                "cutting China's major banks from SWIFT", "freeze", "freezing China's central-bank assets", "chips", "a total chip and tooling embargo",
                "distant", "a distant blockade of Chinese shipping"};
        String name = id;
        for (int i = 0; i < names.length; i += 2) if (names[i].equals(id)) name = names[i + 1];
        c.log("The United States imposes " + name + ".", true);
        c.news(News.Kind.WORLD, "Washington announces " + name + " against China", null, 2.5);
        s.nationalism = Math.min(100, s.nationalism + 1.5);
        if (!id.startsWith("sanctions")) country(c, "EU").stance += 1;
        if (s.phase.equals("crisis")) { deterrence(c, id.equals("sanctions1") ? 1 : 2); s.prcStability -= 0.5; }
        return true;
    }

    /** A Chinese counter-measure (spec 7.6). */
    static boolean prcMeasure(Ctx c, String id) {
        TaiwanState s = c.s;
        switch (id) {
            case "rareearths" -> {
                if (s.rareEarths) return false;
                s.rareEarths = true;
                for (int i = 0; i < s.countries.size(); i++) if (!c.d.countries.get(i).aligned()) s.countries.get(i).fear = Math.min(1, s.countries.get(i).fear + 0.03);
            }
            case "boycott" -> { if (s.boycott) return false; s.boycott = true; country(c, "JPN").stance += 2; }
            case "treasury" -> { if (s.treasury) return false; s.treasury = true; s.gap[E_CHN] += 0.004; }
            case "tourism" -> { if (s.tourism) return false; s.tourism = true; }
            case "countersanctions" -> { if (s.sanctCN >= 1) return false; s.sanctCN = 1; }
            case "seize" -> { if (s.sanctCN >= 3) return false; s.sanctCN = 3; country(c, "EU").stance += 6; country(c, "ASEAN").stance += 3; }
            default -> throw new IllegalArgumentException("No such measure: " + id);
        }
        c.log("Beijing retaliates: " + id + ".", true);
        return true;
    }

    // =================================================================================================================
    //  Politics (spec 8.7)
    // =================================================================================================================

    static void politics(Ctx c) {
        TaiwanState s = c.s;
        double kia = c.loss("USA").killed;
        double provoked = s.provoked ? 0.6 : 1.0;
        double anchor = 46 + (s.provoked ? 14 : 0) + (s.guamStruck ? 4 : 0);
        anchor -= 6 * provoked * TMath.ln(1 + kia / 500.0);
        anchor -= Math.max(0, s.brent - 92) * 0.07 + Math.max(0, 1 - s.usEquity) * 25;
        if (s.blueFirst) anchor -= 8;
        if (s.atWar() && s.usCombat) {
            double w = (s.hour - s.usCombatHour) / 24.0;
            s.weariness = Math.max(0, (w - 20) * 0.3 * provoked);
            anchor -= s.weariness;
            if (amphibLostFrac(c) > 0.4) anchor += 4;
            anchor -= TaiwanLand.heldKm2(s) / 36000.0 * 15;
        }
        if (s.nukeRisk > 50) anchor -= (s.nukeRisk - 50) * 0.15;
        if (progDone(c, US, "declassify")) anchor += 3;
        anchor += s.rally;
        s.rally *= 0.97;
        s.usSupport = TMath.clamp(s.usSupport + (anchor - s.usSupport) * 0.1, 5, 95);
        double aAnchor = 43 + (s.usSupport - 46) * 0.4 - Math.max(0, s.brent - 92) * 0.08 - s.gap[E_USA] * 100 * 0.8 + s.rally * 0.5;
        s.approval = TMath.clamp(s.approval + (aAnchor - s.approval) * 0.08, 15, 85);
        double cAnchor = 52 + (s.usSupport - 46) * 0.9;
        s.congress = TMath.clamp(s.congress + (cAnchor - s.congress) * 0.1, 5, 95);
        if (!s.aumf && s.usCombat && s.congress > 58 && s.warHours() > 48) {
            s.aumf = true;
            c.alert("Congress passes an authorization for the use of force over Taiwan.");
            c.news(News.Kind.WORLD, "CONGRESS AUTHORIZES WAR WITH CHINA", null, 4);
        }
        double pain = s.gap[E_CHN] * 100;
        double sAnchor = 72 - pain * 1.4 + (s.nationalism - 62) * 0.25 - TMath.ln(1 + c.loss("CHN").killed / 2000.0) * 3.5;
        if (s.atWar()) {
            if (s.plan == 0) sAnchor += TaiwanLand.heldKm2(s) / 36000.0 * 30 - amphibLostFrac(c) * 12;
            if (s.plan == 1 || s.plan == 2) sAnchor += (1 - s.elec) * 8 - (s.throughput > 0.6 && s.warHours() > 24 * 14 ? 6 : 0);
            if (s.plan == 1 || s.plan == 2) sAnchor -= coalition(c);
        }
        if (s.mainlandHits > 0) sAnchor -= Math.min(8, s.mainlandHits / 150.0);
        s.prcStability = TMath.clamp(s.prcStability + (sAnchor - s.prcStability) * 0.08, 0, 100);
        s.nationalism = TMath.clamp(s.nationalism + (62 - s.nationalism) * 0.02, 0, 100);
    }

    // =================================================================================================================
    //  The allies (spec 8.1-8.3)
    // =================================================================================================================

    static void diplomacy(Ctx c) {
        TaiwanState s = c.s;
        int red = s.rung[PRC];
        double cred = s.usCombat ? 8 : s.atWar() && red >= 3 && s.warHours() > 72 ? -12 : 0;
        if (s.usCombat && amphibLostFrac(c) > 0.4) cred += 5;
        if (c.loss("USA").carriers >= 2) cred -= 4;
        for (int i = 0; i < s.countries.size(); i++) {
            Country k = s.countries.get(i);
            TaiwanData.Country d = c.d.countries.get(i);
            double anchor;
            if (d.aligned()) {
                anchor = d.stance0() - (s.atWar() ? 10 : 0) - (s.rung[US] >= 4 ? 8 : 0) + k.momentum;
                if (s.distantBlockade) anchor -= 5;
            } else {
                anchor = d.stance0() + k.momentum;
                anchor += red * (2 + 7 * k.fear) * (d.id().equals("JPN") || d.id().equals("AUS") || d.id().equals("PHL") ? 1.2 : 0.7);
                anchor += cred * k.trust;
                if (k.attacked) anchor += 45;
                anchor -= k.pressure * d.exposure() * 10;
                anchor -= gapOf(s, d.id()) * 100 * 0.6 * d.exposure();
                if (s.blueFirst) anchor -= 10;
                if (s.rung[US] >= 5) anchor -= 6 * (1 - k.trust);
                if (s.nukeRisk > 60) anchor -= (s.nukeRisk - 60) * 0.2;
                if (d.id().equals("KOR") && country(c, "PRK").belligerent) anchor -= 15;
                if (d.id().equals("PHL") && progDone(c, US, "littoral")) anchor += 3;
            }
            k.stance = TMath.clamp(k.stance + (anchor - k.stance) * 0.08, -100, 100);
            k.momentum *= 0.95;
            k.pressure *= 0.96;
            if (!d.aligned()) {
                if (k.level() >= 4) k.overflight = true;
                if (k.level() <= 2 && k.basing && !k.attacked && !d.id().equals("AUS")) {
                    k.basing = false;
                    c.alert(d.name() + " withdraws permission for US combat operations from its territory.");
                }
                if (k.stance >= 94 && s.usCombat && !k.forcesSent && (d.id().equals("JPN") && s.japanEntry >= 1 || d.id().equals("AUS") || d.id().equals("GBR")))
                    belligerent(c, i, "the public and parliament back intervention");
            } else {
                if (d.id().equals("RUS") && k.stance < -85 && !k.belligerent && s.rung[US] >= 5) belligerent(c, i, "US strikes deep in China");
                if (d.id().equals("PRK") && k.stance < -80 && !k.belligerent && s.atWar() && c.chance(Ctx.R_DIPLO, 0.05)) belligerent(c, i, "artillery barrages across the DMZ");
            }
            // the presidency's relations follow, at half the stance's movement
            double delta = k.stance - k.reported;
            if (!d.aligned() && Math.abs(delta) >= 2) {
                c.effect(new Effect.WorldShift("rel:" + d.ags(), Math.floor(delta * 0.5 + 0.5)));
                k.reported = k.stance;
            }
        }
        Country au = country(c, "AUS");
        if (s.usCombat && !au.belligerent && au.stance > 80 && s.warHours() > 48) belligerent(c, c.d.country("AUS"), "ANZUS consultations");
    }

    static double gapOf(TaiwanState s, String id) {
        return switch (id) { case "JPN" -> s.gap[E_JPN]; case "KOR" -> s.gap[E_KOR]; case "EU", "GBR", "FRA" -> s.gap[E_EU]; default -> s.gap[E_WORLD]; };
    }

    static void belligerent(Ctx c, int ct, String why) {
        TaiwanState s = c.s;
        Country k = s.countries.get(ct);
        TaiwanData.Country d = c.d.countries.get(ct);
        if (k.belligerent) return;
        if (d.id().equals("JPN") && s.japanEntry == 0 && !k.attacked) return;
        k.belligerent = true;
        k.forcesSent = true;
        if (d.aligned()) {
            k.stance = Math.min(k.stance, -85);
            c.alert(d.name() + " enters the conflict on China's side (" + why + ").");
            c.news(News.Kind.WORLD, d.name().toUpperCase(Locale.ROOT) + " JOINS CHINA", null, 4);
            if (d.id().equals("PRK")) country(c, "KOR").stance -= 10;
            return;
        }
        k.stance = Math.max(k.stance, 85);
        k.basing = true;
        k.overflight = true;
        TaiwanSea.releaseAllies(c, d.id(), true);
        c.alert(d.name() + " enters the war alongside the United States (" + why + ").");
        c.news(News.Kind.WORLD, d.name().toUpperCase(Locale.ROOT) + " ENTERS THE WAR", null, 4);
        s.will += 3;
        if (d.id().equals("JPN")) s.usSupport += 3;
    }

    /** One diplomatic action (spec 8.2). Throws when it cannot be taken; returns what happened. */
    static String act(Ctx c, int side, String countryId, String action) {
        TaiwanState s = c.s;
        int ct = c.d.country(countryId);
        Country k = s.countries.get(ct);
        TaiwanData.Country d = c.d.countries.get(ct);
        double cost = side == US ? switch (action) {
            case "consult" -> 3; case "access" -> 6; case "basing" -> 10; case "combat" -> 14; case "assurance" -> 8; case "sanctions" -> 6;
            case "airports" -> 8; case "warn" -> 5; default -> throw new IllegalArgumentException("No such action: " + action);
        } : switch (action) {
            case "reassure" -> 5; case "coerce" -> 6; case "threaten" -> 6; case "support" -> 10; case "neutrality" -> 8; case "influence" -> 5;
            default -> throw new IllegalArgumentException("No such action: " + action);
        };
        if (s.dipCap[side] < cost) throw new IllegalArgumentException("Not enough diplomatic capital (" + (int) s.dipCap[side] + " of " + (int) cost + ")");
        if ((side == US ? k.lastUS : k.lastPRC) == s.day()) throw new IllegalArgumentException(d.name() + " has already been approached today");
        if (side == US && action.equals("airports") && !d.id().equals("JPN")) throw new IllegalArgumentException("Only Japan's civil airports can be opened");
        if (side == US && action.equals("warn") && !d.aligned()) throw new IllegalArgumentException("Only Russia or North Korea can be warned");
        if (side == PRC && action.equals("support") && !d.aligned()) throw new IllegalArgumentException("Only Russia or North Korea can be asked for support");
        if (side == US && action.equals("combat") && !s.usCombat) throw new IllegalArgumentException("Allies will not consider combat until America itself is fighting");
        if (side == US && action.equals("basing") && k.basing) throw new IllegalArgumentException(d.name() + " already allows combat operations from its bases");
        if (side == US && action.equals("combat") && k.belligerent) throw new IllegalArgumentException(d.name() + " is already fighting");
        s.dipCap[side] -= cost;
        if (side == US) k.lastUS = s.day(); else k.lastPRC = s.day();
        String res;
        if (side == US) {
            switch (action) {
                case "consult" -> { k.momentum += 3 + 3 * k.trust; res = d.name() + " welcomes closer consultation."; }
                case "access" -> {
                    if (k.level() >= 3 || c.u(Ctx.R_DIPLO) < 0.3 + k.trust * 0.4) { k.overflight = true; k.momentum += 2; res = d.name() + " grants access and overflight."; }
                    else { k.momentum -= 2; res = d.name() + " declines for now."; }
                }
                case "basing" -> {
                    if (k.level() >= 4 && c.u(Ctx.R_DIPLO) < 0.4 + (k.stance - 40) / 60.0 + (k.attacked ? 0.5 : 0)) {
                        k.basing = true; res = d.name() + " agrees to US combat operations from its territory.";
                        c.alert(d.name() + " allows US combat operations from its territory.");
                    } else { k.momentum -= 3; k.pressure += 0.1; res = d.name() + " refuses: its government fears becoming a target."; }
                }
                case "combat" -> {
                    if (k.level() >= 5 && c.u(Ctx.R_DIPLO) < (k.stance - 55) / 50.0 + (k.attacked ? 0.6 : 0)) { belligerent(c, ct, "a US request"); res = d.name() + " agrees to join the fight."; }
                    else { k.momentum -= 2; res = d.name() + " will support but not fight, yet."; }
                }
                case "assurance" -> { k.momentum += 4 + 6 * d.exposure(); k.pressure = Math.max(0, k.pressure - 0.3); res = d.name() + " is reassured by American economic guarantees."; }
                case "sanctions" -> {
                    if (k.sanctions) res = d.name() + " is already sanctioning China.";
                    else if (k.level() >= 3 && c.u(Ctx.R_DIPLO) < 0.35 + (k.stance - 15) / 70.0 - d.exposure() * 0.3 + (s.atWar() ? 0.25 : 0)) {
                        k.sanctions = true; res = d.name() + " joins sanctions on China."; c.log(res, true);
                    } else res = d.name() + " will not impose sanctions at this stage.";
                }
                case "airports" -> {
                    if (k.civilAirports) res = "Japan's civil airports are already open to US aircraft.";
                    else if (k.level() >= 5 && c.u(Ctx.R_DIPLO) < 0.5 + (k.attacked ? 0.4 : 0)) { k.civilAirports = true; res = "Japan opens its civil airports to US aircraft."; }
                    else res = "Tokyo hesitates to put civilian airports at risk.";
                }
                default -> { k.momentum += 6; res = d.name() + " takes note of American warnings."; }
            }
        } else {
            switch (action) {
                case "reassure" -> {
                    if (d.aligned()) { k.momentum -= 6; res = d.name() + " deepens its ties with Beijing."; }
                    else { k.momentum -= 2 + 4 * d.exposure() * (1 - k.trust); k.pressure = Math.max(0, k.pressure - 0.1); res = d.name() + " responds cautiously to Chinese offers."; }
                }
                case "coerce" -> {
                    if (d.aligned()) res = "No effect on partners.";
                    else {
                        k.pressure += 0.35 * d.exposure();
                        if (k.trust > 0.7 && c.u(Ctx.R_DIPLO) < 0.5) { k.momentum += 3; res = "Coercion backfires: " + d.name() + " moves closer to Washington."; }
                        else { k.momentum -= 3; res = d.name() + " feels the economic pressure."; }
                    }
                }
                case "threaten" -> {
                    k.fear = TMath.clamp(k.fear + 0.06, 0, 1);
                    if (k.fear > 0.6 && d.exposure() > 0.5 && c.u(Ctx.R_DIPLO) < 0.6) { k.momentum -= 6; res = d.name() + " signals it will stay out."; }
                    else { k.momentum += 4; res = "Threats harden opinion in " + d.name() + "."; }
                }
                case "support" -> {
                    k.momentum -= 12;
                    if (d.id().equals("RUS") && k.stance < -70) { s.satHealth[PRC] = Math.min(1.3, s.satHealth[PRC] + 0.05); res = "Moscow promises energy, satellite data and diplomatic cover."; }
                    else if (d.id().equals("PRK") && k.stance < -70 && s.atWar()) { belligerent(c, ct, "a Chinese request"); res = "Pyongyang stages provocations that pin down South Korea."; }
                    else res = d.name() + " expresses solidarity.";
                }
                case "neutrality" -> {
                    if (d.aligned() || k.belligerent) res = "Not applicable.";
                    else if (c.u(Ctx.R_DIPLO) < 0.25 + d.exposure() * 0.4 - k.trust * 0.3 + k.fear * 0.2) {
                        k.momentum -= 15; k.basing = k.basing && k.attacked; res = d.name() + " declares neutrality in any Taiwan conflict.";
                        c.log(res, true);
                    } else res = d.name() + " rejects Beijing's neutrality proposal.";
                }
                default -> { k.momentum -= 2; k.pressure += 0.1; res = "Influence operations are under way in " + d.name() + "."; }
            }
        }
        c.log((side == US ? "US" : "PRC") + " diplomacy (" + d.name() + "): " + res);
        return res;
    }

    // =================================================================================================================
    //  The war's turning points (spec 8.4, 8.5)
    // =================================================================================================================

    static void startWar(Ctx c, int side) {
        TaiwanState s = c.s;
        if (!s.phase.equals("crisis")) return;
        s.phase = "war";
        s.dHour = s.hour;
        s.launchHour = -1;
        s.blueStrikeHour = -1;
        c.effect(new Effect.Tension(HotSpot.TAIWAN_STRAIT, 15));
        c.effect(new Effect.WorldShift("rel:CN", -20));
        if (side == PRC) {
            if (s.plan < 0) s.plan = 4;
            switch (s.plan) {
                case 2 -> { s.blockade = Math.max(s.blockade, 1); s.rung[PRC] = Math.max(s.rung[PRC], 1); }
                case 1 -> { s.blockade = Math.max(s.blockade, 2 + TMath.clampi(s.lethality, 0, 2)); s.rung[PRC] = Math.max(s.rung[PRC], s.blockade >= 4 ? 3 : 2); }
                case 3 -> { s.rung[PRC] = Math.max(s.rung[PRC], 3); s.blockade = Math.max(s.blockade, 1); }
                case 4 -> { s.rung[PRC] = Math.max(s.rung[PRC], 4); s.blockade = Math.max(s.blockade, 2); }
                default -> { s.rung[PRC] = Math.max(s.rung[PRC], 4); s.blockade = Math.max(s.blockade, 4); }
            }
            if (usWillNotFight(s)) { s.optStrikeUS = false; s.optStrikeJapan = false; }   // Beijing keeps America out of it
            if (s.optStrikeUS && s.plan != 2) s.rung[PRC] = Math.max(s.rung[PRC], 5);
            String h = switch (s.plan) {
                case 2 -> "China's coast guard begins a customs quarantine of Taiwan";
                case 1 -> "The PLA declares a blockade of Taiwan and closes the sea and air around it";
                case 3 -> "PLA forces move against Taiwan's outlying islands";
                case 4 -> "The PLA launches missile strikes across Taiwan";
                default -> "The PLA launches a massive assault on Taiwan";
            };
            c.alert("D-DAY. " + h + ".");
            c.news(News.Kind.WORLD, h.toUpperCase(Locale.ROOT), null, 6);
            cyberSpace(c);
            onWarTaiwan(c);
            TaiwanAI.hHour(c);
            TaiwanSea.mobilize(c, "PRC operations against Taiwan");
            if (s.rung[PRC] >= 5) usEnter(c, "PLA strikes on US forces", true);
            if (s.japanEntry == 2) belligerent(c, c.d.country("JPN"), "Tokyo declares a survival-threatening situation");
        } else {
            s.rung[US] = Math.max(s.rung[US], 4);
            if (s.mainland == 0) s.mainland = 1;
            s.blueFirst = true;
            s.usSupport -= 10;
            s.nationalism = Math.min(100, s.nationalism + 15);
            for (String k : new String[]{"JPN", "KOR", "AUS", "PHL", "IND", "EU", "ASEAN"}) country(c, k).stance -= 12;
            s.rung[PRC] = Math.max(s.rung[PRC], 5);
            if (s.plan < 0) s.plan = 4;
            s.blockade = Math.max(s.blockade, 2);
            c.alert("D-DAY. The United States launches pre-emptive strikes against PLA forces.");
            c.news(News.Kind.WORLD, "US STRIKES CHINESE FORCES MASSING OPPOSITE TAIWAN", null, 6);
            usEnter(c, "a pre-emptive strike", false);
            onWarTaiwan(c);
            TaiwanAI.hHour(c);
        }
    }

    static void usEnter(Ctx c, String why, boolean provoked) {
        TaiwanState s = c.s;
        if (s.usCombat) return;
        if (!provoked && !s.commitment.equals("INTERVENE")) return;
        s.usCombat = true;
        s.usCombatHour = s.hour;
        s.rung[US] = Math.max(s.rung[US], provoked && s.rung[PRC] >= 5 ? 3 : 2);
        if (provoked) {
            s.provoked = true;
            s.rally = Math.max(s.rally, 16);
            s.usSupport += 14;
            s.approval += 7;
            s.congress += 12;
            c.effect(new Effect.Rally(16));
            c.effect(new Effect.Approval(7));
            if (!s.commitment.equals("INTERVENE")) { s.commitment = "INTERVENE"; c.alert("Attacked, the United States fights whatever it meant to do."); }
        } else s.usSupport -= 3;
        TaiwanSea.mobilize(c, why);
        TaiwanSea.releaseBombers(c);
        c.alert("The United States enters the war (" + why + ").");
        c.news(News.Kind.WORLD, provoked ? "AMERICA AT WAR AFTER CHINESE ATTACK" : "THE PRESIDENT ORDERS US FORCES INTO COMBAT TO DEFEND TAIWAN", null, 6);
    }

    /** The cap the president's commitment puts on the US rung (spec 11.6). */
    static int rungCap(TaiwanState s) {
        return switch (s.commitment) { case "STAND_ASIDE" -> 0; case "ARM_AND_SANCTION" -> 1; default -> 6; };
    }

    static void setRung(Ctx c, int side, int rung) {
        TaiwanState s = c.s;
        rung = TMath.clampi(rung, 0, 6);
        if (side == US) rung = Math.min(rung, rungCap(s));
        int old = s.rung[side];
        if (rung == old) return;
        s.rung[side] = rung;
        if (side == PRC) {
            c.effect(new Effect.Tension(HotSpot.TAIWAN_STRAIT, 4 * (rung - old)));
            if (rung > old) {
                c.alert("Beijing escalates: " + RED_RUNG[rung].toLowerCase(Locale.ROOT) + ".");
                for (int i = 0; i < s.countries.size(); i++) if (!c.d.countries.get(i).aligned()) s.countries.get(i).stance += (rung - old) * 2.5 * (0.5 + s.countries.get(i).fear);
                if (rung >= 2 && s.blockade < 2 && s.plan != 2) s.blockade = 2;
                if (rung >= 3 && s.blockade >= 2 && s.lethality >= 1) s.blockade = Math.max(s.blockade, 3);
                if (rung >= 5) c.effect(new Effect.WorldShift("rel:CN", -10));
            } else c.log("Beijing steps back: the PLA is now limited to " + RED_RUNG[rung].toLowerCase(Locale.ROOT) + ".", true);
        } else {
            if (rung > old) {
                c.alert("Washington authorises " + BLUE_RUNG[rung].toLowerCase(Locale.ROOT) + ".");
                if (rung >= 2 && !s.usCombat && s.atWar()) usEnter(c, "a presidential decision", s.provoked);
                if (rung >= 5 && s.satHealth[PRC] > 0.7) { s.satHealth[PRC] -= 0.3; c.log("US counter-space operations blind part of China's reconnaissance satellites."); }
                if (rung >= 4) {
                    s.nationalism = Math.min(100, s.nationalism + 6);
                    for (String k : new String[]{"KOR", "IND", "ASEAN", "EU"}) country(c, k).stance -= 3 * (rung - 3);
                }
            } else c.log("Washington restricts operations to " + BLUE_RUNG[rung].toLowerCase(Locale.ROOT) + ".", true);
        }
    }

    static void cyberSpace(Ctx c) {
        TaiwanState s = c.s;
        if (!s.optCyber) return;
        double cy = prog(c, PRC, "cyber") / 100.0, sp = prog(c, PRC, "space") / 100.0;
        double hunt = progDone(c, US, "huntforward") ? 0.45 : 1;
        if (cy > 0.1) {
            s.c2 = Math.max(0.35, s.c2 - 0.3 * cy * hunt);
            s.cablesCut = Math.min(24, s.cablesCut + (int) Math.floor(8 + 8 * cy + 0.5));
            s.elec = Math.max(0.5, s.elec - 0.2 * cy * hunt);
            for (Squadron q : s.squadrons) if (q.side == US && q.hub >= 0 && q.hubArrive > s.hour) q.hubArrive += 24 * cy * hunt;
            for (Group g : s.groups) if (g.side == US && g.hub >= 0 && g.hubArrive > s.hour) g.hubArrive += 18 * cy * hunt;
            c.alert("Cyber attacks disrupt Taiwan's power grid and telecoms; undersea cables to Taiwan are cut.");
        }
        if (sp > 0.1) {
            s.satHealth[US] = Math.max(0.35, s.satHealth[US] - 0.35 * sp);
            s.dazzle[US] = 0.2 * sp;
            c.alert("US reconnaissance and communications satellites are jammed, dazzled and attacked.");
        }
    }

    static void onShipLost(Ctx c, Group g, ShipClass k) {
        TaiwanState s = c.s;
        if (g.side == US) {
            if (g.nat.equals("USA")) {
                if (!s.usCombat && s.atWar()) usEnter(c, "a US warship sunk", true);
                if (k.carrier()) {
                    s.usSupport += s.provoked ? 1 : -5;
                    s.approval -= 3;
                    c.effect(new Effect.Approval(-3));
                    c.news(News.Kind.BAD, "AN AMERICAN CARRIER IS SUNK WITH HEAVY LOSS OF LIFE", null, 6);
                    c.alert("An American carrier of " + g.name + " is sunk.");
                }
            } else if (g.nat.equals("JPN")) {
                Country jp = country(c, "JPN");
                jp.attacked = true;
                jp.stance += 30;
                if (s.japanEntry >= 1) belligerent(c, c.d.country("JPN"), "a Japanese warship sunk");
            } else if (!g.nat.equals("TWN")) {
                Country k2 = natCountry(c, g.nat);
                if (k2 != null) { k2.attacked = true; k2.stance += 30; belligerent(c, c.d.country(g.nat), "a " + g.nat + " warship sunk"); }
            }
        } else {
            if (k.carrier()) { s.prcStability -= 4; s.nationalism += 2; c.news(News.Kind.WORLD, "A CHINESE CARRIER IS SUNK", null, 4); c.alert("A Chinese carrier is sunk."); }
            else if (k.role().equals("amphib") || k.role().equals("lst") || k.role().equals("roro")) s.prcStability -= 0.25;
            else if (k.role().equals("cruiser") || k.role().equals("destroyer")) s.prcStability -= 0.5;
            if (k.role().equals("amphib") || k.role().equals("lst")) s.amphSunk++;
            if (g.nat.equals("RUS")) country(c, "RUS").stance -= 20;
        }
    }

    static final String[] US_BASES_IN_JAPAN = {"b_kadena_ab", "b_misawa_ab", "b_yokota_ab", "b_mcas_futenma", "b_mcas_iwakuni", "b_naf_atsugi",
            "b_yokosuka_naval_base", "b_sasebo_naval_base", "b_white_beach", "b_camp_hansen"};

    static void onBaseStruck(Ctx c, int bi, int attacker, int leak) {
        TaiwanState s = c.s;
        Base d = c.d.bases.get(bi);
        String cc = d.cc();
        if (attacker == PRC) {
            if (cc.equals("USA") || cc.equals("PLW")) {
                if (d.region().equals("Guam") || d.region().equals("Marianas")) {
                    if (!s.guamStruck) { c.news(News.Kind.WORLD, "CHINESE MISSILES STRIKE GUAM", null, 6); s.rally += 6; c.effect(new Effect.Rally(6)); }
                    s.guamStruck = true;
                    s.homelandHits += Math.max(0, leak);
                }
                if (!s.usCombat) usEnter(c, "PLA strikes on US territory", true);
            } else if (cc.equals("JPN")) {
                Country jp = country(c, "JPN");
                if (!s.japanStruck) { c.news(News.Kind.WORLD, "CHINESE MISSILES HIT BASES IN JAPAN", null, 5); jp.stance += 25; }
                s.japanStruck = true;
                jp.attacked = true;
                if (s.japanEntry >= 1) belligerent(c, c.d.country("JPN"), "an attack on Japanese territory");
                boolean usBase = false;
                for (String id : US_BASES_IN_JAPAN) if (d.id().equals(id)) usBase = true;
                if (!s.usCombat && usBase) usEnter(c, "PLA strikes on US bases in Japan", true);
            } else if (cc.equals("PHL") || cc.equals("KOR") || cc.equals("AUS")) {
                Country h = host(c, cc);
                if (h != null && !h.attacked) { h.attacked = true; h.stance += 35; belligerent(c, c.d.country(cc), "an attack on its territory"); }
                if (!s.usCombat) usEnter(c, "PLA strikes on US forces abroad", true);
            }
        } else if (cc.equals("CHN")) {
            s.mainlandHits += Math.max(0, leak);
            if (d.has(TaiwanData.F_C2) || d.has(TaiwanData.F_MISSILE)) s.c2Hits += Math.max(0, leak);
            s.nationalism = Math.min(100, s.nationalism + 0.05 * Math.max(0, leak));
        }
    }

    // =================================================================================================================
    //  Nuclear risk (spec 8.8)
    // =================================================================================================================

    static void nuclear(Ctx c) {
        TaiwanState s = c.s;
        double t = 3;
        if (s.atWar()) t += 4;
        t += Math.max(0, s.rung[US] - 3) * 9;
        if (s.rung[US] >= 6) t += 15;
        t += Math.min(22, s.mainlandHits * 0.05) + Math.min(18, s.c2Hits * 0.2);
        if (s.prcStability < 40) t += 40 - s.prcStability;
        t += (s.alert[0] + s.alert[1]) * 7;
        if (s.homelandHits > 0) t += 6 + Math.min(10, s.homelandHits * 0.3);
        if (s.rung[PRC] >= 6) t += 10;
        if (c.loss("USA").carriers >= 2) t += 5;
        if (s.plan == 0 && s.atWar() && s.warHours() > 24 * 5 && s.plaAshore() < 6 && amphibLostFrac(c) > 0.6) t += 8;
        if (TaiwanLand.heldKm2(s) > 15000) t += 4;
        s.nukeRisk = TMath.clamp(s.nukeRisk + (t - s.nukeRisk) * 0.3, 0, 100);
        if (s.nukeRisk > 70 && s.crisisTurns < 0) {
            s.crisisTurns = 3;
            s.crisisCount++;
            c.alert("NUCLEAR CRISIS: strategic forces on both sides move to higher alert.");
            c.news(News.Kind.WORLD, "THE WORLD ON EDGE AS NUCLEAR TENSIONS SPIKE", null, 6);
            c.effect(new Effect.PresidentStrain(-3, 8));
        } else if (s.crisisTurns >= 0) {
            s.crisisTurns--;
            if (s.nukeRisk < 55) { s.crisisTurns = -1; c.log("The nuclear crisis eases.", true); }
        }
    }

    static void nuclearAlert(Ctx c, int side, int level) {
        TaiwanState s = c.s;
        level = TMath.clampi(level, 0, 3);
        if (s.alert[side] == level) return;
        s.alert[side] = level;
        String who = side == PRC ? "China" : "The United States";
        String[] lv = {"returns its nuclear forces to normal readiness", "raises the readiness of its nuclear forces",
                "disperses its mobile missiles and surges its missile submarines", "issues an explicit nuclear threat"};
        c.alert(who + " " + lv[level] + ".");
        if (level >= 2) c.news(News.Kind.WORLD, who.toUpperCase(Locale.ROOT) + " RAISES ITS NUCLEAR ALERT", null, 5);
        for (int i = 0; i < s.countries.size(); i++) if (!c.d.countries.get(i).aligned()) s.countries.get(i).fear = TMath.clamp(s.countries.get(i).fear + (side == PRC ? 0.05 : 0.02) * level, 0, 1);
        if (side == PRC && level >= 2) s.usSupport -= 3 * (level - 1);
        if (side == US && level >= 2) s.nationalism += 3;
    }

    // =================================================================================================================
    //  How it ends (spec 8.9)
    // =================================================================================================================

    static void outcome(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        if (s.phase.equals("crisis")) {
            if (s.day() > 110) end(c, 0, "China did not attack before the summer typhoon season closed the window.");
            else if (s.standDownDay >= 0 && s.day() >= s.standDownDay + 7) end(c, 0, "Beijing stood down; the exercises around Taiwan are over.");
            return;
        }
        double wd = s.warDays();
        boolean squeezed = TaiwanLand.mainHeld(c) > 800 || s.elec < 0.55 || s.rung[PRC] >= 4;
        double held = TaiwanLand.heldKm2(s), ashore = s.plaAshore();
        if (s.will < 15 && squeezed) { end(c, 6, "Taiwan's government, its will to resist exhausted, accepts Beijing's terms."); return; }
        if (s.sectors.get(c.d.sector("s_taipei")).ctrl > 0.8 && held > 18000) { end(c, 5, "The PLA holds Taipei and most of Taiwan's west coast."); return; }
        if (s.prcStability < 12) { end(c, 9, "Facing unrest at home, Beijing halts operations and declares its objectives met."); return; }
        // a blockade that costs Beijing more at home each day: the leadership may blink before Taiwan does (docs/BALANCE_TAIWAN.md)
        if ((s.plan == 1 || s.plan == 2) && s.hour % 24 == 0 && s.prcStability < BLINK && c.u(Ctx.R_POLITICS) < (BLINK - s.prcStability) / BLINK_SCALE) {
            end(c, 9, "With the economy reeling under the world's sanctions, Beijing lifts the blockade and declares its point made.");
            return;
        }
        if (s.usSupport < 18 && s.usCombat) {
            if (intact(c)) end(c, 8, "With public support collapsing, Washington accepts a ceasefire on current lines.");
            else end(c, 10, "With public support collapsing, Washington accepts a ceasefire that leaves Beijing's gains in place.");
            return;
        }
        if (s.plan == 0 && wd > 6) {
            double lift = 1 - amphibLostFrac(c);
            if (ashore < 1.5 && (lift < 0.25 || wd > 20)) { end(c, 1, "The invasion has been defeated; no PLA forces remain on Taiwan."); return; }
            if (ashore > 0 && lift < 0.12 && wd > 21 && ashore < 25) { end(c, 1, "Cut off from resupply, the PLA lodgement collapses."); return; }
        }
        if ((s.plan == 1 || s.plan == 2) && wd > 21 && s.throughput > 0.7 && s.elec > 0.85 && avgThroughput(s, 10) > 0.65) {
            end(c, 1, "Convoys and escorts have broken the blockade; Taiwan is supplied.");
            return;
        }
        if (s.plan == 3 && wd > 14 && s.rung[PRC] <= 3) {
            int taken = 0;
            for (String id : new String[]{"s_kinmen", "s_matsu", "s_penghu"}) if (s.sectors.get(c.d.sector(id)).ctrl > 0.8) taken++;
            if (taken >= 2 && !s.usCombat && wd > 30) { end(c, 4, "China has seized Taiwan's outlying islands; the main island remains free."); return; }
        }
        if (wd >= 75) {
            if ((s.plan == 1 || s.plan == 2 || s.blockade >= 2) && s.elec < 0.45 && avgThroughput(s, 10) < 0.5 && held < 12000)
                end(c, 6, "Eleven weeks of blockade have left Taiwan dark and short of fuel; Taipei accepts Beijing's terms.");
            else end(c, judge(c), "After eleven weeks the fighting settles into a ceasefire on current lines.");
        }
    }

    /**
     * The world's economic front against a blockade (docs/BALANCE_TAIWAN.md): America's measures and the partners that have
     * joined its sanctions, as a weight on Beijing's stability. With enough of the world behind it, a blockade costs China
     * more at home than it can bear, and Beijing lifts it: the way diplomacy without force can still save Taiwan.
     */
    static double coalition(Ctx c) {
        TaiwanState s = c.s;
        double p = s.sanctUS * 4 + (s.swift ? 5 : 0) + (s.freeze ? 3 : 0) + (s.chipEmbargo ? 4 : 0) + (s.fullEmbargo ? 3 : 0);
        for (String k : COALITION) if (country(c, k).sanctions) p += k.equals("EU") ? 7 : k.equals("JPN") ? 5 : 3;
        return Math.min(COALITION_CAP, p * COALITION_WEIGHT);
    }

    static final String[] COALITION = {"JPN", "AUS", "KOR", "GBR", "EU", "PHL", "IND"};
    /** Below this stability, Beijing may lift a blockade any day. */
    static final double BLINK = 40;
    /** The daily chance of lifting a blockade is (BLINK - stability) / BLINK_SCALE. */
    static final double BLINK_SCALE = 500;
    /** The coalition's weight on Beijing's stability, and its most (tuned in docs/BALANCE_TAIWAN.md). */
    static final double COALITION_WEIGHT = 0.6, COALITION_CAP = 28;

    /** Taiwan whole and free: almost no ground held by the PLA, and no blockade still choking it (strike damage alone is not
     *  Beijing's gain to keep). */
    static boolean intact(Ctx c) {
        TaiwanState s = c.s;
        return TaiwanLand.heldKm2(s) < 200 && (s.blockade < 2 || avgThroughput(s, 10) > 0.5);
    }

    /** Washington has declared it will not fight: the president holds the rules of engagement at "diplomatic only". */
    static boolean usWillNotFight(TaiwanState s) {
        return s.rung[US] == 0 && s.held.contains("rung") && s.playerSide == US;
    }

    static double avgThroughput(TaiwanState s, int days) {
        double v = 0;
        int n = 0;
        for (int i = s.history.size() - 1; i >= 0 && n < days; i--, n++) v += s.history.get(i)[11];
        return n == 0 ? 0 : v / n;
    }

    static int judge(Ctx c) {
        TaiwanState s = c.s;
        double held = TaiwanLand.heldKm2(s);
        if (held > 12000 || s.sectors.get(c.d.sector("s_taipei")).ctrl > 0.5) return 5;
        if (held > 1500 || s.elec < 0.45) return 4;
        if (held > 300) return 3;
        Loss us = c.loss("USA");
        if (us.killed > 12000 || us.carriers >= 3 || s.gap[E_TWN] > 0.3) return 2;
        return 1;
    }

    static void end(Ctx c, int code, String reason) {
        TaiwanState s = c.s;
        s.phase = "over";
        s.outcome = code;
        s.outcomeText = TaiwanEngine.OUTCOMES[code];
        s.endReason = reason;
        s.endHour = s.hour;
        s.endDay = s.day();
        s.offerFrom = -1;
        s.scoreUS = score(c, US);
        s.scorePRC = score(c, PRC);
        c.alert("END: " + s.outcomeText + ". " + reason);
        c.news(code == 1 || code == 0 || code == 9 ? News.Kind.GOOD : code == 7 ? News.Kind.BAD : News.Kind.WORLD, s.outcomeText.toUpperCase(Locale.ROOT), reason, 7);
        c.out.effects().addAll(TaiwanEngine.fallout(c));
    }

    static double score(Ctx c, int side) {
        TaiwanState s = c.s;
        double v = switch (s.outcome) { case 0 -> 85; case 1 -> 75; case 2 -> 58; case 3 -> 45; case 8 -> 40; case 4 -> 32; case 5 -> 15; case 6 -> 8; case 9 -> 80; case 10 -> 12; default -> 0; };
        if (s.outcome == 8 || s.outcome == 3) v -= TaiwanLand.heldKm2(s) / 1000.0;
        Loss us = c.loss("USA"), cn = c.loss("CHN");
        if (side == US) v -= Math.min(10, us.killed / 1500.0) + Math.min(6, us.ships / 4.0) + s.gap[E_USA] * 40 - Math.min(8, cn.ships / 15.0);
        else {
            v = 100 - v;
            v -= Math.min(10, cn.killed / 4000.0) + s.gap[E_CHN] * 60 + (72 - Math.min(72, s.prcStability)) * 0.2;
        }
        if (s.outcome == 7) v = 0;
        return TMath.clamp(v, 0, 100);
    }

    static void propose(Ctx c, int side, int terms) {
        c.s.offerFrom = side;
        c.s.offerTerms = terms;
        c.s.offerHour = c.s.hour;
        String[] t = {"status quo ante (the PLA leaves Taiwan, the blockade is lifted)", "a ceasefire in place", "unification talks, with US forces leaving the region"};
        c.alert((side == US ? "Washington" : "Beijing") + " proposes a ceasefire: " + t[terms] + ".");
    }

    /**
     * What an agreed ceasefire comes to (docs/BALANCE_TAIWAN.md): status quo ante defends Taiwan (or, with the PLA still
     * ashore, is a stalemate); unification talks, or a ceasefire in place that leaves the PLA more than 1,500 km2 of Taiwan,
     * is America backing down; a smaller lodgement is a stalemate, and Taiwan whole and lit is defended.
     */
    static int ceasefireOutcome(int terms, double heldKm2, double elec) {
        if (terms == 0) return heldKm2 > 500 ? 3 : 1;
        if (terms == 2) return 10;
        if (heldKm2 < 200 && elec > 0.6) return 1;
        return heldKm2 > 1500 ? 10 : heldKm2 > 200 ? 3 : 8;
    }

    static void respond(Ctx c, boolean accept) {
        TaiwanState s = c.s;
        if (s.offerFrom < 0) return;
        int from = s.offerFrom, terms = s.offerTerms;
        s.offerFrom = -1;
        if (!accept) { c.log((from == US ? "Beijing" : "Washington") + " rejects the ceasefire proposal.", true); return; }
        int o = ceasefireOutcome(terms, TaiwanLand.heldKm2(s), s.elec);
        end(c, o, "Ceasefire agreed: " + (terms == 0 ? "status quo ante" : terms == 1 ? "a ceasefire in place" : "unification talks") + ".");
    }
}
