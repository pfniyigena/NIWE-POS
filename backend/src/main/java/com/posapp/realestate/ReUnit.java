package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One plot or unit of a project, with its list price. */
@Getter
@Setter
@Entity
@Table(name = "re_unit", indexes = {@Index(columnList = "project_id"), @Index(columnList = "status")}, uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "unit_no"}))
public class ReUnit extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "unit_no", nullable = false, length = 40)
    private String unitNo;

    /** Phase, block, building or street: how the project groups its units. */
    @Column(length = 80)
    private String block;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitKind kind = UnitKind.PLOT;

    private Integer floor;
    private Integer bedrooms;
    private Integer bathrooms;

    @Column(precision = 12, scale = 2)
    private BigDecimal sizeSqm;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal listPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitStatus status = UnitStatus.AVAILABLE;

    @Column(length = 1000)
    private String notes;
}
