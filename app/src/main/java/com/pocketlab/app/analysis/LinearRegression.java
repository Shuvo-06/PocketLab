package com.pocketlab.app.analysis;

/** Ordinary least-squares line fit y = slope * x + intercept. */
public final class LinearRegression {

    private final int n;
    private final double slope;
    private final double intercept;
    private final double rSquared;
    private final double slopeStdError;

    private LinearRegression(int n, double slope, double intercept, double rSquared, double slopeStdError) {
        this.n = n;
        this.slope = slope;
        this.intercept = intercept;
        this.rSquared = rSquared;
        this.slopeStdError = slopeStdError;
    }

    /** Needs at least two points and at least two different x values. */
    public static LinearRegression fit(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length) {
            throw new IllegalArgumentException("x and y must have the same length");
        }
        int n = x.length;
        if (n < 2) throw new IllegalArgumentException("need at least 2 points");
        double mx = Stats.mean(x), my = Stats.mean(y);
        double sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        if (sxx == 0) throw new IllegalArgumentException("all x values are equal");
        double slope = sxy / sxx;
        double intercept = my - slope * mx;
        double ssRes = 0;
        for (int i = 0; i < n; i++) {
            double r = y[i] - (slope * x[i] + intercept);
            ssRes += r * r;
        }
        double r2 = syy == 0 ? 1.0 : 1.0 - ssRes / syy;
        double se = n > 2 ? Math.sqrt(ssRes / (n - 2) / sxx) : Double.NaN;
        return new LinearRegression(n, slope, intercept, r2, se);
    }

    public static LinearRegression fit(Series s) {
        return fit(s.timesCopy(), s.valuesCopy());
    }

    public int getCount() { return n; }
    public double getSlope() { return slope; }
    public double getIntercept() { return intercept; }
    /** Coefficient of determination, 1 = perfect line. */
    public double getRSquared() { return rSquared; }
    /** Standard error of the slope; NaN with only two points. */
    public double getSlopeStdError() { return slopeStdError; }
    public double predict(double x) { return slope * x + intercept; }
}
