package com.posapp.audit;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Task: "Implement an audit log of who did what and when." One row per meaningful business
 * action (settings changes, user management, product/price edits, app install/enable, register
 * changes, sale voids, return processing — see {@link AuditLogService}'s javadoc for the full
 * list and why this is a curated set rather than every mutating request). Deliberately its own
 * top-level package, not nested under any single feature: an audit log is cross-cutting by
 * nature, referenced from a dozen otherwise-unrelated services.
 * <p>
 * Extends {@link BaseEntity} purely for its {@code id}/{@code createdAt} — {@code createdAt} is
 * this entity's own "when": a row is written once, at the moment the action happened, and never
 * updated again, so {@code updatedAt} is simply always equal to {@code createdAt} and isn't shown
 * anywhere. {@code actorUsername}/{@code actorFullName} are denormalized (copied at write time)
 * rather than a live {@code @ManyToOne} to {@link com.posapp.user.AppUser}: a log entry has to
 * keep reading correctly even if that user account is later renamed or deactivated — "who did
 * this" should never silently change after the fact. {@code actorId} is kept alongside anyway,
 * nullable, purely so an admin viewing the log could one day jump to that user's own profile.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    /** Nullable: kept for a possible future "view this user" link, never relied on for display — see class javadoc. */
    @Column(name = "actor_id")
    private Long actorId;

    /** Denormalized at write time — see class javadoc for why this never changes even if the account is later renamed. "system" for an action with no authenticated caller (shouldn't normally happen for anything audited, but never left null). */
    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    /** Denormalized at write time, same reasoning as {@link #actorUsername}. Nullable only because a "system" actor has none. */
    @Column(name = "actor_full_name")
    private String actorFullName;

    /**
     * A short, stable machine code identifying the kind of action (e.g. {@code "PRODUCT_UPDATED"},
     * {@code "TAX_RATE_CREATED"}) — see {@link AuditLogService} for the fixed set this app writes.
     * Lets the admin viewer filter by action type without parsing {@link #description} text.
     */
    @Column(nullable = false)
    private String action;

    /**
     * The human-readable sentence describing exactly what happened (e.g. "Updated tax rate VAT:
     * 18% -&gt; 20%") — composed once, at write time, by whichever service recorded the entry.
     * This is what actually renders in the audit log viewer; {@link #action}/{@link #entityType}/
     * {@link #entityId} exist for filtering, not display.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** e.g. "Product", "TaxRate", "AppUser" — optional, for filtering by what kind of thing was affected. Null for an action with no single affected entity (e.g. app enable/disable). */
    @Column(name = "entity_type")
    private String entityType;

    /** The affected entity's own id, paired with {@link #entityType}. Null when there isn't a single one (or {@link #entityType} itself is null). */
    @Column(name = "entity_id")
    private Long entityId;
}
