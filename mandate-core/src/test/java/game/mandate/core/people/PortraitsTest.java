package game.mandate.core.people;

import game.mandate.core.campaign.Candidate;
import game.mandate.core.campaign.CandidateCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portraits (docs/BUG_FIX_REPORT.md, U-01: real men drawn with women's hair and blouses): every candidate the game can
 * draw has a look keyed by their own id, with the right presentation, and no look can put a beard on a blouse or a bob on
 * a tie. The presentation list here is written independently of data/portraits.csv, so a slip in either fails.
 */
class PortraitsTest {
    /** Each candidate's presentation, from the public record (not from data/portraits.csv). */
    static final Map<String, String> EXPECTED = new HashMap<>();
    static {
        for (String id : List.of("bg_harris", "bg_aoc", "bg_whitmer", "bg_raimondo", "bg_klobuchar", "bg_haley", "bg_sanders", "bg_greene",
                "d_reyes", "d_okafor", "r_marsh", "r_harlan")) EXPECTED.put(id, "f");
        for (String id : List.of("bg_newsom", "bg_buttigieg", "bg_shapiro", "bg_pritzker", "bg_beshear", "bg_moore", "bg_booker", "bg_khanna",
                "bg_kelly", "bg_gallego", "bg_murphy", "bg_ossoff", "bg_warnock", "bg_walz", "bg_emanuel", "bg_talarico", "bg_cooper", "bg_brown",
                "bg_cuban", "bg_smith", "bg_vance", "bg_rubio", "bg_trumpjr", "bg_desantis", "bg_youngkin", "bg_cruz", "bg_hawley", "bg_kemp",
                "bg_ramaswamy", "bg_carlson", "bg_rfk", "d_whitfield", "d_lindqvist", "r_calloway", "r_benton")) EXPECTED.put(id, "m");
    }

    final Portraits portraits = Portraits.load();

    @Test
    void everyCandidateHasTheirOwnLookWithTheRightPresentation() {
        List<Candidate> all = CandidateCatalog.load(true).all();
        assertEquals(47, all.size(), "the fictional cast and the real roster");
        for (Candidate c : all) {
            var look = portraits.look(c.id()).orElseThrow(() -> new AssertionError("no portrait for " + c.name() + " (" + c.id() + ")"));
            assertEquals(c.id(), look.id());
            assertEquals(EXPECTED.get(c.id()), look.presentation(), c.name() + "'s presentation");
        }
        assertEquals(EXPECTED.keySet(), new HashSet<>(all.stream().map(Candidate::id).toList()), "the expected list covers exactly the roster");
    }

    @Test
    void theTwoReportedInPlayAreMen() {
        assertEquals("m", portraits.look("bg_ossoff").orElseThrow().presentation());
        var vance = portraits.look("bg_vance").orElseThrow();
        assertEquals("m", vance.presentation());
        assertEquals("beard", vance.facial(), "JD Vance has a beard");
        for (var l : portraits.all().values())
            assertTrue((l.masculine() ? Portraits.MASCULINE_HAIR : Portraits.FEMININE_HAIR).contains(l.hair()), l.id() + "'s hair fits the presentation");
    }

    @Test
    void realPeopleCanBeToldApart() {
        Set<String> seen = new HashSet<>();
        for (var l : portraits.all().values()) {
            if (!l.id().startsWith("bg_")) continue;
            String key = l.presentation() + l.skin() + l.hair() + l.hairColour() + l.facial() + l.glasses() + l.face() + Math.round(l.age() * 10);
            assertTrue(seen.add(key), l.id() + " looks exactly like another real person");
        }
    }

    @Test
    void anInvalidLookIsRefused() {
        assertThrows(IllegalStateException.class, () -> Portraits.check(new Portraits.Look("x", "f", 2, "bob", "dark", "beard", false, "oval", 0.4)));
        assertThrows(IllegalStateException.class, () -> Portraits.check(new Portraits.Look("x", "m", 2, "bob", "dark", "none", false, "oval", 0.4)));
        assertThrows(IllegalStateException.class, () -> Portraits.check(new Portraits.Look("x", "m", 2, "bald", "dark", "none", false, "oval", 0.4)));
        assertThrows(IllegalStateException.class, () -> Portraits.check(new Portraits.Look("x", "q", 2, "short", "dark", "none", false, "oval", 0.4)));
        assertThrows(IllegalStateException.class, () -> Portraits.check(new Portraits.Look("x", "m", 9, "short", "dark", "none", false, "oval", 0.4)));
    }

    @Test
    void aNewcomersLookIsStableAndFitsTheirPresentation() {
        for (int i = 0; i < 2000; i++) {
            String id = "new_" + i, p = i % 2 == 0 ? "m" : "f";
            Portraits.Look a = Portraits.generated(id, p, (i % 10) / 10.0), b = Portraits.generated(id, p, (i % 10) / 10.0);
            assertEquals(a, b, "the same person, the same face");
            assertEquals(p, a.presentation());
            if (p.equals("f")) assertFalse(Portraits.MASCULINE_HAIR.contains(a.hair()) && !a.hair().equals("close"), id + " has feminine hair");
        }
    }
}
