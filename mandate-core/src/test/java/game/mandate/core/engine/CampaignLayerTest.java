package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.campaign.CampaignStrategy;
import game.mandate.core.campaign.CampaignStrategy.Orders;
import game.mandate.core.campaign.CampaignStrategy.SpendRate;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The playable campaign: who you are, what you promise, how you run, whom you face, and what the polls tell you. */
class CampaignLayerTest {

    static GameSetup slice(long seed) { return setup(seed).withLength(GameSetup.Length.HUNDRED_DAYS); }

    /** Plays campaign weeks with the autopilot answering events but leaving the player's orders alone. */
    static void weeks(GameEngine g, int n) {
        for (int i = 0; i < n && g.phase() == Phase.GENERAL_CAMPAIGN_2028; i++) AutoPilot.playTurn(g);
    }

    // ---- candidates ------------------------------------------------------------------------------

    @Test
    void aCustomCandidateIsBoughtWithinTheBudgetAndSurvivesASave() {
        assertThrows(IllegalArgumentException.class, () -> Candidate.custom(Party.DEMOCRAT, "Too Good", "PA", 9, 9, 9, 9, 9, -0.2));
        assertThrows(IllegalArgumentException.class, () -> Candidate.custom(Party.DEMOCRAT, "Wrong Side", "PA", 7, 7, 7, 6, 6, 0.6));
        assertThrows(IllegalArgumentException.class, () -> Candidate.custom(Party.REPUBLICAN, "", "OH", 7, 7, 7, 6, 6, 0.3));
        Candidate me = Candidate.custom(Party.REPUBLICAN, "Dana Whitcomb", "OH", 8, 7, 6, 6, 6, 0.25);
        GameEngine g = new GameEngine(slice(1).withCustomCandidate(me));   // a Republican race against a Democrat
        assertEquals("Dana Whitcomb", g.player().candidate().name());
        assertEquals(Party.REPUBLICAN, g.player().party());
        weeks(g, 3);
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(me, loaded.player().candidate());
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertTrue(SaveManager.consistent(SaveManager.save(g)));
    }

    @Test
    void theRealRosterIsOptInAndRealPeopleGetNoInventedPersonalScandals() {
        assertTrue(CandidateCatalog.load().all().stream().noneMatch(Candidate::isReal));
        assertTrue(CandidateCatalog.load(true).all().stream().anyMatch(Candidate::isReal));
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(new GameSetup(1, Party.DEMOCRAT, "bg_buttigieg", "r_harlan",
                Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD)));

