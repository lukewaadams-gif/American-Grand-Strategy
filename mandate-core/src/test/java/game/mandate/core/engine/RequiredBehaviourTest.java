package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.command.Command;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.diplomacy.Escalation;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.events.EventCatalog;
import game.mandate.core.military.TheaterOutcome;
import game.mandate.core.politics.Coalition;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.engine.EngineFixtures.answerAll;
import static game.mandate.core.engine.EngineFixtures.governing;
import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static game.mandate.core.engine.EngineFixtures.untilLaw;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The five behaviours the integrated engine must demonstrate, each as an end-to-end test over the real engines. */
class RequiredBehaviourTest {

    // ---- 1. Elections affect the next administration ---------------------------------------------

    @Test
    void electionsAffectTheNextAdministration() {
        List<double[]> evAndStart = new ArrayList<>();
        for (long seed = 1; evAndStart.size() < 30 && seed < 600; seed++) {
            // Measured at the inauguration, where the mandate is set (before the first hundred days are played).
            GameEngine g = playUntil(new GameEngine(setup(seed)), x -> x.phase() == Phase.FIRST_100_DAYS || x.over());
            if (g.phase() != Phase.FIRST_100_DAYS) continue;
            ElectionResult r = g.result2028().orElseThrow();
            WorldState w = g.state();
            double startSwing = w.politics().approval(Coalition.SWING), startCapital = w.politics().capital();
            // The winner, and nobody else, is now president, with the winner's party.
            assertEquals(g.player().candidate().id(), w.government().presidentId());
            assertEquals(r.winner(), w.government().presidentParty());
            assertEquals(game.mandate.core.politics.Role.PRESIDENT, w.politics().role(g.player().candidate().id()));
            assertEquals(game.mandate.core.politics.Role.DEFEATED, w.politics().role(g.opponent().candidate().id()));
            // The Congress the election produced is the Congress that governs.
            var elected = w.ledger().byCause("Election 2028").stream().filter(c -> c.owner().equals("congress")).toList();
            assertEquals(1, elected.size());
            assertTrue(elected.get(0).effect().contains("House D " + g.congress().house(Party.DEMOCRAT)));
            // The flagship of the first hundred days is the new president's first priority.
            assertEquals(g.agenda().get(0), w.hundredDays().flagshipId());
            // By the end of the hundred days, every cabinet post has been filled or put to the Senate.
            playUntil(g, x -> x.phase() == Phase.GOVERNING || x.over());
            for (var post : game.mandate.core.executive.Post.values()) assertTrue(w.government().seat(post).name() != null);
            evAndStart.add(new double[]{r.electoralVotes(g.player().party()), startSwing, startCapital});
        }
        assertTrue(evAndStart.size() >= 20, "enough won elections: " + evAndStart.size());
        // A bigger mandate starts the term with more swing-voter approval and more political capital.
        assertTrue(correlation(evAndStart, 0, 1) > 0.5, "EV vs starting swing approval: " + correlation(evAndStart, 0, 1));
        assertTrue(correlation(evAndStart, 0, 2) > 0.5, "EV vs starting capital: " + correlation(evAndStart, 0, 2));
    }

    // ---- 2. Legislation influences economic or political conditions ------------------------------

    @Test
    void legislationInfluencesEconomicAndPoliticalConditions() {
        int compared = 0;
        double deficitGap = 0, growthGap = 0, recordGap = 0;
        for (long seed = 1; compared < 12 && seed < 400; seed += 8) {
            GameEngine base = governing(seed);
            // The deficit-financed bill and route with the best whip count, from the player's own agenda or the bipartisan list.
            String bill = null;
            LegislativeStrategy route = null;
            double best = 0.3;
            for (var b : base.bills().availableTo(base.player().party())) {
                if (b.deficitPctGdp() < 0.2) continue;
                for (LegislativeStrategy s2 : List.of(LegislativeStrategy.PARTY_LINE, LegislativeStrategy.BIPARTISAN_DEAL)) {
                    double chance = base.whip(b.id(), s2, 20).passChance();
                    if (chance > best) { best = chance; bill = b.id(); route = s2; }
                }
            }
            if (bill == null) continue;
            GameEngine withLaw = SaveManager.load(SaveManager.save(base)), without = SaveManager.load(SaveManager.save(base));
            withLaw.apply(new Command.PushBill(bill, route, 20));     // to the Hill, where it moves week by week
            if (!untilLaw(withLaw, without, bill)) continue;
            // two years of weekly turns: a law's money starts after a quarter and phases in by year (infrastructure
            // spends about a fifth of its money in the first year), and the economy takes months to respond
            for (int wk = 0; wk < 104; wk++) {
                answerAll(withLaw, 0); withLaw.apply(new Command.EndTurn());
                answerAll(without, 0); without.apply(new Command.EndTurn());
            }
            // Congress's majority moves bills of its own: a twin that passed the same law is no counterfactual
            if (without.laws().contains(bill)) continue;
            deficitGap += withLaw.state().budget().policyDeficit() - without.state().budget().policyDeficit();
            growthGap += withLaw.economy().orElseThrow().outputGap() - without.economy().orElseThrow().outputGap();
            recordGap += withLaw.state().politics().record(Coalition.SWING) - without.state().politics().record(Coalition.SWING);
            // The consequences were routed to their owners and recorded against the decision.
            String b2 = bill;
            var owners = withLaw.state().ledger().byCause("Law " + b2).stream().map(ConsequenceLedger.Consequence::owner).distinct().toList();
            assertTrue(owners.contains("economy") && owners.contains("politics"), "owners " + owners);
            compared++;
        }
        assertTrue(compared >= 5, "found enough passed laws: " + compared);
        assertTrue(deficitGap / compared > 0.1, "a borrowed program should raise the deficit: " + deficitGap / compared);
        assertTrue(growthGap / compared > 0.02, "and stimulate output: " + growthGap / compared);
        assertTrue(recordGap / compared > 0.1, "and leave a lasting mark on swing voters: " + recordGap / compared);
    }

