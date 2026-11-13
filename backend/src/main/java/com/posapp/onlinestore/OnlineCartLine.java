package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class OnlineCartLine extends BaseEntity {
    private Long cartId;
    private Long productId;
    private int quantity = 1;
}
