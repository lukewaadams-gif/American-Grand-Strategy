package game.mandate.core.engine;

import game.mandate.core.campaign.Activity;
import game.mandate.core.campaign.AirWar;
import game.mandate.core.campaign.CampaignEngine;
import game.mandate.core.campaign.CampaignGeography;
import game.mandate.core.campaign.CampaignOrder;
import game.mandate.core.campaign.CampaignOrders;
import game.mandate.core.campaign.ElectionState.StaffArea;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.time.Phase;
import game.mandate.core.time.SimulationClock.Granularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Battleground 2028's schedule, ground game and air war, as ported: costs, effects, refusals, the final stretch, saves. */
class CampaignTrailTest {

    static GameEngine manual(long seed) {
        GameEngine g = new GameEngine(setup(seed));
        for (StaffArea a : StaffArea.values()) g.apply(new Command.Campaign(new CampaignOrder.Staff(a, false)));
        g.apply(new Command.Campaign(new CampaignOrder.ClearSchedule()));
        g.apply(new Command.Campaign(new CampaignOrder.PullAds(true, true)));
        // no surrogates either: these tests measure the candidate's own trail
        for (int slot = 0; slot < game.mandate.core.campaign.CampaignStaff.SURROGATES; slot++)
            g.apply(new Command.Campaign(new CampaignOrder.Surrogate(slot, null, null)));
        return g;
    }

    static void fill(GameEngine g, Activity a, String state) {
        for (int i = 0; i < CampaignOrders.SLOTS; i++) g.apply(new Command.Campaign(new CampaignOrder.Slot(i, a, state, 0)));
    }

    static void endTurn(GameEngine g) {
        EngineFixtures.answerAll(g, 0);
        g.apply(new Command.EndTurn());
    }

    // ---- the schedule ---------------------------------------------------------------------------------------------

    @Test
    void aWeekOfRalliesCostsMoneyAndStaminaAndBuildsVisitsAndVolunteers() {
        GameEngine rallies = manual(2), rest = manual(2);
        fill(rallies, Activity.RALLY, "PA");
        fill(rest, Activity.REST, null);
        double cash = rallies.player().cash();
        endTurn(rallies);
        endTurn(rest);
        assertEquals(21, rallies.player().rallies());
        assertTrue(rallies.player().stamina() < rest.player().stamina() - 20, rallies.player().stamina() + " vs " + rest.player().stamina());
        assertEquals(100, rest.player().stamina(), 1e-9, "a week of rest leaves a fresh candidate fresh");
        assertTrue(rallies.player().visit("PA") > 5);
        assertTrue(rallies.player().volunteers("PA") > rest.player().volunteers("PA") + 10000);
        assertTrue(rallies.player().spentLastTurn() > rest.player().spentLastTurn() + 21 * Activity.RALLY.cost() - 1e-6);
        assertTrue(cash - rallies.player().cash() > 0, "rallies are paid for");
    }

    @Test
    void aSameDayFlightAcrossTheCountryCostsThirtyPercentOfTheEvent() {
        GameEngine hop = manual(4), stay = manual(4);
        hop.apply(new Command.Campaign(new CampaignOrder.Slot(0, Activity.RETAIL, "CA", 0)));
        hop.apply(new Command.Campaign(new CampaignOrder.Slot(1, Activity.RETAIL, "PA", 0)));
        stay.apply(new Command.Campaign(new CampaignOrder.Slot(0, Activity.RETAIL, "CA", 0)));
        stay.apply(new Command.Campaign(new CampaignOrder.Slot(3, Activity.RETAIL, "PA", 0)));   // the next morning: overnight travel
        endTurn(hop);
        endTurn(stay);
        assertEquals(0.7, hop.player().visit("PA") / stay.player().visit("PA"), 1e-9);
    }

