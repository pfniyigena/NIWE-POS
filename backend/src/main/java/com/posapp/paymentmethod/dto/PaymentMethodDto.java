package com.posapp.paymentmethod.dto;

import com.posapp.paymentmethod.PaymentMethodEntity;

public record PaymentMethodDto(Long id, String code, String name, boolean active, boolean cash, boolean credit, int sortOrder) {
    public static PaymentMethodDto from(PaymentMethodEntity pm) {
        return new PaymentMethodDto(pm.getId(), pm.getCode(), pm.getName(), pm.isActive(), pm.isCash(), pm.isCredit(), pm.getSortOrder());
    }
}
