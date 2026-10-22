package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A standard service: a checklist, usual parts and a fixed price that a new work order (or, later, a law matter) starts from. */
@Getter
@Setter
@Entity
public class ServicePackage extends BaseEntity {

    @Column(length = 40)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Price of the whole package; 0 = bill the hours and parts instead. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal fixedPrice = BigDecimal.ZERO;

    @Column(precision = 8, scale = 2)
    private BigDecimal estimatedHours;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;
}