    // ---- 3. Economic conditions influence future elections ---------------------------------------

    @Test
    void economicConditionsInfluenceFutureElections() {
        double marginHealthy = 0, marginRecession = 0;
        int wonHealthy = 0, wonRecession = 0, n = 0;
        for (long seed = 3; n < 24 && seed < 900; seed += 5) {
            GameEngine start = governing(seed);
            String snapshot = SaveManager.save(start);
            GameEngine healthy = SaveManager.load(snapshot), recession = SaveManager.load(snapshot);
            // A deep recession arrives about a year before the election (a system transition, as from content).
            playUntil(healthy, g -> !g.date().isBefore(java.time.LocalDate.of(2031, 10, 1)));
            playUntil(recession, g -> !g.date().isBefore(java.time.LocalDate.of(2031, 10, 1)));
            recession.dispatch(0, "Test: recession", new Effect.EconomyShock(-9.0, -0.5, 6));
            // to the 2032 result (a re-elected president now serves on; the comparison is on election night)
            playUntil(healthy, g -> g.over() || g.result2032().isPresent());
            playUntil(recession, g -> g.over() || g.result2032().isPresent());
            if (healthy.result2032().isEmpty() || recession.result2032().isEmpty()) continue;
            Party p = healthy.player().party();
            marginHealthy += healthy.result2032().get().marginFor(p);
            marginRecession += recession.result2032().get().marginFor(p);
            if (healthy.result2032().get().winner() == p) wonHealthy++;
            if (recession.result2032().get().winner() == p) wonRecession++;
            assertTrue(recession.economy().orElseThrow().unemployment() > healthy.economy().orElseThrow().unemployment() - 0.5);
            n++;
        }
        assertTrue(n >= 12, "enough re-election races: " + n);
        assertTrue(marginRecession / n < marginHealthy / n - 1.0,
                String.format("a recession should cost the incumbent votes: %.2f vs %.2f", marginRecession / n, marginHealthy / n));
        assertTrue(wonRecession < wonHealthy, "and re-elections: " + wonRecession + " vs " + wonHealthy);
    }

    // ---- 4. Military losses persist -------------------------------------------------------------

