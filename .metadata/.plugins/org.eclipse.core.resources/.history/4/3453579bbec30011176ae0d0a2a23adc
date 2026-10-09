package com.posapp.inventory;

import com.posapp.catalog.Category;
import com.posapp.common.BaseEntity;
import com.posapp.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical stock count of one warehouse (every active location in it), optionally limited to one
 * category. The system quantities are frozen onto the sheet when it is started (see {@link
 * StockCountLine#getSystemQuantity()}); sales may carry on meanwhile. When the count is approved, each
 * counted line's difference (counted minus the quantity recorded at the start) is posted as a stock
 * adjustment with reason RECOUNT — a relative change, so anything sold or received after the sheet was
 * started is preserved.
 */
@Getter
@Setter
@Entity
public class StockCount extends BaseEntity {

    @Column(nullable = false)
    private String countNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse warehouse;

    /** Snapshot, so the sheet still reads correctly if the warehouse is renamed. */
    private String warehouseName;

    /** Optional — null counts every category. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    private StockCountStatus status = StockCountStatus.IN_PROGRESS;

    private Instant submittedAt;

    private Instant approvedAt;

    private String approvedBy;

    /** Branch of the counted warehouse (null = head office). */
    @Column(name = "branch_id")
    private Long branchId;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockCountLine> lines = new ArrayList<>();
}
