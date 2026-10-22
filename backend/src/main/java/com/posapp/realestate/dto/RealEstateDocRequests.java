package com.posapp.realestate.dto;

import com.posapp.realestate.ReDocCategory;
import com.posapp.realestate.ValuationMethod;
import com.posapp.realestate.ValuationPurpose;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RealEstateDocRequests {

    private RealEstateDocRequests() {
    }

    /** Edits a document's details (not the file): anything left out stays as it was; a blank text clears it. clearExpiry removes the expiry date. */
    public record DocumentRequest(String name, ReDocCategory category, String description, LocalDate expiryDate, Boolean clearExpiry) {
    }

    /** New valuation (propertyId, valuationDate, valuerName and marketValue needed) or edit: anything left out stays as it was; clearValidUntil removes the end date. */
    public record ValuationRequest(Long propertyId, LocalDate valuationDate, String valuerName, String valuerFirm, ValuationPurpose purpose, ValuationMethod method,
                                   BigDecimal marketValue, BigDecimal forcedSaleValue, BigDecimal monthlyRent, LocalDate validUntil, Boolean clearValidUntil, String notes) {
    }
}
