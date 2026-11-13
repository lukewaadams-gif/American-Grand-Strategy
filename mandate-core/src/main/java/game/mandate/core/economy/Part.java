package game.mandate.core.economy;

/** The parts of the consumer price index the model builds headline inflation from, with their weights in it. */
public enum Part {
    SHELTER("shelter", "Shelter", "Rent and owners' rent", 0.35),
    FOOD("food", "Food", "Groceries and eating out", 0.13),
    ENERGY("energy", "Energy", "Gas and utilities", 0.07),
    MEDICAL("medical", "Medical care", "Insurance, care and drugs", 0.08),
    GOODS("goods", "Goods", "Cars, clothes, appliances and other goods", 0.20),
    SERVICES("services", "Other services", "Services where most of the cost is wages", 0.17);

    public static final int COUNT = 6;

    public final String key, label, detail;
    public final double weight;

    Part(String key, String label, String detail, double weight) {
        this.key = key;
        this.label = label;
        this.detail = detail;
        this.weight = weight;
    }

    /** Core inflation leaves out food and energy. */
    public boolean core() { return this != FOOD && this != ENERGY; }
}
