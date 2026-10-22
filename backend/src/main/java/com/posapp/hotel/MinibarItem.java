package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** An item in the room minibar and its price (before tax). */
@Getter
@Setter
@Entity
public class MinibarItem extends BaseEntity {
    private String name;
    private BigDecimal price = BigDecimal.ZERO;
    private boolean taxable = true;
    private boolean active = true;
}
