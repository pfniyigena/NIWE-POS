package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money received from a company against a {@link CompanyBill}. */
@Getter
@Setter
@Entity
@Table(name = "company_receipt")
public class CompanyReceipt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CompanyBill companyBill;

    private String method;
    private BigDecimal amount = BigDecimal.ZERO;
    private String reference;
    private LocalDate receivedOn;
}
