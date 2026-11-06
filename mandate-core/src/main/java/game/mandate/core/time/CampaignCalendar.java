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
    public static int releases(LocalDate from, LocalDate to, boolean jobs) {
        int n = 0;
        for (LocalDate m = from.withDayOfMonth(1); m.isBefore(to); m = m.plusMonths(1)) {
            LocalDate r = jobs ? jobsReport(m.getYear(), m.getMonth()) : cpiReport(m.getYear(), m.getMonth());
            if (!r.isBefore(from) && r.isBefore(to)) n++;
        }
        return n;
    }
}
