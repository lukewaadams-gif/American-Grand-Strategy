package game.mandate.core.rng;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic, named random streams derived from one master seed. (Formerly {@code DeterministicRandomManager}.)
 *
 * <p>Each subsystem draws from its own stream ("economy", "congress", "events.<id>" ...), so adding a draw in
 * one system never shifts another. Each stream is a SplitMix64 generator whose entire state is one {@code long},
 * which makes the random state trivially saveable: {@link #snapshot()} / {@link #restore(Map)} let a save file
 * reproduce exactly the same future draws.
 */
public final class DeterministicRandomManager {
    private final long masterSeed;
    private final Map<String, long[]> states = new LinkedHashMap<>();

    public DeterministicRandomManager(long masterSeed) { this.masterSeed = masterSeed; }

    public long masterSeed() { return masterSeed; }

    /** Next raw 64-bit value from a stream (SplitMix64). */
    public long nextLong(String name) {
        long[] s = states.computeIfAbsent(name, n -> new long[]{mix(masterSeed, n)});
        long z = (s[0] += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Moves one stream to a state of its own for this salt: a run that differs, and still replays (the salt is in the command). */
    public void perturb(String name, long salt) {
        long[] s = states.computeIfAbsent(name, n -> new long[]{mix(masterSeed, n)});
        s[0] ^= mix(salt, name);
    }

    /** Uniform in [0, 1). */
    public double uniform(String name) { return (nextLong(name) >>> 11) * 0x1.0p-53; }

    public boolean chance(String name, double probability) { return uniform(name) < probability; }

    /** Uniform integer in [0, bound). */
    public int nextInt(String name, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) ((nextLong(name) >>> 1) % bound);
    }

    /** Standard normal draw (Box-Muller); systems never reach for Math.random(). */
    public double gaussian(String name) {
        double u1 = Math.max(1e-12, uniform(name));
        double u2 = uniform(name);
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /** The complete random state: one long per stream that has been used. */
    public Map<String, Long> snapshot() {
        Map<String, Long> m = new LinkedHashMap<>();
        states.forEach((k, v) -> m.put(k, v[0]));
        return Collections.unmodifiableMap(m);
    }

    /** Replace the random state with a snapshot taken from a manager with the same master seed. */
    public void restore(Map<String, Long> snapshot) {
        states.clear();
        snapshot.forEach((k, v) -> states.put(k, new long[]{v}));
    }

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
