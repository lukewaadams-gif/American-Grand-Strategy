package game.mandate.core.foreign;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Fmt;
import game.mandate.core.effects.Effect;
import game.mandate.core.foreign.ForeignData.Country;
import game.mandate.core.foreign.ForeignData.Crisis;
import game.mandate.core.foreign.ForeignData.CrisisOption;
import game.mandate.core.foreign.ForeignData.ObjectiveDef;
import game.mandate.core.foreign.ForeignData.Step;
import game.mandate.core.foreign.ForeignState.Objective;
import game.mandate.core.foreign.ForeignState.Question;
import game.mandate.core.foreign.ForeignState.War;
import game.mandate.core.news.News;
import game.mandate.core.news.RdFx;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static game.mandate.core.foreign.ForeignData.*;

/**
 * The world, ported from Resolute Desk Version 2 ({@code world/Diplo}, {@code Statecraft}, {@code Objectives},
 * {@code Summit}, {@code Wars}, the war loop of {@code history/History}, and the Daily Brief of {@code HomeFront}):
 * forty countries and their relations, calls, invitations, visits and summits; the hot spots week by week; crises with
 * advisers and odds; military operations and covert action; trade, sanctions and aid; the Statecraft tools; alliances
 * and force posture; wars of choice; objectives; and the President's Daily Brief. What the world does to the rest of
 * the game leaves as effects for the game engine to route.
 *
 * <p>Two departures from Resolute Desk: the Taiwan war is fought on this game's operational model (the world stops
 * Taiwan's clock while it runs, and the game engine reports how it ended), and oil moves through this game's markets
 * (the hot spots set the risk premium; an "oil" effect is a price shock).
 */
public final class ForeignEngine {
    /** A bloc in Congress, for the votes on force, trade deals and treaties. */
    public record Bloc(Party party, int seats, double loyalty, double hawk, double rel) {}

    /**
     * What the world reads from the rest of the game. Ratings are Resolute Desk's 0-100 scale; qualities 0-1
     * ({@code pq} for the cabinet, {@code sq} for the staff), readiness 5-100.
     */
    public record Context(LocalDate date, long seed, Party president, String presidentName, double cha, double fpc, double spe, double age,
                          double approval, double polarization, double pqDef, double pqState, double pqCia, double pqDni, double pqDhs, double pqDoj,
                          double pqHhs, double sqNsa, double sqCos, double sqComms, double sqPress, double readyDef, double readyDhs, double readyDoj,
                          double readyHhs, List<Bloc> house, List<Bloc> senate, boolean houseMajority, boolean senateMajority, double priceLevel,
                          String adviserNames) {}

    public record Outcome(List<Effect> effects, List<News> news, List<String> log, boolean[] taiwanWar) {
        public static Outcome empty() { return new Outcome(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new boolean[1]); }
        /** The Taiwan Strait has gone to war this week: the game engine opens its war. */
        public boolean startsTaiwanWar() { return taiwanWar[0]; }
    }

    final ForeignData data;

    public ForeignEngine(ForeignData data) { this.data = data; }

    public ForeignData data() { return data; }

    // =========================================================================================
    //  Setup (Resolute Desk's Diplo.setup and Statecraft.setup, the 2029 values)
    // =========================================================================================

    public ForeignState newWorld(Context c, int week, boolean realLeaders, java.util.Map<String, String> realNames) {
        ForeignState s = new ForeignState();
        s.week = week;
        for (int i = 0; i < NC; i++) {
            Country k = data.country(i);
            s.rel[i] = s.base[i] = k.relations();
            s.align[i] = k.align();
            s.leader[i] = realLeaders && realNames.containsKey(k.leader()) ? realNames.get(k.leader()) : k.leader();
            s.title[i] = k.title();
            s.lastCall[i] = -10; s.visitAt[i] = -1;
            s.ties[i] = 2; s.unrest[i] = 15;
        }
        for (String[] x : SANCTIONS) s.sanction[data.index(x[0])] = Integer.parseInt(x[1]);
        for (String[] x : AID) s.aid[data.index(x[0])] = Integer.parseInt(x[1]);
        for (String x : NO_TIES) s.ties[data.index(x)] = 0;
        s.ties[data.index("TW")] = 1;
        for (String[] x : BASES) s.troopsIn[data.index(x[0])] = Double.parseDouble(x[1]);
        for (String[] x : SUPPORT) { int i = data.index(x[0]); s.milAid[i] = Integer.parseInt(x[1]); s.arms[i] = Integer.parseInt(x[2]); }
        for (String[] x : UNREST) s.unrest[data.index(x[0])] = Double.parseDouble(x[1]);
        refreshObjectives(s, c);
        brief(s, c);
        return s;
    }

    // =========================================================================================
    //  The odds (Resolute Desk's Diplo.p) and its words
    // =========================================================================================

    public double p(Context c, String kind, double base) {
        if (base >= 1) return 1;
        double fpc = kind.equals("m") || kind.equals("d") || kind.equals("i") ? (c.fpc() - 60) / 250 : 0;
        double m = switch (kind) {
            case "m" -> (c.readyDef() - 60) / 300 + (c.pqDef() - 0.6) * 0.2;
            case "d" -> (c.pqState() - 0.6) * 0.3 + (c.sqNsa() - 0.6) * 0.15;
            case "i" -> ((c.pqCia() + c.pqDni()) / 2 - 0.6) * 0.35;
            case "h" -> (c.readyDhs() - 60) / 250 + (c.pqDhs() - 0.6) * 0.25;
            case "j" -> (c.pqDoj() - 0.6) * 0.3 + (c.readyDoj() - 60) / 400;
            case "x" -> (c.readyHhs() - 60) / 250 + (c.pqHhs() - 0.6) * 0.25;
            case "c" -> (c.sqComms() + c.sqPress() - 1.2) * 0.15 + (c.spe() - 60) / 250;
            default -> (c.sqCos() - 0.6) * 0.15;
        };
        return clamp(base + fpc + m, 0.05, 0.97);
    }

    public static String oddsText(double q) {
        if (q >= 0.99) return "This one is in our hands.";
        int n = (int) Math.round(q * 100);
        if (q >= 0.75) return "I think it works: maybe " + n + "%.";
        if (q >= 0.5) return "Better than even, about " + n + "%.";
        return "A long shot, maybe " + n + "%.";
    }

    // =========================================================================================
    //  The week (Resolute Desk's Diplo.week, Statecraft.week, History.wars, the brief)
    // =========================================================================================

    public Outcome week(ForeignState s, Context c, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        for (Question q : new ArrayList<>(s.questions)) if (s.week >= q.due) answer(s, c, q, q.recommended, out, rng);
        statecraftWeek(s, c, out, rng);
        if (c.date().getYear() != s.objectivesYear) refreshObjectives(s, c);
        drift(s, c, out, rng);
        theaters(s, c, out, rng);
        issues(s, out);
        if (s.defcon < 5 && rng.uniform("world.defcon") < 0.25) {
            s.defcon++;
            if (s.defcon == 5) out.news().add(news(c, News.Kind.WORLD, "The Pentagon returns to normal readiness", null, 1.0));
        }
        for (int i = 0; i < s.cool.length; i++) if (s.cool[i] > 0) s.cool[i]--;
        for (int i = 0; i < s.opCool.length; i++) if (s.opCool[i] > 0) s.opCool[i]--;
        for (int i = 0; i < s.cvCool.length; i++) if (s.cvCool[i] > 0) s.cvCool[i]--;
        crises(s, c, out, rng);
        for (int i = 0; i < NC; i++) if (s.visitAt[i] >= 0 && s.week >= s.visitAt[i]) {
            s.visitAt[i] = -1;
            visit(s, c, i, false, out);
            out.effects().add(new Effect.PresidentStrain(-2, 0));
        }
        if (s.summitCountry >= 0 && s.summitRound == 0 && s.week >= s.summitWeek) summitStart(s, c, out);
        multilateral(s, c, out);
        wars(s, c, out, rng);
        brief(s, c);
        s.week++;
        return out;
    }

    // ---- relations (1.3) ----

    void drift(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        double fpc = (c.fpc() - 60) / 4;
        for (int i = 0; i < NC; i++) {
            Country k = data.country(i);
            double target = s.base[i] + s.aid[i] * 4 - s.sanction[i] * 5 - TARIFF_VAL[s.tariff[i]] / 2.0 + fpc
                    + (s.align[i] == AL_TREATY ? ((k.region() == R_EUROPE ? s.nato : s.pacific) - 60) / 8 : 0);
            if (in(new String[]{"TW", "JP", "KR", "PH", "AU"}, k.id())) target += (s.deter - 55) / 4;
            if (k.id().equals("UA")) target += s.aid[i] * 4;
            s.rel[i] = clamp(s.rel[i] + (target - s.rel[i]) * 0.012 + rng.gaussian("world.rel") * 0.4, -100, 100);
        }
        for (int i = 0; i < NC; i++) {
            Country k = data.country(i);
            if (s.tariff[i] >= 2 && k.power() >= 5 && rng.uniform("world.retaliate") < 0.015) {
                out.effects().add(new Effect.FinancialShock(-0.25));
                out.effects().add(new Effect.GroupStanding("rural", -0.6));
                s.rel[i] -= 3;
                out.news().add(news(c, News.Kind.ECON, cap(k.name()) + " retaliates with tariffs on American farm goods", null, 1.4));
            }
        }
    }

    // ---- the hot spots (2.3-2.8) ----

