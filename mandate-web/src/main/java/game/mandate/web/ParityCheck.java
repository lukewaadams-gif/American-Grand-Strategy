package game.mandate.web;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;

/**
 * Plays 40 whole games by autopilot and prints one line per game (electoral votes, the day-100 grade, the command
 * count, whether the save reloads exactly). Run it on the JVM and, compiled to JavaScript with {@code -Pparity}, in
 * Node, and compare: the browser build plays the same games.
 */
public final class ParityCheck {
    public static void main(String[] args) {
        long t0 = System.currentTimeMillis();
        for (long seed = 1; seed <= 40; seed++) {
            boolean dem = seed % 2 == 0;
            GameSetup setup = new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? "d_reyes" : "r_marsh", dem ? "r_harlan" : "d_whitfield",
                    Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD, GameSetup.Length.HUNDRED_DAYS, false, null);
            GameEngine g = new GameEngine(setup);
            int guard = 0;
            while (!g.over() && guard++ < 400) AutoPilot.playTurn(g);
            String save = SaveManager.save(g);
            boolean reload = SaveManager.save(SaveManager.load(save)).equals(save);
            System.out.println("seed " + seed + " ev " + g.result2028().map(r -> r.demElectoralVotes() + "-" + r.repElectoralVotes()).orElse("-")
                    + " report " + g.dayHundredReport().map(r -> r.outcome() + "/" + r.score()).orElse("-") + " commands " + g.history().size() + " reload " + reload);
        }
        // full terms: the president's weeks, weekly governing and Resolute Desk's approval, to the 2032 election
        for (long seed = 1; seed <= 6; seed++) {
            boolean dem = seed % 2 == 0;
            GameSetup setup = new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? "d_reyes" : "r_marsh", dem ? "r_harlan" : "d_whitfield",
                    Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD, GameSetup.Length.FULL_TERM, false, null);
            GameEngine g = new GameEngine(setup);
            int guard = 0;
            while (!g.over() && guard++ < 1200) AutoPilot.playTurn(g);
            // the world's numbers, rounded (a save's text formats doubles a little differently in each runtime)
            var p = g.president();
            System.out.println("term " + seed + " 2032 " + g.result2032().map(r -> r.demElectoralVotes() + "-" + r.repElectoralVotes()).orElse("-")
                    + " weeks " + p.map(x -> x.week()).orElse(0) + " laws " + g.laws().size() + " commands " + g.history().size()
                    + " approval " + game.mandate.core.data.Fmt.format("%.6f", g.state().politics().nationalApproval())
                    + " stamina " + p.map(x -> game.mandate.core.data.Fmt.format("%.6f", x.stamina())).orElse("-")
                    + " health " + p.map(x -> game.mandate.core.data.Fmt.format("%.6f", x.health())).orElse("-")
                    + " chest " + p.map(x -> game.mandate.core.data.Fmt.format("%.6f", x.warChest())).orElse("-")
                    + " news " + g.news().size() + " unemployment " + game.mandate.core.data.Fmt.format("%.6f", g.state().economy().unemployment())
                    + " congress " + g.congress() + " hill " + g.hill().map(h -> h.laws() + "/" + h.vetoes() + "/" + h.probes().size() + "/" + h.record().size()
                    + "/" + h.houseDemocrats().values().stream().mapToInt(Integer::intValue).sum()).orElse("-")
                    + " westwing " + g.westWing().map(x -> x.departures() + "/" + x.leaks() + "/" + x.scandals().size() + "/" + x.justices().size() + "/" + x.storylines().size()
                    + "/" + x.record().size() + "/" + game.mandate.core.data.Fmt.format("%.4f", x.pressRelations())).orElse("-")
                    + " terms " + g.terms().map(t -> g.term() + "/" + t.ending() + "/" + (t.race() == null ? "-" : t.race().year() + ":" + t.race().evMe() + "-" + t.race().evOpp())
                    + "/" + t.record().size()).orElse("-") + " legacy " + g.legacy().map(l -> l.score() + "/" + l.measures()).orElse("-")
                    + " world " + g.foreign().map(f -> f.crisesFaced() + "/" + f.okCount() + "/" + f.kia() + "/" + f.twStage() + "/" + f.uaStage()
                    + "/" + game.mandate.core.data.Fmt.format("%.4f/%.4f/%.4f", f.twt(), f.nato(), f.relations(0)) + "/" + f.record().size()).orElse("-"));
        }
        // the budget office's scores, on a governing economy
        GameSetup setup = new GameSetup(4, Party.DEMOCRAT, "d_reyes", "r_harlan", Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD,
                GameSetup.Length.FULL_TERM, false, null);
        GameEngine g = new GameEngine(setup);
        int guard = 0;
        while (!g.over() && g.president().map(x -> x.week() < 20).orElse(true) && guard++ < 600) AutoPilot.playTurn(g);
        long s0 = System.currentTimeMillis();
        for (String[] q : new String[][]{{"min_wage", "1"}, {"zoning", "2"}, {"corp_rate", "1"}, {"child_credit", "1"}}) {
            var r = g.scorePolicy(q[0], Integer.parseInt(q[1]));
            System.out.println("score " + q[0] + " jobs " + game.mandate.core.data.Fmt.format("%.4f", r.central().jobs()[2])
                    + " output " + game.mandate.core.data.Fmt.format("%.4f", r.central().output()[2])
                    + " range " + game.mandate.core.data.Fmt.format("%.4f", r.min(x -> x.output()[2])) + " " + game.mandate.core.data.Fmt.format("%.4f", r.max(x -> x.output()[2]))
                    + " cost " + game.mandate.core.data.Fmt.format("%.2f", r.central().direct()));
        }
        System.out.println("scoring ms " + (System.currentTimeMillis() - s0));
        // the Strait of Hormuz, the pessimistic case: a strike on the first day, then the playbook to the end
        GameEngine hz = SaveManager.load(SaveManager.save(g));
        hz.apply(new game.mandate.core.command.Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Begin(2)));
        hz.apply(new game.mandate.core.command.Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Strike("missiles")));
        for (int d = 0; d < 300 && hz.hormuzActive(); d++) AutoPilot.playTurn(hz);
        var h = hz.hormuz().orElseThrow();
        System.out.println("hormuz " + h.outcome() + " days " + h.day() + " peak " + h.peakRung() + " killed " + h.usKilled() + " seized " + h.tankersSeized()
                + " hit " + h.tankersHit() + " talks " + game.mandate.core.data.Fmt.format("%.4f", h.talks()) + " spr " + game.mandate.core.data.Fmt.format("%.4f", h.sprLeft())
                + " oil " + game.mandate.core.data.Fmt.format("%.4f", hz.state().economy().oil()) + " approval " + game.mandate.core.data.Fmt.format("%.6f", hz.state().politics().nationalApproval())
                + " commands " + hz.history().size());
        // the Taiwan crisis, the pessimistic case: the staff's playbook to the end, inside the presidency
        GameEngine tw = SaveManager.load(SaveManager.save(g));
        tw.apply(new game.mandate.core.command.Command.TaiwanBegin(2));
        for (int t = 0; t < 1500 && tw.taiwanActive(); t++) AutoPilot.playTurn(tw);
        var ts = tw.taiwan().orElseThrow();
        System.out.println("taiwan " + ts.outcome() + " plan " + ts.plan() + " days " + ts.day() + " war " + game.mandate.core.data.Fmt.format("%.3f", ts.warDays())
                + " killed " + ts.usKilled() + " km2 " + game.mandate.core.data.Fmt.format("%.3f", ts.territoryKm2()) + " elec " + game.mandate.core.data.Fmt.format("%.6f", ts.electricity())
                + " brent " + game.mandate.core.data.Fmt.format("%.4f", ts.brent()) + " risk " + game.mandate.core.data.Fmt.format("%.4f", ts.nuclearRisk())
                + " oil " + game.mandate.core.data.Fmt.format("%.4f", tw.state().economy().oil()) + " approval " + game.mandate.core.data.Fmt.format("%.6f", tw.state().politics().nationalApproval())
                + " commands " + tw.history().size());
        // Island Chain on its own: China's invasion in 2031, both sides by the computer
        var ic = game.mandate.core.taiwan.TaiwanHarness.play(new game.mandate.core.taiwan.TaiwanEngine(),
                game.mandate.core.taiwan.TaiwanScenario.standalone(2031, 0, "PRC").withPlan("invasion"), 7);
        System.out.println("islandchain " + ic.outcome() + " days " + ic.day() + " war " + game.mandate.core.data.Fmt.format("%.3f", ic.warDays())
                + " US killed " + ic.usKilled() + " US ships " + ic.losses("USA").ships() + " PRC ships " + ic.losses("CHN").ships() + " amphib " + ic.plaAmphibiousSunk()
                + " ashore " + game.mandate.core.data.Fmt.format("%.3f", ic.plaAshore()) + " km2 " + game.mandate.core.data.Fmt.format("%.3f", ic.territoryKm2())
                + " score " + game.mandate.core.data.Fmt.format("%.4f", ic.score(0)));
        System.out.println("ms " + (System.currentTimeMillis() - t0));
    }
}
