package com.posapp.realestate.dto;

import com.posapp.realestate.SaleNoteKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the collections screen reads: who owes what on the unit sales, and what has been done about it. */
public final class RealEstateCollectionDtos {

    private RealEstateCollectionDtos() {
    }

    /**
     * One sold unit with money to collect. {@code basis} is PLAN (instalments with due dates) or INVOICE (a balance with no schedule). A promise is the latest promise to pay
     * on the sale; it is {@code promiseBroken} when its day has passed and the buyer is still overdue.
     */
    public record CollectionRow(Long saleId, String saleCode, Long projectId, String projectName, String unitNo, String buyerName, String buyerPhone, String buyerEmail, Long agentUserId,
                                String agentName, String basis, BigDecimal agreedPrice, BigDecimal paid, BigDecimal outstanding, BigDecimal overdueAmount, int overdueInstallments,
                                long oldestDaysOverdue, LocalDate nextDueDate, BigDecimal nextDueAmount, Long planId, SaleNoteKind lastNoteKind, String lastNoteText, Instant lastNoteAt,
                                LocalDate promiseDate, boolean promiseBroken) {
    }

    public record CollectionBoard(LocalDate today, int soonDays, BigDecimal overdueTotal, BigDecimal dueSoonTotal, BigDecimal unscheduledTotal, boolean installmentsModuleOn,
                                  boolean mailConfigured, List<CollectionRow> overdue, List<CollectionRow> dueSoon, List<CollectionRow> unscheduled) {
    }

    public record SaleNoteDto(Long id, SaleNoteKind kind, String text, LocalDate promiseDate, String createdByName, Instant createdAt) {
    }

    public record SaleNoteRequest(SaleNoteKind kind, String text, LocalDate promiseDate) {
    }

    /** The outcome of an e-mail to the buyer: who it went to. */
    public record MailedBuyer(String sentTo, String message) {
    }
}