    @Test
    void fundraisersRaiseMoreInDonorHubs() {
        GameEngine hub = manual(6), elsewhere = manual(6);
        for (int d = 0; d < 7; d++) {
            hub.apply(new Command.Campaign(new CampaignOrder.Slot(d * 3 + 2, Activity.FUNDRAISER, "NY", 0)));
            elsewhere.apply(new Command.Campaign(new CampaignOrder.Slot(d * 3 + 2, Activity.FUNDRAISER, "WI", 0)));
        }
        endTurn(hub);
        endTurn(elsewhere);
        assertTrue(hub.player().raisedLastTurn() > elsewhere.player().raisedLastTurn() + 3,
                hub.player().raisedLastTurn() + " vs " + elsewhere.player().raisedLastTurn());
    }

    @Test
    void autoFillFillsOnlyTheEmptySlotsAndClearEmptiesTheWeek() {
        GameEngine g = manual(8);
        g.apply(new Command.Campaign(new CampaignOrder.Slot(4, Activity.SPEECH, "MI", 0)));
        g.apply(new Command.Campaign(new CampaignOrder.FillSchedule()));
        CampaignOrders o = g.player().orders();
        assertEquals(Activity.SPEECH, o.activity(4));
        for (int i = 0; i < CampaignOrders.SLOTS; i++) assertFalse(o.activity(i) == Activity.NONE, "slot " + i + " filled");
        g.apply(new Command.Campaign(new CampaignOrder.ClearSchedule()));
        for (int i = 0; i < CampaignOrders.SLOTS; i++) assertEquals(Activity.NONE, g.player().orders().activity(i));
    }

    @Test
    void theStaffRebuildsTheWeekOnlyWhereItHasBeenHandedTheJob() {
        GameEngine kept = manual(10), handed = manual(10);
        kept.apply(new Command.Campaign(new CampaignOrder.Slot(0, Activity.PREP, null, 0)));
        handed.apply(new Command.Campaign(new CampaignOrder.Slot(0, Activity.PREP, null, 0)));
        handed.apply(new Command.Campaign(new CampaignOrder.Staff(StaffArea.SCHEDULE, true)));
        endTurn(kept);
        endTurn(handed);
        assertEquals(Activity.PREP, kept.player().orders().activity(0), "the player's own schedule stands");
        long planned = java.util.stream.IntStream.range(0, CampaignOrders.SLOTS).filter(i -> handed.player().orders().activity(i) != Activity.NONE).count();
        assertEquals(CampaignOrders.SLOTS, planned, "the scheduler plans a full week");
    }

    // ---- the ground game ------------------------------------------------------------------------------------------

