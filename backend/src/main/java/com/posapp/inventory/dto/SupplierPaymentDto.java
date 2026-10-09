package com.posapp.inventory.dto;

import com.posapp.inventory.PayableType;
import com.posapp.inventory.SupplierPayment;
import com.posapp.inventory.SupplierPaymentAllocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A recorded supplier payment plus how it was allocated — the accounts-payable mirror of {@code
 * com.posapp.credit.dto.CustomerPaymentDto}: same shape backs the immediate response right after
 * recording a payment and each row of payment history. {@code supplierBalanceAfter} is always the
 * supplier's *current* total outstanding balance at the moment this DTO is built, not a snapshot
 * frozen at the time this particular payment was made — see {@code CustomerPaymentDto}'s javadoc
 * for the identical reasoning on the customer side.
 */
public record SupplierPaymentDto(
        Long id,
        Long supplierId,
        String supplierName,
        BigDecimal amount,
        Instant paidAt,
        /** See SupplierPayment.method's javadoc — null for a payment recorded through the general supplier-wide allocation screen, or before this field existed. */
        String method,
        String note,
        String receiptNumber,
        String recordedByName,
        List<AllocationDto> allocations,
        BigDecimal supplierBalanceAfter
) {
    /** {@code payableType} distinguishes a Purchase-Order-direct allocation from a Bill one — see {@code SupplierPaymentAllocation}'s "exclusive arc" javadoc. */
    public record AllocationDto(Long purchaseOrderId, PayableType payableType, String orderNumber, BigDecimal amountApplied) {
    }

    public static SupplierPaymentDto from(SupplierPayment payment, BigDecimal supplierBalanceAfter) {
        return new SupplierPaymentDto(
                payment.getId(),
                payment.getSupplier().getId(),
                payment.getSupplier().getName(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getMethod(),
                payment.getNote(),
                payment.getReceiptNumber(),
                payment.getRecordedBy() != null ? payment.getRecordedBy().getFullName() : "—",
                payment.getAllocations().stream()
                        .map(SupplierPaymentDto::allocationFrom)
                        .toList(),
                supplierBalanceAfter
        );
    }

    /** Branches on which side of the two-way "exclusive arc" is set — see SupplierPaymentAllocation's javadoc. */
    private static AllocationDto allocationFrom(SupplierPaymentAllocation allocation) {
        if (allocation.getBill() != null) {
            return new AllocationDto(
                    allocation.getBill().getId(),
                    PayableType.BILL,
                    allocation.getBill().getBillNumber(),
                    allocation.getAmountApplied()
            );
        }
        return new AllocationDto(
                allocation.getPurchaseOrder().getId(),
                PayableType.PURCHASE_ORDER,
                allocation.getPurchaseOrder().getOrderNumber(),
                allocation.getAmountApplied()
        );
    }
}
