package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.taiwan.TaiwanOrder;
import game.mandate.core.taiwan.TaiwanState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static game.mandate.core.engine.EngineFixtures.playUntil;

/**
 * The Taiwan crisis's balance (docs/BALANCE_TAIWAN.md): Individual Crisis played many times by five styles of president, on
 * each preset, and the results counted. Off by default; run with {@code -Dmandate.taiwanBalance=N} (N worlds; each world
 * also starts the crisis on several weeks, for variety).
 */
class TaiwanBalanceRun {

    /** A style of play: what the president orders on a turn of the crisis, before ending it. */
    enum Style { STAFF, IDLE, DIPLOMAT, DETERRER, HAWK, BACKDOWN, EARLY_PREP, ALLIES, NAVAL, NO_ESCALATE, SKILLED, PATIENT }

    static final String[] US_PROGRAMMES = {"readiness", "subs", "bombers", "fighters", "disperse", "munitions", "sealift", "arms", "declassify",
            "departure", "showforce", "littoral", "mobilise", "huntforward", "advisers"};

    @Test
    void run() {
        int worlds = Integer.getInteger("mandate.taiwanBalance", 0);
        Assumptions.assumeTrue(worlds > 0, "the balance run is off; -Dmandate.taiwanBalance=N runs it");
        int variants = Integer.getInteger("mandate.taiwanVariants", 3);
        Map<String, Map<String, Integer>> table = new TreeMap<>();
        List<String> lines = new ArrayList<>();
        for (long seed = 1; seed <= worlds; seed++) {
            String base = ModesTest.governingSave(seed);
            for (int wk = 0; wk < variants; wk++) {
                String start = base;
                long crisisSeed = 7919L * (wk + 1) + seed;
                for (int preset : presets()) for (Style st : styles()) {
                    String r = play(start, preset, st, crisisSeed);
                    table.computeIfAbsent(st + " p" + preset, k -> new LinkedHashMap<>()).merge(r.split(":")[0], 1, Integer::sum);
                    lines.add(seed + "/" + wk + " p" + preset + " " + st + " -> " + r);
                }
            }
        }
        lines.forEach(System.out::println);
        System.out.println("---- by style and preset (WON / PARTIAL / LOST)");
        table.forEach((k, v) -> System.out.println(k + "  " + v));
    }

