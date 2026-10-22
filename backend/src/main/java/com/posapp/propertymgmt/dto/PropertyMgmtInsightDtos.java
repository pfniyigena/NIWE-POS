package com.posapp.propertymgmt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the Property Management reports that look across leases, months and contractors. */
public final class PropertyMgmtInsightDtos {

    private PropertyMgmtInsightDtos() {
    }

    /** A lease in force with a yearly increase, and what its rent becomes when it is renewed. */
    public record RentIncreaseRow(Long leaseId, String code, String propertyName, String unitNo, String ownerName, String tenantName, LocalDate endDate, Long daysToEnd, BigDecimal rent,
                                  BigDecimal escalationPercent, BigDecimal newRent, BigDecimal increase, String frequency, String state, String renewalCode) {
    }

    /** One month: how many units were let at its end (or today, for the current month) and how the rent that fell due in it was collected. */
    public record TrendRow(String month, long units, long occupied, BigDecimal occupancyPercent, long charges, BigDecimal invoiced, BigDecimal collected, BigDecimal outstanding,
                           BigDecimal collectedPercent) {
    }

    /** The repair jobs given to one contractor. */
    public record ContractorRow(String contractorName, long jobs, long completed, long open, long late, BigDecimal totalCost, BigDecimal averageCost, BigDecimal averageDays) {
    }
}
