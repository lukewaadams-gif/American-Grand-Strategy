/*
 * BgStateTraits: exports the per-state shares Battleground 2028's electorate uses to place its overlay groups (rural,
 * urban, union households, veteran households, gun owners, LGBTQ adults) as state_traits.csv. Resolute Desk's voter
 * groups (and Battleground's) are built from the 96 cells of each state plus these overlays; the cells are already in
 * electorate_cells.csv. A standalone developer tool, like BgExport; nothing in the game runs it.
 *
 *   javac -encoding UTF-8 -nowarn -d /tmp/bg-classes /path/to/Battleground2028/Battleground2028.java
 *   java tools/BgStateTraits.java /tmp/bg-classes mandate-core/src/main/resources/data b678c752db24a711b78b821af603302b26beeeba
 */

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class BgStateTraits {
    public static void main(String[] a) throws Exception {
        Path classes = Path.of(a[0]), out = Path.of(a.length > 1 ? a[1] : "mandate-core/src/main/resources/data");
        String commit = a.length > 2 ? a[2] : "unknown";
        try (URLClassLoader cl = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null)) {
            Class<?> main = Class.forName("Battleground2028", true, cl);
            Field f = main.getDeclaredField("STATES");
            f.setAccessible(true);
            Object[] states = (Object[]) f.get(null);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("state_traits.csv")))) {
                w.println("# code,rural,urban,union,vet,gun,lgbt");
                w.println("# Battleground 2028's per-state shares, percent (StateDef rural, urban, union, vet, gun, lgbt), exported by");
                w.println("# tools/BgStateTraits.java from BG commit " + commit + ". Resolute Desk's overlay voter groups are placed with them.");
                w.println("# Do not edit by hand: re-run the tool.");
                for (Object s : states) {
                    w.println(String.join(",", (String) field(s, "ab"), num(field(s, "rural")), num(field(s, "urban")), num(field(s, "union")),
                            num(field(s, "vet")), num(field(s, "gun")), num(field(s, "lgbt"))));
                }
            }
            System.out.println("state_traits.csv: " + states.length + " states");
        }
    }

    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static String num(Object v) { return String.format(Locale.ROOT, "%.2f", (Double) v); }
}