    @Test
    void militaryLossesPersist() {
        GameEngine g = governing(11);
        int carriers0 = g.state().military().force("CARRIER"), surface0 = g.state().military().force("SURFACE");
        double lrasm0 = g.state().military().munitions("LRASM");
        g.queueEvent(EventCatalog.escalation(Escalation.WAR));
        var war = g.pendingEvents().stream().filter(p -> p.definition().id().equals("taiwan_war")).findFirst().orElseThrow();
        g.apply(new Command.ResolveEvent(war.instanceId(), 0));          // defend Taiwan with US forces
        assertTrue(g.operationsActive());
        assertEquals(SimulationClock.Granularity.OPERATIONAL, g.clock().mode(), "the war is fought in operational turns");
        long turnsBefore = g.clock().turns();
        var start = g.clock().now();
        g.apply(new Command.EndTurn());
        assertEquals(6, java.time.Duration.between(start, g.clock().now()).toHours(), "first-week operational turns are six hours");
        while (g.operationsActive()) { answerAll(g, 0); g.apply(new Command.EndTurn()); }
        assertTrue(g.clock().turns() - turnsBefore > 10, "a war takes many operational turns");
        assertEquals(SimulationClock.Granularity.WEEKLY, g.clock().mode(), "governing resumes in weekly turns");

        TheaterOutcome out = g.war().orElseThrow();
        var m = g.state().military();
        int shipsLost = m.lost("SURFACE"), killed = m.killed();
        assertTrue(shipsLost > 0 && killed > 0, "an intervention costs ships and lives");
        assertEquals(surface0 - shipsLost, m.force("SURFACE"));
        assertEquals(out.shipsLost(), shipsLost);
        double lrasmAfterWar = m.munitions("LRASM");
        assertTrue(lrasmAfterWar < lrasm0, "munitions were spent");

        // A year later: ships and aircraft are still gone; munitions recover only through production.
        for (int month = 0; month < 12 && g.phase() == Phase.GOVERNING; month++) { answerAll(g, 0); g.apply(new Command.EndTurn()); }
        assertEquals(surface0 - shipsLost, g.state().military().force("SURFACE"), "lost ships are not restored");
        assertEquals(carriers0 - m.lost("CARRIER"), g.state().military().force("CARRIER"));
        assertEquals(killed, g.state().military().killed(), "the dead stay counted");
        assertTrue(g.state().military().munitions("LRASM") < lrasm0, "a year of production does not refill the magazine");

        // And through a save.
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(g.state().military().force("SURFACE"), loaded.state().military().force("SURFACE"));
        assertEquals(killed, loaded.state().military().killed());
        assertEquals(1, loaded.state().military().wars().size());
        assertFalse(loaded.state().ledger().byCause("War").isEmpty(), "the war's consequences are in the record");
    }

    // ---- 5. Saves reproduce equivalent futures under the same decisions and random state ----------

    @Test
    void savesReproduceTheSameFuture() {
        for (long seed : new long[]{2, 7, 15}) {
            GameEngine original = new GameEngine(setup(seed));
            playUntil(original, g -> g.state().commandsApplied() >= 25);            // mid-campaign
            assertFuturesMatch(original);
            playUntil(original, g -> g.phase() == Phase.GOVERNING || g.over());      // early term
            assertFuturesMatch(original);
            playUntil(original, g -> g.date().getYear() >= 2031 || g.over());        // late term
            assertFuturesMatch(original);
        }
    }

    @Test
    void savesTakenDuringAWarReproduceTheRestOfTheWar() {
        GameEngine g = governing(19);
        g.queueEvent(EventCatalog.escalation(Escalation.WAR));
        var war = g.pendingEvents().stream().filter(p -> p.definition().id().equals("taiwan_war")).findFirst().orElseThrow();
        g.apply(new Command.ResolveEvent(war.instanceId(), 0));
        for (int i = 0; i < 5; i++) g.apply(new Command.EndTurn());
        assertTrue(g.operationsActive());
        // The war was started by a test hook, not a command, so only the snapshot path (not replay) can reproduce it.
        assertFuturesMatch(g, false);
    }

    /** Saves {@code g}, plays the original on, then feeds the loaded copy the same commands: the worlds must be identical. */
    private static void assertFuturesMatch(GameEngine original) { assertFuturesMatch(original, true); }

    private static void assertFuturesMatch(GameEngine original, boolean replayable) {
        if (original.over()) return;
        String save = SaveManager.save(original);
        GameEngine loaded = SaveManager.load(save);
        assertEquals(Json(original), Json(loaded), "a loaded save is the same world");
        int from = original.history().size();
        playUntil(original, g -> g.history().size() >= from + 60);
        for (Command c : original.history().subList(from, original.history().size())) loaded.apply(c);
        assertEquals(Json(original), Json(loaded), "the same decisions from the same state and random state give the same future");
        assertEquals(original.log(), loaded.log());
        if (!replayable) return;
        assertTrue(SaveManager.consistent(save), "the snapshot equals a replay of its own command log");
        // The version-1 format (setup + commands) is still readable and reaches the same world by replay.
        assertEquals(Json(original), Json(SaveManager.load(SaveManager.saveReplayOnly(original))));
    }

    private static String Json(GameEngine g) { return game.mandate.core.data.Json.write(g.state().toJson()); }

    private static double correlation(List<double[]> rows, int a, int b) {
        int n = rows.size();
        double ma = rows.stream().mapToDouble(r -> r[a]).average().orElse(0), mb = rows.stream().mapToDouble(r -> r[b]).average().orElse(0);
        double cov = 0, va = 0, vb = 0;
        for (double[] r : rows) { cov += (r[a] - ma) * (r[b] - mb); va += (r[a] - ma) * (r[a] - ma); vb += (r[b] - mb) * (r[b] - mb); }
        return cov / Math.sqrt(va * vb + 1e-12);
    }

    @SuppressWarnings("unused")
    private static void autopilot(GameEngine g) { AutoPilot.playTurn(g); }
}
