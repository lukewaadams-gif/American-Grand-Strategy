package game.mandate.core.data;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Fmt} gives exactly what {@code String.format} gives, for every pattern the core uses and many values. */
class FmtTest {
    static final String[] SPECS = {"%.0f", "%.1f", "%.2f", "%.3f", "%+.0f", "%+.1f", "%+.2f", "%f", "%5.1f", "%-6.1f", "%,.0f", "%,.2f", "%08.2f", "% .1f"};
    static final String[] INT_SPECS = {"%d", "%+d", "%5d", "%-5d", "%05d", "%,d", "% d", "%04x", "%x"};

    @Test void decimalsMatchJavaOnAwkwardValues() {
        double[] tricky = {0, -0.0, 0.05, 0.15, 0.25, 0.35, 0.45, 1.25, 2.5, -2.5, 1.005, 2.675, -0.04, -0.05, 0.5, 1.5, 9.995, 99.95, 123456.785,
                1e-7, 1e7, 1e15, 1e21, -1e21, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.1 + 0.2};
        for (String spec : SPECS) for (double v : tricky) assertEquals(String.format(spec, v), Fmt.format(spec, v), spec + " of " + v);
    }

    @Test void decimalsMatchJavaOnRandomValues() {
        Random r = new Random(7);
        for (int i = 0; i < 200_000; i++) {
            double v = switch (i % 4) {
                case 0 -> (r.nextDouble() - 0.5) * 200;
                case 1 -> Math.round((r.nextDouble() - 0.5) * 2000) / 20.0;     // many exact halves
                case 2 -> (r.nextDouble() - 0.5) * 1e6;
                default -> r.nextGaussian() * 3;
            };
            String spec = SPECS[i % SPECS.length];
            assertEquals(String.format(spec, v), Fmt.format(spec, v), spec + " of " + v);
        }
    }

    @Test void integersMatchJava() {
        Random r = new Random(11);
        long[] tricky = {0, 1, -1, 7, -7, 999, 1000, -1000, 123456789, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE};
        for (String spec : INT_SPECS) {
            for (long v : tricky) {
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) assertEquals(String.format(spec, (int) v), Fmt.format(spec, (int) v), spec + " of int " + v);
                assertEquals(String.format(spec, v), Fmt.format(spec, v), spec + " of long " + v);
            }
            for (int i = 0; i < 5000; i++) { int v = r.nextInt(); assertEquals(String.format(spec, v), Fmt.format(spec, v), spec + " of " + v); }
        }
    }

    @Test void stringsAndLiterals() {
        assertEquals(String.format("%s and %s: 50%% of %d%%n", "a", null, 3), Fmt.format("%s and %s: 50%% of %d%%n", "a", null, 3));
        assertEquals(String.format("Approval %.0f%% (from %.0f%%); %s; midterm %+d.%n", 48.5, 51.0, "x", -3), Fmt.format("Approval %.0f%% (from %.0f%%); %s; midterm %+d.%n", 48.5, 51.0, "x", -3));
        assertEquals(String.format("[%-8s|%8s|%.3s]", "ab", "cd", "abcdef"), Fmt.format("[%-8s|%8s|%.3s]", "ab", "cd", "abcdef"));
    }

    @Test void refusesWhatJavaRefuses() {
        assertThrows(IllegalArgumentException.class, () -> Fmt.format("%.1f", "text"));
        assertThrows(IllegalArgumentException.class, () -> Fmt.format("%d", 1.5));
        assertThrows(IllegalArgumentException.class, () -> Fmt.format("%s and %s", "only one"));
        assertThrows(IllegalArgumentException.class, () -> Fmt.format("%q", 1));
    }

    /** Every format string in the core, with arguments of the types its conversions take, formats as Java does. */
    @Test void everyPatternInTheCoreFormatsAsJavaDoes() throws Exception {
        Pattern call = Pattern.compile("Fmt\\.format\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Pattern conv = Pattern.compile("%[-+ 0,]*\\d*(\\.\\d+)?([a-zA-Z%])");
        List<String> patterns = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(f));
                while (m.find()) patterns.add(m.group(1).replace("\\\"", "\"").replace("\\n", "\n"));
            }
        }
        assertFalse(patterns.isEmpty(), "the core formats through Fmt");
        Random r = new Random(3);
        for (String p : patterns) {
            for (int k = 0; k < 50; k++) {
                List<Object> args = new ArrayList<>();
                Matcher m = conv.matcher(p);
                while (m.find()) {
                    switch (m.group(2)) {
                        case "f" -> args.add((r.nextDouble() - 0.5) * 100);
                        case "d", "x" -> args.add(r.nextInt(2000) - 1000);
                        case "s" -> args.add("w" + k);
                        default -> { }
                    }
                }
                assertEquals(String.format(p, args.toArray()), Fmt.format(p, args.toArray()), p);
            }
        }
        assertTrue(patterns.size() > 50, "found " + patterns.size() + " patterns");
    }
}
