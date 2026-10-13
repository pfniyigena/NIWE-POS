package com.posapp.installment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The schedule arithmetic: how a financed amount (plus interest) is cut into dated instalments. Amounts are kept to 2 decimals; the last instalment takes the rounding difference. */
public final class InstallmentCalculator {

    private InstallmentCalculator() {
    }

    public record Line(int seq, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {
    }

    public static BigDecimal round(BigDecimal v) {
        return round(v, 2);
    }

    public static BigDecimal round(BigDecimal v, int scale) {
        return v.setScale(scale, RoundingMode.HALF_UP);
    }

    /** {@code scale} is 2 normally, 0 for "whole amounts" (currencies without cents); the last instalment takes whatever rounding is left. */
    public static List<Line> build(BigDecimal principal, InterestMethod method, BigDecimal annualRatePercent, InstallmentFrequency frequency,
                                   int n, LocalDate firstDue, int scale) {
        boolean noInterest = method == InterestMethod.NONE || annualRatePercent == null || annualRatePercent.signum() <= 0;
        List<Line> lines = new ArrayList<>();
        if (noInterest || method == InterestMethod.FLAT) {
            BigDecimal totalInterest = noInterest ? BigDecimal.ZERO
                    : round(principal.multiply(annualRatePercent).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(n)).divide(BigDecimal.valueOf(frequency.perYear()), 10, RoundingMode.HALF_UP), scale);
            BigDecimal principalEach = principal.divide(BigDecimal.valueOf(n), scale, RoundingMode.DOWN);
            BigDecimal interestEach = totalInterest.divide(BigDecimal.valueOf(n), scale, RoundingMode.DOWN);
            BigDecimal principalLeft = principal;
            BigDecimal interestLeft = totalInterest;
            for (int k = 1; k <= n; k++) {
                BigDecimal p = k == n ? principalLeft : principalEach;
                BigDecimal i = k == n ? interestLeft : interestEach;
                lines.add(new Line(k, frequency.dueDate(firstDue, k - 1), p, i));
                principalLeft = principalLeft.subtract(p);
                interestLeft = interestLeft.subtract(i);
            }
            return lines;
        }
        // REDUCING: equal total payments, interest on the balance still owed.
        double i = annualRatePercent.doubleValue() / 100.0 / frequency.perYear();
        double factor = 1 - Math.pow(1 + i, -n);
        BigDecimal payment = round(principal.multiply(BigDecimal.valueOf(i / factor)), scale);
        BigDecimal balance = principal;
        for (int k = 1; k <= n; k++) {
            BigDecimal interest = round(balance.multiply(BigDecimal.valueOf(i)), scale);
            BigDecimal p = k == n ? balance : payment.subtract(interest);
            if (p.compareTo(balance) > 0) {
                p = balance;
            }
            if (p.signum() < 0) {
                p = BigDecimal.ZERO;
            }
            lines.add(new Line(k, frequency.dueDate(firstDue, k - 1), p, interest));
            balance = balance.subtract(p);
        }
        return lines;
    }
}
