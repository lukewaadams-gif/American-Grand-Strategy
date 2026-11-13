package game.mandate.core.economy;

import game.mandate.core.data.Tuning;

/**
 * Every constant of the economy, read from {@code tuning.properties} (keys {@code economy.*}, {@code budget.*},
 * {@code markets.*} and {@code sectors.*}; the comments there say where each comes from). {@link #quiet()} gives a copy
 * with the noise and the random shocks turned off, for the calibration tests and for scoring a bill.
 */
public final class EconomyParams {
    // supply
    double capitalShare, depreciation, tfpGrowth, populationGrowth, immigration, immigrationLabour, participation, participationDrift,
            naturalUnemployment, investRateSensitivity, userCostElasticity, aiTfp;
    // demand
    double gapAdjust, gapNoise, rateSensitivity, mortgageSensitivity, mortgageNormal, tariffDemand, dollarDemand, sentimentDemand,
            fiscalAdaptWeeks, multiplierSlump, multiplierFloor, multiplierBoom;
    // jobs
    double okun, unemploymentAdjust, unemploymentNoise, labourForce;
    // prices
    double inflationTarget, cpiWedge, phillipsSlope, partAdjust, partNoise, expectAdjust, energyGasShare,
            tariffGoods, tariffFood, tariffOther, tariffAbsorbPerWeek, tariffTfp, dollarGoods, shelterHousing, shelterMortgage,
            supplyPassThrough, priceLevel;
    int gapLagWeeks;
    final double[] wedge = new double[Part.COUNT], startPart = new double[Part.COUNT];
    // credibility
    double credibilityLossAbove, credibilityLossRate, credibilityFloor, credibilityGainBelow, credibilityGainRate, credibilityCeiling,
            fedPressureCredibility;
    // the Fed
    double neutralRealRate, taylorInflation, taylorGap, fedHold, fedPressuredHold, fedBigStep, fedFloor, fedLiftoffGap, fedLookThrough, fedStaffGap, releaseNoise;
    // shocks
    double shockRecession, shockFinancial, shockOil, shockBoom, recessionPush, recessionDemand, financialDemand, boomDemand,
            demandDecay, financialDecay, supplyDecay, gameToGap, gameLinger;
    int pushWeeks, calmRecession, calmFinancial, calmOil, calmBoom;
    // starting values
    double startGap, startUnemployment, startFedRate, startCredibility, startExpected, startOil, startTenYear, startDebt, startStocks;
    // budget
    double revenue, revenueCyclical, outlaysCyclical, mandatory, mandatoryGrowth, discretionary, discretionaryDrift, averageRate,
            averageRateAdjust, averageRateFedShare, tariffRevenue;
    // markets
    double oilBase, oilAdjustPerWeek, oilPersistence, oilNoisePerWeek, gasIntercept, gasPerOilDollar, gasAdjust, termPremium, debtPremium, unfundedPremium,
            tenYearFedWeight, tenYearPendingWeight, tenYearTreasuryWeight, tenYearCredibilityWeight, tenYearAdjustPerWeek, tenYearNewsPerWeek, tenYearNoisePerWeek,
            mortgageSpread, stockDrift, stockGrowth, stockTenYear, stockFinancial, stockGap, stockNoise, dollarTenYear, dollarTariff,
            dollarAdjust, dollarNoise, sentimentURef, sentimentPiRef, sentimentGasRef, sentimentAdjust, sentimentNoise, tariff;
    // sectors
    double housing, housingMortgage, housingGap, housingAdjust, manufacturingGap, manufacturingTariff, manufacturingDollar, aiGrowth, aiNoise,
            farmTariff, energyOil, tourismGap, tourismDollar;

    private Tuning source;
    /** No noise and no random shocks. */
    boolean quiet;

    private EconomyParams() {}

