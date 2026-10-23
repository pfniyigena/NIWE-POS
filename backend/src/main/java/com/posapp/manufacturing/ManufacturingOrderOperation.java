package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * MF-2: one step of the routing, copied onto the order when it is confirmed (name, workstation,
 * minutes per unit and the two hourly rates), so a later change to the routing or to a workstation's
 * rate never changes what an order in progress is charged.
 */
@Getter
@Setter
@Entity
public class ManufacturingOrderOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrder manufacturingOrder;

    private int sequence;

    private String name;

    private Long workstationId;

    private String workstationName;

    private BigDecimal minutesPerUnit = BigDecimal.ZERO;

    private BigDecimal machineRate = BigDecimal.ZERO;

    private BigDecimal labourRate = BigDecimal.ZERO;
}
