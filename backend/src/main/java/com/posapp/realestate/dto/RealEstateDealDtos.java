package com.posapp.realestate.dto;

import com.posapp.realestate.CommissionBasis;
import com.posapp.realestate.CommissionStatus;
import com.posapp.realestate.DealStage;
import com.posapp.realestate.MilestoneKind;
import com.posapp.realestate.PayeeKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RealEstateDealDtos {

    private RealEstateDealDtos() {
    }

    public record DealRow(Long id, String code, Long propertyId, String propertyCode, String propertyTitle, String buyerName, Long buyerCustomerId, String agentName,
                          Long agentUserId, DealStage stage, BigDecimal listPrice, BigDecimal offerAmount, BigDecimal agreedPrice, BigDecimal paid, BigDecimal outstanding,
                          BigDecimal commissionTotal, LocalDate offerDate, LocalDate offerValidUntil, LocalDate reservationExpiry, LocalDate expectedCompletion,
                          LocalDate completedDate, boolean overdue, Instant createdAt) {
    }

    /** What the buyer's invoice for one payment looks like. status: NOT_INVOICED, DRAFT, ISSUED, PAID, VOID. */
    public record MilestoneDto(Long id, MilestoneKind kind, String label, BigDecimal amount, LocalDate dueDate, Long invoiceId, String invoiceNumber, String status,
                               BigDecimal paid, BigDecimal outstanding, boolean overdue) {
    }

    public record CommissionDto(Long id, String role, PayeeKind payeeKind, Long payeeUserId, String payeeName, CommissionBasis basis, BigDecimal sharePercent,
                                BigDecimal fixedAmount, BigDecimal amount, CommissionStatus status, boolean due, Long expenseId, String expenseStatus, boolean expensePaid,
                                String note) {
    }

    public record FeeInvoiceDto(Long invoiceId, String invoiceNumber, String status, BigDecimal total, BigDecimal paid) {
    }

    public record DealDto(Long id, String code, Long propertyId, String propertyCode, String propertyTitle, String propertyArea, Long leadId, String leadName,
                          Long buyerCustomerId, String buyerName, String buyerPhone, String buyerEmail, Long agentUserId, String agentName, DealStage stage,
                          BigDecimal listPrice, BigDecimal offerAmount, BigDecimal agreedPrice, LocalDate offerDate, LocalDate offerValidUntil, BigDecimal reservationDeposit,
                          LocalDate reservationExpiry, LocalDate agreementDate, LocalDate expectedCompletion, LocalDate completedDate, BigDecimal commissionPercent,
                          BigDecimal commissionFixed, BigDecimal commissionTotal, BigDecimal commissionAllocated, BigDecimal commissionKept, Long sellerCustomerId,
                          String sellerName, FeeInvoiceDto feeInvoice, String notes, String cancelReason, String createdByName, Instant createdAt, BigDecimal scheduled,
                          BigDecimal invoiced, BigDecimal paid, BigDecimal outstanding, List<MilestoneDto> milestones, List<CommissionDto> commissions, boolean canManage) {
    }

    public record CommissionRow(Long dealId, String dealCode, String propertyCode, String propertyTitle, LocalDate completedDate, DealStage stage, BigDecimal price,
                                Long commissionId, String role, String payeeName, PayeeKind payeeKind, BigDecimal amount, CommissionStatus status, boolean due, String expenseStatus,
                                boolean expensePaid) {
    }
}
