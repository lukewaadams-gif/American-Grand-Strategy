package game.mandate.core.economy;

import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

/**
 * The single authority for macroeconomic calculations: the structural model of docs/ECONOMY_MODEL.md, stepped once a
 * week. Supply is built from productivity, capital and labour; demand moves the output gap toward a target set by
 * interest rates, fiscal policy, trade, wealth and mood; Okun's law turns the gap into jobs; prices are built from six
 * parts, each with its own drivers; a Taylor-rule Fed moves at its real meetings; the 10-year yield carries a premium
 * that rises with the debt; the budget is kept as CBO keeps it. Resolute Desk's markets, sentiment, sectors and shocks
 * sit on top.
 *
 * <p>{@link #step} takes months, as before, and runs that many weeks. Every random draw comes from a named stream, so a
 * game replays exactly and the browser and Java builds agree.
 */
public final class EconomyEngine {
    public static final double WEEKS_PER_MONTH = 4.345;
    private static final int RING = EconomyState.RING;

    /** The business cycle's surprises. */
    public enum Shock { RECESSION, FINANCIAL, OIL, BOOM }

    private final EconomyParams p;

    public EconomyEngine(EconomyParams p) { this.p = p; }

    public EconomyParams params() { return p; }

    // =========================================================================================
    //  The starting economy
    // =========================================================================================

    public EconomyState newEconomy() {
        EconomyState s = new EconomyState();
        init(s, p.startGap, p.startUnemployment, 0, p.startFedRate, p.startCredibility, p.startOil, p.startTenYear);
        return s;
    }

    public BudgetState newBudget() {
        BudgetState b = new BudgetState();
        b.debt = p.startDebt;
        b.averageRate = p.averageRate;
        budget(b, p.startGap, p.tariff);
        return b;
    }

    /**
     * Sets up an economy on a balanced path: capital growing with output, the last year's output and prices as the
     * starting figures say they grew. {@code inflationShift} moves every price part (an old save's inflation).
     */
    private void init(EconomyState s, double gap, double u, double inflationShift, double fed, double credibility, double oil, double ten) {
        s.week = 0;
        s.participation = p.participation;
        s.nairu = p.naturalUnemployment;
        s.immigration = p.immigration;
        s.aiInvest = 100;
        double gL = labourGrowth(s), gY = p.tfpGrowth / (1 - p.capitalShare) + gL;   // balanced growth, % a year
        double ky = 3.0;
        s.investBase = ky * (p.depreciation + gY / 100);
        s.potential = (p.capitalShare * Math.log(ky)) / (1 - p.capitalShare);
        s.capital = Math.log(ky) + s.potential;
        s.potentialGrowth = gY;
        s.outputGap = gap;
        s.gapPrev = gap;
        s.ring = RING - 1;
        double y = s.potential + gap / 100;
        for (int i = 0; i < RING; i++) {
            int back = RING - 1 - i;
            s.logY[i] = y - back * gY / 5200;
            s.gaps[i] = gap;
        }
        s.growth = gY;
        s.reportedGrowth = gY;
        s.unemployment = u;
        s.uAtLastJobs = u;
        s.expected = p.startExpected + inflationShift;
        s.credibility = credibility;
        s.fedRate = fed;
        s.tenYear = ten;
        s.realRateLag = fed - (s.expected - p.cpiWedge);
        s.real10Neutral = ten - s.expected;
        s.tariff = p.tariff;
        s.tariffAbsorbed = p.tariff - 1.5;   // the last of the 2025-28 tariffs still reaching shelves
        s.oilBase = p.oilBase;
        s.oil = oil;
        s.oilDev = oil - p.oilBase;
        s.priceLevel = p.priceLevel;
        s.gas = p.gas(oil);
        s.gasLog0 = Math.log(s.gas);
        for (Part part : Part.values()) {
            int i = part.ordinal();
            double start = p.startPart[i] + inflationShift;
            s.trend[i] = s.expected + p.wedge[i];
            for (int k = 0; k < RING; k++) s.prices[i][k] = -(RING - 1 - k) * start / 5200;
            s.yoy[i] = start;
        }
        headline(s);
        s.stocks = p.startStocks;
        s.stocks0 = p.startStocks;
        s.dollar = 100;
        s.housing = p.housing;
        s.housingSmooth = p.housing;
        s.manufacturing = 50;
        s.farm = 100;
        s.energyIndex = 100;
        s.tourism = 100;
        s.sentiment = sentimentOf(s);
        s.sentiment0 = s.sentiment;
        s.reportedUnemployment = u;
        s.reportedInflation = Math.round(s.inflation * 10) / 10.0;
        s.payrolls = p.labourForce * gL / 1200;
        s.startMisery = s.misery();
    }

