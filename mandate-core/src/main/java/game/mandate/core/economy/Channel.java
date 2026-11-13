package game.mandate.core.economy;

/**
 * The ways a policy reaches the economy beyond its money (docs/ECONOMY_MODEL.md, "What a score holds"), each with the
 * column name the policy table uses. A law carries one value per channel, at its full effect.
 */
public enum Channel {
    IMMIGRATION("immigration", "Net immigration", "million a year"),
    PARTICIPATION("participation", "Labour-force participation", "points"),
    NAIRU("nairu", "The natural rate of unemployment", "points"),
    TFP_LEVEL("tfp_level", "Productivity", "%"),
    TFP_GROWTH("tfp_growth", "Productivity growth", "points a year"),
    USER_COST("capital", "The cost of capital", "%"),
    SHELTER("shelter", "Shelter prices", "%"),
    FOOD("food", "Food prices", "%"),
    ENERGY("energy", "Energy prices", "%"),
    MEDICAL("medical", "Medical prices", "%"),
    GOODS("goods", "Goods prices", "%"),
    SERVICES("services", "Services prices", "%"),
    OIL("oil", "Oil", "$ a barrel"),
    GAS("gas", "Gas", "$ a gallon"),
    HOUSING("housing", "Housing starts", "%"),
    MANUFACTURING("manufacturing", "Manufacturing", "index points"),
    AI("ai", "AI investment", "%"),
    FARM("farm", "Farm income", "index points"),
    ENERGY_PRODUCTION("energy_production", "Energy production", "index points"),
    TOURISM("tourism", "Tourism", "index points");

    public static final int COUNT = 20;

    public final String key, label, unit;

    Channel(String key, String label, String unit) {
        this.key = key;
        this.label = label;
        this.unit = unit;
    }

    /** The price part a price channel moves, or null. */
    public Part part() {
        return switch (this) {
            case SHELTER -> Part.SHELTER; case FOOD -> Part.FOOD; case ENERGY -> Part.ENERGY; case MEDICAL -> Part.MEDICAL;
            case GOODS -> Part.GOODS; case SERVICES -> Part.SERVICES; default -> null;
        };
    }

    /** By the policy table's column or key name ("energy" in the sectors column is energy production). */
    public static Channel of(String key, boolean sector) {
        if (sector && key.equals("energy")) return ENERGY_PRODUCTION;
        for (Channel c : values()) if (c.key.equals(key)) return c;
        throw new IllegalArgumentException("no channel " + key);
    }
}
