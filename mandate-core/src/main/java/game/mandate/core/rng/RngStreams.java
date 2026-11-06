package game.mandate.core.rng;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic, named random streams derived from one master seed.
 *
 * <p>Every subsystem draws from its own stream ("economy", "congress", "events" ...). Adding a
 * random draw to one system therefore never changes the outcomes of another, which keeps replays,
 * balance batches and regression tests stable as content grows. This fixes a recurring problem in
 * the source games, where a single shared {@code java.util.Random} couples every system.
 */
public final class RngStreams {
    private final long masterSeed;
    private final Map<String, SplittableRandom> streams = new HashMap<>();

    public RngStreams(long masterSeed) { this.masterSeed = masterSeed; }

    public long masterSeed() { return masterSeed; }

    public SplittableRandom stream(String name) {
        return streams.computeIfAbsent(name, n -> new SplittableRandom(mix(masterSeed, n)));
    }

    /** Normally distributed draw (Box-Muller); kept here so systems never reach for Math.random(). */
    public double gaussian(String name) {
        SplittableRandom r = stream(name);
        double u1 = Math.max(1e-12, r.nextDouble());
        double u2 = r.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    public double uniform(String name) { return stream(name).nextDouble(); }

    public boolean chance(String name, double probability) { return stream(name).nextDouble() < probability; }

    private static long mix(long seed, String name) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < name.length(); i++) {
            h ^= name.charAt(i);
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 31;
        }
        return h;
    }
}
