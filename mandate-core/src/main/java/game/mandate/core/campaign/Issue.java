package game.mandate.core.campaign;

import game.mandate.core.state.Party;

/**
 * The issues voters weigh. {@code ownership} is the baseline trust advantage in points for Democrats
 * (negative = Republicans trusted more). Salience is dynamic and lives in the game state.
 */
public enum Issue {
    ECONOMY("The economy", -3.0, 0.20),
    COST_OF_LIVING("Cost of living", -1.0, 0.18),
    HEALTHCARE("Health care", 6.0, 0.10),
    IMMIGRATION("Immigration", -6.0, 0.12),
    ABORTION("Abortion", 7.0, 0.07),
    CRIME("Crime and safety", -4.0, 0.06),
    CLIMATE_ENERGY("Climate and energy", 3.0, 0.06),
    CHINA("China", -3.0, 0.05),
    AI_TECH("AI and jobs", 0.0, 0.06),
    DEMOCRACY("Democracy and corruption", 2.0, 0.07),
    EDUCATION("Education", 2.0, 0.03);

    private final String label;
    private final double ownership;
    private final double baseSalience;

    Issue(String label, double ownership, double baseSalience) {
        this.label = label;
        this.ownership = ownership;
        this.baseSalience = baseSalience;
    }

    public String label() { return label; }
    public double baseSalience() { return baseSalience; }

    /** Trust advantage for {@code party} on this issue, in points. */
    public double advantageFor(Party party) { return ownership * party.sign(); }
}
