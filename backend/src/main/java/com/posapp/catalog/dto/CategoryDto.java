package com.posapp.catalog.dto;

import com.posapp.catalog.Category;

public record CategoryDto(Long id, String name, String colorHex) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getColorHex());
    }
}
