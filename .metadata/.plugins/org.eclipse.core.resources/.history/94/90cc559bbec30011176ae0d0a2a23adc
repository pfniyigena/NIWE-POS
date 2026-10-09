package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskCategory;

public record HelpDeskCategoryDto(Long id, String name, String description, boolean active) {
    public static HelpDeskCategoryDto from(HelpDeskCategory c) {
        return new HelpDeskCategoryDto(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }
}