    public static EconomyParams from(Tuning t) {
        EconomyParams p = new EconomyParams();
        p.source = t;
        p.capitalShare = t.d("economy.capitalShare"); p.depreciation = t.d("economy.depreciation"); p.tfpGrowth = t.d("economy.tfpGrowth");
        p.populationGrowth = t.d("economy.populationGrowth"); p.immigration = t.d("economy.immigration"); p.immigrationLabour = t.d("economy.immigrationLabour");
        p.participation = t.d("economy.participation"); p.participationDrift = t.d("economy.participationDrift");
        p.naturalUnemployment = t.d("economy.naturalUnemployment"); p.investRateSensitivity = t.d("economy.investRateSensitivity");
        p.userCostElasticity = t.d("economy.userCostElasticity"); p.aiTfp = t.d("economy.aiTfp");

        p.gapAdjust = t.d("economy.gapAdjust"); p.gapNoise = t.d("economy.gapNoise"); p.rateSensitivity = t.d("economy.rateSensitivity");
        p.mortgageSensitivity = t.d("economy.mortgageSensitivity"); p.mortgageNormal = t.d("economy.mortgageNormal");
        p.tariffDemand = t.d("economy.tariffDemand"); p.dollarDemand = t.d("economy.dollarDemand"); p.sentimentDemand = t.d("economy.sentimentDemand");
        p.fiscalAdaptWeeks = t.d("economy.fiscalAdaptWeeks");
        p.multiplierSlump = t.d("economy.multiplierSlump"); p.multiplierFloor = t.d("economy.multiplierFloor"); p.multiplierBoom = t.d("economy.multiplierBoom");

        p.okun = t.d("economy.okun"); p.unemploymentAdjust = t.d("economy.unemploymentAdjust"); p.unemploymentNoise = t.d("economy.unemploymentNoise");
        p.labourForce = t.d("economy.labourForce");

        p.inflationTarget = t.d("economy.inflationTarget"); p.cpiWedge = t.d("economy.cpiWedge"); p.phillipsSlope = t.d("economy.phillipsSlope");
        p.gapLagWeeks = t.i("economy.gapLagWeeks"); p.partAdjust = t.d("economy.partAdjust"); p.partNoise = t.d("economy.partNoise");
        p.expectAdjust = t.d("economy.expectAdjust");
        for (Part part : Part.values()) {
            p.wedge[part.ordinal()] = t.d("economy.wedge." + part.key);
            p.startPart[part.ordinal()] = t.d("economy.start." + part.key);
        }
        p.energyGasShare = t.d("economy.energyGasShare"); p.tariffGoods = t.d("economy.tariffGoods"); p.tariffFood = t.d("economy.tariffFood");
        p.tariffOther = t.d("economy.tariffOther"); p.tariffAbsorbPerWeek = t.d("economy.tariffAbsorbPerWeek"); p.tariffTfp = t.d("economy.tariffTfp");
        p.dollarGoods = t.d("economy.dollarGoods"); p.shelterHousing = t.d("economy.shelterHousing"); p.shelterMortgage = t.d("economy.shelterMortgage");
        p.supplyPassThrough = t.d("economy.supplyPassThrough"); p.priceLevel = t.d("economy.priceLevel");

        p.credibilityLossAbove = t.d("economy.credibilityLossAbove"); p.credibilityLossRate = t.d("economy.credibilityLossRate");
        p.credibilityFloor = t.d("economy.credibilityFloor"); p.credibilityGainBelow = t.d("economy.credibilityGainBelow");
        p.credibilityGainRate = t.d("economy.credibilityGainRate"); p.credibilityCeiling = t.d("economy.credibilityCeiling");
        p.fedPressureCredibility = t.d("economy.fedPressureCredibility");

        p.neutralRealRate = t.d("economy.neutralRealRate"); p.taylorInflation = t.d("economy.taylorInflation"); p.taylorGap = t.d("economy.taylorGap");
        p.fedHold = t.d("economy.fedHold"); p.fedPressuredHold = t.d("economy.fedPressuredHold"); p.fedBigStep = t.d("economy.fedBigStep");
        p.fedFloor = t.d("economy.fedFloor"); p.fedLiftoffGap = t.d("economy.fedLiftoffGap"); p.fedLookThrough = t.d("economy.fedLookThrough"); p.fedStaffGap = t.d("economy.fedStaffGap"); p.releaseNoise = t.d("economy.releaseNoise");

        p.shockRecession = t.d("economy.shock.recession"); p.shockFinancial = t.d("economy.shock.financial"); p.shockOil = t.d("economy.shock.oil");
        p.shockBoom = t.d("economy.shock.boom"); p.recessionPush = t.d("economy.shock.recessionPush"); p.recessionDemand = t.d("economy.shock.recessionDemand");
        p.financialDemand = t.d("economy.shock.financialDemand"); p.boomDemand = t.d("economy.shock.boomDemand"); p.pushWeeks = t.i("economy.shock.pushWeeks");
        p.demandDecay = t.d("economy.shock.demandDecay"); p.financialDecay = t.d("economy.shock.financialDecay"); p.supplyDecay = t.d("economy.shock.supplyDecay");
        p.calmRecession = t.i("economy.shock.calmRecession"); p.calmFinancial = t.i("economy.shock.calmFinancial"); p.calmOil = t.i("economy.shock.calmOil");
        p.calmBoom = t.i("economy.shock.calmBoom"); p.gameToGap = t.d("economy.shock.gameToGap"); p.gameLinger = t.d("economy.shock.gameLinger");

        p.startGap = t.d("economy.start.outputGap"); p.startUnemployment = t.d("economy.start.unemployment"); p.startFedRate = t.d("economy.start.fedRate");
        p.startCredibility = t.d("economy.start.credibility"); p.startExpected = t.d("economy.start.expected");
        p.startOil = t.d("markets.start.oil"); p.startTenYear = t.d("markets.start.tenYear"); p.startDebt = t.d("budget.start.debt");
        p.startStocks = t.d("markets.stocks");

        p.revenue = t.d("budget.revenue"); p.revenueCyclical = t.d("budget.revenueCyclical"); p.outlaysCyclical = t.d("budget.outlaysCyclical");
        p.mandatory = t.d("budget.mandatory"); p.mandatoryGrowth = t.d("budget.mandatoryGrowth"); p.discretionary = t.d("budget.discretionary");
        p.discretionaryDrift = t.d("budget.discretionaryDrift"); p.averageRate = t.d("budget.averageRate"); p.averageRateAdjust = t.d("budget.averageRateAdjust");
        p.averageRateFedShare = t.d("budget.averageRateFedShare"); p.tariffRevenue = t.d("budget.tariffRevenue");

        p.oilBase = t.d("markets.oilBase"); p.oilAdjustPerWeek = t.d("markets.oilAdjustPerWeek"); p.oilPersistence = t.d("markets.oilPersistence");
        p.oilNoisePerWeek = t.d("markets.oilNoisePerWeek"); p.gasIntercept = t.d("markets.gasIntercept"); p.gasPerOilDollar = t.d("markets.gasPerOilDollar");
        p.gasAdjust = t.d("markets.gasAdjust"); p.termPremium = t.d("markets.termPremium"); p.debtPremium = t.d("markets.debtPremium"); p.unfundedPremium = t.d("markets.unfundedPremium");
        p.tenYearFedWeight = t.d("markets.tenYearFedWeight"); p.tenYearPendingWeight = t.d("markets.tenYearPendingWeight");
        p.tenYearTreasuryWeight = t.d("markets.tenYearTreasuryWeight"); p.tenYearCredibilityWeight = t.d("markets.tenYearCredibilityWeight");
        p.tenYearAdjustPerWeek = t.d("markets.tenYearAdjustPerWeek"); p.tenYearNoisePerWeek = t.d("markets.tenYearNoisePerWeek"); p.tenYearNewsPerWeek = t.d("markets.tenYearNewsPerWeek");
        p.mortgageSpread = t.d("markets.mortgageSpread");
        p.stockDrift = t.d("markets.stockDrift"); p.stockGrowth = t.d("markets.stockGrowth"); p.stockTenYear = t.d("markets.stockTenYear");
        p.stockFinancial = t.d("markets.stockFinancial"); p.stockGap = t.d("markets.stockGap"); p.stockNoise = t.d("markets.stockNoise");
        p.dollarTenYear = t.d("markets.dollarTenYear"); p.dollarTariff = t.d("markets.dollarTariff"); p.dollarAdjust = t.d("markets.dollarAdjust");
        p.dollarNoise = t.d("markets.dollarNoise");
        p.sentimentURef = t.d("markets.sentimentURef"); p.sentimentPiRef = t.d("markets.sentimentPiRef"); p.sentimentGasRef = t.d("markets.sentimentGasRef");
        p.sentimentAdjust = t.d("markets.sentimentAdjust"); p.sentimentNoise = t.d("markets.sentimentNoise"); p.tariff = t.d("markets.tariff");

        p.housing = t.d("sectors.housing"); p.housingMortgage = t.d("sectors.housingMortgage"); p.housingGap = t.d("sectors.housingGap");
        p.housingAdjust = t.d("sectors.housingAdjust"); p.manufacturingGap = t.d("sectors.manufacturingGap");
        p.manufacturingTariff = t.d("sectors.manufacturingTariff"); p.manufacturingDollar = t.d("sectors.manufacturingDollar");
        p.aiGrowth = t.d("sectors.aiGrowth"); p.aiNoise = t.d("sectors.aiNoise"); p.farmTariff = t.d("sectors.farmTariff");
        p.energyOil = t.d("sectors.energyOil"); p.tourismGap = t.d("sectors.tourismGap"); p.tourismDollar = t.d("sectors.tourismDollar");
        return p;
    }

