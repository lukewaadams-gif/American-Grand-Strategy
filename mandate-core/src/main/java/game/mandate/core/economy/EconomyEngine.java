package game.mandate.core.economy;

import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

/**
 * The single authority for macroeconomic calculations: output gap, Okun's law, an expectations-anchored
 * Phillips curve, a Taylor-rule Fed that moves only at its meetings, debt dynamics, and the fast-moving markets
 * (oil, gas, the 10-year yield, consumer confidence). Derived from Resolute Desk's economy (damping and the
 * never-decaying fiscal impulse fixed) and the 12-week prototype's weekly markets.
 *
 * <p>Every step takes {@code dtMonths}, so the monthly governing game and the weekly prototype run the same
 * equations: partial-adjustment rates are compounded ({@code 1-(1-a)^dt}) and noise scales with {@code sqrt(dt)}.
 */
public final class EconomyEngine {
    public static final double WEEKS_PER_MONTH = 4.345;

    private final EconomyParams p;

    public EconomyEngine(EconomyParams p) { this.p = p; }

    public EconomyParams params() { return p; }

    public EconomyState newEconomy() {
        EconomyState s = new EconomyState();
        s.outputGap = p.startGap();
        s.growth = p.potentialGrowth();
        s.unemployment = p.startUnemployment();
        s.inflation = p.startInflation();
        s.fedRate = p.startFedRate();
        s.credibility = p.startCredibility();
        s.oil = p.startOil();
        s.tenYear = p.startTenYear();
        s.confidence = 100;
        s.reportedUnemployment = s.unemployment;
        s.reportedInflation = s.inflation;
        s.startMisery = s.misery();
        return s;
    }

    public BudgetState newBudget() {
        BudgetState b = new BudgetState();
        b.baselineDeficit = p.baselineDeficit();
        b.debt = p.startDebt();
        return b;
    }

    // ---- transitions accepted from other systems ----------------------------------------------

    /** Applies an economic transition. Returns false if the effect is not the economy's to apply. */
    public boolean apply(EconomyState s, BudgetState b, Effect e) {
        switch (e) {
            case Effect.EconomyShock x -> addShock(s, x.growthPoints(), x.inflationPoints(), x.months());
            case Effect.Deficit x -> b.policyDeficit += x.pctGdp();
            case Effect.FedPressure x -> {
                s.credibility = Math.max(p.credibilityFloor(), s.credibility - p.fedPressureCredibility());
                s.fedPressured = true;
            }
            case Effect.OilRelease x -> s.sprRelief = Math.max(s.sprRelief, x.dollars());
            default -> { return false; }
        }
        return true;
    }

    void addShock(EconomyState s, double growthPoints, double inflationPoints, double months) {
        if (months <= 0) return;
        s.shocks.add(new EconomyState.Shock(growthPoints / months, inflationPoints / months, months));
    }

    // ---- the step ------------------------------------------------------------------------------

