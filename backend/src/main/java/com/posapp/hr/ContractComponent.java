package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A monthly allowance (housing, transport...) or fixed deduction (loan repayment...) on a contract. */
@Getter
@Setter
@Entity
public class ContractComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Contract contract;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentKind kind = ComponentKind.EARNING;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Earnings only: counts as taxable income. */
    private boolean taxable = true;
}
