package com.posapp.tracking;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import com.posapp.warehouse.WarehouseLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Units of a BATCH-tracked product held at one location, all sharing a batch number and expiry date.
 * {@code quantity} is what is left. While the goods are on an inter-branch transfer the location is null
 * and {@code transferId} says which transfer carries them.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_batch", indexes = {
        @Index(name = "idx_stock_batch_product_location", columnList = "product_id,location_id"),
        @Index(name = "idx_stock_batch_transfer", columnList = "transfer_id")
})
public class StockBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation location;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    private int quantity;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "received_at")
    private Instant receivedAt = Instant.now();

    /** Where it came from, e.g. "PO-123" or "Stock adjustment". */
    private String source;
}
