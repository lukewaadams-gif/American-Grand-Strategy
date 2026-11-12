package game.mandate.ui;

import game.mandate.core.data.Json;
import game.mandate.ui.kit.Theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The player's preferences, kept apart from any one game ({@code settings.json} next to the saves). */
public final class Settings {

    /** How much the interface shows at once. It changes presentation only, never the simulation. */
    public enum Complexity {
        GUIDED("Guided", "Tips and the advisor are on; screens show the essentials and hide expert detail (crosstabs, raw effects, market internals)."),
        STANDARD("Standard", "Everything a player needs, with expert detail one click away."),
        EXPERT("Expert", "Every figure the game shows anywhere, on every screen: crosstabs, the consequence ledger's raw effects, bond markets, relationship scores.");

        public final String label, description;
        Complexity(String label, String description) { this.label = label; this.description = description; }
    }

    public Theme.Palette palette = Theme.Palette.STANDARD;
    public double scale = 1.0;
    public Complexity complexity = Complexity.STANDARD;
    public boolean tips = true, advisor = true, confirmUnspent = true, reduceMotion = false, autoOpenDecisions = true;

    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            if (!Files.exists(file)) return s;
            Map<String, Object> m = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            s.palette = Theme.Palette.valueOf((String) m.getOrDefault("palette", "STANDARD"));
            s.scale = Json.num(m.getOrDefault("scale", 1.0));
            s.complexity = Complexity.valueOf((String) m.getOrDefault("complexity", "STANDARD"));
            s.tips = (Boolean) m.getOrDefault("tips", true);
            s.advisor = (Boolean) m.getOrDefault("advisor", true);
            s.confirmUnspent = (Boolean) m.getOrDefault("confirmUnspent", true);
            s.reduceMotion = (Boolean) m.getOrDefault("reduceMotion", false);
            s.autoOpenDecisions = (Boolean) m.getOrDefault("autoOpenDecisions", true);
        } catch (IOException | RuntimeException e) {
            return new Settings();                 // a damaged settings file never stops the game
        }
        return s;
    }

    public void save(Path file) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("palette", palette.name()); m.put("scale", scale); m.put("complexity", complexity.name()); m.put("tips", tips); m.put("advisor", advisor);
        m.put("confirmUnspent", confirmUnspent); m.put("reduceMotion", reduceMotion); m.put("autoOpenDecisions", autoOpenDecisions);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(m), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    public boolean expert() { return complexity == Complexity.EXPERT; }
    public boolean guided() { return complexity == Complexity.GUIDED; }
}
