package com.posapp.branch.dto;

import com.posapp.branch.Branch;

public record BranchDto(Long id, String code, String name, String address, String phone, String email,
                        String receiptHeader, String taxId, String efrisBranchCode, String documentPrefix,
                        boolean active, boolean headquarters) {

    public static BranchDto from(Branch b) {
        return new BranchDto(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getPhone(), b.getEmail(),
                b.getReceiptHeader(), b.getTaxId(), b.getEfrisBranchCode(), b.getDocumentPrefix(),
                b.isActive(), b.isHeadquarters());
    }
}
