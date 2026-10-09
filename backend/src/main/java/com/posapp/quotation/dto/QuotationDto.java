package com.posapp.quotation.dto;

import com.posapp.quotation.Quotation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuotationDto(
        Long id,
        String quoteNumber,
        Long customerId,
        String customerName,
        /** Fourier: "sending ... quotations ... to the customer's email" — the live email straight off Customer, for the admin UI to pre-fill the "email this" prompt with; the actual send always re-reads Customer itself rather than trusting this snapshot, see QuotationService.emailToCustomer. */
        String customerEmail,
        String status,
        Instant quoteDate,
        LocalDate validUntil,
        String notes,
        /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — now tax-inclusive (subtotalAmount + taxAmount); was previously just the sum of quantity * unitPrice. */
        BigDecimal totalAmount,
        /** Sum of every line's pre-tax contribution — see QuotationCalculator.subtotalAmount. */
        BigDecimal subtotalAmount,
        /** Sum of every line's own tax — zero when no line has a tax rate assigned. See QuotationCalculator.taxAmount. */
        BigDecimal taxAmount,
        /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discountAmount, purely informational (already folded into subtotalAmount/totalAmount above). Zero whenever the feature is off or no line has one. */
        BigDecimal discountAmount,
        /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole quotation (GLOBAL mode only — see Quotation.discountPercent's javadoc). Zero when discount was entered per line instead, or not at all. */
        BigDecimal discountPercent,
        /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was entered; see Quotation.discountType's javadoc. */
        String discountType,
        /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. See Quotation.discountFixedAmount's javadoc. */
        BigDecimal discountFixedAmount,
        List<QuotationLineDto> lines,
        Long convertedSalesOrderId,
        String convertedSalesOrderNumber,
        /** The branch this quotation is issued from; null on legacy rows (= head office). */
        Long branchId
) {
    public static QuotationDto from(Quotation q, BigDecimal totalAmount, BigDecimal subtotalAmount, BigDecimal taxAmount, BigDecimal discountAmount) {
        var customer = q.getCustomer();
        return new QuotationDto(
                q.getId(),
                q.getQuoteNumber(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                customer != null ? customer.getEmail() : null,
                q.getStatus().name(),
                q.getQuoteDate(),
                q.getValidUntil(),
                q.getNotes(),
                totalAmount,
                subtotalAmount,
                taxAmount,
                discountAmount,
                q.getDiscountPercent(),
                q.getDiscountType() != null ? q.getDiscountType().name() : null,
                q.getDiscountFixedAmount(),
                q.getLines().stream().map(l -> QuotationLineDto.from(q.getDiscountPercent(), l)).toList(),
                q.getConvertedSalesOrderId(),
                q.getConvertedSalesOrderNumber(),
                q.getBranchId()
        );
    }
}
