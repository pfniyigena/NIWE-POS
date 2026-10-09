package com.posapp.recurringexpense.dto;

import com.posapp.accounting.PayeeType;
import com.posapp.recurringexpense.RecurrenceFrequency;
import com.posapp.recurringexpense.RecurringExpenseTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecurringExpenseTemplateDto(
        Long id,
        String name,
        Long accountId,
        String accountCode,
        String accountName,
        PayeeType payeeType,
        Long payeeId,
        String payeeName,
        BigDecimal amount,
        boolean paid,
        String memo,
        Long costCenterId,
        String costCenterName,
        RecurrenceFrequency frequency,
        LocalDate nextRunDate,
        boolean active,
        Instant lastRunAt
) {
    public static RecurringExpenseTemplateDto from(RecurringExpenseTemplate t) {
        var account = t.getAccount();
        var costCenter = t.getCostCenter();
        return new RecurringExpenseTemplateDto(t.getId(), t.getName(),
                account != null ? account.getId() : null, account != null ? account.getCode() : null, account != null ? account.getName() : null,
                t.getPayeeType(), t.getPayeeId(), t.getPayeeName(), t.getAmount(), t.isPaid(), t.getMemo(),
                costCenter != null ? costCenter.getId() : null, costCenter != null ? costCenter.getName() : null,
                t.getFrequency(), t.getNextRunDate(), t.isActive(), t.getLastRunAt());
    }
}
