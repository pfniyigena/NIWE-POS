package com.posapp.inventory.dto;

import com.posapp.inventory.Supplier;

public record SupplierDto(
        Long id,
        String name,
        String contactName,
        String phone,
        String email,
        String taxId,
        String address,
        String notes,
        boolean active
) {
    public static SupplierDto from(Supplier s) {
        return new SupplierDto(
                s.getId(),
                s.getName(),
                s.getContactName(),
                s.getPhone(),
                s.getEmail(),
                s.getTaxId(),
                s.getAddress(),
                s.getNotes(),
                s.isActive()
        );
    }
}
