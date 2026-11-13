package game.mandate.core.hill;

import game.mandate.core.congress.BillCatalog;
import game.mandate.core.data.Json;
import game.mandate.core.effects.Effect;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UsState;
import game.mandate.core.policy.PolicyCatalog;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Congress as Resolute Desk models it: seating, votes, the budget calendar, elections, saves and determinism. */
class HillTest {
    static final HillEngine ENGINE = new HillEngine(BillCatalog.load(), PolicyCatalog.shared());
    static final StateCatalog STATES = StateCatalog.load();

    static HillEngine.Context ctx(LocalDate date, Party president, int houseDem, int senateDem, double approval) {
        Map<String, Double> lean = new TreeMap<>();
        for (UsState u : STATES.all()) if (!u.code().equals("DC")) lean.put(u.code(), u.baselineMargin() + 1.5);
        return new HillEngine.Context(date, 20, president, approval, 60, 60, 0.6, 5, List.of(), Map.of(), 10, 1.0, houseDem, senateDem, 2.0, 4.2, 4.5,
                6.0, 100, 31, lean, LocalDate.of(2030, 11, 5), 0, 0);
    }

    static HillState seat(Party president, int houseDem, int senateDem, long seed) {
        Map<String, Party> one = new TreeMap<>(), three = new TreeMap<>();
        Map<String, Integer> house = new TreeMap<>();
        for (UsState u : STATES.all()) {
            if (u.code().equals("DC")) continue;
            u.senateUp2030().ifPresent(p -> one.put(u.code(), p));
            u.senateUp2028().ifPresent(p -> three.put(u.code(), p));
            house.put(u.code(), u.electoralVotes() - 2);
        }
        return ENGINE.newHill(ctx(LocalDate.of(2029, 1, 22), president, houseDem, senateDem, 50), one, three, house, ENGINE.firstLeaders(), 2, new DeterministicRandomManager(seed));
    }

