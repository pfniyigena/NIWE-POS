package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskStatus;
import com.posapp.helpdesk.HelpDeskTicket;

import java.time.Instant;

/** The guest's own confirmation after submitting a ticket — deliberately slim, same "just enough to prove it worked and let them look it up later" shape as PublicReservationDto. */
public record PublicHelpDeskTicketDto(String ticketNumber, String subject, HelpDeskStatus status, Instant createdAt) {
    public static PublicHelpDeskTicketDto from(HelpDeskTicket t) {
        return new PublicHelpDeskTicketDto(t.getTicketNumber(), t.getSubject(), t.getStatus(), t.getCreatedAt());
    }
}
