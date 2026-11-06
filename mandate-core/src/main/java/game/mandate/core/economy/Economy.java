package game.mandate.core.economy;

import game.mandate.core.rng.RngStreams;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, legible macro model stepped monthly. It is a reduced form of the Resolute Desk economy
 * (output gap, Okun's law, expectations-anchored Phillips curve, Taylor-rule Fed, debt dynamics).
 * Every equation is one line so designers can reason about it and players can be told why.
 *
 * <p>Units: rates in percent; growth annualised; deficit and debt in percent of GDP.
 */
public final class Economy {
    public static final double POTENTIAL_GROWTH = 1.9;
    public static final double NATURAL_UNEMPLOYMENT = 4.3;
    public static final double INFLATION_TARGET = 2.0;
    public static final double NEUTRAL_REAL_RATE = 0.9;
    public static final double BASELINE_DEFICIT = 6.0;
    /** Monthly probability of an organic recession shock (~one per decade). */
    public static final double RECESSION_RISK = 1.0 / 110.0;

    private double outputGap;          // percent of potential output
    private double growth;             // annualised real GDP growth, smoothed
    private double unemployment;
    private double inflation;          // year-over-year CPI, smoothed
    private double fedRate;
    private double policyDeficit;      // deficit added by enacted policy, % GDP
    private double debt;
    private double credibility = 0.85; // Fed credibility: how anchored expectations are
    private final List<Shock> shocks = new ArrayList<>();

    public Economy(double outputGap, double unemployment, double inflation, double fedRate, double debt) {
        this.outputGap = outputGap;
        this.growth = POTENTIAL_GROWTH;
        this.unemployment = unemployment;
        this.inflation = inflation;
        this.fedRate = fedRate;
        this.debt = debt;
    }

    public static Economy baseline2029() { return new Economy(0.2, 4.4, 2.8, 3.5, 101.0); }

    public void addShock(double growthPoints, double inflationPoints, int months) {
        if (months <= 0) return;
        shocks.add(new Shock(growthPoints / months, inflationPoints / months, months));
    }

    public void addPolicyDeficit(double pctGdp) { policyDeficit += pctGdp; }

    /** Advance one month. */
    public void stepMonth(RngStreams rng) {
        if (rng.chance("economy.recession", RECESSION_RISK)) addShock(-5.0, -0.4, 6);

        double demandImpulse = 0, supplyImpulse = 0;
        for (Shock s : shocks) { demandImpulse += s.growthPerMonth; supplyImpulse += s.inflationPerMonth; s.monthsLeft--; }
        shocks.removeIf(s -> s.monthsLeft <= 0);

        double realRate = fedRate - inflation;
        double previousGap = outputGap;
        // IS curve: the gap closes on its own, is pushed by shocks and fiscal policy, and pulled by real rates.
        outputGap = 0.95 * outputGap + demandImpulse * 0.75 - 0.025 * (realRate - NEUTRAL_REAL_RATE)
                + 0.08 * rng.gaussian("economy.noise");
        double monthlyGrowth = POTENTIAL_GROWTH + 12.0 * (outputGap - previousGap);
        growth += 0.25 * (monthlyGrowth - growth);

        // Okun's law, with unemployment adjusting gradually.
        double targetUnemployment = NATURAL_UNEMPLOYMENT - 0.5 * outputGap;
        unemployment += 0.3 * (targetUnemployment - unemployment);

        // Phillips curve with anchored expectations.
        double expected = credibility * INFLATION_TARGET + (1 - credibility) * inflation;
        double targetInflation = expected + 0.25 * outputGap + supplyImpulse * 12.0 / 4.0;
        inflation += 0.12 * (targetInflation - inflation) + 0.04 * rng.gaussian("economy.noise");
        if (inflation > 4.0) credibility = Math.max(0.5, credibility - 0.01);
        else if (inflation < 3.0) credibility = Math.min(0.9, credibility + 0.003);

        // Taylor rule, moving at most a quarter point a month and never below zero.
        double taylor = NEUTRAL_REAL_RATE + inflation + 0.5 * (inflation - INFLATION_TARGET) + 0.5 * outputGap;
        fedRate += Math.max(-0.25, Math.min(0.25, taylor - fedRate));
        fedRate = Math.max(0, Math.round(fedRate * 4) / 4.0);

        // Debt dynamics: deficit adds, nominal growth erodes.
        double deficit = deficit();
        debt += deficit / 12.0 - debt * (growth + inflation) / 100.0 / 12.0;
    }

    public double deficit() { return BASELINE_DEFICIT + policyDeficit - 0.4 * outputGap + Math.max(0, fedRate - 3.0) * 0.3; }
    public double outputGap() { return outputGap; }
    public double growth() { return growth; }
    public double unemployment() { return unemployment; }
    public double inflation() { return inflation; }
    public double fedRate() { return fedRate; }
    public double debt() { return debt; }

    /** The "misery index" voters feel; used by approval and the epilogue. */
    public double misery() { return unemployment + inflation; }

    private static final class Shock {
        final double growthPerMonth;
        final double inflationPerMonth;
        int monthsLeft;

        Shock(double g, double i, int m) { growthPerMonth = g; inflationPerMonth = i; monthsLeft = m; }
    }
}
