package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.hormuz.HormuzOrder;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static game.mandate.core.engine.EngineFixtures.governing;
import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The four games on one engine (docs/MODES.md): Full Game, Campaign Only, Governing Only and Individual Crisis. */
class ModesTest {

    @Test
    void campaignOnlyEndsOnElectionNightWonOrLost() {
        boolean won = false, lost = false;
        for (long seed = 1; seed < 30 && !(won && lost); seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed).withMode(GameSetup.Mode.CAMPAIGN_ONLY)), x -> x.phase() != Phase.GENERAL_CAMPAIGN_2028);
            assertEquals(Phase.EPILOGUE, g.phase(), "the game ends on election night, won or lost");
            assertTrue(g.over());
            assertTrue(g.president().isEmpty(), "no presidency in Campaign Only");
            assertEquals(2028, g.electionNightPending(), "the night is still to be watched");
            var a = g.campaignAssessment().orElseThrow();
            boolean w = g.result2028().orElseThrow().winner() == g.player().party();
            assertEquals(w, a.won());
            assertEquals(a.grade(), g.legacy().orElseThrow().grade(), "the verdict is the campaign's");
            assertTrue(a.lines().size() >= 3, String.valueOf(a.lines()));
            if (w) { won = true; assertTrue(a.lines().get(1).startsWith("Your margin of safety")); }
            else { lost = true; assertTrue(a.lines().get(1).contains("electoral votes short")); }
            // the night is watched, and the game stays at its end
            g.apply(new Command.ElectionNightSeen(2028));
            assertEquals(0, g.electionNightPending());
            assertTrue(SaveManager.load(SaveManager.save(g)).over());
        }
        assertTrue(won && lost, "a win and a loss both end the game");
    }

    @Test
    void governingOnlyOpensOnElectionNightWithTheChosenAdministration() {
        for (Party p : Party.values()) for (Scenario sc : new Scenario[]{Scenario.TOSS_UP, p == Party.DEMOCRAT ? Scenario.RED_ENVIRONMENT : Scenario.BLUE_ENVIRONMENT}) {
            String me = p == Party.DEMOCRAT ? "bg_shapiro" : "bg_vance", them = p == Party.DEMOCRAT ? "bg_vance" : "bg_shapiro";
            GameSetup s = new GameSetup(11, p, me, them, sc, GameSetup.Difficulty.STANDARD, GameSetup.Mode.GOVERNING_ONLY, true, null);
            GameEngine g = GameEngine.start(s);
            assertEquals(Phase.TRANSITION, g.phase(), p + " " + sc);
            assertEquals(GameSetup.Mode.GOVERNING_ONLY, g.mode());
            assertEquals(me, g.player().candidate().id(), "the president-elect is the one chosen");
            assertEquals(p, g.result2028().orElseThrow().winner(), "the chosen party won 2028");
            assertEquals(2028, g.result2028().orElseThrow().year());
            assertEquals(LocalDate.of(2028, 11, 7), g.calendar().election2028(), "election night, November 7, 2028");
            assertEquals(2028, g.electionNightPending(), "Governing Only opens on the 2028 broadcast");
            assertTrue(g.log().stream().anyMatch(l -> l.contains("  Governing Only: ")));
            // the same choice gives the same world, and the save replays to it
            assertEquals(g.fingerprint(), GameEngine.start(s).fingerprint());
            assertEquals(g.fingerprint(), g.restart().fingerprint(), "a restart is the same Governing Only start");
            assertTrue(SaveManager.consistent(SaveManager.save(g)), "the save replays to the same world");
        }
    }

    @Test
    void governingOnlyGoesOnThroughTheSamePresidencyAsTheFullGame() {
        GameEngine g = GameEngine.start(setup(4).withMode(GameSetup.Mode.GOVERNING_ONLY));
        playUntil(g, x -> x.phase() == Phase.FIRST_100_DAYS);
        assertEquals(Phase.FIRST_100_DAYS, g.phase(), "the hundred days open the presidency");
        playUntil(g, x -> x.phase() != Phase.FIRST_100_DAYS);
        assertEquals(Phase.GOVERNING, g.phase());
        assertTrue(g.dayHundredReport().isPresent(), "the 100-day report, and the presidency goes on");
        assertTrue(g.hill().isPresent() && g.westWing().isPresent() && g.foreign().isPresent(), "the rooms are open");
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(GameSetup.Mode.GOVERNING_ONLY, loaded.mode());
        assertEquals(g.fingerprint(), loaded.fingerprint());
    }

    /** A governing presidency with the Situation Room open, for a crisis to begin from. */
    static String governingSave(long seed) {
        GameEngine g = governing(seed);
        playUntil(g, x -> x.foreign().isPresent() && !x.taiwanActive() && !x.hormuzActive() && x.pendingEvents().isEmpty());
        return SaveManager.save(g);
    }

    static GameEngine crisis(String base, String kind, int preset, Command first, boolean playbook) {
        GameEngine g = SaveManager.load(base);
        g.apply(new Command.IndividualCrisis(kind, preset));
        if (first != null) g.apply(first);
        int guard = 0;
        while (!g.over() && guard++ < 6000) {
            if (playbook) { AutoPilot.playTurn(g); continue; }
            EngineFixtures.answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        return g;
    }

    @Test
    void anIndividualCrisisEndsAtItsOutcomeWithAnAfterActionReport() {
        String base = governingSave(2);
        Set<CrisisRecord.Result> hormuz = EnumSet.noneOf(CrisisRecord.Result.class), taiwan = EnumSet.noneOf(CrisisRecord.Result.class);
        for (int preset = 0; preset < 3; preset++) {
            for (GameEngine g : new GameEngine[]{crisis(base, "hormuz", preset, null, true), crisis(base, "hormuz", preset, null, false),
                    crisis(base, "hormuz", preset, new Command.Hormuz(new HormuzOrder.Invade(0)), false)}) {
                CrisisRecord.Report r = check(g, "hormuz");
                hormuz.add(r.result());
                if (r.outcome().equals("A settlement") || r.outcome().equals("Shipping restored")) assertEquals(CrisisRecord.Result.WON, r.result());
                if (r.outcome().equals("Regime change and occupation")) assertEquals(CrisisRecord.Result.PARTIAL, r.result());
            }
            CrisisRecord.Report t = check(crisis(base, "taiwan", preset, null, true), "taiwan");
            taiwan.add(t.result());
        }
        assertEquals(EnumSet.allOf(CrisisRecord.Result.class), hormuz, "the strait can be won, lost or ended in part");
        // which of won, lost and ended in part Taiwan comes to turns on the president's orders: TaiwanOutcomeTest
        assertFalse(taiwan.isEmpty(), "Taiwan: " + taiwan);
    }

    /** The game ended with the crisis, and its report says so. */
    static CrisisRecord.Report check(GameEngine g, String kind) {
        assertEquals(Phase.EPILOGUE, g.phase(), "the game ends when the crisis does");
        assertEquals(GameSetup.Mode.CRISIS, g.mode());
        assertFalse(g.taiwanActive() || g.hormuzActive());
        CrisisRecord c = g.individualCrisis().orElseThrow();
        assertEquals(kind, c.kind());
        CrisisRecord.Report r = c.report();
        assertNotNull(r, "an after-action report");
        assertTrue(r.days() > 0 && !r.verdict().isBlank() && !r.outcome().isBlank());
        assertTrue(r.figures().size() >= 5, String.valueOf(r.figures()));
        assertFalse(r.record().isEmpty(), "the crisis's own record");
        assertEquals(r, SaveManager.load(SaveManager.save(g)).individualCrisis().orElseThrow().report(), "the report is part of the save");
        return r;
    }

    @Test
    void anIndividualCrisisBeginsOnlyFromAGoverningPresidency() {
        GameEngine campaign = new GameEngine(setup(2));
        assertThrows(IllegalCommandException.class, () -> campaign.apply(new Command.IndividualCrisis("hormuz", 0)));
        GameEngine g = SaveManager.load(governingSave(2));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.IndividualCrisis("mars", 0)));
        g.apply(new Command.IndividualCrisis("taiwan", 0));
        assertTrue(g.taiwanActive());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.IndividualCrisis("hormuz", 0)), "one crisis a game");
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(setup(2).withMode(GameSetup.Mode.CRISIS)), "a crisis starts from a presidency");
    }

    @Test
    void anOlderSaveOfTheHundredDaysSliceLoadsAsTheFullGame() {
        GameEngine g = EngineFixtures.playUntil(new GameEngine(setup(3)), x -> x.phase() == Phase.FIRST_100_DAYS);
        if (g.phase() != Phase.FIRST_100_DAYS) g = EngineFixtures.playUntil(new GameEngine(setup(4)), x -> x.phase() == Phase.FIRST_100_DAYS);
        // the save as the slice wrote it: a length, and no mode
        Map<String, Object> m = Json.object(Json.parse(SaveManager.save(g)));
        Map<String, Object> setupJson = Json.object(m.get("setup"));
        setupJson.remove("mode");
        setupJson.put("length", "HUNDRED_DAYS");
        Json.object(m.get("world")).remove("mode");
        GameEngine old = SaveManager.load(Json.write(m));
        assertEquals(GameSetup.Mode.FULL_GAME, old.mode());
        assertEquals(g.fingerprint(), old.fingerprint(), "nothing else about the game changes");
        playUntil(old, x -> x.phase() != Phase.FIRST_100_DAYS);
        assertEquals(Phase.GOVERNING, old.phase(), "the hundred days go on into the presidency");
        assertTrue(old.dayHundredReport().isPresent());
    }
}
