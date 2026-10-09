package com.posapp.helpdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST /api/public/helpdesk/tickets — the guest "Submit a ticket" form, no login
 * required (see {@code PublicHelpDeskController}'s own javadoc). Always creates a {@code
 * HelpDeskTicketType.CUSTOMER} ticket; there's no public way to raise an INTERNAL one. No {@code
 * priority} field — Fourier's own scope decision was that a guest doesn't triage priority, staff
 * do that on review (same reasoning as not exposing a public ticket list: self-service input, not
 * self-service management). {@link #requesterEmail} is required (not just optional) because it
 * doubles as the credential half of the public "check my ticket" lookup — see {@code
 * HelpDeskTicketRepository.findByTicketNumberIgnoreCaseAndRequesterEmailIgnoreCase}.
 */
public class PublicHelpDeskTicketRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    private Long categoryId;

    @NotBlank(message = "Your name is required")
    private String requesterName;

    @NotBlank(message = "Your email is required")
    @Email(message = "Enter a valid email")
    private String requesterEmail;

    private String requesterPhone;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getRequesterPhone() { return requesterPhone; }
    public void setRequesterPhone(String requesterPhone) { this.requesterPhone = requesterPhone; }
}
