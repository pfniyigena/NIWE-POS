package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.StatementLineKind;
import com.posapp.propertymgmt.StatementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the owner statement screens read. */
public final class PropertyMgmtStatementDtos {

    private PropertyMgmtStatementDtos() {
    }

    /** managementFee and repairs are positive amounts taken off; adjustments are signed; netPayable = rent - fee - repairs + adjustments. */
    public record StatementRow(Long id, String code, Long ownerId, String ownerName, LocalDate periodFrom, LocalDate periodTo, StatementStatus status, BigDecimal rentCollected,
                               BigDecimal managementFee, BigDecimal vat, BigDecimal repairs, BigDecimal adjustments, BigDecimal netPayable, LocalDate paidDate) {
    }

    /** amount is signed from the owner's side. editable: a manual line on a draft. */
    public record StatementLineDto(Long id, StatementLineKind kind, Long propertyId, String propertyName, Long unitId, String unitNo, Long tenantId, String tenantName, LocalDate date,
                                   String description, String reference, BigDecimal baseAmount, BigDecimal feePercent, BigDecimal amount, boolean editable) {
    }

    public record StatementPropertyRow(Long propertyId, String propertyName, BigDecimal rent, BigDecimal feePercent, BigDecimal fee, BigDecimal vat, BigDecimal repairs, BigDecimal adjustments,
                                       BigDecimal net) {
    }

    public record StatementDto(StatementRow statement, String notes, List<StatementLineDto> lines, List<StatementPropertyRow> properties, List<String> warnings, String ownerPhone,
                               String ownerBankName, String ownerBankAccount, LocalDate finalizedDate, String finalizedByName, String paidMethod, String paidReference,
                               boolean canManage, String createdByName, Instant createdAt, boolean postedToAccounts, String ownerEmail, Instant emailedAt, String emailedTo) {
    }

    /** sent: e-mails sent. skipped: left out (no address, already sent...). failed: the mail server refused. notes: one line for each skipped or failed statement. */
    public record EmailAllResult(int sent, int skipped, int failed, List<String> notes) {
    }

    /** ran: false when nothing was due (the message says why). */
    public record AutoRunResult(boolean ran, String message, LocalDate periodFrom, LocalDate periodTo, int created, int agreed, int emailed, List<String> notes) {
    }

    public record GenerateAllResult(int created, int skipped, List<String> notes, List<StatementRow> statements) {
    }

    /** drafts: statements still being prepared. toPay: agreed statements not yet paid, with the amount owed to owners. */
    public record StatementSummary(long drafts, long toPay, BigDecimal toPayAmount) {
    }
}
