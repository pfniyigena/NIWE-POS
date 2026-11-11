package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** One line of an order's history: a status change, who made it and an optional note. */
@Getter
@Setter
@Entity
public class OnlineOrderEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "online_order_id", nullable = false)
    private OnlineOrder onlineOrder;
    private String status;
    private String note;
    private String byUser;
}
