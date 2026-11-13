package game.mandate.core.people;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.data.Tuning;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real-person start (docs/DECISIONS_TO_REVIEW.md): with real people, the 2029 world seats only real people or
 * office-labelled placeholders, never a made-up name; fictional newcomers appear only from 2030, at the configured rate.
 */
class RealPeopleTest {
    final RealPeople data = RealPeople.load();

    /** Every name the 2029 data holds, with the placeholders' forms: what a 2029 name may be. */
    Set<String> realNames() {
        Set<String> s = new HashSet<>();
        for (var x : data.senators()) s.add(x.name());
        for (var x : data.allCabinet()) s.add(x.name());
        for (var x : data.allCommittees()) s.add(x.name());
        for (var x : data.allStaff()) s.add(x.name());
        for (var c : game.mandate.core.campaign.CandidateCatalog.load(true).all()) if (c.isReal()) s.add(c.name());
        for (var j : new game.mandate.core.westwing.WestWingData().court()) s.add(j.real());
        for (String k : List.of("houseD", "houseR", "senateD", "senateR")) data.leader(k).ifPresent(s::add);
        for (Party p : Party.values()) {
            for (String ch : List.of("H", "S")) for (var x : data.swing(ch, p, 2000)) s.add(x.name());
            for (var j : data.judges(p)) s.add(j.name());
        }
        return s;
    }

    static boolean placeholder(String name) {
        return name.startsWith("Senator from ") || name.startsWith("Representative from ") || name.startsWith("the ") || name.startsWith("Acting ")
                || name.startsWith("A federal appeals judge") || name.equals("the Vice President");
    }

    @Test
    void theDataIsWhole() {
        assertEquals(100, data.senators().size());
        Map<String, Integer> perState = new TreeMap<>();
        for (var s : data.senators()) perState.merge(s.state(), 1, Integer::sum);
        assertEquals(50, perState.size());
        assertTrue(perState.values().stream().allMatch(n -> n == 2), "two senators a state");
        for (String k : List.of("houseD", "houseR", "senateD", "senateR")) assertTrue(data.leader(k).isPresent(), k);
        for (String post : game.mandate.core.westwing.WestWingData.POST_NAME)
            for (Party p : Party.values()) assertFalse(data.cabinet(post, p).isEmpty(), "a real prospect for " + post + " (" + p + ")");
        for (String role : game.mandate.core.westwing.WestWingData.STAFF_TITLE)
            for (Party p : Party.values()) assertFalse(data.staff(role, p).isEmpty(), "a real prospect for " + role + " (" + p + ")");
    }

    @Test
    void the2029WorldSeatsOnlyRealPeopleOrOffices() {
        for (long seed : new long[]{4, 9}) {
            boolean dem = seed % 2 == 0;
            GameSetup setup = new GameSetup(seed, dem ? Party.DEMOCRAT : Party.REPUBLICAN, dem ? "bg_shapiro" : "bg_vance", dem ? "bg_vance" : "bg_shapiro",
                    Scenario.TOSS_UP, GameSetup.Difficulty.STANDARD, GameSetup.Length.FULL_TERM, true, null);
            GameEngine g = new GameEngine(setup);
            int guard = 0;
            while (!g.over() && g.phase() != Phase.GOVERNING && guard++ < 400) AutoPilot.playTurn(g);
            if (g.phase() != Phase.GOVERNING) continue;   // lost 2028: try the other seed
            Set<String> ok = realNames();
            List<String> invented = new ArrayList<>();
            g.hill().ifPresent(h -> {
                for (var m : h.members()) if (!ok.contains(m.name()) && !placeholder(m.name())) invented.add("member " + m.role() + ": " + m.name());
                for (var x : h.senate()) if (!ok.contains(x.holder()) && !placeholder(x.holder())) invented.add("senator " + x.state() + ": " + x.holder());
            });
            g.westWing().ifPresent(w -> {
                for (var p : w.everyone()) if (!ok.contains(p.name()) && !placeholder(p.name())) invented.add("official: " + p.name());
                assertTrue(g.candidates().all().stream().anyMatch(c -> c.name().equals(w.vicePresident())) || placeholder(w.vicePresident()), "the vice president is real: " + w.vicePresident());
            });
            assertTrue(invented.isEmpty(), "invented people in a 2029 real-people world: " + invented);
            assertTrue(g.date().getYear() <= 2029, "checked in 2029");
        }
    }

    /** The fictional newcomers in a game now: names that are neither real nor an office's placeholder. */
    List<String> newcomers(GameEngine g) {
        Set<String> ok = realNames();
        List<String> l = new ArrayList<>();
        g.hill().ifPresent(h -> {
            for (var m : h.members()) if (!ok.contains(m.name()) && !placeholder(m.name())) l.add(m.name());
            for (var x : h.senate()) if (!ok.contains(x.holder()) && !placeholder(x.holder())) l.add(x.holder());
        });
        g.westWing().ifPresent(w -> { for (var p : w.everyone()) if (!ok.contains(p.name()) && !placeholder(p.name())) l.add(p.name()); });
        return l;
    }

    @Test
    void newcomersEmergeAfter2030InAWholeGame() {
        int found = 0;
        for (long seed = 4; seed < 40 && found == 0; seed += 2) {
            GameSetup setup = new GameSetup(seed, Party.DEMOCRAT, "bg_shapiro", "bg_vance", Scenario.BLUE_ENVIRONMENT, GameSetup.Difficulty.STORY,
                    GameSetup.Length.FULL_TERM, true, null);
            GameEngine g = new GameEngine(setup);
            int guard = 0;
            while (!g.over() && g.date().isBefore(java.time.LocalDate.of(2030, 1, 1)) && guard++ < 600) AutoPilot.playTurn(g);
            if (g.over()) continue;
            assertTrue(newcomers(g).isEmpty(), "no newcomer before 2030: " + newcomers(g));
            while (!g.over() && g.date().isBefore(java.time.LocalDate.of(2033, 2, 1)) && guard++ < 1600) AutoPilot.playTurn(g);
            found = newcomers(g).size();
        }
        assertTrue(found > 0, "fictional newcomers have emerged by 2033");
    }

    @Test
    void newcomersAppearOnlyFrom2030AtTheConfiguredRate() {
        People p = People.of(true, Tuning.defaults());
        assertEquals(0, p.newcomerRate(2028));
        assertEquals(0, p.newcomerRate(2029));
        for (int i = 0; i < 1000; i++) {
            assertFalse(p.newcomer(2028, i / 1000.0));
            assertFalse(p.newcomer(2029, i / 1000.0));
        }
        double prev = 0;
        for (int y = 2030; y <= 2036; y++) {
            assertTrue(p.newcomerRate(y) > 0, "newcomers in " + y);
            assertTrue(p.newcomerRate(y) >= prev, "more of them as the years go on");
            prev = p.newcomerRate(y);
        }
        assertEquals(p.newcomerRate(2036), p.newcomerRate(2040), "years after the last listed use its rate");
        Tuning none = Tuning.defaults().withOverrides(Map.of("people.newcomers.2030", "0", "people.newcomers.2031", "0"));
        assertEquals(0, People.of(true, none).newcomerRate(2030), "the rate is configurable");
        assertTrue(People.fictional().newcomer(2028, 0.99), "the fictional cast always makes people up");
    }
}
