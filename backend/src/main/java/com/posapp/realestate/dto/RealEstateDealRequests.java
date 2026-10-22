package com.posapp.realestate.dto;

import com.posapp.realestate.CommissionBasis;
import com.posapp.realestate.MilestoneKind;
import com.posapp.realestate.PayeeKind;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RealEstateDealRequests {

    private RealEstateDealRequests() {
    }

    /** New deal (propertyId, offerAmount and a buyer name or a lead are needed) or edit (anything left out stays as it was). */
    public record DealRequest(Long propertyId, Long leadId, String buyerName, String buyerPhone, String buyerEmail, Long buyerCustomerId, BigDecimal offerAmount,
                              LocalDate offerDate, LocalDate offerValidUntil, Long agentUserId, BigDecimal commissionPercent, BigDecimal commissionFixed, BigDecimal agreedPrice,
                              LocalDate expectedCompletion, String notes) {
    }

    public record ReserveRequest(BigDecimal agreedPrice, BigDecimal deposit, LocalDate reservationExpiry, LocalDate expectedCompletion) {
    }

    public record AgreementRequest(LocalDate agreementDate, LocalDate expectedCompletion) {
    }

    public record CompleteRequest(LocalDate completedDate, Boolean force) {
    }

    public record CancelRequest(String reason) {
    }

    public record MilestoneRequest(MilestoneKind kind, String label, BigDecimal amount, LocalDate dueDate) {
    }

    /** Replaces the payments that are not invoiced yet with a deposit and equal instalments. */
    public record PlanRequest(BigDecimal depositPercent, Integer instalments, LocalDate firstDue, Integer everyMonths) {
    }

    public record InvoiceMilestoneRequest(Boolean issue) {
    }

    public record CommissionRequest(String role, PayeeKind payeeKind, Long payeeUserId, String payeeName, CommissionBasis basis, BigDecimal sharePercent, BigDecimal fixedAmount,
                                    String note) {
    }

    public record PostCommissionRequest(Long accountId) {
    }
}
