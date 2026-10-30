package com.posapp.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddHelpDeskCommentRequest {
    @NotBlank(message = "Comment is required")
    @Size(max = 8000, message = "A comment can be at most 8000 characters")
    private String body;
    private boolean internal = true;
    /** With a visible reply: also set the ticket to waiting for the customer (pauses the SLA clock). */
    private boolean waitForCustomer;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
    public boolean isWaitForCustomer() { return waitForCustomer; }
    public void setWaitForCustomer(boolean waitForCustomer) { this.waitForCustomer = waitForCustomer; }
}
