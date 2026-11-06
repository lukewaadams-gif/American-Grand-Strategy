package game.mandate.core.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON codec for save files and content: objects become {@code LinkedHashMap<String,Object>},
 * arrays {@code List<Object>}, numbers {@code Double} or {@code Long}, plus strings, booleans and null.
 * Doubles are written with {@link Double#toString} (round-trip exact), so a reloaded save is bit-identical.
 * Long values that must survive exactly (random states) should be written as strings.
 */
public final class Json {
    private Json() {}

    // ---- writing ------------------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value, 0);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v, int indent) {
        if (v == null) b.append("null");
        else if (v instanceof String s) quote(b, s);
        else if (v instanceof Boolean x) b.append(x);
        else if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) throw new IllegalArgumentException("JSON cannot hold " + d);
            b.append(d);
        } else if (v instanceof Float || v instanceof Integer || v instanceof Long || v instanceof Short) b.append(v);
        else if (v instanceof Enum<?> e) quote(b, e.name());
        else if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                newline(b, indent + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), indent + 1);
            }
            if (!m.isEmpty()) newline(b, indent);
            b.append('}');
        } else if (v instanceof Iterable<?> it) {
            b.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) b.append(", ");
                first = false;
                write(b, o, indent + 1);
            }
            b.append(']');
        } else throw new IllegalArgumentException("Cannot write " + v.getClass());
    }

    private static void newline(StringBuilder b, int indent) {
        b.append('\n');
        for (int i = 0; i < indent; i++) b.append("  ");
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }

    // ---- reading ------------------------------------------------------------------------------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw p.error("Trailing characters");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) { return (List<Object>) o; }

    public static double num(Object o) { return ((Number) o).doubleValue(); }

    public static int integer(Object o) { return ((Number) o).intValue(); }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        Object value() {
            ws();
            if (i >= s.length()) throw error("Unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> obj();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> lit("true", Boolean.TRUE);
                case 'f' -> lit("false", Boolean.FALSE);
                case 'n' -> lit("null", null);
                default -> number();
            };
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek('}')) { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                if (peek(',')) { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek(']')) { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (peek(',')) { i++; continue; }
                expect(']');
                return l;
            }
        }

        String str() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw error("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else b.append(c);
            }
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw error("Unexpected character");
            boolean real = t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0;
            return real ? (Object) Double.parseDouble(t) : (Object) Long.parseLong(t);
        }

        Object lit(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("Expected " + word);
            i += word.length();
            return v;
        }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        void expect(char c) { if (!peek(c)) throw error("Expected '" + c + "'"); i++; }
        IllegalArgumentException error(String m) { return new IllegalArgumentException(m + " at character " + i); }
    }
}