    void theaters(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        int tw = data.index("TW"), cn = data.index("CN"), ua = data.index("UA"), ir = data.index("IR"), ru = data.index("RU"), mx = data.index("MX");
        // Taiwan: still while its war is fought on the operational model
        if (!s.taiwanWar) {
            double detT = 30 + s.posture[0] * 9 + s.aid[tw] * 6 + s.pacific * 0.25 + c.pqDef() * 10;
            s.deter += (detT - s.deter) * 0.03;
            s.twt = clamp(s.twt + 0.10 - (s.deter - 55) * 0.006 - (s.rel[cn] + 30) * 0.002 + rng.gaussian("world.taiwan") * 0.9, 0, 100);
            if (s.twStage < 5 && s.twt > 42 + 12 * s.twStage && rng.uniform("world.taiwan") < 0.05) {
                s.twStage++;
                out.news().add(news(c, s.twStage >= 3 ? News.Kind.WORLD : News.Kind.INFO, ("China escalates around Taiwan: " + TW_STAGE[s.twStage].toLowerCase()).toUpperCase(), null, s.twStage >= 3 ? 3.5 : 2));
                if (s.twStage == 5) { s.taiwanWar = true; out.taiwanWar()[0] = true; s.record.add(stamp(c) + "Chinese forces attacked Taiwan"); }
            } else if (s.twStage > 0 && s.twStage < 5 && s.twt < 26 + 12 * s.twStage && rng.uniform("world.taiwan") < 0.08) {
                s.twStage--;
                out.news().add(news(c, News.Kind.GOOD, "Tensions ease around Taiwan", null, 1.6));
            }
        }
        // Ukraine
        if (s.uaStage == 2) {
            s.ukr = clamp(s.ukr - 0.32 + s.aid[ua] * 0.1 + (s.nato - 60) * 0.003 + (s.posture[1] - 1) * 0.03 + (40 - s.ukr) * 0.004 + rng.gaussian("world.ukraine") * 0.7, 5, 95);
            s.ruGain = clamp(s.ruGain + (50 - s.ukr) * 0.006 + rng.gaussian("world.ukraine") * 0.25, 0, 100);
            s.talks = clamp(s.talks + 0.01 + (s.ukr < 35 ? 0.04 : 0) - (s.talks > 50 ? 0.04 : 0) + rng.gaussian("world.ukraine") * 0.4, 0, 100);
            if (s.talks > 80 && rng.uniform("world.ukraine") < 0.03) {
                s.uaStage = 1;
                out.effects().add(new Effect.Performance(1.5));
                out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.3));
                out.effects().add(new Effect.EconomyShock(0.008, -0.06, 6));
                s.record.add(stamp(c) + "a ceasefire in Ukraine");
                out.news().add(news(c, News.Kind.GOOD, "A CEASEFIRE TAKES HOLD IN UKRAINE", "The guns fall silent along the front for the first time in years.", 4));
            }
        } else if (s.uaStage == 1) {
            s.talks = clamp(s.talks + 0.1 + rng.gaussian("world.ukraine") * 0.5, 0, 100);
            if (s.talks > 95 && rng.uniform("world.ukraine") < 0.02) {
                s.uaStage = 0;
                out.effects().add(new Effect.Performance(2));
                s.record.add(stamp(c) + "a peace agreement in Ukraine");
                out.news().add(news(c, News.Kind.GOOD, "RUSSIA AND UKRAINE SIGN A PEACE AGREEMENT", null, 4));
            } else if (rng.uniform("world.ukraine") < 0.004 + Math.max(0, 50 - s.talks) * 0.0004) {
                s.uaStage = 2; s.talks = 15;
                out.effects().add(new Effect.Performance(-1));
                out.news().add(news(c, News.Kind.BAD, "THE CEASEFIRE IN UKRAINE COLLAPSES", null, 3.5));
            }
        }
        // the Middle East
        s.gaza = clamp(s.gaza + (45 - s.gaza) * 0.01 + rng.gaussian("world.mideast") * 1.2, 0, 100);
        s.iran = clamp(s.iran + 0.12 - s.sanction[ir] * 0.02 + rng.gaussian("world.mideast") * 0.5, 0, 100);
        s.redsea = clamp(s.redsea + (s.gaza * 0.6 + 15 - s.redsea) * 0.02 - s.posture[2] * 0.12 + rng.gaussian("world.mideast") * 1.0, 0, 100);
        s.proxy = clamp(s.proxy + (s.iran * 0.3 + s.gaza * 0.2 - s.proxy) * 0.015 - (s.posture[2] - 1) * 0.15 + rng.gaussian("world.mideast") * 1.0, 0, 100);
        if (s.iran >= 100) {
            s.iran = 99;
            if (rng.uniform("world.mideast") < 0.1) {
                out.effects().add(new Effect.Performance(-2));
                s.iran = 80;
                s.record.add(stamp(c) + "Iran tested a nuclear weapon");
                out.news().add(news(c, News.Kind.BAD, "IRAN TESTS A NUCLEAR WEAPON", "Seismic stations register an underground blast. The region braces.", 5));
            }
        }
        // Korea, terror, cyber, migration, cartels
        double iq = (c.pqCia() + c.pqDni()) / 2;
        s.nk = clamp(s.nk + (55 - s.nk) * 0.01 + 0.04 + rng.gaussian("world.threats") * 1.0, 0, 100);
        s.terror = clamp(s.terror + (30 + s.proxy * 0.15 - s.terror) * 0.02 - (iq - 0.55) * 0.4 + rng.gaussian("world.threats") * 1.0, 0, 100);
        s.cyber = clamp(s.cyber + (42 + (s.rel[ru] < -50 ? 6 : 0) - s.cyber) * 0.02 + rng.gaussian("world.threats") * 1.1, 0, 100);
        s.migr = clamp(s.migr + (48 - (s.rel[mx] - 30) / 4 - s.migr) * 0.02 + rng.gaussian("world.threats") * 1.0, 0, 100);
        s.cartel = clamp(s.cartel + (55 - s.cartel) * 0.01 + rng.gaussian("world.threats") * 0.8, 0, 100);
        // the alliances
        double eu = 0;
        int ne = 0;
        for (int i = 0; i < NC; i++) if (data.country(i).region() == R_EUROPE && s.align[i] == AL_TREATY) { eu += s.rel[i]; ne++; }
        eu = ne == 0 ? 40 : eu / ne;
        double natoT = 40 + (eu - 40) * 0.3 + s.posture[1] * 4 + s.aid[ua] * 2 - (s.allySpend - 2) * 4;
        s.nato = clamp(s.nato + (natoT - s.nato) * 0.02, 0, 100);
        double pacT = 40 + (s.rel[data.index("JP")] + s.rel[data.index("KR")] + s.rel[data.index("AU")] + s.rel[data.index("PH")]) / 4 * 0.3 + s.posture[0] * 4;
        s.pacific = clamp(s.pacific + (pacT - s.pacific) * 0.02, 0, 100);
    }

    /** The supply shock of a Taiwan crisis and the foreign-policy standing the theaters build (9.2, 9.3). */
    void issues(ForeignState s, Outcome out) {
        double ally = 0;
        int n = 0;
        for (int i = 0; i < NC; i++) if (s.align[i] == AL_TREATY) { ally += s.rel[i]; n++; }
        ally = n == 0 ? 55 : ally / n;
        double foreign = (ally - 55) * 0.0002 + (s.uaStage < 2 ? 0.002 : 0) - (s.twStage >= 3 && !s.taiwanWar ? 0.004 : 0);
        double security = -(s.terror - 40) * 0.0002 - (s.defcon < 5 ? 0.004 : 0);
        double immig = -(s.migr - 50) * 0.0002;
        out.effects().add(new Effect.IssueStanding(Issue.CHINA, foreign + security));
        out.effects().add(new Effect.IssueStanding(Issue.IMMIGRATION, immig));
        if (s.twStage >= 3 || s.defcon < 5) out.effects().add(RdFx.salience(Issue.CHINA, 0.02));
        if (s.twStage >= 3 && !s.taiwanWar) out.effects().add(new Effect.EconomyShock(-0.002 * (s.twStage - 2), 0.004 * (s.twStage - 2), 1));
    }

    // ---- Statecraft's week (2.8, 5.3) ----

    void statecraftWeek(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        int ua = data.index("UA"), tw = data.index("TW");
        for (int i = 0; i < NC; i++) {
            if (s.cvProg[i] > 0 && s.week >= s.cvDue[i]) covertResult(s, c, i, out, rng);
            if (i == ua && s.milAid[i] > 0 && s.uaStage == 2) s.ukr += 0.06 * s.milAid[i];
            if (i == tw && s.milAid[i] > 0) s.deter += 0.02 * s.milAid[i];
            s.unrest[i] = clamp(s.unrest[i] + (15 - s.unrest[i]) * (s.peace[i] > 0 ? 0.03 : 0.006), 0, 100);
            if (s.unrest[i] > 60) s.refugees += 0.03 * (s.unrest[i] - 60) / 10;
        }
        s.refugees = clamp(s.refugees + (20 - s.refugees) * 0.01, 0, 100);
        if (s.refugees > 35) { s.nato -= 0.02; s.migr += 0.01 * (s.refugees - 35); }
    }

    // =========================================================================================
    //  The effect language (Resolute Desk's Diplo.fx): the world's keys here, the rest as effects
    // =========================================================================================

    public void fx(ForeignState s, Context c, String fx, Outcome out) {
        if (fx == null || fx.isBlank()) return;
        StringBuilder rest = new StringBuilder();
        for (String tok : fx.trim().split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            String k = tok.substring(0, eq);
            double v;
            try { v = Double.parseDouble(tok.substring(eq + 1)); } catch (NumberFormatException e) { continue; }
            if (!shift(s, c, k, v, out)) rest.append(tok).append(' ');
        }
        out.effects().addAll(RdFx.effects(rest.toString()));
    }

    /** One of the world's own keys; false if the key belongs to the rest of the game. */
    public boolean shift(ForeignState s, Context c, String k, double v, Outcome out) {
        if (k.startsWith("rel:")) {
            int i = data.index(k.substring(4));
            if (i >= 0) s.rel[i] = clamp(s.rel[i] + v, -100, 100);
            return true;
        }
        switch (k) {
            case "twt" -> s.twt = clamp(s.twt + v, 0, 100);
            case "deter" -> s.deter = clamp(s.deter + v, 0, 100);
            case "ukr" -> s.ukr = clamp(s.ukr + v, 0, 100);
            case "talks" -> s.talks = clamp(s.talks + v, 0, 100);
            case "gaza" -> s.gaza = clamp(s.gaza + v, 0, 100);
            case "iran" -> s.iran = clamp(s.iran + v, 0, 100);
            case "redsea" -> s.redsea = clamp(s.redsea + v, 0, 100);
            case "proxy" -> s.proxy = clamp(s.proxy + v, 0, 100);
            case "nk" -> s.nk = clamp(s.nk + v, 0, 100);
            case "terror" -> s.terror = clamp(s.terror + v, 0, 100);
            case "cyber" -> s.cyber = clamp(s.cyber + v, 0, 100);
            case "migr" -> s.migr = clamp(s.migr + v, 0, 100);
            case "cartel" -> s.cartel = clamp(s.cartel + v, 0, 100);
            case "nato" -> s.nato = clamp(s.nato + v, 0, 100);
            case "pacific" -> s.pacific = clamp(s.pacific + v, 0, 100);
            case "twstage" -> { if (!s.taiwanWar) s.twStage = (int) Math.max(0, Math.min(4, v)); }   // the war itself is opened by the theater
            case "ua" -> s.uaStage = (int) Math.max(0, Math.min(2, v));
            case "defcon" -> s.defcon = (int) Math.max(1, Math.min(5, v));
            case "tcn" -> { int cn = data.index("CN"); if (s.tariff[cn] > 0) setTariff(s, c, cn, s.tariff[cn] - 1, out); }
            case "kia" -> {
                int n = (int) v;
                if (n > 0) {
                    s.kia += n;
                    out.effects().add(new Effect.Performance(-Math.min(6, 0.3 + 0.12 * n)));
                    out.news().add(news(c, News.Kind.BAD, (Fmt.format("%,d", n) + (n == 1 ? " American service member killed" : " American service members killed")).toUpperCase(Locale.ROOT),
                            "The president will meet the families at Dover.", 3.5));
                }
            }
            default -> { return false; }
        }
        return true;
    }

    // =========================================================================================
    //  Crises (Resolute Desk's Diplo.crises and crisisDecision)
    // =========================================================================================

    /** A crisis's condition: clauses joined by commas, {@code name>n}, {@code name<n} or a weekly chance {@code p=x}. */
    boolean cond(ForeignState s, Context c, String spec, DeterministicRandomManager rng) {
        if (spec == null || spec.isBlank()) return true;
        for (String cl : spec.split(",")) {
            cl = cl.trim();
            if (cl.startsWith("p=")) { if (!(rng.uniform("world.crisis") < Double.parseDouble(cl.substring(2)))) return false; continue; }
            int gt = cl.indexOf('>'), lt = cl.indexOf('<');
            int at = gt >= 0 ? gt : lt;
            if (at <= 0) continue;
            double x = var(s, cl.substring(0, at)), v = Double.parseDouble(cl.substring(at + 1));
            if (gt >= 0 ? !(x > v) : !(x < v)) return false;
        }
        return true;
    }

    /** The world's variables for conditions, here and in the era's stories (Resolute Desk's {@code Diplo.var}); NaN if not the world's. */
    public double var(ForeignState s, String k) {
        return switch (k) {
            case "twstage" -> s.twStage;
            case "twt" -> s.twt;
            case "ua" -> s.uaStage;
            case "ukr" -> s.ukr;
            case "talks" -> s.talks;
            case "gaza" -> s.gaza;
            case "iran" -> s.iran;
            case "redsea" -> s.redsea;
            case "proxy" -> s.proxy;
            case "nk" -> s.nk;
            case "terror" -> s.terror;
            case "cyber" -> s.cyber;
            case "migr" -> s.migr;
            case "cartel" -> s.cartel;
            case "kia" -> s.kia;
            case "wars" -> s.wars.stream().filter(War::fighting).count() + (s.taiwanWar ? 1 : 0);
            case "defcon" -> s.defcon;
            case "tcn" -> TARIFF_VAL[s.tariff[data.index("CN")]] + s.tariffAdd;
            default -> {
                if (k.startsWith("rel_")) { int i = data.index(k.substring(4)); yield i < 0 ? 0 : s.rel[i]; }
                yield Double.NaN;
            }
        };
    }

    void crises(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        List<Crisis> all = data.crises();
        for (int i = 0; i < all.size(); i++) {
            Crisis k = all.get(i);
            if (k.id().equals("tw_war")) continue;           // the Taiwan war opens on the operational model (theaters)
            if (s.cool[i] > 0) continue;
            if (!condSafe(s, c, k.cond(), rng)) continue;
            s.cool[i] = k.cool();
            s.crises++;
            out.news().add(news(c, News.Kind.WORLD, k.title(), null, 2.0));
            Question q = new Question();
            q.id = "w" + s.nextId++; q.kind = "crisis"; q.ref = k.id(); q.a = i;
            q.title = k.title();
            q.body = k.body() + " Your advisers each have a view; the odds are their best guess.";
            double best = -9;
            for (int o = 0; o < k.options().size(); o++) {
                CrisisOption opt = k.options().get(o);
                double odds = p(c, opt.kind(), opt.odds());
                q.options.add(opt.label());
                q.advice.add(opt.adviser() + ": \"" + oddsText(odds) + "\"");
                // the staff's choice (Resolute Desk's cautious auto-choice, a steady hand)
                double score = odds - (opt.kind().equals("m") ? 0.12 : 0) - (odds >= 1 ? 0.45 : 0);
                if (score > best) { best = score; q.recommended = o; }
            }
            q.due = s.week + 1;
            s.questions.add(q);
            if (k.id().equals("ru_nuclear")) out.effects().add(new Effect.PresidentStrain(0, 12));
            return;
        }
    }

    /** A condition that also accepts the stories' variables; unknown names read 0. */
    boolean condSafe(ForeignState s, Context c, String spec, DeterministicRandomManager rng) {
        if (spec == null || spec.isBlank()) return true;
        for (String cl : spec.split(",")) {
            cl = cl.trim();
            if (cl.startsWith("p=")) { if (!(rng.uniform("world.crisis") < Double.parseDouble(cl.substring(2)))) return false; continue; }
            int gt = cl.indexOf('>'), lt = cl.indexOf('<');
            int at = gt >= 0 ? gt : lt;
            if (at <= 0) continue;
            double x = var(s, cl.substring(0, at));
            if (Double.isNaN(x)) x = 0;
            double v = Double.parseDouble(cl.substring(at + 1));
            if (gt >= 0 ? !(x > v) : !(x < v)) return false;
        }
        return true;
    }

    void crisisDecision(ForeignState s, Context c, Question q, int k, Outcome out, DeterministicRandomManager rng) {
        Crisis cr = data.crises().get(q.a);
        CrisisOption opt = cr.options().get(k);
        boolean ok = rng.uniform("world.crisis") < p(c, opt.kind(), opt.odds());
        if (ok) s.okCount++; else s.failCount++;
        fx(s, c, ok ? opt.okFx() : opt.failFx(), out);
        String story = ok ? opt.okStory() : opt.failStory();
        out.news().add(news(c, ok ? News.Kind.GOOD : News.Kind.BAD, story, null, 1.8));
        s.record.add(stamp(c) + cr.title() + ": " + lower(story));
        info(s, ok ? "It worked" : "It didn't go as hoped", story);
    }

    // =========================================================================================
    //  Questions on the desk
    // =========================================================================================

    public Outcome answer(ForeignState s, Context c, String id, int k, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        Question q = s.question(id);
        if (q == null) throw new IllegalArgumentException("That question is no longer on the desk");
        if (k < 0 || k >= q.options.size()) throw new IllegalArgumentException("No such answer");
        answer(s, c, q, k, out, rng);
        return out;
    }

    void answer(ForeignState s, Context c, Question q, int k, Outcome out, DeterministicRandomManager rng) {
        s.questions.remove(q);
        switch (q.kind) {
            case "crisis" -> crisisDecision(s, c, q, k, out, rng);
            case "summit" -> summitRound(s, c, q, k, out, rng);
            case "gsummit" -> multiDecision(s, c, q, k, out);
            case "warpowers" -> warPowers(s, c, q, k, out);
            default -> { }
        }
    }

    void info(ForeignState s, String title, String body) {
        Question q = new Question();
        q.id = "w" + s.nextId++; q.kind = "info"; q.title = title; q.body = body;
        q.options.add("Understood"); q.advice.add("");
        q.due = s.week + 1;
        s.questions.add(q);
    }

    // =========================================================================================
    //  Calls, invitations and visits (3.1-3.4)
    // =========================================================================================

    public String call(ForeignState s, Context c, int i, Outcome out) {
        if (s.callWeek != s.week) { s.callWeek = s.week; s.callsThisWeek = 0; }
        if (s.callsThisWeek >= 3) throw new IllegalArgumentException("You've made three calls this week.");
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        s.callsThisWeek++;
        Country k = data.country(i);
        double kk = (c.cha() + c.fpc()) / 140;
        double gain = (3 + 4 * kk) * (s.week - s.lastCall[i] < 4 ? 0.4 : 1) * (s.align[i] == AL_ADVERSARY ? 0.6 : 1);
        s.rel[i] = clamp(s.rel[i] + gain, -100, 100);
        s.lastCall[i] = s.week;
        if (k.id().equals("CN")) s.twt = Math.max(0, s.twt - 1.5);
        if (k.id().equals("RU") && s.uaStage == 2) s.talks = Math.min(100, s.talks + 2);
        if (k.power() >= 6 || s.align[i] == AL_ADVERSARY)
            out.news().add(news(c, News.Kind.WORLD, "President " + last(c.presidentName()) + " speaks with " + s.leader[i], null, 0.8));
        return "You speak with " + s.leader[i] + ". Relations warm " + (gain >= 4 ? "noticeably." : "a little.");
    }

    public String invite(ForeignState s, Context c, int i, DeterministicRandomManager rng) {
        if (s.visitAt[i] >= 0) throw new IllegalArgumentException(s.leader[i] + " is already coming.");
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        s.visitAt[i] = s.week + 3 + rng.nextInt("world.visit", 4);
        return s.leader[i] + " accepts an invitation to the White House around " + around(c, s.visitAt[i] - s.week) + ".";
    }

    /** A visit, either way (Resolute Desk's {@code Diplo.visit}). */
    public void visit(ForeignState s, Context c, int i, boolean abroad, Outcome out) {
        Country k = data.country(i);
        double kk = (0.6 * c.cha() + 0.4 * c.fpc()) / 70;
        s.rel[i] = clamp(s.rel[i] + (abroad ? 9 : 7) * kk, -100, 100);
        s.visits[i]++;
        out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.03 * kk));
        if (s.align[i] == AL_TREATY) { if (k.region() == R_EUROPE) s.nato = Math.min(100, s.nato + 2); else s.pacific = Math.min(100, s.pacific + 2); }
        out.news().add(news(c, News.Kind.WORLD, abroad ? "President " + last(c.presidentName()) + " visits " + k.name() : s.leader[i] + " visits the White House",
                null, k.power() >= 6 ? 1.4 : 0.8));
        if (abroad) s.record.add(stamp(c) + "visited " + k.name());
    }

    // =========================================================================================
    //  Summits (3.5, 3.6)
    // =========================================================================================

    public String proposeSummit(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        if (s.summitCountry >= 0) throw new IllegalArgumentException("A summit is already planned.");
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        Country k = data.country(i);
        if (s.rel[i] < -70 && rng.uniform("world.summit") < 0.5) { s.rel[i] -= 2; return cap(k.name()) + " turns down the invitation."; }
        s.summitCountry = i; s.summitWeek = s.week + 4 + rng.nextInt("world.summit", 5); s.summitRound = 0;
        out.news().add(news(c, News.Kind.WORLD, "The White House announces a summit with " + s.leader[i], null, k.power() >= 6 ? 2.0 : 1.0));
        return "The summit with " + s.leader[i] + " is set for around " + around(c, s.summitWeek - s.week) + ".";
    }

    void summitStart(ForeignState s, Context c, Outcome out) {
        int i = s.summitCountry;
        s.summitRound = 1; s.summitMood = s.rel[i] / 2 + 10; s.summitLeverage = 0;
        out.effects().add(new Effect.PresidentStrain(-6, 0));
        Question q = summitQuestion(s, "Summit with " + s.leader[i] + ": the opening", "You sit down with " + s.leader[i] + " of " + nameIn(i)
                + ". Relations stand at " + Math.round(s.rel[i]) + ". How do you open?");
        q.options.addAll(List.of("Warmly: talk about what you share", "Firmly: lay out your red lines", "Personally: flatter and charm"));
        q.advice.addAll(List.of("Secretary of State: \"Build trust first; the asks come later.\"", "National security adviser: \"Show strength and they'll take you seriously.\"",
                "Chief of staff: \"You're good in a room. Use it.\""));
        q.a = 1;
    }

    Question summitQuestion(ForeignState s, String title, String body) {
        Question q = new Question();
        q.id = "w" + s.nextId++; q.kind = "summit"; q.title = title; q.body = body; q.ref = data.country(s.summitCountry).id();
        q.due = s.week + 1;
        s.questions.add(q);
        return q;
    }

    /** The asks open with this country (Resolute Desk's {@code Summit.asks}, 2029). */
    List<Integer> asks(ForeignState s, int i) {
        String id = data.country(i).id();
        List<Integer> l = new ArrayList<>();
        if (tradeOf(s, i) > 20) l.add(0);
        if (in(new String[]{"CN", "MX", "IN"}, id)) l.add(1);
        if (id.equals("CN")) l.add(2);
        if (id.equals("RU") || id.equals("UA")) l.add(3);
        if (id.equals("RU") || id.equals("CN")) l.add(4);
        if (id.equals("IR")) l.add(5);
        if (id.equals("KP")) l.add(6);
        if (s.align[i] == AL_TREATY) l.add(7);
        if (in(new String[]{"MX", "PA", "VE", "CU"}, id)) l.add(8);
        if (in(new String[]{"SA", "AE", "QA", "CA"}, id)) l.add(9);
        if (l.isEmpty()) l.add(11);
        return l;
    }

    void summitRound(ForeignState s, Context c, Question q, int k, Outcome out, DeterministicRandomManager rng) {
        int i = s.summitCountry;
        if (i < 0) return;
        if (q.a == 1) {
            switch (k) {
                case 0 -> s.summitMood += 10 + (c.fpc() - 60) / 6;
                case 1 -> { s.summitMood -= 8; s.summitLeverage += 15; }
                default -> s.summitMood += (c.cha() - 55) / 2 + rng.gaussian("world.summit") * 6;
            }
            s.summitRound = 2;
            Question n = summitQuestion(s, "Summit with " + s.leader[i] + ": the ask", "The mood in the room is "
                    + (s.summitMood > 35 ? "warm" : s.summitMood > 5 ? "businesslike" : "frosty") + ". What do you ask for?");
            for (int a : asks(s, i)) { n.options.add(ASKS[a][0]); n.advice.add(""); n.codes.add(a); }
            n.a = 2;
        } else if (q.a == 2) {
            s.summitAsk = q.codes.get(k);
            s.summitRound = 3;
            Question n = summitQuestion(s, "Summit with " + s.leader[i] + ": the give", "They want something in return. What do you put on the table?");
            n.options.addAll(List.of("Lift some tariffs or sanctions", "More aid or investment", "Nothing: hold firm"));
            n.advice.addAll(List.of("Treasury secretary: \"A little relief buys a lot of goodwill.\"", "Secretary of State: \"Money talks.\"",
                    "National security adviser: \"Don't pay for what they should do anyway.\""));
            n.a = 3;
        } else {
            double give = new double[]{20, 15, -5}[k];
            double chance = logistic((s.summitMood + 0.5 * s.summitLeverage + give - 20) / 18 + (c.fpc() - 60) / 40);
            if (k == 0) { if (s.tariff[i] > 0) setTariff(s, c, i, s.tariff[i] - 1, out); else if (s.sanction[i] > 0) setSanction(s, c, i, s.sanction[i] - 1, out); }
            if (k == 1) out.effects().add(new Effect.Deficit(0.01));
            boolean ok = rng.uniform("world.summit") < chance;
            summitFinish(s, c, i, s.summitAsk, ok, out);
            s.summitCountry = -1; s.summitWeek = -1; s.summitRound = 0;
        }
    }

    void summitFinish(ForeignState s, Context c, int i, int ask, boolean ok, Outcome out) {
        Country k = data.country(i);
        if (ok) {
            s.deals++;
            s.rel[i] = clamp(s.rel[i] + 8, -100, 100);
            out.effects().add(new Effect.Performance(0.8));
            out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.15));
            switch (ask) {
                case 0 -> { if (s.tariff[i] > 0) setTariff(s, c, i, 0, out); out.effects().add(new Effect.FinancialShock(0.4)); out.effects().add(new Effect.GroupStanding("rural", 1)); }
                case 1 -> { s.cartel -= 10; out.effects().add(new Effect.IssueStanding(Issue.CRIME, 0.1)); }
                case 2 -> { s.twt -= 15; if (s.twStage > 0 && !s.taiwanWar) s.twStage--; }
                case 3 -> s.talks = Math.min(100, s.talks + 18);
                case 4 -> { out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.2)); s.defcon = 5; }
                case 5 -> { s.iran -= 25; out.effects().addAll(RdFx.effects("oil=-2")); }
                case 6 -> s.nk -= 20;
                case 7 -> { s.allySpend += 0.2; s.nato -= 2; }
                case 8 -> { s.migr -= 15; out.effects().add(new Effect.IssueStanding(Issue.IMMIGRATION, 0.15)); }
                case 9 -> out.effects().addAll(RdFx.effects("oil=-4"));
                case 11 -> s.rel[i] = clamp(s.rel[i] + 4, -100, 100);
                default -> { }
            }
            String story = "A deal with " + nameIn(i) + ": " + ASKS[ask][0].toLowerCase(Locale.ROOT) + ".";
            s.record.add(stamp(c) + story.substring(0, story.length() - 1));
            out.news().add(news(c, News.Kind.GOOD, story, null, k.power() >= 6 ? 3.5 : 2));
            info(s, "A deal", story);
        } else {
            s.rel[i] = clamp(s.rel[i] - 3, -100, 100);
            out.effects().add(new Effect.Performance(-0.3));
            String story = "The summit with " + s.leader[i] + " ends without a deal.";
            out.news().add(news(c, News.Kind.BAD, story, null, 1.6));
            info(s, "No deal", story);
        }
    }

    void multilateral(ForeignState s, Context c, Outcome out) {
        for (String[] m : SUMMITS) {
            LocalDate d = LocalDate.of(c.date().getYear(), Integer.parseInt(m[1]), Integer.parseInt(m[2]));
            if (d.isBefore(c.date()) || !d.isBefore(c.date().plusDays(7))) continue;
            Question q = new Question();
            q.id = "w" + s.nextId++; q.kind = "gsummit"; q.a = m[0].equals("NATO") ? 1 : 0; q.ref = m[0];
            q.title = "The " + m[0] + " summit";
            q.body = "World leaders gather for the " + m[0] + " summit. It will take three days of your time. What's your message?";
            q.options.addAll(List.of("Reassure allies", "Press allies to do more", "Skip it and send the vice president"));
            q.advice.addAll(List.of("Secretary of State: \"Allies are nervous. Calm them.\"", "Treasury secretary: \"Make them pay their share.\"",
                    "Chief of staff: \"You need the rest more than they need a photo.\""));
            q.due = s.week + 1;
            s.questions.add(q);
        }
    }

    void multiDecision(ForeignState s, Context c, Question q, int k, Outcome out) {
        boolean nato = q.a == 1;
        if (k == 2) {
            s.nato -= 5; s.pacific -= 3;
            out.effects().add(new Effect.IssueStanding(Issue.CHINA, -0.05));
            out.news().add(news(c, News.Kind.INFO, "The vice president stands in for the president at the " + q.ref + " summit", null, 1.2));
            return;
        }
        out.effects().add(new Effect.PresidentStrain(-7, 0));
        if (k == 0) {
            s.nato += nato ? 4 : 1.5; s.pacific += 1.5;
            for (int i = 0; i < NC; i++) if (s.align[i] == AL_TREATY) s.rel[i] = clamp(s.rel[i] + 1, -100, 100);
        } else {
            s.allySpend += 0.15; s.nato -= nato ? 3 : 1;
            for (int i = 0; i < NC; i++) if (s.align[i] == AL_TREATY) s.rel[i] = clamp(s.rel[i] - 1.5, -100, 100);
            out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.05));
        }
        s.nato = clamp(s.nato, 0, 100); s.pacific = clamp(s.pacific, 0, 100);
        out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.05));
        out.news().add(news(c, News.Kind.WORLD, "The president attends the " + q.ref + " summit", null, 1.4));
    }

    // =========================================================================================
    //  Trade, sanctions and aid (7.1, 7.2)
    // =========================================================================================

    public String setTariff(ForeignState s, Context c, int i, int level, Outcome out) {
        level = Math.max(0, Math.min(3, level));
        int was = s.tariff[i];
        s.tariff[i] = level;
        double add = 0, total = data.tradeTotal();
        for (int j = 0; j < NC; j++) add += TARIFF_VAL[s.tariff[j]] * data.country(j).trade() / total;
        out.effects().add(new Effect.Tariff(add - s.tariffAdd));
        s.tariffAdd = add;
        Country k = data.country(i);
        if (level > was) {
            s.rel[i] -= 4 + 3 * level;        // Resolute Desk charged this on any non-zero level; only a rise is a new tariff here
            out.news().add(news(c, News.Kind.ECON, "New " + TARIFF_VAL[level] + "% tariffs on goods from " + nameIn(i), null, k.trade() > 100 ? 1.6 : 0.7));
            return "Tariffs on " + nameIn(i) + " rise to " + TARIFF_VAL[level] + "%.";
        }
        if (level == 0 && was > 0) out.news().add(news(c, News.Kind.ECON, "Tariffs on " + nameIn(i) + " are lifted", null, k.trade() > 100 ? 1.4 : 0.6));
        return level == was ? "No change." : "Tariffs on " + nameIn(i) + " come down to " + TARIFF_VAL[level] + "%.";
    }

    public String setSanction(ForeignState s, Context c, int i, int level, Outcome out) {
        level = Math.max(0, Math.min(3, level));
        int d = level - s.sanction[i];
        s.sanction[i] = level;
        s.rel[i] -= 6 * d;
        String id = data.country(i).id();
        double oil = id.equals("RU") ? 2 * d : id.equals("IR") || id.equals("VE") ? d : 0;
        if (oil != 0) out.effects().addAll(RdFx.effects("oil=" + oil));
        if (d != 0) out.news().add(news(c, News.Kind.WORLD, (d > 0 ? "Tougher" : "Lighter") + " sanctions on " + nameIn(i), null, 1.0));
        return d == 0 ? "No change." : (d > 0 ? "Sanctions on " + nameIn(i) + " tighten." : "Sanctions on " + nameIn(i) + " ease.");
    }

    public String setAid(ForeignState s, Context c, int i, int level, Outcome out) {
        level = Math.max(0, Math.min(3, level));
        int d = level - s.aid[i];
        s.aid[i] = level;
        s.rel[i] += 6 * d;
        out.effects().add(new Effect.Deficit(d * (data.country(i).id().equals("UA") ? 0.06 : 0.015)));
        if (d != 0) out.news().add(news(c, News.Kind.WORLD, (d > 0 ? "More" : "Less") + " American aid for " + nameIn(i), null, 0.9));
        return d == 0 ? "No change." : (d > 0 ? "More aid for " : "Less aid for ") + nameIn(i) + ".";
    }

    double tradeOf(ForeignState s, int i) { return atWar(s, i) ? 0 : data.country(i).trade(); }

    // =========================================================================================
    //  Operations and covert action (5.1, 5.2)
    // =========================================================================================

    public boolean operationReady(ForeignState s, Context c, int i) { return s.opCool[i] == 0 && condSafe(s, c, data.operations().get(i).cond(), null); }

    public double operationOdds(Context c, int i) { return p(c, "m", data.operations().get(i).odds()); }

    public String order(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        ForeignData.Operation op = data.operations().get(i);
        if (s.opCool[i] > 0) throw new IllegalArgumentException("The forces are resetting.");
        if (!condSafe(s, c, op.cond(), null)) throw new IllegalArgumentException("There is no target for that now.");
        boolean ok = rng.uniform("world.operation") < operationOdds(c, i);
        s.ops++; s.opCool[i] = 8;
        out.effects().add(new Effect.Readiness(2, -1.5));
        out.effects().add(new Effect.Deficit(0.01));
        fx(s, c, ok ? op.okFx() : op.failFx(), out);
        out.news().add(news(c, ok ? News.Kind.GOOD : News.Kind.BAD, op.name() + (ok ? ": the operation succeeds." : ": the operation falls short."), null, 2.4));
        s.record.add(stamp(c) + lower(op.name()) + (ok ? " (it succeeded)" : " (it fell short)"));
        if (!c.houseMajority() && i <= 3 && rng.uniform("world.operation") < 0.3)
            out.news().add(news(c, News.Kind.BAD, "Lawmakers say the president needed Congress's approval under the War Powers Act", null, 1.6));
        return ok ? "The operation succeeds." : "The operation falls short.";
    }

    public double covertOdds(Context c, int i) { return p(c, "i", data.covert().get(i).odds()); }
    public double covertLeak(Context c, int i) { return data.covert().get(i).expose() * (1.3 - 0.6 * c.pqCia()); }

    public String covert(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        ForeignData.Covert cv = data.covert().get(i);
        if (s.cvCool[i] > 0) throw new IllegalArgumentException("The CIA is still running the last one.");
        s.covert++; s.cvCool[i] = 26;
        boolean ok = rng.uniform("world.covert") < covertOdds(c, i);
        if (ok) fx(s, c, cv.fx(), out);
        boolean exp = rng.uniform("world.covert") < covertLeak(c, i);
        if (exp) {
            s.exposed++;
            fx(s, c, cv.exposedFx(), out);
            out.effects().add(new Effect.Scandal(0.875));
            out.news().add(news(c, News.Kind.BAD, "A secret program is exposed", "Reporters reveal a covert operation: " + lower(cv.name()) + ".", 2.4));
        }
        return ok ? (exp ? "It worked, but the program has leaked." : "The program is under way, and it's working.")
                : (exp ? "It failed, and the program has leaked." : "It isn't working yet.");
    }

    // =========================================================================================
    //  The Statecraft tools (7.3)
    // =========================================================================================

    void bumpRival(ForeignState s, int i, double d) {
        String r = rivalOf(data.country(i).id());
        if (r != null) { int j = data.index(r); s.rel[j] = clamp(s.rel[j] + d, -100, 100); }
    }

    public String milAid(ForeignState s, Context c, int i, int level, Outcome out) {
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        level = Math.max(0, Math.min(3, level));
        int d = level - s.milAid[i];
        s.milAid[i] = level;
        out.effects().add(new Effect.Deficit(0.012 * d));
        s.rel[i] = clamp(s.rel[i] + 6 * d, -100, 100);
        bumpRival(s, i, -4 * d);
        if (d > 0 && in(AUTOCRACY, data.country(i).id())) out.effects().add(new Effect.IssueStanding(Issue.ABORTION, -0.03 * d));
        return d == 0 ? "No change." : "Military aid to " + nameIn(i) + ": " + MIL_AID[level].toLowerCase(Locale.ROOT) + ".";
    }

    public String arms(ForeignState s, Context c, int i, int level, Outcome out) {
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        level = Math.max(0, Math.min(2, level));
        int d = level - s.arms[i];
        s.arms[i] = level;
        s.rel[i] = clamp(s.rel[i] + 8 * d, -100, 100);
        bumpRival(s, i, -5 * d);
        if (d > 0) {
            out.effects().add(new Effect.IssueStanding(Issue.ECONOMY, 0.03 * d));
            if (in(AUTOCRACY, data.country(i).id())) out.effects().add(new Effect.IssueStanding(Issue.ABORTION, -0.05 * d));
            if (data.country(i).id().equals("TW")) { s.twt = clamp(s.twt + 2 * d, 0, 100); s.deter = clamp(s.deter + 2 * d, 0, 100); }
        }
        return d == 0 ? "No change." : "Arms sales to " + nameIn(i) + ": " + ARMS[level].toLowerCase(Locale.ROOT) + ".";
    }

    public String troops(ForeignState s, Context c, int i, int step, Outcome out) {
        if (atWar(s, i)) throw new IllegalArgumentException("Troops in a war are managed in the Wars tab.");
        if (s.align[i] == AL_ADVERSARY) throw new IllegalArgumentException(cap(data.country(i).name()) + " won't host American forces.");
        step = Math.max(0, Math.min(TROOP_STEP.length - 1, step));
        double d = TROOP_STEP[step] - s.troopsIn[i];
        s.troopsIn[i] = TROOP_STEP[step];
        out.effects().add(new Effect.Deficit(0.0015 * d));
        s.rel[i] = clamp(s.rel[i] + (d > 0 ? Math.min(10, 0.4 * d) : Math.max(-10, 0.4 * d)), -100, 100);
        bumpRival(s, i, d > 0 ? -Math.min(8, 0.3 * d) : Math.min(5, -0.2 * d));
        Country k = data.country(i);
        if (k.region() == R_EUROPE) s.nato = clamp(s.nato + clamp(0.15 * d, -6, 6), 0, 100);
        if (k.region() == R_ASIA) {
            s.pacific = clamp(s.pacific + clamp(0.15 * d, -6, 6), 0, 100);
            if (in(new String[]{"TW", "JP", "PH"}, k.id())) s.deter = clamp(s.deter + clamp(0.2 * d, -6, 6), 0, 100);
        }
        if (k.id().equals("TW") && d > 0) s.twt = clamp(s.twt + 0.6 * d, 0, 100);
        return "American forces in " + nameIn(i) + ": " + TROOP_NAME[step].toLowerCase(Locale.ROOT) + ".";
    }

    public String ties(ForeignState s, Context c, int i, int level, Outcome out) {
        level = Math.max(0, Math.min(2, level));
        int d = level - s.ties[i];
        if (d == 0) return "No change.";
        Country k = data.country(i);
        if (k.id().equals("TW") && level == 2) {
            int cn = data.index("CN");
            s.ties[i] = 2;
            s.rel[cn] = clamp(s.rel[cn] - 60, -100, 100); s.rel[i] = clamp(s.rel[i] + 30, -100, 100);
            s.twt = clamp(s.twt + 30, 0, 100);
            s.ties[cn] = Math.min(s.ties[cn], 1);
            out.effects().add(new Effect.FinancialShock(-1.5));
            s.record.add(stamp(c) + "recognized Taiwan");
            out.news().add(news(c, News.Kind.WORLD, "UNITED STATES RECOGNIZES TAIWAN AS AN INDEPENDENT COUNTRY", "Beijing recalls its ambassador and warns of grave consequences.", 5));
            return "You recognize Taiwan. Beijing is furious.";
        }
        s.ties[i] = level;
        s.rel[i] = clamp(s.rel[i] + d * (level == 2 ? 15 : 12), -100, 100);
        if (level == 0) { s.sanction[i] = Math.max(1, s.sanction[i]); out.effects().add(new Effect.IssueStanding(Issue.CHINA, -0.05)); }
        out.news().add(news(c, News.Kind.WORLD, (d > 0 ? "Washington restores ties with " : "Washington cuts ties with ") + nameIn(i), null, 1.4));
        return "Diplomatic ties with " + nameIn(i) + ": " + TIES[level].toLowerCase(Locale.ROOT) + ".";
    }

    public String peacekeepers(ForeignState s, Context c, int i, boolean on, Outcome out) {
        if (on == (s.peace[i] > 0)) return "No change.";
        if (on && (s.unrest[i] < 35 || atWar(s, i))) throw new IllegalArgumentException("There is no peace to keep in " + nameIn(i) + ".");
        s.peace[i] = on ? 1 : 0;
        out.effects().add(new Effect.Deficit(on ? 0.006 : -0.006));
        if (on) { s.unrest[i] = clamp(s.unrest[i] - 8, 0, 100); out.effects().add(new Effect.IssueStanding(Issue.CHINA, 0.05)); }
        return on ? "American peacekeepers deploy to " + nameIn(i) + "." : "The peacekeepers come home from " + nameIn(i) + ".";
    }

    /** Resolute Desk's {@code Statecraft.yes}: the yes votes in a chamber. ({@code hawk} is the blocs' deficit hawkishness, as in Resolute Desk.) */
    double yes(Context c, List<Bloc> chamber, double partyPull, double otherPull, double hawkPull) {
        double v = 0;
        for (Bloc b : chamber) {
            double l = (b.party() == c.president() ? partyPull * b.loyalty() : otherPull - 0.6 * c.polarization()) + hawkPull * (b.hawk() - 0.45)
                    + (c.approval() - 50) / 50 + (b.rel() - 50) / 100;
            v += b.seats() * logistic(l);
        }
        return v;
    }

    public double tradeOdds(ForeignState s, Context c, int i) {
        double m = s.align[i] == AL_ADVERSARY ? -4 : s.rel[i] / 100 - 0.75;
        double h = yes(c, c.house(), 1.1 + m, m, 0), se = yes(c, c.senate(), 1.1 + m, m, 0);
        return logistic((h - 218) / 9) * logistic((se - 50.5) / 3);
    }

    public String tradeDeal(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        if (s.pact[i] > 0) throw new IllegalArgumentException("There is already a trade agreement with " + nameIn(i) + ".");
        boolean ok = rng.uniform("world.trade") < tradeOdds(s, c, i);
        if (ok) {
            s.pact[i] = 1;
            s.rel[i] = clamp(s.rel[i] + 10, -100, 100);
            out.effects().add(new Effect.EconomyShock(0.01 * Math.min(1, data.country(i).trade() / 200) * 4, 0, 52));
            out.effects().add(new Effect.GroupStanding("rural", 0.6));
            s.deals++;
            s.record.add(stamp(c) + "a trade agreement with " + nameIn(i));
            out.news().add(news(c, News.Kind.GOOD, "Congress approves a trade agreement with " + nameIn(i), null, 2));
            return "Congress approves the agreement.";
        }
        s.rel[i] = clamp(s.rel[i] - 4, -100, 100);
        out.news().add(news(c, News.Kind.BAD, "Congress rejects the trade agreement with " + nameIn(i), null, 1.8));
        return "Congress rejects the agreement.";
    }

    public double treatyOdds(ForeignState s, Context c, int i) {
        String id = data.country(i).id();
        boolean exposed = id.equals("UA") && s.uaStage == 2 || id.equals("TW");
        double m = s.align[i] == AL_ADVERSARY ? -4 : s.rel[i] / 80 - (exposed ? 1.8 : 0) - (data.country(i).region() == R_MIDEAST ? 0.6 : 0)
                - (data.country(i).nuclear() ? 0.3 : 0) - 0.3;
        double se = yes(c, c.senate(), 1.2 + m, 0.4 + m, 0.8);
        return logistic((se - 66.5) / 3);
    }

    public String treaty(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        if (s.align[i] == AL_TREATY) throw new IllegalArgumentException(cap(data.country(i).name()) + " is already a treaty ally.");
        if (s.align[i] == AL_ADVERSARY) throw new IllegalArgumentException("Not with an adversary.");
        if (s.rel[i] < 40) throw new IllegalArgumentException("Relations must be 40 or better.");
        boolean ok = rng.uniform("world.treaty") < treatyOdds(s, c, i);
        if (ok) {
            s.align[i] = AL_TREATY;
            s.rel[i] = clamp(s.rel[i] + 15, -100, 100);
            bumpRival(s, i, -15);
            s.deter = clamp(s.deter + 3, 0, 100);
            if (data.country(i).region() == R_EUROPE) s.nato = clamp(s.nato + 4, 0, 100);
            if (data.country(i).region() == R_ASIA) s.pacific = clamp(s.pacific + 4, 0, 100);
            s.deals++;
            s.record.add(stamp(c) + "a defense treaty with " + nameIn(i));
            out.news().add(news(c, News.Kind.GOOD, "The Senate ratifies a defense treaty with " + nameIn(i), null, 2.5));
            return "The Senate ratifies the treaty.";
        }
        s.rel[i] = clamp(s.rel[i] - 6, -100, 100);
        out.news().add(news(c, News.Kind.BAD, "The Senate rejects a defense treaty with " + nameIn(i), null, 2));
        return "The Senate rejects the treaty.";
    }

    // ---- country covert programs (5.3) ----

    public double covertOdds(ForeignState s, Context c, int i, int kind) {
        return p(c, "i", CV2_ODDS[kind] - 0.02 * data.country(i).power() + (kind == 3 ? s.unrest[i] / 250 : 0));
    }

    public double covertLeakOdds(Context c, int kind) { return CV2_LEAK[kind] * (1.3 - 0.6 * c.pqCia()); }

    public String startCovert(ForeignState s, Context c, int i, int kind) {
        if (s.cvProg[i] > 0) throw new IllegalArgumentException("A program is already running in " + nameIn(i) + ".");
        if (kind >= 2 && s.align[i] == AL_TREATY) throw new IllegalArgumentException("The CIA won't arm rebels against a treaty ally.");
        if (atWar(s, i)) throw new IllegalArgumentException("Not while the two countries are at war.");
        kind = Math.max(0, Math.min(3, kind));
        s.cvProg[i] = kind + 1; s.cvDue[i] = s.week + CV2_WEEKS[kind]; s.covert++;
        return "You sign the finding. The CIA expects results within " + CV2_WEEKS[kind] + " weeks.";
    }

    void covertResult(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        int kind = s.cvProg[i] - 1;
        s.cvProg[i] = 0;
        boolean ok = rng.uniform("world.covert") < covertOdds(s, c, i, kind), leak = rng.uniform("world.covert") < covertLeakOdds(c, kind);
        Country k = data.country(i);
        if (leak) {
            s.exposed++;
            s.rel[i] = clamp(s.rel[i] - 15, -100, 100);
            out.effects().add(new Effect.Scandal(1.5 + kind));
            out.effects().add(new Effect.IssueStanding(Issue.CHINA, -0.1));
            out.news().add(news(c, News.Kind.BAD, ("Report: CIA behind a secret operation in " + k.name()).toUpperCase(Locale.ROOT),
                    "The administration won't comment. " + cap(k.name()) + " calls it an act of war.", 3.5));
        }
        if (!ok) { out.news().add(news(c, News.Kind.INFO, "Intelligence officials say a covert effort in " + nameIn(i) + " has failed", null, 1.0)); return; }
        switch (kind) {
            case 0 -> { s.rel[i] = clamp(s.rel[i] + 6, -100, 100); s.unrest[i] = clamp(s.unrest[i] + 4, 0, 100); }
            case 1 -> {
                switch (k.id()) {
                    case "IR" -> s.iran = clamp(s.iran - 15, 0, 100);
                    case "KP" -> s.nk = clamp(s.nk - 10, 0, 100);
                    case "CN", "RU" -> s.cyber = clamp(s.cyber + 8, 0, 100);
                    default -> { }
                }
            }
            case 2 -> { s.unrest[i] = clamp(s.unrest[i] + 25, 0, 100); s.refugees += 4; }
            default -> { topple(s, c, i, "covert action", out); return; }
        }
        out.news().add(news(c, News.Kind.INFO, "Officials say quietly that a covert program in " + nameIn(i) + " has worked", null, 0.8));
    }

    void topple(ForeignState s, Context c, int i, String how, Outcome out) {
        Country k = data.country(i);
        s.toppled[i] = true;
        s.align[i] = AL_PARTNER;
        s.leader[i] = "a transitional authority"; s.title[i] = "Transitional government";
        s.sanction[i] = 0; s.ties[i] = 2;
        s.rel[i] = 35;
        s.base[i] = Math.max(s.base[i], 0);        // Resolute Desk kept the old hostility as the anchor; a new government starts from neutral
        for (int j = 0; j < NC; j++) if (j != i && s.align[j] == AL_ADVERSARY) s.rel[j] = clamp(s.rel[j] - 10, -100, 100);
        s.record.add(stamp(c) + "the government of " + k.name() + " fell after " + how);
        out.news().add(news(c, News.Kind.WORLD, ("The government of " + k.name() + " falls").toUpperCase(Locale.ROOT), null, 5));
    }

    // ---- alliances and posture (7.4) ----

    public String article5(ForeignState s, Context c) {
        if (s.week - s.art5Week < 13) throw new IllegalArgumentException("You reaffirmed it recently.");
        s.art5Week = s.week;
        s.nato = Math.min(100, s.nato + 6);
        for (int i = 0; i < NC; i++) if (data.country(i).region() == R_EUROPE && s.align[i] == AL_TREATY) s.rel[i] = clamp(s.rel[i] + 2, -100, 100);
        return "You reaffirm the commitment to defend every ally.";
    }

    public String pressAllies(ForeignState s, Context c) {
        if (s.week - s.pressWeek < 13) throw new IllegalArgumentException("You pressed them recently.");
        s.pressWeek = s.week;
        s.allySpend += 0.1;
        s.nato = Math.max(0, s.nato - 4);
        return "Allies grumble, then pledge more.";
    }

    public String drills(ForeignState s, Context c) {
        if (s.week - s.drillWeek < 13) throw new IllegalArgumentException("The last exercises were recent.");
        s.drillWeek = s.week;
        s.pacific = Math.min(100, s.pacific + 4);
        s.rel[data.index("CN")] -= 3; s.rel[data.index("KP")] -= 3;
        s.nk = Math.min(100, s.nk + 3);
        return "The exercises reassure allies and irritate Beijing and Pyongyang.";
    }

    public String posture(ForeignState s, Context c, int area, int level, Outcome out) {
        level = Math.max(0, Math.min(3, level));
        int d = level - s.posture[area];
        if (d == 0) return "No change.";
        s.posture[area] = level;
        out.effects().add(new Effect.Deficit(0.04 * d));
        if (d > 0) out.effects().add(new Effect.Readiness(2, -2 * d));
        if (area == 0) { s.rel[data.index("CN")] -= 5 * d; s.pacific = clamp(s.pacific + 3 * d, 0, 100); }
        if (area == 1) { s.rel[data.index("RU")] -= 4 * d; s.nato = clamp(s.nato + 3 * d, 0, 100); }
        out.news().add(news(c, News.Kind.WORLD, "The Pentagon " + (d > 0 ? "adds forces in " : "draws down forces in ") + POSTURE_AREA[area].toLowerCase(Locale.ROOT), null, 1.2));
        return POSTURE_AREA[area] + ": " + POSTURE[level].toLowerCase(Locale.ROOT) + ".";
    }

    // =========================================================================================
    //  Objectives (1.6)
    // =========================================================================================

    void refreshObjectives(ForeignState s, Context c) {
        int year = c.date().getYear();
        s.objectivesYear = year;
        for (ObjectiveDef d : data.objectives()) {
            if (d.who().startsWith("*") || year < d.from() || year > d.to()) continue;
            int i = data.index(d.who());
            if (i < 0 || has(s, d.id())) continue;
            s.objectives.add(objective(d.id(), d.id(), i));
        }
        List<ObjectiveDef> templates = data.objectives().stream().filter(d -> d.who().startsWith("*") && year >= d.from() && year <= d.to()).toList();
        for (int i = 0; i < NC; i++) {
            int n = 0;
            for (Objective o : s.objectives) if (o.country == i) n++;
            String code = String.valueOf("NPTA".charAt(s.align[i]));
            List<ObjectiveDef> fit = templates.stream().filter(d -> d.who().substring(1).equals(code)).toList();
            for (int j = 0; n < 2 && j < fit.size(); j++) {
                ObjectiveDef d = fit.get((i + j) % fit.size());
                String id = d.id() + ":" + data.country(i).id();
                if (has(s, id)) continue;
                s.objectives.add(objective(id, d.id(), i));
                n++;
            }
        }
        for (Objective o : s.objectives) if (o.status == 2 && s.week >= o.tryAfter) { o.status = 0; o.fails = 0; }
    }

    static Objective objective(String id, String def, int country) {
        Objective o = new Objective();
        o.id = id; o.def = def; o.country = country;
        return o;
    }

    static boolean has(ForeignState s, String id) { for (Objective o : s.objectives) if (o.id.equals(id)) return true; return false; }

    public ObjectiveDef definition(Objective o) {
        for (ObjectiveDef d : data.objectives()) if (d.id().equals(o.def)) return d;
        throw new IllegalStateException("No objective " + o.def);
    }

    public String fill(String text, int country) { return text.replace("{C}", nameIn(country)); }

    /** Why an objective cannot be tried now, or null (Resolute Desk's {@code Objectives.cannot}). */
    public String cannot(ForeignState s, Objective o) {
        if (o.status == 1) return "Achieved.";
        if (o.status == 2) return "Stalled after three failures; it can be tried again in the new year.";
        if (s.week < o.tryAfter) return "Give it time: the next try can come in " + (o.tryAfter - s.week) + (o.tryAfter - s.week == 1 ? " week." : " weeks.");
        if (atWar(s, o.country)) return "Not while the two countries are at war.";
        Step st = definition(o).steps().get(o.step);
        String need = need(s, o.country, st.need());
        return need == null ? null : "First: " + need + ".";
    }

    /** An unmet requirement in words, or null when it is met. */
    String need(ForeignState s, int country, String req) {
        if (req == null || req.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)(@(\\w+))?(>=|<=)(-?[\\d.]+)").matcher(req.trim());
        if (!m.matches()) return null;
        String key = m.group(1);
        int i = m.group(3) == null ? country : data.index(m.group(3));
        if (i < 0) return null;
        boolean ge = m.group(4).equals(">=");
        double v = Double.parseDouble(m.group(5));
        double x = switch (key) {
            case "milaid" -> s.milAid[i];
            case "arms" -> s.arms[i];
            case "aid" -> s.aid[i];
            case "sanction" -> s.sanction[i];
            case "troops" -> s.troopsIn[i];
            case "ties" -> s.ties[i];
            case "tariff" -> s.tariff[i];
            default -> s.rel[i];
        };
        if (ge ? x >= v : x <= v) return null;
        String who = nameIn(i);
        return switch (key) {
            case "milaid" -> "military aid to " + who + " at " + MIL_AID[(int) Math.min(3, v)].toLowerCase(Locale.ROOT) + " or more";
            case "arms" -> v >= 2 ? "a major arms package for " + who : "arms sales to " + who + " approved";
            case "aid" -> "foreign aid to " + who + (v >= 2 ? " at a substantial level" : "");
            case "sanction" -> ge ? "sanctions on " + who + " at level " + (int) v + " or more" : "sanctions on " + who + " eased to level " + (int) v + " or less";
            case "troops" -> "at least " + Fmt.format("%,.0f", v * 1000) + " American troops in " + who;
            case "ties" -> "full diplomatic relations with " + who;
            case "tariff" -> "tariffs on " + who + " brought down";
            default -> "better relations with " + who;
        };
    }

    public double stepOdds(ForeignState s, Context c, Objective o) {
        Step st = definition(o).steps().get(o.step);
        return st.odds() >= 1 ? 1 : p(c, st.kind(), st.odds() + s.rel[o.country] / 500);
    }

    public String tryObjective(ForeignState s, Context c, String id, Outcome out, DeterministicRandomManager rng) {
        Objective o = null;
        for (Objective x : s.objectives) if (x.id.equals(id)) o = x;
        if (o == null) throw new IllegalArgumentException("No such objective");
        String no = cannot(s, o);
        if (no != null) throw new IllegalArgumentException(no);
        ObjectiveDef d = definition(o);
        Step st = d.steps().get(o.step);
        String cc = data.country(o.country).id();
        fx(s, c, st.cost().replace("{C}", cc), out);
        boolean ok = rng.uniform("world.objective") < stepOdds(s, c, o);
        if (!ok) {
            fx(s, c, st.failFx().replace("{C}", cc), out);
            o.fails++; o.tryAfter = s.week + 4;
            if (o.fails >= 3) { o.status = 2; o.tryAfter = s.week + 26; return fill(st.label(), o.country) + " didn't work. After three failures this is stalled for six months."; }
            return fill(st.label(), o.country) + " didn't work. You can try again in a month.";
        }
        fx(s, c, st.okFx().replace("{C}", cc), out);
        o.step++; o.fails = 0; o.tryAfter = s.week + 1;
        if (o.step >= d.steps().size()) {
            o.status = 1;
            fx(s, c, d.payoff().replace("{C}", cc), out);
            s.done++; s.deals++;
            String name = fill(d.name(), o.country);
            s.record.add(stamp(c) + "achieved: " + lower(name));
            out.news().add(news(c, News.Kind.GOOD, "Achieved: " + name, null, 2.5));
            return "Achieved: " + name + ".";
        }
        return fill(st.label(), o.country) + ": it worked. Next: " + fill(d.steps().get(o.step).label(), o.country) + ".";
    }

    // =========================================================================================
    //  Wars of choice (6)
    // =========================================================================================

    public boolean atWar(ForeignState s, int i) { for (War x : s.wars) if (x.fighting() && x.enemy == i) return true; return false; }

    public double need(int i, int kind) {
        int p = data.country(i).power();
        return switch (kind) { case WK_STRIKES -> 4 + 1.5 * p; case WK_BLOCKADE -> 8 + 3 * p; case WK_NOFLY -> 6 + p; default -> 40 + 30 * p; };
    }

    double pace(int i, int kind) {
        Country k = data.country(i);
        int p = k.power();
        String prot = protector(k.id());
        boolean ground = kind == WK_INVASION || kind == WK_OCCUPATION, guarded = ground && prot != null;
        if (kind == WK_OCCUPATION) return -0.06 - 0.015 * p - (guarded ? 0.15 : 0);
        double v = WK_PACE[kind] / (1 + (kind == WK_INVASION ? 0.6 : 0.3) * p);
        if (p >= 7) v = v * 0.5 - 0.15;
        if (k.nuclear()) v = v * 0.3 - 0.25;
        if (guarded) v = v * 0.1 - 0.6;
        return v;
    }

    public boolean authorized(ForeignState s, int i) { return s.authorized[i] > 0 && s.week - s.authorized[i] < 52; }

    /** Why a war of this kind cannot be started, or null (Resolute Desk's {@code Wars.cannot}). */
    public String cannotWar(ForeignState s, int i, int kind) {
        for (War x : s.wars) if (x.fighting() && x.enemy == i && x.kind == kind) return "That war is already being fought.";
        if (kind == WK_INVASION && data.country(i).nuclear())
            return "The Joint Chiefs won't plan an invasion of a nuclear power. Strikes and a blockade are still on the table, at grave risk.";
        if (kind == WK_NOFLY && s.unrest[i] < 40) return "There is no fighting inside " + nameIn(i) + " for a no-fly zone to stop.";
        if (kind < 0 || kind > WK_INVASION) return "No such operation.";
        return null;
    }

    /** What to expect from a war (Resolute Desk's {@code Wars.expect}). */
    public String expect(ForeignState s, int i, int kind) {
        Country k = data.country(i);
        StringBuilder b = new StringBuilder("About " + Fmt.format("%,.0f", need(i, kind) * 1000) + " troops.");
        if (in(OIL_PRODUCER, k.id()) && kind != WK_NOFLY) b.append(" Oil prices will jump.");
        if (k.power() >= 6) b.append(" Markets will fall hard.");
        if (k.nuclear()) b.append(" A nuclear-armed enemy: DEFCON 2, and the war could spiral.");
        if (k.region() == R_MIDEAST || k.region() == R_SOUTH_ASIA) b.append(" Expect terror attacks in revenge.");
        if (kind == WK_INVASION) {
            b.append(" If the government falls, you own the occupation.");
            String prot = protector(k.id());
            if (prot != null) { Country pk = data.country(data.index(prot)); b.append(" ").append(cap(pk.name())).append(" has promised to fight for ").append(k.name()).append(pk.nuclear() ? ", and it has the bomb." : "."); }
        }
        if (s.align[i] == AL_TREATY) b.append(" Attacking a treaty ally would shatter the alliances.");
        else if (s.align[i] == AL_PARTNER) b.append(" Attacking a partner will alarm every ally.");
        if (!authorized(s, i)) b.append(" Congress hasn't authorized it.");
        return b.toString();
    }

    static String warName(String country, int kind) {
        return switch (kind) {
            case WK_STRIKES -> "The air war against " + country;
            case WK_BLOCKADE -> "The blockade of " + country;
            case WK_NOFLY -> "The no-fly zone over " + country;
            case WK_INVASION -> "The invasion of " + country;
            default -> "The occupation of " + country;
        };
    }

    public String startWar(ForeignState s, Context c, int i, int kind, Outcome out) {
        String no = cannotWar(s, i, kind);
        if (no != null) throw new IllegalArgumentException(no);
        Country k = data.country(i);
        War x = new War();
        x.id = "choice:" + k.id() + ":" + kind + ":" + s.week; x.kind = kind; x.enemy = i;
        x.name = warName(k.name(), kind);
        x.need = need(i, kind); x.troops = x.need;
        x.drift = pace(i, kind);
        x.lethal = WK_LETHAL[kind] * (kind == WK_INVASION && protector(k.id()) != null ? 4 : 1); x.cost = WK_COST[kind];
        x.authorized = authorized(s, i);
        x.stakes = 0.7 + (s.rel[i] < -50 ? 0.2 : 0) + (x.authorized ? 0.2 : 0);
        int al = s.align[i];
        x.legit = clamp(38 + (x.authorized ? 14 : 0) + (s.rel[i] < -50 ? 8 : s.rel[i] < 0 ? 3 : -10) - (al == AL_TREATY ? 30 : al == AL_PARTNER ? 15 : 0) - (k.nuclear() ? 15 : 0), 5, 70);
        x.support = clamp(x.legit + 2 + (c.approval() - 50) * 0.2, 8, 85);
        x.front = WK_FRONT[kind]; x.stage = WS_FIGHTING; x.started = s.week; x.strategy = 1;
        s.wars.add(x);
        s.record.add(stamp(c) + lower(x.name) + " began");
        out.news().add(news(c, News.Kind.WORLD, ("President " + last(c.presidentName()) + " orders " + lower(x.name)).toUpperCase(Locale.ROOT),
                x.authorized ? "Congress has authorized the use of force." : "The White House says the president has the authority to act alone.", 5));
        shock(s, c, x, out);
        warSpend(s, out);
        return x.name + " begins. Run it from the Wars tab.";
    }

    void shock(ForeignState s, Context c, War x, Outcome out) {
        int i = x.enemy;
        Country k = data.country(i);
        int p = k.power();
        if (k.nuclear()) { out.effects().add(new Effect.Performance(-4)); out.effects().add(new Effect.PresidentStrain(0, 15)); }
        else out.effects().add(new Effect.Rally(x.authorized ? 3 + 0.3 * p : 1 + 0.15 * p));
        out.effects().add(new Effect.FinancialShock(-(0.2 * p + (k.nuclear() ? 2 : 0))));
        if (in(OIL_PRODUCER, k.id()) && x.kind != WK_NOFLY)
            out.effects().addAll(RdFx.effects("oil=" + (x.kind == WK_STRIKES ? 6 : x.kind == WK_BLOCKADE ? 10 : 12) * (in(BIG_PRODUCER, k.id()) ? 2 : 1) * c.priceLevel()));
        s.rel[i] = -100; s.ties[i] = 0; s.sanction[i] = 3;
        if (!x.authorized) { for (int j = 0; j < NC; j++) if (s.align[j] == AL_TREATY) s.rel[j] -= 4; s.nato -= 3; }
        if (s.align[i] == AL_TREATY) { s.nato -= 25; s.pacific -= 20; for (int j = 0; j < NC; j++) if (j != i && s.align[j] == AL_TREATY) s.rel[j] -= 30; }
        else if (s.align[i] == AL_PARTNER) { for (int j = 0; j < NC; j++) if (j != i && data.country(j).region() == k.region()) s.rel[j] -= 10; }
        if (in(ADVERSARY_BLOC, k.id())) for (String b : ADVERSARY_BLOC) { int j = data.index(b); if (j != i) s.rel[j] -= 8; }
        s.refugees += x.kind == WK_INVASION ? 15 : 3; s.unrest[i] = clamp(s.unrest[i] + (x.kind == WK_INVASION ? 30 : 10), 0, 100);
        if (k.region() == R_MIDEAST || k.region() == R_SOUTH_ASIA) s.terror = clamp(s.terror + (x.kind == WK_INVASION ? 15 : 8), 0, 100);
        if (k.region() == R_AMERICAS) s.migr = clamp(s.migr + (x.kind == WK_INVASION ? 10 : 4), 0, 100);
        if (k.nuclear() || p >= 8) s.defcon = Math.min(s.defcon, k.nuclear() ? 2 : 3);
        String prot = protector(k.id());
        if (x.kind == WK_INVASION && prot != null) {
            int pj = data.index(prot);
            s.rel[pj] -= 40;
            if (data.country(pj).nuclear()) s.defcon = Math.min(s.defcon, 2);
            out.news().add(news(c, News.Kind.WORLD, (data.country(pj).name() + " sends its forces to defend " + k.name()).toUpperCase(Locale.ROOT),
                    "American commanders now face " + nameIn(pj) + "'s army as well.", 4));
        }
        out.effects().add(RdFx.salience(Issue.CHINA, 0.4));
        if (!x.authorized) out.effects().add(new Effect.BlocRelations(-1, -1, -5));
        for (int j = 0; j < NC; j++) s.rel[j] = clamp(s.rel[j], -100, 100);
        s.nato = clamp(s.nato, 0, 100); s.pacific = clamp(s.pacific, 0, 100);
    }

    /** The war budget (Resolute Desk's {@code econ.war}, troops times cost, % of GDP a year): its change goes to the deficit. */
    void warSpend(ForeignState s, Outcome out) {
        double spend = 0;
        for (War x : s.wars) if (x.fighting()) spend += x.troops * x.cost;
        if (Math.abs(spend - s.warBudget) > 1e-9) out.effects().add(new Effect.Deficit(spend - s.warBudget));
        s.warBudget = spend;
    }

    void wars(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        boolean any = false;
        for (War x : new ArrayList<>(s.wars)) {
            if (!x.fighting()) continue;
            any = true;
            x.weeks++;
            if (step(s, c, x, out, rng)) continue;
            double eff = x.troops <= 0 ? -1.2 : clamp(Math.log(x.troops / x.need) * 0.9, -1.5, 1.2);
            double strat = new double[]{-0.15, 0, 0.45, -0.2}[x.strategy];
            double sk = Math.sqrt(Math.max(0.5, x.stakes)), dq = c.pqDef();
            x.front = clamp(x.front + (x.drift + 0.35 * eff + strat + (dq - 0.6) * 0.4 + rng.gaussian("world.war") * 0.9) / sk, 0, 100);
            double kiaRate = x.lethal * new double[]{0.6, 1, 1.6, 0.7}[x.strategy] * (1.2 - 0.4 * dq);
            double dead = x.troops * kiaRate * (0.6 + 0.8 * rng.uniform("world.war"));
            x.kia += dead;
            s.kia += (int) Math.round(dead);
            if (dead > 0) out.effects().add(new Effect.Performance(-Math.min(0.3, dead / 250)));
            double supT = x.legit - 6 * Math.log10(1 + x.kia / 10) - 0.05 * Math.max(0, x.weeks - 26);
            x.support = clamp(x.support + (supT - x.support) * 0.03 - dead / x.stakes / 2000 - (x.weeks > 104 ? 0.03 : 0) + (x.front - 50) * 0.003, 5, 98);
            x.talks = clamp(x.talks + (x.strategy == 3 ? 1.5 : 0.1) + (x.front > 65 ? 0.4 : 0) - (x.front < 35 ? 0.3 : 0) + rng.gaussian("world.war") * 0.6, 0, 100);
            out.effects().add(new Effect.IssueStanding(Issue.CHINA, (x.support - 50) * 0.0004 + (x.front - 50) * 0.0002));
            out.effects().add(RdFx.salience(Issue.CHINA, 0.004 * x.stakes));
            if (dead > 50 && rng.uniform("world.war") < 0.06)
                out.news().add(news(c, News.Kind.BAD, x.name + ": " + Fmt.format("%,.0f", x.kia) + " Americans killed so far", null, 2));
            if (x.front >= 100) victory(s, c, x, out);
            else if (x.front <= 0) end(s, c, x, WS_LOST, out);
            else if (x.talks >= 100 && x.front >= 25) end(s, c, x, WS_AGREED, out);
            else if (x.troops <= 0.5 && x.weeks > 4) end(s, c, x, x.front >= 60 ? WS_AGREED : WS_LOST, out);
        }
        if (any) warSpend(s, out);
    }

    /** A war of choice's own week (Resolute Desk's {@code Wars.step}); true if it ended. */
    boolean step(ForeignState s, Context c, War x, Outcome out, DeterministicRandomManager rng) {
        int i = x.enemy;
        Country k = data.country(i);
        double intensity = x.need > 0 ? x.troops / x.need : 0;
        if (x.kind != WK_OCCUPATION) x.drift = pace(i, x.kind) * Math.min(1.3, intensity);
        boolean mideast = k.region() == R_MIDEAST || k.region() == R_SOUTH_ASIA;
        s.terror = clamp(s.terror + 0.03 * intensity * (mideast ? 2 : 1), 0, 100);
        s.refugees += (x.kind == WK_INVASION || x.kind == WK_OCCUPATION ? 0.12 : 0.02) * intensity;
        out.effects().add(new Effect.Performance(-0.007 * Math.max(0, 55 - x.support)));
        if (!x.authorized && x.support < 45) for (int j = 0; j < NC; j++) if (s.align[j] == AL_TREATY) s.rel[j] -= 0.05;
        if (in(OIL_PRODUCER, k.id()) && (x.kind == WK_BLOCKADE || x.kind == WK_INVASION || x.kind == WK_OCCUPATION))
            out.effects().addAll(RdFx.effects("oil=" + 0.15 * c.priceLevel()));
        if (x.kind == WK_OCCUPATION) s.unrest[i] = clamp(s.unrest[i] + (50 - x.front) * 0.01, 0, 100);
        String prot = protector(k.id());
        boolean nuclearRisk = k.nuclear() || (x.kind == WK_INVASION || x.kind == WK_OCCUPATION) && prot != null && data.country(data.index(prot)).nuclear();
        if (nuclearRisk && rng.uniform("world.war") < 0.015 * intensity) { spiral(s, c, x, out, rng); return true; }
        if (!x.authorized && x.weeks >= 9 && !x.wpVoted) {
            x.wpVoted = true;
            if (!c.houseMajority() || !c.senateMajority() || x.support < 45) {
                Question q = new Question();
                q.id = "w" + s.nextId++; q.kind = "warpowers"; q.ref = x.id;
                q.title = "Congress orders the troops home";
                q.body = "Under the War Powers Resolution, both chambers have voted to end " + lower(x.name) + " within thirty days. The war was never authorized.";
                q.options.addAll(List.of("Veto it and fight on", "Sign it and bring the troops home"));
                q.advice.addAll(List.of("Counsel: \"You can veto it; they probably can't override. But expect hearings, and worse.\"",
                        "Chief of staff: \"The country is tired of this. Take the exit Congress is offering.\""));
                q.recommended = x.front < 55 ? 1 : 0;
                q.due = s.week + 1;
                s.questions.add(q);
            }
        }
        if (x.timetable >= 0) {
            double span = Math.max(1, x.timetable - x.timetableStart), left = Math.max(0, x.timetable - s.week);
            x.troops = x.troops0 * left / span;
            x.front -= 0.05;
            if (s.week >= x.timetable) { finish(s, c, x, WS_TIMETABLE, out); return true; }
        }
        if (x.handoff >= 0) {
            x.troops = Math.max(0.85 * x.troops, 0.2 * x.need);
            if (s.week >= x.handoff) { finish(s, c, x, WS_HANDED, out); return true; }
        }
        return false;
    }

    void spiral(ForeignState s, Context c, War x, Outcome out, DeterministicRandomManager rng) {
        double dead = 3000 + 3000 * rng.uniform("world.war");
        x.kia += dead; s.kia += (int) dead;
        s.defcon = 1;
        out.effects().add(new Effect.FinancialShock(-5));
        out.effects().addAll(RdFx.effects("oil=" + 20 * c.priceLevel()));
        out.effects().add(new Effect.Performance(-8));
        out.news().add(news(c, News.Kind.BAD, (x.name + " spirals out of control").toUpperCase(Locale.ROOT),
                "Thousands of Americans are killed in a single day of escalation. The world's leaders force a ceasefire.", 6));
        end(s, c, x, WS_AGREED, out);
        s.defcon = 2;
    }

    void victory(ForeignState s, Context c, War x, Outcome out) {
        if (x.kind == WK_INVASION) {
            Country k = data.country(x.enemy);
            topple(s, c, x.enemy, "an American invasion", out);
            x.kind = WK_OCCUPATION; x.name = warName(k.name(), WK_OCCUPATION); x.front = 50; x.drift = pace(x.enemy, WK_OCCUPATION);
            x.lethal = 0.05 * (protector(k.id()) != null ? 3 : 1);
            out.effects().add(new Effect.Rally(3)); out.effects().add(new Effect.Performance(2));
            info(s, "The government has fallen", "American forces hold the capital, and a transitional authority is in charge. Now comes the hard part: insurgents are already "
                    + "attacking, and the country will need years of help. You can hand over to the new government once it can hold its ground, set a timetable, or stay.");
            return;
        }
        finish(s, c, x, x.kind == WK_BLOCKADE ? WS_AGREED : WS_WON, out);
    }

    void end(ForeignState s, Context c, War x, int how, Outcome out) {
        if (!x.fighting()) return;
        x.stage = how; x.ended = s.week;
        String name = x.name.toUpperCase(Locale.ROOT);
        String head = switch (how) {
            case WS_WON -> name + " ENDS IN VICTORY";
            case WS_LOST -> name + " ENDS IN DEFEAT";
            case WS_TIMETABLE -> name + " ENDS AS THE LAST TROOPS COME HOME";
            case WS_HANDED -> name + ": AMERICAN FORCES HAND OVER AND COME HOME";
            case WS_DECLARED -> "PRESIDENT DECLARES VICTORY: " + name + " IS OVER";
            default -> name + " ENDS IN AN AGREEMENT";
        };
        double years = Math.max(1, x.weeks) / 52.0;
        out.news().add(news(c, how == WS_LOST ? News.Kind.BAD : News.Kind.WORLD, head, Fmt.format("%,.0f Americans died over %s.", x.kia, years < 1.5 ? "a year" : Fmt.format("%.0f years", years)), 5));
        s.record.add(stamp(c) + lower(x.name) + " ended (" + WAR_STAGE[how].toLowerCase(Locale.ROOT) + ")");
        switch (how) {
            case WS_WON -> { out.effects().add(new Effect.Performance(1.5 + x.stakes)); out.effects().add(new Effect.Rally(2 + x.stakes)); }
            case WS_LOST -> { out.effects().add(new Effect.Performance(-3)); out.effects().add(new Effect.IssueStanding(Issue.CHINA, -0.5)); }
            case WS_AGREED -> out.effects().add(new Effect.Performance(1.5));
            default -> { }
        }
        x.troops = 0;
        info(s, how == WS_WON ? "Victory" : how == WS_LOST ? "Defeat" : "The War Is Over", cap(head.toLowerCase(Locale.ROOT)) + ".");
    }

    void finish(ForeignState s, Context c, War x, int how, Outcome out) {
        double f = x.front;
        end(s, c, x, how, out);
        int i = x.enemy;
        switch (how) {
            case WS_TIMETABLE -> { if (f >= 55) out.effects().add(new Effect.Performance(1)); else { out.effects().add(new Effect.Performance(-1.5)); s.unrest[i] += 15; s.deter -= 3; } }
            case WS_HANDED -> { out.effects().add(new Effect.Performance(1)); if (f < 70) s.unrest[i] += 20; }
            case WS_DECLARED -> {
                if (f >= 70) { out.effects().add(new Effect.Performance(1)); out.effects().add(new Effect.Rally(1)); }
                else { out.effects().add(new Effect.Performance(-2)); s.deter -= 6; for (int j = 0; j < NC; j++) if (s.align[j] == AL_TREATY) s.rel[j] -= 4; }
            }
            case WS_WON -> {
                s.deter += 4;
                String id = data.country(i).id();
                if (x.kind == WK_STRIKES && id.equals("IR")) { s.iran -= 30; s.proxy += 8; }
                if (x.kind == WK_STRIKES && id.equals("KP")) s.nk -= 20;
                if (x.kind == WK_STRIKES && id.equals("SY")) s.unrest[i] += 10;
                if (x.kind == WK_NOFLY) { s.unrest[i] -= 25; s.refugees -= 5; }
                if (x.kind == WK_OCCUPATION) { s.unrest[i] -= 30; s.rel[i] = Math.max(s.rel[i], 40); }
            }
            default -> { }
        }
        s.rel[i] = Math.max(s.rel[i], -60);
        if (!s.toppled[i]) s.sanction[i] = Math.max(1, s.sanction[i] - 1);
        s.unrest[i] = clamp(s.unrest[i], 0, 100);
        s.deter = clamp(s.deter, 0, 100);
        warSpend(s, out);
    }

    // ---- the president's levers in a war (6.5) ----

    War fighting(ForeignState s, String id) {
        War x = s.war(id);
        if (x == null || !x.fighting()) throw new IllegalArgumentException("That war is over.");
        return x;
    }

    public String setTroops(ForeignState s, Context c, String id, double change, Outcome out) {
        War x = fighting(s, id);
        double t = clamp(x.troops + Math.signum(change) * Math.max(Math.abs(change) * x.troops, 0.05 * x.need), 0, 1.6 * x.need);
        if (t > x.troops) x.support = clamp(x.support - 2 / x.stakes, 5, 98);
        x.troops = t;
        warSpend(s, out);
        return Fmt.format("%,.0f American troops are now committed.", x.troops * 1000);
    }

    public String setStrategy(ForeignState s, String id, int strategy) {
        War x = fighting(s, id);
        x.strategy = Math.max(0, Math.min(3, strategy));
        return "The strategy: " + STRATEGY[x.strategy].toLowerCase(Locale.ROOT) + ".";
    }

    public String withdraw(ForeignState s, Context c, String id, Outcome out) {
        War x = fighting(s, id);
        end(s, c, x, x.front >= 60 ? WS_AGREED : WS_LOST, out);
        warSpend(s, out);
        return "The troops are coming home.";
    }

    public String declareVictory(ForeignState s, Context c, String id, Outcome out) {
        War x = fighting(s, id);
        boolean believed = x.front >= 70;
        finish(s, c, x, WS_DECLARED, out);
        return believed ? "You declare victory and bring the troops home. Most of the country agrees." : "You declare victory and bring the troops home. Few believe it, and allies and enemies take note.";
    }

    public String timetable(ForeignState s, Context c, String id, int weeks) {
        War x = fighting(s, id);
        if (x.timetable >= 0) throw new IllegalArgumentException("A timetable is already set.");
        x.timetable = s.week + weeks; x.timetableStart = s.week; x.troops0 = x.troops;
        x.support = clamp(x.support + 4, 5, 98);
        return "The last troops leave in " + (weeks >= 52 ? "a year." : weeks >= 26 ? "six months." : weeks + " weeks.");
    }

    public String handover(ForeignState s, Context c, String id) {
        War x = fighting(s, id);
        if (x.front < 55) throw new IllegalArgumentException("The front must hold first.");
        if (x.handoff >= 0) throw new IllegalArgumentException("A handover is under way.");
        x.handoff = s.week + 8;
        return "In eight weeks " + (x.kind == WK_OCCUPATION ? "the new government's forces" : "allies and UN peacekeepers") + " take over.";
    }

    public String escalate(ForeignState s, Context c, String id, Outcome out) {
        War x = fighting(s, id);
        x.troops = Math.min(1.6 * x.need, 1.5 * x.troops);
        x.strategy = 2; x.front = clamp(x.front + 4, 0, 100); x.support = clamp(x.support - 4, 5, 98);
        if (data.country(x.enemy).nuclear()) s.defcon = Math.max(1, s.defcon - 1);
        out.news().add(news(c, News.Kind.WORLD, ("The president escalates " + lower(x.name)).toUpperCase(Locale.ROOT), null, 3.5));
        warSpend(s, out);
        return "More troops, and the offensive.";
    }

    public double authorizeOdds(ForeignState s, Context c, int i) {
        double host = s.rel[i] < -70 ? 0.8 : s.rel[i] < -40 ? 0.3 : s.rel[i] < 0 ? -1.5 : -2.8;
        host -= s.align[i] == AL_TREATY ? 2.5 : s.align[i] == AL_PARTNER ? 1 : 0;
        if (data.country(i).nuclear()) host -= 0.9;
        host -= 0.2;
        double h = yes(c, c.house(), 1.2 + host, host - 0.3, 1.8), se = yes(c, c.senate(), 1.2 + host, host - 0.3, 1.8);
        return logistic((h - 218) / 9) * logistic((se - 60 + 0.5) / 3);
    }

    public String authorize(ForeignState s, Context c, int i, Outcome out, DeterministicRandomManager rng) {
        boolean ok = rng.uniform("world.authorize") < authorizeOdds(s, c, i);
        Country k = data.country(i);
        if (ok) {
            s.authorized[i] = Math.max(1, s.week);
            for (War x : s.wars) if (x.fighting() && x.enemy == i) { x.authorized = true; x.legit = clamp(x.legit + 12, 5, 70); x.support = clamp(x.support + 10, 5, 98); }
            out.effects().add(new Effect.Rally(1));
            out.news().add(news(c, News.Kind.WORLD, ("Congress authorizes the use of force against " + k.name()).toUpperCase(Locale.ROOT), null, 3.5));
            return "Congress authorizes the use of force.";
        }
        out.effects().add(new Effect.Performance(-0.5));
        for (War x : s.wars) if (x.fighting() && x.enemy == i) x.support = clamp(x.support - 8, 5, 98);
        out.news().add(news(c, News.Kind.BAD, ("Congress refuses to authorize force against " + k.name()).toUpperCase(Locale.ROOT), null, 3.5));
        return "Congress refuses.";
    }

    void warPowers(ForeignState s, Context c, Question q, int k, Outcome out) {
        War x = s.war(q.ref);
        if (x == null || !x.fighting()) return;
        if (k == 0) {
            out.effects().add(new Effect.Performance(-0.5));
            out.effects().add(new Effect.Scandal(2));
            x.support = clamp(x.support - 3, 5, 98);
            out.news().add(news(c, News.Kind.BAD, "PRESIDENT VETOES THE WAR POWERS RESOLUTION; THE WAR GOES ON", null, 3.5));
        } else {
            x.timetable = s.week + 4; x.timetableStart = s.week; x.troops0 = x.troops;
        }
    }

    // =========================================================================================
    //  Orders from the Situation Room
    // =========================================================================================

    int country(String code) {
        int i = code == null ? -1 : data.index(code);
        if (i < 0) throw new IllegalArgumentException("No such country");
        return i;
    }

    /** Carries out one order; the message says what came of it (it is also kept as the room's last message). */
    public Outcome apply(ForeignState s, Context c, ForeignOrder o, DeterministicRandomManager rng) {
        Outcome out = Outcome.empty();
        String msg = switch (o) {
            case ForeignOrder.Call x -> call(s, c, country(x.country()), out);
            case ForeignOrder.Invite x -> invite(s, c, country(x.country()), rng);
            case ForeignOrder.Summit x -> proposeSummit(s, c, country(x.country()), out, rng);
            case ForeignOrder.Tariff x -> setTariff(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Sanction x -> setSanction(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Aid x -> setAid(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Operation x -> {
                if (x.index() < 0 || x.index() >= data.operations().size()) throw new IllegalArgumentException("No such operation");
                yield order(s, c, x.index(), out, rng);
            }
            case ForeignOrder.Covert x -> {
                if (x.index() < 0 || x.index() >= data.covert().size()) throw new IllegalArgumentException("No such program");
                yield covert(s, c, x.index(), out, rng);
            }
            case ForeignOrder.MilitaryAid x -> milAid(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Arms x -> arms(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Troops x -> troops(s, c, country(x.country()), x.step(), out);
            case ForeignOrder.Ties x -> ties(s, c, country(x.country()), x.level(), out);
            case ForeignOrder.Peacekeepers x -> peacekeepers(s, c, country(x.country()), x.on(), out);
            case ForeignOrder.TradeDeal x -> tradeDeal(s, c, country(x.country()), out, rng);
            case ForeignOrder.Treaty x -> treaty(s, c, country(x.country()), out, rng);
            case ForeignOrder.CovertProgram x -> startCovert(s, c, country(x.country()), x.kind());
            case ForeignOrder.Objective x -> tryObjective(s, c, x.id(), out, rng);
            case ForeignOrder.Posture x -> {
                if (x.area() < 0 || x.area() > 2) throw new IllegalArgumentException("No such area");
                yield posture(s, c, x.area(), x.level(), out);
            }
            case ForeignOrder.Alliance x -> switch (x.what()) {
                case "article5" -> article5(s, c);
                case "press" -> pressAllies(s, c);
                case "drills" -> drills(s, c);
                default -> throw new IllegalArgumentException("No such step");
            };
            case ForeignOrder.Authorize x -> authorize(s, c, country(x.country()), out, rng);
            case ForeignOrder.StartWar x -> startWar(s, c, country(x.country()), x.kind(), out);
            case ForeignOrder.WarTroops x -> setTroops(s, c, x.war(), x.change(), out);
            case ForeignOrder.WarStrategy x -> setStrategy(s, x.war(), x.strategy());
            case ForeignOrder.EndWar x -> switch (x.how()) {
                case "withdraw" -> withdraw(s, c, x.war(), out);
                case "declare" -> declareVictory(s, c, x.war(), out);
                case "timetable26" -> timetable(s, c, x.war(), 26);
                case "timetable52" -> timetable(s, c, x.war(), 52);
                case "handover" -> handover(s, c, x.war());
                case "escalate" -> escalate(s, c, x.war(), out);
                default -> throw new IllegalArgumentException("No such step");
            };
            case ForeignOrder.Answer x -> {
                Question q = s.question(x.question());
                if (q == null) throw new IllegalArgumentException("That question is no longer on the desk");
                if (x.option() < 0 || x.option() >= q.options.size()) throw new IllegalArgumentException("No such answer");
                answer(s, c, q, x.option(), out, rng);
                yield null;
            }
        };
        if (msg != null) s.message = msg;
        return out;
    }

    // =========================================================================================
    //  The Taiwan war, fought on this game's operational model
    // =========================================================================================

    /** The game engine's war over Taiwan has ended: how it ended sets the Strait (Resolute Desk's tw_war outcomes). */
    public void taiwanWarOver(ForeignState s, Context c, String status, int killed, Outcome out) {
        s.taiwanWar = false;
        s.kia += killed;
        int cn = data.index("CN"), tw = data.index("TW");
        switch (status) {
            case "DEFENDED" -> { s.twStage = 3; s.twt = clamp(s.twt - 20, 0, 100); s.rel[cn] = clamp(s.rel[cn] - 60, -100, 100); s.pacific = clamp(s.pacific + 15, 0, 100); }
            case "STALEMATE" -> { s.twStage = 4; s.rel[cn] = clamp(s.rel[cn] - 50, -100, 100); }
            default -> {
                s.twStage = 4; s.twt = 90;
                s.pacific = clamp(s.pacific - 35, 0, 100);
                s.rel[tw] = clamp(s.rel[tw] - 50, -100, 100); s.rel[data.index("JP")] -= 25; s.rel[data.index("KR")] -= 20;
            }
        }
        s.record.add(stamp(c) + "the war over Taiwan ended (" + status.toLowerCase(Locale.ROOT) + ")");
    }

    // =========================================================================================
    //  The President's Daily Brief (8.1)
    // =========================================================================================

    void brief(ForeignState s, Context c) {
        s.brief.clear();
        for (War x : s.wars) if (x.fighting()) {
            String nm = x.name.startsWith("The ") ? x.name.substring(4) : x.name;
            s.brief.add((x.front < 35 ? 3 : 2) + "|" + nm.toUpperCase(Locale.ROOT) + ": " + (x.front >= 70 ? "Our forces are advancing." : x.front >= 45 ? "The front is holding." : "The enemy is gaining ground.")
                    + Fmt.format(" %,.0f Americans deployed; %,.0f killed so far. Support at home: %.0f%%.", x.troops * 1000, x.kia, x.support));
        }
        if (s.taiwanWar) s.brief.add("3|TAIWAN: The war in the Strait goes on. Our forces are engaged.");
        else if (s.twStage >= 2) s.brief.add("3|TAIWAN: The PLA is " + (s.twStage >= 3 ? "enforcing a " + TW_STAGE[s.twStage].toLowerCase(Locale.ROOT) : "drilling around the island")
                + ". Our deterrence index is " + Math.round(s.deter) + ".");
        else if (s.twt > 45) s.brief.add("2|CHINA: Pressure on Taiwan is building: more incursions into its air-defense zone.");
        if (s.uaStage == 2) s.brief.add((s.ukr < 35 ? 3 : 2) + "|UKRAINE: " + (s.ukr < 35 ? "Ukrainian lines are under severe strain." : "The front is grinding but holding.")
                + " Talks index " + Math.round(s.talks) + ".");
        if (s.iran > 80) s.brief.add("3|IRAN: Enrichment is near weapons grade. Breakout could be weeks away.");
        else if (s.iran > 60) s.brief.add("2|IRAN: Enrichment continues at a steady pace.");
        if (s.redsea > 55) s.brief.add("2|RED SEA: Houthi attacks on shipping are rising; insurers are raising rates.");
        if (s.proxy > 55) s.brief.add("2|IRAQ AND SYRIA: Militias are planning more attacks on US bases.");
        if (s.nk > 70) s.brief.add("2|NORTH KOREA: Activity at the nuclear test site.");
        if (s.terror > 50) s.brief.add("3|TERRORISM: Increased chatter about plots against Americans.");
        if (s.cyber > 55) s.brief.add("2|CYBER: Ransomware groups are probing pipelines and hospitals.");
        if (s.migr > 65) s.brief.add("2|THE BORDER: Crossings are rising toward a surge.");
        if (s.brief.isEmpty()) s.brief.add("1|A quiet week. Nothing urgent overnight.");
    }

    // =========================================================================================
    //  The hands-off president's diplomacy (Resolute Desk's Decisions.autoExec), for watch mode
    // =========================================================================================

    public void autoExec(ForeignState s, Context c, Outcome out, DeterministicRandomManager rng) {
        int w = s.week;
        try {
            if (w % 5 == 2) {
                String[] friends = {"GB", "JP", "DE", "FR", "CA", "MX", "KR", "IN"};
                int i = data.index(friends[(w / 5) % 8]);
                if (i >= 0 && s.align[i] != AL_ADVERSARY) call(s, c, i, out);
            }
            if (w % 7 == 3) call(s, c, data.index(w % 14 == 3 ? "CN" : "RU"), out);
            if (w % 40 == 20 && s.summitCountry < 0) proposeSummit(s, c, data.index(s.uaStage == 2 ? "RU" : "CN"), out, rng);
            if (w % 13 == 6) {
                String[] guests = {"GB", "JP", "IN", "DE", "FR", "CA", "AU", "KR"};
                int i = data.index(guests[(w / 13) % 8]);
                if (i >= 0 && s.visitAt[i] < 0 && s.align[i] != AL_ADVERSARY) invite(s, c, i, rng);
            }
        } catch (IllegalArgumentException refused) {
            // the hands-off president skips what cannot be done this week
        }
    }

    // =========================================================================================
    //  What the rest of the game reads
    // =========================================================================================

    /** The tension of this game's three hot spots (0-100), for the markets and the old diplomacy screens. */
    public double[] tensions(ForeignState s) {
        double me = 0.35 * s.redsea + 0.25 * s.iran + 0.2 * s.gaza + 0.2 * s.proxy;
        double ee = s.uaStage == 2 ? 50 + (50 - s.ukr) * 0.5 + 10 : s.uaStage == 1 ? 40 : 25;
        return new double[]{s.taiwanWar ? 95 : s.twt, me, clamp(ee, 0, 100)};
    }

    /** Mean relations with the treaty allies (the verdict's international measure). */
    public double alliesRelations(ForeignState s) {
        double a = 0;
        int n = 0;
        for (int i = 0; i < NC; i++) if (s.align[i] == AL_TREATY) { a += s.rel[i]; n++; }
        return n == 0 ? 50 : a / n;
    }

    // =========================================================================================
    //  Helpers
    // =========================================================================================

    News news(Context c, News.Kind kind, String head, String body, double weight) { return new News(c.date(), kind, head, body, weight, null); }

    String stamp(Context c) {
        String m = c.date().getMonth().name();
        return m.charAt(0) + m.substring(1).toLowerCase(Locale.ROOT) + " " + c.date().getDayOfMonth() + ", " + c.date().getYear() + ": ";
    }

    String around(Context c, int weeks) {
        LocalDate d = c.date().plusWeeks(weeks);
        String m = d.getMonth().name();
        return m.charAt(0) + m.substring(1).toLowerCase(Locale.ROOT) + " " + d.getDayOfMonth();
    }

    /** "the United Kingdom", "the Philippines", otherwise the name (Resolute Desk's {@code cnameIn}). */
    public String nameIn(int i) {
        String n = data.country(i).name();
        return n.startsWith("United ") || n.startsWith("Philippines") ? "the " + n : n;
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    static String lower(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
    static String last(String name) { int i = name.lastIndexOf(' '); return i < 0 ? name : name.substring(i + 1); }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }
}
