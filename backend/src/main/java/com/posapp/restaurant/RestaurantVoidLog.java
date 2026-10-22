package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One thing that took money off a bill after the fact: a voided item, a comped item, a cancelled order or a receipt copy. */
@Getter
@Setter
@Entity
public class RestaurantVoidLog extends BaseEntity {

    /** VOID, COMP, CANCEL or REPRINT. */
    private String entryType;
    private Long orderId;
    private String orderLabel;
    private String description;
    private int quantity;
    private BigDecimal amount = BigDecimal.ZERO;
    private String reason;
    private String byUser;
    private String approvedBy;
}