    /**
     * A save from before the structural model: the economy is rebuilt around the figures it had (the gap, jobs,
     * inflation, the Fed, oil, the 10-year), and the old policy deficit becomes a program the economy has adjusted to.
     */
    public void upgrade(EconomyState s, BudgetState b) {
        if (s.legacy) {
            EconomyState fresh = new EconomyState();
            init(fresh, s.outputGap, s.unemployment, 0, s.fedRate, s.credibility, s.oil, s.tenYear);
            double shift = s.inflation - fresh.inflation;
            init(fresh, s.outputGap, s.unemployment, shift, s.fedRate, s.credibility, s.oil, s.tenYear);
            fresh.reportedUnemployment = s.reportedUnemployment;
            fresh.reportedInflation = s.reportedInflation;
            fresh.sprRelief = s.sprRelief;
            fresh.fedPressured = s.fedPressured;
            fresh.startMisery = s.startMisery;
            copyInto(fresh, s);
        }
        if (b.legacy) {
            b.legacy = false;
            b.averageRate = p.averageRate;
            if (Math.abs(b.legacyPolicyDeficit) > 1e-9) {
                Program old = new Program("earlier-laws", FiscalClass.MIXED, b.legacyPolicyDeficit, new double[]{1}, s.week, -1, 1);
                old.adapted = b.legacyPolicyDeficit;
                b.programs.add(old);
            }
            b.week = s.week;
            budget(b, s.outputGap, s.tariff);
        }
    }

    private static void copyInto(EconomyState from, EconomyState to) {
        java.util.Map<String, Object> json = from.toJson();
        EconomyState x = EconomyState.fromJson(json);
        to.legacy = false;
        to.week = x.week; to.tfp = x.tfp; to.capital = x.capital; to.population = x.population; to.participation = x.participation;
        to.nairu = x.nairu; to.structural = x.structural; to.immigration = x.immigration; to.tfpPolicy = x.tfpPolicy; to.userCost = x.userCost;
        to.investBase = x.investBase; to.real10Neutral = x.real10Neutral; to.potential = x.potential; to.potentialGrowth = x.potentialGrowth;
        to.outputGap = x.outputGap; to.growth = x.growth; to.realRateLag = x.realRateLag; to.fiscal = x.fiscal; to.ring = x.ring;
        System.arraycopy(x.logY, 0, to.logY, 0, RING); System.arraycopy(x.gaps, 0, to.gaps, 0, RING);
        System.arraycopy(x.tariffPrices, 0, to.tariffPrices, 0, RING);
        to.unemployment = x.unemployment; to.payrolls = x.payrolls; to.uAtLastJobs = x.uAtLastJobs;
        for (int i = 0; i < Part.COUNT; i++) {
            to.trend[i] = x.trend[i]; to.yoy[i] = x.yoy[i]; to.partPolicy[i] = x.partPolicy[i];
            System.arraycopy(x.prices[i], 0, to.prices[i], 0, RING);
        }
        to.inflation = x.inflation; to.core = x.core; to.expected = x.expected; to.cpiLog = x.cpiLog; to.priceLevel = x.priceLevel;
        to.utilities = x.utilities; to.gasLog0 = x.gasLog0; to.tariffAbsorbed = x.tariffAbsorbed;
        to.fedRate = x.fedRate; to.credibility = x.credibility; to.tenYear = x.tenYear; to.tenNews = x.tenNews; to.oil = x.oil; to.oilBase = x.oilBase; to.oilDev = x.oilDev;
        to.gas = x.gas; to.stocks = x.stocks; to.stocks0 = x.stocks0; to.dollar = x.dollar; to.sentiment = x.sentiment; to.sentiment0 = x.sentiment0;
        to.tariff = x.tariff; to.reportedUnemployment = x.reportedUnemployment; to.reportedInflation = x.reportedInflation;
        to.reportedGrowth = x.reportedGrowth; to.sprRelief = x.sprRelief; to.fedPressured = x.fedPressured; to.startMisery = x.startMisery;
        to.demand = x.demand; to.financial = x.financial; to.financialPrev = x.financialPrev; to.supply = x.supply; to.gapPrev = x.gapPrev;
        to.calm = x.calm; to.pushes.clear(); to.pushes.addAll(x.pushes);
        to.housing = x.housing; to.housingSmooth = x.housingSmooth; to.housingPolicy = x.housingPolicy; to.manufacturing = x.manufacturing;
        to.aiInvest = x.aiInvest; to.farm = x.farm; to.energyIndex = x.energyIndex; to.tourism = x.tourism;
    }

