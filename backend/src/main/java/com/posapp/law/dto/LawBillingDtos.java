package com.posapp.law.dto;

import com.posapp.law.BillKind;
import com.posapp.law.BillStatus;
import com.posapp.law.BillingType;
import com.posapp.law.StageStatus;
import com.posapp.timetracking.dto.TimeDtos.EntryDto;
import com.posapp.timetracking.dto.TimeDtos.RateDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the matter billing screens and the billing overview return. */
public final class LawBillingDtos {

    private LawBillingDtos() {
    }

    public record FeeTermsDto(BillingType billingType, String billingNote, BigDecimal hourlyRate, BigDecimal fixedFee, BigDecimal retainerAmount,
                              BigDecimal contingencyPercent) {
    }

    public record StageDto(Long id, String description, BigDecimal amount, LocalDate dueDate, StageStatus status, Long billId, String invoiceNumber) {
    }

    public record CostDto(Long id, LocalDate expenseDate, String description, String supplierName, BigDecimal amount, BigDecimal chargeAmount, boolean billed,
                          Long billId, String invoiceNumber, Long expenseId, String createdByName, boolean canEdit) {
    }

    public record BillDto(Long id, BillKind kind, BillStatus status, Long invoiceId, String invoiceNumber, String invoiceStatus, BigDecimal amount,
                          BigDecimal feesAmount, BigDecimal expensesAmount, BigDecimal retainerApplied, BigDecimal recoveredAmount, String summary,
                          String createdByName, Instant createdAt, BigDecimal amountPaid, BigDecimal amountOutstanding, boolean stale) {
    }

    public record BillingSummary(BigDecimal hoursLogged, BigDecimal billableHours, int timeWaitingApproval, BigDecimal unbilledTime, BigDecimal unbilledCosts,
                                 BigDecimal stagesPlanned, BigDecimal billed, BigDecimal paid, BigDecimal outstanding, BigDecimal retainerInvoiced,
                                 BigDecimal retainerApplied, BigDecimal retainerBalance) {
    }

    public record BillingDto(Long matterId, String number, String title, String clientName, Long timeProjectId, FeeTermsDto terms, BillingSummary summary,
                             List<RateDto> rates, List<StageDto> stages, List<CostDto> costs, List<BillDto> bills, List<EntryDto> time, boolean canTerms,
                             boolean canBill, boolean canWork, boolean canApprove) {
    }

    public record PreviewLine(String kind, String description, BigDecimal amount) {
    }

    public record PreviewDto(Long matterId, String number, Long customerId, String customerName, List<PreviewLine> lines, BigDecimal fees, BigDecimal costs,
                             BigDecimal retainerBalance, BigDecimal retainerApplied, BigDecimal total, int timeEntries, String warning) {
    }

    public record BillResult(Long billId, Long invoiceId, String invoiceNumber, BigDecimal total, BigDecimal retainerApplied) {
    }

    /** One row of the billing overview: what a matter has earned, billed and still to bill. */
    public record OverviewRow(Long matterId, String number, String title, String clientName, BillingType billingType, String status, BigDecimal hoursLogged,
                              BigDecimal unbilledTime, BigDecimal unbilledCosts, BigDecimal stagesPlanned, BigDecimal billed, BigDecimal paid, BigDecimal outstanding,
                              BigDecimal retainerBalance, boolean restricted) {
    }

    /** One bill for the fees report. */
    public record FeeRow(LocalDate date, String matterNumber, String title, String clientName, boolean restricted, String kind, String invoiceNumber, String invoiceStatus,
                         BigDecimal fees, BigDecimal costs, BigDecimal retainerApplied, BigDecimal amount, BigDecimal paid, BigDecimal outstanding) {
    }
}
