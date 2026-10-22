package com.posapp.realestate.dto;

import com.posapp.realestate.LeadInterest;
import com.posapp.realestate.LeadSource;
import com.posapp.realestate.LeadStatus;
import com.posapp.realestate.ListingPurpose;
import com.posapp.realestate.NoteKind;
import com.posapp.realestate.PropertyStatus;
import com.posapp.realestate.PropertyType;
import com.posapp.realestate.ViewingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class RealEstateRequests {

    private RealEstateRequests() {
    }

    public record SettingsRequest(String areas, Integer viewingMinutes, Integer staleLeadDays, Boolean websiteEnabled, String websiteTitle, String websiteIntro, String websitePhone,
                                  String websiteEmail, Integer enquiriesPerPhonePerDay, BigDecimal defaultCommissionPercent, Long commissionAccountId, Boolean followUpEmailEnabled, Boolean viewingReminderEnabled, Integer viewingReminderHours,
                                  String offerTerms, String reservationTerms, String agreementTerms) {
    }

    public record PropertyRequest(String title, PropertyType type, ListingPurpose purpose, String description, String area, String address, String titleReference,
                                  Double latitude, Double longitude, Integer bedrooms, Integer bathrooms, Integer parkingSpaces, BigDecimal sizeSqm, BigDecimal plotSqm,
                                  Integer yearBuilt, Boolean furnished, String features, BigDecimal salePrice, BigDecimal rentPrice, Boolean negotiable, Boolean featured, Boolean publicListing,
                                  String ownerName, String ownerPhone, String ownerEmail, Long agentUserId) {
    }

    public record PropertyStatusRequest(PropertyStatus status) {
    }

    public record LeadRequest(String name, String phone, String email, LeadInterest interest, LeadSource source, BigDecimal budgetMin, BigDecimal budgetMax,
                              String preferredArea, PropertyType preferredType, Integer minBedrooms, String requirements, Long propertyId, Long assignedUserId,
                              LocalDate nextFollowUp, Boolean force) {
    }

    public record LeadStatusRequest(LeadStatus status, String lostReason) {
    }

    public record NoteRequest(NoteKind kind, String text, LocalDate nextFollowUp) {
    }

    public record ViewingRequest(Long propertyId, Long leadId, Long agentUserId, Instant startAt, Instant endAt, Boolean force) {
    }

    public record ViewingStatusRequest(ViewingStatus status, String feedback) {
    }
}
