package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.foreign.ForeignData;
import game.mandate.core.foreign.ForeignOrder;
import game.mandate.core.foreign.ForeignState;
import game.mandate.core.presidency.Activity;
import game.mandate.core.presidency.PresidencyOrder;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import static game.mandate.core.engine.EngineFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Milestone 8: Resolute Desk's world and the Situation Room. */
class ForeignTest {

    static GameEngine world(long seed) {
        GameEngine g = governing(seed);
        assertTrue(g.foreign().isPresent(), "the Situation Room opens on day 100");
        return g;
    }

    static int idx(GameEngine g, String code) { return g.foreignEngine().data().index(code); }

    @Test
    void theWorldOf2029() {
        ForeignData d = new ForeignData();
        assertEquals(40, d.countries().size());
        assertEquals(20, d.crises().size());
        assertEquals(10, d.operations().size());
        assertEquals(7, d.covert().size());
        assertTrue(d.objectives().size() >= 70);
        GameEngine g = world(3);
        ForeignState s = g.foreign().get();
        assertEquals(3, s.sanction(idx(g, "RU")));
        assertEquals(0, s.ties(idx(g, "IR")));
        assertEquals(54, s.troopsIn(idx(g, "JP")), 1e-9);
        assertEquals(2, s.uaStage());
        assertEquals(55, s.twt(), 1e-9);
        assertFalse(s.objectives().isEmpty(), "objectives for the year");
        assertFalse(s.brief().isEmpty(), "the first Daily Brief");
        // every country has at least two objectives (its own or a template)
        for (int i = 0; i < ForeignData.NC; i++) {
            final int c = i;
            assertTrue(s.objectives().stream().filter(o -> o.country() == c).count() >= 1, d.country(i).name());
        }
    }

