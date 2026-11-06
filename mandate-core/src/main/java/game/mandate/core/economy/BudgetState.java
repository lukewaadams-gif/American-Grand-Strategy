package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** The federal budget and debt (percent of GDP). Owned by {@link EconomyEngine}. */
public final class BudgetState {
    double baselineDeficit;
    double policyDeficit;   // added by enacted policy
    double debt;

    BudgetState() {}

    public double baselineDeficit() { return baselineDeficit; }
    public double policyDeficit() { return policyDeficit; }
    public double debt() { return debt; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baselineDeficit", baselineDeficit); m.put("policyDeficit", policyDeficit); m.put("debt", debt);
        return m;
    }

    public static BudgetState fromJson(Map<String, Object> m) {
        BudgetState b = new BudgetState();
        b.baselineDeficit = Json.num(m.get("baselineDeficit")); b.policyDeficit = Json.num(m.get("policyDeficit")); b.debt = Json.num(m.get("debt"));
        return b;
    }
}
