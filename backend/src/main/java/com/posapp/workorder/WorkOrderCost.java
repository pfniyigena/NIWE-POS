package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money spent outside the business on the job (a subcontractor, a bought-in part). Can be re-charged to the customer. */
@Getter
@Setter
@Entity
public class WorkOrderCost extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(nullable = false)
    private LocalDate costDate;

    @Column(nullable = false, length = 500)
    private String description;

    private String supplierName;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** What is added to the customer's invoice for it; 0 = absorbed. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal chargeAmount = BigDecimal.ZERO;

    /** The expense recorded in Expenses for it, when one was made. */
    private Long expenseId;
}
