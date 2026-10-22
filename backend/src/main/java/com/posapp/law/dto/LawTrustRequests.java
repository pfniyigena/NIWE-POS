package com.posapp.law.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the client money (trust) screens send. */
public final class LawTrustRequests {

    private LawTrustRequests() {
    }

    public record TrustDepositRequest(BigDecimal amount, LocalDate date, String method, String reference, String payerName, String purpose) {
    }

    public record TrustPayoutRequest(BigDecimal amount, LocalDate date, String method, String reference, String payeeName, String purpose) {
    }

    public record TrustFeeTransferRequest(Long invoiceId, BigDecimal amount, String purpose) {
    }

    public record TrustNoteRequest(String note) {
    }

    public record TrustReverseRequest(String reason) {
    }

    public record TrustReconcileRequest(LocalDate asOfDate, BigDecimal statementBalance, BigDecimal depositsInTransit, BigDecimal outstandingPayments, String note) {
    }
}