    /** Advance the macro economy and markets by {@code dtMonths}. */
    public void step(EconomyState s, BudgetState b, double dtMonths, MarketContext ctx, DeterministicRandomManager rng) {
        double dt = dtMonths, rootDt = Math.sqrt(dt);
        if (rng.chance("economy.recession", 1 - Math.pow(1 - p.recessionRiskPerMonth(), dt)))
            addShock(s, p.recessionGrowth(), p.recessionInflation(), p.recessionMonths());

        double demandImpulse = 0, supplyImpulse = 0;
        for (EconomyState.Shock sh : s.shocks) {
            double used = Math.min(dt, sh.monthsLeft);
            demandImpulse += sh.growthPerMonth * used;
            supplyImpulse += sh.inflationPerMonth * used;
            sh.monthsLeft -= dt;
        }
        s.shocks.removeIf(sh -> sh.monthsLeft <= 1e-9);

        // Markets first: oil follows crises, gas follows oil.
        double oilAdjust = 1 - Math.pow(1 - p.oilAdjustPerWeek(), dt * WEEKS_PER_MONTH);
        double oilTarget = p.oilBase() + ctx.oilPremium() - s.sprRelief;
        s.oil += oilAdjust * (oilTarget - s.oil) + p.oilNoisePerWeek() * Math.sqrt(dt * WEEKS_PER_MONTH) * rng.gaussian("economy.oil");
        if (s.sprRelief > 0) s.sprRelief = Math.max(0, s.sprRelief - dt * WEEKS_PER_MONTH);
        double gas = p.gas(s.oil);

        // IS curve: the gap closes on its own, is pushed by shocks and policy, and pulled by real rates.
        double realRate = s.fedRate - s.inflation;
        double previousGap = s.outputGap;
        s.outputGap = Math.pow(p.gapPersistence(), dt) * s.outputGap + p.shockToGap() * demandImpulse
                - p.rateSensitivity() * dt * (realRate - p.neutralRealRate()) + p.gapNoise() * rootDt * rng.gaussian("economy.noise");
        double annualisedGrowth = p.potentialGrowth() + 12.0 * (s.outputGap - previousGap) / dt;
        s.growth += (1 - Math.pow(1 - p.growthSmoothing(), dt)) * (annualisedGrowth - s.growth);

        // Okun's law.
        double uTarget = p.naturalUnemployment() - p.okun() * s.outputGap;
        s.unemployment += (1 - Math.pow(1 - p.unemploymentAdjust(), dt)) * (uTarget - s.unemployment);

        // Phillips curve with anchored expectations; supply shocks and gas prices pass through.
        double expected = expectedInflation(s);
        double piTarget = expected + p.phillipsSlope() * s.outputGap + p.supplyPassThrough() * supplyImpulse / dt
                + p.gasPassThrough() * (gas - p.gas(p.oilBase()));
        s.inflation += (1 - Math.pow(1 - p.inflationAdjust(), dt)) * (piTarget - s.inflation) + p.inflationNoise() * rootDt * rng.gaussian("economy.noise");
        if (s.inflation > p.credibilityLossAbove()) s.credibility = Math.max(p.credibilityFloor(), s.credibility - p.credibilityLossRate() * dt);
        else if (s.inflation < p.credibilityGainBelow()) s.credibility = Math.min(p.credibilityCeiling(), s.credibility + p.credibilityGainRate() * dt);

        // Bonds and confidence.
        double tenTarget = p.tenYearFedWeight() * s.fedRate + (1 - p.tenYearFedWeight()) * (p.neutralRealRate() + expected) + p.termPremium()
                + p.tenYearDeficitWeight() * Math.max(0, b.policyDeficit) + p.tenYearPendingWeight() * ctx.pendingDeficit()
                + p.tenYearTreasuryWeight() * (70 - ctx.treasuryCompetence()) + p.tenYearCredibilityWeight() * (p.startCredibility() - s.credibility);
        s.tenYear += (1 - Math.pow(1 - p.tenYearAdjustPerWeek(), dt * WEEKS_PER_MONTH)) * (tenTarget - s.tenYear)
                + p.tenYearNoisePerWeek() * Math.sqrt(dt * WEEKS_PER_MONTH) * rng.gaussian("economy.bonds");
        double confTarget = 100 - p.confidenceGas() * (gas - p.gas(p.oilBase())) - p.confidenceInflation() * (s.inflation - p.startInflation()) - ctx.stress();
        s.confidence += (1 - Math.pow(1 - p.confidenceAdjustPerWeek(), dt * WEEKS_PER_MONTH)) * (confTarget - s.confidence);

        // Debt dynamics: the deficit adds, nominal growth erodes.
        b.debt += deficit(s, b) * dt / 12.0 - b.debt * (s.growth + s.inflation) / 100.0 * dt / 12.0;
    }

    public double expectedInflation(EconomyState s) {
        return s.credibility * p.inflationTarget() + (1 - s.credibility) * s.inflation;
    }

    public double deficit(EconomyState s, BudgetState b) {
        return b.baselineDeficit + b.policyDeficit - p.cyclicalDeficit() * s.outputGap
                + Math.max(0, s.fedRate - p.interestAboveRate()) * p.interestSensitivity();
    }

    public double gas(EconomyState s) { return p.gas(s.oil); }

    /**
     * A Federal Reserve meeting: a Taylor-style rule on the <em>reported</em> data, moving one step at most.
     * Public pressure narrows the threshold for a close call (and has already cost credibility).
     * @return the change in the policy rate
     */
    public double fedMeeting(EconomyState s, BudgetState b) {
        double gapEstimate = (p.naturalUnemployment() - s.reportedUnemployment) / p.okun();
        double target = p.neutralRealRate() + s.reportedInflation + p.taylorInflation() * (s.reportedInflation - p.inflationTarget())
                + 0.5 * gapEstimate + p.fedFiscalWeight() * b.policyDeficit;
        double threshold = s.fedPressured ? p.fedPressuredThreshold() : p.fedThreshold();
        double before = s.fedRate;
        if (target - s.fedRate > threshold) s.fedRate += p.fedStep();
        else if (s.fedRate - target > threshold) s.fedRate = Math.max(0, s.fedRate - p.fedStep());
        s.fedPressured = false;
        return s.fedRate - before;
    }

    /** Monthly jobs report: returns the change from the previous report. */
    public double jobsReport(EconomyState s, DeterministicRandomManager rng) {
        double was = s.reportedUnemployment;
        s.reportedUnemployment = Math.round((s.unemployment + p.releaseNoise() * rng.gaussian("economy.release")) * 10) / 10.0;
        return s.reportedUnemployment - was;
    }

    /** Monthly inflation report: returns the change from the previous report. */
    public double inflationReport(EconomyState s, DeterministicRandomManager rng) {
        double was = s.reportedInflation;
        s.reportedInflation = Math.round((s.inflation + p.releaseNoise() * rng.gaussian("economy.release")) * 10) / 10.0;
        return s.reportedInflation - was;
    }
}
