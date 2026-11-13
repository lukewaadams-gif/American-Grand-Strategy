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
 * Saves for the browser's election checks (mandate-web/src/test/browser/election.js), not shipped with the game: the eve
 * of each presidential election, one the player wins and one the player loses, in 2028 and 2032, and two saved on the
 * night itself before it is watched. Usage: java -cp mandate.jar game.mandate.ui.ElectionSaves [out-dir]
 */
public final class ElectionSaves {
    private ElectionSaves() {}

    public static void main(String[] args) throws java.io.IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "mandate-web/target/preview/saves/test");
        Files.createDirectories(out);
        boolean win28 = false, lose28 = false, win32 = false, lose32 = false;
        for (long seed = 1; seed < 80 && !(win28 && lose28 && win32 && lose32); seed++) {
            GameEngine g = new GameEngine(Fixtures.setup(seed, GameSetup.Length.FULL_TERM));
            int guard = 0;
            while (!g.over() && g.date().isBefore(LocalDate.of(2028, 11, 6)) && guard++ < 400) AutoPilot.playTurn(g);
            if (g.phase() != Phase.GENERAL_CAMPAIGN_2028) continue;
            String eve = SaveManager.save(g);
            AutoPilot.playTurn(g);
            String night = SaveManager.save(g);
            boolean won = g.result2028().map(r -> r.winner() == g.player().party()).orElse(false);
            if (won && !win28) { write(out, "eve2028-win", eve); write(out, "night2028-win", night); win28 = true; }
            if (!won && !lose28) { write(out, "eve2028-lose", eve); lose28 = true; }
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
    }

    private static void write(Path dir, String name, String save) throws java.io.IOException {
        Files.writeString(dir.resolve(name + ".json"), save);
        System.out.println("wrote " + name);
    }
}
