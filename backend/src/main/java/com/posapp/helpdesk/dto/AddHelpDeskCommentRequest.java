package com.posapp.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/helpdesk/tickets/{id}/comments. internal defaults true (private note) — a staff member unchecks it to leave an update visible on the public lookup page instead. */
public class AddHelpDeskCommentRequest {
    @NotBlank(message = "Comment is required")
    private String body;

    private boolean internal = true;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
}