    @Test
    void theNewCongressMatchesTheElection() {
        HillState s = seat(Party.DEMOCRAT, 224, 51, 1);
        assertEquals(224, s.houseDemocrats().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(435, s.houseSeats().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(100, s.senate().size());
        assertEquals(51, s.senate().stream().filter(x -> x.party() == Party.DEMOCRAT).count());
        for (String st : s.houseSeats().keySet()) assertEquals(2, s.senate().stream().filter(x -> x.state().equals(st)).count(), st);
        for (int ch = 0; ch < 2; ch++) {
            int c = ch;
            assertEquals(ch == 0 ? 435 : 100, s.blocs().stream().filter(b -> b.chamber() == c).mapToInt(HillState.Bloc::seats).sum());
        }
        assertTrue(s.members().stream().anyMatch(m -> m.role().equals("Speaker") && m.name().equals("Marcus Bell")), "the era's Speaker");
        assertEquals(24, s.members().stream().filter(m -> m.role().equals("Chair")).count());
    }

    @Test
    void theWhipCountsThePresidentsOwnPartyAndTheMajority() {
        LocalDate d = LocalDate.of(2029, 5, 7);
        HillState unified = seat(Party.DEMOCRAT, 228, 52, 2), divided = seat(Party.DEMOCRAT, 205, 47, 2);
        String dem = "lower_costs";
        double u = ENGINE.whip(unified, ctx(d, Party.DEMOCRAT, 228, 52, 50), dem, 1, 0.5, false).passChance();
        double v = ENGINE.whip(divided, ctx(d, Party.DEMOCRAT, 205, 47, 50), dem, 1, 0.5, false).passChance();
        assertTrue(u > v, "a party bill is likelier with the president's party in charge: " + u + " vs " + v);
        // the read changes nothing
        String before = Json.write(unified.toJson());
        ENGINE.whip(unified, ctx(d, Party.DEMOCRAT, 228, 52, 50), dem, 1, 0.5, false);
        assertEquals(before, Json.write(unified.toJson()));
    }

    @Test
    void aRefusedSendChangesNothing() {
        HillState s = seat(Party.DEMOCRAT, 228, 52, 3);
        HillEngine.Context c = ctx(LocalDate.of(2029, 5, 7), Party.DEMOCRAT, 228, 52, 50);
        int sent = 0;
        for (var b : BillCatalog.load().availableTo(Party.DEMOCRAT)) {
            if (sent == 3) break;
            ENGINE.propose(s, c, b.id(), 1, 0.5, false);
            sent++;
        }
        String before = Json.write(s.toJson());
        String fourth = BillCatalog.load().availableTo(Party.DEMOCRAT).get(3).id();
        assertThrows(IllegalArgumentException.class, () -> ENGINE.propose(s, c, fourth, 1, 0.5, false));
        assertEquals(before, Json.write(s.toJson()));
    }

    @Test
    void theFiscalYearEndsInAStopgapAShutdownFightOrABudget() {
        int fights = 0;
        for (long seed = 1; seed <= 10; seed++) {
            HillState s = seat(Party.REPUBLICAN, 230, 46, seed);    // divided: Democrats hold the House
            DeterministicRandomManager rng = new DeterministicRandomManager(seed);
            for (LocalDate d = LocalDate.of(2029, 5, 7); d.isBefore(LocalDate.of(2029, 10, 8)); d = d.plusWeeks(1))
                ENGINE.week(s, ctx(d, Party.REPUBLICAN, 230, 46, 45), rng);
            boolean decided = s.pending().stream().anyMatch(p -> p.kind().equals("cr") || p.kind().equals("shutdown")) || s.shutdown() || s.crUntil() >= 0;
            boolean funded = true;
            for (int i = 0; i < 12; i++) funded &= s.appropriation(i) == 3;
            assertTrue(decided || funded, "seed " + seed + ": the deadline passed without a decision");
            if (decided) fights++;
        }
        assertTrue(fights >= 5, "divided government should usually fight over funding: " + fights);
    }

    @Test
    void theMidtermsReseatTheHouseByStateAndTheSenateRaceByRace() {
        HillState s = seat(Party.DEMOCRAT, 222, 51, 4);
        DeterministicRandomManager rng = new DeterministicRandomManager(4);
        for (LocalDate d = LocalDate.of(2030, 7, 27); d.isBefore(LocalDate.of(2030, 11, 5)); d = d.plusWeeks(1))
            ENGINE.week(s, ctx(d, Party.DEMOCRAT, 222, 51, 38), rng);
        assertEquals(33, s.races().size(), "class I is up in 2030");
        HillEngine.Outcome o = ENGINE.holdElection(s, ctx(LocalDate.of(2030, 11, 5), Party.DEMOCRAT, 222, 51, 38), null, ENGINE.firstLeaders(), rng);
        Effect.CongressElected e = (Effect.CongressElected) o.effects().stream().filter(x -> x instanceof Effect.CongressElected).findFirst().orElseThrow();
        assertEquals(e.houseDem(), s.houseDemocrats().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(e.senateDem(), s.senate().stream().filter(x -> x.party() == Party.DEMOCRAT).count());
        assertTrue(e.houseDem() < 222, "an unpopular president's party loses House seats: " + e.houseDem());
        assertEquals(122, s.congressNumber());
        assertTrue(s.races().stream().allMatch(r -> r.result() != null));
    }

    @Test
    void savesRoundTripAndTheHillIsDeterministic() {
        HillState a = seat(Party.REPUBLICAN, 210, 53, 7), b = seat(Party.REPUBLICAN, 210, 53, 7);
        DeterministicRandomManager ra = new DeterministicRandomManager(7), rb = new DeterministicRandomManager(7);
        for (LocalDate d = LocalDate.of(2029, 5, 7); d.isBefore(LocalDate.of(2030, 3, 1)); d = d.plusWeeks(1)) {
            ENGINE.staff(a, ctx(d, Party.REPUBLICAN, 210, 53, 48), ra);
            ENGINE.week(a, ctx(d, Party.REPUBLICAN, 210, 53, 48), ra);
            ENGINE.staff(b, ctx(d, Party.REPUBLICAN, 210, 53, 48), rb);
            ENGINE.week(b, ctx(d, Party.REPUBLICAN, 210, 53, 48), rb);
        }
        String json = Json.write(a.toJson());
        assertEquals(json, Json.write(b.toJson()));
        assertEquals(json, Json.write(HillState.fromJson(Json.object(Json.parse(json))).toJson()));
        assertNotEquals(0, a.record().size());
    }
}
