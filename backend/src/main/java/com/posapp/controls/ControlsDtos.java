package com.posapp.controls;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes of the accounting controls. */
public final class ControlsDtos {

    private ControlsDtos() {}

    public record LockRequest(LocalDate lockDate, String reason) {}

    public record ReconStart(Long accountId, LocalDate statementDate, BigDecimal statementBalance, String note) {}

    public record ReconUpdate(LocalDate statementDate, BigDecimal statementBalance, String note) {}

    public record TickRequest(List<Long> lineIds, boolean ticked) {}

    public record TickAllRequest(LocalDate from, LocalDate to, boolean ticked) {}

    public record AdjustmentRequest(Long otherAccountId, BigDecimal amount, String direction, LocalDate date, String memo) {}

    public record MoneyAccount(Long id, String code, String name) {}

    public record ReconRow(Long id, String reconciliationNumber, Long accountId, String accountCode, String accountName, LocalDate statementDate, BigDecimal statementBalance,
                           String status, int ticked, BigDecimal difference, String note, String createdBy, String completedBy, Instant completedAt, boolean canUndo) {}

    public record LineRow(Long lineId, LocalDate date, String entryNumber, String memo, String description, BigDecimal debit, BigDecimal credit, boolean ticked) {}

    public record ReconDetail(ReconRow header, BigDecimal clearedBefore, BigDecimal tickedDeposits, BigDecimal tickedWithdrawals, BigDecimal cleared, BigDecimal difference,
                              BigDecimal ledgerBalance, List<LineRow> lines, boolean truncated) {}

    public record OpeningLine(Long accountId, BigDecimal debit, BigDecimal credit) {}

    public record OpeningRequest(LocalDate asOf, boolean balanceToEquity, String memo, List<OpeningLine> lines) {}

    public record OpeningLineView(Long accountId, String accountCode, String accountName, BigDecimal debit, BigDecimal credit) {}

    public record OpeningState(boolean recorded, Long entryId, String entryNumber, LocalDate asOf, BigDecimal totalDebit, BigDecimal totalCredit, List<OpeningLineView> lines) {}
}
