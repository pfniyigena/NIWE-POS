package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One {@link OverheadCost} pool selected onto a {@link ManufacturingOrder} — {@code
 * nameSnapshot}/{@code methodSnapshot}/{@code rateSnapshot} are copied from the {@link
 * OverheadCost} at the moment it's added (confirm time), the same snapshot convention every cost
 * figure in this app follows, so a later edit to that overhead pool's own rate never rewrites an
 * order already costed against it.
 */
@Getter
@Setter
@Entity
public class ManufacturingOrderOverhead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrder manufacturingOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    private OverheadCost overheadCost;

    private String nameSnapshot;

    @Enumerated(EnumType.STRING)
    private OverheadAllocationMethod methodSnapshot;

    private BigDecimal rateSnapshot;
}
