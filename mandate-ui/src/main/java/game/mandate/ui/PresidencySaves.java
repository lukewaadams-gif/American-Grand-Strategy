package game.mandate.ui;

import game.mandate.core.time.Phase;
import game.mandate.core.game.GameSetup;
import game.mandate.core.engine.SaveManager;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The sample saves shipped with the browser game (mandate-web/src/main/preview/saves): the moments the start screen
 * offers (the first hundred days, the transition, governing, the crises, the second term, the succession, the handoff).
 * Each is played by the autopilot from a fixed seed, with its election night already watched. Rebuild them after any
 * change to the simulation: java -cp mandate.jar game.mandate.ui.PresidencySaves mandate-web/src/main/preview/saves
 */
public final class PresidencySaves {
    public static void main(String[] a) throws Exception {
        Path out = Path.of(a[0]);
        // the campaign: week 9 and the final stretch, with real people (Josh Shapiro against JD Vance)
        var camp = new game.mandate.core.engine.GameEngine(Fixtures.realSetup(2, GameSetup.Length.FULL_TERM));
        Fixtures.until(camp, x -> !x.date().isBefore(java.time.LocalDate.of(2028, 9, 11)));
        Files.writeString(out.resolve("campaign.json"), SaveManager.save(camp));
        Fixtures.until(camp, x -> !x.date().isBefore(java.time.LocalDate.of(2028, 10, 25)));
        Files.writeString(out.resolve("daily.json"), SaveManager.save(camp));
        System.out.println("campaign: " + camp.player().candidate().name() + " against " + camp.opponent().candidate().name() + ", " + camp.date());
        Files.writeString(out.resolve("presidency.json"), watched(Fixtures.wonReal(GameSetup.Length.FULL_TERM, g -> g.phase() == Phase.FIRST_100_DAYS)));
        // milestone 10: the transition office, the day after the 2028 election, before any choices
        Files.writeString(out.resolve("transition.json"), watched(Fixtures.wonReal(GameSetup.Length.FULL_TERM, g -> g.phase() == Phase.TRANSITION)));
        var g = Fixtures.wonReal(GameSetup.Length.FULL_TERM, x -> x.phase() == Phase.GOVERNING && !x.date().isBefore(java.time.LocalDate.of(2029, 9, 3)));
        // the autopilot handed the Hill to the staff: the player takes it back
        if (g.hill().map(h -> h.delegated()).orElse(false)) g.apply(new game.mandate.core.command.Command.Hill(new game.mandate.core.hill.HillOrder.Delegate(false)));
        if (g.westWing().isPresent()) {
            g.apply(new game.mandate.core.command.Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("people", false)));
            g.apply(new game.mandate.core.command.Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("pardons", false)));
        }
        Files.writeString(out.resolve("governing.json"), watched(g));
        // the Strait of Hormuz: a crisis three days old, with two escort groups out and the reserve open
        var hz = SaveManager.load(SaveManager.save(g));
        hz.apply(new game.mandate.core.command.Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Begin(0)));
        hz.apply(new game.mandate.core.command.Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Escorts(2)));
        hz.apply(new game.mandate.core.command.Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Oil("spr", 1)));
        for (int d = 0; d < 3; d++) {
            for (var p : hz.pendingEvents()) hz.apply(new game.mandate.core.command.Command.ResolveEvent(p.instanceId(), game.mandate.core.ai.AutoPilot.chooseOption(p)));
            hz.apply(new game.mandate.core.command.Command.EndTurn());
        }
        Files.writeString(out.resolve("hormuz.json"), watched(hz));
        System.out.println("hormuz: day " + hz.hormuz().get().day() + " rung " + hz.hormuz().get().rung() + " active " + hz.hormuzActive());
        // the Taiwan crisis: three days in, and the war a day and a half old
        var tw = SaveManager.load(SaveManager.save(g));
        tw.apply(new game.mandate.core.command.Command.TaiwanBegin(0));
        for (int d = 0; d < 3; d++) {
            for (var p : tw.pendingEvents()) tw.apply(new game.mandate.core.command.Command.ResolveEvent(p.instanceId(), game.mandate.core.ai.AutoPilot.chooseOption(p)));
            tw.apply(new game.mandate.core.command.Command.EndTurn());
        }
        Files.writeString(out.resolve("taiwan.json"), watched(tw));
        System.out.println("taiwan: day " + tw.taiwan().get().day() + " phase " + tw.taiwan().get().phase() + " active " + tw.taiwanActive());
        var tww = SaveManager.load(SaveManager.save(tw));
        for (int t = 0; t < 400 && tww.taiwanActive() && tww.taiwan().get().warDays() < 1.5; t++) game.mandate.core.ai.AutoPilot.playTurn(tww);
        for (var p : tww.pendingEvents()) tww.apply(new game.mandate.core.command.Command.ResolveEvent(p.instanceId(), game.mandate.core.ai.AutoPilot.chooseOption(p)));
        Files.writeString(out.resolve("taiwan-war.json"), watched(handBack(tww)));
        System.out.println("taiwan-war: day " + tww.taiwan().get().day() + " phase " + tww.taiwan().get().phase() + " war days " + tww.taiwan().get().warDays() + " active " + tww.taiwanActive());
        // milestone 9: just re-elected (the second term's theme on the desk), and the race to succeed a two-term president
        var re = Fixtures.wonReal(GameSetup.Length.FULL_TERM, x -> x.phase() == Phase.GOVERNING && x.result2032().map(r -> r.winner() == x.player().party()).orElse(false));
        Files.writeString(out.resolve("secondterm.json"), watched(handBack(re)));
        var race = Fixtures.wonReal(GameSetup.Length.FULL_TERM, x -> x.term() == 2 && !x.date().isBefore(java.time.LocalDate.of(2036, 3, 2)));
        Files.writeString(out.resolve("succession.json"), watched(handBack(race)));
        var end = Fixtures.wonReal(GameSetup.Length.FULL_TERM, x -> x.terms().map(t -> t.leaving()).orElse(false) && !x.date().isBefore(java.time.LocalDate.of(2036, 12, 28)));
        Files.writeString(out.resolve("handoff.json"), watched(handBack(end)));
        for (var x : java.util.List.of(g, re, race, end)) System.out.println("crisis active in a preview save: " + x.hormuzActive() + " " + x.taiwanActive() + " " + x.date());
    }

    /** A moment's save, with its election night already watched (a quick start opens on the moment, not the night). */
    static String watched(game.mandate.core.engine.GameEngine g) {
        int night = g.electionNightPending();
        if (night > 0) g.apply(new game.mandate.core.command.Command.ElectionNightSeen(night));
        return SaveManager.save(g);
    }

    static game.mandate.core.engine.GameEngine handBack(game.mandate.core.engine.GameEngine g) {
        if (g.hill().map(h -> h.delegated()).orElse(false)) g.apply(new game.mandate.core.command.Command.Hill(new game.mandate.core.hill.HillOrder.Delegate(false)));
        if (g.westWing().map(w -> w.delegatedPeople()).orElse(false)) g.apply(new game.mandate.core.command.Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("people", false)));
        if (g.westWing().map(w -> w.delegatedPardons()).orElse(false)) g.apply(new game.mandate.core.command.Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("pardons", false)));
        return g;
    }
}
