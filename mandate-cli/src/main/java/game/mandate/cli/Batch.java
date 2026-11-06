package game.mandate.cli;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.military.TheaterOutcome;

import java.util.Map;
import java.util.TreeMap;

/**
 * Balance report: autopilot vs. AI across many seeds, alternating the player's party.
 * The release gate (DEVELOPMENT_ROADMAP.md) asks for 2028 win rates between 40% and 60% for each
 * party on TOSS_UP, and real spread in outcomes.
 */
final class Batch {
    private Batch() {}

    static void run(int runs, Scenario scenario) {
        int[] won28 = new int[2], played = new int[2], reelected = new int[2], governed = new int[2];
        int demWins = 0, landslides = 0, crises = 0, wars = 0;
        Map<String, Integer> tipping = new TreeMap<>();
        Map<TheaterOutcome.TaiwanStatus, Integer> warResults = new TreeMap<>();
        double evSum = 0, evSq = 0, lawSum = 0, approvalSum = 0, scoreSum = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < runs; i++) {
            Party party = i % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN;
            GameEngine g = new GameEngine(Main.defaultSetup(10_000L + i, party, scenario));
            while (!g.over()) AutoPilot.playTurn(g);
            int p = party.ordinal();
            played[p]++;
            ElectionResult r = g.result2028().orElseThrow();
            if (r.winner() == Party.DEMOCRAT) demWins++;
            evSum += r.demElectoralVotes();
            evSq += (double) r.demElectoralVotes() * r.demElectoralVotes();
            if (Math.abs(r.demElectoralVotes() - 269) > 100) landslides++;
            tipping.merge(r.tippingPoint(), 1, Integer::sum);
            if (r.winner() == party) {
                won28[p]++;
                governed[p]++;
                lawSum += g.laws().size();
                approvalSum += g.approval();
                if (g.diplomacy().rung() != null) crises++;
                if (g.war().isPresent()) { wars++; warResults.merge(g.war().get().status(), 1, Integer::sum); }
                if (g.result2032().map(x -> x.winner() == party).orElse(false)) reelected[p]++;
            }
            scoreSum += g.legacy().map(l -> l.score()).orElse(0);
        }
        int gov = governed[0] + governed[1];
        double mean = evSum / runs;
        System.out.printf("%d games, scenario %s, %.1f s%n", runs, scenario, (System.currentTimeMillis() - t0) / 1000.0);
        System.out.printf("2028: Democrats won %d of %d (%.0f%%). Dem EV mean %.0f, sd %.0f. Landslides (>100 EV margin): %d%n",
                demWins, runs, 100.0 * demWins / runs, mean, Math.sqrt(Math.max(0, evSq / runs - mean * mean)), landslides);
        for (Party p : Party.values()) {
            int k = p.ordinal();
            System.out.printf("  playing %-10s won 2028 %3d/%3d (%.0f%%), re-elected %3d of %3d governed (%.0f%%)%n", p, won28[k], played[k],
                    100.0 * won28[k] / Math.max(1, played[k]), reelected[k], governed[k], 100.0 * reelected[k] / Math.max(1, governed[k]));
        }
        if (gov > 0) System.out.printf("Governing: avg %.1f laws, final approval %.1f%%, Taiwan crisis in %d of %d terms, war in %d %s%n",
                lawSum / gov, approvalSum / gov, crises, gov, wars, warResults);
        System.out.printf("Mean legacy score %.0f. Tipping-point states: %s%n", scoreSum / runs, tipping);
    }
}
