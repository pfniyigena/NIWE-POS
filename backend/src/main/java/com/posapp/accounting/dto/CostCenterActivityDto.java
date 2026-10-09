package com.posapp.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/** One cost center's own totals for the "Cost Centers" journals report — see AccountingReportService.costCenterActivity. */
public record CostCenterActivityDto(
        Long costCenterId,
        String costCenterCode,
        String costCenterName,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        /** totalDebit - totalCredit — positive typically means net expense activity, negative net revenue, for whichever lines were tagged with this cost center. */
        BigDecimal net,
        /** Every journal-entry line tagged with this cost center in range (Fourier: "Add all details") — see ReportDetailLineDto's own javadoc. */
        List<ReportDetailLineDto> details
) {
}