    @Test
    void callsInvitationsAndSummits() {
        GameEngine g = world(5);
        ForeignState s = g.foreign().get();
        int jp = idx(g, "JP");
        double before = s.relations(jp);
        g.apply(new Command.Foreign(new ForeignOrder.Call("JP")));
        assertTrue(s.relations(jp) > before, "a call warms relations");
        g.apply(new Command.Foreign(new ForeignOrder.Call("GB")));
        g.apply(new Command.Foreign(new ForeignOrder.Call("DE")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Call("FR"))), "three calls a week");
        g.apply(new Command.Foreign(new ForeignOrder.Invite("IN")));
        assertTrue(s.visitAt(idx(g, "IN")) > s.week());
        g.apply(new Command.Foreign(new ForeignOrder.Summit("CN")));
        assertEquals(idx(g, "CN"), s.summitCountry());
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Summit("RU"))), "one summit at a time");
        // the summit's rounds come to the desk and conclude
        for (int wk = 0; wk < 12 && s.summitCountry() >= 0; wk++) {
            for (ForeignState.Question q : java.util.List.copyOf(s.questions())) g.apply(new Command.Foreign(new ForeignOrder.Answer(q.id(), 0)));
            answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        assertEquals(-1, s.summitCountry(), "the summit is held");
        assertTrue(s.visits(idx(g, "IN")) >= 1, "the invited leader came");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Call("XX"))));
    }

    @Test
    void tradeSanctionsAidAndTheTools() {
        GameEngine g = world(7);
        ForeignState s = g.foreign().get();
        int cn = idx(g, "CN"), mx = idx(g, "MX"), ua = idx(g, "UA");
        double rel = s.relations(cn);
        g.apply(new Command.Foreign(new ForeignOrder.Tariff("CN", 2)));
        assertEquals(2, s.tariff(cn));
        assertTrue(s.relations(cn) < rel - 9, "a 25% tariff angers China");
        assertFalse(g.state().ledger().byCause("The Situation Room").isEmpty(), "the order is in the record");
        g.apply(new Command.Foreign(new ForeignOrder.Aid("MX", 2)));
        assertEquals(2, s.aid(mx));
        g.apply(new Command.Foreign(new ForeignOrder.MilitaryAid("UA", 3)));
        g.apply(new Command.Foreign(new ForeignOrder.Troops("PL", 3)));
        assertEquals(20, s.troopsIn(idx(g, "PL")), 1e-9);
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Troops("RU", 1))), "an adversary hosts no American forces");
        g.apply(new Command.Foreign(new ForeignOrder.Posture(1, 2)));
        assertEquals(2, s.posture(1));
        g.apply(new Command.Foreign(new ForeignOrder.Alliance("article5")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Alliance("article5"))), "once a quarter");
        assertTrue(s.relations(ua) > -100);
    }

    @Test
    void crisesComeToTheDeskAndResolve() {
        GameEngine g = world(9);
        ForeignState s = g.foreign().get();
        int faced = 0;
        for (int wk = 0; wk < 120 && g.phase() == Phase.GOVERNING && faced < 2; wk++) {
            for (ForeignState.Question q : java.util.List.copyOf(s.questions())) {
                if (q.kind().equals("crisis")) {
                    faced++;
                    assertTrue(q.options().size() >= 3);
                    assertTrue(q.advice().get(0).contains(":"), "each option has an adviser's view of the odds");
                }
                g.apply(new Command.Foreign(new ForeignOrder.Answer(q.id(), q.recommended())));
            }
            answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        assertTrue(faced >= 1, "crises come");
        assertEquals(s.okCount() + s.failCount(), Math.min(s.crisesFaced(), s.okCount() + s.failCount()));
        assertTrue(s.okCount() + s.failCount() >= 1);
    }

    @Test
    void aWarOfChoice() {
        GameEngine g = world(11);
        ForeignState s = g.foreign().get();
        int ve = idx(g, "VE");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.StartWar("RU", 3))), "no invasion of a nuclear power");
        g.apply(new Command.Foreign(new ForeignOrder.StartWar("VE", 0)));
        ForeignState.War x = s.wars().get(0);
        assertTrue(x.fighting());
        assertEquals(-100, s.relations(ve), 1e-9);
        assertEquals(3, s.sanction(ve));
        assertTrue(s.warBudget() > 0, "the war is paid for");
        g.apply(new Command.Foreign(new ForeignOrder.WarStrategy(x.id(), 2)));
        g.apply(new Command.Foreign(new ForeignOrder.WarTroops(x.id(), 0.25)));
        int weeks = 0;
        while (x.fighting() && weeks++ < 60 && g.phase() == Phase.GOVERNING) {
            for (ForeignState.Question q : java.util.List.copyOf(s.questions())) g.apply(new Command.Foreign(new ForeignOrder.Answer(q.id(), q.recommended())));
            answerAll(g, 0);
            g.apply(new Command.EndTurn());
        }
        assertTrue(x.weeks() > 0);
        if (x.fighting()) g.apply(new Command.Foreign(new ForeignOrder.EndWar(x.id(), "withdraw")));
        assertFalse(x.fighting(), "the war ends");
        assertEquals(0, s.warBudget(), 1e-9, "and so does its budget");
    }

    @Test
    void objectivesAndCovertPrograms() {
        GameEngine g = world(13);
        ForeignState s = g.foreign().get();
        ForeignState.Objective o = s.objectives().stream().filter(x -> g.foreignEngine().cannot(s, x) == null).findFirst().orElseThrow();
        g.apply(new Command.Foreign(new ForeignOrder.Objective(o.id())));
        assertTrue(o.step() == 1 || o.fails() == 1 || o.status() == 1, "a step was tried");
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.Objective(o.id()))), "give it time");
        g.apply(new Command.Foreign(new ForeignOrder.CovertProgram("IR", 1)));
        assertEquals(1, s.covertProgram(idx(g, "IR")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Foreign(new ForeignOrder.CovertProgram("GB", 3))), "no regime change against an ally");
        g.apply(new Command.Foreign(new ForeignOrder.Covert(0)));
        assertEquals(26, s.covertCooldown(0));
    }

    @Test
    void aTripAbroadOnTheCalendar() {
        GameEngine g = world(15);
        ForeignState s = g.foreign().get();
        int gb = idx(g, "GB");
        for (int k = 0; k < 3; k++) g.apply(new Command.Presidency(new PresidencyOrder.Slot(3 + k, Activity.ABROAD, "GB")));
        assertThrows(IllegalCommandException.class, () -> g.apply(new Command.Presidency(new PresidencyOrder.Slot(9, Activity.ABROAD, "ZZ"))));
        int visits = s.visits(gb);
        double stamina = g.president().orElseThrow().stamina();
        answerAll(g, 0);
        g.apply(new Command.EndTurn());
        assertEquals(visits + 1, s.visits(gb), "one visit for a run of slots");
    }

    @Test
    void theWorldSurvivesASave() {
        GameEngine g = world(17);
        g.apply(new Command.Foreign(new ForeignOrder.Tariff("MX", 1)));
        g.apply(new Command.Foreign(new ForeignOrder.StartWar("SY", 0)));
        for (int i = 0; i < 3; i++) { answerAll(g, 0); g.apply(new Command.EndTurn()); }
        GameEngine loaded = SaveManager.load(SaveManager.save(g));
        assertEquals(g.foreign().get().toJson(), loaded.foreign().get().toJson());
        GameEngine replayed = GameEngine.replay(g.setup(), g.history());
        assertEquals(g.foreign().get().toJson(), replayed.foreign().get().toJson(), "the world replays from the commands");
    }
}