    // =========================================================================================
    //  Transitions accepted from other systems
    // =========================================================================================

    /** Applies an economic transition. Returns false if the effect is not the economy's to apply. */
    public boolean apply(EconomyState s, BudgetState b, Effect e) {
        switch (e) {
            case Effect.EconomyShock x -> push(s, x.growthPoints(), x.inflationPoints(), x.months());
            // until bills are re-expressed as the policy steps they make, a bill's demand arrives with its growth impulse
            // (an EconomyShock), so its deficit only adds to the debt
            case Effect.Deficit x -> addProgram(b, new Program("deficit-" + (b.programs.size() + 1), FiscalClass.NONE, x.pctGdp(),
                    new double[]{1}, s.week, -1, 1));
            case Effect.FedPressure x -> {
                s.credibility = Math.max(p.credibilityFloor, s.credibility - p.fedPressureCredibility);
                s.fedPressured = true;
            }
            case Effect.OilRelease x -> s.sprRelief = Math.max(s.sprRelief, x.dollars());
            default -> { return false; }
        }
        return true;
    }

    /** A law's money, from the week it starts. */
    public void addProgram(BudgetState b, Program program) { b.programs.add(program); }

    /**
     * An event's effect: {@code growthPoints} of growth spread over the months (a push to the gap), part of it lingering
     * in demand afterwards as Resolute Desk's shocks do, and inflation over the same months.
     */
    void push(EconomyState s, double growthPoints, double inflationPoints, double months) {
        if (months <= 0) return;
        int weeks = Math.max(1, (int) Math.round(months * WEEKS_PER_MONTH));
        s.pushes.add(new EconomyState.Push(p.gameToGap * growthPoints / weeks, p.supplyPassThrough * inflationPoints / months, weeks));
        s.demand += p.gameLinger * p.gameToGap * growthPoints;
    }

    // =========================================================================================
    //  The week
    // =========================================================================================

    /** Advance the economy by {@code dtMonths}: that many weeks, at least one. */
    public void step(EconomyState s, BudgetState b, double dtMonths, MarketContext ctx, DeterministicRandomManager rng) {
        upgrade(s, b);
        int weeks = Math.max(1, (int) Math.round(dtMonths * WEEKS_PER_MONTH));
        for (int i = 0; i < weeks; i++) week(s, b, ctx, rng);
    }

