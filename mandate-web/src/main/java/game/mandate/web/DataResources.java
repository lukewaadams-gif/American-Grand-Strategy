package game.mandate.web;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/** Tells TeaVM to embed the core's data files, so {@code getResourceAsStream} works in the browser as on the JVM. */
public final class DataResources implements ResourceSupplier {
    static final String[] FILES = {"bills.csv", "blocs.csv", "cabinet.csv", "cabinet_archetypes.csv", "calendar.properties", "candidates.csv",
            "campaign_states.csv", "candidates_bg.csv", "countries.csv", "electorate_cells.csv", "forces.csv", "media_markets.csv", "munitions.csv",
            "states.csv", "tuning.properties", "state_centroids.csv", "candidate_ages.csv", "rd/ambient.tsv", "rd/state_economy.tsv", "rd/policies.tsv", "policy_scores.tsv", "bill_policies.tsv",
            "rd/names.tsv", "rd/era_2029.tsv", "rd/actions.tsv", "rd/outlets.tsv", "rd/storylines.tsv", "justices.tsv"};

    @Override public String[] supplyResources(ResourceSupplierContext context) {
        String[] out = new String[FILES.length];
        for (int i = 0; i < FILES.length; i++) out[i] = "data/" + FILES[i];
        return out;
    }
}
