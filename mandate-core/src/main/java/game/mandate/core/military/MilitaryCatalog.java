package game.mandate.core.military;

import game.mandate.core.data.Csv;

import java.util.ArrayList;
import java.util.List;

/** Force types and munitions, with their 2027 baselines ({@code /data/forces.csv}, {@code /data/munitions.csv}). */
public final class MilitaryCatalog {
    public record ForceType(String id, String name, String category, int baseline, double killedIfLost) {}

    public record Munition(String id, String name, String role, int baseline, double productionPerMonth, boolean transferable) {}

    private final List<ForceType> forces;
    private final List<Munition> munitions;

    private MilitaryCatalog(List<ForceType> f, List<Munition> m) { forces = List.copyOf(f); munitions = List.copyOf(m); }

    public static MilitaryCatalog load() {
        List<ForceType> f = new ArrayList<>();
        for (String[] r : Csv.readResource("/data/forces.csv"))
            f.add(new ForceType(r[0], r[1], r[2], Integer.parseInt(r[3]), Double.parseDouble(r[4])));
        List<Munition> m = new ArrayList<>();
        for (String[] r : Csv.readResource("/data/munitions.csv"))
            m.add(new Munition(r[0], r[1], r[2], Integer.parseInt(r[3]), Double.parseDouble(r[4]), Boolean.parseBoolean(r[5])));
        return new MilitaryCatalog(f, m);
    }

    public List<ForceType> forces() { return forces; }
    public List<Munition> munitions() { return munitions; }

    public List<Munition> munitions(String role) { return munitions.stream().filter(m -> m.role().equals(role)).toList(); }

    public int baseline(String role) { return munitions(role).stream().mapToInt(Munition::baseline).sum(); }

    public ForceType force(String id) {
        return forces.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No force " + id));
    }
}