    private void week(EconomyState s, BudgetState b, MarketContext ctx, DeterministicRandomManager rng) {
        shocks(s, rng);
        double gapPush = 0, inflationPush = 0;
        for (EconomyState.Push x : s.pushes) { gapPush += x.gapPerWeek; inflationPush += x.inflation; x.weeksLeft--; }
        s.pushes.removeIf(x -> x.weeksLeft <= 0);

        // ---- supply: labour, productivity and capital make potential output
        s.population += (p.populationGrowth + s.immigration * p.immigrationLabour) / 5200;
        s.participation += p.participationDrift / 52;
        double absorb = p.tariffAbsorbPerWeek * (s.tariff - s.tariffAbsorbed);
        s.tariffAbsorbed += absorb;
        s.tfp += (p.tfpGrowth + s.tfpPolicy + p.aiTfp * (s.aiInvest - 100) / 100) / 5200 - p.tariffTfp / 100 * absorb;
        double real10 = s.tenYear - s.expected;
        double invest = (s.investBase - p.investRateSensitivity / 100 * (real10 - s.real10Neutral)) * (1 - p.userCostElasticity * s.userCost / 100);
        double yNow = s.logY[s.ring];
        s.capital = Math.log(Math.exp(s.capital) * (1 - p.depreciation / 52) + Math.max(0, invest) * Math.exp(yNow) / 52);
        double before = s.potential;
        s.potential = s.tfp + p.capitalShare * s.capital + (1 - p.capitalShare) * labour(s);
        s.potentialGrowth += ((s.potential - before) * 5200 - s.potentialGrowth) / 13;

        // ---- demand: the gap moves toward its target
        double mortgage = s.tenYear + p.mortgageSpread;
        s.realRateLag += (s.fedRate - (s.expected - p.cpiWedge) - s.realRateLag) / 13;
        s.fiscal = fiscal(s, b);
        double target = -p.rateSensitivity * (s.realRateLag - p.neutralRealRate) - p.mortgageSensitivity * (mortgage - p.mortgageNormal) + s.fiscal
                - p.tariffDemand * (s.tariffAbsorbed - p.tariff) - p.dollarDemand * (s.dollar - 100) + p.sentimentDemand * (s.sentiment - s.sentiment0)
                + s.demand + s.financial;
        s.gapPrev = s.outputGap;
        s.outputGap += p.gapAdjust * (target - s.outputGap) + gapPush + p.gapNoise * rng.gaussian("economy.noise");
        int next = (s.ring + 1) % RING, yearAgo = (next + 1) % RING;
        s.logY[next] = s.potential + s.outputGap / 100;
        s.gaps[next] = s.outputGap;
        s.growth = (s.logY[next] - s.logY[yearAgo]) * 100;

        // ---- jobs: Okun's law
        s.structural *= 0.995;
        double uTarget = s.nairu + s.structural - p.okun * s.outputGap;
        s.unemployment = clamp(s.unemployment + p.unemploymentAdjust * (uTarget - s.unemployment) + p.unemploymentNoise * rng.gaussian("economy.noise"), 1, 30);

        // ---- prices, part by part
        s.expected += p.expectAdjust * (s.credibility * (p.inflationTarget + p.cpiWedge) + (1 - s.credibility) * s.inflation - s.expected);
        double lagged = s.gaps[(next - p.gapLagWeeks + RING) % RING];
        double slack = lagged < -5 ? -5 + 0.04 * (lagged + 5) : lagged;   // deep slumps stop pulling prices down (Resolute Desk)
        double weekly = 0, tariffLift = 0;
        double dollarMove = 0;
        double dollarBefore = s.dollar;
        markets(s, b, ctx, rng);
        dollarMove = Math.log(s.dollar / dollarBefore);
        for (Part part : Part.values()) {
            int i = part.ordinal();
            double driver = switch (part) {
                case SHELTER -> -p.shelterHousing * (s.housingSmooth - p.housing) + p.shelterMortgage * (mortgage - p.mortgageNormal);
                case FOOD -> 0.01 * (s.oil - s.oilBase);
                case SERVICES -> 0.3 * (s.nairu - s.unemployment);
                default -> 0;
            };
            double partTarget = s.expected + p.wedge[i] + p.phillipsSlope * slack + driver + s.partPolicy[i] + s.supply + inflationPush;
            s.trend[i] += p.partAdjust * (partTarget - s.trend[i]) + p.partNoise * rng.gaussian("economy.prices");
            double last = s.prices[i][s.ring], now;
            if (part == Part.ENERGY) {
                s.utilities += s.trend[i] / 5200;
                now = p.energyGasShare * (Math.log(s.gas) - s.gasLog0) + (1 - p.energyGasShare) * s.utilities;
            } else {
                now = last + s.trend[i] / 5200;
                if (part == Part.GOODS) now += p.tariffGoods / 100 * absorb - p.dollarGoods * dollarMove;
                else if (part == Part.FOOD) now += p.tariffFood / 100 * absorb;
                else now += p.tariffOther / 100 * absorb;
            }
            if (part == Part.GOODS) tariffLift += part.weight * p.tariffGoods / 100 * absorb;
            else if (part == Part.FOOD) tariffLift += part.weight * p.tariffFood / 100 * absorb;
            else if (part != Part.ENERGY) tariffLift += part.weight * p.tariffOther / 100 * absorb;
            s.prices[i][next] = now;
            s.yoy[i] = (now - s.prices[i][yearAgo]) * 100;
            weekly += part.weight * (now - last);
        }
        s.tariffPrices[next] = s.tariffPrices[s.ring] + tariffLift;
        s.ring = next;
        headline(s);
        s.cpiLog += weekly;
        s.priceLevel = p.priceLevel * Math.exp(s.cpiLog);
        if (s.inflation > p.credibilityLossAbove) s.credibility = Math.max(p.credibilityFloor, s.credibility - p.credibilityLossRate / WEEKS_PER_MONTH);
        else if (s.inflation < p.credibilityGainBelow) s.credibility = Math.min(p.credibilityCeiling, s.credibility + p.credibilityGainRate / WEEKS_PER_MONTH);

        sectors(s, rng);
        s.week++;
        b.week = s.week;
        budget(b, s);
    }

