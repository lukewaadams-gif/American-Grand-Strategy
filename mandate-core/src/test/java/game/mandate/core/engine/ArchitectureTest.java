package game.mandate.core.engine;

import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.ElectionState;
import game.mandate.core.congress.CongressState;
import game.mandate.core.congress.PolicyState;
import game.mandate.core.diplomacy.DiplomacyState;
import game.mandate.core.economy.BudgetState;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.executive.GovernmentState;
import game.mandate.core.military.MilitaryState;
import game.mandate.core.military.OperationState;
import game.mandate.core.politics.PartyState;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.presidency.PresidentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ownership boundaries, enforced: these tests fail if a system can write another system's state. */
class ArchitectureTest {
    static final List<Class<?>> DOMAIN_STATES = List.of(EconomyState.class, BudgetState.class, PoliticsState.class, PartyState.class,
            CongressState.class, PolicyState.class, GovernmentState.class, DiplomacyState.class, MilitaryState.class, OperationState.class,
            ElectionState.class, CampaignSide.class, WorldState.class, PresidentState.class,
            game.mandate.core.policy.PolicyTableState.class, game.mandate.core.hill.HillState.class, game.mandate.core.westwing.WestWingState.class,
            game.mandate.core.terms.TermsState.class);

    /** Domain states expose readers only. Writes go through their owning engine (same package) or a transition. */
    @Test
    void domainStatesHaveNoPublicMutators() {
        List<String> bad = new ArrayList<>();
        for (Class<?> c : DOMAIN_STATES) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                if (m.getReturnType() == void.class) bad.add(c.getSimpleName() + "." + m.getName());
            }
            for (Field f : c.getDeclaredFields())
                if (Modifier.isPublic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) bad.add(c.getSimpleName() + "." + f.getName());
        }
        assertTrue(bad.isEmpty(), "public mutators on domain state: " + bad);
    }

    static final Set<String> DOMAINS = Set.of("economy", "politics", "congress", "executive", "diplomacy", "military", "campaign", "presidency", "policy", "hill", "westwing", "terms");

    /** No domain imports another domain's state or engine: cross-system effects must be transitions routed by the game engine. */
    @Test
    void domainsDoNotReachIntoEachOther() throws IOException {
        Pattern imp = Pattern.compile("import game\\.mandate\\.core\\.(\\w+)\\.(\\w+);");
        List<String> bad = new ArrayList<>();
        Path root = Path.of("src/main/java/game/mandate/core");
        for (String domain : DOMAINS) {
            try (Stream<Path> files = Files.list(root.resolve(domain))) {
                for (Path f : files.toList()) {
                    Matcher m = imp.matcher(Files.readString(f));
                    while (m.find()) {
                        String pkg = m.group(1), cls = m.group(2);
                        if (pkg.equals(domain) || !DOMAINS.contains(pkg)) continue;
                        if ((cls.endsWith("State") && !cls.equals("UsState")) || cls.endsWith("Engine"))
                            bad.add(domain + "/" + f.getFileName() + " imports " + pkg + "." + cls);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "cross-domain reach: " + bad);
    }

    /** Saves are hashed for the golden worlds: a map copied from Map.of takes its random iteration order into the JSON. */
    @Test
    void savesHaveAFixedKeyOrder() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList())
                if (Files.readString(f).matches("(?s).*new (Linked)?HashMap<[^>]*>\\((Map|Set)\\.of\\(.*")) bad.add(f.getFileName().toString());
        }
        assertTrue(bad.isEmpty(), "maps copied from Map.of: " + bad);
    }

    /** No mutable static state in the simulation (the source games' main architectural defect), except content caches. */
    @Test
    void noMutableStaticState() throws IOException {
        Pattern field = Pattern.compile("^\\s*(?:private |public |protected )?static (?!final)(?!class)(?!synchronized)(?![\\w<>\\[\\], ]+\\()[\\w<>\\[\\], ]+ \\w+\\s*[;=]", Pattern.MULTILINE);
        Set<String> contentCaches = Set.of("Bloc.java", "NomineeType.java", "Tuning.java");
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (contentCaches.contains(f.getFileName().toString())) continue;
                Matcher m = field.matcher(Files.readString(f));
                while (m.find()) bad.add(f.getFileName() + ": " + m.group().strip());
            }
        }
        assertTrue(bad.isEmpty(), "mutable statics: " + bad);
    }

    /** Every data file the core reads is embedded in the browser build (TeaVM's resources), or the browser fails at load. */
    @Test
    void everyDataFileIsEmbeddedForTheBrowser() throws IOException {
        Path resources = Path.of("../mandate-web/src/main/java/game/mandate/web/DataResources.java");
        if (!Files.exists(resources)) return;
        String embedded = Files.readString(resources);
        Pattern data = Pattern.compile("\"/data/([A-Za-z0-9_/.]+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = data.matcher(Files.readString(f));
                while (m.find()) if (!embedded.contains("\"" + m.group(1) + "\"")) missing.add(m.group(1) + " (" + f.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "not embedded for the browser: " + missing);
    }
}
