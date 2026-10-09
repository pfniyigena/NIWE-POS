package com.posapp.tracking;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import com.posapp.warehouse.WarehouseLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One physical unit of a SERIAL-tracked product. A serial number is unique per product. */
@Getter
@Setter
@Entity
@Table(name = "stock_serial",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_serial_product_number", columnNames = {"product_id", "serial_number"}),
        indexes = {
                @Index(name = "idx_stock_serial_product_location", columnList = "product_id,location_id,serial_status"),
                @Index(name = "idx_stock_serial_transfer", columnList = "transfer_id")
        })
public class StockSerial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation location;

    @Column(name = "transfer_id")
    private Long transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "serial_status", length = 20)
    private SerialStatus status = SerialStatus.IN_STOCK;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "received_at")
    private Instant receivedAt = Instant.now();

    private String source;

    @Column(name = "sold_at")
    private Instant soldAt;

    /** Receipt/invoice number the unit left stock on, when known. */
    @Column(name = "sold_ref")
    private String soldRef;
}
