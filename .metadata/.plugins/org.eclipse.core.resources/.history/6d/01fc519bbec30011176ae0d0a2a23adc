package com.posapp.bill.dto;

import com.posapp.bill.Bill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BillDto(
        Long id,
        String billNumber,
        Long purchaseOrderId,
        String purchaseOrderNumber,
        Long supplierId,
        String supplierName,
        String status,
        Instant billDate,
        String notes,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — now tax-inclusive (subtotalAmount + taxAmount); was previously just the sum of every line's lineTotal, with no tax at all. */
        BigDecimal totalAmount,
        /** Sum of every line's pre-tax contribution — see BillService.subtotalAmount. */
        BigDecimal subtotalAmount,
        /** Sum of every line's own tax — zero when no line has a tax rate assigned. See BillService.taxAmount. */
        BigDecimal taxAmount,
        /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discountAmount, purely informational (already folded into subtotalAmount/totalAmount above). Zero whenever the feature is off or no line has one. */
        BigDecimal discountAmount,
        /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole bill (carried verbatim from the originating Purchase Order — see Bill.discountPercent's javadoc). Zero when discount was entered per line instead, or not at all. */
        BigDecimal discountPercent,
        /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was entered on the originating Purchase Order, carried verbatim; see Bill.discountType's javadoc. */
        String discountType,
        /** The flat currency amount actually typed on the originating Purchase Order, when discountType is FIXED_AMOUNT; null otherwise. See Bill.discountFixedAmount's javadoc. */
        BigDecimal discountFixedAmount,
        List<BillLineDto> lines,
        Instant voidedAt,
        String voidReason,
        /** Fourier: "Refunds equivalent with Credit note" — null until this bill is voided; see Bill.refundNumber's javadoc. */
        String refundNumber,
        /** Zero for a DRAFT/VOID bill — only an ISSUED bill is ever payable; see SupplierPaymentService.amountPaidForBill/outstandingForBill's javadoc. */
        BigDecimal amountPaid,
        BigDecimal amountOutstanding,
        /** The branch this bill belongs to; null on legacy rows (= head office). */
        Long branchId
) {
    public static BillDto from(Bill b, BigDecimal amountPaid, BigDecimal amountOutstanding, BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal discountAmount) {
        return new BillDto(
                b.getId(),
                b.getBillNumber(),
                b.getPurchaseOrderId(),
                b.getPurchaseOrderNumber(),
                b.getSupplierId(),
                b.getSupplierName(),
                b.getStatus().name(),
                b.getBillDate(),
                b.getNotes(),
                b.getTotalAmount(),
                subtotalAmount,
                taxAmount,
                discountAmount,
                b.getDiscountPercent(),
                b.getDiscountType() != null ? b.getDiscountType().name() : null,
                b.getDiscountFixedAmount(),
                b.getLines().stream().map(BillLineDto::from).toList(),
                b.getVoidedAt(),
                b.getVoidReason(),
                b.getRefundNumber(),
                amountPaid,
                amountOutstanding,
                b.getBranchId()
        );
    }
}