    /** A copy with every source of noise and the random shocks off: what a score or a calibration test runs on. */
    public EconomyParams quiet() {
        EconomyParams q = from(source);
        q.gapNoise = 0; q.unemploymentNoise = 0; q.partNoise = 0; q.releaseNoise = 0; q.oilNoisePerWeek = 0; q.tenYearNoisePerWeek = 0;
        q.stockNoise = 0; q.dollarNoise = 0; q.sentimentNoise = 0; q.aiNoise = 0;
        q.quiet = true;
        q.shockRecession = 0; q.shockFinancial = 0; q.shockOil = 0; q.shockBoom = 0;
        return q;
    }

    /** Gas, $ a gallon, at a given oil price and today's price level ($1 a barrel is about 2.4 cents). */
    public double gas(double oil) { return gasIntercept + gasPerOilDollar * oil; }

    public double oilBase() { return oilBase; }
    public double mortgageSpread() { return mortgageSpread; }
    public double naturalUnemployment() { return naturalUnemployment; }
    public double inflationTarget() { return inflationTarget; }
    public double neutralRealRate() { return neutralRealRate; }
    public double okun() { return okun; }
    public double credibilityFloor() { return credibilityFloor; }
    public double startCredibility() { return startCredibility; }
    public double startUnemployment() { return startUnemployment; }
    public double startFedRate() { return startFedRate; }
    public double tariff() { return tariff; }
}
