package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskCannedResponse;

public record HelpDeskCannedDto(Long id, String title, String body, Long categoryId, boolean active) {
    public static HelpDeskCannedDto from(HelpDeskCannedResponse c) {
        return new HelpDeskCannedDto(c.getId(), c.getTitle(), c.getBody(), c.getCategoryId(), c.isActive());
    }
}
