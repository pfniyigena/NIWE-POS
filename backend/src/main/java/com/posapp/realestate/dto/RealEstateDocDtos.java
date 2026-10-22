package com.posapp.realestate.dto;

import com.posapp.realestate.DocSubject;
import com.posapp.realestate.ReDocCategory;
import com.posapp.realestate.ValuationMethod;
import com.posapp.realestate.ValuationPurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What the document and valuation screens read. */
public final class RealEstateDocDtos {

    private RealEstateDocDtos() {
    }

    /** expiryState: NONE (no expiry date), OK, SOON (within 30 days) or EXPIRED. */
    public record DocumentDto(Long id, DocSubject subjectType, Long subjectId, String subjectLabel, String name, ReDocCategory category, String description, LocalDate expiryDate,
                              String expiryState, Long daysToExpiry, String fileName, String contentType, long sizeBytes, String uploadedByName, Instant createdAt) {
    }

    public record ValuationDto(Long id, Long propertyId, String propertyCode, String propertyTitle, LocalDate valuationDate, String valuerName, String valuerFirm,
                               ValuationPurpose purpose, ValuationMethod method, BigDecimal marketValue, BigDecimal forcedSaleValue, BigDecimal monthlyRent, LocalDate validUntil,
                               boolean expired, boolean current, BigDecimal askingPrice, BigDecimal vsAskingPercent, String notes, long documents, String createdByName,
                               Instant createdAt) {
    }
}
