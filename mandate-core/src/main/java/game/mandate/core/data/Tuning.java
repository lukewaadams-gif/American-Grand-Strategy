package game.mandate.core.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Balancing constants, loaded from {@code /data/tuning.properties}. This file is the single source of every
 * tunable number: code never carries a default of its own, so a missing key is an error rather than a silent
 * second copy of the formula's constant. Mods and scenarios can override any key ({@link #withOverrides}).
 *
 * <p>Keys read during a session are tracked, so a test can assert that the file holds no dead constants.
 */
public final class Tuning {
    public static final String RESOURCE = "/data/tuning.properties";

    private final Map<String, String> values;
    private final Set<String> read = Collections.synchronizedSet(new TreeSet<>());

    private Tuning(Map<String, String> values) { this.values = Collections.unmodifiableMap(values); }

    private static Tuning defaults;

    /** The shipped balance. Cached; immutable. */
    public static synchronized Tuning defaults() {
        if (defaults == null) defaults = load(Tuning.class.getResourceAsStream(RESOURCE), RESOURCE);
        return defaults;
    }

    public static Tuning fromFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) { return load(in, file.toString()); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static Tuning load(InputStream in, String source) {
        if (in == null) throw new IllegalStateException("Missing tuning file " + source);
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        Map<String, String> m = new TreeMap<>();
        for (String k : p.stringPropertyNames()) m.put(k, p.getProperty(k).strip());
        return new Tuning(m);
    }

    /** A copy with some keys replaced. Unknown keys are rejected so a typo cannot silently do nothing. */
    public Tuning withOverrides(Map<String, String> overrides) {
        Map<String, String> m = new TreeMap<>(values);
        for (var e : overrides.entrySet()) {
            if (!m.containsKey(e.getKey())) throw new IllegalArgumentException("Unknown tuning key " + e.getKey());
            m.put(e.getKey(), e.getValue());
        }
        return new Tuning(m);
    }

    public double d(String key) {
        String v = values.get(key);
        if (v == null) throw new IllegalStateException("Missing tuning key " + key);
        read.add(key);
        return Double.parseDouble(v);
    }

    public int i(String key) { return (int) Math.round(d(key)); }

    public Set<String> keys() { return values.keySet(); }

    public Set<String> keysRead() { synchronized (read) { return Set.copyOf(read); } }

    public Map<String, String> asMap() { return values; }
}
