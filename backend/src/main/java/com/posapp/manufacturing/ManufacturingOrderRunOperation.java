package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** MF-2: what one routing step cost in one production run: the minutes it took (planned and actual) and the two charges. */
@Getter
@Setter
@Entity
public class ManufacturingOrderRunOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private ManufacturingOrderRun run;

    private int sequence;

    private String name;

    private Long workstationId;

    private String workstationName;

    private BigDecimal plannedMinutes = BigDecimal.ZERO;

    private BigDecimal actualMinutes = BigDecimal.ZERO;

    private BigDecimal machineCost = BigDecimal.ZERO;

    private BigDecimal labourCost = BigDecimal.ZERO;
}
