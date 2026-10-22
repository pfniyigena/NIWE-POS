package com.posapp.realestate.dto;

import com.posapp.installment.InstallmentFrequency;
import com.posapp.installment.InterestMethod;
import com.posapp.realestate.ProjectStatus;
import com.posapp.realestate.UnitKind;
import com.posapp.realestate.UnitStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RealEstateProjectRequests {

    private RealEstateProjectRequests() {
    }

    /** New project (name needed) or edit: anything left out stays as it was; a blank text clears it. */
    public record ProjectRequest(String name, String area, String address, String developerName, ProjectStatus status, LocalDate launchDate, LocalDate handoverDate,
                                 String description) {
    }

    /** New unit (unitNo and listPrice needed) or edit: anything left out stays as it was. status may only be AVAILABLE or WITHHELD. */
    public record UnitRequest(String unitNo, String block, UnitKind kind, Integer floor, Integer bedrooms, Integer bathrooms, BigDecimal sizeSqm, BigDecimal listPrice,
                              UnitStatus status, String notes, String reason) {
    }

    /** Creates unit numbers prefix+from ... prefix+to (zero-padded to {@code padding} digits), all alike. */
    public record BulkUnitsRequest(String prefix, Integer from, Integer to, Integer padding, String block, UnitKind kind, Integer floor, Integer bedrooms, Integer bathrooms,
                                   BigDecimal sizeSqm, BigDecimal listPrice) {
    }

    /** Moves the list price of units that are still on sale: by a percentage (negative lowers it), rounded to roundTo. preview = true only shows the result. */
    public record PriceAdjustRequest(BigDecimal percent, Integer roundTo, List<Long> unitIds, String block, UnitKind kind, String reason, Boolean preview) {
    }

    /** New reservation (unitId and a buyer name or a lead needed) or edit while it is still reserved. agreedPrice defaults to the unit's list price. */
    public record UnitSaleRequest(Long unitId, Long leadId, String buyerName, String buyerPhone, String buyerEmail, Long buyerCustomerId, Long agentUserId, BigDecimal agreedPrice,
                                  LocalDate reservationExpiry, String notes) {
    }

    public record SaleAgreementRequest(LocalDate agreementDate) {
    }

    /** The instalment terms; the invoice is the sale's own. See the Installments module. */
    public record UnitPlanRequest(BigDecimal deposit, String depositMethod, Integer numberOfInstallments, InstallmentFrequency frequency, LocalDate firstDueDate,
                                  InterestMethod interestMethod, BigDecimal interestRate, Integer graceDays, BigDecimal lateFeeFixed, BigDecimal lateFeePercent,
                                  Boolean remindersEnabled, Boolean wholeUnits, String notes) {
    }

    public record SaleCompleteRequest(LocalDate completedDate, Boolean force) {
    }

    public record SaleCancelRequest(String reason) {
    }
}
