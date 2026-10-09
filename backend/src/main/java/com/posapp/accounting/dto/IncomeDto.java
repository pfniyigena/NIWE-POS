package com.posapp.accounting.dto;

import com.posapp.accounting.Income;
import com.posapp.accounting.PayeeType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeDto(
        Long id,
        LocalDate incomeDate,
        Long accountId,
        String accountCode,
        String accountName,
        PayeeType payeeType,
        Long payeeId,
        String payeeName,
        BigDecimal amount,
        boolean received,
        String memo,
        Long costCenterId,
        String costCenterName,
        /** The branch this income belongs to; null on legacy rows (= head office). */
        Long branchId
) {
    public static IncomeDto from(Income i) {
        var account = i.getAccount();
        var costCenter = i.getCostCenter();
        return new IncomeDto(i.getId(), i.getIncomeDate(),
                account != null ? account.getId() : null, account != null ? account.getCode() : null, account != null ? account.getName() : null,
                i.getPayeeType(), i.getPayeeId(), i.getPayeeName(), i.getAmount(), i.isReceived(), i.getMemo(),
                costCenter != null ? costCenter.getId() : null, costCenter != null ? costCenter.getName() : null,
                i.getBranchId());
    }
}
