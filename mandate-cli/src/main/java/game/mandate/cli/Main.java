package game.mandate.cli;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.ai.CampaignAi;
import game.mandate.core.campaign.CampaignWeekPlan;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.diplomacy.HotSpot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

/**
 * The executable: {@code java -jar mandate.jar} starts the game. Other modes are designer tools:
 * <pre>
 *   play  [--seed N] [--save FILE]   the game (the default): the 2028 election and the first hundred days, or a full term
 *   auto  [seed] [D|R]               the autopilot plays one full game and prints the log
 *   batch [runs] [scenario]          balance report over many autopilot games
 *   weeks [seed] [D|R] [unified|divided]   the 12-week governing prototype on its own
 *   compare [seeds]                  the prototype's strategy probes side by side
 *   war [runs]                       the operational war model's calibration
 * </pre>
 * The game autosaves to {@code ~/.mandate/save.json} (or {@code $MANDATE_HOME/save.json}).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "play";
        if (mode.startsWith("--")) { String[] a = new String[args.length + 1]; a[0] = "play"; System.arraycopy(args, 0, a, 1, args.length); args = a; mode = "play"; }
        switch (mode) {
            case "auto" -> auto(longArg(args, 1, 2028), args.length > 2 ? Party.valueOf(args[2].equals("R") ? "REPUBLICAN" : "DEMOCRAT") : Party.DEMOCRAT);
            case "compare" -> {
                Compare.run((int) longArg(args, 1, 200), true);
                System.out.println();
                Compare.run((int) longArg(args, 1, 200), false);
            }
            case "weeks" -> new WeeksCli(longArg(args, 1, System.nanoTime() % 100000),
                    args.length > 2 && args[2].equals("R") ? Party.REPUBLICAN : Party.DEMOCRAT,
                    !(args.length > 3 && args[3].equals("divided"))).run();
            case "war" -> WarProbe.run((int) longArg(args, 1, 300));
            case "batch" -> Batch.run((int) longArg(args, 1, 200), args.length > 2 ? Scenario.valueOf(args[2]) : Scenario.TOSS_UP);
            case "play" -> play(args);
            default -> System.out.println("usage: mandate [play [--seed N] [--save FILE] | auto [seed] [D|R] | batch [runs] [SCENARIO] | weeks [seed] [D|R] [unified|divided] | compare [seeds] | war [runs]]");
        }
    }

    static long longArg(String[] a, int i, long def) { return a.length > i ? Long.parseLong(a[i]) : def; }

    static void play(String[] args) {
        long seed = -1;
        Path save = defaultSave();
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (args[i].equals("--seed")) seed = Long.parseLong(args[i + 1]);
            else if (args[i].equals("--save")) save = Path.of(args[i + 1]);
        }
        new GameShell(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out, save, seed).run();
    }

    static Path defaultSave() {
        String home = System.getenv("MANDATE_HOME");
        return (home != null && !home.isBlank() ? Path.of(home) : Path.of(System.getProperty("user.home"), ".mandate")).resolve("save.json");
    }

    static GameSetup defaultSetup(long seed, Party party, Scenario scenario) {
        CandidateCatalog c = CandidateCatalog.load();
        List<Candidate> mine = c.forParty(party), theirs = c.forParty(party.opponent());
        int i = (int) Math.floorMod(seed, mine.size()), j = (int) Math.floorMod(seed / 7, theirs.size());
        return new GameSetup(seed, party, mine.get(i).id(), theirs.get(j).id(), scenario, GameSetup.Difficulty.STANDARD);
    }

    static void auto(long seed, Party party) {
        GameEngine g = new GameEngine(defaultSetup(seed, party, Scenario.TOSS_UP));
        while (!g.over()) AutoPilot.playTurn(g);
        g.log().forEach(System.out::println);
        g.legacy().ifPresent(l -> l.lines().forEach(s -> System.out.println("  " + s)));
    }

}