        Set<String> realEvents = new HashSet<>(), fictionalEvents = new HashSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            GameSetup real = new GameSetup(seed, Party.DEMOCRAT, "bg_buttigieg", "r_harlan", Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD,
                    GameSetup.Length.HUNDRED_DAYS, true, null);
            realEvents.addAll(campaignEvents(new GameEngine(real)));
            fictionalEvents.addAll(campaignEvents(new GameEngine(slice(seed * 2))));
        }
        assertFalse(realEvents.contains("gaffe"));
        assertFalse(realEvents.contains("october_surprise"));
        assertTrue(realEvents.contains("leaked_memo") || realEvents.contains("vendor_dossier"), "campaign-level stand-ins fire: " + realEvents);
        assertFalse(fictionalEvents.contains("leaked_memo"));
        assertTrue(fictionalEvents.contains("gaffe") || fictionalEvents.contains("october_surprise"));
    }

    private static Set<String> campaignEvents(GameEngine g) {
        Set<String> seen = new HashSet<>();
        while (g.phase() == Phase.GENERAL_CAMPAIGN_2028) {
            for (PendingEvent p : g.pendingEvents()) seen.add(p.definition().id());
            AutoPilot.playTurn(g);
        }
        return seen;
    }

    // ---- promises --------------------------------------------------------------------------------

    @Test
    void promisesAreLimitedAndBindTheAgenda() {
        GameEngine g = new GameEngine(slice(2));       // a Democrat
        g.apply(new Command.Pledge("lower_costs"));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Pledge("lower_costs")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Pledge("tax_cut")));        // the other party's bill
        g.apply(new Command.Pledge("public_option"));
        g.apply(new Command.Pledge("housing"));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Pledge("clean_energy")));  // three is the limit
        assertEquals(List.of("lower_costs", "public_option", "housing"), g.election().pledges());
        assertFalse(g.state().ledger().byCause("Promise lower_costs").isEmpty());
    }

    @Test
    void breakingAPromiseAtTheTransitionCostsTheBase() {
        double kept = 0, broken = 0;
        int n = 0;
        for (long seed = 2; n < 6 && seed < 200; seed += 2) {
            GameEngine g = new GameEngine(slice(seed));
            g.apply(new Command.Pledge("lower_costs"));
            g.apply(new Command.Pledge("clean_energy"));
            playUntil(g, x -> x.phase() == Phase.TRANSITION);
            if (g.phase() != Phase.TRANSITION) continue;
            GameEngine other = SaveManager.load(SaveManager.save(g));
            g.apply(new Command.SetAgenda(List.of("lower_costs", "clean_energy", "housing")));
            other.apply(new Command.SetAgenda(List.of("housing", "public_option")));
            assertEquals(List.of("lower_costs", "clean_energy"), other.brokenPromises());
            g.apply(new Command.EndTurn());          // the inauguration
            other.apply(new Command.EndTurn());
            kept += g.state().politics().party(Party.DEMOCRAT).wingMood(0);
            broken += other.state().politics().party(Party.DEMOCRAT).wingMood(0);
            assertEquals("lower_costs", g.state().hundredDays().flagshipId(), "the first promise is the flagship");
            n++;
        }
        assertTrue(n >= 3);
        assertEquals(12.0, (kept - broken) / n, 1e-9, "two broken promises at -6 each");
    }

    // ---- how the campaign is run -----------------------------------------------------------------

    @Test
    void standingOrdersShapeTheCampaign() {
        GameEngine bank = new GameEngine(slice(4)), air = new GameEngine(slice(4)), ground = new GameEngine(slice(4));
        bank.apply(new Command.SetCampaignOrders(new Orders(CampaignStrategy.BANK_IT, SpendRate.CAREFUL, null)));
        air.apply(new Command.SetCampaignOrders(new Orders(CampaignStrategy.AIR_WAR, SpendRate.ALL_IN, null)));
        ground.apply(new Command.SetCampaignOrders(new Orders(CampaignStrategy.GROUND_GAME, SpendRate.STEADY, null)));
        for (GameEngine g : List.of(bank, air, ground)) weeks(g, 5);
        assertTrue(bank.player().cash() > air.player().cash() + 50, "banking saves money: " + bank.player().cash() + " vs " + air.player().cash());
        double airAds = 0, groundField = 0, airField = 0;
        for (var s : air.states().all()) { airAds += air.player().ads(s.code()); airField += air.player().field(s.code()); groundField += ground.player().field(s.code()); }
        assertTrue(airAds > 0);
        assertTrue(groundField > 2 * airField, "the ground game builds field: " + groundField + " vs " + airField);
        // A fixed message is driven every week.
        GameEngine msg = new GameEngine(slice(4));
        msg.apply(new Command.SetCampaignOrders(new Orders(CampaignStrategy.BATTLEGROUND, SpendRate.STEADY, game.mandate.core.campaign.Issue.HEALTHCARE)));
        weeks(msg, 3);
        assertEquals(game.mandate.core.campaign.Issue.HEALTHCARE, msg.player().lastMessage());
        assertEquals(3, msg.player().weeksOnMessage());
    }

    @Test
    void theOpponentAdaptsAndSaysWhy() {
        Set<CampaignStrategy> used = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            GameEngine g = new GameEngine(slice(seed));
            assertEquals(2, g.election().opponentPledges().size());
            while (g.phase() == Phase.GENERAL_CAMPAIGN_2028) {
                AutoPilot.playTurn(g);
                used.add(g.election().opponentOrders().strategy());
                assertFalse(g.election().opponentReasoning().isEmpty());
            }
        }
        assertTrue(used.size() >= 3, "the opponent changes its campaign with the race: " + used);
        assertTrue(used.contains(CampaignStrategy.GROUND_GAME), "early field building");
    }

    /** Working opposition: a stronger opponent wins more often, and how the player runs the campaign changes the odds. */
    @Test
    void theOpponentIsCredibleAndTheCampaignChoiceMatters() {
        int storyWins = 0, hardWins = 0, groundWins = 0, airWins = 0, n = 150;
        for (long seed = 1; seed <= n; seed++) {
            GameSetup base = slice(seed);
            storyWins += wins(withDifficulty(base, GameSetup.Difficulty.STORY), null) ? 1 : 0;
            hardWins += wins(withDifficulty(base, GameSetup.Difficulty.HARD), null) ? 1 : 0;
            groundWins += wins(base, new Orders(CampaignStrategy.GROUND_GAME, SpendRate.ALL_IN, null)) ? 1 : 0;
            airWins += wins(base, new Orders(CampaignStrategy.AIR_WAR, SpendRate.ALL_IN, null)) ? 1 : 0;
        }
        assertTrue(hardWins + 5 < storyWins, "a harder opponent wins more: story " + storyWins + ", hard " + hardWins);
        assertTrue(airWins + 15 < groundWins, "the campaign you run matters: ground game " + groundWins + ", air war " + airWins);
    }

    private static GameSetup withDifficulty(GameSetup s, GameSetup.Difficulty d) {
        return new GameSetup(s.seed(), s.playerParty(), s.playerCandidateId(), s.opponentCandidateId(), s.scenario(), d, s.length(), s.realRoster(), s.customCandidate());
    }

    private static boolean wins(GameSetup s, Orders orders) {
        GameEngine g = new GameEngine(s);
        if (orders != null) g.apply(new Command.SetCampaignOrders(orders));
        playUntil(g, x -> x.phase() != Phase.GENERAL_CAMPAIGN_2028);
        return g.result2028().orElseThrow().winner() == g.player().party();
    }

    // ---- the polls -------------------------------------------------------------------------------

    @Test
    void thePollsAreWeeklyExplainedAndTheForecastDiscriminates() {
        double forecastSum = 0, wins = 0, highWins = 0, high = 0, lowWins = 0, low = 0;
        int n = 0;
        for (long seed = 1; seed <= 120; seed++) {
            GameEngine g = new GameEngine(slice(seed));
            playUntil(g, x -> x.phase() != Phase.GENERAL_CAMPAIGN_2028);
            List<PollPoint> polls = g.polls();
            assertTrue(polls.size() >= 15, "a poll a week");
            assertEquals(g.calendar().campaignStart(), polls.get(0).date());
            assertFalse(polls.get(polls.size() - 1).date().isAfter(g.calendar().election2028()));
            for (PollPoint p : polls.subList(1, polls.size())) assertFalse(p.movers().isEmpty());
            PollPoint last = polls.get(polls.size() - 1);
            assertTrue(last.winChance() >= 0 && last.winChance() <= 1);
            boolean won = g.result2028().orElseThrow().winner() == g.player().party();
            forecastSum += last.winChance();
            wins += won ? 1 : 0;
            if (last.winChance() >= 0.55) { high++; highWins += won ? 1 : 0; }
            if (last.winChance() <= 0.35) { low++; lowWins += won ? 1 : 0; }
            n++;
        }
        // A polls-only forecast: it cannot know the opponent is the stronger campaigner, so it runs a little high.
        assertTrue(Math.abs(forecastSum / n - wins / n) < 0.12, "forecast " + forecastSum / n + " vs wins " + wins / n);
        assertTrue(high >= 10 && low >= 10);
        assertTrue(highWins / high > lowWins / low + 0.15, "higher forecasts win more: " + highWins / high + " vs " + lowWins / low);
        GameEngine g = new GameEngine(slice(3));
        assertNotNull(g.crosstabs().orElseThrow().byBloc());
        assertEquals(16, g.crosstabs().orElseThrow().byBloc().size(), "Battleground 2028's sixteen voter blocs");
    }
}
