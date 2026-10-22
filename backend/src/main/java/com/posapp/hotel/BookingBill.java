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

/** One numbered bill of a stay. Bill 1 exists implicitly; a row is stored once it has a label or a company. */
@Getter
@Setter
@Entity
@Table(name = "booking_bill")
public class BookingBill extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Column(name = "bill_no", nullable = false)
    private int billNo;

    private String label;

    /** When true, what is on this bill is owed by the stay's group (moved to the group account at check-out). */
    @Column(name = "to_group")
    private boolean toGroup = false;

    /** When set, what is on this bill is owed by the company instead of the guest (moved to the company ledger at check-out). */
    @ManyToOne(fetch = FetchType.EAGER)
    private Customer company;
}
