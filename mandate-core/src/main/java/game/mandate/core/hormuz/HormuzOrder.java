package game.mandate.core.hormuz;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** What the president orders in the Strait of Hormuz crisis. Carried by {@code Command.Hormuz}. */
public sealed interface HormuzOrder {
    /** Escort groups shepherding tankers through the lanes, 0-4. */
    record Escorts(int groups) implements HormuzOrder {}
    /** American mine-countermeasure groups at work, 0-3. */
    record Sweep(int groups) implements HormuzOrder {}
    /** Strikes on one kind of coastal site: "missiles", "boats", "drones" or "mines". */
    record Strike(String kind) implements HormuzOrder {}
    /** Diplomacy: "oman" or "qatar" (an envoy to Tehran), "china", "india", "gulf" (the hosts), "allies" (minesweepers). */
    record Envoy(String channel) implements HormuzOrder {}
    /** The oil levers: "spr" (amount: million barrels a day), "iea", "bypass", "reinsure". */
    record Oil(String lever, double amount) implements HormuzOrder {}
    /** Israel: hold its fire, or let it strike Iran's nuclear sites. */
    record Israel(boolean restrain) implements HormuzOrder {}
    /** The public message: firm, or calm. */
    record Message(boolean firm) implements HormuzOrder {}
    /** An invasion: 0 a full invasion of Iran, 1 the islands in the strait, 2 the coast. */
    record Invade(int option) implements HormuzOrder {}
    /** A crisis begins at once (the standalone scenario's start): 0 the base case, 1 optimistic, 2 pessimistic. */
    record Begin(int preset) implements HormuzOrder {}
    /** The National Security Council's playbook for the day (watch mode and the autopilot): escorts, minesweepers and the
     *  oil levers on the first day; then the envoys whenever they are free. No strikes and no invasion. */
    record Playbook() implements HormuzOrder {}

    static Map<String, Object> toJson(HormuzOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Escorts x -> m.put("groups", x.groups());
            case Sweep x -> m.put("groups", x.groups());
            case Strike x -> m.put("kind", x.kind());
            case Envoy x -> m.put("channel", x.channel());
            case Oil x -> { m.put("lever", x.lever()); m.put("amount", x.amount()); }
            case Israel x -> m.put("restrain", x.restrain());
            case Message x -> m.put("firm", x.firm());
            case Invade x -> m.put("option", x.option());
            case Begin x -> m.put("preset", x.preset());
            case Playbook x -> { }
        }
        return m;
    }

    static HormuzOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Escorts" -> new Escorts(Json.integer(m.get("groups")));
            case "Sweep" -> new Sweep(Json.integer(m.get("groups")));
            case "Strike" -> new Strike((String) m.get("kind"));
            case "Envoy" -> new Envoy((String) m.get("channel"));
            case "Oil" -> new Oil((String) m.get("lever"), Json.num(m.get("amount")));
            case "Israel" -> new Israel((Boolean) m.get("restrain"));
            case "Message" -> new Message((Boolean) m.get("firm"));
            case "Invade" -> new Invade(Json.integer(m.get("option")));
            case "Begin" -> new Begin(Json.integer(m.get("preset")));
            case "Playbook" -> new Playbook();
            default -> throw new IllegalArgumentException("Unknown Hormuz order " + m.get("type"));
        };
    }
}