    /** Oil and gas, the 10-year, stocks, the dollar and consumer sentiment. */
    private void markets(EconomyState s, BudgetState b, MarketContext ctx, DeterministicRandomManager rng) {
        s.oilDev = p.oilPersistence * s.oilDev + p.oilNoisePerWeek * s.oilBase / 75 * rng.gaussian("economy.oil");
        double oilTarget = s.oilBase + s.oilDev + ctx.oilPremium() - s.sprRelief;
        s.oil = clamp(s.oil + p.oilAdjustPerWeek * (oilTarget - s.oil), 15, 250);
        if (s.sprRelief > 0) s.sprRelief = Math.max(0, s.sprRelief - 1);
        s.gas += p.gasAdjust * (gasTarget(s, s.oil) - s.gas);

        // the 10-year: fundamentals (the Fed's path, expected inflation, the term premium and the debt) move it slowly; news
        // (bills under debate, new borrowing not yet in the debt, the Treasury, the Fed's credibility) within weeks
        double before = s.tenYear;
        double fundamentals = p.tenYearFedWeight * s.fedRate + (1 - p.tenYearFedWeight) * (p.neutralRealRate + s.expected - p.cpiWedge) + p.termPremium
                + p.debtPremium * Math.max(0, b.debt - 100);
        double news = p.unfundedPremium * unfunded(s, b) + p.tenYearPendingWeight * ctx.pendingDeficit()
                + p.tenYearTreasuryWeight * (70 - ctx.treasuryCompetence()) + p.tenYearCredibilityWeight * (p.startCredibility - s.credibility);
        double core = s.tenYear - s.tenNews;
        core += p.tenYearAdjustPerWeek * (fundamentals - core) + p.tenYearNoisePerWeek * rng.gaussian("economy.bonds");
        s.tenNews += p.tenYearNewsPerWeek * (news - s.tenNews);
        s.tenYear = core + s.tenNews;

        double ret = p.stockDrift + p.stockGrowth * (s.growth - s.potentialGrowth) - p.stockTenYear * (s.tenYear - before)
                + p.stockFinancial * (s.financial - s.financialPrev) + p.stockGap * (s.outputGap - s.gapPrev) + p.stockNoise * rng.gaussian("economy.stocks");
        s.financialPrev = s.financial;
        s.stocks *= Math.exp(ret);

        double dollarTarget = 100 + p.dollarTenYear * (s.tenYear - p.startTenYear) + p.dollarTariff * (s.tariff - p.tariff);
        s.dollar += p.dollarAdjust * (dollarTarget - s.dollar) + p.dollarNoise * rng.gaussian("economy.dollar");

        s.sentiment += p.sentimentAdjust * (sentimentOf(s) - ctx.stress() - s.sentiment) + p.sentimentNoise * rng.gaussian("economy.mood");
    }

