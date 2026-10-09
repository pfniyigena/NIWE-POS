package com.posapp.restaurant.dto;

import com.posapp.restaurant.MenuCategory;

public record MenuCategoryDto(Long id, String name, int sortOrder, boolean active) {
    public static MenuCategoryDto from(MenuCategory c) {
        return new MenuCategoryDto(c.getId(), c.getName(), c.getSortOrder(), c.isActive());
    }
}
