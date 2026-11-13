package game.mandate.core.time;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The dated milestones of the 2028-2032 arc, loaded from {@code /data/calendar.properties}. */
public record CampaignCalendar(LocalDate campaignStart, LocalDate election2028, int transitionWeeks, LocalDate inauguration,
                               LocalDate midterms, LocalDate reelectionStart, LocalDate election2032,
                               List<LocalDate> debates2028, List<LocalDate> debates2032, List<LocalDate> fomc) {

    public static CampaignCalendar load() {
        Properties p = new Properties();
        try (InputStream in = CampaignCalendar.class.getResourceAsStream("/data/calendar.properties")) {
            if (in == null) throw new IllegalStateException("Missing /data/calendar.properties");
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return new CampaignCalendar(d(p, "campaignStart"), d(p, "election2028"), Integer.parseInt(p.getProperty("transitionWeeksBeforeInauguration").strip()),
                d(p, "inauguration"), d(p, "midterms"), d(p, "reelectionStart"), d(p, "election2032"),
                list(p, "debates2028"), list(p, "debates2032"), list(p, "fomc"));
    }

    private static LocalDate d(Properties p, String k) { return LocalDate.parse(p.getProperty(k).strip()); }

    private static List<LocalDate> list(Properties p, String k) {
        List<LocalDate> l = new ArrayList<>();
        for (String s : p.getProperty(k).split(",")) if (!s.isBlank()) l.add(LocalDate.parse(s.strip()));
        return List.copyOf(l);
    }

    /** The inauguration of a term (1 the first, 2 the second). */
    public LocalDate inauguration(int term) { return inauguration.plusYears(4L * (term - 1)); }

    /** A term's midterm elections. */
    public LocalDate midterms(int term) { return term <= 1 ? midterms : electionDay(inauguration.getYear() + 1 + 4 * (term - 1)); }

    /** The presidential election at the end of a term (2032 for the first, 2036 for the second). */
    public LocalDate presidentialElection(int term) { return term <= 1 ? election2032 : electionDay(inauguration.getYear() + 3 + 4 * (term - 1)); }

    /** Noon on January 20 when the term ends. */
    public LocalDate termEnd(int term) { return inauguration(term + 1); }

    /** FOMC meetings on or after {@code from} and before {@code to}. */
    public int fomcMeetings(LocalDate from, LocalDate to) {
        return (int) fomc.stream().filter(d -> !d.isBefore(from) && d.isBefore(to)).count();
    }

    /** US general elections fall on the Tuesday after the first Monday of November. */
    public static LocalDate electionDay(int year) {
        return LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)).plusDays(1);
    }

    /** The monthly jobs report: the first Friday of the month. */
    public static LocalDate jobsReport(int year, Month month) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.FRIDAY));
    }

    /** The monthly consumer-price report: the second Wednesday of the month. */
    public static LocalDate cpiReport(int year, Month month) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.WEDNESDAY));
    }

    /** Number of monthly releases (jobs or CPI) dated in [from, to). */
    /** The first estimate of last quarter's GDP: the last Thursday of January, April, July and October. */
    public static LocalDate gdpReport(int year, Month month) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.THURSDAY));
    }

    /** How many GDP reports fall in [from, to). */
    public static int gdpReleases(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate m = from.withDayOfMonth(1); m.isBefore(to); m = m.plusMonths(1)) {
            if (m.getMonthValue() % 3 != 1) continue;
            LocalDate r = gdpReport(m.getYear(), m.getMonth());
            if (!r.isBefore(from) && r.isBefore(to)) n++;
        }
        return n;
    }

    public static int releases(LocalDate from, LocalDate to, boolean jobs) {
        int n = 0;
        for (LocalDate m = from.withDayOfMonth(1); m.isBefore(to); m = m.plusMonths(1)) {
            LocalDate r = jobs ? jobsReport(m.getYear(), m.getMonth()) : cpiReport(m.getYear(), m.getMonth());
            if (!r.isBefore(from) && r.isBefore(to)) n++;
        }
        return n;
    }
}
