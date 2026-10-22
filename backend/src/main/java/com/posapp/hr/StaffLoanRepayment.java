package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One reduction of a loan's balance. */
@Getter
@Setter
@Entity
public class StaffLoanRepayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StaffLoan loan;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate repaidOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepaymentSource source;

    private Long runId;
    private String note;
    private String createdByName;
}
