package game.mandate.core.economy;

import game.mandate.core.rng.RngStreams;

/**
 * Presidential approval. Pulled toward a target set by the economy, a decaying honeymoon and
 * decaying event/policy modifiers, inside a polarised band: in the modern era few presidents leave
 * 38-58 for long, and the game respects that so no single decision feels like it ends the game.
 */
public final class ApprovalModel {
    public static final double FLOOR = 34, CEILING = 64;

    private double approval;
    private double honeymoon;
    private double modifiers; // sum of event, law and crisis effects, decaying

    public ApprovalModel(double start, double honeymoon) {
        this.approval = start;
        this.honeymoon = honeymoon;
    }

    public void add(double points) { modifiers += points; }

    public void stepMonth(Economy e, RngStreams rng) {
        honeymoon *= 0.85;
        modifiers *= 0.88;
        double economic = -1.4 * (e.unemployment() - 4.5) - 1.6 * (e.inflation() - 2.5) + 0.8 * (e.growth() - 2.0);
        double target = 46 + honeymoon + economic + modifiers;
        // Soft polarisation band: movement beyond 40/56 is compressed.
        if (target > 56) target = 56 + (target - 56) * 0.4;
        if (target < 40) target = 40 - (40 - target) * 0.4;
        approval += 0.35 * (target - approval) + 0.6 * rng.gaussian("approval");
        approval = Math.max(FLOOR, Math.min(CEILING, approval));
    }

    public double approval() { return approval; }
}
