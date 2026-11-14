package game.mandate.ui;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.game.GameSetup;
import game.mandate.core.time.Phase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Saves for the browser's election and mode checks (mandate-web/src/test/browser/election.js and modes.js), not
 * shipped with the game:
 * <ul>
 *   <li>the eve of each presidential election, one the player wins and one the player loses, in 2028 and 2032, and two
 *       saved on the night itself before it is watched;</li>
 *   <li>the eve of 2028 in Campaign Only, won and lost (the same games, in that mode);</li>
 *   <li>the last turn of an Individual Crisis, Hormuz and Taiwan, from the shipped governing save;</li>
 *   <li>the nights of the races to succeed the president: 2036, and 2032 without the president on the ballot;</li>
 *   <li>the last week of the first hundred days.</li>
 * </ul>
 * Usage: java -cp mandate.jar game.mandate.ui.ElectionSaves [out-dir] [shipped saves dir]
 */
public final class ElectionSaves {
    private ElectionSaves() {}

    public static void main(String[] args) throws java.io.IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "mandate-web/target/preview/saves/test");
        Files.createDirectories(out);
        boolean win28 = false, lose28 = false, win32 = false, lose32 = false;
        for (long seed = 1; seed < 80 && !(win28 && lose28 && win32 && lose32); seed++) {
            GameEngine g = new GameEngine(Fixtures.setup(seed, GameSetup.Mode.FULL_GAME));
            int guard = 0;
            while (!g.over() && g.date().isBefore(LocalDate.of(2028, 11, 6)) && guard++ < 400) AutoPilot.playTurn(g);
            if (g.phase() != Phase.GENERAL_CAMPAIGN_2028) continue;
            String eve = SaveManager.save(g);
            AutoPilot.playTurn(g);
            String night = SaveManager.save(g);
            boolean won = g.result2028().map(r -> r.winner() == g.player().party()).orElse(false);
            if (won && !win28) { write(out, "eve2028-win", eve); write(out, "night2028-win", night); write(out, "eve2028-campaign-win", campaignOnly(eve)); win28 = true; }
            if (!won && !lose28) { write(out, "eve2028-lose", eve); write(out, "eve2028-campaign-lose", campaignOnly(eve)); lose28 = true; }
            if (!won || (win32 && lose32)) continue;
            guard = 0;
            while (!g.over() && g.phase() != Phase.REELECTION_CAMPAIGN_2032 && guard++ < 1500) AutoPilot.playTurn(g);
            while (!g.over() && g.phase() == Phase.REELECTION_CAMPAIGN_2032 && g.date().isBefore(LocalDate.of(2032, 11, 1)) && guard++ < 1800) AutoPilot.playTurn(g);
            if (g.phase() != Phase.REELECTION_CAMPAIGN_2032) continue;
            String eve32 = SaveManager.save(g);
            while (g.phase() == Phase.REELECTION_CAMPAIGN_2032 && guard++ < 1900) AutoPilot.playTurn(g);
            String night32 = SaveManager.save(g);
            boolean won32 = g.result2032().map(r -> r.winner() == g.player().party()).orElse(false);
            if (won32 && !win32) { write(out, "eve2032-win", eve32); win32 = true; }
            if (!won32 && !lose32) { write(out, "eve2032-lose", eve32); write(out, "night2032-lose", night32); lose32 = true; }
        }
        if (!(win28 && lose28 && win32 && lose32)) throw new IllegalStateException("not every outcome was found");
        // Individual Crisis, one turn from its end (the staff's playbook, from the shipped governing save)
        String base = Files.readString(Path.of(args.length > 1 ? args[1] : "mandate-web/src/main/preview/saves").resolve("governing.json"));
        for (String kind : new String[]{"hormuz", "taiwan"}) {
            GameEngine c = SaveManager.load(base);
            c.apply(new game.mandate.core.command.Command.IndividualCrisis(kind, 1));
            int turns = 0;
            while (!c.over() && turns < 6000) { AutoPilot.playTurn(c); turns++; }
            if (!c.over()) throw new IllegalStateException("the " + kind + " crisis did not end");
            GameEngine again = SaveManager.load(base);       // the same game, stopped one turn short
            again.apply(new game.mandate.core.command.Command.IndividualCrisis(kind, 1));
            for (int i = 0; i < turns - 1; i++) AutoPilot.playTurn(again);
            write(out, "crisis-" + kind + "-last", SaveManager.save(again));
        }
        // the races to succeed the president, on their nights: 2036 after two terms, and 2032 when the president did not run
        // (the lifecycle run's games: LifecycleTest.setup(0, 1) and setup(9, 10))
        write(out, "night2036", SaveManager.save(firstNight(1, GameSetup.Mode.FULL_GAME, 2036)));
        write(out, "night2032-successor", SaveManager.save(firstNight(10, GameSetup.Mode.GOVERNING_ONLY, 2032)));
        // the last week of the first hundred days
        GameEngine d = Fixtures.won(GameSetup.Mode.FULL_GAME, x -> x.phase() == Phase.FIRST_100_DAYS && x.hundredDays().map(h -> h.week() >= 14).orElse(false));
        write(out, "day100-eve", SaveManager.save(d));
    }

    /** The game played by the staff until that year's election night is waiting to be watched. */
    /** The first game from this seed on (Shapiro against Vance) whose night of that year is a race to succeed the president. */
    static GameEngine firstNight(long fromSeed, GameSetup.Mode mode, int year) {
        for (long seed = fromSeed; seed < fromSeed + 30; seed++) {
            try {
                GameEngine g = toNight(new GameSetup(seed, game.mandate.core.state.Party.DEMOCRAT, "bg_shapiro", "bg_vance",
                        game.mandate.core.game.Scenario.values()[0], GameSetup.Difficulty.values()[0], mode, true, null), year);
                if (g.electionKind(year).equals("succession")) return g;
            } catch (IllegalStateException e) { /* not this game: the next */ }
        }
        throw new IllegalStateException("no " + year + " succession night in thirty games");
    }

    static GameEngine toNight(GameSetup s, int year) {
        GameEngine g = GameEngine.start(s);
        int guard = 0;
        while (!g.over() && g.electionNightPending() != year && guard++ < 6000) AutoPilot.playTurn(g);
        if (g.electionNightPending() != year) throw new IllegalStateException("no " + year + " night in this game");
        return g;
    }

    /** The same game, as Campaign Only: only what happens after the vote differs. */
    static String campaignOnly(String save) {
        java.util.Map<String, Object> m = game.mandate.core.data.Json.object(game.mandate.core.data.Json.parse(save));
        game.mandate.core.data.Json.object(m.get("setup")).put("mode", GameSetup.Mode.CAMPAIGN_ONLY.name());
        game.mandate.core.data.Json.object(m.get("world")).put("mode", GameSetup.Mode.CAMPAIGN_ONLY.name());
        return game.mandate.core.data.Json.write(m);
    }

    private static void write(Path dir, String name, String save) throws java.io.IOException {
        Files.writeString(dir.resolve(name + ".json"), save);
        System.out.println("wrote " + name);
    }
}
