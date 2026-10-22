package com.posapp.realestate.dto;

import com.posapp.realestate.PayeeKind;
import com.posapp.realestate.PayoutState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Commission payouts: what each agent (or referrer) has earned on completed sales, what has gone to the accounts and what has been paid out. */
public final class RealEstateCommissionDtos {

    private RealEstateCommissionDtos() {
    }

    /** One commission share on one deal. */
    public record ShareLine(Long dealId, String dealCode, String propertyCode, String propertyTitle, LocalDate completedDate, BigDecimal salePrice, Long commissionId, String role,
                            BigDecimal amount, PayoutState state, Long expenseId, LocalDate paidOn) {
    }

    /** Everything one person is owed or has been paid, with the lines behind the totals. */
    public record PayeeStatement(String key, String payeeName, PayeeKind payeeKind, Long payeeUserId, boolean hasEmail, BigDecimal earned, BigDecimal paid, BigDecimal owed,
                                 BigDecimal toPost, BigDecimal awaitingApproval, BigDecimal toPay, BigDecimal planned, BigDecimal rejected, List<ShareLine> lines) {
    }

    public record PayeeOption(String key, String name) {
    }

    public record PayoutBoard(LocalDate from, LocalDate to, boolean expensesModuleOn, boolean accountSet, boolean mailConfigured, BigDecimal earned, BigDecimal paid, BigDecimal owed,
                              BigDecimal toPost, BigDecimal planned, long sharesToPost, List<PayeeOption> payeeOptions, List<PayeeStatement> payees) {
    }

    public record PostDueResult(int posted, int failed, BigDecimal amount, List<String> problems, String message) {
    }

    public record MailedPayee(String sentTo, String message) {
    }
}
