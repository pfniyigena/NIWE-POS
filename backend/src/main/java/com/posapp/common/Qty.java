package com.posapp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quantities are decimal numbers (2.5 kg, 0.75 m) kept to four decimals. These helpers keep the arithmetic short and null-safe,
 * and {@link #n} gives the tidy form for printing and exporting (5 rather than 5.0000).
 */
public final class Qty {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    private static final int SCALE = 4;

    private Qty() {
    }

    /** A number coming out of a query row (Number, BigDecimal, null) as a quantity. */
    public static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(o.toString());
    }

    /** null counts as zero. */
    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal of(long v) {
        return BigDecimal.valueOf(v);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return nz(a).add(nz(b));
    }

    public static BigDecimal sub(BigDecimal a, BigDecimal b) {
        return nz(a).subtract(nz(b));
    }

    public static BigDecimal mul(BigDecimal a, BigDecimal b) {
        return nz(a).multiply(nz(b)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0 ? nz(a) : nz(b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) <= 0 ? nz(a) : nz(b);
    }

    /** The larger of the value and zero (a shortage can't be negative). */
    public static BigDecimal atLeastZero(BigDecimal a) {
        return max(a, BigDecimal.ZERO);
    }

    public static int cmp(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b));
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return cmp(a, b) > 0;
    }

    public static boolean ge(BigDecimal a, BigDecimal b) {
        return cmp(a, b) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return cmp(a, b) < 0;
    }

    public static boolean le(BigDecimal a, BigDecimal b) {
        return cmp(a, b) <= 0;
    }

    public static boolean eq(BigDecimal a, BigDecimal b) {
        return cmp(a, b) == 0;
    }

    public static boolean isZero(BigDecimal a) {
        return nz(a).signum() == 0;
    }

    public static boolean isPositive(BigDecimal a) {
        return nz(a).signum() > 0;
    }

    public static boolean isNegative(BigDecimal a) {
        return nz(a).signum() < 0;
    }

    /** Rounded to four decimals (the most the database keeps). */
    public static BigDecimal round(BigDecimal a) {
        return a == null ? null : a.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Tidy form: no trailing zeros and never an exponent (5, 2.5, 0.0625). */
    public static BigDecimal n(BigDecimal a) {
        if (a == null) {
            return null;
        }
        BigDecimal s = a.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** Printable form of {@link #n}: "5", "2.5". */
    public static String str(BigDecimal a) {
        return a == null ? "" : n(a).toPlainString();
    }

    /** A positive quantity with at most four decimals, or an error naming the field. */
    public static BigDecimal requirePositive(BigDecimal a, String what) {
        if (a == null || a.signum() <= 0) {
            throw ApiException.badRequest(what + " must be more than zero.");
        }
        if (a.stripTrailingZeros().scale() > SCALE) {
            throw ApiException.badRequest(what + " can have at most " + SCALE + " decimals.");
        }
        return a;
    }

    /** Sum of the values (null counts as zero). */
    public static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            t = t.add(nz(v));
        }
        return t;
    }
}
