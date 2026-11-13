package game.mandate.core;

import game.mandate.core.opening.OpeningSetup;
import game.mandate.core.opening.Report;
import game.mandate.core.opening.Strategy;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.state.Party;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design-level guard rails measured with scripted strategy probes. These test properties of the rules
 * (divergence, non-dominance, strategy explaining outcomes), not whether the game is fun: only players
 * can tell us that (GAMEPLAY_DESIGN.md, section 8).
 */
class OpeningStrategyTest {
    static final String[] METRICS = {"approval", "policy", "economy", "security", "unity", "goodwill"};
    static final int SEEDS = 150;
    static final List<Strategy> PHILOSOPHIES = List.of(Strategy.PARTISAN, Strategy.DEALMAKER, Strategy.HAWK, Strategy.TECHNOCRAT, Strategy.DELEGATOR);

    static Map<Strategy, double[][]> unified, divided;

    @BeforeAll
    static void play() {
        unified = run(true);
        divided = run(false);
    }

    static Map<Strategy, double[][]> run(boolean u) {
        Map<Strategy, double[][]> m = new EnumMap<>(Strategy.class);
        for (Strategy s : Strategy.values()) {
            double[][] v = new double[SEEDS][];
            for (int k = 0; k < SEEDS; k++) {
                Report r = Strategy.play(new OpeningSetup(500 + k, k % 2 == 0 ? Party.DEMOCRAT : Party.REPUBLICAN, u), s).report();
                v[k] = new double[]{r.approval(), r.policyDelivered(), r.economyIndex(), r.securityIndex(), r.partyUnity(), r.goodwill(),
                        r.billStage() == BillStage.LAW ? 1 : 0};
            }
            m.put(s, v);
        }
        return m;
    }

    static double mean(double[][] v, int metric) {
        double s = 0;
        for (double[] row : v) s += row[metric];
        return s / v.length;
    }

    /** Principle 2 and 7: no philosophy beats another on every measure. */
    @Test
    void noStrategyDominatesAnother() {
        for (Map<Strategy, double[][]> cond : List.of(unified, divided)) {
            for (Strategy a : PHILOSOPHIES) for (Strategy b : PHILOSOPHIES) {
                if (a == b) continue;
                boolean allGe = true, oneGt = false;
                for (int m = 0; m < METRICS.length; m++) {
                    double ma = mean(cond.get(a), m), mb = mean(cond.get(b), m);
                    if (ma < mb) allGe = false;
                    if (ma > mb) oneGt = true;
                }
                assertTrue(!(allGe && oneGt), a + " dominates " + b + (cond == unified ? " (unified)" : " (divided)"));
            }
        }
    }

    /** Principle 7: each hands-on philosophy leads on something, so each is a reason to play differently. */
    @Test
    void everyHandsOnPhilosophyLeadsSomewhere() {
        for (Strategy s : List.of(Strategy.PARTISAN, Strategy.DEALMAKER, Strategy.HAWK, Strategy.TECHNOCRAT)) {
            List<String> leads = new ArrayList<>();
            for (Map<Strategy, double[][]> cond : List.of(unified, divided)) {
                for (int m = 0; m < METRICS.length; m++) {
                    boolean best = true;
                    for (Strategy o : PHILOSOPHIES) if (o != s && mean(cond.get(o), m) >= mean(cond.get(s), m)) best = false;
                    if (best) leads.add(METRICS[m]);
                }
            }
            assertTrue(!leads.isEmpty(), s + " leads on nothing");
        }
    }

    /** Principle 4: results are uncertain, but strategy explains a real share of them. */
    @Test
    void outcomesAreUncertainButNotArbitrary() {
        for (Map<Strategy, double[][]> cond : List.of(unified, divided)) {
            int strong = 0;
            for (int m = 0; m < METRICS.length; m++) {
                double grand = 0;
                for (Strategy s : PHILOSOPHIES) grand += mean(cond.get(s), m);
                grand /= PHILOSOPHIES.size();
                double between = 0, within = 0;
                for (Strategy s : PHILOSOPHIES) {
                    double mu = mean(cond.get(s), m);
                    between += SEEDS * (mu - grand) * (mu - grand);
                    for (double[] row : cond.get(s)) within += (row[m] - mu) * (row[m] - mu);
                }
                double eta2 = between / (between + within);
                assertTrue(within > 0, METRICS[m] + " has no uncertainty at all");
                // Resolute Desk's approval moves 15% a week toward its target and carries a slow random mood, so fourteen
                // weeks of strategy explain less of day-100 approval than the prototype's direct model did. The structural
                // economy (docs/ECONOMY_MODEL.md) carries real market noise (oil moves gas and energy prices week to week)
                // and prices and jobs respond to policy over quarters, so in a hundred days strategy shows mainly in the
                // bond market (it explains 40-60% of the 10-year) and less of the whole economy index
                double floor = METRICS[m].equals("approval") || METRICS[m].equals("economy") ? 0.1 : 0.2;
                assertTrue(eta2 >= floor, METRICS[m] + ": strategy explains only " + eta2 + " of the variance");
                if (eta2 >= 0.5) strong++;
            }
            // day-100 approval is mostly the economy, the honeymoon and the public mood under Resolute Desk's model
            assertTrue(strong >= 2, "strategy should dominate the variance of at least two measures, got " + strong);
        }
    }

    /** Different situations reward different paths: the partisan route needs a unified Congress. */
    @Test
    void theRightPathDependsOnTheCongressYouHave() {
        double partisanUnified = mean(unified.get(Strategy.PARTISAN), 6), partisanDivided = mean(divided.get(Strategy.PARTISAN), 6);
        double dealDivided = mean(divided.get(Strategy.DEALMAKER), 6);
        assertTrue(partisanUnified - partisanDivided > 0.5, "partisan law rate " + partisanUnified + " vs " + partisanDivided);
        assertTrue(dealDivided > partisanDivided + 0.5, "under divided government the deal path should pass more laws");
    }

    /** Principle 10: casual play is viable but not the best play in hard conditions. */
    @Test
    void delegationIsViableButExpertPlayPaysOffWhenItIsHard() {
        double idle = mean(divided.get(Strategy.IDLE), 1), delegated = mean(divided.get(Strategy.DELEGATOR), 1);
        assertTrue(delegated > idle, "delegation should beat doing nothing");
        double bestHandsOn = 0;
        for (Strategy s : List.of(Strategy.DEALMAKER, Strategy.STRATEGIST)) bestHandsOn = Math.max(bestHandsOn, mean(divided.get(s), 1));
        assertTrue(bestHandsOn > delegated + 0.3, "an attentive player should deliver more policy than the staff in a divided Congress");
        assertTrue(mean(unified.get(Strategy.DELEGATOR), 0) > mean(unified.get(Strategy.IDLE), 0), "delegation should beat idling on approval");
    }

    /** Doing nothing is never a strategy: idling delivers no policy and trails on approval. */
    @Test
    void idlingIsNotAStrategy() {
        for (Map<Strategy, double[][]> cond : List.of(unified, divided)) {
            int worse = 0;
            for (Strategy s : PHILOSOPHIES) {
                assertTrue(mean(cond.get(s), 1) >= mean(cond.get(Strategy.IDLE), 1));
                if (mean(cond.get(s), 0) > mean(cond.get(Strategy.IDLE), 0)) worse++;
            }
            assertTrue(worse >= 3, "idle approval beats too many strategies");
        }
    }
}
