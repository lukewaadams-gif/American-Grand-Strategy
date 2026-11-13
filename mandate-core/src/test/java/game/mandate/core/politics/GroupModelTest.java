package game.mandate.core.politics;

import game.mandate.core.state.Party;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The voter groups: the cells lean as they voted, the groups break the national number down, and effects aimed at a group move it. */
class GroupModelTest {
    static final GroupModel M = new GroupModel();

    static double share(double[] g, int i) { return g[i]; }

    @Test
    void groupsLeanAsTheyVoted() {
        double[] d = M.approval(48, Party.DEMOCRAT, 0.85, new double[GroupModel.NG], Map.of());
        double[] r = M.approval(48, Party.REPUBLICAN, 0.85, new double[GroupModel.NG], Map.of());
        assertTrue(d[10] > 70, "Black voters approve of a Democratic president: " + d[10]);
        assertTrue(r[10] < 30, "and not of a Republican: " + r[10]);
        assertTrue(r[6] > 65, "white evangelicals approve of a Republican: " + r[6]);
        assertTrue(d[22] < d[23], "rural voters below urban ones for a Democrat");
        assertTrue(r[26] > 55, "gun owners approve of a Republican: " + r[26]);
        // women and men together straddle the national number
        assertTrue(Math.min(d[4], d[5]) < 48 && Math.max(d[4], d[5]) > 48);
    }

    @Test
    void anEffectAimedAtAGroupMovesItAgainstTheRest() {
        double[] adj = new double[GroupModel.NG];
        double[] before = M.approval(45, Party.DEMOCRAT, 0.85, adj, Map.of());
        adj[GroupModel.index("blk")] = 0.2;
        double[] after = M.approval(45, Party.DEMOCRAT, 0.85, adj, Map.of());
        assertTrue(after[10] > before[10] + 1, "Black voters warm: " + before[10] + " -> " + after[10]);
        assertTrue(after[9] < before[9], "and the rest give a little back, the headline being fixed: " + before[9] + " -> " + after[9]);
        // a group that cuts across the cells moves by its members' share of each (Resolute Desk's partial membership)
        double[] adj2 = new double[GroupModel.NG];
        adj2[GroupModel.index("union")] = 0.2;
        double[] union = M.approval(45, Party.DEMOCRAT, 0.85, adj2, Map.of());
        assertTrue(union[24] > before[24], "union households warm a little: " + before[24] + " -> " + union[24]);
        assertEquals(24, GroupModel.index("union"));
    }
}
