package game.mandate.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal input and output for the game shell. Every prompt has a default taken on Enter, so a new player can
 * press Enter through any screen and a test can drive the whole game with a script. End of input ends the session
 * cleanly ({@link Closed}).
 */
final class Console {
    static final int WIDTH = 96;

    /** The input ended: the shell saves and exits. */
    static final class Closed extends RuntimeException {
        Closed() { super("input closed", null, false, false); }
    }

    private final BufferedReader in;
    final PrintStream out;

    Console(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    String line() {
        try {
            String s = in.readLine();
            if (s == null) throw new Closed();
            return s.strip();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** A numbered menu; returns the chosen index (0-based). Enter takes {@code defaultIndex}. */
    int menu(String title, List<String> options, int defaultIndex) {
        if (title != null && !title.isEmpty()) out.println(title);
        for (int i = 0; i < options.size(); i++) out.printf("  %2d. %s%n", i + 1, options.get(i));
        return choose(options.size(), defaultIndex);
    }

    /** Reads a choice 1..n (Enter = default). */
    int choose(int n, int defaultIndex) {
        while (true) {
            out.print("  > [" + (defaultIndex + 1) + "] ");
            out.flush();
            String s = line();
            if (s.isEmpty()) return defaultIndex;
            try {
                int k = Integer.parseInt(s);
                if (k >= 1 && k <= n) return k - 1;
            } catch (NumberFormatException ignored) { }
            out.println("  Choose 1 to " + n + ", or press Enter for " + (defaultIndex + 1) + ".");
        }
    }

    int number(String prompt, int min, int max, int def) {
        while (true) {
            out.print("  " + prompt + " (" + min + "-" + max + ") [" + def + "] ");
            out.flush();
            String s = line();
            if (s.isEmpty()) return def;
            try {
                int k = Integer.parseInt(s);
                if (k >= min && k <= max) return k;
            } catch (NumberFormatException ignored) { }
        }
    }

    String text(String prompt, String def) {
        out.print("  " + prompt + " [" + def + "] ");
        out.flush();
        String s = line();
        return s.isEmpty() ? def : s;
    }

    void pause() {
        out.print("  (Enter to continue) ");
        out.flush();
        line();
    }

    void rule(String title) {
        String t = "== " + title + " ";
        out.println();
        out.println(t + "=".repeat(Math.max(3, WIDTH - t.length())));
    }

    /** Prints {@code text} wrapped to the console width, every line after the first indented. */
    void wrap(String prefix, String text) {
        for (String l : wrapLines(text, WIDTH - prefix.length())) {
            out.println(prefix + l);
            prefix = " ".repeat(prefix.length());
        }
    }

    static List<String> wrapLines(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (String word : text.split(" ")) {
            if (b.length() > 0 && b.length() + 1 + word.length() > width) { lines.add(b.toString()); b.setLength(0); }
            if (b.length() > 0) b.append(' ');
            b.append(word);
        }
        if (b.length() > 0) lines.add(b.toString());
        return lines;
    }

    /** A small text sparkline of a series, for poll and approval trends. */
    static String spark(List<Double> values) {
        if (values.isEmpty()) return "";
        String ticks = "_.-~^";
        double lo = values.stream().mapToDouble(Double::doubleValue).min().orElse(0), hi = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        StringBuilder b = new StringBuilder();
        for (double v : values) b.append(ticks.charAt(hi - lo < 1e-9 ? 2 : (int) Math.round((v - lo) / (hi - lo) * (ticks.length() - 1))));
        return b.toString();
    }
}
