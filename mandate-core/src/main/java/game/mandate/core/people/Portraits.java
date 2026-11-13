package game.mandate.core.people;

import game.mandate.core.data.Csv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How each person the game draws looks (data/portraits.csv), keyed by the person's stable id: one source of truth for
 * every screen and both interfaces, and for saves (a save carries ids, and the look follows the id). A person with no
 * look is drawn as a neutral placeholder, never as somebody else. Fictional newcomers (from 2030) carry a look made
 * from their own id and recorded presentation, through {@link #generated}.
 */
public final class Portraits {

    /** A portrait's features. Presentation decides the clothes and which hair styles are drawn. */
    public record Look(String id, String presentation, int skin, String hair, String hairColour, String facial, boolean glasses, String face, double age) {
        public boolean masculine() { return presentation.equals("m"); }
    }

    public static final Set<String> MASCULINE_HAIR = Set.of("short", "side-part", "receding", "close", "slicked", "swept", "bald");
    public static final Set<String> FEMININE_HAIR = Set.of("bob", "shoulder", "long", "pulled-back", "close");
    public static final Set<String> COLOURS = Set.of("black", "dark", "brown", "light-brown", "blond", "salt", "grey", "white", "red", "none");
    public static final Set<String> FACIAL = Set.of("none", "stubble", "beard", "goatee", "moustache");
    public static final Set<String> FACES = Set.of("oval", "round", "long", "square");

    private final Map<String, Look> looks;

    private Portraits(Map<String, Look> looks) { this.looks = looks; }

    public static Portraits load() {
        Map<String, Look> m = new LinkedHashMap<>();
        for (String[] f : Csv.readResource("/data/portraits.csv")) {
            Look l = new Look(f[0], f[1], Integer.parseInt(f[2]), f[3], f[4], f[5], f[6].equals("1"), f[7], Double.parseDouble(f[8]));
            check(l);
            if (m.put(l.id(), l) != null) throw new IllegalStateException("Two portraits for " + l.id());
        }
        return new Portraits(Collections.unmodifiableMap(m));
    }

    /** A look is valid: its presentation, and features that belong to it (no beard on a blouse, no bob on a tie). */
    static void check(Look l) {
        String why = null;
        if (!l.presentation().equals("m") && !l.presentation().equals("f")) why = "presentation must be m or f";
        else if (l.skin() < 1 || l.skin() > 6) why = "skin must be 1-6";
        else if (!(l.masculine() ? MASCULINE_HAIR : FEMININE_HAIR).contains(l.hair())) why = "hair '" + l.hair() + "' is not drawn for presentation " + l.presentation();
        else if (!COLOURS.contains(l.hairColour())) why = "unknown hair colour " + l.hairColour();
        else if (l.hair().equals("bald") != l.hairColour().equals("none")) why = "only a bald head has no hair colour";
        else if (!FACIAL.contains(l.facial()) || (!l.masculine() && !l.facial().equals("none"))) why = "facial hair '" + l.facial() + "' does not fit";
        else if (!FACES.contains(l.face())) why = "unknown face " + l.face();
        else if (l.age() < 0 || l.age() > 1) why = "age must be 0-1";
        if (why != null) throw new IllegalStateException("Portrait " + l.id() + ": " + why);
    }

    public Optional<Look> look(String id) { return Optional.ofNullable(looks.get(id)); }
    public Map<String, Look> all() { return looks; }

    /**
     * A fictional newcomer's look, made from the person's own stable id and presentation (so it is the same on every
     * screen, in every save and both interfaces), and only from features that fit the presentation.
     */
    public static Look generated(String id, String presentation, double age) {
        long h = 1125899906842597L;
        for (int i = 0; i < id.length(); i++) h = 31 * h + id.charAt(i);
        long x = h ^ (h >>> 29);
        boolean m = presentation.equals("m");
        List<String> hair = m ? List.of("short", "side-part", "receding", "close", "slicked", "swept", "bald") : List.of("bob", "shoulder", "long", "pulled-back", "close");
        List<String> colours = List.of("black", "dark", "brown", "light-brown", "blond", "salt", "grey");
        List<String> faces = List.of("oval", "round", "long", "square");
        String style = hair.get((int) Math.floorMod(x, hair.size()));
        String colour = style.equals("bald") ? "none" : age > 0.65 ? (Math.floorMod(x >>> 7, 2) == 0 ? "grey" : "white") : colours.get((int) Math.floorMod(x >>> 9, colours.size()));
        String facial = m && Math.floorMod(x >>> 13, 6) == 0 ? (Math.floorMod(x >>> 17, 2) == 0 ? "beard" : "moustache") : "none";
        Look l = new Look(id, m ? "m" : "f", 1 + (int) Math.floorMod(x >>> 3, 6), style, colour, facial, Math.floorMod(x >>> 21, 4) == 0,
                faces.get((int) Math.floorMod(x >>> 25, faces.size())), Math.max(0, Math.min(1, age)));
        check(l);
        return l;
    }
}
