package game.mandate.core.politics;

/**
 * The economy as voters feel it, assembled by the caller from the economy's state. Public opinion reads these;
 * it never computes macroeconomics itself.
 */
public record OpinionInputs(double gas, double referenceGas, double inflation, double unemployment, double mortgage,
                            double confidence, double growth) {}
