package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskCategory;

/** The guest form's own category picker — id+name only, same "no internal fields leaked to an anonymous caller" treatment as PublicTableDto. */
public record PublicHelpDeskCategoryDto(Long id, String name) {
    public static PublicHelpDeskCategoryDto from(HelpDeskCategory c) {
        return new PublicHelpDeskCategoryDto(c.getId(), c.getName());
    }
}
