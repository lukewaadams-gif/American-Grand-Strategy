package game.mandate.core.economy;

import game.mandate.core.data.Json;
import game.mandate.core.data.Tuning;
import game.mandate.core.rng.DeterministicRandomManager;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calibration targets of docs/ECONOMY_MODEL.md ("Numbers the model must reproduce"), each run as the budget office
 * scores a law: the same economy twice, with the change and without it, noise and random shocks off, the Fed meeting
 * eight times a year on monthly data.
 */
class EconomyModelTest {
    static final EconomyParams LOUD = EconomyParams.from(Tuning.defaults());
    static final EconomyParams QUIET = LOUD.quiet();

    /** A run of the economy: the state, its budget and the weeks it has had. */
    static final class Run {
        final EconomyEngine engine;
        EconomyState s;
        BudgetState b;
        final DeterministicRandomManager rng;
        int weeks;

        Run(EconomyParams p, long seed) {
            engine = new EconomyEngine(p);
            s = engine.newEconomy();
            b = engine.newBudget();
            rng = new DeterministicRandomManager(seed);
        }

        Run copy() {
            Run r = new Run(engine.params(), 1);
            r.s = EconomyState.fromJson(Json.object(Json.parse(Json.write(s.toJson()))));
            r.b = BudgetState.fromJson(Json.object(Json.parse(Json.write(b.toJson()))));
            r.rng.restore(rng.snapshot());
            r.weeks = weeks;
            return r;
        }

        void week() {
            engine.step(s, b, 1.0 / EconomyEngine.WEEKS_PER_MONTH, MarketContext.calm(), rng);
            weeks++;
            if (weeks % 4 == 0) { engine.jobsReport(s, rng); engine.inflationReport(s, rng); }
            if ((weeks * 8) / 52 != ((weeks - 1) * 8) / 52) engine.fedMeeting(s, b);
        }

        void weeks(int n) { for (int i = 0; i < n; i++) week(); }

        double logY() { return s.logY[s.ring]; }
    }

    interface Probe { double read(Run r); }

    /** The largest difference (with minus without) of a figure over some weeks, and the difference at the end. */
    static double[] compare(Run base, Consumer<Run> change, int weeks, Probe probe) {
        Run with = base.copy(), without = base.copy();
        change.accept(with);
        double peak = 0, trough = 0, last = 0;
        for (int w = 0; w < weeks; w++) {
            with.week(); without.week();
            last = probe.read(with) - probe.read(without);
            peak = Math.max(peak, last);
            trough = Math.min(trough, last);
        }
        return new double[]{peak, trough, last};
    }

    static void within(String what, double v, double lo, double hi) {
        System.out.printf("%-58s %8.3f  [%s, %s]%n", what, v, lo, hi);
        assertTrue(v >= lo && v <= hi, what + " = " + v + ", expected " + lo + " to " + hi);
    }

    @Test
    void noNewPolicyFollowsTheBudgetOfficesProjection() {
        Run r = new Run(QUIET, 1);
        double growth = 0, uLo = 99, uHi = 0, deficit0 = r.b.deficit();
        for (int y = 0; y < 4; y++) {
            for (int w = 0; w < 52; w++) {
                r.week();
                uLo = Math.min(uLo, r.s.unemployment()); uHi = Math.max(uHi, r.s.unemployment());
            }
            growth += r.s.growth() / 4;
            System.out.printf("year %d: growth %.2f potential %.2f u %.2f cpi %.2f core %.2f fed %.2f 10y %.2f deficit %.2f debt %.1f interest %.2f sentiment %.1f housing %.2f stocks %.0f%n",
                    y + 1, r.s.growth(), r.s.potentialGrowth(), r.s.unemployment(), r.s.inflation(), r.s.core(), r.s.fedRate(), r.s.tenYear(),
                    r.b.deficit(), r.b.debt(), r.b.interest(), r.s.sentiment(), r.s.housing(), r.s.stocks());
        }
        within("baseline: growth, average of 2029-2032", growth, 1.7, 2.0);
        within("baseline: lowest unemployment", uLo, 4.2, 4.7);
        within("baseline: highest unemployment", uHi, 4.2, 4.7);
        within("baseline: inflation in 2033", r.s.inflation(), 2.2, 2.6);
        within("baseline: the 10-year in 2033", r.s.tenYear(), 4.0, 4.6);
        within("baseline: deficit in 2033", r.b.deficit(), 6.0, 7.5);
        within("baseline: deficit rises", r.b.deficit() - deficit0, 0.1, 2.0);
        within("baseline: debt in 2033", r.b.debt(), 106, 112);
        within("baseline: potential growth in 2033", r.s.potentialGrowth(), 1.6, 2.0);
    }

    /** An economy in a slump, with the Fed at its floor: demand held down until the gap is about -4. */
    static Run slump() {
        EconomyParams p = LOUD.quiet();
        p.demandDecay = 1;
        Run r = new Run(p, 1);
        r.s.demand = -6.5;
        r.weeks(260);
        return r;
    }

