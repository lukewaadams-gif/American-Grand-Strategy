package game.mandate.ui;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Real game states for rendering and checking every screen: built by the autopilot from fixed seeds, so the
 * screenshots and the automated checks see the same moments every run.
 */
public final class Fixtures {
    private Fixtures() {}

    static GameSetup setup(long seed, GameSetup.Length length) {
        boolean dem = seed % 2 == 0;
        return new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? "d_reyes" : "r_marsh", dem ? "r_harlan" : "d_whitfield", Scenario.TOSS_UP,
                GameSetup.Difficulty.STANDARD, length, false, null);
    }

    static GameEngine until(GameEngine g, Predicate<GameEngine> stop) {
        int guard = 0;
        while (!g.over() && !stop.test(g) && guard++ < 1200) AutoPilot.playTurn(g);
        return g;
    }

    /** A won election, played to the moment {@code stop} holds. */
    static GameEngine won(GameSetup.Length length, Predicate<GameEngine> stop) {
        for (long seed = 1; seed < 200; seed++) {
            GameEngine g = until(new GameEngine(setup(seed, length)), x -> x.phase() == Phase.TRANSITION);
            if (g.phase() != Phase.TRANSITION) continue;
            until(g, stop);
            if (!g.over() || stop.test(g)) return g;
        }
        throw new IllegalStateException("no seed reached the state");
    }

    /** The named moments, each as a save text (so every render starts from a fresh copy). */
    public static Map<String, String> moments() {
        Map<String, String> m = new LinkedHashMap<>();
        GameEngine campaign = until(new GameEngine(setup(2, GameSetup.Length.HUNDRED_DAYS)), g -> g.polls().size() >= 9);
        m.put("campaign", SaveManager.save(campaign));
        GameEngine debate = until(new GameEngine(setup(2, GameSetup.Length.HUNDRED_DAYS)), g -> g.debateThisWeek().isPresent());
        m.put("debate", SaveManager.save(debate));
        m.put("transition", SaveManager.save(won(GameSetup.Length.HUNDRED_DAYS, g -> g.phase() == Phase.TRANSITION)));
        m.put("week1", SaveManager.save(won(GameSetup.Length.HUNDRED_DAYS, g -> g.phase() == Phase.FIRST_100_DAYS)));
        m.put("crisis", SaveManager.save(won(GameSetup.Length.HUNDRED_DAYS, g -> g.phase() == Phase.FIRST_100_DAYS
                && g.hundredDays().get().crisis() == CrisisOutcome.ACTIVE && !g.hundredDays().get().pending().isEmpty()
                && g.hundredDays().get().attention() >= 1 && g.hundredDays().get().stage() != game.mandate.core.opening.Types.BillStage.NOT_DRAFTED
                && g.hundredDays().get().stage() != game.mandate.core.opening.Types.BillStage.LAW
                && g.hundredDays().get().stage() != game.mandate.core.opening.Types.BillStage.DEAD)));
        m.put("report", SaveManager.save(won(GameSetup.Length.HUNDRED_DAYS, g -> g.dayHundredReport().isPresent())));
        for (long seed = 1; seed < 200; seed++) {
            GameEngine g = until(new GameEngine(setup(seed, GameSetup.Length.HUNDRED_DAYS)), x -> x.phase() != Phase.GENERAL_CAMPAIGN_2028);
            if (g.result2028().get().winner() != g.player().party()) { m.put("lost", SaveManager.save(g)); break; }
        }
        m.put("governing", SaveManager.save(won(GameSetup.Length.FULL_TERM, g -> g.phase() == Phase.GOVERNING && g.indicators().size() > 18 && g.attention() >= 2)));
        return m;
    }

    /** Writes every moment as a save file to a directory ({@code DIR/<moment>.json}): the browser checks load them. */
    public static void main(String[] args) throws java.io.IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "target/moments");
        java.nio.file.Files.createDirectories(dir);
        Map<String, String> all = moments();
        for (var e : all.entrySet()) java.nio.file.Files.writeString(dir.resolve(e.getKey() + ".json"), e.getValue());
        System.out.println(all.size() + " moments written to " + dir.toAbsolutePath());
    }

    /** A session over a temporary home holding one moment. */
    public static Session session(Path home, String save) {
        Session s = new Session(home);
        try {
            java.nio.file.Files.createDirectories(home);
            java.nio.file.Files.writeString(home.resolve("moment.json"), save);
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        s.load(new Session.Slot("moment", home.resolve("moment.json"), true, "", ""));
        return s;
    }
}