    /**
     * New borrowing the markets have priced but the debt does not show yet: each program in force, weighted by how much of
     * its first decade is still to come.
     */
    private double unfunded(EconomyState s, BudgetState b) {
        double u = 0;
        for (Program pr : b.programs) {
            if (pr.endWeek >= 0 && s.week >= pr.endWeek) continue;
            u += pr.fullPctGdp * Math.max(0, 1 - (s.week - pr.startWeek) / 520.0);
        }
        return u;
    }

    /** Resolute Desk's sectors, driven by the model. */
    private void sectors(EconomyState s, DeterministicRandomManager rng) {
        double mortgage = s.tenYear + p.mortgageSpread;
        s.housing += p.housingAdjust * (p.housing - p.housingMortgage * (mortgage - p.mortgageNormal) + p.housingGap * s.outputGap + s.housingPolicy - s.housing)
                + 0.01 * noise(rng, "economy.sectors");
        s.housing = Math.max(0.3, s.housing);
        s.housingSmooth += (s.housing - s.housingSmooth) / 52;
        s.manufacturing += 0.08 * (50 + p.manufacturingGap * s.outputGap - p.manufacturingTariff * (s.tariff - p.tariff) - p.manufacturingDollar * (s.dollar - 100)
                - s.manufacturing) + 0.4 * noise(rng, "economy.sectors");
        s.aiInvest *= 1 + p.aiGrowth + p.aiNoise * rng.gaussian("economy.sectors");
        s.farm += 0.03 * (100 - p.farmTariff * Math.max(0, s.tariff - p.tariff) - s.farm) + 0.5 * noise(rng, "economy.sectors");
        s.energyIndex += 0.03 * (100 + p.energyOil * (s.oil - s.oilBase) - s.energyIndex) + 0.3 * noise(rng, "economy.sectors");
        s.tourism += 0.05 * (100 + p.tourismGap * s.outputGap - p.tourismDollar * (s.dollar - 100) - s.tourism) + 0.4 * noise(rng, "economy.sectors");
    }

    /** Noise for the sectors, off when the economy is quiet. */
    private double noise(DeterministicRandomManager rng, String stream) {
        double g = rng.gaussian(stream);
        return p.quiet ? 0 : g;
    }

    /** The budget: revenue and outlays by kind, interest on the debt as it rolls over, the programs laws added, and the debt. */
    private void budget(BudgetState b, EconomyState s) {
        b.averageRate += p.averageRateAdjust / 52 * ((1 - p.averageRateFedShare) * s.tenYear + p.averageRateFedShare * s.fedRate - b.averageRate);
        int prev = (s.ring - 1 + RING) % RING;
        double nominal = (s.logY[s.ring] - s.logY[prev]) + (s.inflation - p.cpiWedge) / 5200;
        b.debt = b.debt * Math.exp(-nominal) + b.deficit / 52;
        for (Program pr : b.programs) pr.adapted += (pr.pctGdp(b.week) - pr.adapted) / p.fiscalAdaptWeeks;
        budget(b, s.outputGap, s.tariff);
    }

    private void budget(BudgetState b, double gap, double tariff) {
        double years = b.week / 52.0;
        b.revenue = p.revenue + p.revenueCyclical * gap + p.tariffRevenue * (tariff - p.tariff);
        b.mandatory = p.mandatory + p.mandatoryGrowth * years - p.outlaysCyclical * gap;
        b.discretionary = p.discretionary + p.discretionaryDrift * years;
        b.interest = b.averageRate * b.debt / 100;
        double programs = 0;
        for (Program pr : b.programs) programs += pr.pctGdp(b.week);
        b.programsNow = programs;
        b.deficit = b.mandatory + b.discretionary + b.interest - b.revenue + programs;
    }

    /**
     * The demand fiscal policy adds to the gap's target: each program's money not yet adjusted to, times its multiplier
     * in today's economy, times the law's luck.
     */
    private double fiscal(EconomyState s, BudgetState b) {
        double f = 0;
        for (Program pr : b.programs) {
            double fresh = pr.pctGdp(s.week) - pr.adapted;
            if (fresh != 0) f += multiplier(pr.cls, s) * pr.draw * fresh;
        }
        return f;
    }

