package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Money in or out on a guest's bill. Amount is always positive; the kind says which way it went. */
@Getter
@Setter
@Entity
public class FolioPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    private PaymentKind kind;

    /** Payment method code (same codes as the POS), snapshotted like Payment.method. */
    private String method;

    private BigDecimal amount = BigDecimal.ZERO;
    private String reference;
    private String note;
}
