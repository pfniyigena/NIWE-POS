package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One line of an inspection: an item in a room, its condition, and (at move-out) what putting it right is expected to cost. */
@Getter
@Setter
@Entity
@Table(name = "pm_inspection_item", indexes = {@Index(columnList = "inspection_id")})
public class PmInspectionItem extends BaseEntity {

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 80)
    private String room;

    @Column(nullable = false, length = 120)
    private String item;

    /** Null until the item has been looked at. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PmItemCondition itemCondition;

    /** At move-out: what the same item was at move-in. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PmItemCondition baselineCondition;

    @Column(length = 500)
    private String notes;

    @Column(precision = 18, scale = 2)
    private BigDecimal repairCost;
}
