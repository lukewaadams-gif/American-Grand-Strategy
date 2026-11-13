package game.mandate.core.westwing;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Json;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The executive as Resolute Desk models it: seating, the Senate, orders and rules, the courts, scandals, the press and stories. */
class WestWingTest {
    static final WestWingEngine ENGINE = new WestWingEngine(new WestWingData());

    static List<WestWingEngine.SenateBloc> senate(int dem) {
        List<WestWingEngine.SenateBloc> b = new ArrayList<>();
        b.add(new WestWingEngine.SenateBloc("Progressive senators", Party.DEMOCRAT, (int) Math.round(dem * 0.25), -0.7, -0.6, 0.85, 50));
        b.add(new WestWingEngine.SenateBloc("Mainstream Democrats", Party.DEMOCRAT, (int) Math.round(dem * 0.6), -0.4, -0.3, 0.9, 50));
        b.add(new WestWingEngine.SenateBloc("Moderate Democrats", Party.DEMOCRAT, dem - (int) Math.round(dem * 0.25) - (int) Math.round(dem * 0.6), -0.1, 0, 0.7, 50));
        int rep = 100 - dem;
        b.add(new WestWingEngine.SenateBloc("Hardliners", Party.REPUBLICAN, (int) Math.round(rep * 0.2), 0.75, 0.8, 0.85, 40));
        b.add(new WestWingEngine.SenateBloc("Mainstream Republicans", Party.REPUBLICAN, (int) Math.round(rep * 0.68), 0.5, 0.5, 0.85, 40));
        b.add(new WestWingEngine.SenateBloc("Moderate Republicans", Party.REPUBLICAN, rep - (int) Math.round(rep * 0.2) - (int) Math.round(rep * 0.68), 0.2, 0.1, 0.7, 40));
        return b;
    }

    static WestWingEngine.Context ctx(LocalDate date, Party president, int senateDem, Map<String, Double> vars, boolean real) {
        Map<Issue, Double> sal = new EnumMap<>(Issue.class), app = new EnumMap<>(Issue.class);
        for (Issue i : Issue.values()) { sal.put(i, i.baseSalience()); app.put(i, 47.0); }
        Party senateMaj = senateDem > 50 ? Party.DEMOCRAT : senateDem < 50 ? Party.REPUBLICAN : president;
        return new WestWingEngine.Context(date, 42, 30, president, "President Test", 47, app, sal, 70, 30, new double[]{60, 60, 60, 60, 60, 60, 40, 60, 60},
                senate(senateDem), president, senateMaj, 0.85, false, 4.3, 2.8, false, null, null, false, 0,
                k -> vars.getOrDefault(k, Double.NaN), real, List.of(), null);
    }

    static WestWingState seat(Party p, int senateDem, boolean real) {
        Map<Integer, String[]> first = new TreeMap<>();
        first.put(WestWingData.P_STATE, new String[]{"Ada Stone", "1"});
        first.put(WestWingData.P_TREAS, null);
        return ENGINE.seat(ctx(LocalDate.of(2029, 5, 7), p, senateDem, Map.of(), real), first, new DeterministicRandomManager(1));
    }

    @Test
    void theWestWingIsStaffedOnDayOneHundred() {
        WestWingState s = seat(Party.DEMOCRAT, 52, false);
        for (int r = 0; r < WestWingState.NSTAFF; r++) assertTrue(s.staff(r) != null, "staff " + r);
        assertEquals("Ada Stone", s.post(WestWingData.P_STATE).name());
        assertEquals(null, s.post(WestWingData.P_TREAS), "a post the hundred days left empty has an acting official");
        assertEquals(20, s.everyone().size() - WestWingState.NSTAFF);
        assertEquals(9, s.justices().size());
        assertEquals("Harold Whitcomb", s.justices().get(0).name(), "fictional justices by default");
        assertEquals("John Roberts", seat(Party.DEMOCRAT, 52, true).justices().get(0).name(), "the real Court with the real roster");
        assertTrue(s.vicePresident() != null);
    }

