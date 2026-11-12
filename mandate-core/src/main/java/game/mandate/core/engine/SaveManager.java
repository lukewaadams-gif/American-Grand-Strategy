package game.mandate.core.engine;

import game.mandate.core.command.Command;
import game.mandate.core.data.Json;
import game.mandate.core.campaign.Candidate;
import game.mandate.core.game.GameSetup;
import game.mandate.core.game.Scenario;
import game.mandate.core.state.Party;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Saves and loads games as JSON.
 *
 * <ul>
 * <li><b>Version 2</b> (written by default): the setup, the full {@link WorldState} snapshot, the random state of
 * every stream, and the command log. Loading restores the snapshot directly, so the future is identical to the
 * original game's under the same commands, without replaying the past.</li>
 * <li><b>Version 1</b> (the foundation's format, ADR-3): setup plus command log. Still readable: it is loaded by
 * replay, which reproduces the game exactly as long as the rules have not changed.</li>
 * </ul>
 * The rules fingerprint (a hash of the tuning file) is stored so a loader can warn when a version-1 replay would
 * run under different rules. Java serialization is deliberately not used (see docs/SOURCE_AUDIT.md).
 */
public final class SaveManager {
    public static final String FORMAT = "mandate-save";
    public static final int VERSION = 2;

    private SaveManager() {}

    public static String save(GameEngine g) {
        Map<String, Object> m = header(g, VERSION);
        m.put("world", g.state().toJson());
        Map<String, Object> rng = new LinkedHashMap<>();
        new TreeMap<>(g.random().snapshot()).forEach((k, v) -> rng.put(k, Long.toString(v)));
        m.put("random", rng);
        return Json.write(m);
    }

    /** The version-1 format: setup and commands only. */
    public static String saveReplayOnly(GameEngine g) { return Json.write(header(g, 1)); }

    private static Map<String, Object> header(GameEngine g, int version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("version", version);
        m.put("rules", rulesFingerprint(g));
        m.put("setup", setupJson(g.setup()));
        List<Object> cmds = new ArrayList<>();
        for (Command c : g.history()) cmds.add(CommandCodec.toJson(c));
        m.put("commands", cmds);
        return m;
    }

    public static GameEngine load(String text) {
        Map<String, Object> m = Json.object(Json.parse(text));
        if (!FORMAT.equals(m.get("format"))) throw new IllegalArgumentException("Not a Mandate save file");
        int version = Json.integer(m.get("version"));
        GameSetup setup = setupFromJson(Json.object(m.get("setup")));
        List<Command> commands = new ArrayList<>();
        for (Object o : Json.array(m.get("commands"))) commands.add(CommandCodec.fromJson(Json.object(o)));
        return switch (version) {
            case 1 -> GameEngine.replay(setup, commands);
            case 2 -> {
                Map<String, Long> rng = new LinkedHashMap<>();
                Json.object(m.get("random")).forEach((k, v) -> rng.put(k, Long.parseLong((String) v)));
                yield GameEngine.restore(setup, Json.object(m.get("world")), rng, commands);
            }
            default -> throw new IllegalArgumentException("Unsupported save version " + version);
        };
    }

    /** Whether replaying a version-2 save's commands reproduces its snapshot exactly (a consistency check). */
    public static boolean consistent(String text) {
        Map<String, Object> m = Json.object(Json.parse(text));
        GameEngine restored = load(text);
        GameEngine replayed = GameEngine.replay(setupFromJson(Json.object(m.get("setup"))), restored.history());
        return Json.write(replayed.state().toJson()).equals(Json.write(restored.state().toJson()))
                && replayed.random().snapshot().equals(restored.random().snapshot());
    }

    public static void write(GameEngine g, Path file) {
        try { Files.writeString(file, save(g), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static GameEngine read(Path file) {
        try { return load(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static String rulesFingerprint(GameEngine g) {
        return Integer.toHexString(new TreeMap<>(g.tuning().asMap()).toString().hashCode());
    }

    static Map<String, Object> setupJson(GameSetup s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seed", Long.toString(s.seed())); m.put("party", s.playerParty().name()); m.put("player", s.playerCandidateId());
        m.put("opponent", s.opponentCandidateId()); m.put("scenario", s.scenario().name()); m.put("difficulty", s.difficulty().name());
        m.put("length", s.length().name());
        m.put("realRoster", s.realRoster());
        Candidate c = s.customCandidate();
        if (c != null) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("party", c.party().name()); x.put("name", c.name()); x.put("home", c.homeState()); x.put("charisma", c.charisma());
            x.put("debate", c.debate()); x.put("fundraising", c.fundraising()); x.put("policy", c.policy()); x.put("discipline", c.discipline());
            x.put("ideology", c.ideology());
            m.put("custom", x);
        } else m.put("custom", null);
        return m;
    }

    static GameSetup setupFromJson(Map<String, Object> m) {
        return new GameSetup(Long.parseLong((String) m.get("seed")), Party.valueOf((String) m.get("party")), (String) m.get("player"),
                (String) m.get("opponent"), Scenario.valueOf((String) m.get("scenario")), GameSetup.Difficulty.valueOf((String) m.get("difficulty")),
                m.get("length") == null ? GameSetup.Length.FULL_TERM : GameSetup.Length.valueOf((String) m.get("length")), // saves before the slice are full-term
                Boolean.TRUE.equals(m.get("realRoster")), customFromJson(m.get("custom")));
    }

    private static Candidate customFromJson(Object o) {
        if (o == null) return null;
        Map<String, Object> x = Json.object(o);
        return Candidate.custom(Party.valueOf((String) x.get("party")), (String) x.get("name"), (String) x.get("home"), Json.integer(x.get("charisma")),
                Json.integer(x.get("debate")), Json.integer(x.get("fundraising")), Json.integer(x.get("policy")), Json.integer(x.get("discipline")),
                Json.num(x.get("ideology")));
    }
}
