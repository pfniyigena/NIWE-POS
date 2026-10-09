package com.posapp.helpdesk.dto;

/** Body for POST /api/helpdesk/tickets/{id}/assign — null assignedToUserId unassigns the ticket. */
public class AssignTicketRequest {
    private Long assignedToUserId;

    public Long getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Long assignedToUserId) { this.assignedToUserId = assignedToUserId; }
}
