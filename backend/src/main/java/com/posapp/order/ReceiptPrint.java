package com.posapp.order;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One printing of a sale's receipt: copy 0 is the original, 1, 2... are reprints. */
@Getter
@Setter
@Entity
public class ReceiptPrint extends BaseEntity {
    private Long orderId;
    private int copyNumber;
    private String printedBy;
}
