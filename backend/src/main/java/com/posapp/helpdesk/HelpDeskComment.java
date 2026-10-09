package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * One update/note on a {@link HelpDeskTicket}'s timeline — always staff-authored (this app has no
 * customer-facing login for a requester to reply through; see {@code HelpDeskTicket}'s own
 * javadoc), but {@link #internal} still distinguishes a private note from an update meant to be
 * read by the requester: a {@link HelpDeskTicketType#CUSTOMER} ticket's non-internal comments are
 * exactly what the public "check my ticket" lookup page shows (see {@code
 * PublicHelpDeskController#lookup}), so a comment with sensitive internal discussion stays
 * {@code internal = true} and never reaches that page.
 */
@Getter
@Setter
@Entity
public class HelpDeskComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private HelpDeskTicket ticket;

    @Column(nullable = false)
    private Long authorUserId;

    @Column(nullable = false)
    private String authorUserFullName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Defaults to true (private note) — a comment is only ever shown to the requester when a staff member deliberately unchecks this. */
    @Column(nullable = false)
    private boolean internal = true;
}
