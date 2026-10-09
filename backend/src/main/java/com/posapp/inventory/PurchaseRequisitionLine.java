package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * One line of a {@link PurchaseRequisition} — a product, how many are being requested, and an
 * optional per-line reason. Deliberately no cost/price field at all — see {@code
 * PurchaseRequisition}'s class javadoc for why pricing only enters the picture at conversion time,
 * once a supplier has actually been picked.
 */
@Getter
@Setter
@Entity
public class PurchaseRequisitionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseRequisition requisition;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Snapshot of the product's name at the time this line was added, same "survives a later rename" reasoning as every other line's productName field. */
    private String productName;

    private int quantityRequested;

    /** Optional — e.g. "running low", "for the new display" — purely informational context for whoever approves this. */
    private String note;
}
