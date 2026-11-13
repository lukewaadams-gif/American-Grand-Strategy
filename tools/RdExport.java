/*
 * RdExport: exports Resolute Desk Version 2's content tables for the presidency of 2029-2033 as content files. A standalone
 * developer tool; it is not part of the Maven build and nothing in the game runs it. The source is only read.
 *
 * How the shipped files were produced (run from the repository root, OpenJDK 21). Resolute Desk's own build writes
 * into its working tree, so build a copy:
 *
 *   cp -r /path/to/resolutedesk /tmp/rd && (cd /tmp/rd && java tools/Build.java compile)
 *   java -Djava.awt.headless=true tools/RdExport.java /tmp/rd/build/classes mandate-core/src/main/resources/data/rd 2ce2b37
 *
 * Arguments: <compiled Resolute Desk classes> [<output directory>] [<Resolute Desk commit, written into the headers>].
 *
 * What it writes (tab-separated; '#' lines are comments; the first other line names the columns). Rows that belong to
 * other eras are left out: only what can happen between 2029 and 2033.
 *   policies.tsv       the policy table (PolicyTable.ROWS), each row in Resolute Desk's own pipe-separated layout
 *   policy_links.tsv   which policy levels each bill sets (PolicyTable.BILL_POLICY), and the voter-group codes
 *   bills.tsv          the bills a modern president can propose (BillData.BILLS_MODERN), one column per field
 *   actions.tsv        executive orders, regulations and emergencies (ActionData)
 *   crises.tsv, operations.tsv, covert.tsv   the world's crises, military operations and covert actions (CrisisData)
 *   home_events.tsv    events at home (EventData)
 *   storylines.tsv     storylines (StorylineTable.LINES): one row per storyline header or stage
 *   ambient.tsv        the era's news beyond the White House (Ambient*.ROWS)
 *   objectives.tsv     foreign-policy objectives with each country (ObjectiveTable.ROWS)
 *   countries.tsv      the 40 countries and their leaders in 2029 (WorldData)
 *   outlets.tsv        the news outlets (Outlets)
 *   names.tsv          the name pools for made-up people (Names)
 *   era_2029.tsv       the 2029 starting point (Eras: the "After Battleground 2028" era)
 *   real_names.tsv     OPT-IN ONLY: the real people and outlets the stand-ins replace (nothing loads it by default)
 *
 * Real people: Resolute Desk's 2029 world names real heads of government, congressional leaders, a Fed chair and real
 * news outlets. This game is fictional by default (RISK_REGISTER R-02, R-03): each is replaced by a made-up stand-in
 * with the same role, and the mapping is written to real_names.tsv for an opt-in real roster. Rows already made up in
 * Resolute Desk (leaders it marks with '*', and every storyline, whose names are {who}) are kept as they are.
 */

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RdExport {
    static final int FROM = 2029, TO = 2033;

    /** Real heads of government in Resolute Desk's 2029 world (WorldData.LEADER29), by country id, and their stand-ins. */
    static final String[][] LEADERS = {
            {"CN", "Xi Jinping", "Zhou Wenhai"}, {"RU", "Vladimir Putin", "Pavel Sokolov"}, {"UA", "Volodymyr Zelensky", "Andriy Kovalenko"},
            {"GB", "Andy Burnham", "Helen Ashworth"}, {"DE", "Friedrich Merz", "Jonas Ritter"}, {"IT", "Giorgia Meloni", "Chiara Benedetti"},
            {"CA", "Mark Carney", "Daniel Fortier"}, {"MX", "Claudia Sheinbaum", "Lucia Navarro"}, {"JP", "Sanae Takaichi", "Haruka Mori"},
            {"KR", "Lee Jae-myung", "Park Jun-ho"}, {"KP", "Kim Jong Un", "Kim Ju-hyok"}, {"TW", "Lai Ching-te", "Chen Wei-lun"},
            {"IN", "Narendra Modi", "Arjun Mehta"}, {"PK", "Shehbaz Sharif", "Faisal Qureshi"}, {"IL", "Benjamin Netanyahu", "Avi Rosen"},
            {"IR", "Mojtaba Khamenei", "Hossein Rahimi"}, {"SA", "Mohammed bin Salman", "Tariq bin Nasser"}, {"TR", "Recep Tayyip Erdogan", "Kemal Aydin"},
            {"EG", "Abdel Fattah el-Sisi", "Hassan Mansour"}, {"QA", "Tamim bin Hamad Al Thani", "Hamad bin Khalid"},
            {"AE", "Mohamed bin Zayed", "Zayed bin Rashid"}, {"JO", "Abdullah II", "Hashem I"}, {"IQ", "Ali al-Zaidi", "Haider Karim"},
            {"SY", "Ahmed al-Sharaa", "Omar Haddad"}, {"BR", "Luiz Inacio Lula da Silva", "Rafael Moreira"}, {"AR", "Javier Milei", "Martina Ferraro"},
            {"VE", "Delcy Rodriguez", "Carlos Ibarra"}, {"PA", "Jose Raul Mulino", "Ricardo Solis"}, {"PL", "Donald Tusk", "Marta Wisniewska"},
            {"HU", "Peter Magyar", "Gabor Szabo"}, {"DK", "Mette Frederiksen", "Sofie Lund"}, {"FI", "Alexander Stubb", "Mikko Virtanen"},
            {"AU", "Anthony Albanese", "Jack Harrington"}, {"ID", "Prabowo Subianto", "Budi Santoso"}, {"VN", "To Lam", "Nguyen Van Hai"},
            {"NG", "Bola Tinubu", "Chinedu Okeke"}, {"ZA", "Cyril Ramaphosa", "Thabo Nkosi"}};

    /** The 2029 era's Fed chair, congressional leaders and predecessor. */
    static final String[][] ERA_PEOPLE = {
            {"fedChair", "Kevin Warsh", "Evelyn Hart"}, {"speakerD", "Hakeem Jeffries", "Marcus Bell"}, {"houseLeaderD", "Hakeem Jeffries", "Marcus Bell"},
            {"speakerR", "Mike Johnson", "Dale Prescott"}, {"houseLeaderR", "Mike Johnson", "Dale Prescott"}, {"leaderD", "Chuck Schumer", "Ruth Feldman"},
            {"leaderR", "John Thune", "Wade Calhoun"}, {"predecessor", "Donald Trump", "the outgoing president"}};

    /** The modern news outlets (Outlets.OUT_NAME) and their stand-ins: the same kind of outlet, lean and audience. */
    static final String[][] OUTLETS = {
            {"The New York Times", "The Metropolitan Times"}, {"The Washington Post", "The Capitol Post"}, {"The Wall Street Journal", "The Financial Journal"},
            {"The Associated Press", "The National Wire"}, {"Reuters", "Global Wire"}, {"CNN", "Cable News One"}, {"MSNBC", "The Progress Network"},
            {"Fox News", "Liberty News Channel"}, {"NBC News", "Meridian News"}, {"CBS News", "Continental News"}, {"ABC News", "Union Broadcast News"},
            {"Politico", "The Beltway Insider"}, {"Newsmax", "Patriot Media"}, {"NPR", "Public Radio Network"},
            {"The Joe Rogan Experience", "A top long-form talk show"}, {"Pod Save America", "A progressive politics podcast"},
            {"The Ben Shapiro Show", "A conservative commentary show"}, {"60 Minutes", "Sunday Hour"}};

    private final ClassLoader loader;
    private final String commit;
    private final List<String[]> real = new ArrayList<>();

    private RdExport(Path classes, String commit) throws Exception {
        this.loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, RdExport.class.getClassLoader());
        this.commit = commit;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java -Djava.awt.headless=true tools/RdExport.java <rd-classes> [<out-dir>] [<rd-commit>]");
            System.exit(2);
        }
        System.setProperty("java.awt.headless", "true");
        Path out = Path.of(args.length > 1 ? args[1] : "mandate-core/src/main/resources/data/rd");
        RdExport x = new RdExport(Path.of(args[0]), args.length > 2 ? args[2] : "unrecorded");
        Files.createDirectories(out);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("policies.tsv", x.specRows(out.resolve("policies.tsv"), "policy.PolicyTable", "ROWS", false,
                "The policy table: id|area|name|levels;...|level by year|channels|... in Resolute Desk's PolicyTable layout (see PolicyDef)."));
        counts.put("policy_links.tsv", x.policyLinks(out.resolve("policy_links.tsv")));
        counts.put("bills.tsv", x.bills(out.resolve("bills.tsv")));
        counts.put("actions.tsv", x.parallel(out.resolve("actions.tsv"), "executive.ActionData", "AD_MIN",
                "AD_ID", "AD_KIND", "AD_MIN", "AD_NAME", "AD_DESC", "AD_AREA", "AD_LEAN", "AD_SUP", "AD_LEGAL", "AD_NEEDS", "AD_FX"));
        counts.put("crises.tsv", x.parallel(out.resolve("crises.tsv"), "world.CrisisData", null, "CR_ID", "CR_COND", "CR_COOL", "CR_TITLE", "CR_BODY", "CR_OPTS"));
        counts.put("operations.tsv", x.parallel(out.resolve("operations.tsv"), "world.CrisisData", null, "OP_NAME", "OP_DESC", "OP_COND", "OP_FX"));
        counts.put("covert.tsv", x.parallel(out.resolve("covert.tsv"), "world.CrisisData", null, "CV_NAME", "CV_DESC", "CV_ODDS", "CV_EXPOSE", "CV_FX", "CV_EXPFX"));
        counts.put("home_events.tsv", x.parallel(out.resolve("home_events.tsv"), "home.EventData", "EV_MIN",
                "EV_ID", "EV_COND", "EV_MIN", "EV_COOL", "EV_TITLE", "EV_BODY", "EV_OPTS"));
        counts.put("storylines.tsv", x.storylines(out.resolve("storylines.tsv")));
        counts.put("ambient.tsv", x.ambient(out.resolve("ambient.tsv")));
        counts.put("objectives.tsv", x.specRows(out.resolve("objectives.tsv"), "world.ObjectiveTable", "ROWS", true,
                "Foreign-policy objectives: id|country|from|to|name|description|effects (ObjectiveTable)."));
        counts.put("countries.tsv", x.countries(out.resolve("countries.tsv")));
        counts.put("outlets.tsv", x.outlets(out.resolve("outlets.tsv")));
        counts.put("names.tsv", x.names(out.resolve("names.tsv")));
        counts.put("era_2029.tsv", x.era(out.resolve("era_2029.tsv")));
        counts.put("real_names.tsv", x.realNames(out.resolve("real_names.tsv")));
        counts.forEach((k, v) -> System.out.printf(Locale.ROOT, "%-18s %4d rows%n", k, v));
    }

    // ---------------------------------------------------------------------------------------------- the tables

    /** A table of Resolute Desk row specs ("a|b|c..."), one per line; with {@code years}, fields 2 and 3 are its first and last year. */
    private int specRows(Path file, String cls, String field, boolean years, String what) throws Exception {
        String[] rows = (String[]) stat(cls, field);
        List<String[]> out = new ArrayList<>();
        for (String r : rows) {
            if (years) {
                String[] t = r.split("\\|", -1);
                if (!overlaps(Integer.parseInt(t[2]), Integer.parseInt(t[3]))) continue;
            }
            out.add(new String[]{r});
        }
        write(file, what, new String[]{"spec"}, out);
        return out.size();
    }

    private int policyLinks(Path file) throws Exception {
        List<String[]> out = new ArrayList<>();
        for (String r : (String[]) stat("policy.PolicyTable", "BILL_POLICY")) {
            int sp = r.indexOf(' ');
            out.add(new String[]{"bill", sp < 0 ? r : r.substring(0, sp), sp < 0 ? "" : r.substring(sp + 1)});
        }
        String[] groups = (String[]) stat("policy.PolicyTable", "GROUP_CODE");
        for (int i = 0; i < groups.length; i++) out.add(new String[]{"group", Integer.toString(i), groups[i]});
        write(file, "Which policy levels each bill sets (kind=bill: bill id, policy=level ...), and the voter-group codes the policy table uses (kind=group: index, code).",
                new String[]{"kind", "key", "value"}, out);
        return out.size();
    }

    /** BillData.BILLS_MODERN: every instance field of each BillDef, in declaration order. */
    private int bills(Path file) throws Exception {
        Object[] defs = (Object[]) stat("congress.BillData", "BILLS_MODERN");
        List<Field> fields = new ArrayList<>();
        for (Field f : defs[0].getClass().getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); fields.add(f); }
        String[] head = fields.stream().map(Field::getName).toArray(String[]::new);
        List<String[]> out = new ArrayList<>();
        for (Object d : defs) {
            int from = (int) get(d, "from"), to = (int) get(d, "to");
            if (!overlaps(from, to)) continue;
            String[] row = new String[fields.size()];
            for (int i = 0; i < row.length; i++) row[i] = str(fields.get(i).get(d));
            out.add(row);
        }
        write(file, "The bills a president can propose in this era (BillData.BILLS_MODERN, one column per BillDef field).", head, out);
        return out.size();
    }

    /** Parallel arrays of one class, one row per index; {@code minYear} (optional) is a column of first years. */
    private int parallel(Path file, String cls, String minYear, String... fields) throws Exception {
        Object[] arrays = new Object[fields.length];
        int n = -1;
        for (int i = 0; i < fields.length; i++) {
            arrays[i] = stat(cls, fields[i]);
            int len = Array.getLength(arrays[i]);
            if (n >= 0 && len != n) throw new IllegalStateException(cls + "." + fields[i] + " has " + len + " entries, expected " + n);
            n = len;
        }
        int[] years = minYear == null ? null : (int[]) stat(cls, minYear);
        List<String[]> out = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            if (years != null && years[r] > TO) continue;
            String[] row = new String[fields.length];
            for (int i = 0; i < fields.length; i++) row[i] = str(Array.get(arrays[i], r));
            out.add(row);
        }
        write(file, "Resolute Desk's " + cls + " (parallel arrays, one row per entry).", fields, out);
        return out.size();
    }

    /** StorylineTable.LINES: a storyline's header (id|from|to|trigger|fuel|...) and its stages, kept for storylines alive in 2029-2033. */
    private int storylines(Path file) throws Exception {
        String[][] lines = (String[][]) stat("stories.StorylineTable", "LINES");
        List<String[]> out = new ArrayList<>();
        for (String[] l : lines) {
            String[] t = l[0].split("\\|", -1);
            if (!overlaps(Integer.parseInt(t[1]), Integer.parseInt(t[2]))) continue;
            for (int s = 0; s < l.length; s++) out.add(new String[]{t[0], Integer.toString(s), l[s]});
        }
        write(file, "Storylines (StorylineTable.LINES; layout in StorylineDef): stage 0 is the storyline's header, the rest its stages. Every name in them is made up ({who}).",
                new String[]{"storyline", "stage", "spec"}, out);
        return out.size();
    }

    /** The era's news beyond the White House: rows "from|to|conditions|section|headline|body|effects" alive in 2029-2033. */
    private int ambient(Path file) throws Exception {
        List<String[]> out = new ArrayList<>();
        for (String cls : new String[]{"AmbientEarly", "AmbientMiddle", "AmbientRecent", "AmbientAny"}) {
            for (String r : (String[]) stat("stories." + cls, "ROWS")) {
                String[] t = r.split("\\|", -1);
                if (!overlaps(Integer.parseInt(t[0]), Integer.parseInt(t[1]))) continue;
                out.add(new String[]{cls, r});
            }
        }
        write(file, "The era's news beyond the White House (stories.Ambient*.ROWS): from|to|conditions|section|headline|body|effects.",
                new String[]{"table", "spec"}, out);
        return out.size();
    }

    private int countries(Path file) throws Exception {
        String c = "world.WorldData";
        String[] id = (String[]) stat(c, "CT_ID"), name = (String[]) stat(c, "CT_NAME"), region = (String[]) stat(c, "REGION");
        String[] leader = (String[]) stat(c, "LEADER29"), align = (String[]) stat(c, "ALIGN"), flag = (String[]) stat(c, "CT_FLAG");
        int[] reg = (int[]) stat(c, "CT_REGION"), al = (int[]) stat(c, "CT_ALIGN");
        double[] rel = (double[]) stat(c, "CT_REL"), power = (double[]) stat(c, "CT_POWER"), trade = (double[]) stat(c, "CT_TRADE"), bal = (double[]) stat(c, "CT_BAL");
        double[] lx = (double[]) stat(c, "CT_LX"), ly = (double[]) stat(c, "CT_LY");
        boolean[] nuke = (boolean[]) stat(c, "CT_NUKE");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < id.length; i++) {
            String[] lt = leader[i].split("\\|", -1);
            String who = lt[0];
            if (who.startsWith("*")) who = who.substring(1);                 // made up in Resolute Desk already
            else who = standIn(id[i], who, "leader of " + name[i]);
            out.add(new String[]{id[i], name[i], region[reg[i]], align[al[i]], str(rel[i]), str(power[i]), str(trade[i]), str(bal[i]), str(nuke[i]),
                    str(lx[i]), str(ly[i]), flag[i], who, lt.length > 1 ? lt[1] : ""});
        }
        write(file, "The 40 countries (WorldData): relations -100..100, alignment, power 1-10, trade and balance, nuclear, map position, flag in Resolute Desk's "
                        + "flag language, and the head of government in 2029 (a made-up stand-in for each real person; see real_names.tsv).",
                new String[]{"id", "name", "region", "alignment", "relations", "power", "trade", "balance", "nuclear", "map_x", "map_y", "flag", "leader", "title"}, out);
        return out.size();
    }

    private String standIn(String key, String realName, String role) {
        for (String[] l : LEADERS) if (l[0].equals(key) && l[1].equals(realName)) { real.add(new String[]{role, l[2], realName}); return l[2]; }
        throw new IllegalStateException("No stand-in for " + realName + " (" + key + "): add one to LEADERS");
    }

    private int outlets(Path file) throws Exception {
        String c = "press.Outlets";
        String[] name = (String[]) stat(c, "OUT_NAME");
        double[] lean = (double[]) stat(c, "OUT_LEAN"), aud = (double[]) stat(c, "OUT_AUD");
        boolean[] sit = (boolean[]) stat(c, "OUT_SIT");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < name.length; i++) {
            String n = null;
            for (String[] o : OUTLETS) if (o[0].equals(name[i])) n = o[1];
            if (n == null) throw new IllegalStateException("No stand-in for outlet " + name[i]);
            real.add(new String[]{"news outlet", n, name[i]});
            out.add(new String[]{n, str(lean[i]), str(aud[i]), str(sit[i])});
        }
        write(file, "The news outlets (Outlets): lean -1 left .. +1 right, audience (millions), whether it does sit-down interviews. Stand-ins for real outlets.",
                new String[]{"name", "lean", "audience", "interviews"}, out);
        return out.size();
    }

    private int names(Path file) throws Exception {
        List<String[]> out = new ArrayList<>();
        for (String f : new String[]{"FIRST_M", "FIRST_F", "LASTS", "MORE_LASTS", "WIDER_LASTS"})
            for (String n : (String[]) stat("data.Names", f)) out.add(new String[]{f.toLowerCase(Locale.ROOT), n});
        write(file, "Name pools for made-up people: members of Congress, staff, prospects, judges (Names).", new String[]{"pool", "name"}, out);
        return out.size();
    }

    /** The 2029 era (Eras): its economy, Congress, issues and people, with stand-ins for the real people. */
    private int era(Path file) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> eras = (List<Object>) stat("data.Eras", "ERAS");
        Object e = null;
        for (Object x : eras) if ((int) get(x, "year") == FROM) e = x;
        if (e == null) throw new IllegalStateException("No 2029 era");
        List<String[]> out = new ArrayList<>();
        for (Field f : e.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            String v = str(f.get(e));
            for (String[] p : ERA_PEOPLE) if (p[0].equals(f.getName()) && p[1].equals(v)) {
                v = p[2];
                if (real.stream().noneMatch(r -> r[1].equals(p[2]))) real.add(new String[]{f.getName(), p[2], p[1]});
            }
            out.add(new String[]{f.getName(), v});
        }
        write(file, "The starting point of January 2029 (Eras, \"After Battleground 2028\"): economy, Congress, issue weights, Senate seats up at the "
                + "midterm, and the people in charge (stand-ins for real people).", new String[]{"field", "value"}, out);
        return out.size();
    }

    private int realNames(Path file) throws Exception {
        write(file, "OPT-IN REAL ROSTER. NOT FOR COMMERCIAL RELEASE (RISK_REGISTER.md R-02). Nothing loads this file by default. The real people and outlets "
                + "Resolute Desk names in 2029, and the stand-in used for each.", new String[]{"role", "stand_in", "real"}, real);
        return real.size();
    }

    // ---------------------------------------------------------------------------------------------- helpers

    static boolean overlaps(int from, int to) { return from <= TO && to >= FROM; }

    private void write(Path file, String what, String[] head, List<String[]> rows) throws Exception {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("# " + what);
            w.println("# Exported by tools/RdExport.java from Resolute Desk commit " + commit + ". Do not edit by hand: re-run the tool.");
            w.println("# Tab-separated. Only what can happen between " + FROM + " and " + TO + " is kept.");
            w.println(String.join("\t", head));
            for (String[] r : rows) {
                for (String f : r) if (f.indexOf('\t') >= 0 || f.indexOf('\n') >= 0) throw new IllegalStateException(file + ": tab or newline in " + f);
                w.println(String.join("\t", r));
            }
        }
    }

    private Object stat(String cls, String field) throws Exception {
        Class<?> c = Class.forName("resolutedesk." + cls, true, loader);
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(null);
    }

    private static Object get(Object o, String field) throws Exception {
        Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(o);
    }

    private static String str(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue()) : Double.toString(d);
        return String.valueOf(v);
    }
}
