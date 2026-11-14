package game.mandate.core.taiwan;

import game.mandate.core.taiwan.TaiwanData.AirType;
import game.mandate.core.taiwan.TaiwanData.Base;
import game.mandate.core.taiwan.TaiwanData.Munition;
import game.mandate.core.taiwan.TaiwanState.Battery;
import game.mandate.core.taiwan.TaiwanState.Contact;
import game.mandate.core.taiwan.TaiwanState.Country;
import game.mandate.core.taiwan.TaiwanState.Group;
import game.mandate.core.taiwan.TaiwanState.Squadron;
import game.mandate.core.taiwan.TaiwanState.Sub;
import game.mandate.core.taiwan.TaiwanState.Troop;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.taiwan.TaiwanState.*;

/**
 * The computer's minds (spec 4): the PRC planner (its course of action, preparation schedule, go/no-go rule, escalation
 * and fire plan), the US staff (posture by warning level, entry, sanctions and allies), Taiwan's staff, and the staffs of
 * both sides that carry out the theatre directives unit by unit. The directive layer is a list of {@link TaiwanOrder}s
 * (the playbook), the same for the computer's side and for the player's side when the player leaves it to the staff.
 */
final class TaiwanAI {
    private TaiwanAI() {}

    /** A deterministic draw from the state (the playbook must not touch the random streams: it is also shown to the player). */
    static double roll(TaiwanState s, long salt) {
        long z = s.planSeed + salt * 0x9E3779B97F4A7C15L + s.day() * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    static int difficulty(TaiwanState s, int side) { return side == US ? s.difficultyUS : s.difficultyPRC; }

    // =================================================================================================================
    //  The PRC planner (spec 4.3)
    // =================================================================================================================

    static final String[] REGION_BEACHES_NORTH = {"beach_guanyin_zhuwei", "beach_hsinchu_nanliao", "beach_tamsui_bali"};
    static final String[] REGION_BEACHES_CENTRE = {"beach_dajia_qingshui", "beach_lukang_fangyuan", "beach_houlong"};
    static final String[] REGION_BEACHES_SOUTH = {"beach_qigu_anping", "beach_linyuan", "beach_budai"};
    static final String[] STAGE_PORT = {"b_pingtan", "b_quanzhou_port", "b_xiamen_naval_base"};

    static int[] regionBeaches(Ctx c, int reg) {
        String[] ids = reg == 0 ? REGION_BEACHES_NORTH : reg == 1 ? REGION_BEACHES_CENTRE : REGION_BEACHES_SOUTH;
        int[] b = new int[ids.length];
        for (int i = 0; i < ids.length; i++) b[i] = c.d.need("bc:" + ids[i]);
        return b;
    }

    /** The plan and its options as the planner would set them (spec 4.3), drawn from the state's seed. */
    static TaiwanOrder.Plan planOrder(TaiwanState s, int plan) {
        boolean big = plan == 0 || plan == 4;
        boolean intervene = s.commitment.equals("INTERVENE");
        boolean strikeUS = intervene && (big && roll(s, 11) < (plan == 0 ? 0.8 : 0.45) || plan == 1 && roll(s, 12) < 0.15);
        double reg = roll(s, 13);
        int region = reg < 0.45 ? 0 : reg < 0.62 ? 1 : 2;
        int lethality = plan == 1 ? (roll(s, 14) < 0.6 ? 1 : roll(s, 15) < 0.5 ? 2 : 0) : 1;
        return new TaiwanOrder.Plan(TaiwanData.PLANS[plan], strikeUS, strikeUS && s.strikeJapanEnabled, plan != 2, plan == 0 && roll(s, 16) < 0.35,
                plan == 0 && roll(s, 17) < 0.3, region, lethality);
    }

    static int choosePlan(TaiwanState s) {
        if (s.fixedPlan != null) return TaiwanEngine.planIndex(s.fixedPlan);
        double[] w = {0.46, 0.24, 0.1, 0.12, 0.08};
        if (s.preset == 2) w[0] += 0.15;
        if (s.difficultyPRC == 0) { w[2] += 0.1; w[0] -= 0.1; }
        double tot = 0;
        for (double x : w) tot += x;
        double x = roll(s, 10) * tot;
        for (int i = 0; i < w.length; i++) { x -= w[i]; if (x <= 0) return i; }
        return 0;
    }

    static int lead(TaiwanData.Programme p, TaiwanState s) {
        int l = p.id().equals("exercise") && s.difficultyPRC == 2 ? 15 : p.lead();
        return s.difficultyPRC == 0 ? (int) (l * 0.75) : l;
    }

    /** How costly attacking looks from Beijing (spec 4.3), reading the truth. */
    static double deterrence(Ctx c) {
        TaiwanState s = c.s;
        double d = 8 + s.deterrenceG * 0.8;
        d += s.blueMobilized >= 0 ? 5 : 0;
        Country jp = TaiwanWorld.country(c, "JPN");
        d += jp.basing ? 4 : -6;
        d += jp.level() >= 5 ? 3 : 0;
        d += TaiwanWorld.country(c, "PHL").basing ? 4 : 0;
        d += TaiwanWorld.country(c, "AUS").level() >= 5 ? 2 : 0;
        int cv = 0;
        for (Group g : s.groups) if (g.side == US && !g.dead() && !g.gone && g.hub < 0 && TaiwanSea.carrierGroup(c, g) && TaiwanSea.fromTaiwan(c, g) < 2000) cv++;
        d += cv * 2;
        d += s.sanctUS * 2 + (s.swift ? 3 : 0) + (s.freeze ? 2 : 0);
        d += (s.will - 60) * 0.3 + (s.usSupport - 45) * 0.3 - (s.nationalism - 60) * 0.3;
        if (s.plan >= 0) {
            var red = c.d.programmes(PRC);
            for (int i = 0; i < red.size(); i++) if (red.get(i).needed(s.plan) && s.progPct[PRC][i] < 100) d += 3;
        }
        d += TaiwanWorld.progDone(c, US, "subs") ? 2 : 0;
        d += TaiwanWorld.progDone(c, US, "disperse") ? 2 : 0;
        d += TaiwanWorld.progDone(c, US, "mobilise") ? 4 : 0;
        if (TaiwanWorld.usWillNotFight(s)) d -= 8;                 // Washington has said it will not fight
        if (s.commitment.equals("STAND_ASIDE")) d -= 25;
        else if (s.commitment.equals("ARM_AND_SANCTION")) d -= 12;
        return d;
    }

    /**
     * Beijing's appetite for risk in this crisis (docs/BALANCE_TAIWAN.md): drawn once from the plan's seed, bolder in the
     * pessimistic case. However well Washington prepares, a determined leadership sometimes goes ahead.
     */
    static double resolve(TaiwanState s) {
        double r = (roll0(s, 41) - 0.5) * RESOLVE_SPREAD;
        return r + (s.preset == 2 ? 5 : s.preset == 1 ? -3 : 0);
    }

    /** How many warning levels the president's own staff waits beyond a computer-run staff (docs/BALANCE_TAIWAN.md). */
    static final int STAFF_CAUTION = 1;
    /** The spread of Beijing's resolve, in points of the go/no-go threshold. */
    static final double RESOLVE_SPREAD = 18;

    /** A draw from the plan's seed that does not change from day to day. */
    static double roll0(TaiwanState s, long salt) {
        long z = s.planSeed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    static void red(Ctx c, List<TaiwanOrder> l) {
        TaiwanState s = c.s;
        int day = s.day();
        if (s.phase.equals("crisis")) {
            if (s.hour % 24 != 0) return;
            if (!s.planChosen) {
                int plan = choosePlan(s);
                l.add(planOrder(s, plan));
                l.add(new TaiwanOrder.DDay(36 + (int) Math.floor(roll(s, 20) * 18)));
                return;
            }
            int dd = s.dDay;
            if (dd <= 0 || s.standDownDay >= 0) return;
            var red = c.d.programmes(PRC);
            for (int i = 0; i < red.size(); i++)
                if (!s.progOn[PRC][i] && red.get(i).needed(s.plan) && day >= dd - lead(red.get(i), s)) l.add(new TaiwanOrder.Programme(red.get(i).id(), true));
            if (day % 4 == 1 && s.dipCap[PRC] > 12) {
                String[] tg = {"PHL", "KOR", "ASEAN", "EU", "IND", "VNM"};
                String k = tg[(day / 4) % tg.length];
                l.add(new TaiwanOrder.Diplomacy(k, k.equals("EU") || k.equals("ASEAN") || k.equals("IND") ? "reassure" : roll(s, 30) < 0.5 ? "coerce" : "neutrality"));
            }
            if (day == dd - 6 && s.dipCap[PRC] >= 10) l.add(new TaiwanOrder.Diplomacy("RUS", "support"));
            if (s.sanctUS >= 2 && !s.rareEarths) l.add(new TaiwanOrder.Economic("rareearths"));
            if (s.sanctUS >= 1 && !s.boycott && roll(s, 31) < 0.3) l.add(new TaiwanOrder.Economic("boycott"));
            if (day == dd - 2 && s.fixedPlan == null) {        // a plan fixed by the scenario goes ahead (as the source's harness forced it)
                double d = deterrence(c);
                double thr = (s.difficultyPRC == 0 ? 56 : s.difficultyPRC == 1 ? 60 : 66) + resolve(s);
                if (d > thr) {
                    if (s.postponed < 2) l.add(new TaiwanOrder.DDay(dd + 7 + (int) Math.floor(roll(s, 21) * 5)));
                    else if (s.plan == 0 && d < thr + 15) {
                        TaiwanOrder.Plan p = planOrder(s, 1);
                        l.add(new TaiwanOrder.Plan("blockade", false, false, p.cyber(), false, false, s.region, 1));
                    } else l.add(new TaiwanOrder.StandDown());
                }
            }
            return;
        }
        if (!s.atWar()) return;
        double wh = s.warHours();
        boolean big = s.plan == 0 || s.plan == 4 || s.optStrikeUS;
        int r = s.rung[PRC];
        if (s.usCombat && r < 3) { l.add(new TaiwanOrder.Rung(3)); r = 3; }
        if (s.usCombat && r < 5 && (big || s.rung[US] >= 3 || s.mainlandHits > 0) && (s.difficultyPRC >= 1 || wh > 24)) { l.add(new TaiwanOrder.Rung(5)); r = 5; }
        if (TaiwanWorld.country(c, "JPN").belligerent && r < 5 && (big || s.rung[US] >= 3)) { l.add(new TaiwanOrder.Rung(5)); r = 5; }
        if (s.plan == 2 && wh > 24 * 8 && s.throughput > 0.5 && r < 2) { l.add(new TaiwanOrder.Rung(2)); r = 2; }
        if ((s.plan == 1 || s.plan == 2) && wh > 24 * 20 && s.elec > 0.7 && r < 3) l.add(new TaiwanOrder.Rung(3));
        if (s.mainlandHits > 200 && s.alert[PRC] < 1) l.add(new TaiwanOrder.Nuclear(1));
        if (s.c2Hits > 60 && s.alert[PRC] < 2) l.add(new TaiwanOrder.Nuclear(2));
        if (s.nukeRisk < 40 && s.alert[PRC] > 0 && s.mainlandHits < 50) l.add(new TaiwanOrder.Nuclear(0));
        // the daily routine, once a day from 05:00 (the source ran it only on turns starting at exactly 05:00)
        if (s.aiDay1 != day && s.hour % 24 >= 5) {
            if (s.sanctUS >= 2 && !s.rareEarths) l.add(new TaiwanOrder.Economic("rareearths"));
            if (s.swift && s.sanctCN < 1) l.add(new TaiwanOrder.Economic("countersanctions"));
            if (s.freeze && !s.treasury && s.difficultyPRC >= 1) l.add(new TaiwanOrder.Economic("treasury"));
            if (s.dipCap[PRC] > 20) {
                String[] tg = {"KOR", "PHL", "ASEAN", "IND"};
                String k = tg[day % tg.length];
                l.add(new TaiwanOrder.Diplomacy(k, k.equals("ASEAN") || k.equals("IND") ? "reassure" : "threaten"));
            }
            if (TaiwanWorld.country(c, "PRK").stance < -70 && !TaiwanWorld.country(c, "PRK").belligerent && s.dipCap[PRC] > 12 && s.difficultyPRC == 2)
                l.add(new TaiwanOrder.Diplomacy("PRK", "support"));
        }
    }

    // =================================================================================================================
    //  The US staff (spec 4.4)
    // =================================================================================================================

    static final String[] ALLIES = {"JPN", "AUS", "PHL", "GBR", "KOR", "EU"};

    static void blue(Ctx c, List<TaiwanOrder> l) {
        TaiwanState s = c.s;
        int day = s.day();
        int diff = s.difficultyUS;
        boolean stand = s.commitment.equals("STAND_ASIDE"), arm = s.commitment.equals("ARM_AND_SANCTION");
        if (s.phase.equals("crisis")) {
            if (s.hour % 24 != 0) return;
            int w = s.warnLevel[US] - (diff == 0 ? 1 : 0) + (diff == 2 ? 1 : 0);
            // the president's own staff waits for clearer warning before the big preparations: those are the president's to
            // order (docs/BALANCE_TAIWAN.md); a computer-run America prepares as the source's staff did
            if (s.playerSide == US) w -= STAFF_CAUTION;
            if (arm) { prog(c, l, "arms"); if (s.sanctUS < 2) l.add(new TaiwanOrder.Economic(s.sanctUS < 1 ? "sanctions1" : "sanctions2")); }
            if (w >= 1 && !stand) { prog(c, l, "readiness"); if (day % 5 == 0) l.add(new TaiwanOrder.Diplomacy("JPN", "consult")); }
            if (w >= 2) {
                if (!stand) {
                    prog(c, l, "subs"); prog(c, l, "bombers"); prog(c, l, "munitions"); prog(c, l, "arms"); prog(c, l, "sealift");
                    if (!TaiwanWorld.country(c, "PHL").basing && day % 3 == 0) l.add(new TaiwanOrder.Diplomacy("PHL", "basing"));
                    if (s.mobilized < 0.5) l.add(new TaiwanOrder.TaiwanAsk("partial"));
                }
                if (diff >= 1) prog(c, l, "declassify");
                if (day % 4 == 2) l.add(new TaiwanOrder.Diplomacy("AUS", "consult"));
            }
            if (w >= 3 && !stand) {
                prog(c, l, "fighters"); prog(c, l, "disperse"); prog(c, l, "littoral"); prog(c, l, "departure"); prog(c, l, "huntforward");
                if (diff >= 1) prog(c, l, "showforce");
                if (diff == 2) { prog(c, l, "mobilise"); if (s.sanctUS < 1) l.add(new TaiwanOrder.Economic("sanctions1")); }
                if (!TaiwanWorld.country(c, "JPN").civilAirports && day % 3 == 1) l.add(new TaiwanOrder.Diplomacy("JPN", "airports"));
                if (day % 3 == 2) l.add(new TaiwanOrder.Diplomacy("KOR", "assurance"));
            }
            if (w >= 4 && !stand) { prog(c, l, "mobilise"); prog(c, l, "advisers"); }
            return;
        }
        if (!s.atWar()) return;
        double wh = s.warHours();
        int cap = TaiwanWorld.rungCap(s);
        if (!s.usCombat) {
            double delay = diff == 0 ? 24 : diff == 1 ? 6 : 1;
            if (s.rung[PRC] >= 4 && wh >= delay && cap >= 2) l.add(new TaiwanOrder.Rung(2));
            else if (s.rung[PRC] >= 2 && s.blockade >= 2) {
                if (s.rung[US] < 1 && cap >= 1) l.add(new TaiwanOrder.Rung(1));
                if (cap >= 1) l.add(new TaiwanOrder.Convoys(true));
                if ((s.rung[PRC] >= 3 && wh > delay * 4 || s.elec < 0.65) && cap >= 2) l.add(new TaiwanOrder.Rung(2));
            } else if (s.rung[PRC] >= 1 && s.rung[US] < 1 && cap >= 1) l.add(new TaiwanOrder.Rung(1));
        } else {
            if (s.rung[PRC] >= 5 && s.rung[US] < 3) l.add(new TaiwanOrder.Rung(3));
            if (diff == 2 && s.plan == 0 && s.rung[US] < 4 && s.usSupport > 42 && s.nukeRisk < 45 && wh > 12) l.add(new TaiwanOrder.Rung(4));
            if (s.nukeRisk > 70 && s.rung[US] > 3) l.add(new TaiwanOrder.Rung(3));
        }
        if (s.blockade >= 2 && cap >= 1) l.add(new TaiwanOrder.Convoys(true));
        // the daily routine, once a day from 09:00 (the source ran it only on turns starting at exactly 09:00, spec 13.1)
        if (s.aiDay0 != day && s.hour % 24 >= 9 && !stand) {
            if (s.sanctUS < 2) l.add(new TaiwanOrder.Economic(s.sanctUS < 1 ? "sanctions1" : "sanctions2"));
            if (s.rung[PRC] >= 4 && !s.swift) l.add(new TaiwanOrder.Economic("swift"));
            if (s.rung[PRC] >= 4 && !s.freeze) l.add(new TaiwanOrder.Economic("freeze"));
            if (!s.chipEmbargo && wh > 48) l.add(new TaiwanOrder.Economic("chips"));
            if (s.sanctUS < 3 && wh > 24 * 7) l.add(new TaiwanOrder.Economic("sanctions3"));
            if (!s.distantBlockade && s.rung[US] >= 3 && wh > 24 * 10 && diff >= 1) l.add(new TaiwanOrder.Economic("distant"));
            double capital = s.dipCap[US];
            for (String k : ALLIES) {
                if (capital < 10) break;
                Country x = TaiwanWorld.country(c, k);
                int role = x.role;
                if (s.usCombat && !x.belligerent && x.level() >= 5 && role >= 3) { l.add(new TaiwanOrder.Diplomacy(k, "combat")); capital -= 14; continue; }
                if (!x.basing && x.level() >= 4 && role >= 1 && (k.equals("PHL") || k.equals("KOR") || role >= 2)) { l.add(new TaiwanOrder.Diplomacy(k, "basing")); capital -= 10; continue; }
                if (!x.sanctions && k.equals("EU")) { l.add(new TaiwanOrder.Diplomacy(k, "sanctions")); capital -= 6; }
            }
        }
    }

    static void prog(Ctx c, List<TaiwanOrder> l, String id) {
        if (!TaiwanWorld.progOn(c, US, id) && !TaiwanWorld.progDone(c, US, id)) l.add(new TaiwanOrder.Programme(id, true));
    }

    /** The ceasefire rules of the computer's side (spec 4.6): answers, and proposals once a day from noon after five days of war. */
    static void peace(Ctx c, int side, List<TaiwanOrder> l, boolean propose) {
        TaiwanState s = c.s;
        if (!s.atWar()) return;
        double held = TaiwanLand.heldKm2(s), ashore = s.plaAshore();
        if (s.offerFrom >= 0 && s.offerFrom != side) {
            int t = s.offerTerms;
            boolean yes;
            if (side == PRC) {
                if (t == 0) yes = s.prcStability < 30 || s.plan == 0 && TaiwanWorld.amphibLostFrac(c) > 0.8 && ashore < 5;
                else if (t == 1) yes = held > 1500 || s.elec < 0.5 || s.prcStability < 40 || s.nukeRisk > 75;
                else yes = true;
            } else {
                if (t == 0) yes = true;
                else if (t == 1) yes = s.usSupport < 30 || s.will < 30 || s.nukeRisk > 80 || held < 100;
                else yes = s.usSupport < 18;
            }
            l.add(new TaiwanOrder.Answer(yes));
            return;
        }
        if (!propose || s.offerFrom >= 0 || s.warHours() < 24 * 5 || (side == US ? s.peaceDay : s.peaceDayPRC) == s.day() || s.hour % 24 < 12) return;
        if (side == PRC) {
            boolean stalled = s.plan == 0 && TaiwanWorld.amphibLostFrac(c) > 0.6 && held > 500;
            if (stalled || s.nukeRisk > 75) l.add(new TaiwanOrder.Ceasefire(1));
            else if (s.prcStability < 25) l.add(new TaiwanOrder.Ceasefire(0));
        } else {
            if (s.plan == 0 && ashore < 4 && TaiwanWorld.amphibLostFrac(c) > 0.6) l.add(new TaiwanOrder.Ceasefire(0));
            else if (s.usSupport < 25) l.add(new TaiwanOrder.Ceasefire(1));
        }
    }

    /** The staff's directives for a side this turn. */
    static List<TaiwanOrder> playbook(Ctx c, int side, boolean proposals) {
        List<TaiwanOrder> l = new ArrayList<>();
        if (c.s.over()) return l;
        if (side == PRC) red(c, l); else blue(c, l);
        peace(c, side, l, proposals);
        return l;
    }

    // =================================================================================================================
    //  The staffs carry out the directives (spec 4.2)
    // =================================================================================================================

    static void staff(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        for (int side = 0; side < 2; side++) {
            air(c, side, false);
            naval(c, side, false);
            subs(c, side, false);
            fires(c, side, false);
            if (side == PRC) { amphib(c); ground(c, PRC); }
        }
        taiwanTurn(c);
    }

    /** At H-hour the PLA's opening plan runs at once, then Taiwan's staff (spec 4.2). */
    static void hHour(Ctx c) {
        fires(c, PRC, false);
        air(c, PRC, false);
        subs(c, PRC, false);
        naval(c, PRC, false);
        amphib(c);
        taiwanTurn(c);
    }

    static void taiwanTurn(Ctx c) {
        TaiwanState s = c.s;
        if (s.over()) return;
        if (s.phase.equals("crisis") && s.hour % 24 == 0 && (s.warnLevel[US] >= 3 || s.blockade >= 1) && s.mobilized < 0.5)
            TaiwanWorld.mobilizeTaiwan(c, 0.5, "PLA preparations");
        air(c, US, true);
        naval(c, US, true);
        subs(c, US, true);
        fires(c, US, true);
        if (s.atWar() && s.hour - s.groundHour0 >= 6) { s.groundHour0 = s.hour; taiwanGround(c); }
    }

    // ---- air ----------------------------------------------------------------------------------------------------------

    static int zone(Ctx c, String id) { return c.d.need("z:" + id); }

    static int firstInReach(Ctx c, double[] o, double r, String[] pref) {
        for (String p : pref) { TaiwanData.Zone z = c.d.zones.get(zone(c, p)); if (TaiwanSea.dist(o[0], o[1], z.x(), z.y()) <= r) return zone(c, p); }
        int best = -2;
        double bd = 1e18;
        for (String p : pref) { TaiwanData.Zone z = c.d.zones.get(zone(c, p)); double dd = TaiwanSea.dist(o[0], o[1], z.x(), z.y()); if (dd < bd) { bd = dd; best = zone(c, p); } }
        return bd <= r * 1.6 ? best : -2;
    }

    static int rotateInReach(Ctx c, double[] o, double r, String[] pref, int salt) {
        List<Integer> ok = new ArrayList<>();
        for (String p : pref) { TaiwanData.Zone z = c.d.zones.get(zone(c, p)); if (TaiwanSea.dist(o[0], o[1], z.x(), z.y()) <= r) ok.add(zone(c, p)); }
        if (ok.isEmpty()) return firstInReach(c, o, r * 1.5, pref);
        return ok.get(Math.abs(salt) % Math.min(ok.size(), 3));
    }

    static int coverArea(Ctx c, int side, double[] o, double r) {
        return firstInReach(c, o, r, side == US ? new String[]{"EAST", "NE", "OKINAWA", "PS_W", "GUAM", "YELLOW", "PS_C"}
                : new String[]{"TS_C", "TS_N", "TS_S", "ECS_S", "SCS_N"});
    }

    /** -1 is over Taiwan (the twZone), -2 nothing in reach. */
    static int capArea(Ctx c, int side, double[] o, double r) {
        double toTw = TaiwanSea.dist(o[0], o[1], 0, 0);
        if (side == US) {
            if (c.s.atWar() && toTw < r * 1.1) return c.d.twZone;
            return firstInReach(c, o, r * 0.6, new String[]{"OKINAWA", "ECS_S", "YONAGUNI", "GUAM", "YELLOW", "WLUZON", "PS_C", "SOJ"});
        }
        return firstInReach(c, o, r * 0.8, new String[]{"TS_C", "TS_N", "TS_S", "PENGHU", "ECS_S", "SCS_N", "YELLOW"});
    }

    static int aswArea(Ctx c, int side, double[] o, double r, int salt) {
        return rotateInReach(c, o, r * 0.9, side == US ? new String[]{"TS_S", "BASHI", "EAST", "MIYAKO", "PS_W", "OKINAWA", "GUAM", "LUZON", "SOJ", "PS_C"}
                : new String[]{"TS_N", "TS_C", "EAST", "BASHI", "SCS_N", "ECS_S", "YELLOW"}, salt);
    }

    static int isrArea(Ctx c, int side, double[] o, double r, int salt) {
        return rotateInReach(c, o, r * 0.9, side == US ? new String[]{"TS_C", "TS_N", "TS_S", "ECS_S", "SCS_N", "PS_W", "GUAM"}
                : new String[]{"PS_W", "EAST", "OKINAWA", "PS_N", "PS_C", "GUAM", "BASHI"}, salt + c.s.day());
    }

    static void setZone(Squadron q, int mission, int zone) {
        if (zone == -2) { q.mission = AM_HOLD; q.zone = -1; return; }
        q.mission = mission; q.zone = zone;
    }

    /** The US air priority directive as a role per squadron (it replaces the source's id % 4): 0 anti-ship strike, 1 mainland
     *  strike, 2 CAP over Taiwan, 3 fleet defence. The shares are of aircraft, in the order of battle. */
    static int[] usRoles(Ctx c) {
        TaiwanState s = c.s;
        int[] role = new int[s.squadrons.size()];
        double[] sh = s.airShares;
        double tot = sh[0] + sh[1] + sh[2] + sh[3];
        if (tot <= 0) { sh = new double[]{0.5, 0, 0.25, 0.25}; tot = 1; }
        double strikers = 0, fighters = 0;
        for (Squadron q : s.squadrons) {
            if (q.side != US || q.nat.equals("TWN") || q.n <= 0) continue;
            AirType a = TaiwanAir.at(c, q);
            if (a.is(TaiwanData.FTR) && a.is(TaiwanData.STRIKE)) strikers += q.n;
            else if (a.is(TaiwanData.FTR)) fighters += q.n;
        }
        double accS = 0, accF = 0;
        double capShare = sh[2] / Math.max(1e-9, sh[2] + sh[3]);
        for (int i = 0; i < s.squadrons.size(); i++) {
            Squadron q = s.squadrons.get(i);
            if (q.side != US || q.nat.equals("TWN") || q.n <= 0) continue;
            AirType a = TaiwanAir.at(c, q);
            if (a.is(TaiwanData.FTR) && a.is(TaiwanData.STRIKE)) {
                double f = (accS + q.n * 0.5) / Math.max(1, strikers) * tot;
                accS += q.n;
                role[i] = f < sh[0] ? 0 : f < sh[0] + sh[1] ? 1 : f < sh[0] + sh[1] + sh[2] ? 2 : 3;
            } else if (a.is(TaiwanData.FTR)) {
                double f = (accF + q.n * 0.5) / Math.max(1, fighters);
                accF += q.n;
                role[i] = f < capShare ? 2 : 3;
            } else role[i] = sh[1] > sh[0] ? 1 : 0;
        }
        return role;
    }

    static void air(Ctx c, int side, boolean taiwan) {
        TaiwanState s = c.s;
        boolean war = s.atWar();
        java.util.Map<Integer, Integer> onTarget = new java.util.LinkedHashMap<>();
        for (Squadron o2 : s.squadrons) if (o2.side == side && o2.mission == AM_STRIKE && o2.tKind == TK_GROUP) onTarget.merge(o2.tId, 1, Integer::sum);
        boolean replan = TaiwanEngine.turnLength(s) >= 6 || s.hour % 6 == 0;
        int[] roles = side == US && !taiwan ? usRoles(c) : null;
        for (int i = 0; i < s.squadrons.size(); i++) {
            Squadron q = s.squadrons.get(i);
            if (q.side != side || q.n <= 0 || q.held || !TaiwanAir.flies(c, q)) continue;
            if (side == US && q.nat.equals("TWN") != taiwan) continue;
            if (war && !TaiwanAir.nationActive(c, q.nat, side)) {
                if (q.mission != AM_HOLD && q.mission != AM_ISR && q.mission != AM_AEW && q.mission != AM_ASW) { q.mission = AM_HOLD; q.zone = -1; }
                continue;
            }
            double[] o = TaiwanAir.origin(c, q);
            if (o == null) continue;
            AirType a = TaiwanAir.at(c, q);
            boolean busy = q.mission == AM_STRIKE && TaiwanFire.targetPos(c, side, q.tKind, q.tId) != null && s.hour - q.lastStrike < 12;
            if (busy && !replan) continue;
            int fleetZone = -2;
            if (q.carrier >= 0) { Group cv = c.group(q.carrier); if (cv != null && cv.at >= 0) fleetZone = cv.at; }
            if (a.is(TaiwanData.AEW)) { setZone(q, AM_AEW, fleetZone >= 0 ? fleetZone : coverArea(c, side, o, a.radius())); continue; }
            if (a.is(TaiwanData.TNK)) { q.mission = AM_TANK; continue; }
            if (a.is(TaiwanData.ASW)) { setZone(q, AM_ASW, aswArea(c, side, o, a.radius(), q.id)); continue; }
            if (a.is(TaiwanData.ISR)) { setZone(q, AM_ISR, isrArea(c, side, o, a.radius(), q.id)); continue; }
            if (a.is(TaiwanData.LIFT)) { lift(c, q); continue; }
            if (a.is(TaiwanData.EW)) { q.mission = AM_HOLD; continue; }
            boolean fighter = a.is(TaiwanData.FTR), striker = a.is(TaiwanData.STRIKE) || a.is(TaiwanData.BMB);
            if (!war) {
                if (fighter && q.id % 3 == 0) setZone(q, AM_CAP, capArea(c, side, o, a.radius()));
                else q.mission = AM_HOLD;
                continue;
            }
            if (taiwan && a.is(TaiwanData.HELO)) {
                int sec = TaiwanLand.hottest(s);
                if (sec >= 0) { q.mission = AM_CAS; q.tKind = TK_SECTOR; q.tId = sec; } else q.mission = AM_HOLD;
                continue;
            }
            int role;
            if (roles != null) role = roles[i] == 0 ? 2 : roles[i] == 1 ? 3 : roles[i] == 2 ? 0 : 1;   // the source's numbering: 2-3 strike, 0 CAP, 1 fleet
            else role = q.id % 4;
            boolean mainlandRole = roles != null && roles[i] == 1;
            if (striker && (!fighter || role >= 2 || a.is(TaiwanData.BMB))) {
                if (q.mission == AM_STRIKE && q.tKind == TK_GROUP) onTarget.merge(q.tId, -1, Integer::sum);
                if (pickStrike(c, q, o, mainlandRole, onTarget)) { if (q.tKind == TK_GROUP) onTarget.merge(q.tId, 1, Integer::sum); continue; }
                if (side == US && a.is(TaiwanData.BMB) && mine(c, q)) continue;
                if (side == PRC && !fighter && TaiwanLand.hottestRed(s) >= 0 && a.radius() > 300) { q.mission = AM_CAS; q.tKind = TK_SECTOR; q.tId = TaiwanLand.hottestRed(s); continue; }
            }
            if (fighter) {
                if (taiwan && s.rung[PRC] <= 3 && q.id % 4 != 0) { q.mission = AM_HOLD; continue; }
                int z = capArea(c, side, o, a.radius());
                if (roles != null && roles[i] == 2 && TaiwanSea.dist(o[0], o[1], 0, 0) < a.radius() * 1.1) z = c.d.twZone;
                if (fleetZone >= 0 && (role <= 1 || !striker) && (roles == null || roles[i] == 3)) z = fleetZone;
                if (side == PRC && a.quality() >= 2.4 && role == 0) { q.mission = AM_SWEEP; q.zone = c.d.twZone; continue; }
                if (z == -2) { q.mission = AM_HOLD; continue; }
                setZone(q, AM_CAP, z);
                if (role == 1 && a.quality() >= 1.4) {
                    for (Squadron b : s.squadrons) {
                        if (b.side != side || b.mission != AM_STRIKE || b.n <= 0 || !TaiwanAir.at(c, b).is(TaiwanData.BMB)) continue;
                        double[] bo = TaiwanAir.origin(c, b);
                        if (bo != null && TaiwanSea.dist(bo[0], bo[1], o[0], o[1]) < 900) { q.mission = AM_ESCORT; q.escortOf = b.id; break; }
                    }
                }
                continue;
            }
            q.mission = AM_HOLD;
        }
    }

    /** The value of an enemy naval contact to a staff (spec 4.2). */
    static double value(Ctx c, Contact k) {
        Group g = c.group(k.id);
        if (k.decoy) return 6;
        if (g == null) return 1;
        if (k.ident == 0) return 3;
        if (TaiwanSea.amphib(c, g)) return c.s.atWar() && !g.cargo.isEmpty() ? 12 : 9;
        if (TaiwanSea.carrierGroup(c, g)) return 8;
        if (TaiwanSea.coastGuard(c, g)) return c.s.rung[PRC] >= 3 ? 1.5 : 0.2;
        return 2 + g.alive() * 0.4;
    }

    static int perSortie(Ctx c, Squadron q, int dflt) {
        if (q.side != US) return dflt;
        return c.s.munitionsPolicy == 0 ? Math.max(2, dflt / 2) : c.s.munitionsPolicy == 2 ? q.n : dflt;
    }

    static boolean pickStrike(Ctx c, Squadron q, double[] o, boolean mainlandFirst, java.util.Map<Integer, Integer> onTarget) {
        TaiwanState s = c.s;
        int side = q.side;
        AirType a = TaiwanAir.at(c, q);
        int p = TaiwanFire.poolFor(side, q.nat);
        double reachBase = a.radius() * (1 + c.tank[side]);
        if (mainlandFirst && strikeMainland(c, q, o, reachBase, p, false)) return true;
        Contact best = null;
        double bv = 0;
        int bw = -1;
        int w = TaiwanFire.autoWeapon(c, q, TK_GROUP);
        if (w >= 0) {
            Munition mu = c.d.munitions.get(w);
            for (Contact k : s.contacts.get(side).values()) {
                if (k.kind != TK_GROUP || s.hour - k.seen > 4) continue;
                if (s.contactError(k) > 120) continue;
                double dd = TaiwanSea.dist(o[0], o[1], k.x, k.y);
                if (dd > reachBase + mu.range() * 0.85) continue;
                if (!TaiwanFire.allowed(c, side, TK_GROUP, k.id, w)) continue;
                double v = value(c, k) * (1 - dd / (reachBase + mu.range()) * 0.4);
                v /= 1 + Math.max(0, onTarget.getOrDefault(k.id, 0)) * 0.5;
                if (v > bv) { bv = v; best = k; bw = w; }
            }
        }
        if (best != null && bv > 1) {
            q.mission = AM_STRIKE; q.tKind = TK_GROUP; q.tId = best.id; q.weapon = bw; q.perSortie = perSortie(c, q, q.n);
            return true;
        }
        if (side == PRC && (a.is(TaiwanData.BMB) || a.weapons().length > 0 && c.d.munitions.get(a.weapons()[a.weapons().length - 1]).land())) {
            int lw = -1;
            for (int x : a.weapons()) if (c.d.munitions.get(x).land() && s.pool[p][x] >= 1) lw = x;
            if (lw >= 0) {
                int t = landTarget(c, o, reachBase + c.d.munitions.get(lw).range() * 0.85, lw);
                if (t >= 0) { q.mission = AM_STRIKE; q.tKind = TK_BASE; q.tId = t; q.weapon = lw; q.perSortie = Math.max(2, q.n / 2); return true; }
            }
        }
        if (side == US && (mainlandFirst || s.rung[US] >= 4 && a.is(TaiwanData.BMB))) return strikeMainland(c, q, o, reachBase, p, a.is(TaiwanData.BMB));
        return false;
    }

    static boolean strikeMainland(Ctx c, Squadron q, double[] o, double reachBase, int p, boolean longRange) {
        TaiwanState s = c.s;
        if (s.rung[US] < 4 || s.mainland == 0) return false;
        AirType a = TaiwanAir.at(c, q);
        int w = -1;
        for (int x : a.weapons()) {
            Munition mu = c.d.munitions.get(x);
            if (mu.land() && s.pool[p][x] >= 1 && (!longRange || mu.range() > 500)) w = x;
        }
        if (w < 0) return false;
        int t = mainlandTarget(c, o, reachBase + c.d.munitions.get(w).range() * 0.85);
        if (t < 0) return false;
        q.mission = AM_STRIKE; q.tKind = TK_BASE; q.tId = t; q.weapon = w; q.perSortie = perSortie(c, q, Math.max(2, q.n / 2));
        return true;
    }

    /** US bombers lay mines in the strait the PLA's amphibious groups cross (the source never assigned minelaying, spec 13.1). */
    static boolean mine(Ctx c, Squadron q) {
        TaiwanState s = c.s;
        if (s.plan != 0 && s.plan != 3) return false;
        int qs = c.d.munition("QUICKSTRIKE");
        boolean carries = false;
        for (int x : TaiwanAir.at(c, q).weapons()) if (x == qs) carries = true;
        if (!carries || s.pool[TaiwanFire.poolFor(US, q.nat)][qs] < 8) return false;
        int bestZone = -1;
        double bestSeen = -1;
        for (Contact k : s.contacts.get(US).values()) {
            if (k.kind != TK_GROUP || k.zone < 0 || !c.d.zones.get(k.zone).id().startsWith("TS_")) continue;
            Group g = c.group(k.id);
            if (g == null || !TaiwanSea.amphib(c, g) || k.ident < 1) continue;
            if (k.seen > bestSeen) { bestSeen = k.seen; bestZone = k.zone; }
        }
        if (bestZone < 0 || s.mines[US][bestZone] >= 400 || !TaiwanFire.allowed(c, US, TK_ZONE, bestZone, qs)) return false;
        double[] o = TaiwanAir.origin(c, q);
        TaiwanData.Zone z = c.d.zones.get(bestZone);
        if (o == null || TaiwanSea.dist(o[0], o[1], z.x(), z.y()) > TaiwanAir.at(c, q).radius() * (1 + c.tank[US])) return false;
        q.mission = AM_MINE; q.tKind = TK_ZONE; q.tId = bestZone; q.weapon = qs; q.perSortie = Math.max(2, q.n / 2);
        return true;
    }

    static void lift(Ctx c, Squadron q) {
        TaiwanState s = c.s;
        if (q.side == PRC) {
            if (!s.atWar() || s.plan != 0 && s.plan != 3) { q.mission = AM_HOLD; return; }
            boolean troops = false;
            for (Troop t : TaiwanLand.staged(c, PRC, q.base, 120))
                if (TaiwanLand.isType(t, "airborne") || TaiwanLand.isType(t, "airassault") || TaiwanLand.isType(t, "sof")) { troops = true; break; }
            int sec = dropSector(c, q);
            if (troops && sec >= 0 && s.warHours() >= 3) { q.mission = AM_AIRBORNE; q.tKind = TK_SECTOR; q.tId = sec; q.perSortie = q.n; return; }
            int f = capturedAirfield(c);
            if (f >= 0) { q.mission = AM_AIRLIFT; q.tKind = TK_FACILITY; q.tId = f; q.perSortie = q.n; return; }
            q.mission = AM_HOLD;
            return;
        }
        if (TaiwanAir.at(c, q).id().equals("MV22")) { q.mission = AM_HOLD; return; }
        if (s.blockade >= 2 && s.rung[PRC] <= 3) {
            int b = c.d.base("b_hualien_ab_chiashan");
            if (TaiwanAir.runwayOpen(c, b) && !s.bases.get(b).captured) { q.mission = AM_AIRLIFT; q.tKind = TK_BASE; q.tId = b; q.perSortie = Math.max(2, q.n / 3); return; }
        }
        if (s.blueMobilized >= 0 && q.base >= 0 && c.d.bases.get(q.base).cc().equals("USA")) {
            q.mission = AM_AIRLIFT; q.tKind = TK_BASE; q.tId = c.d.base("b_kadena_ab"); q.perSortie = Math.max(2, q.n / 4);
            return;
        }
        q.mission = AM_HOLD;
    }

    // ---- surface fleets ------------------------------------------------------------------------------------------------

    static boolean crippled(Ctx c, Group g) {
        if (!g.nat.equals("USA")) return false;
        for (TaiwanState.Ship sh : g.ships) { TaiwanData.ShipClass k = TaiwanSea.sc(c, sh); if ((k.carrier() || k.amphib()) && sh.mk && sh.dmg > 0.4) return true; }
        return false;
    }

    /** A group goes to port when its area defences run low, when it has no offensive missiles left (the source kept such
     *  groups at sea, spec 13.1), when half its ships cannot fight, or when its carrier or big-deck amphib is hurt. */
    static boolean needsPort(Ctx c, Group g) {
        int capL = 0;
        for (TaiwanState.Ship sh : g.ships) capL += TaiwanSea.sc(c, sh).samL() * sh.n;
        boolean dry = capL > 0 && g.samL < capL * (g.side == US ? 0.4 : 0.25);
        boolean warships = TaiwanSea.countRole(c, g, "destroyer") + TaiwanSea.countRole(c, g, "cruiser") + TaiwanSea.countRole(c, g, "frigate") > 0;
        boolean out = warships && !TaiwanSea.amphib(c, g) && !TaiwanSea.coastGuard(c, g) && TaiwanSea.offensive(c, g) == 0;
        boolean hurt = g.fit() < g.alive() * 0.5;
        for (TaiwanState.Ship sh : g.ships) { TaiwanData.ShipClass k = TaiwanSea.sc(c, sh); if ((k.carrier() || k.amphib()) && (sh.mk || sh.dmg > 0.3)) hurt = true; }
        return dry || out || hurt;
    }

    static void naval(Ctx c, int side, boolean taiwan) {
        TaiwanState s = c.s;
        for (Group g : s.groups) {
            if (g.side != side || g.dead() || g.hub >= 0 || g.gone || g.held) continue;
            if (side == US && g.nat.equals("TWN") != taiwan) continue;
            if (side == PRC && TaiwanSea.amphib(c, g)) continue;
            if (TaiwanSea.hasRole(c, g, "pier") || TaiwanSea.hasRole(c, g, "roro")) continue;
            if (g.mission == M_RETURN || g.mission == M_WITHDRAW) continue;
            if (s.atWar() && side == US && crippled(c, g)) { TaiwanSea.withdraw(c, g); continue; }
            if (g.port >= 0 && g.rearmUntil > s.hour) continue;
            if (g.mission != M_IDLE && s.atWar() && needsPort(c, g)) {
                int p = TaiwanSea.rearmPort(c, g);
                if (p >= 0) { TaiwanSea.orderPort(c, g, p); c.log(g.name + " returns to " + c.d.bases.get(p).name() + " to rearm and repair."); }
                continue;
            }
            if (s.atWar() && !TaiwanSea.coastGuard(c, g)) g.emcon = true;
            int[] want = side == PRC ? redNaval(c, g) : taiwan ? taiwanNaval(c, g) : blueNaval(c, g);
            if (want == null) continue;
            int z = want[0], mission = want[1];
            if (z < 0) {
                if (g.port < 0 && mission == M_IDLE) {
                    int p = g.homePort >= 0 && TaiwanSea.portFor(c, g.homePort, g.side, g.nat) ? g.homePort : TaiwanSea.nearestPort(c, side, g.nat, TaiwanSea.gx(c, g), TaiwanSea.gy(c, g));
                    if (p >= 0) TaiwanSea.orderPort(c, g, p);
                }
                continue;
            }
            if (g.zone == z && g.mission == mission && (g.place == z || g.place < 0 && g.to == z)) continue;
            TaiwanSea.orderZone(c, g, z, mission);
        }
    }

    static int[] blueNaval(Ctx c, Group g) {
        TaiwanState s = c.s;
        boolean war = s.atWar();
        boolean active = TaiwanAir.nationActive(c, g.nat, US);
        int w = s.warnLevel[US];
        String t = g.tag;
        if (g.nat.equals("USA")) {
            if (t.startsWith("csg")) {
                if (!war) {
                    if (s.fleet == 2) return new int[]{zone(c, "GUAM"), M_PATROL};
                    if (t.equals("csg_south") && w >= 2) return new int[]{zone(c, "SCS_C"), M_PATROL};
                    if (TaiwanWorld.progOn(c, US, "showforce") && !TaiwanWorld.progDone(c, US, "showforce") || s.fleet == 0 && w >= 2) return new int[]{zone(c, "PS_W"), M_PATROL};
                    if (w >= 2 && g.port >= 0) return new int[]{zone(c, "PS_N"), M_PATROL};
                    return null;
                }
                boolean stand;
                switch (s.fleet) {
                    case 0 -> stand = false;
                    case 1 -> stand = true;
                    case 2 -> { return new int[]{zone(c, "GUAM"), M_PATROL}; }
                    default -> {
                        double asbm = s.pool[TaiwanData.P_PRC][c.d.munition("DF21D")] + s.pool[TaiwanData.P_PRC][c.d.munition("DF26")];
                        for (Battery b : s.batteries) if (b.side == PRC && b.alive && (c.d.land.get(b.sys).id().equals("DF21D") || c.d.land.get(b.sys).id().equals("DF26"))) asbm += b.ready;
                        stand = asbm > 250 || s.rung[PRC] < 5;
                    }
                }
                return new int[]{zone(c, stand ? (t.equals("csg_fwd") ? "PS_N" : "PS_C") : "PS_W"), M_PATROL};
            }
            if (t.equals("desron")) {
                if (war) return new int[]{zone(c, s.blockade >= 2 && !s.usCombat ? "EAST" : "OKINAWA"), s.blockade >= 2 && s.rung[US] <= 2 && s.convoys ? M_CONVOY : M_PATROL};
                return w >= 2 ? new int[]{zone(c, "OKINAWA"), M_PATROL} : null;
            }
            if (t.equals("sag")) return war ? new int[]{zone(c, s.usCombat ? "EAST" : "PS_W"), s.usCombat || !s.convoys ? M_PATROL : M_CONVOY} : new int[]{zone(c, "PS_C"), M_PATROL};
            if (t.equals("esg")) return war ? new int[]{zone(c, "PS_N"), M_PATROL} : null;
            if (t.equals("lcs")) return war || w >= 2 ? new int[]{zone(c, "GUAM"), M_PATROL} : null;
            return null;
        }
        if (g.nat.equals("JPN")) {
            String[] z = {"ECS_S", "OKINAWA", "YONAGUNI", "MIYAKO"};
            if (active) return new int[]{zone(c, z[g.id % z.length]), M_PATROL};
            if (TaiwanWorld.country(c, "JPN").level() >= 4 && (w >= 3 || war)) return new int[]{zone(c, z[1 + g.id % 3]), M_PATROL};
            return null;
        }
        if (!active) return null;
        return switch (g.nat) {
            case "KOR" -> new int[]{zone(c, "YELLOW"), M_PATROL};
            case "PHL" -> new int[]{zone(c, "WLUZON"), M_PATROL};
            default -> new int[]{zone(c, "PS_C"), M_PATROL};
        };
    }

    static int[] taiwanNaval(Ctx c, Group g) {
        TaiwanState s = c.s;
        boolean war = s.atWar() && (s.rung[PRC] >= 3 || s.rung[US] >= 2);
        int w = s.warnLevel[US];
        String t = g.tag;
        if (t.equals("cg")) return null;
        if (!war) {
            if (w >= 3 || s.blockade >= 1 || s.twDisperseAsked) {
                if (t.equals("tw_ddg") || t.equals("tw_ffg")) return new int[]{zone(c, "EAST"), M_PATROL};
                if (s.twDisperseAsked) return new int[]{zone(c, "EAST"), M_PATROL};
            }
            return null;
        }
        boolean landed = false;
        for (int i = 0; i < s.sectors.size(); i++) if (c.d.sectors.get(i).terrain() != 3 && TaiwanLand.bns(s, i, PRC) > 0.3) landed = true;
        if (s.rung[PRC] <= 3 && !landed) {
            if (t.equals("tw_ddg") && s.blockade >= 2) return new int[]{zone(c, "EAST"), M_PATROL};
            return s.twDisperseAsked ? new int[]{zone(c, "EAST"), M_PATROL} : new int[]{-1, M_IDLE};
        }
        return switch (t) {
            case "tw_ddg" -> new int[]{zone(c, "EAST"), M_PATROL};
            case "tw_ffg" -> new int[]{zone(c, "BASHI"), M_PATROL};
            case "tw_ffl" -> new int[]{zone(c, "NE"), M_PATROL};
            case "tw_cor", "tw_fac_n" -> new int[]{zone(c, "TS_N"), M_PATROL};
            case "tw_fac_s" -> new int[]{zone(c, "PENGHU"), M_PATROL};
            default -> null;
        };
    }

    static int[] redNaval(Ctx c, Group g) {
        TaiwanState s = c.s;
        boolean war = s.atWar();
        String t = g.tag;
        boolean exercise = TaiwanWorld.progOn(c, PRC, "exercise");
        if (t.equals("russia")) return TaiwanWorld.country(c, "RUS").stance < -60 && (war || exercise) ? new int[]{zone(c, "SOJ"), M_PATROL} : null;
        if (t.equals("cg")) {
            if (g.decoy) return null;
            String[] zones = {"TS_C", "TS_N", "TS_S", "EAST", "BASHI", "NE", "PENGHU"};
            int z = zone(c, zones[g.id % zones.length]);
            if (war && s.blockade >= 1) return new int[]{z, s.blockade >= 2 ? M_BLOCKADE : M_QUARANTINE};
            if (TaiwanWorld.progDone(c, PRC, "militia") || exercise) return new int[]{zone(c, zones[g.id % 3]), M_PATROL};
            return null;
        }
        if (!war) {
            if (!exercise && !TaiwanWorld.progOn(c, PRC, "subs")) return null;
            if (t.startsWith("cv")) return new int[]{zone(c, t.equals("cv_north") ? "ECS_S" : "SCS_N"), M_PATROL};
            if (t.startsWith("sag_east") || t.equals("escort") || t.startsWith("mb")) return new int[]{zone(c, t.startsWith("mb") ? "TS_C" : "TS_N"), M_PATROL};
            return null;
        }
        String strait = s.region == 0 ? "TS_N" : s.region == 1 ? "TS_C" : "TS_S";
        return switch (t) {
            case "cv_north" -> new int[]{zone(c, s.warHours() < 48 ? "ECS_S" : "NE"), M_PATROL};
            case "cv_bashi" -> new int[]{zone(c, "BASHI"), M_PATROL};
            case "cv_south" -> new int[]{zone(c, s.difficultyPRC == 2 && s.warHours() > 72 ? "PS_W" : "SCS_N"), M_PATROL};
            case "sag_east1" -> new int[]{zone(c, "EAST"), M_PATROL};
            case "sag_east2" -> new int[]{zone(c, "NE"), M_PATROL};
            case "sag_north" -> new int[]{zone(c, "ECS_S"), M_PATROL};
            case "sag_south" -> new int[]{zone(c, "BASHI"), M_PATROL};
            case "escort", "asw" -> new int[]{zone(c, strait), M_SCREEN};
            case "asw_south" -> new int[]{zone(c, "TS_S"), M_SCREEN};
            case "mb" -> new int[]{zone(c, strait), M_PATROL};
            case "mb_penghu" -> new int[]{zone(c, "PENGHU"), M_PATROL};
            default -> new int[]{zone(c, strait), M_SCREEN};
        };
    }

    // ---- submarines ----------------------------------------------------------------------------------------------------

    static void subs(Ctx c, int side, boolean taiwan) {
        TaiwanState s = c.s;
        boolean go = side == PRC ? s.atWar() || TaiwanWorld.progOn(c, PRC, "subs")
                : s.atWar() || TaiwanWorld.progOn(c, US, "subs") || s.warnLevel[US] >= 2;
        if (!go) return;
        String[] usList = switch (s.subFocus) {
            case 0 -> new String[]{"TS_S", "TS_N", "TS_C", "PENGHU", "BASHI"};
            case 1 -> new String[]{"BASHI", "LUZON", "YONAGUNI", "MIYAKO", "ECS_S", "NE"};
            case 2 -> new String[]{"SCS_N", "SCS_C", "ECS_N", "YELLOW", "WLUZON"};
            default -> new String[]{"TS_S", "TS_N", "BASHI", "ECS_S", "SCS_N", "TS_C", "EAST", "YONAGUNI", "LUZON", "ECS_N", "YELLOW"};
        };
        String[] redList = {"EAST", "BASHI", "PS_W", "MIYAKO", "LUZON", "NE", "OKINAWA", "GUAM", "YONAGUNI", "PS_N", "SCS_N"};
        int k = 0;
        for (Sub x : s.subs) {
            if (x.side != side || x.lost || x.hub >= 0 || x.held) continue;
            if (side == US && x.nat.equals("TWN") != taiwan) continue;
            if (!TaiwanAir.nationActive(c, x.nat, side) && !(x.nat.equals("USA") || x.nat.equals("CHN") || x.nat.equals("TWN"))) {
                if (!x.nat.equals("JPN") || TaiwanWorld.country(c, "JPN").level() < 4) continue;
            }
            if (x.mission == SM_PORT && (x.rearmUntil > s.hour || x.torps < 4)) continue;
            if (x.mission == SM_RETURN) continue;
            if (x.mission == SM_PATROL || x.mission == SM_TRANSIT) { k++; continue; }
            String[] list = side == PRC ? redList : usList;
            switch (x.nat) {
                case "JPN" -> list = new String[]{"MIYAKO", "YONAGUNI", "ECS_S", "OKINAWA", "SOJ"};
                case "TWN" -> list = new String[]{"TS_N", "TS_S", "PENGHU"};
                case "KOR" -> list = new String[]{"YELLOW", "SOJ"};
                case "AUS" -> list = new String[]{"SCS_C", "WLUZON"};
                default -> { }
            }
            if (side == PRC && !c.d.subs.get(x.cls).nuclear()) list = new String[]{"EAST", "BASHI", "TS_S", "NE", "YONAGUNI", "TS_N"};
            TaiwanSea.orderSubZone(c, x, zone(c, list[(x.id + k) % list.length]));
            k++;
        }
    }

    // ---- missile batteries ---------------------------------------------------------------------------------------------

    static void fires(Ctx c, int side, boolean taiwan) {
        TaiwanState s = c.s;
        if (!s.atWar()) return;
        if (side == PRC) { firePlan(c); return; }
        for (Battery b : s.batteries) {
            if (b.side != US || !b.alive || b.hub >= 0 || b.held || b.base < 0 || b.ready <= 0 || b.nat.equals("TWN") != taiwan) continue;
            TaiwanData.LandSys ls = c.d.land.get(b.sys);
            if (ls.sam() || b.tKind != TK_NONE) continue;
            Munition mu = c.d.munitions.get(ls.munition());
            if (mu.antiShip() || !TaiwanAir.nationActive(c, b.nat, US)) continue;
            Base bb = c.d.bases.get(b.base);
            if (ls.id().equals("HIMARS") || ls.id().equals("HIMARS_TW")) {
                int sec = TaiwanLand.hottest(s);
                if (sec >= 0) {
                    TaiwanData.Sector sd = c.d.sectors.get(sec);
                    if (TaiwanSea.dist(bb.x(), bb.y(), sd.x(), sd.y()) <= mu.range() && TaiwanFire.allowed(c, US, TK_SECTOR, sec, ls.munition())) {
                        b.tKind = TK_SECTOR; b.tId = sec; b.salvo = 4;
                        continue;
                    }
                }
            }
            if (s.rung[US] >= 4) {
                int t = mainlandTarget(c, new double[]{bb.x(), bb.y()}, mu.range());
                if (t >= 0 && TaiwanFire.allowed(c, US, TK_BASE, t, ls.munition())) {
                    b.tKind = TK_BASE; b.tId = t; b.salvo = Math.max(2, Math.min(b.ready, b.launchers * ls.rounds() / 2));
                }
            }
        }
    }

    record Tgt(int kind, int id, int want, boolean ship) {}

    /** The PLA's missile campaign (spec 4.3): a target list with the missiles each needs, then batteries in two passes. */
    static void firePlan(Ctx c) {
        TaiwanState s = c.s;
        double wh = s.warHours();
        int tl = Math.max(1, TaiwanEngine.turnLength(s));
        double scale = wh < 24 ? 1.0 : tl / 12.0;
        List<Tgt> list = new ArrayList<>();
        int df21d = c.d.munition("DF21D"), srbm = c.d.munition("DF11"), phl = c.d.munition("PHL16"), df26 = c.d.munition("DF26");
        for (Contact k : s.contacts.get(PRC).values()) {
            if (k.kind != TK_GROUP || s.hour - k.seen > 1.01) continue;
            Group g = c.group(k.id);
            if (value(c, k) < 4 || k.err > 30 || k.ident < 1 || !TaiwanFire.allowed(c, PRC, TK_GROUP, k.id, df21d)) continue;
            list.add(new Tgt(TK_GROUP, k.id, g != null && TaiwanSea.carrierGroup(c, g) ? 16 : 10, true));
        }
        if (s.rung[PRC] >= 3) {
            for (int i = 0; i < c.d.bases.size(); i++) {
                Base d = c.d.bases.get(i);
                if (!TaiwanFire.allowed(c, PRC, TK_BASE, i, srbm)) continue;
                TaiwanState.BaseState b = s.bases.get(i);
                if (b.captured) continue;
                int want = 0;
                if (d.cc().equals("TWN")) {
                    if (d.has(TaiwanData.F_AIR)) want = wh < 6 ? 18 : TaiwanAir.runwayOpen(c, i) ? (int) (10 * scale) : 0;
                    if (d.has(TaiwanData.F_C2)) want = Math.max(want, wh < 6 && (s.optDecap || d.id().equals("b_taipei_military_command_centre")) ? 8 : 0);
                    if (d.has(TaiwanData.F_NAVAL)) want = Math.max(want, wh < 12 ? 8 : 0);
                    if (d.has(TaiwanData.F_ENERGY) && (s.plan == 1 || s.plan == 4)) want = Math.max(want, (int) (6 * scale));
                    if (s.plan == 3 && !(d.region().equals("Kinmen") || d.region().equals("Matsu") || d.region().equals("Penghu"))) want = 0;
                } else {
                    int ac = TaiwanAir.aircraftAt(c, i);
                    if (d.has(TaiwanData.F_AIR) && ac > 0) want = wh < 3 ? 20 : TaiwanAir.runwayOpen(c, i) ? (int) (Math.min(24, 6 + ac / 6.0) * scale) : 0;
                    if (d.has(TaiwanData.F_NAVAL)) {
                        int ships = 0;
                        for (Group g : s.groups) if (g.port == i && g.side == US && !g.dead()) ships += g.alive();
                        if (ships > 0) want = Math.max(want, 6 + ships * 2);
                    }
                    // Japanese bases of the JSDF at H-hour, if the planner chose to strike Japan (an option the source never read, spec 13.2)
                    if (s.optStrikeJapan && wh < 3 && d.cc().equals("JPN") && d.has(TaiwanData.F_AIR) && !d.has(TaiwanData.F_CIVIL)) want = Math.max(want, 12);
                }
                if (want > 0) list.add(new Tgt(TK_BASE, i, want, false));
            }
            if (s.rung[PRC] >= 4 && wh < 36)
                for (Battery b : s.batteries)
                    if (b.side == US && b.alive && b.nat.equals("TWN") && b.hub < 0 && b.base >= 0 && TaiwanFire.allowed(c, PRC, TK_LAND, b.id, srbm))
                        list.add(new Tgt(TK_LAND, b.id, c.d.land.get(b.sys).sam() ? 6 : 4, false));
        }
        for (int sec = 0; sec < s.sectors.size(); sec++) {
            double rb = TaiwanLand.bns(s, sec, PRC), bb = TaiwanLand.bns(s, sec, US);
            boolean prep = s.plan == 0 && wh < 18 && landingSector(c, sec);
            if ((rb > 0.3 && bb > 0.3 || prep) && TaiwanFire.allowed(c, PRC, TK_SECTOR, sec, phl)) list.add(new Tgt(TK_SECTOR, sec, 24, false));
        }
        boolean[] used = new boolean[s.batteries.size()];
        for (Tgt t : list) {
            double[] p = TaiwanFire.targetPos(c, PRC, t.kind, t.id);
            if (p == null) continue;
            int need = t.want;
            for (int pass = 0; pass < 2 && need > 0; pass++) {
                for (int i = 0; i < s.batteries.size() && need > 0; i++) {
                    Battery b = s.batteries.get(i);
                    if (used[i] || b.side != PRC || !b.alive || b.hub >= 0 || b.held || b.base < 0 || b.ready <= 0 || b.tKind != TK_NONE) continue;
                    TaiwanData.LandSys ls = c.d.land.get(b.sys);
                    if (ls.sam()) continue;
                    int m = ls.munition();
                    Munition mu = c.d.munitions.get(m);
                    if (t.ship && !mu.antiShip()) continue;
                    if (t.ship && (m == df26 || m == df21d) && !b.asbm) continue;
                    if (!t.ship && b.asbm) continue;
                    if (!t.ship && t.kind != TK_SECTOR && !mu.land()) continue;
                    if (t.kind == TK_SECTOR && m != phl) continue;
                    if (t.kind != TK_SECTOR && m == phl && (t.kind != TK_BASE || !c.d.bases.get(t.id).cc().equals("TWN"))) continue;
                    Base bb = c.d.bases.get(b.base);
                    double dist = TaiwanSea.dist(bb.x(), bb.y(), p[0], p[1]);
                    if (!t.ship && m == df21d || !t.ship && m == df26 && pass == 0 && dist < 1500) continue;
                    if (dist > mu.range()) continue;
                    if (pass == 0 && mu.range() > 3 * dist && mu.range() > 1500) continue;
                    int n = Math.min(Math.min(b.ready, Math.max(1, b.launchers * ls.rounds())), need);
                    b.tKind = t.kind; b.tId = t.id; b.salvo = n;
                    used[i] = true;
                    need -= n;
                }
            }
        }
    }

    static boolean landingSector(Ctx c, int sec) {
        for (int b : regionBeaches(c, TMath.clampi(c.s.region, 0, 2))) if (c.d.beaches.get(b).sector() == sec) return true;
        return false;
    }

    /** Targets for the PLA's bombers with stand-off missiles. */
    static int landTarget(Ctx c, double[] o, double reach, int w) {
        TaiwanState s = c.s;
        int best = -1;
        double bv = 0;
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base d = c.d.bases.get(i);
            if (!d.has(TaiwanData.F_AIR) && !d.has(TaiwanData.F_NAVAL) && !d.has(TaiwanData.F_ENERGY) && !d.has(TaiwanData.F_C2)) continue;
            if (!TaiwanFire.allowed(c, PRC, TK_BASE, i, w)) continue;
            if (TaiwanSea.dist(o[0], o[1], d.x(), d.y()) > reach) continue;
            double v = d.cc().equals("TWN") ? 1 : 1.6;
            v *= 1 + TaiwanAir.aircraftAt(c, i) / 40.0;
            if (d.has(TaiwanData.F_AIR) && !TaiwanAir.runwayOpen(c, i)) v *= 0.3;
            v *= 1 + c.u(Ctx.R_STAFF) * 0.3;
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }

    /** What the US would hit first on the mainland: embarkation ports with ships, then airfields opposite Taiwan (spec 4.4). */
    static int mainlandTarget(Ctx c, double[] o, double reach) {
        TaiwanState s = c.s;
        int best = -1;
        double bv = 0;
        int jer = c.d.munition("JASSM_ER");
        TaiwanData.Point xm = c.d.point("xiamen");
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base d = c.d.bases.get(i);
            if (!d.cc().equals("CHN") || TaiwanSea.dist(o[0], o[1], d.x(), d.y()) > reach || !TaiwanFire.allowed(c, US, TK_BASE, i, jer)) continue;
            double v = 0;
            if (d.has(TaiwanData.F_NAVAL) || d.has(TaiwanData.F_PORT)) {
                for (Group g : s.groups) if (g.port == i && g.side == PRC && !g.dead()) v += TaiwanSea.amphib(c, g) ? 6 : 2;
                for (Troop t : s.troops) if (t.side == PRC && t.state == GS_STAGING && t.stage == i) v += 0.3 * t.bns;
            }
            if (d.has(TaiwanData.F_AIR) && TaiwanAir.runwayOpen(c, i)) v += TaiwanAir.aircraftAt(c, i) / 12.0;
            if (d.has(TaiwanData.F_MISSILE) && s.rung[US] >= 5) v += 3;
            if (TaiwanSea.dist(d.x(), d.y(), xm.x(), xm.y()) > 900) v *= 0.4;
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }

    // ---- the ground war ------------------------------------------------------------------------------------------------

    static void ground(Ctx c, int side) {
        TaiwanState s = c.s;
        if (!s.atWar() || s.hour - s.groundHour1 < 6) return;
        s.groundHour1 = s.hour;
        for (Troop t : new ArrayList<>(s.troops)) {
            if (t.side != PRC || t.state != GS_READY || t.held || t.sector < 0 || t.moveTo >= 0) continue;
            TaiwanState.SectorState ss = s.sectors.get(t.sector);
            if (ss.ctrl < 0.85 || t.bns < 1 || t.supply < 0.45) continue;
            int best = -1;
            double bv = 0;
            for (int nb : c.d.sectors.get(t.sector).adj()) {
                if (c.d.sectors.get(nb).terrain() == 2 && c.u(Ctx.R_GROUND) < 0.7) continue;
                double v = TaiwanLand.objectiveValue(c, nb) * (1.2 - s.sectors.get(nb).ctrl);
                double def = TaiwanLand.power(s, nb, US), mine = TaiwanLand.power(s, nb, PRC) + t.power();
                v *= TMath.clamp(mine / (def + 0.5), 0.2, 2);
                if (v > bv) { bv = v; best = nb; }
            }
            if (best >= 0 && bv > 0.6) {
                Troop d = t.bns > 2 ? TaiwanLand.split(c, t, t.bns * 0.6) : t;
                d.moveTo = best;
            }
        }
    }

    static void taiwanGround(Ctx c) {
        TaiwanState s = c.s;
        int n = s.sectors.size();
        double[] threat = new double[n];
        for (int i = 0; i < n; i++) threat[i] = TaiwanLand.power(s, i, PRC) * 1.5 - TaiwanLand.power(s, i, US) * TaiwanData.TERRAIN_DEF[c.d.sectors.get(i).terrain()];
        for (Troop t : s.troops) {
            if (t.side != US || t.state != GS_READY || t.held || t.sector < 0 || t.moveTo >= 0) continue;
            if (TaiwanLand.isType(t, "garrison") || c.d.sectors.get(t.sector).terrain() == 3) continue;
            if (threat[t.sector] > -1) continue;
            if (t.key.equals("tw_capital") && c.d.sectors.get(t.sector).id().equals("s_taipei")) continue;
            int target = -1;
            double best = 0.5;
            for (int i = 0; i < n; i++) {
                if (threat[i] <= 0.5 || c.d.sectors.get(i).terrain() == 3) continue;
                int hops = hops(c, t.sector, i);
                if (hops < 0 || hops > 4) continue;
                double v = threat[i] / (1 + hops);
                if (v > best) { best = v; target = i; }
            }
            if (target < 0) continue;
            int step = nextStep(c, t.sector, target);
            if (step < 0) continue;
            boolean frontier = false;
            for (int nb : c.d.sectors.get(t.sector).adj()) if (threat[nb] > 0.5 && nb != target) frontier = true;
            if (frontier && c.u(Ctx.R_GROUND) < 0.6) continue;
            t.moveTo = step;
            threat[target] -= t.power() * TaiwanData.TERRAIN_DEF[c.d.sectors.get(target).terrain()];
        }
    }

    static int hops(Ctx c, int a, int b) {
        if (a == b) return 0;
        int n = c.d.sectors.size();
        int[] dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        List<Integer> q = new ArrayList<>();
        q.add(a);
        dist[a] = 0;
        for (int i = 0; i < q.size(); i++) {
            int x = q.get(i);
            for (int nb : c.d.sectors.get(x).adj()) {
                if (dist[nb] >= 0) continue;
                dist[nb] = dist[x] + 1;
                if (nb == b) return dist[nb];
                q.add(nb);
            }
        }
        return -1;
    }

    static int nextStep(Ctx c, int a, int b) {
        int best = -1, bd = 99;
        for (int nb : c.d.sectors.get(a).adj()) {
            int d = nb == b ? 0 : hops(c, nb, b);
            if (d >= 0 && d < bd) { bd = d; best = nb; }
        }
        return best;
    }

    // ---- the amphibious planner (spec 4.3) -----------------------------------------------------------------------------

    static int stagePortFor(Ctx c, Group g, int reg) {
        if (g.tag.equals("amph_south")) return c.d.base(reg == 0 ? "b_quanzhou_port" : "b_xiamen_naval_base");
        if (g.tag.equals("amph_76") || g.tag.equals("lighter")) return c.d.base("b_fuzhou_luoyuan_bay");
        return c.d.base(STAGE_PORT[reg]);
    }

    static void amphib(Ctx c) {
        TaiwanState s = c.s;
        if (s.plan != 0 && s.plan != 3) return;
        int dd = s.dDay, day = s.day();
        int reg = TMath.clampi(s.region, 0, 2);
        int mainBeach = mainLodgedBeach(c);
        int penghu = c.d.need("bc:beach_penghu_beaches");
        for (Group g : s.groups) {
            if (g.side != PRC || g.dead() || g.held || g.hub >= 0 || g.gone) continue;
            boolean amph = TaiwanSea.amphib(c, g);
            boolean roro = TaiwanSea.hasRole(c, g, "roro") && !amph;
            boolean pier = TaiwanSea.hasRole(c, g, "pier");
            if (!amph && !roro && !pier) continue;
            boolean islandGroup = s.plan == 3 || s.optPenghu && (g.tag.equals("lighter") || g.tag.equals("amph_76"));
            if (s.phase.equals("crisis")) {
                if (dd <= 0) continue;
                int stage = stagePortFor(c, g, reg);
                if (day >= dd - 5 && g.port != stage && g.mission != M_RETURN && amph) { TaiwanSea.orderPort(c, g, stage); g.homePort = stage; }
                if (day >= dd - 2 && g.port >= 0 && g.cargo.isEmpty() && !pier) TaiwanLand.embark(c, g);
                continue;
            }
            double wh = s.warHours();
            if (pier) {
                if (g.place >= 0 && g.mission != M_ASSAULT && mainBeach >= 0) TaiwanSea.orderBeach(c, g, mainBeach);
                continue;
            }
            if (g.mission == M_ASSAULT) {
                if (g.place >= 0 && g.cargo.isEmpty() && g.beachHours > 8) {
                    int hp = g.homePort >= 0 ? g.homePort : stagePortFor(c, g, reg);
                    if (!TaiwanLand.staged(c, PRC, hp, 420).isEmpty() || waveReady(c, hp)) { TaiwanSea.orderPort(c, g, hp); g.shuttling = false; }
                }
                continue;
            }
            if (g.mission == M_RETURN || g.mission == M_MOVE || g.place < 0) continue;
            if (g.port < 0) { TaiwanSea.orderPort(c, g, g.homePort >= 0 ? g.homePort : stagePortFor(c, g, reg)); continue; }
            if (g.cargo.isEmpty()) {
                if (g.readyAt > s.hour) continue;
                if (TaiwanLand.embark(c, g) <= 0) {
                    int alt = portWithTroops(c);
                    if (alt >= 0 && alt != g.port && TaiwanLand.baseDist(c, alt, g.port) > 420) { TaiwanSea.orderPort(c, g, alt); g.homePort = alt; }
                }
                continue;
            }
            if (g.readyAt > s.hour) continue;
            int fac = capturedPort(c);
            if (roro) {
                if (fac >= 0 && c.d.facilities.get(fac).place() >= 0) { TaiwanSea.orderFacility(c, g, fac); continue; }
                int pb = pierBeach(c);
                if (pb >= 0) TaiwanSea.orderBeach(c, g, pb);
                continue;
            }
            int beach;
            if (s.plan == 3) { beach = islandBeach(c, g); if (beach < 0) continue; }
            else if (islandGroup && wh < 72) beach = penghu;
            else if (mainBeach >= 0 && wh > 12) beach = mainBeach;
            else beach = regionBeaches(c, reg)[g.id % 2];
            TaiwanSea.orderBeach(c, g, beach);
            c.log(g.name + " sails for " + c.d.beaches.get(beach).name() + " with " + TaiwanLand.one(TaiwanLand.cargoBns(c, g)) + " battalions.");
        }
    }

    static int islandBeach(Ctx c, Group g) {
        TaiwanState s = c.s;
        int best = -1;
        double bv = 1e18;
        double gx = TaiwanSea.gx(c, g), gy = TaiwanSea.gy(c, g);
        for (String id : new String[]{"beach_kinmen_beaches", "beach_penghu_beaches", "beach_matsu_beaches"}) {
            int b = c.d.need("bc:" + id);
            TaiwanData.Beach bd = c.d.beaches.get(b);
            if (s.sectors.get(bd.sector()).ctrl >= 0.95) continue;
            int already = 0;
            for (Group o : s.groups) if (o != g && o.side == PRC && !o.dead() && o.mission == M_ASSAULT && o.beach == b) already++;
            double sc = TaiwanSea.dist(gx, gy, bd.x(), bd.y()) + already * 250 - TaiwanLand.bns(s, bd.sector(), US) * 20;
            if (sc < bv) { bv = sc; best = b; }
        }
        return best;
    }

    static int mainLodgedBeach(Ctx c) {
        int best = -1;
        double bv = 0.3;
        for (int b = 0; b < c.d.beaches.size(); b++) {
            TaiwanData.Beach bd = c.d.beaches.get(b);
            if (c.d.sectors.get(bd.sector()).terrain() == 3) continue;
            double v = TaiwanLand.bns(c.s, bd.sector(), PRC) * bd.quality();
            if (v > bv) { bv = v; best = b; }
        }
        return best;
    }

    static int pierBeach(Ctx c) {
        for (Group g : c.s.groups) if (g.side == PRC && !g.dead() && TaiwanSea.hasRole(c, g, "pier") && g.mission == M_ASSAULT && g.place >= 0 && g.beach >= 0) return g.beach;
        return -1;
    }

    static boolean waveReady(Ctx c, int port) {
        for (Troop t : c.s.troops) if (t.side == PRC && t.state == GS_RESERVE && t.mobilizeAt >= 0 && t.stage == port) return true;
        return false;
    }

    static int portWithTroops(Ctx c) {
        int best = -1;
        double bv = 0.5;
        for (int i = 0; i < c.d.bases.size(); i++) {
            Base d = c.d.bases.get(i);
            if (!d.cc().equals("CHN") || !d.has(TaiwanData.F_PORT) && !d.has(TaiwanData.F_NAVAL) || d.place() < 0) continue;
            double v = 0;
            for (Troop t : c.s.troops)
                if (t.side == PRC && t.state == GS_STAGING && t.stage == i && !TaiwanLand.isType(t, "airborne") && !TaiwanLand.isType(t, "airassault")) v += t.bns;
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }

    static int capturedPort(Ctx c) {
        int best = -1;
        double bv = 0;
        for (int f = 0; f < c.d.facilities.size(); f++) {
            TaiwanData.Facility fd = c.d.facilities.get(f);
            TaiwanState.FacilityState fs = c.s.facilities.get(f);
            if (!fd.port() || fs.owner != PRC || fs.damage > 0.65) continue;
            double v = fd.kt() * (1 - fs.damage);
            if (v > bv) { bv = v; best = f; }
        }
        return best;
    }

    static int capturedAirfield(Ctx c) {
        for (int f = 0; f < c.d.facilities.size(); f++) if (!c.d.facilities.get(f).port() && c.s.facilities.get(f).owner == PRC && c.s.facilities.get(f).damage < 0.7) return f;
        return -1;
    }

    static int dropSector(Ctx c, Squadron q) {
        TaiwanState s = c.s;
        double[] o = TaiwanAir.origin(c, q);
        if (o == null) return -1;
        int best = -1;
        double bv = 0;
        for (int i = 0; i < s.sectors.size(); i++) {
            TaiwanData.Sector sd = c.d.sectors.get(i);
            if (s.plan == 3 ? sd.terrain() != 3 : sd.terrain() == 3 || sd.terrain() == 2) continue;
            if (TaiwanSea.dist(o[0], o[1], sd.x(), sd.y()) > TaiwanAir.at(c, q).radius()) continue;
            double red = TaiwanLand.power(s, i, PRC), blue = TaiwanLand.power(s, i, US) * TaiwanData.TERRAIN_DEF[sd.terrain()];
            boolean near = red > 0.5;
            for (int nb : sd.adj()) if (TaiwanLand.bns(s, nb, PRC) > 1) near = true;
            if (!near && !(landingSector(c, i) && s.warHours() >= 6)) continue;
            double v = (red + 1) / (blue + 1) * TaiwanLand.objectiveValue(c, i);
            if (v > bv) { bv = v; best = i; }
        }
        return best;
    }
}