    @Test
    void trace() {
        Assumptions.assumeTrue(Boolean.getBoolean("mandate.taiwanTrace"), "the trace is off; -Dmandate.taiwanTrace=true runs it");
        GameEngine g = SaveManager.load(ModesTest.governingSave(1));
        g.apply(new Command.IndividualCrisis("taiwan", Integer.getInteger("mandate.preset", 1), Long.getLong("mandate.crisisSeed", 0L)));
        Style st = Style.valueOf(System.getProperty("mandate.style", "DIPLOMAT"));
        boolean opened = false, warSeen = false;
        int lastDay = -1, guard = 0;
        while (!g.over() && guard++ < 8000) {
            if (g.taiwanActive()) {
                TaiwanState s = g.taiwan().orElseThrow();
                if (!opened) { opening(g, st); opened = true; }
                if (s.atWar() && !warSeen) { atWar(g, st); warSeen = true; }
                daily(g, st, s);
                if (s.day() != lastDay) {
                    lastDay = s.day();
                    System.out.printf("day %3d %-8s %s stab %5.1f will %5.1f elec %4.2f support %4.1f rung %d/%d%n", s.day(), s.phase(), s.plan(), s.prcStability(), s.will(),
                            s.electricity(), s.usSupport(), s.usRung(), s.chinaRung());
                }
            }
            EngineFixtures.answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        System.out.println(g.individualCrisis().map(c -> c.report().outcome() + ": " + c.report().reason()).orElse("?"));
    }

    static int[] presets() {
        String only = System.getProperty("mandate.taiwanPresets");
        if (only == null || only.isBlank()) return new int[]{0, 1, 2};
        return java.util.Arrays.stream(only.split(",")).mapToInt(x -> Integer.parseInt(x.trim())).toArray();
    }

    static List<Style> styles() {
        String only = System.getProperty("mandate.taiwanStyles");
        if (only == null || only.isBlank()) return List.of(Style.values());
        List<Style> l = new ArrayList<>();
        for (String x : only.split(",")) l.add(Style.valueOf(x.trim()));
        return l;
    }

    static String play(String base, int preset, Style st, long crisisSeed) {
        GameEngine g = SaveManager.load(base);
        g.apply(new Command.IndividualCrisis("taiwan", preset, crisisSeed));
        boolean opened = false, warSeen = false;
        int guard = 0;
        double deterSeen = -1, minStab = 100, minWill = 100;
        while (!g.over() && guard++ < 8000) {
            if (g.taiwanActive() && !g.taiwan().orElseThrow().atWar()) deterSeen = g.taiwan().orElseThrow().deterrenceAsSeen();
            if (g.taiwanActive() && g.taiwan().orElseThrow().atWar()) {
                minStab = Math.min(minStab, g.taiwan().orElseThrow().prcStability());
                minWill = Math.min(minWill, g.taiwan().orElseThrow().will());
            }
            if (st == Style.STAFF) { AutoPilot.playTurn(g); continue; }
            if (g.taiwanActive()) {
                TaiwanState s = g.taiwan().orElseThrow();
                if (!opened) { opening(g, st); opened = true; }
                if (s.atWar() && !warSeen) { atWar(g, st); warSeen = true; }
                daily(g, st, s);
            }
            EngineFixtures.answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        var c = g.individualCrisis();
        if (c.isEmpty() || c.get().report() == null) return "NONE: not ended";
        var r = c.get().report();
        String why = g.taiwan().map(t -> t.plan() + ": " + t.endReason()).orElse("");
        return r.result() + ": " + r.outcome() + " (" + r.days() + " days; deterrence seen " + Math.round(deterSeen) + ", PRC stability min " + Math.round(minStab) + ", will min " + Math.round(minWill) + ") " + why;
    }

    static void order(GameEngine g, TaiwanOrder o) {
        try { g.apply(new Command.Taiwan(o)); } catch (IllegalCommandException e) { /* refused: not now */ }
    }

    static void opening(GameEngine g, Style st) {
        switch (st) {
            case DIPLOMAT -> { order(g, new TaiwanOrder.Rung(0)); order(g, new TaiwanOrder.Mainland(0)); }
            case DETERRER -> {
                order(g, new TaiwanOrder.Rung(0)); order(g, new TaiwanOrder.Mainland(0));
                for (String p : new String[]{"readiness", "subs", "bombers", "munitions", "arms", "declassify", "advisers", "mobilise", "disperse", "fighters"})
                    order(g, new TaiwanOrder.Programme(p, true));
                order(g, new TaiwanOrder.TaiwanAsk("mobilise"));
            }
            case HAWK -> {
                for (String p : US_PROGRAMMES) order(g, new TaiwanOrder.Programme(p, true));
                order(g, new TaiwanOrder.TaiwanAsk("mobilise"));
                order(g, new TaiwanOrder.TaiwanAsk("disperse"));
                order(g, new TaiwanOrder.AllyRole("JPN", 3));
                order(g, new TaiwanOrder.AllyRole("AUS", 3));
                order(g, new TaiwanOrder.AllyRole("PHL", 1));
                order(g, new TaiwanOrder.Rung(1));
                order(g, new TaiwanOrder.Fleet(1));
            }
            case EARLY_PREP, SKILLED, PATIENT -> {
                for (String p : US_PROGRAMMES) if (!p.equals("departure") || st == Style.EARLY_PREP) order(g, new TaiwanOrder.Programme(p, true));
                order(g, new TaiwanOrder.TaiwanAsk("mobilise"));
                order(g, new TaiwanOrder.TaiwanAsk("disperse"));
            }
            default -> { }
        }
    }

    static void atWar(GameEngine g, Style st) {
        switch (st) {
            case HAWK -> {
                order(g, new TaiwanOrder.Rung(4));
                order(g, new TaiwanOrder.Mainland(1));
                order(g, new TaiwanOrder.Submarines(0));
                order(g, new TaiwanOrder.Convoys(true));
                order(g, new TaiwanOrder.MunitionsPolicy(1));
                order(g, new TaiwanOrder.AirPriority(0.6, 0.1, 0.2, 0.1));
            }
            case SKILLED -> {
                TaiwanState t = g.taiwan().orElseThrow();
                boolean siege = t.plan().equals("blockade") || t.plan().equals("quarantine");
                order(g, new TaiwanOrder.Mainland(0));
                order(g, new TaiwanOrder.Rung(siege ? 2 : 3));
                order(g, new TaiwanOrder.Convoys(true));
                order(g, new TaiwanOrder.Submarines(siege ? 1 : 0));
                order(g, new TaiwanOrder.AirPriority(siege ? 0.6 : 0.7, 0, siege ? 0.2 : 0.15, siege ? 0.2 : 0.15));
                if (siege) order(g, new TaiwanOrder.TaiwanAsk("ration"));
            }
            case PATIENT -> {
                TaiwanState t = g.taiwan().orElseThrow();
                boolean siege = t.plan().equals("blockade") || t.plan().equals("quarantine");
                order(g, new TaiwanOrder.Mainland(0));
                order(g, new TaiwanOrder.Convoys(true));
                order(g, new TaiwanOrder.Submarines(siege ? 1 : 0));
                order(g, new TaiwanOrder.AirPriority(siege ? 0.6 : 0.7, 0, siege ? 0.2 : 0.15, siege ? 0.2 : 0.15));
                if (siege) order(g, new TaiwanOrder.TaiwanAsk("ration"));
            }
            case NAVAL -> {
                order(g, new TaiwanOrder.Convoys(true));
                order(g, new TaiwanOrder.Submarines(0));
                order(g, new TaiwanOrder.AirPriority(0.7, 0, 0.15, 0.15));
            }
            case NO_ESCALATE -> { order(g, new TaiwanOrder.Mainland(0)); }
            default -> { }
        }
    }

    static void daily(GameEngine g, Style st, TaiwanState s) {
        if (s.over()) return;
        switch (st) {
            case DIPLOMAT, DETERRER, PATIENT -> {
                if (s.atWar()) {
                    for (String m : new String[]{"sanctions1", "sanctions2", "sanctions3", "swift", "freeze", "chips"}) order(g, new TaiwanOrder.Economic(m));
                    String[] co = {"EU", "JPN", "AUS", "KOR", "GBR", "PHL", "IND"};
                    order(g, new TaiwanOrder.Diplomacy(co[s.day() % co.length], "sanctions"));
                    if (s.day() % 7 == 0) order(g, new TaiwanOrder.TaiwanAsk("ration"));
                }
                if (!s.atWar()) {
                    String[][] plan = {{"JPN", "basing"}, {"PHL", "basing"}, {"AUS", "consult"}, {"JPN", "airports"}, {"KOR", "assurance"}, {"EU", "sanctions"}};
                    String[] p = plan[s.day() % plan.length];
                    order(g, new TaiwanOrder.Diplomacy(p[0], p[1]));
                    if (s.day() == 3) order(g, new TaiwanOrder.Economic("sanctions1"));
                }
                String[] who = {"JPN", "AUS", "PHL", "KOR", "EU"};
                order(g, new TaiwanOrder.Diplomacy(who[s.day() % who.length], s.day() % 2 == 0 ? "consult" : "assurance"));
                if (s.day() % 6 == 0) order(g, new TaiwanOrder.Economic("sanctions1"));
                if (s.offerFrom() >= 0) order(g, new TaiwanOrder.Answer(true));
            }
            case ALLIES, SKILLED -> {
                if (s.atWar() && st == Style.SKILLED && s.usFighting()) {
                    String[] co = {"JPN", "AUS", "PHL", "GBR"};
                    order(g, new TaiwanOrder.Diplomacy(co[s.day() % co.length], s.day() % 2 == 0 ? "combat" : "basing"));
                }
                if (!s.atWar()) {
                    String[][] plan = {{"JPN", "basing"}, {"PHL", "basing"}, {"AUS", "consult"}, {"JPN", "airports"}, {"KOR", "assurance"}, {"EU", "sanctions"}};
                    String[] p = plan[s.day() % plan.length];
                    order(g, new TaiwanOrder.Diplomacy(p[0], p[1]));
                }
            }
            case BACKDOWN -> {
                if (s.atWar() && s.warHours() > 24 * 4 && s.offerFrom() < 0) order(g, new TaiwanOrder.Ceasefire(1));
                if (s.offerFrom() >= 0) order(g, new TaiwanOrder.Answer(true));
            }
            default -> { }
        }
    }
}
