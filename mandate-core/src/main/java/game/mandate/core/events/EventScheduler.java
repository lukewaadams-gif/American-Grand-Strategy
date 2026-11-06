package game.mandate.core.events;

import game.mandate.core.data.Json;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.time.Phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Owns which events are waiting for the player and which have already happened. Each turn it rolls the content
 * catalogue against the world (conditions read a {@link WorldView}); systems may also queue an event directly
 * (an escalation rung crossed, for example).
 */
public final class EventScheduler {
    private final List<PendingEvent> pending = new ArrayList<>();
    private final TreeSet<String> fired = new TreeSet<>();
    private int nextId = 1;

    public List<PendingEvent> pending() { return List.copyOf(pending); }
    public boolean hasPending() { return !pending.isEmpty(); }
    public boolean fired(String id) { return fired.contains(id); }

    /** Rolls every eligible event for this turn. Returns what fired. */
    public List<PendingEvent> roll(List<EventDefinition> catalog, Phase phase, WorldView world, DeterministicRandomManager rng) {
        List<PendingEvent> out = new ArrayList<>();
        for (EventDefinition d : catalog) {
            if (!d.phases().contains(phase)) continue;
            if (!d.repeatable() && fired.contains(d.id())) continue;
            if (pending.stream().anyMatch(p -> p.definition().id().equals(d.id()))) continue;
            double chance = d.chancePerTurn().applyAsDouble(world);
            if (chance > 0 && rng.chance("events." + d.id(), chance)) out.add(queue(d));
        }
        return out;
    }

    public PendingEvent queue(EventDefinition d) {
        fired.add(d.id());
        PendingEvent p = new PendingEvent(nextId++, d);
        pending.add(p);
        return p;
    }

    public PendingEvent find(int instanceId) {
        return pending.stream().filter(e -> e.instanceId() == instanceId).findFirst().orElse(null);
    }

    void remove(PendingEvent p) { pending.remove(p); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> l = new ArrayList<>();
        for (PendingEvent p : pending) l.add(List.of((long) p.instanceId(), p.definition().id()));
        m.put("pending", l); m.put("fired", new ArrayList<>(fired)); m.put("nextId", nextId);
        return m;
    }

    public static EventScheduler fromJson(Map<String, Object> m) {
        EventScheduler s = new EventScheduler();
        for (Object o : Json.array(m.get("pending"))) {
            List<Object> x = Json.array(o);
            s.pending.add(new PendingEvent(Json.integer(x.get(0)), EventCatalog.byId((String) x.get(1))));
        }
        for (Object o : Json.array(m.get("fired"))) s.fired.add((String) o);
        s.nextId = Json.integer(m.get("nextId"));
        return s;
    }
}