    @Test
    void stimulusInASlumpWithTheFedAtItsFloor() {
        Run r = slump();
        System.out.printf("slump: gap %.2f u %.2f fed %.2f cpi %.2f%n", r.s.outputGap(), r.s.unemployment(), r.s.fedRate(), r.s.inflation());
        within("slump: the gap before", r.s.outputGap(), -5, -3);
        within("slump: the Fed before", r.s.fedRate(), 0, 0.5);
        double[] d = compare(r, x -> x.engine.addProgram(x.b, new Program("purchases", FiscalClass.PURCHASES, 0.87, new double[]{1}, x.s.week, x.s.week + 52, 1)),
                156, x -> x.logY() * 100);
        within("slump: $300bn of purchases, peak output %", d[0], 1.0, 1.6);
    }

    @Test
    void stimulusAtFullEmploymentIsCrowdedOut() {
        Run r = new Run(QUIET, 1);
        r.weeks(260);
        System.out.printf("full employment: gap %.2f u %.2f fed %.2f%n", r.s.outputGap(), r.s.unemployment(), r.s.fedRate());
        Consumer<Run> stimulus = x -> x.engine.addProgram(x.b, new Program("purchases", FiscalClass.PURCHASES, 0.87, new double[]{1}, x.s.week, x.s.week + 52, 1));
        double[] y = compare(r, stimulus, 156, x -> x.logY() * 100);
        double[] fed = compare(r, stimulus, 156, x -> x.s.fedRate());
        double[] gap = compare(r, stimulus, 156, x -> x.s.outputGap());
        double[] mult = compare(r, stimulus, 30, x -> x.s.fiscalPush());
        System.out.printf("full employment: the Fed's peak rise %.2f, gap peak %.2f, fiscal push at week 30 %.2f%n", fed[0], gap[0], mult[2]);
        within("full employment: $300bn of purchases, peak output %", y[0], 0.3, 0.6);
        double[] at2 = compare(r, stimulus, 104, x -> x.logY() * 100);
        within("full employment: output % two years on", at2[2], -0.3, 0.15);
        double[] ten = compare(r, stimulus, 104, x -> x.s.tenYear());
        within("full employment: the 10-year's peak rise", ten[0], 0.03, 0.5);
    }

    @Test
    void aPermanentDeficitRaisesRatesAndCostsOutput() {
        Run r = new Run(QUIET, 1);
        r.weeks(52);
        Consumer<Run> deficit = x -> x.engine.addProgram(x.b, new Program("deficit", FiscalClass.MIXED, 1.0, new double[]{1}, x.s.week, -1, 1));
        double[] ten = compare(r, deficit, 520, x -> x.s.tenYear());
        within("deficit +1%: the 10-year after a decade", ten[2], 0.2, 0.3);
        double[] pot = compare(r, deficit, 780, x -> x.s.potential * 100);
        within("deficit +1%: potential output after fifteen years, %", pot[2], -0.5, -0.2);
        double[] cap = compare(r, deficit, 520, x -> x.s.capital * 100);
        within("deficit +1%: capital after a decade, %", cap[2], -3, -0.2);
    }

    @Test
    void aHigherCorporateRateCostsOutputThroughInvestment() {
        Run r = new Run(QUIET, 1);
        r.weeks(52);
        Consumer<Run> corporate = x -> {
            x.s.userCost += 6;
            x.engine.addProgram(x.b, new Program("corporate", FiscalClass.CORPORATE, -0.29, new double[]{1}, x.s.week, -1, 1));
        };
        double[] pot = compare(r, corporate, 780, x -> x.s.potential * 100);
        within("corporate 21% to 28%: potential output after fifteen years, %", pot[2], -0.6, -0.2);
    }

    @Test
    void tariffsRaisePricesAndCostOutput() {
        Run r = new Run(QUIET, 1);
        r.weeks(104);
        Consumer<Run> tariff = x -> x.s.tariff += 10;
        double[] prices = compare(r, tariff, 52, x -> Math.log(x.s.priceLevel()) * 100);
        within("tariff +10: consumer prices after a year, %", prices[2], 0.8, 1.5);
        double[] y = compare(r, tariff, 156, x -> x.logY() * 100);
        within("tariff +10: output, lowest %", y[1], -0.6, -0.3);
        double[] farm = compare(r, tariff, 104, x -> x.s.farmIncome());
        within("tariff +10: farm income", farm[2], -12, -2);
    }

    @Test
    void oilFeedsGasAndInflation() {
        Run r = new Run(QUIET, 1);
        r.weeks(104);
        Consumer<Run> oil = x -> { x.s.oilBase += 10; x.s.oilDev = 0; };
        double[] gas = compare(r, oil, 26, x -> x.s.gas());
        within("oil +$10: gas, $", gas[2], 0.20, 0.28);
        double[] pi = compare(r, oil, 52, x -> x.s.inflation());
        within("oil +$10: inflation, peak points", pi[0], 0.2, 0.3);
    }

