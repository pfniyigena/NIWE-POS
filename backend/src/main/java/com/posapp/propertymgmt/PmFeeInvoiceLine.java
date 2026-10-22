package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One line of a fee invoice: the management fee on one property's rent, and its VAT. */
@Getter
@Setter
@Entity
@Table(name = "pm_fee_invoice_line", indexes = {@Index(columnList = "invoice_id")})
public class PmFeeInvoiceLine extends BaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private int sortOrder;

    private Long propertyId;

    @Column(nullable = false, length = 300)
    private String description;

    /** The rent the fee was worked out on. */
    @Column(precision = 18, scale = 2)
    private BigDecimal baseAmount;

    @Column(precision = 7, scale = 2)
    private BigDecimal feePercent;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal vatAmount;
}
