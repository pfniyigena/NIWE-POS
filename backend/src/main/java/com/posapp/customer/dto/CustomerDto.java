package com.posapp.customer.dto;

import com.posapp.customer.Customer;

import java.math.BigDecimal;

public record CustomerDto(
        Long id,
        String name,
        String phone,
        String email,
        String taxId,
        boolean creditEnabled,
        BigDecimal creditLimit,
        Integer defaultCreditTermDays,
        /** See Customer.walkIn's javadoc — the terminal pre-selects whichever customer has this set. */
        boolean walkIn
) {
    public static CustomerDto from(Customer c) {
        return new CustomerDto(
                c.getId(),
                c.getName(),
                c.getPhone(),
                c.getEmail(),
                c.getTaxId(),
                c.isCreditEnabled(),
                c.getCreditLimit(),
                c.getDefaultCreditTermDays(),
                c.isWalkIn()
        );
    }
}
