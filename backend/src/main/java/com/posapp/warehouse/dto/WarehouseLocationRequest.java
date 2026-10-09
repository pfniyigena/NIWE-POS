package com.posapp.warehouse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — {@code type} is required (the
 * enum's plain name, e.g. "SALES"); {@code WarehouseService#createLocation} parses and validates
 * it, including the "at most one RECEIVING per warehouse" invariant. Not annotated
 * {@code @NotBlank} here deliberately — an invalid/unknown value should get the same
 * friendly {@code ApiException.badRequest} treatment as any other domain validation, not a raw
 * bean-validation 400.
 */
public record WarehouseLocationRequest(@NotBlank String name, String type) {
}
