package game.mandate.core.engine;

import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static game.mandate.core.engine.EngineFixtures.playUntil;
import static game.mandate.core.engine.EngineFixtures.setup;

/**
 * How much the 2032 race follows the president's record (docs/MODES.md, "The 2032 campaign"): approval and the economy
 * at the campaign's start against the incumbent's popular margin. Off by default: -Dmandate.recordBalance=N games.
 */
class RecordBalanceRun {
    @Test
    void run() {
        int n = Integer.getInteger("mandate.recordBalance", 0);
        Assumptions.assumeTrue(n > 0, "off; -Dmandate.recordBalance=N runs it");
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        int k = 0;
        java.util.List<double[]> rows = new java.util.ArrayList<>();
        for (long seed = 1; k < n && seed < n * 4L; seed++) {
            GameEngine g = playUntil(new GameEngine(setup(seed)), x -> x.phase() == Phase.REELECTION_CAMPAIGN_2032 || x.over());
            if (g.phase() != Phase.REELECTION_CAMPAIGN_2032) continue;
            double approval = g.approval(), growth = g.state().economy.growth(), unemployment = g.state().economy.unemployment();
            Party mine = g.player().party();
            playUntil(g, x -> x.result2032().isPresent() || x.over());
            if (g.result2032().isEmpty()) continue;
            double margin = g.result2032().get().marginFor(mine);
            System.out.printf("seed %d approval %.1f growth %.1f unemployment %.1f -> margin %+.1f %s%n", seed, approval, growth, unemployment, margin,
                    margin > 0 ? "re-elected" : "defeated");
            rows.add(new double[]{1, approval, growth, unemployment, margin});
            sx += approval; sy += margin; sxx += approval * approval; sxy += approval * margin; syy += margin * margin;
            k++;
        }
        double slope = (k * sxy - sx * sy) / (k * sxx - sx * sx), r = (k * sxy - sx * sy) / Math.sqrt((k * sxx - sx * sx) * (k * syy - sy * sy));
        System.out.printf("%d races: margin = %.2f x approval %+.1f; r = %.2f, r2 = %.2f%n", k, slope, (sy - slope * sx) / k, r, r * r);
        // the record together: margin ~ approval + growth + unemployment (least squares)
        double[][] a = new double[4][5];
        for (double[] row : rows) for (int i = 0; i < 4; i++) { for (int j = 0; j < 4; j++) a[i][j] += row[i] * row[j]; a[i][4] += row[i] * row[4]; }
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) { double f = a[j][i] / a[i][i]; for (int c = i; c < 5; c++) a[j][c] -= f * a[i][c]; }
        double[] b = new double[4];
        for (int i = 3; i >= 0; i--) { double v = a[i][4]; for (int j = i + 1; j < 4; j++) v -= a[i][j] * b[j]; b[i] = v / a[i][i]; }
        double ssr = 0, sst = 0, mean = sy / k;
        for (double[] row : rows) { double f = b[0] + b[1] * row[1] + b[2] * row[2] + b[3] * row[3]; ssr += (row[4] - f) * (row[4] - f); sst += (row[4] - mean) * (row[4] - mean); }
        System.out.printf("the record together: r2 = %.2f; what the campaign and the night add: +/-%.1f points (sd)%n", 1 - ssr / sst, Math.sqrt(ssr / k));
    }
}
