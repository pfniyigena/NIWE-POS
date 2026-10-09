package com.posapp.catalog.dto;

import com.posapp.catalog.Brand;

public record BrandDto(Long id, String name) {
    public static BrandDto from(Brand b) {
        return new BrandDto(b.getId(), b.getName());
    }
}