    @Test
    void aHostileSenateSitsOnWeakNomineesAndConfirmsStrongOnes() {
        WestWingState s = seat(Party.DEMOCRAT, 45, false);
        WestWingEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.DEMOCRAT, 45, Map.of(), false);
        var options = ENGINE.options(s, c, WestWingData.P_TREAS, 0);
        double expert = ENGINE.chance(c, options.get(0)), loyalist = ENGINE.chance(c, options.get(1));
        assertTrue(expert > loyalist, "the expert is easier to confirm than the loyalist: " + expert + " vs " + loyalist);
        ENGINE.apply(s, c, new WestWingOrder.Nominate(WestWingData.P_TREAS, 0), new DeterministicRandomManager(2));
        assertThrows(IllegalArgumentException.class, () -> ENGINE.apply(s, c, new WestWingOrder.Nominate(WestWingData.P_TREAS, 1), new DeterministicRandomManager(2)));
        DeterministicRandomManager rng = new DeterministicRandomManager(3);
        for (int wk = 0; wk < 30 && s.nominee(WestWingData.P_TREAS) != null; wk++) ENGINE.week(s, c, rng);
        assertEquals(null, s.nominee(WestWingData.P_TREAS), "the Senate voted within thirty weeks");
    }

    @Test
    void anOrderTakesEffectAtOnceAndARuleAfterItsCommentPeriod() {
        WestWingState s = seat(Party.REPUBLICAN, 53, false);
        WestWingEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.REPUBLICAN, 53, Map.of(), false);
        DeterministicRandomManager rng = new DeterministicRandomManager(4);
        WestWingEngine.Outcome o = ENGINE.apply(s, c, new WestWingOrder.Issue("eo_drill"), rng);
        assertTrue(o.effects().stream().anyMatch(e -> e instanceof Effect.PolicyStep), "drilling's economics: " + o.effects());
        assertThrows(IllegalArgumentException.class, () -> ENGINE.apply(s, c, new WestWingOrder.Issue("eo_drill"), rng), "once is enough");
        assertThrows(IllegalArgumentException.class, () -> ENGINE.apply(s, c, new WestWingOrder.Issue("eo_troops"), rng), "troops at the border need the emergency");
        ENGINE.apply(s, c, new WestWingOrder.Issue("rule_mining"), rng);
        WestWingState.Action rule = s.actions().get(s.actions().size() - 1);
        assertEquals(WestWingState.Action.COMMENT, rule.status());
        for (int wk = 0; wk < 16; wk++) ENGINE.week(s, c, rng);
        assertTrue(rule.status() != WestWingState.Action.COMMENT, "the comment period ends");
        // revoking an order switches its economics off again
        WestWingState.Action drill = s.actions().get(0);
        if (drill.status() == WestWingState.Action.LIVE) {
            WestWingEngine.Outcome off = ENGINE.apply(s, c, new WestWingOrder.Revoke(drill.id()), rng);
            assertTrue(off.effects().stream().anyMatch(e -> e instanceof Effect.PolicyStep p && p.id().endsWith("-off")));
        }
    }

    @Test
    void riskyOrdersEndUpInCourtAndTheCourtsDecide() {
        int sued = 0, decided = 0;
        for (long seed = 1; seed <= 12; seed++) {
            WestWingState s = seat(Party.REPUBLICAN, 53, false);
            WestWingEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.REPUBLICAN, 53, Map.of(), false);
            DeterministicRandomManager rng = new DeterministicRandomManager(seed);
            ENGINE.apply(s, c, new WestWingOrder.Issue("eo_birthright"), rng);
            if (!s.cases().isEmpty()) sued++;
            for (int wk = 0; wk < 60; wk++) {
                ENGINE.week(s, c, rng);
                for (WestWingState.Decision d : new ArrayList<>(s.decisions())) ENGINE.answer(s, c, d.id(), d.recommended(), rng);
            }
            if (!s.cases().isEmpty() && s.cases().get(0).done()) decided++;
        }
        assertTrue(sued >= 10, "an order with a 92% legal risk is nearly always sued: " + sued);
        assertTrue(decided >= 6, "and the courts rule within a year: " + decided);
    }

    @Test
    void aBigScandalAsksForAResponseAndFadesWithTime() {
        WestWingState s = seat(Party.DEMOCRAT, 52, false);
        WestWingEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.DEMOCRAT, 52, Map.of(), false);
        WestWingEngine.Outcome out = WestWingEngine.Outcome.empty();
        ENGINE.trouble.add(s, c, WestWingState.Scandal.ETHICS, "A test scandal", "Body.", 4, s.post(WestWingData.P_HUD), false, out);
        assertTrue(s.decisions().stream().anyMatch(d -> d.kind().equals("scandal")));
        assertTrue(out.effects().stream().anyMatch(e -> e instanceof Effect.Scandal));
        DeterministicRandomManager rng = new DeterministicRandomManager(5);
        WestWingState.Decision d = s.decisions().stream().filter(x -> x.kind().equals("scandal")).findFirst().orElseThrow();
        ENGINE.answer(s, c, d.id(), 0, rng);
        assertTrue(s.scandals().get(0).severity() < 4, "releasing everything takes the sting out");
        for (int wk = 0; wk < 120; wk++) ENGINE.week(s, c, rng);
        assertTrue(s.scandals().get(0).over(), "and it fades");
    }

    @Test
    void aPressConferenceIsThreeQuestionsThenAReview() {
        WestWingState s = seat(Party.DEMOCRAT, 52, false);
        WestWingEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.DEMOCRAT, 52, Map.of(), false);
        DeterministicRandomManager rng = new DeterministicRandomManager(6);
        ENGINE.apply(s, c, new WestWingOrder.PressConference(), rng);
        boolean performance = false;
        for (int q = 0; q < 3; q++) {
            WestWingState.Decision d = s.decisions().stream().filter(x -> x.kind().equals("qa")).findFirst().orElseThrow();
            WestWingEngine.Outcome o = ENGINE.answer(s, c, d.id(), 0, rng);
            performance |= o.effects().stream().anyMatch(e -> e instanceof Effect.Performance);
        }
        assertTrue(performance, "the review moves the record");
        assertFalse(s.inQuestions());
        assertThrows(IllegalArgumentException.class, () -> ENGINE.apply(s, c, new WestWingOrder.PressConference(), rng), "not two weeks running");
    }

    @Test
    void theStateOfTheUnionComesInFebruaryAndTheStaffFinishesItIfLeft() {
        WestWingState s = seat(Party.DEMOCRAT, 52, false);
        DeterministicRandomManager rng = new DeterministicRandomManager(7);
        LocalDate d = LocalDate.of(2029, 12, 31);
        for (int wk = 0; wk < 8; wk++, d = d.plusWeeks(1)) ENGINE.week(s, ctx(d, Party.DEMOCRAT, 52, Map.of(), false), rng);
        assertEquals(1, s.speeches(), "the State of the Union was given");
        assertTrue(s.record().stream().anyMatch(r -> r.contains("State of the Union")));
    }

    @Test
    void storiesStartWhenTheirConditionsHold() {
        WestWingState s = seat(Party.DEMOCRAT, 52, false);
        Map<String, Double> vars = new TreeMap<>(Map.of("ai", 1.0, "unemp", 6.5, "app", 40.0, "infl", 2.0, "year", 2029.0, "month", 6.0));
        DeterministicRandomManager rng = new DeterministicRandomManager(8);
        LocalDate d = LocalDate.of(2029, 5, 7);
        for (int wk = 0; wk < 200 && s.storylines().isEmpty(); wk++, d = d.plusWeeks(1)) ENGINE.week(s, ctx(d, Party.DEMOCRAT, 52, vars, false), rng);
        assertFalse(s.storylines().isEmpty(), "with AI and high unemployment, a story starts");
        assertTrue(s.decisions().stream().anyMatch(x -> x.kind().equals("story")) || s.storylines().get(0).status() != WestWingState.Storyline.ACTIVE);
    }

    @Test
    void savesRoundTripAndTheWestWingIsDeterministic() {
        WestWingState a = seat(Party.REPUBLICAN, 48, false), b = seat(Party.REPUBLICAN, 48, false);
        DeterministicRandomManager ra = new DeterministicRandomManager(9), rb = new DeterministicRandomManager(9);
        LocalDate d = LocalDate.of(2029, 5, 7);
        for (int wk = 0; wk < 80; wk++, d = d.plusWeeks(1)) {
            WestWingEngine.Context c = ctx(d, Party.REPUBLICAN, 48, Map.of("app", 44.0, "year", 2030.0), false);
            ENGINE.week(a, c, ra);
            ENGINE.week(b, c, rb);
        }
        String json = Json.write(a.toJson());
        assertEquals(json, Json.write(b.toJson()));
        assertEquals(json, Json.write(WestWingState.fromJson(Json.object(Json.parse(json))).toJson()));
        assertNotEquals(0, a.record().size());
    }
}
