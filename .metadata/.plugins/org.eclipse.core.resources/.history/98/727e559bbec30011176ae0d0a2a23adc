package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Fourier: "Add Help Desk module (with its menu) should manage customer and internal support
 * requests from creation through resolution", then (via AskUserQuestion): one unified ticket
 * model for both, distinguished by {@link #type}; staff-created (ADMIN, from this app's own admin
 * screen) or guest-created (no login, the public Help Desk page — Fourier: "Customer login is not
 * necessary to create a Help Desk ticket ... supporting both authenticated and guest ticket
 * creation", read as "staff-logged-in vs. public form" since this app has no customer-facing
 * login system at all — see {@code PublicHelpDeskController}'s own javadoc, which mirrors the
 * existing public-reservation feature exactly).
 *
 * <p>{@link #customerId}/{@link #customerName} are plain denormalized fields, not a live {@code
 * @ManyToOne Customer} — same convention {@code com.posapp.subscription.Subscription} already
 * uses for its own customer link, kept decoupled since a ticket may well have no matching
 * {@code Customer} record at all (a guest who's never bought anything, or a purely {@link
 * HelpDeskTicketType#INTERNAL} request). Similarly {@link #createdByUserId}/{@link
 * #createdByUserFullName} and {@link #assignedToUserId}/{@link #assignedToUserFullName} are
 * snapshotted id+name pairs rather than {@code @ManyToOne AppUser} relations, the same "stamp the
 * name so history reads correctly even if the user account changes/deactivates later" convention
 * {@code PurchaseRequisition.approvedByFullName} already uses.
 *
 * <p>{@link #category} stays a real {@code @ManyToOne} (unlike the links above) since it's
 * entirely within this same module — same treatment {@code Product.category} gives {@code
 * Category}.
 */
@Getter
@Setter
@Entity
public class HelpDeskTicket extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpDeskTicketType type;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private HelpDeskCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpDeskPriority priority = HelpDeskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpDeskStatus status = HelpDeskStatus.OPEN;

    /** Who's asking — required for a guest-created ticket; for a staff-created one these default from {@link #customerId}'s own Customer record when one is picked. */
    private String requesterName;
    private String requesterEmail;
    private String requesterPhone;

    /**
     * Set when the requester matches (or was picked as) an existing {@code Customer} — for a
     * guest-created ticket, {@code HelpDeskTicketService} tries to match one by email so a
     * returning customer's tickets line up with their own record; for a staff-created one the
     * admin picks a customer explicitly. Null for an {@link HelpDeskTicketType#INTERNAL} request,
     * or a {@link HelpDeskTicketType#CUSTOMER} one from someone with no matching record at all.
     */
    private Long customerId;
    private String customerName;

    /** Null for a guest-created ticket — see this class's own javadoc. */
    private Long createdByUserId;
    private String createdByUserFullName;

    private Long assignedToUserId;
    private String assignedToUserFullName;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    private Instant resolvedAt;
    private Instant closedAt;
}
