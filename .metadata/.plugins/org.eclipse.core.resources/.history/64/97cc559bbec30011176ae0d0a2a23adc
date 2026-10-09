package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskPriority;
import com.posapp.helpdesk.HelpDeskTicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body for POST/PUT /api/helpdesk/tickets — the staff "New ticket"/edit form. Mirrors {@code
 * CreateSubscriptionRequest}'s "most fields editable, type fixed after creation" shape: {@link
 * #type} is read on create but ignored on update (see {@code HelpDeskTicketService.update}'s own
 * javadoc) — switching a ticket between Customer/Internal after the fact would leave its
 * requester/customer fields in an inconsistent state, so a miscategorized ticket is closed and
 * recreated instead, same as this app never lets a Sale Order's channel change after the fact.
 */
public class HelpDeskTicketRequest {

    @NotNull(message = "Type is required")
    private HelpDeskTicketType type;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    private Long categoryId;

    private HelpDeskPriority priority;

    /** Links this ticket to an existing Customer record — optional even for a CUSTOMER-type ticket (a true walk-in with no record yet just gets requesterName/Email/Phone instead). */
    private Long customerId;

    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;

    private Long assignedToUserId;

    public HelpDeskTicketType getType() { return type; }
    public void setType(HelpDeskTicketType type) { this.type = type; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public HelpDeskPriority getPriority() { return priority; }
    public void setPriority(HelpDeskPriority priority) { this.priority = priority; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getRequesterPhone() { return requesterPhone; }
    public void setRequesterPhone(String requesterPhone) { this.requesterPhone = requesterPhone; }
    public Long getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }
}
