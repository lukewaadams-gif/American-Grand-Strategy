package game.mandate.cli;

import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End to end: the game as a player sees it, driven by scripted keyboard input through the real shell. */
class GameShellTest {
    @TempDir Path dir;

    /** Runs the shell on a script; returns everything it printed. */
    static String run(Path save, long seed, List<String> script) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new GameShell(new BufferedReader(new StringReader(String.join("\n", script) + "\n")), out, save, seed).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    static List<String> enters(int n) { return new ArrayList<>(Collections.nCopies(n, "")); }

    /** How many prompts the shell asked before {@code marker} first appeared. */
    static int promptsBefore(String out, String marker) {
        String head = out.substring(0, out.indexOf(marker));
        int n = 0;
        for (int i = head.indexOf("  > ["); i >= 0; i = head.indexOf("  > [", i + 1)) n++;
        for (int i = head.indexOf("(Enter to continue)"); i >= 0; i = head.indexOf("(Enter to continue)", i + 1)) n++;
        for (String p : List.of("  Your agenda: ", "  Name [", "  Home state", "  Charisma", "  Debating", "  Fundraising", "  Policy", "  Discipline",
                "  Rally states", "  Money to spend", "  Share on ads", "  Political capital to commit"))
            for (int i = head.indexOf(p); i >= 0; i = head.indexOf(p, i + 1)) n++;
        return n;
    }

    /** A script that presses Enter until the end-of-game menu, then makes {@code choices}. */
    List<String> toTheEnd(long seed, String... choices) {
        String probe = run(dir.resolve("probe-end-" + seed + ".json"), seed, enters(400));
        List<String> script = enters(promptsBefore(probe, "What next?"));
        script.addAll(List.of(choices));
        return script;
    }

    /** The first seed for which a player pressing Enter throughout wins the election. */
    long winningSeed() {
        for (long seed = 1; seed < 40; seed++) {
            Path save = dir.resolve("probe-" + seed + ".json");
            String out = run(save, seed, enters(120));
            if (out.contains("YOU WIN")) return seed;
        }
        throw new AssertionError("no seed won");
    }

    @Test
    void onePlayerGoesFromTheCampaignThroughTheInaugurationToTheDayHundredReport() throws Exception {
        long seed = winningSeed();
        Path save = dir.resolve("save.json");
        String out = run(save, seed, toTheEnd(seed, "", "3"));   // at the end: back to the menu, then quit
        int campaign = out.indexOf("CAMPAIGN HQ"), night = out.indexOf("ELECTION NIGHT"), transition = out.indexOf("THE TRANSITION"),
                oath = out.indexOf("Inauguration Day."), days = out.indexOf("THE FIRST HUNDRED DAYS   Week 1 of 14"), report = out.indexOf("== DAY 100: ");
        assertTrue(campaign > 0 && campaign < night && night < transition && transition < oath && oath < days && days < report, "one continuous game, in order");
        assertTrue(out.contains("TIP "), "the coach teaches along the way");
        assertTrue(out.contains("What moved the race"), "the polls explain themselves");
        assertTrue(out.contains("[MET]") || out.contains("[PART]") || out.contains("[MISS]"));
        assertTrue(out.contains("What next?"));
        // One unified save, readable by the engine, that replays to the same world.
        String text = Files.readString(save);
        GameEngine g = SaveManager.load(text);
        assertEquals(Phase.EPILOGUE, g.phase());
        assertTrue(g.dayHundredReport().isPresent());
        assertTrue(SaveManager.consistent(text));
        assertFalse(SaveManager.ui(text).isEmpty(), "the tutorial's progress lives in the same file");
    }

    @Test
    void continueResumesExactlyWhereThePlayerLeft() throws Exception {
        Path save = dir.resolve("save.json");
        run(save, 3, enters(14));                          // setup and a few campaign weeks, then the input ends
        GameEngine before = SaveManager.load(Files.readString(save));
        assertEquals(Phase.GENERAL_CAMPAIGN_2028, before.phase());
        List<String> script = new ArrayList<>(List.of("1"));  // Continue
        script.addAll(enters(3));
        String out = run(save, 3, script);
        assertTrue(out.contains("Continue: "));
        assertTrue(out.contains("Welcome back."));
        String date = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US).format(before.date());
        assertTrue(out.contains("CAMPAIGN HQ   " + date), "resumes at " + date);
    }

    @Test
    void aPlayerCanBuildACandidatePromiseChangeStrategyAndPlanAWeekByHand() throws Exception {
        Path save = dir.resolve("save.json");
        List<String> script = new ArrayList<>(List.of(
                "1",            // new game: the slice
                "1",            // Democratic
                "5",            // create your own candidate
                "Sam Rivera", "oh", "9", "9", "5", "5", "5", "2",   // name, state, five ratings (33 points), mainstream
                "", "", "", ""  // opponent, mood, difficulty, tutorial
        ));
        script.addAll(List.of(
                "5", "1",       // promise the first bill on the list
                "2", "3", "2", "1",  // standing orders: ground game, steady, staff picks the message
                "3", "PA MI", "40", "50", "1",  // a hand-built week: rallies, $40M, half on ads, message
                "1"));          // run the week
        script.addAll(enters(4));
        String out = run(save, 11, script);
        assertTrue(out.contains("You are Sam Rivera (D-OH)"), out);
        assertTrue(out.contains("You promise to pass the"));
        assertTrue(out.contains("Orders: Ground game, steady spending"));
        assertTrue(out.contains("This week, by hand: rallies PA MI"));
        GameEngine g = SaveManager.load(Files.readString(save));
        assertEquals("Sam Rivera", g.player().candidate().name());
        assertEquals(1, g.election().pledges().size());
        assertNotNull(g.election().playerOrders());
    }

    @Test
    void afterTheReportThePlayerCanGovernTheHundredDaysAgain() throws Exception {
        long seed = winningSeed();
        List<String> script = toTheEnd(seed, "1");                // govern the hundred days again
        script.addAll(enters(3));
        String out = run(dir.resolve("again.json"), seed, script);
        assertTrue(out.contains("Back to Inauguration Day."), "the replay starts from the inauguration");
        assertTrue(out.lastIndexOf("THE FIRST HUNDRED DAYS   Week 1 of 14") > out.indexOf("Back to Inauguration Day."));
        GameEngine g = SaveManager.load(Files.readString(dir.resolve("again.json")));
        assertEquals(Phase.FIRST_100_DAYS, g.phase());
    }
}
