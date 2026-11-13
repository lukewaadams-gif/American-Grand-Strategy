package game.mandate.core.economy;

/**
 * What kind of federal money a policy moves, and how much a dollar of it moves output. The ranges are the
 * Congressional Budget Office's for its estimates of the 2009 Recovery Act; the central values are this game's
 * choice within them. A slump, or the Fed at its floor, moves a multiplier toward the top of its range; a boom
 * toward the bottom (see {@link EconomyEngine#multiplier}).
 */
public enum FiscalClass {
    PURCHASES("Federal purchases of goods and services", 0.5, 2.5, 1.3),
    INFRASTRUCTURE("Grants to states for infrastructure", 0.4, 2.2, 1.2),
    STATE_GRANTS("Other grants to states", 0.4, 1.8, 1.0),
    TRANSFERS("Transfers to people", 0.4, 2.1, 1.2),
    RETIREES("Payments to retirees", 0.2, 1.0, 0.5),
    TAX_MIDDLE("Taxes on lower and middle incomes", 0.3, 1.5, 0.8),
    TAX_HIGH("Taxes on higher incomes", 0.1, 0.6, 0.3),
    CORPORATE("Corporate taxes", 0.0, 0.4, 0.2),
    /** This game's older bills, until each is re-expressed as the policy steps it makes. */
    MIXED("A mix of spending and tax changes", 0.3, 1.5, 0.8),
    /** Money that moves no demand directly (tariff revenue, fees, savings that come from lower prices). */
    NONE("No direct effect on demand", 0, 0, 0);

    public final String label;
    public final double low, high, central;

    FiscalClass(String label, double low, double high, double central) {
        this.label = label;
        this.low = low;
        this.high = high;
        this.central = central;
    }
}