    /**
     * What a dollar of this kind moves output by today, within CBO's range: in a slump it rises up to half way to the top
     * of the range, further when the Fed is at its floor and cannot offset it; above capacity it falls toward the bottom.
     */
    public double multiplier(FiscalClass c, EconomyState s) {
        double slump = Math.min(0.5, p.multiplierSlump * Math.max(0, -s.outputGap));
        double floor = p.multiplierFloor * clamp((1.0 - s.fedRate) / (1.0 - p.fedFloor), 0, 1);
        double boom = p.multiplierBoom * Math.max(0, s.outputGap);
        double x = clamp(slump + floor - boom, -1, 1);
        return x >= 0 ? c.central + (c.high - c.central) * x : c.central + (c.central - c.low) * x;
    }

    // =========================================================================================
    //  Shocks
    // =========================================================================================

    /** The business cycle's surprises (Resolute Desk's odds): a recession, a financial crisis, an oil spike, the odd boom. */
    private void shocks(EconomyState s, DeterministicRandomManager rng) {
        s.demand *= p.demandDecay;
        s.financial *= p.financialDecay;
        s.supply *= p.supplyDecay;
        double x = rng.uniform("economy.recession"), size = rng.uniform("economy.recession");
        s.lastShock = null;
        if (s.calm > 0) { s.calm--; return; }
        double r = p.shockRecession, f = r + p.shockFinancial, o = f + p.shockOil, bm = o + p.shockBoom;
        if (x < r) {
            s.demand += p.recessionDemand * (0.75 + 0.5 * size);
            s.pushes.add(new EconomyState.Push(p.recessionPush * (0.75 + 0.5 * size) / p.pushWeeks, -0.3, p.pushWeeks));
            s.calm = p.calmRecession;
            s.lastShock = Shock.RECESSION;
        } else if (x < f) {
            s.financial += p.financialDemand * (0.75 + 0.5 * size);
            s.calm = p.calmFinancial;
            s.lastShock = Shock.FINANCIAL;
        } else if (x < o) {
            s.oilDev += s.oil * (0.25 + 0.2 * size);
            s.oilBase *= 1.08;
            s.calm = p.calmOil;
            s.lastShock = Shock.OIL;
        } else if (x < bm) {
            s.demand += p.boomDemand * (0.5 + size);
            s.calm = p.calmBoom;
            s.lastShock = Shock.BOOM;
        }
    }

    // =========================================================================================
    //  The Fed and the releases
    // =========================================================================================

    /**
     * A Federal Reserve meeting: a Taylor rule on the <em>reported</em> data (inflation as the Fed measures it, a little
     * under the consumer price index), moving 25 or 50 basis points (Resolute Desk's steps), faster in a financial crisis.
     * It looks through most of what tariffs add to prices, as it said it would in 2025, and does not raise rates while
     * unemployment is well above its natural rate. Public pressure lowers the bar for a cut
     * (and has already cost credibility).
     * @return the change in the policy rate
     */
    public double fedMeeting(EconomyState s, BudgetState b) {
        double pce = s.reportedInflation - p.cpiWedge - p.fedLookThrough * s.tariffInflation();
        // the gap as the Fed sees it: half from the jobs report, half from its staff's estimate of output against potential
        double gapEstimate = (1 - p.fedStaffGap) * (s.nairu - s.reportedUnemployment) / p.okun + p.fedStaffGap * s.outputGap;
        double taylor = clamp(p.neutralRealRate + pce + p.taylorInflation * (pce - p.inflationTarget) + p.taylorGap * gapEstimate, p.fedFloor, 20);
        double diff = taylor - s.fedRate;
        if (diff > 0 && gapEstimate < p.fedLiftoffGap && pce < p.inflationTarget + 0.5) diff = 0;
        double hold = s.fedPressured && diff < 0 ? p.fedPressuredHold : p.fedHold;
        double step = Math.abs(diff) < hold ? 0 : Math.abs(diff) < p.fedBigStep ? 0.25 : 0.5;
        if (s.financial < -2 && diff < 0) step = Math.max(step, Math.min(1.0, Math.floor(-diff * 4) / 4));
        double before = s.fedRate;
        s.fedRate = clamp(s.fedRate + Math.signum(diff) * step, p.fedFloor, 20);
        s.fedPressured = false;
        return s.fedRate - before;
    }