    @Test
    void officesAndOrganisersAreOpenedAndHiredAtTheirCostsAndRecruitVolunteers() {
        GameEngine g = manual(12);
        double offices = g.player().offices("WI"), organizers = g.player().organizers("WI"), volunteers = g.player().volunteers("WI");
        g.apply(new Command.Campaign(new CampaignOrder.Field("WI", 3, 40)));
        endTurn(g);
        assertEquals(offices + 3, g.player().offices("WI"));
        assertEquals(organizers + 40, g.player().organizers("WI"));
        assertTrue(g.player().volunteers("WI") > volunteers);
        assertTrue(g.player().doors("WI") > 0);
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Field("WI", -999, 0))));
    }

    @Test
    void getOutTheVoteBuildsOnlyInTheLastSixWeeks() {
        GameEngine g = new GameEngine(setup(2));
        EngineFixtures.answerAll(g, 0);
        g.apply(new Command.EndTurn());
        for (var s : g.states().all()) assertEquals(0, g.player().getOutTheVote(s.code()), s.code());
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 16)));
        double gotv = 0;
        for (String s : List.of("PA", "MI", "WI", "GA", "AZ")) gotv += g.player().getOutTheVote(s);
        assertTrue(gotv > 0, "the battlegrounds are knocking on doors to turn out the vote");
    }

    // ---- the air war ----------------------------------------------------------------------------------------------

    @Test
    void ratingPointsInAMarketReachEveryStateItCoversAndArePaidFor() {
        GameEngine g = manual(14);
        CampaignGeography geo = g.campaignEngine().geography();
        int philly = geo.markets().stream().filter(m -> m.name().equals("Philadelphia")).findFirst().orElseThrow().id();
        double cpp = g.campaignEngine().costPerPoint(philly, g.campaignTurn());
        g.apply(new Command.Campaign(new CampaignOrder.Airtime(philly, 1000, AirWar.Tone.CONTRAST)));
        double before = g.player().spent();
        endTurn(g);
        for (String s : List.of("PA", "NJ", "DE")) assertTrue(g.player().tvStock(s) > 0, s);
        assertEquals(0, g.player().tvStock("OH"));
        assertTrue(g.player().spent() - before >= 1000 * cpp - 1e-6);
        assertTrue(g.player().ads("PA") > 0, "the ad money shows in the state's recent ad spending");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Airtime(philly, 2500, AirWar.Tone.POSITIVE))));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Airtime(9999, 100, AirWar.Tone.POSITIVE))));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Digital("XX", 1))));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Digital("PA", 9))));
    }

    @Test
    void airtimeGetsDearerTowardNovember() {
        GameEngine g = new GameEngine(setup(2));
        int market = 0;
        double july = g.campaignEngine().costPerPoint(market, g.campaignTurn());
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 23)));
        double late = g.campaignEngine().costPerPoint(market, g.campaignTurn());
        assertTrue(late > july * 1.4, july + " -> " + late);
    }

    @Test
    void theAdStudioProducesTestsAndAirsAdsAndKeepsTheEightNewest() {
        GameEngine g = manual(16);
        double cash = g.player().cash();
        g.apply(new Command.Campaign(new CampaignOrder.ProduceAd(0, Issue.COST_OF_LIVING, 21, AirWar.Messenger.VOTERS)));
        assertEquals(cash - AirWar.PRODUCE_COST, g.player().cash(), 1e-9);
        var ad = g.player().ads().get(0);
        assertFalse(ad.isTested());
        assertTrue(ad.quality() > 0 && ad.quality() <= 1);
        g.apply(new Command.Campaign(new CampaignOrder.TestAd(0)));
        ad = g.player().ads().get(0);
        assertTrue(ad.isTested() && ad.tested() <= 1);
        assertEquals(3, ad.quotes().size());
        g.apply(new Command.Campaign(new CampaignOrder.AirAd(0)));
        assertEquals(0, g.player().airing());
        for (int i = 0; i < 8; i++) g.apply(new Command.Campaign(new CampaignOrder.ProduceAd(2, Issue.CRIME, -1, AirWar.Messenger.NARRATOR)));
        assertEquals(AirWar.LIBRARY, g.player().ads().size());
        assertEquals(-1, g.player().airing(), "the aired ad fell out of the library");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.TestAd(8))));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.ProduceAd(3, Issue.CRIME, -1, AirWar.Messenger.NARRATOR))));
    }

    // ---- the final stretch, the opponent, commands and saves -----------------------------------------------------------

    @Test
    void theLastTwoWeeksArePlayedADayAtATime() {
        GameEngine g = new GameEngine(setup(2));
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 23)));
        assertEquals(LocalDate.of(2028, 10, 23), g.date());
        assertEquals(Granularity.DAILY, g.clock().mode());
        assertEquals(0, g.campaignDay());
        int days = 0;
        while (g.phase() == Phase.GENERAL_CAMPAIGN_2028 && days < 40) {
            assertEquals(g.date().getDayOfWeek().getValue() - 1, g.campaignDay());
            game.mandate.core.ai.AutoPilot.playTurn(g);
            days++;
        }
        assertEquals(15, days, "Monday 23 October to Monday 6 November");
        assertTrue(g.election().result2028() != null);
    }

    @Test
    void aDayThatIsOverCannotBeRescheduled() {
        GameEngine g = new GameEngine(setup(2));
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 24)));
        assertEquals(1, g.campaignDay());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Campaign(new CampaignOrder.Slot(0, Activity.RALLY, "PA", 0))));
        g.apply(new Command.Campaign(new CampaignOrder.Slot(3, Activity.RALLY, "PA", 0)));
        assertEquals(Activity.RALLY, g.player().orders().activity(3));
    }

    @Test
    void theOpponentPlansAFullCampaignFromThePolls() {
        GameEngine g = new GameEngine(setup(3));
        var o = g.opponent().orders();
        int slots = 0, markets = 0;
        for (int i = 0; i < CampaignOrders.SLOTS; i++) if (o.activity(i) != Activity.NONE) slots++;
        for (int m = 0; m < g.campaignEngine().geography().marketCount(); m++) if (o.tvGrp(m) > 0) markets++;
        assertEquals(CampaignOrders.SLOTS, slots);
        assertTrue(markets >= 5, markets + " markets");
        assertFalse(g.election().opponentReasoning().isBlank());
    }

    @Test
    void everyCampaignOrderSurvivesTheCommandLog() {
        List<CampaignOrder> all = List.of(new CampaignOrder.Slot(5, Activity.CHURCH, "GA", 2), new CampaignOrder.FillSchedule(),
                new CampaignOrder.ClearSchedule(), new CampaignOrder.Staff(StaffArea.ADS, false), new CampaignOrder.Airtime(4, 650, AirWar.Tone.NEGATIVE),
                new CampaignOrder.NationalTv(AirWar.Network.CABLE, 125), new CampaignOrder.Digital("AZ", 0.4),
                new CampaignOrder.DigitalMix(List.of(0.2, 0.2, 0.2, 0.2, 0.1, 0.1)),
                new CampaignOrder.PlatformMessage(AirWar.Platform.SEARCH, AirWar.DigitalMessage.GOTV), new CampaignOrder.Field("NV", 1, 15),
                new CampaignOrder.Transfer("NC", 0.25), new CampaignOrder.RedBox("MI", true), new CampaignOrder.Headquarters(2),
                new CampaignOrder.Message(null), new CampaignOrder.Message(Issue.HEALTHCARE), new CampaignOrder.PullAds(true, false),
                new CampaignOrder.ProduceAd(1, Issue.ECONOMY, -1, AirWar.Messenger.CANDIDATE), new CampaignOrder.TestAd(0), new CampaignOrder.AirAd(-1));
        for (CampaignOrder o : all) {
            Command c = new Command.Campaign(o);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) Json.parse(Json.write(CommandCodec.toJson(c)));
            assertEquals(c, CommandCodec.fromJson(m), o.toString());
        }
    }

    @Test
    void aSaveCarriesTheWholeCampaignAndReplaysExactly() {
        GameEngine g = new GameEngine(setup(5));
        g.apply(new Command.Campaign(new CampaignOrder.ProduceAd(0, Issue.ECONOMY, -1, AirWar.Messenger.CANDIDATE)));
        g.apply(new Command.Campaign(new CampaignOrder.AirAd(0)));
        g.apply(new Command.Campaign(new CampaignOrder.Staff(StaffArea.FIELD, false)));
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 10, 25)));
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(g.fingerprint(), loaded.fingerprint());
        assertTrue(SaveManager.consistent(SaveManager.save(g)), "the snapshot matches a replay of the command log");
        assertEquals(g.player().orders().tvGrp(0), loaded.player().orders().tvGrp(0));
        assertEquals(g.player().doorsToDate("PA"), loaded.player().doorsToDate("PA"));
        assertEquals(1, loaded.player().ads().size());
        assertFalse(loaded.election().staffRuns(StaffArea.FIELD));
    }

    @Test
    void campaignEffectsReachTheMarginsThroughTheElectorate() {
        GameEngine g = new GameEngine(setup(2));
        playUntil(g, x -> !x.date().isBefore(LocalDate.of(2028, 9, 18)));
        double moved = 0;
        for (String s : List.of("PA", "MI", "WI", "GA", "AZ", "NV", "NC")) moved += Math.abs(g.localShift(s));
        assertTrue(moved > 1, "both campaigns' stocks move the battlegrounds: " + moved);
        assertTrue(moved < 40, "but by points, not landslides: " + moved);
        assertEquals(0.12, CampaignEngine.gotvSat(10), 1e-9);
        assertEquals(0.1, CampaignEngine.fieldSat(10), 1e-9);
    }
}
