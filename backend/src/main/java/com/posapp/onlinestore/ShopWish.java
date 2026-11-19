package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A product a signed-in shop customer put on their wishlist (the heart). A customer holds a product once. */
@Getter
@Setter
@Entity
public class ShopWish extends BaseEntity {
    private Long shopCustomerId;
    private Long productId;
}
