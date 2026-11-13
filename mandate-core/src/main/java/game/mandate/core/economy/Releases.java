package game.mandate.core.economy;

import game.mandate.core.data.Fmt;
import game.mandate.core.news.News;

import java.time.LocalDate;

/**
 * The economy's news (Resolute Desk's {@code Economy.shocks}, {@code fomc} and {@code reports}): the business cycle's
 * surprises, the Fed's decisions, and the monthly jobs and price reports and the quarterly GDP report. Routine releases
 * run in the paper's Economy section; a shock, a Fed move or bad numbers are Washington news and can lead the paper.
 */
public final class Releases {
    private Releases() {}

    static final String SECTION = "Economy";
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
            "November", "December"};

    private static News story(LocalDate d, String head, String body, double weight) {
        return new News(d, News.Kind.ECON, head, body, weight, weight >= 1.2 ? null : SECTION);
    }

    /** The surprise of the week, if there was one. */
    public static News shock(LocalDate d, EconomyEngine.Shock s) {
        return switch (s) {
            case RECESSION -> story(d, "Layoffs spread as consumers and businesses pull back", "Economists warn the economy may be heading into recession.", 2.0);
            case FINANCIAL -> story(d, "Markets plunge as a big lender fails", "Credit is tightening and stocks fell sharply.", 2.6);
            case OIL -> story(d, "Oil jumps on supply fears; gas prices climb", null, 1.6);
            case BOOM -> story(d, "Business investment surges; economists raise their growth forecasts", null, 1.0);
        };
    }

    /** The Fed's target as it is announced: a quarter-point range. */
    public static String rate(double r) {
        double lo = Math.floor(r * 4 + 0.001) / 4;
        return Fmt.format("%.2f", lo) + "–" + Fmt.format("%.2f", lo + 0.25) + "%";
    }

    /** A Fed decision. The head keeps the words "The Fed raises" or "The Fed cuts", which the game's notices look for. */
    public static News fed(LocalDate d, EconomyState e, double change) {
        long bp = Math.round(Math.abs(change) * 100);
        if (change > 0) return story(d, "The Fed raises rates to " + rate(e.fedRate),
                "The Federal Reserve lifted its target by " + bp + " basis points, citing inflation of " + Fmt.format("%.1f", e.reportedInflation) + "%.", 1.2);
        if (change < 0) return story(d, "The Fed cuts rates to " + rate(e.fedRate),
                "The Federal Reserve cut its target by " + bp + " basis points as unemployment reached " + Fmt.format("%.1f", e.reportedUnemployment) + "%.", 1.2);
        return story(d, "Fed holds rates at " + rate(e.fedRate), null, 0.4);
    }

    /** The jobs report (released on {@code released}) for the month before it. */
    public static News jobs(LocalDate d, LocalDate released, EconomyState e) {
        String month = MONTHS[released.minusDays(7).getMonthValue() - 1];
        long jobs = Math.round(e.payrolls);
        if (jobs < 0) return story(d, "Economy sheds " + thousands(-jobs) + " jobs in " + month,
                "Unemployment is " + Fmt.format("%.1f", e.reportedUnemployment) + "%.", 1.6);
        return story(d, "Employers add " + thousands(jobs) + " jobs in " + month + "; unemployment " + Fmt.format("%.1f", e.reportedUnemployment) + "%",
                null, jobs > 250 ? 1.4 : 0.9);
    }

    /** The consumer price report, with the prices people notice most. */
    public static News prices(LocalDate d, EconomyState e) {
        return story(d, "Prices up " + Fmt.format("%.1f", e.reportedInflation) + "% over the past year",
                "Food is up " + Fmt.format("%.1f", e.part(Part.FOOD)) + "%, shelter " + Fmt.format("%.1f", e.part(Part.SHELTER)) + "% and medical care "
                        + Fmt.format("%.1f", e.part(Part.MEDICAL)) + "%.", e.reportedInflation > 4 ? 1.6 : 0.7);
    }

    /** The first estimate of last quarter's growth, at an annual rate. */
    public static News gdp(LocalDate d, EconomyState e) {
        double q = e.reportedGrowth;
        return q < 0 ? story(d, "The economy shrank at a " + Fmt.format("%.1f", -q) + "% rate last quarter", null, 2.0)
                : story(d, "The economy grew at a " + Fmt.format("%.1f", q) + "% rate last quarter", null, 0.8);
    }

    /** Thousands of jobs as a count: 85 becomes "85,000". */
    static String thousands(long k) {
        return k >= 1000 ? (k / 1000) + "," + pad(k % 1000) + ",000" : k + ",000";
    }

    private static String pad(long v) { return v < 10 ? "00" + v : v < 100 ? "0" + v : String.valueOf(v); }
}
