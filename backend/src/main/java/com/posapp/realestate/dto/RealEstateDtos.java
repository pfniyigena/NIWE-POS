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
import java.util.List;

public final class RealEstateDtos {

    private RealEstateDtos() {
    }

    public record Option(Long id, String name) {
    }

    public record SettingsDto(List<String> areas, int viewingMinutes, int staleLeadDays, boolean websiteEnabled, String websiteTitle, String websiteIntro, String websitePhone,
                              String websiteEmail, int enquiriesPerPhonePerDay, BigDecimal defaultCommissionPercent, Long commissionAccountId, boolean followUpEmailEnabled,
                              LocalDate followUpLastRun, String followUpLastSummary, boolean mailConfigured, boolean viewingReminderEnabled, int viewingReminderHours,
                              String offerTerms, String reservationTerms, String agreementTerms) {
    }

    public record PropertyRow(Long id, String code, String title, PropertyType type, ListingPurpose purpose, PropertyStatus status, String area, Integer bedrooms,
                              Integer bathrooms, BigDecimal sizeSqm, BigDecimal salePrice, BigDecimal rentPrice, String agentName, Long coverPhotoId, int photos,
                              LocalDate listedDate, long daysListed, boolean featured, boolean publicListing, int openLeads, int upcomingViewings) {
    }

    public record PhotoDto(Long id, String fileName, long sizeBytes, boolean cover) {
    }

    public record PropertyDto(Long id, String code, String title, PropertyType type, ListingPurpose purpose, PropertyStatus status, String description, String area,
                              String address, String titleReference, Double latitude, Double longitude, Integer bedrooms, Integer bathrooms, Integer parkingSpaces,
                              BigDecimal sizeSqm, BigDecimal plotSqm, Integer yearBuilt, boolean furnished, String features, BigDecimal salePrice, BigDecimal rentPrice,
                              boolean negotiable, boolean featured, boolean publicListing, String ownerName, String ownerPhone, String ownerEmail, Long agentUserId, String agentName,
                              LocalDate listedDate, String createdByName, Instant createdAt, List<PhotoDto> photos, List<ViewingDto> viewings, List<LeadRow> leads,
                              boolean canManage) {
    }

    public record NoteDto(Long id, NoteKind kind, String text, String createdByName, Instant createdAt) {
    }

    public record LeadRow(Long id, String name, String phone, String email, LeadInterest interest, LeadStatus status, LeadSource source, BigDecimal budgetMin,
                          BigDecimal budgetMax, String preferredArea, Long propertyId, String propertyCode, String assignedName, Long assignedUserId, LocalDate nextFollowUp,
                          boolean followUpOverdue, boolean stale, Instant lastContactAt, Instant createdAt, int viewings) {
    }

    public record LeadDto(Long id, String name, String phone, String email, LeadInterest interest, LeadStatus status, LeadSource source, BigDecimal budgetMin,
                          BigDecimal budgetMax, String preferredArea, PropertyType preferredType, Integer minBedrooms, String requirements, Long propertyId,
                          String propertyCode, String propertyTitle, Long assignedUserId, String assignedName, LocalDate nextFollowUp, Instant lastContactAt,
                          String lostReason, Long customerId, String createdByName, Instant createdAt, List<NoteDto> notes, List<ViewingDto> viewings, boolean canManage) {
    }

    public record Match(PropertyRow property, int score, List<String> reasons) {
    }

    public record ViewingDto(Long id, Long propertyId, String propertyCode, String propertyTitle, String propertyArea, Long leadId, String leadName, String leadPhone,
                             Long agentUserId, String agentName, Instant startAt, Instant endAt, ViewingStatus status, String feedback, boolean canManage, Instant clientRemindedAt,
                             boolean leadHasEmail) {
    }

    public record Count(String label, long value) {
    }

    public record Overview(long properties, long available, long reserved, long soldOrLet, BigDecimal availableSaleValue, BigDecimal availableMonthlyRent, long newLeadsThisMonth,
                           long openLeads, long followUpsOverdue, long viewingsToday, long viewingsNext7Days, List<Count> propertiesByStatus, List<Count> leadsByStatus,
                           List<Count> leadsBySource, List<ViewingDto> upcomingViewings, List<LeadRow> needAttention, long openDeals, BigDecimal openDealValue, long soldThisMonth,
                           BigDecimal soldValueThisMonth, BigDecimal commissionToPost, long unitsAvailable, long unitsReserved, long unitsSoldThisMonth,
                           BigDecimal unitSalesValueThisMonth, BigDecimal unitReceivable, long documentsExpiring, long valuationsExpired) {
    }
}
