package com.posapp.branch.dto;

import com.posapp.branch.Branch;

/**
 * What a till needs to print a branch's receipt header — readable by any signed-in user (the full
 * branch record is ADMIN-only). Every field except {@code name} is nullable; a null means "use the
 * business-wide value".
 */
public record BranchReceiptInfo(Long id, String name, String address, String phone, String email,
                                String receiptHeader, String taxId) {

    public static BranchReceiptInfo from(Branch b) {
        return new BranchReceiptInfo(b.getId(), b.getName(), b.getAddress(), b.getPhone(), b.getEmail(),
                b.getReceiptHeader(), b.getTaxId());
    }
}
