package com.posapp.inventory.dto;

import com.posapp.inventory.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderDto(
        Long id,
        String orderNumber,
        Long supplierId,
        String supplierName,
        String status,
        Instant orderDate,
        Instant expectedDate,
        String notes,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — this document never carried a total of its own before; now tax-inclusive (subtotalAmount + taxAmount). See SupplierPaymentService.totalCost. */
        BigDecimal totalAmount,
        /** Sum of every line's pre-tax contribution — see SupplierPaymentService.netCost. */
        BigDecimal subtotalAmount,
        /** Sum of every line's own tax — zero when no line has a tax rate assigned. See SupplierPaymentService.taxCost. */
        BigDecimal taxAmount,
        /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discountAmount, purely informational (already folded into subtotalAmount/totalAmount above). Zero whenever the feature is off or no line has one. */
        BigDecimal discountAmount,
        /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole order (GLOBAL mode only — see PurchaseOrder.discountPercent's javadoc). Zero when discount was entered per line instead, or not at all. */
        BigDecimal discountPercent,
        /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was entered; see PurchaseOrder.discountType's javadoc. */
        String discountType,
        /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. See PurchaseOrder.discountFixedAmount's javadoc. */
        BigDecimal discountFixedAmount,
        List<PurchaseOrderLineDto> lines,
        /** Task 5: "Add Record payment on purchase order view or list (if not paid)" — see SupplierPaymentService.amountPaidForOrder/amountOwedForOrder for what each means for a DRAFT/CANCELLED order. */
        BigDecimal amountPaid,
        BigDecimal amountOutstanding,
        /** Task 4: "Supplier Purchase Return" — see SupplierPaymentService.returnedAmountForOrder/refundDueFromSupplierForOrder. */
        BigDecimal amountReturned,
        BigDecimal refundDue,
        /** Fourier: "same workflow as Sale order" — null means no bill has been generated for this order yet; see com.posapp.bill.BillRepository.findByPurchaseOrderId's javadoc. Drives the Purchase Order detail screen's "Generate bill"/"View bill" choice, same shape as the Sale Order detail screen's own invoice field. */
        Long billId,
        String billNumber,
        /** Multi-warehouse phase 2 — the warehouse this order receives into; the primary warehouse's id/name when none was chosen (legacy orders). Filled in by PurchaseOrderService.toDto, which knows the primary warehouse. */
        Long receivingWarehouseId,
        String receivingWarehouseName,
        /** The branch of the receiving warehouse; null on legacy rows (= head office). */
        Long branchId,
        /** Landed costs (freight, duty, ...) added to product cost while receiving this order. */
        BigDecimal landedCostTotal
) {
    public static PurchaseOrderDto from(PurchaseOrder po, BigDecimal totalAmount, BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal discountAmount,
                                         BigDecimal amountPaid, BigDecimal amountOutstanding,
                                         BigDecimal amountReturned, BigDecimal refundDue, Long billId, String billNumber,
                                         Long receivingWarehouseId, String receivingWarehouseName) {
        var supplier = po.getSupplier();
        return new PurchaseOrderDto(
                po.getId(),
                po.getOrderNumber(),
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getName() : null,
                po.getStatus().name(),
                po.getOrderDate(),
                po.getExpectedDate(),
                po.getNotes(),
                totalAmount,
                subtotalAmount,
                taxAmount,
                discountAmount,
                po.getDiscountPercent(),
                po.getDiscountType() != null ? po.getDiscountType().name() : null,
                po.getDiscountFixedAmount(),
                po.getLines().stream().map(l -> PurchaseOrderLineDto.from(po.getDiscountPercent(), l)).toList(),
                amountPaid,
                amountOutstanding,
                amountReturned,
                refundDue,
                billId,
                billNumber,
                receivingWarehouseId,
                receivingWarehouseName,
                po.getBranchId(),
                po.getLandedCostTotal() != null ? po.getLandedCostTotal() : BigDecimal.ZERO
        );
    }
}
