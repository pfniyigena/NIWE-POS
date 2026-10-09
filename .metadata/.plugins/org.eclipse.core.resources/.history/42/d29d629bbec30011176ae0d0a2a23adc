package com.posapp.salesorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module." Mirrors
 * {@code com.posapp.returns.SalesReturn} (the POS return) but scoped to a confirmed/partially-
 * fulfilled/fulfilled {@link SalesOrder} instead of a completed POS {@code SaleOrder} — and, per
 * Fourier's own choice between the two return-money-handling options offered, this is
 * deliberately informational only: {@code refundTotal} records how much the customer is now owed
 * back, not an instruction for the system to actually pay anything out. There is no {@code
 * refundMethod} field (unlike the POS return) because nothing is disbursed here — the money is
 * simply left as a credit that the customer's outstanding balance already reflects (see {@code
 * CustomerCreditService#refundDueForSalesOrder}); any *actual* payout happens outside this system,
 * the same way it would for a bank transfer or cash handed back at the counter. No EFRIS fields
 * either — a back-office Sales Order was never fiscalized as a POS sale in the first place (see
 * {@code SalesOrder}'s own class javadoc), so there is nothing here for EFRIS to reverse.
 */
@Getter
@Setter
@Entity
public class SalesOrderReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SalesOrder salesOrder;

    /** Own "SO-RET-" prefix — distinct from the POS return's "POS-RET-" and every other order-number scheme in the app. */
    private String returnNumber;

    private Instant returnDate = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** Sum of every line's {@code refundAmount} — informational, see class javadoc. */
    private BigDecimal refundTotal = BigDecimal.ZERO;

    /**
     * Fourier: multi-warehouse phase 3 — the location this return moved stock at. Nullable: null
     * means the legacy behaviour (the product's plain primary-warehouse stock figure), which is
     * what every row created before this column existed did. {@code locationName}/{@code
     * warehouseName} are snapshots so renaming either later doesn't rewrite the trail.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private com.posapp.warehouse.WarehouseLocation location;

    private String locationName;

    private String warehouseName;

    @OneToMany(mappedBy = "salesOrderReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderReturnLine> lines = new ArrayList<>();
}
