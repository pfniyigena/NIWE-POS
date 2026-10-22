package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment to the tax authority or social-security fund for the payroll of one month. */
@Getter
@Setter
@Entity
public class PayrollRemittance extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RemittanceKind kind;

    @Column(nullable = false)
    private int periodYear;

    @Column(nullable = false)
    private int periodMonth;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paidOn;

    private String reference;
    private String paidTo;
    private String note;

    /** ACTIVE, or VOIDED when it was entered by mistake (the books are reversed and it no longer counts as paid). */
    @Column(nullable = false)
    private String status = "ACTIVE";

    private String voidNote;
    private String voidedByName;
    private String createdByName;
}
