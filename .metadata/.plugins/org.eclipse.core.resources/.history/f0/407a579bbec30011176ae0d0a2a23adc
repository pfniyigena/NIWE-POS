package com.posapp.inventory;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import com.posapp.warehouse.WarehouseLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One product at one location on a {@link StockCount} sheet. */
@Getter
@Setter
@Entity
public class StockCountLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private String sku;

    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseLocation location;

    private String locationName;

    /** What the system held at this location when the sheet was started. */
    @Column(nullable = false)
    private int systemQuantity;

    /** Null until someone has counted it; an uncounted line is ignored on approval (it is not treated as zero). */
    private Integer countedQuantity;

    /** The product's cost when the sheet was started — values the difference. */
    private BigDecimal unitCost;

    /** The stock adjustment this line produced on approval; null when it had no difference. */
    private Long adjustmentId;
}
