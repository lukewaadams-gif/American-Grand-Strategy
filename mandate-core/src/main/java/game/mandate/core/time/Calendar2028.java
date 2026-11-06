package game.mandate.core.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/** Fixed dates of the 2028-2032 arc. Kept in one place so scenarios can shift them later. */
public final class Calendar2028 {
    private Calendar2028() {}

    /** MVP campaign start: the week after the second convention. */
    public static final LocalDate CAMPAIGN_START = LocalDate.of(2028, Month.JULY, 17);
    public static final LocalDate ELECTION_2028 = LocalDate.of(2028, Month.NOVEMBER, 7);
    public static final LocalDate INAUGURATION_2029 = LocalDate.of(2029, Month.JANUARY, 20);
    public static final LocalDate MIDTERMS_2030 = LocalDate.of(2030, Month.NOVEMBER, 5);
    /** Governing hands over to the weekly re-election campaign after the conventions. */
    public static final LocalDate REELECTION_START = LocalDate.of(2032, Month.JULY, 19);
    public static final LocalDate ELECTION_2032 = LocalDate.of(2032, Month.NOVEMBER, 2);

    /** US general elections fall on the Tuesday after the first Monday of November. */
    public static LocalDate electionDay(int year) {
        LocalDate firstMonday = LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        return firstMonday.plusDays(1);
    }
}
