package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** MF-5: rows of the Manufacturing reports. */
public final class ManufacturingRows {
    private ManufacturingRows() {}

    public record RunRow(LocalDate date, String orderNumber, String productName, BigDecimal quantityProduced, BigDecimal quantityScrapped, BigDecimal yieldPercent,
                         BigDecimal materialCost, BigDecimal labourCost, BigDecimal machineCost, BigDecimal overheadCost, BigDecimal byProductCredit,
                         BigDecimal totalCost, BigDecimal unitCost) {}

    public record ProductCostRow(String productName, int runs, BigDecimal quantityProduced, BigDecimal quantityScrapped, BigDecimal yieldPercent,
                                 BigDecimal materialCost, BigDecimal labourCost, BigDecimal machineCost, BigDecimal overheadCost, BigDecimal byProductCredit,
                                 BigDecimal totalCost, BigDecimal unitCost) {}

    public record ComponentUseRow(String componentName, int runs, BigDecimal plannedQuantity, BigDecimal usedQuantity, BigDecimal varianceQuantity, BigDecimal variancePercent,
                                  BigDecimal totalCost, BigDecimal varianceCost) {}

    public record WorkstationRow(String workstation, int runs, BigDecimal plannedMinutes, BigDecimal actualMinutes, BigDecimal varianceMinutes,
                                 BigDecimal machineCost, BigDecimal labourCost, BigDecimal totalCost) {}

    public record ScrapRow(String productName, String reason, int runs, BigDecimal scrappedUnits, BigDecimal scrapCost) {}

    public record OrderPlanRow(String orderNumber, String productName, String status, LocalDate scheduledDate, BigDecimal quantityPlanned, BigDecimal quantityProduced,
                               BigDecimal quantityRemaining, BigDecimal plannedCost, BigDecimal plannedCostOfUnitsMade, BigDecimal actualCost, BigDecimal costVariance) {}
}
