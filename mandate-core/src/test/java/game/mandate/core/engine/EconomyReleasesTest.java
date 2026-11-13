package game.mandate.core.engine;

import game.mandate.core.ai.AutoPilot;
import game.mandate.core.economy.StateEconomy;
import game.mandate.core.news.News;
import game.mandate.core.time.CampaignCalendar;
import game.mandate.core.time.Phase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The economy in the states, and the releases and news it makes in office (presidency milestone 4, step 2). */
class EconomyReleasesTest {

    @Test
    void gdpIsReportedFourTimesAYear() {
        assertEquals(4, CampaignCalendar.gdpReleases(LocalDate.of(2030, 1, 1), LocalDate.of(2031, 1, 1)));
        assertEquals(LocalDate.of(2030, 1, 31), CampaignCalendar.gdpReport(2030, java.time.Month.JANUARY));
    }

    /** A year in office writes the year's releases: twelve jobs and price reports, four GDP reports and the Fed's eight meetings. */
    @Test
    void aYearInOfficeMakesTheEconomysNews() {
        GameEngine g = EngineFixtures.governing(1);
        EngineFixtures.playUntil(g, x -> x.phase() == Phase.GOVERNING);
        LocalDate start = g.date();
        EngineFixtures.playUntil(g, x -> !x.date().isBefore(start.plusWeeks(52)));
        List<News> year = g.news().stream().filter(n -> !n.date().isBefore(start) && n.kind() == News.Kind.ECON).toList();
        long jobs = year.stream().filter(n -> n.head().startsWith("Employers add") || n.head().startsWith("Economy sheds")).count();
        long prices = year.stream().filter(n -> n.head().startsWith("Prices up")).count();
        long gdp = year.stream().filter(n -> n.head().startsWith("The economy grew") || n.head().startsWith("The economy shrank")).count();
        long fed = year.stream().filter(n -> n.head().contains("Fed")).count();
        assertTrue(jobs >= 11 && jobs <= 13, "jobs reports " + jobs);
        assertTrue(prices >= 11 && prices <= 13, "price reports " + prices);
        assertTrue(gdp >= 3 && gdp <= 5, "GDP reports " + gdp);
        assertTrue(fed >= 7 && fed <= 9, "Fed decisions " + fed);
        // routine releases run in the Economy section; the Fed's moves are Washington news
        assertTrue(year.stream().filter(n -> n.head().startsWith("Prices up") && n.weight() < 1.2).allMatch(n -> "Economy".equals(n.section())));
        assertTrue(year.stream().filter(n -> n.head().startsWith("The Fed raises") || n.head().startsWith("The Fed cuts")).allMatch(n -> n.section() == null));
        // and the week's figures are recorded in detail for the briefing book
        IndicatorPoint last = g.indicators().get(g.indicators().size() - 1);
        for (String k : IndicatorPoint.DETAIL) assertTrue(!Double.isNaN(last.detail(k)), k);
        assertTrue(last.detail("debt") > 100 && last.detail("housing") > 0.5 && last.detail("stocks") > 1000);
    }

    @Test
    void theStatesFeelTheirOwnEconomies() {
        GameEngine g = EngineFixtures.governing(1);
        var e = g.state().economy();
        StateEconomy se = g.stateEconomy();
        assertEquals(51, se.states().size());
        assertTrue(se.unemployment(e, "NV") > e.unemployment() + 1, "Nevada runs above the nation");
        assertTrue(se.unemployment(e, "SD") < e.unemployment() - 1, "South Dakota runs below");
        assertTrue(se.gas(e, "CA") > e.gas() + 1, "California's gas is dearer");
        assertTrue(se.industries("MI").contains("manufacturing"));
        Map<String, Double> terms = se.approvalTerms(e);
        assertTrue(terms.get("NV") < 0 && terms.get("SD") > 0, "worse jobs cost the president there: " + terms.get("NV") + " " + terms.get("SD"));
        assertTrue(terms.get("CA") < terms.get("TX"), "dearer gas costs too");
    }

    @Test
    void jobCountsReadAsNumbers() {
        var e = EngineFixtures.governing(1).state().economy();
        String head = game.mandate.core.economy.Releases.jobs(LocalDate.of(2030, 3, 2), LocalDate.of(2030, 3, 6), e).head();
        assertTrue(head.matches("(Employers add [0-9]{1,3}(,[0-9]{3})*,000 jobs in February; unemployment [0-9.]+%|Economy sheds [0-9,]+ jobs in February)"), head);
    }
}
