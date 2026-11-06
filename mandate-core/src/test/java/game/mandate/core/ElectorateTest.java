package game.mandate.core;

import game.mandate.core.electorate.ElectionCalculator;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.StateCatalog;
import game.mandate.core.electorate.UniformSwingModel;
import game.mandate.core.electorate.UsState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectorateTest {
    private final StateCatalog states = StateCatalog.load();

    @Test
    void catalogHas51UnitsAnd538ElectoralVotes() {
        assertEquals(51, states.all().size());
        assertEquals(538, states.totalElectoralVotes());
    }

    @Test
    void senateClassesHaveTheRightSize() {
        assertEquals(34, states.all().stream().filter(s -> s.senateUp2028().isPresent()).count());
        assertEquals(33, states.all().stream().filter(s -> s.senateUp2030().isPresent()).count());
    }

    @Test
    void zeroSwingReproduces2024() {
        ElectionCalculator calc = new ElectionCalculator(states, new UniformSwingModel());
        ElectionResult r = calc.tally(2024, calc.expectedMargins(0, s -> 0));
        // Maine's and Nebraska's district electors are not split in the foundation; they cancel out in 2024.
        assertEquals(226, r.demElectoralVotes());
        assertEquals(312, r.repElectoralVotes());
        assertTrue(r.popularMargin() < 0, "Republicans won the 2024 popular vote");
    }

    @Test
    void competitiveStatesSwingMoreThanSafeOnes() {
        UsState pa = states.get("PA"), wy = states.get("WY");
        assertTrue(pa.elasticity() > wy.elasticity());
    }
}
