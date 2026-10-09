package com.posapp.recurringinvoice.dto;

import com.posapp.recurringinvoice.RecurrenceFrequency;
import com.posapp.recurringinvoice.RecurringInvoiceTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RecurringInvoiceTemplateDto(
        Long id,
        String name,
        Long customerId,
        String customerName,
        RecurrenceFrequency frequency,
        LocalDate nextRunDate,
        Integer dueInDays,
        String notes,
        BigDecimal discountPercent,
        String discountType,
        BigDecimal discountFixedAmount,
        boolean active,
        Instant lastRunAt,
        List<RecurringInvoiceTemplateLineDto> lines,
        /** Sum of every line's own (quantity * unitPrice), before discount/tax — a quick preview figure only; the actual amount invoiced each cycle is computed fresh at generation time (see InvoiceService.generateForRecurringInvoiceTemplate), same way a standalone invoice's own grand total is never just this. */
        BigDecimal estimatedAmount,
        /** The branch generated invoices are issued from; null on templates older than branches (= head office). */
        Long branchId
) {
    public static RecurringInvoiceTemplateDto from(RecurringInvoiceTemplate t) {
        BigDecimal estimated = t.getLines().stream()
                .map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new RecurringInvoiceTemplateDto(
                t.getId(), t.getName(), t.getCustomerId(), t.getCustomerName(),
                t.getFrequency(), t.getNextRunDate(), t.getDueInDays(), t.getNotes(),
                t.getDiscountPercent(), t.getDiscountType() != null ? t.getDiscountType().name() : null, t.getDiscountFixedAmount(),
                t.isActive(), t.getLastRunAt(),
                t.getLines().stream().map(RecurringInvoiceTemplateLineDto::from).toList(),
                estimated,
                t.getBranchId()
        );
    }
}
