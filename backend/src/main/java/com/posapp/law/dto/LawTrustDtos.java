package com.posapp.law.dto;

import com.posapp.law.MatterStatus;
import com.posapp.law.TrustStatus;
import com.posapp.law.TrustType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the client money (trust) screens return. */
public final class LawTrustDtos {

    private LawTrustDtos() {
    }

    public record TrustTxDto(Long id, String number, TrustType type, TrustStatus status, Long matterId, String matterNumber, Long clientCustomerId,
                             String clientName, BigDecimal amount, LocalDate txDate, String method, String reference, String partyName, String purpose,
                             String requestedByName, Instant createdAt, String decidedByName, Instant decidedAt, String decisionNote, Long invoiceId,
                             String invoiceNumber, Long reversalOfId, boolean reversed, BigDecimal balanceAfter, boolean canApprove, boolean canCancel,
                             boolean canReverse) {
    }

    public record TrustBalanceRow(Long matterId, String matterNumber, String matterTitle, MatterStatus matterStatus, Long clientCustomerId, String clientName,
                                  BigDecimal balance, BigDecimal pending, BigDecimal available, LocalDate lastActivity) {
    }

    public record TrustOverviewDto(boolean enabled, String bankName, String accountNumber, BigDecimal totalHeld, BigDecimal totalPending, int clients,
                                   List<TrustBalanceRow> rows, List<TrustTxDto> approvals, boolean canReconcile) {
    }

    public record TrustInvoiceDto(Long invoiceId, String invoiceNumber, BigDecimal total, BigDecimal outstanding, BigDecimal waiting) {
    }

    public record MatterTrustDto(Long matterId, String number, String title, Long clientCustomerId, String clientName, MatterStatus matterStatus,
                                 boolean enabled, String bankName, String accountNumber, BigDecimal balance, BigDecimal pending, BigDecimal available,
                                 List<TrustTxDto> transactions, List<TrustInvoiceDto> invoices, boolean canRecord, boolean canDeposit) {
    }

    public record ReconciliationDto(Long id, LocalDate asOfDate, BigDecimal statementBalance, BigDecimal depositsInTransit, BigDecimal outstandingPayments,
                                    BigDecimal adjustedBank, BigDecimal ledgerTotal, BigDecimal glBank, BigDecimal glLiability, BigDecimal difference,
                                    boolean balanced, String note, String preparedByName, Instant createdAt) {
    }

    public record ReconciliationView(BigDecimal ledgerTotal, BigDecimal pendingOut, BigDecimal glBank, BigDecimal glLiability, List<ReconciliationDto> history) {
    }
}