    /** Monthly jobs report: the unemployment rate and the jobs added. Returns the change in the reported rate. */
    public double jobsReport(EconomyState s, DeterministicRandomManager rng) {
        double was = s.reportedUnemployment;
        s.reportedUnemployment = Math.round((s.unemployment + p.releaseNoise * rng.gaussian("economy.release")) * 10) / 10.0;
        double noise = rng.gaussian("economy.release") * (p.releaseNoise > 0 ? 45 : 0);
        s.payrolls = p.labourForce * labourGrowth(s) / 1200 - p.labourForce * (s.unemployment - s.uAtLastJobs) / 100 + noise;
        s.uAtLastJobs = s.unemployment;
        return s.reportedUnemployment - was;
    }

    /** The quarterly GDP report: the first estimate of output's growth over the last thirteen weeks, at an annual rate. */
    public double gdpReport(EconomyState s, DeterministicRandomManager rng) {
        double q = (s.logY[s.ring] - s.logY[(s.ring - 13 + RING) % RING]) * 400;
        s.reportedGrowth = Math.round((q + p.releaseNoise * 10 * rng.gaussian("economy.release")) * 10) / 10.0;
        return s.reportedGrowth;
    }

    /** The surprise of the last week stepped, if any (for the news; not saved). */
    public Shock lastShock(EconomyState s) { return s.lastShock; }

    /** Monthly inflation report: returns the change from the previous report. */
    public double inflationReport(EconomyState s, DeterministicRandomManager rng) {
        double was = s.reportedInflation;
        s.reportedInflation = Math.round((s.inflation + p.releaseNoise * rng.gaussian("economy.release")) * 10) / 10.0;
        return s.reportedInflation - was;
    }

    // =========================================================================================
    //  Reading the economy
    // =========================================================================================

    public double expectedInflation(EconomyState s) { return s.expected; }

    public double deficit(EconomyState s, BudgetState b) { return b.deficit; }

    public double gas(EconomyState s) { return s.gas; }

    /** Gas at the ordinary oil price and today's prices: what people measure the pump against. */
    public double normalGas(EconomyState s) { return gasTarget(s, p.oilBase); }

    private double gasTarget(EconomyState s, double oil) { return p.gasIntercept * s.priceLevel / p.priceLevel + p.gasPerOilDollar * oil; }

    /** Consumer sentiment as Resolute Desk computes it: jobs, prices and gas against what people expect, growth and the stock market. */
    double sentimentOf(EconomyState s) {
        double pi = s.inflation >= 0 ? s.inflation : -1.2 * s.inflation;
        return clamp(96 - 4.5 * (s.unemployment - p.sentimentURef + 0.5) - 5.5 * (pi - p.sentimentPiRef + 0.5)
                - 6 * (s.gas / s.priceLevel - p.sentimentGasRef + 0.1) + 1.5 * (s.growth - 2) + 12 * Math.log(s.stocks / s.stocks0), 45, 112);
    }

    /** The labour force's growth, % a year: population, immigration and the drift in participation. */
    private double labourGrowth(EconomyState s) {
        return p.populationGrowth + s.immigration * p.immigrationLabour + 100 * p.participationDrift / s.participation;
    }

    /** Log labour input against the start: people, participation and the natural rate of unemployment. */
    private double labour(EconomyState s) {
        return s.population + Math.log(s.participation / p.participation) + Math.log((1 - s.nairu / 100) / (1 - p.naturalUnemployment / 100));
    }

    private static void headline(EconomyState s) {
        double all = 0, core = 0, coreWeight = 0;
        for (Part part : Part.values()) {
            double v = s.yoy[part.ordinal()];
            all += part.weight * v;
            if (part.core()) { core += part.weight * v; coreWeight += part.weight; }
        }
        s.inflation = all;
        s.core = core / coreWeight;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
