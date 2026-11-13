package game.mandate.core.congress;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Csv;
import game.mandate.core.state.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BillCatalog {
    private final List<Bill> bills;

    private BillCatalog(List<Bill> bills) { this.bills = List.copyOf(bills); }

    public static BillCatalog load() {
        List<Bill> list = new ArrayList<>();
        for (String[] f : Csv.readResource("/data/bills.csv")) {
            Optional<Party> sponsor = f[2].equals("BIPARTISAN") ? Optional.empty() : Optional.of(Party.valueOf(f[2]));
            list.add(new Bill(f[0], f[1], sponsor, Double.parseDouble(f[3]), Issue.valueOf(f[4]), Double.parseDouble(f[5]),
                    Double.parseDouble(f[6]), Double.parseDouble(f[7]), Double.parseDouble(f[8]), Double.parseDouble(f[9]),
                    Boolean.parseBoolean(f[10])));
        }
        return new BillCatalog(list);
    }

    public List<Bill> all() { return bills; }

    public List<Bill> availableTo(Party p) { return bills.stream().filter(b -> b.availableTo(p)).toList(); }

    public boolean has(String id) { return bills.stream().anyMatch(b -> b.id().equals(id)); }

    public Bill get(String id) {
        return bills.stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown bill " + id));
    }
}
