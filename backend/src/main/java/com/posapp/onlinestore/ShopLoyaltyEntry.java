package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * One change of a customer's points. kind: EARN (order completed), SPEND (used at checkout), SPEND_BACK (order cancelled or rejected: the points return),
 * RET_BACK (goods returned: part of the spent points return), RET_TAKE (goods returned: part of the earned points is taken back), ADJUST (staff).
 * points is signed; balanceAfter is the balance once this entry was applied.
 */
@Getter
@Setter
@Entity
public class ShopLoyaltyEntry extends BaseEntity {
    private Long shopCustomerId;
    private String kind;
    private int points;
    private int balanceAfter;
    private Long onlineOrderId;
    private Long onlineReturnId;
    private String orderCode;
    private String note;
    private String createdBy;
}
