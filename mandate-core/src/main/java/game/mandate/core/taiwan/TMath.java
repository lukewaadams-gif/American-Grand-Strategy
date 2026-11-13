package game.mandate.core.taiwan;

/**
 * Exponentials, logarithms and powers in plain arithmetic, so that the browser build (JavaScript) and the JVM give the
 * same bits. {@code Math.exp}, {@code log} and {@code pow} differ between the two in the last digits, and here their
 * results decide branches and random comparisons. Additions, multiplications, divisions and {@code Math.sqrt} are
 * correctly rounded in both, so these functions, built only from them, are identical everywhere. Accuracy is about
 * 1e-15 relative, far below anything the model can feel.
 */
final class TMath {
    private TMath() {}

    static final double LN2 = 0.6931471805599453;

    /** e to the x. */
    static double exp(double x) {
        if (x != x) return x;
        if (x > 700) return Double.MAX_VALUE;
        if (x < -700) return 0;
        // x = k ln2 + r, |r| <= ln2 / 2; e^r by its Taylor series; then times 2^k exactly
        int k = (int) Math.floor(x / LN2 + 0.5);
        double r = x - k * LN2;
        double term = 1, sum = 1;
        for (int i = 1; i < 22; i++) {
            term *= r / i;
            sum += term;
            if (Math.abs(term) < 1e-17 * Math.abs(sum)) break;
        }
        return scale(sum, k);
    }

    /** v times 2^k, exactly (repeated doubling and halving are exact in binary floating point). */
    static double scale(double v, int k) {
        while (k > 0) { int s = Math.min(k, 60); v *= (double) (1L << s); k -= s; }
        while (k < 0) { int s = Math.min(-k, 60); v /= (double) (1L << s); k += s; }
        return v;
    }

    /** The natural logarithm (0 or less gives a large negative number). */
    static double ln(double x) {
        if (x <= 0) return -745;
        if (x != x) return x;
        int k = 0;
        while (x > 1.5) { x /= 2; k++; }
        while (x < 0.75) { x *= 2; k--; }
        // ln x = 2 atanh((x - 1) / (x + 1))
        double y = (x - 1) / (x + 1), y2 = y * y, term = y, sum = 0;
        for (int i = 1; i < 80; i += 2) {
            double t = term / i;
            sum += t;
            if (Math.abs(t) < 1e-18) break;
            term *= y2;
        }
        return 2 * sum + k * LN2;
    }

    /** a to the power b, for a >= 0. */
    static double pow(double a, double b) {
        if (a <= 0) return b == 0 ? 1 : 0;
        if (b == 0) return 1;
        if (b == 1) return a;
        if (b == 2) return a * a;
        if (b == 0.5) return Math.sqrt(a);
        return exp(b * ln(a));
    }

    /** tanh(ln r) = (r^2 - 1) / (r^2 + 1): the ground combat's control shift, without any transcendental call. */
    static double tanhLn(double r) {
        double r2 = r * r;
        return (r2 - 1) / (r2 + 1);
    }

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }

    static int clampi(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
