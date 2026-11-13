package game.mandate.core.data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * {@code String.format} for the patterns the game uses, written out so it gives the same text on every platform.
 * The browser build compiles this code to JavaScript, where the runtime's own formatter rounds halves differently
 * and loses its place after {@code %%}. On the JVM the output is identical to {@code String.format} (tested by
 * {@code FmtTest}).
 *
 * <p>Supported: {@code %s}, {@code %d}, {@code %x}, {@code %f} (Java's rounding: half up on the shortest decimal
 * form of the double), {@code %%} and {@code %n}; the flags {@code -}, {@code +}, space, {@code 0} and {@code ,};
 * a width; a precision. Anything else is refused with an exception, as {@code String.format} would refuse it.
 */
public final class Fmt {
    private Fmt() {}

    public static String format(String pattern, Object... args) {
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int next = 0, i = 0, n = pattern.length();
        while (i < n) {
            char c = pattern.charAt(i);
            if (c != '%') { out.append(c); i++; continue; }
            int start = i++;
            if (i >= n) throw new IllegalArgumentException("Format ends with %: " + pattern);
            // flags
            boolean left = false, plus = false, space = false, zero = false, group = false;
            for (boolean more = true; more && i < n; ) {
                switch (pattern.charAt(i)) {
                    case '-' -> { left = true; i++; }
                    case '+' -> { plus = true; i++; }
                    case ' ' -> { space = true; i++; }
                    case '0' -> { zero = true; i++; }
                    case ',' -> { group = true; i++; }
                    default -> more = false;
                }
            }
            int width = -1;
            int w0 = i;
            while (i < n && Character.isDigit(pattern.charAt(i))) i++;
            if (i > w0) width = Integer.parseInt(pattern.substring(w0, i));
            int precision = -1;
            if (i < n && pattern.charAt(i) == '.') {
                int p0 = ++i;
                while (i < n && Character.isDigit(pattern.charAt(i))) i++;
                if (i == p0) throw new IllegalArgumentException("Missing precision in " + pattern);
                precision = Integer.parseInt(pattern.substring(p0, i));
            }
            if (i >= n) throw new IllegalArgumentException("Unfinished conversion in " + pattern);
            char conv = pattern.charAt(i++);
            String body;
            switch (conv) {
                case '%' -> { out.append('%'); continue; }
                case 'n' -> { out.append('\n'); continue; }
                case 's' -> {
                    Object a = arg(args, next++, pattern, pattern.substring(start, i));
                    body = String.valueOf(a);
                    if (precision >= 0 && body.length() > precision) body = body.substring(0, precision);
                }
                case 'd' -> body = integer(arg(args, next++, pattern, pattern.substring(start, i)), plus, space, group, pattern);
                case 'x' -> {
                    Object a = arg(args, next++, pattern, pattern.substring(start, i));
                    if (a instanceof Integer v) body = Integer.toHexString(v);
                    else if (a instanceof Long v) body = Long.toHexString(v);
                    else throw new IllegalArgumentException("Can't format " + a + " with %x in " + pattern);
                }
                case 'f' -> body = decimal(arg(args, next++, pattern, pattern.substring(start, i)), precision < 0 ? 6 : precision, plus, space, group, pattern);
                default -> throw new IllegalArgumentException("Unsupported conversion %" + conv + " in " + pattern);
            }
            boolean finite = !(body.endsWith("NaN") || body.endsWith("Infinity"));     // Java never zero-pads these
            out.append(pad(body, width, left, zero && conv != 's' && finite));
        }
        return out.toString();
    }

    static Object arg(Object[] args, int index, String pattern, String spec) {
        if (args == null || index >= args.length) throw new IllegalArgumentException("Missing argument for " + spec + " in " + pattern);
        return args[index];
    }

    static String integer(Object a, boolean plus, boolean space, boolean group, String pattern) {
        long v;
        if (a instanceof Integer x) v = x;
        else if (a instanceof Long x) v = x;
        else if (a instanceof Short x) v = x;
        else if (a instanceof Byte x) v = x;
        else throw new IllegalArgumentException("Can't format " + (a == null ? "null" : a.getClass().getSimpleName()) + " with %d in " + pattern);
        String digits = Long.toString(Math.abs(v));
        if (v == Long.MIN_VALUE) digits = "9223372036854775808";
        if (group) digits = group(digits);
        return (v < 0 ? "-" : plus ? "+" : space ? " " : "") + digits;
    }

    static String decimal(Object a, int precision, boolean plus, boolean space, boolean group, String pattern) {
        double v;
        if (a instanceof Double x) v = x;
        else if (a instanceof Float x) v = x;
        else if (a instanceof BigDecimal x) v = x.doubleValue();
        else throw new IllegalArgumentException("Can't format " + (a == null ? "null" : a.getClass().getSimpleName()) + " with %f in " + pattern);
        if (Double.isNaN(v)) return "NaN";
        boolean negative = Double.compare(v, 0.0) < 0;      // true for -0.0, as in Java's formatter
        if (Double.isInfinite(v)) return (negative ? "-" : plus ? "+" : space ? " " : "") + "Infinity";
        // Java rounds the shortest decimal that identifies the double, half up: 0.15 -> "0.2", 1.25 -> "1.3".
        String digits = new BigDecimal(Double.toString(Math.abs(v))).setScale(precision, RoundingMode.HALF_UP).toPlainString();
        if (group) {
            int dot = digits.indexOf('.');
            digits = dot < 0 ? group(digits) : group(digits.substring(0, dot)) + digits.substring(dot);
        }
        return (negative ? "-" : plus ? "+" : space ? " " : "") + digits;
    }

    static String group(String digits) {
        StringBuilder b = new StringBuilder();
        int lead = digits.length() % 3;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) b.append(',');
            b.append(digits.charAt(i));
        }
        return b.toString();
    }

    static String pad(String s, int width, boolean left, boolean zero) {
        if (width <= s.length()) return s;
        int fill = width - s.length();
        if (left) return s + " ".repeat(fill);
        if (zero) {
            int sign = !s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+' || s.charAt(0) == ' ') ? 1 : 0;
            return s.substring(0, sign) + "0".repeat(fill) + s.substring(sign);
        }
        return " ".repeat(fill) + s;
    }
}
