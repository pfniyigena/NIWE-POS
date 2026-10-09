package com.posapp.branch.dto;

import jakarta.validation.constraints.NotBlank;

/** Deliberately has no {@code headquarters} field — see Branch's javadoc. */
public record BranchRequest(@NotBlank String code, @NotBlank String name, String address, String phone,
                            String email, String receiptHeader, String taxId, String efrisBranchCode,
                            String documentPrefix, Boolean active) {
}
