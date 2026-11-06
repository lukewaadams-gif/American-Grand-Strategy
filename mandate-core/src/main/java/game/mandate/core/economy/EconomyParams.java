package game.mandate.core.economy;

import game.mandate.core.data.Tuning;

/** Every constant of the economy, read from {@code tuning.properties} (keys {@code economy.*}). */
public record EconomyParams(
        double potentialGrowth, double naturalUnemployment, double inflationTarget, double neutralRealRate,
        double recessionRiskPerMonth, double recessionGrowth, double recessionInflation, double recessionMonths,
        double gapPersistence, double shockToGap, double rateSensitivity, double gapNoise, double growthSmoothing,
        double okun, double unemploymentAdjust, double phillipsSlope, double supplyPassThrough, double gasPassThrough,
        double inflationAdjust, double inflationNoise,
        double credibilityLossAbove, double credibilityLossRate, double credibilityFloor,
        double credibilityGainBelow, double credibilityGainRate, double credibilityCeiling, double fedPressureCredibility,
        double taylorInflation, double fedFiscalWeight, double fedStep, double fedThreshold, double fedPressuredThreshold,
        double cyclicalDeficit, double interestAboveRate, double interestSensitivity,
        double oilBase, double oilAdjustPerWeek, double oilNoisePerWeek, double gasIntercept, double gasPerOilDollar,
        double termPremium, double tenYearFedWeight, double tenYearDeficitWeight, double tenYearPendingWeight,
        double tenYearTreasuryWeight, double tenYearCredibilityWeight, double tenYearAdjustPerWeek, double tenYearNoisePerWeek,
        double mortgageSpread, double confidenceGas, double confidenceInflation, double confidenceAdjustPerWeek,
        double releaseNoise,
        double startGap, double startUnemployment, double startInflation, double startFedRate, double startCredibility,
        double startOil, double startTenYear, double startDebt, double baselineDeficit) {

    public static EconomyParams from(Tuning t) {
        return new EconomyParams(
                t.d("economy.potentialGrowth"), t.d("economy.naturalUnemployment"), t.d("economy.inflationTarget"), t.d("economy.neutralRealRate"),
                t.d("economy.recessionRiskPerMonth"), t.d("economy.recessionGrowth"), t.d("economy.recessionInflation"), t.d("economy.recessionMonths"),
                t.d("economy.gapPersistence"), t.d("economy.shockToGap"), t.d("economy.rateSensitivity"), t.d("economy.gapNoise"), t.d("economy.growthSmoothing"),
                t.d("economy.okun"), t.d("economy.unemploymentAdjust"), t.d("economy.phillipsSlope"), t.d("economy.supplyPassThrough"), t.d("economy.gasPassThrough"),
                t.d("economy.inflationAdjust"), t.d("economy.inflationNoise"),
                t.d("economy.credibilityLossAbove"), t.d("economy.credibilityLossRate"), t.d("economy.credibilityFloor"),
                t.d("economy.credibilityGainBelow"), t.d("economy.credibilityGainRate"), t.d("economy.credibilityCeiling"), t.d("economy.fedPressureCredibility"),
                t.d("economy.taylorInflation"), t.d("economy.fedFiscalWeight"), t.d("economy.fedStep"), t.d("economy.fedThreshold"), t.d("economy.fedPressuredThreshold"),
                t.d("budget.cyclicalDeficit"), t.d("budget.interestAboveRate"), t.d("budget.interestSensitivity"),
                t.d("markets.oilBase"), t.d("markets.oilAdjustPerWeek"), t.d("markets.oilNoisePerWeek"), t.d("markets.gasIntercept"), t.d("markets.gasPerOilDollar"),
                t.d("markets.termPremium"), t.d("markets.tenYearFedWeight"), t.d("markets.tenYearDeficitWeight"), t.d("markets.tenYearPendingWeight"),
                t.d("markets.tenYearTreasuryWeight"), t.d("markets.tenYearCredibilityWeight"), t.d("markets.tenYearAdjustPerWeek"), t.d("markets.tenYearNoisePerWeek"),
                t.d("markets.mortgageSpread"), t.d("markets.confidenceGas"), t.d("markets.confidenceInflation"), t.d("markets.confidenceAdjustPerWeek"),
                t.d("economy.releaseNoise"),
                t.d("economy.start.outputGap"), t.d("economy.start.unemployment"), t.d("economy.start.inflation"), t.d("economy.start.fedRate"),
                t.d("economy.start.credibility"), t.d("markets.start.oil"), t.d("markets.start.tenYear"), t.d("budget.start.debt"), t.d("budget.baselineDeficit"));
    }

    public double gas(double oil) { return gasIntercept + gasPerOilDollar * oil; }
}
