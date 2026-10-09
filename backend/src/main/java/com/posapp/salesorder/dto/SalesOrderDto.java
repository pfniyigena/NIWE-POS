package com.posapp.salesorder.dto;

import com.posapp.salesorder.SalesOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderDto(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        String status,
        Instant orderDate,
        Instant expectedDate,
        LocalDate dueDate,
        String notes,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — now tax-inclusive (subtotalAmount + taxAmount); was previously just the sum of quantityOrdered * unitPrice. */
        BigDecimal totalAmount,
        /** Sum of every line's pre-tax contribution — see SalesOrderService.subtotalAmount. */
        BigDecimal subtotalAmount,
        /** Sum of every line's own tax — zero when no line has a tax rate assigned. See SalesOrderService.taxAmount. */
        BigDecimal taxAmount,
        /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discountAmount, purely informational (already folded into subtotalAmount/totalAmount above). Zero whenever the feature is off or no line has one. */
        BigDecimal discountAmount,
        /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole order (GLOBAL mode only — see SalesOrder.discountPercent's javadoc). Zero when discount was entered per line instead, or not at all. */
        BigDecimal discountPercent,
        /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was entered; see SalesOrder.discountType's javadoc. */
        String discountType,
        /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. See SalesOrder.discountFixedAmount's javadoc. */
        BigDecimal discountFixedAmount,
        List<SalesOrderLineDto> lines,
        /** Task 3: "Sale Order Return" — see CustomerCreditService.amountPaidForSalesOrder/outstandingForSalesOrder/returnedAmountForSalesOrder/refundDueForSalesOrder for what each means, and for a DRAFT/CANCELLED order. */
        BigDecimal amountPaid,
        BigDecimal amountOutstanding,
        BigDecimal amountReturned,
        BigDecimal refundDue,
        /** Where this order's goods were taken from, as distinct "Warehouse / Location" labels (oldest first). Empty if nothing was fulfilled from a named location (not fulfilled yet, or older fulfilments from the primary warehouse's normal stock). */
        List<String> fulfilledFrom,
        /** The branch this order is sold from; null on legacy rows (= head office). */
        Long branchId
) {
    public static SalesOrderDto from(SalesOrder so, BigDecimal totalAmount, BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal discountAmount,
                                      BigDecimal amountPaid, BigDecimal amountOutstanding, BigDecimal amountReturned, BigDecimal refundDue,
                                      List<String> fulfilledFrom) {
        var customer = so.getCustomer();
        return new SalesOrderDto(
                so.getId(),
                so.getOrderNumber(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                so.getStatus().name(),
                so.getOrderDate(),
                so.getExpectedDate(),
                so.getDueDate(),
                so.getNotes(),
                totalAmount,
                subtotalAmount,
                taxAmount,
                discountAmount,
                so.getDiscountPercent(),
                so.getDiscountType() != null ? so.getDiscountType().name() : null,
                so.getDiscountFixedAmount(),
                so.getLines().stream().map(l -> SalesOrderLineDto.from(so.getDiscountPercent(), l)).toList(),
                amountPaid,
                amountOutstanding,
                amountReturned,
                refundDue,
                fulfilledFrom,
                so.getBranchId()
        );
    }
}
