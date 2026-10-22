package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import com.posapp.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An amount a company owes the hotel for a guest's bill (the "city ledger"). Created at check-out; paid down by {@link CompanyReceipt}s. */
@Getter
@Setter
@Entity
@Table(name = "company_bill")
public class CompanyBill extends BaseEntity {

    @Column(unique = true)
    private String billNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Customer company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Booking booking;

    @Column(name = "bill_no")
    private int billNo = 1;

    private String description;

    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal paid = BigDecimal.ZERO;

    /** OPEN or PAID. */
    private String status = "OPEN";

    private LocalDate issuedOn;
    private LocalDate dueDate;

    public BigDecimal outstanding() {
        return amount.subtract(paid);
    }
}
