package com.posapp.accounting.dto;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountType;

public record AccountDto(
        Long id,
        String code,
        String name,
        AccountType type,
        String description,
        boolean active,
        boolean systemAccount
) {
    public static AccountDto from(Account a) {
        return new AccountDto(a.getId(), a.getCode(), a.getName(), a.getType(), a.getDescription(), a.isActive(), a.isSystemAccount());
    }
}
