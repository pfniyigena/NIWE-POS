package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskEvent;
import com.posapp.helpdesk.HelpDeskEventKind;

import java.time.Instant;

public record HelpDeskEventDto(Long id, HelpDeskEventKind kind, String detail, String by, Instant at) {
    public static HelpDeskEventDto from(HelpDeskEvent e) {
        return new HelpDeskEventDto(e.getId(), e.getKind(), e.getDetail(), e.getCreatedByName(), e.getCreatedAt());
    }
}
