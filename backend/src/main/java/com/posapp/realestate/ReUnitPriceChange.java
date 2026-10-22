package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Every change of a unit's list price, so the price list has a history. */
@Getter
@Setter
@Entity
@Table(name = "re_unit_price_change", indexes = {@Index(columnList = "unit_id")})
public class ReUnitPriceChange extends BaseEntity {

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(precision = 18, scale = 2)
    private BigDecimal oldPrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal newPrice;

    @Column(length = 200)
    private String reason;

    private String changedByName;
}
