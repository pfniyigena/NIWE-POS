package com.posapp.accounting.dto;

import com.posapp.accounting.JournalEntry;
import com.posapp.accounting.JournalEntryLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record JournalEntryDto(
        Long id,
        String entryNumber,
        Instant entryDate,
        String memo,
        String sourceType,
        Long sourceId,
        List<LineDto> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit
) {
    public record LineDto(Long id, Long accountId, String accountCode, String accountName, BigDecimal debit, BigDecimal credit, String description,
                           Long costCenterId, String costCenterName) {
        public static LineDto from(JournalEntryLine line) {
            var account = line.getAccount();
            var costCenter = line.getCostCenter();
            return new LineDto(line.getId(), account != null ? account.getId() : null,
                    account != null ? account.getCode() : null, account != null ? account.getName() : null,
                    line.getDebit(), line.getCredit(), line.getDescription(),
                    costCenter != null ? costCenter.getId() : null, costCenter != null ? costCenter.getName() : null);
        }
    }

    public static JournalEntryDto from(JournalEntry entry) {
        List<LineDto> lines = entry.getLines().stream().map(LineDto::from).toList();
        BigDecimal totalDebit = lines.stream().map(LineDto::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(LineDto::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new JournalEntryDto(entry.getId(), entry.getEntryNumber(), entry.getEntryDate(), entry.getMemo(),
                entry.getSourceType(), entry.getSourceId(), lines, totalDebit, totalCredit);
    }
}