    @Test
    void mortgageRatesMoveHousingStarts() {
        Run r = new Run(QUIET, 1);
        r.weeks(104);
        Consumer<Run> mortgage = x -> x.s.tenYear += 1;
        Run with = r.copy(), without = r.copy();
        mortgage.accept(with);
        double lowest = 0;
        for (int w = 0; w < 52; w++) {
            with.week(); without.week();
            with.s.tenYear = without.s.tenYear + 1;   // held a point higher for the year
            lowest = Math.min(lowest, with.s.housing() / without.s.housing() - 1);
        }
        within("mortgages +1 point: housing starts within a year, %", lowest * 100, -12, -8);
    }

    @Test
    void immigrationGrowsTheLabourForceAndOutput() {
        Run r = new Run(QUIET, 1);
        r.weeks(52);
        Consumer<Run> more = x -> x.s.immigration += 1;
        double[] lab = compare(r, more, 52, x -> x.s.population * 100);
        within("immigration +1m: labour force after a year, %", lab[2], 0.5, 0.7);
        double[] pot = compare(r, more, 260, x -> x.s.potential * 100);
        within("immigration +1m: potential output after five years, %", pot[2], 2.0, 3.3);
    }

    @Test
    void recessionsHurtAndTheFedCuts() {
        EconomyParams p = LOUD.quiet();
        Run r = new Run(p, 1);
        r.weeks(104);
        double u0 = r.s.unemployment(), fed0 = r.s.fedRate();
        r.s.demand += p.recessionDemand;
        r.s.pushes.add(new EconomyState.Push(p.recessionPush / p.pushWeeks, -0.3, p.pushWeeks));
        double uPeak = 0, fedLow = 99;
        for (int w = 0; w < 156; w++) { r.week(); uPeak = Math.max(uPeak, r.s.unemployment()); fedLow = Math.min(fedLow, r.s.fedRate()); }
        within("recession: unemployment rise", uPeak - u0, 2, 4);
        within("recession: the Fed's cuts", fed0 - fedLow, 2, 4);
    }

    @Test
    void recessionsComeAboutOnceInSixYears() {
        int recessions = 0, years = 600;
        Run r = new Run(LOUD, 7);
        for (int w = 0; w < years * 52; w++) {
            int calm = r.s.calm;
            r.week();
            if (r.s.calm == LOUD.calmRecession && calm != LOUD.calmRecession) recessions++;
        }
        within("years per recession", years / (double) recessions, 4.5, 8);
    }

    @Test
    void theEconomyStaysInBoundsWithNoiseAndShocks() {
        for (long seed = 1; seed <= 6; seed++) {
            Run r = new Run(LOUD, seed);
            for (int w = 0; w < 52 * 8; w++) {
                r.week();
                assertTrue(r.s.unemployment() > 2 && r.s.unemployment() < 14, "u " + r.s.unemployment());
                assertTrue(r.s.inflation() > -2 && r.s.inflation() < 9, "pi " + r.s.inflation());
                assertTrue(r.s.tenYear() > 0.5 && r.s.tenYear() < 9, "10y " + r.s.tenYear());
                assertTrue(r.s.stocks() > 1500 && r.s.stocks() < 40000, "stocks " + r.s.stocks());
            }
        }
    }

    /** A game saved in office before the structural model loads, keeps its figures, and plays on. */
    @Test
    void oldSavesAreUpgraded() throws Exception {
        String text;
        try (var in = EconomyModelTest.class.getResourceAsStream("/saves/governing-before-economy-model.json")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        java.util.Map<String, Object> old = Json.object(Json.object(Json.object(Json.parse(text)).get("world")).get("economy"));
        game.mandate.core.engine.GameEngine g = game.mandate.core.engine.SaveManager.load(text);
        EconomyState s = g.state().economy();
        assertTrue(!s.legacy, "upgraded on load");
        within("old save: unemployment kept", s.unemployment() - Json.num(old.get("unemployment")), -1e-9, 1e-9);
        within("old save: inflation kept", s.inflation() - Json.num(old.get("inflation")), -0.01, 0.01);
        within("old save: the Fed kept", s.fedRate() - Json.num(old.get("fedRate")), -1e-9, 1e-9);
        for (int i = 0; i < 30 && !g.over(); i++) game.mandate.core.ai.AutoPilot.playTurn(g);
        assertTrue(g.state().economy().unemployment() > 2 && g.state().economy().unemployment() < 9);
        String again = game.mandate.core.engine.SaveManager.save(g);
        assertTrue(Json.write(game.mandate.core.engine.SaveManager.load(again).state().economy().toJson()).equals(Json.write(g.state().economy().toJson())));
    }

    @Test
    void savesRoundTripExactly() {
        Run r = new Run(LOUD, 3);
        r.engine.addProgram(r.b, new Program("p", FiscalClass.TRANSFERS, 0.4, new double[]{0.5, 1}, 10, -1, 1.2));
        r.weeks(70);
        Run a = r.copy(), b = r.copy();
        a.weeks(30); b.weeks(30);
        assertTrue(Json.write(a.s.toJson()).equals(Json.write(b.s.toJson())));
        assertTrue(Json.write(a.b.toJson()).equals(Json.write(b.b.toJson())));
    }
}
