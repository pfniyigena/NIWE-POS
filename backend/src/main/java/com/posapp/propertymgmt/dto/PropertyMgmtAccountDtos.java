package com.posapp.propertymgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The tenant's account (what was charged and paid) and rent receipts. */
public final class PropertyMgmtAccountDtos {

    private PropertyMgmtAccountDtos() {}

    /** One line of a tenant's account: rent charged (kind RENT) or a payment received (kind PAYMENT), with the running balance after it. */
    public record AccountLine(LocalDate date, String kind, String description, String reference, BigDecimal charge, BigDecimal payment, BigDecimal balance, Long paymentId) {
    }

    /** What a tenant was charged and paid between two dates. {@code opening} is what was owed before {@code from}; {@code overdue} is what is late today. */
    public record TenantAccount(Long tenantId, String tenantCode, String tenantName, String tenantEmail, LocalDate from, LocalDate to, BigDecimal opening, List<AccountLine> lines,
                                BigDecimal charged, BigDecimal paid, BigDecimal closing, BigDecimal overdue) {
    }

    /** A payment received for rent. {@code amount} is the part applied to rent invoices. */
    public record PaymentRow(Long paymentId, String receiptNumber, LocalDate paidDate, String method, String reference, BigDecimal amount, Long tenantId, String tenantName,
                             Long leaseId, String leaseCode, String unit, List<String> invoices) {
    }

    public record ReceiptLine(String invoiceNumber, String leaseCode, String period, BigDecimal invoiceTotal, BigDecimal paidNow, BigDecimal balanceAfter) {
    }

    public record Receipt(PaymentRow payment, String tenantEmail, String recordedBy, List<ReceiptLine> lines) {
    }

    /** {@code from}/{@code to} default to the usual period; {@code to} (the address) defaults to the tenant's e-mail. */
    public record AccountMailRequest(LocalDate from, LocalDate to, String email, String message) {
    }

    public record ReceiptMailRequest(String email, String message) {
    }

    public record MailSent(String sentTo) {
    }
}
