package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.PmFeeInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the fee invoice screens read. */
public final class PropertyMgmtFeeInvoiceDtos {

    private PropertyMgmtFeeInvoiceDtos() {
    }

    public record FeeInvoiceLineDto(Long id, Long propertyId, String propertyName, String description, BigDecimal baseAmount, BigDecimal feePercent, BigDecimal netAmount, BigDecimal vatAmount, BigDecimal total) {
    }

    public record FeeInvoiceRow(Long id, String number, PmFeeInvoiceStatus status, LocalDate issueDate, Long ownerId, String ownerName, String ownerTaxNumber, Long statementId, String statementCode,
                                LocalDate periodFrom, LocalDate periodTo, BigDecimal netAmount, BigDecimal vatPercent, BigDecimal vatAmount, BigDecimal totalAmount) {
    }

    /** taxInvoice = VAT was charged (the document is headed Tax invoice); otherwise it is a plain invoice. */
    public record FeeInvoiceDto(FeeInvoiceRow invoice, boolean taxInvoice, String ownerAddress, String sellerName, String sellerTaxId, String sellerAddress, String sellerPhone, String sellerEmail,
                                String issuedByName, String voidReason, Instant voidedAt, List<FeeInvoiceLineDto> lines, boolean statementExists, boolean canManage) {
    }

    /** What the invoices in the filter add up to (void ones left out). */
    public record FeeInvoiceTotals(long count, BigDecimal net, BigDecimal vat, BigDecimal total) {
    }
}
