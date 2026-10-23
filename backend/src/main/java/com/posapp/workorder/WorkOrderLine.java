package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A part or material used on the job. Stock moves and the cost is posted when the job is completed. */
@Getter
@Setter
@Entity
public class WorkOrderLine extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    /** Price charged to the customer per unit. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Cost per unit taken from the product when the stock moved. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** Covered by the job's fixed price (not invoiced separately). */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean included = false;
}
