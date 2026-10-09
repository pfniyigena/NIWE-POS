package com.posapp.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;

/** Admin > Accounting > Journal Entries > New — a manual entry, distinct from every automatic posting AccountingService makes. */
public class CreateJournalEntryRequest {

    private LocalDate entryDate;

    private String memo;

    @NotEmpty(message = "A journal entry needs at least two lines")
    @Valid
    private List<LineEntry> lines;

    public static class LineEntry {
        @NotNull(message = "Account is required")
        private Long accountId;

        @PositiveOrZero(message = "Debit cannot be negative")
        private java.math.BigDecimal debit = java.math.BigDecimal.ZERO;

        @PositiveOrZero(message = "Credit cannot be negative")
        private java.math.BigDecimal credit = java.math.BigDecimal.ZERO;

        private String description;

        /** Optional — see JournalEntryLine.costCenter's own javadoc. */
        private Long costCenterId;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public java.math.BigDecimal getDebit() { return debit; }
        public void setDebit(java.math.BigDecimal debit) { this.debit = debit; }
        public java.math.BigDecimal getCredit() { return credit; }
        public void setCredit(java.math.BigDecimal credit) { this.credit = credit; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Long getCostCenterId() { return costCenterId; }
        public void setCostCenterId(Long costCenterId) { this.costCenterId = costCenterId; }
    }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public List<LineEntry> getLines() { return lines; }
    public void setLines(List<LineEntry> lines) { this.lines = lines; }
}
