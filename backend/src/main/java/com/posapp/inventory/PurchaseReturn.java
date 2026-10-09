package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
 * Module." The purchasing-side mirror of {@code com.posapp.salesorder.SalesOrderReturn} — a
 * return processed against a SENT/PARTIALLY_RECEIVED/RECEIVED {@link PurchaseOrder} once some of
 * it has actually arrived. Same money-handling choice Fourier made on the sales side, applied
 * here too: {@code refundTotal} is a record of what the *supplier* now owes the store back, not
 * an instruction for the system to actually collect anything — no payout/refund-received field
 * exists, the same reasoning {@code SalesOrderReturn}'s own class javadoc gives. See {@code
 * SupplierPaymentService#refundDueFromSupplierForOrder} for where this figure surfaces.
 */
@Getter
@Setter
@Entity
public class PurchaseReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrder purchaseOrder;

    /** Own "PO-RET-" prefix — distinct from every other return-number/order-number scheme in the app. */
    private String returnNumber;

    private Instant returnDate = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** Sum of every line's {@code refundAmount} — informational, see class javadoc. */
    private BigDecimal refundTotal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnLine> lines = new ArrayList<>();
}
