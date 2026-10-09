package com.posapp.recurringinvoice.dto;

import com.posapp.recurringinvoice.RecurringInvoiceTemplateLine;

import java.math.BigDecimal;

public record RecurringInvoiceTemplateLineDto(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String description,
        BigDecimal discountPercent
) {
    public static RecurringInvoiceTemplateLineDto from(RecurringInvoiceTemplateLine line) {
        var product = line.getProduct();
        return new RecurringInvoiceTemplateLineDto(
                line.getId(),
                product != null ? product.getId() : null,
                product != null ? product.getName() : null,
                line.getQuantity(),
                line.getUnitPrice(),
                line.getDescription(),
                line.getDiscountPercent()
        );
    }
}
