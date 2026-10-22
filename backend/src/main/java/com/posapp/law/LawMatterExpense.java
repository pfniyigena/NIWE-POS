package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money the firm spent for a matter (court fee, filing fee, travel, courier). Can be charged on to the client. */
@Getter
@Setter
@Entity
public class LawMatterExpense extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, length = 500)
    private String description;

    private String supplierName;

    /** What it cost the firm. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** What is put on the client's invoice; 0 = absorbed by the firm. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal chargeAmount = BigDecimal.ZERO;

    /** The expense recorded in Expenses for it, when one was made. */
    private Long expenseId;

    /** The bill it is on; empty = not billed. */
    private Long billId;

    private Long createdByUserId;
    private String createdByName;
}
