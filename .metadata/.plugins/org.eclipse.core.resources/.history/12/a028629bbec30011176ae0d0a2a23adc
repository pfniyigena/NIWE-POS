package com.posapp.returns;

import com.posapp.common.BaseEntity;
import com.posapp.order.SaleOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class SalesReturn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private SaleOrder order;

    /** A payment method code (see Payment.method's javadoc for why this is a plain string, not a FK). */
    private String refundMethod;

    private String reason;
    private Instant returnDate = Instant.now();
    private BigDecimal refundTotal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesReturnLine> lines = new ArrayList<>();

    /**
     * This return's own EFRIS {@code erp_code} — the return's counterpart to {@code
     * SaleOrder.invoiceNumber}, assigned the same way ({@code "POS-RET-" + currentTimeMillis()})
     * the instant a return is processed (see {@code ReturnsService.processReturn}; unlike an
     * order, a return has no draft/open state to assign this any later). {@code order.
     * invoiceNumber} becomes this submission's {@code erp_code_ref} instead — see {@code
     * ReturnsService.buildEfrisPayload}. Null only for a return processed before this feature
     * existed.
     */
    private String returnNumber;

    /**
     * EFRIS (Rwanda RRA e-invoicing) fiscal receipt data for *this return* — Fourier's own
     * follow-up: "The return will also integrate with EFRIS." Exactly the same eight nullable TEXT
     * fields as {@code SaleOrder} carries for a sale (see that class's own javadoc for the shared
     * reasoning), populated once a return has actually been submitted to EFRIS with {@code
     * transaction_type = "R"}.
     */
    @Column(name = "sdc_id", columnDefinition = "TEXT")
    private String sdcId;

    @Column(name = "sdc_date", columnDefinition = "TEXT")
    private String sdcDate;

    @Column(name = "sdc_receipt_number", columnDefinition = "TEXT")
    private String sdcReceiptNumber;

    @Column(name = "internal_data", columnDefinition = "TEXT")
    private String internalData;

    @Column(name = "signature_data", columnDefinition = "TEXT")
    private String signatureData;

    @Column(name = "cis_number", columnDefinition = "TEXT")
    private String cisNumber;

    @Column(name = "cis_date", columnDefinition = "TEXT")
    private String cisDate;

    @Column(name = "mrc_data", columnDefinition = "TEXT")
    private String mrcData;

    /** When an EFRIS submission was last attempted for this return (success or failure) — null means never attempted. See {@link #efrisError}. */
    private Instant efrisSubmittedAt;

    /** Set only when an EFRIS submission was attempted and failed — same "return still completes, flagged for review" policy as SaleOrder.efrisError. Null on success or if never attempted. */
    @Column(columnDefinition = "TEXT")
    private String efrisError;

    /** The raw EFRIS response body from the most recent submission attempt — same convention as SaleOrder.efrisResponse. */
    @Column(columnDefinition = "TEXT")
    private String efrisResponse;

    /**
     * Fourier: warehouse filter on Stock Movement History — which warehouse this return restocked into took stock
     * from, snapshotted when it happened (the Main warehouse is stored as null, which is also what
     * every row created before this column existed reads as). A snapshot rather than derived from
     * the register/setting later, because registers get reassigned and settings change.
     */
    @jakarta.persistence.Column(name = "stock_warehouse_id")
    private Long stockWarehouseId;

    /**
     * Fourier: "How business branches can be introduced?" — the branch of the original sale this return reverses.
     * Snapshot id (not a live reference); null reads as head office, which is also what every row
     * created before branches existed holds.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;
}
