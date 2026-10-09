package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The internal "can we buy this" approval gate placed in front of {@link PurchaseOrder} (Fourier:
 * "Build Quotation, Invoice, and the purchase-requisition approval gate ... without touching
 * warehousing" — see the earlier conversation on why this is the purchase-requisition flavor, not
 * a stock/material-transfer one: there's only one stock pool today, so "move stock between
 * locations" has nothing to move between). Lives in the same package as {@code PurchaseOrder}
 * rather than its own module — this is inventory-domain business through and through, and keeping
 * it here means {@link PurchaseRequisitionService#convertToPurchaseOrder} can go through {@code
 * PurchaseOrderService}'s own public {@code createDraft} without any cross-module boundary at all.
 * <p>
 * Deliberately commits to nothing itself: no supplier, no pricing — those only get supplied once
 * an ADMIN approves this and converts it (see {@link PurchaseRequisitionStatus}'s javadoc for the
 * full lifecycle). {@code requestedByFullName}/{@code approvedByFullName} are stamped from
 * whoever's authenticated at the time (same {@code SecurityContextHolder} pattern {@code
 * AuditLogService.currentActor} already uses), denormalized rather than a live {@code AppUser}
 * relation so this still reads sensibly even if that user account is later removed.
 */
@Getter
@Setter
@Entity
public class PurchaseRequisition extends BaseEntity {

    private String requisitionNumber;

    @Enumerated(EnumType.STRING)
    private PurchaseRequisitionStatus status = PurchaseRequisitionStatus.DRAFT;

    private Instant requestDate = Instant.now();

    private String requestedByFullName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Instant approvedAt;

    private String approvedByFullName;

    private Instant rejectedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    /** Set once this requisition is CONVERTED — kept as a plain id/number pair rather than a JPA relation, same "traceability without a hard schema link" convention as Quotation.convertedSalesOrderId. */
    private Long convertedPurchaseOrderId;

    private String convertedPurchaseOrderNumber;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseRequisitionLine> lines = new ArrayList<>();
}
