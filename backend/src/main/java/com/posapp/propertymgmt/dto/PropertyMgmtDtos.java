package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.LeaseStatus;
import com.posapp.propertymgmt.PmPropertyKind;
import com.posapp.propertymgmt.PmUnitKind;
import com.posapp.propertymgmt.RentFrequency;
import com.posapp.propertymgmt.TenantKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the Property Management screens read. */
public final class PropertyMgmtDtos {

    private PropertyMgmtDtos() {
    }

    public record SettingsDto(List<String> areas, int expiryWarningDays, int defaultDueDay, BigDecimal defaultFeePercent, int invoiceAheadDays, boolean autoInvoice, BigDecimal feeVatPercent, boolean autoStatements, int autoStatementsDay, boolean autoSendStatements,
                               LocalDate autoStatementsThrough) {
    }

    public record OwnerRow(Long id, String code, String name, String phone, String email, boolean active, long properties, long units, long occupied, BigDecimal monthlyRent) {
    }

    public record OwnerDto(Long id, String code, String name, String phone, String email, String idNumber, String taxNumber, String address, String bankName, String bankAccount,
                           BigDecimal feePercent, boolean active, String notes, List<PropertyRow> properties, long units, long occupied, BigDecimal monthlyRent, boolean canManage,
                           Instant createdAt) {
    }

    /** feePercent is the property's own; effectiveFeePercent is the one that applies (the property's, else the lead owner's, else the module's). ownerId / ownerName are the lead owner; ownersLabel lists all owners with their shares; sharePercent is the share of the owner the list was asked for. */
    public record PropertyRow(Long id, String code, String name, PmPropertyKind kind, Long ownerId, String ownerName, String area, boolean active, long units, long occupied, long vacant,
                              BigDecimal monthlyRent, BigDecimal effectiveFeePercent, int ownerCount, String ownersLabel, BigDecimal sharePercent) {
    }

    public record PropertyDto(Long id, String code, String name, PmPropertyKind kind, Long ownerId, String ownerName, String area, String address, String titleReference,
                              BigDecimal feePercent, BigDecimal effectiveFeePercent, LocalDate managedFrom, boolean active, String notes, long unitCount, long occupied, long upcoming,
                              long vacant, long offMarket, BigDecimal monthlyRent, BigDecimal vacantMarketRent, List<UnitRow> units, boolean canManage, Instant createdAt, int ownerCount, String ownersLabel) {
    }

    /**
     * occupancy: OCCUPIED (a lease in force), UPCOMING (a signed lease that has not started), VACANT, or OFF_MARKET. When a lease is in force, tenant, rent and lease dates are
     * its; holdingOver says it is past its end date.
     */
    public record UnitRow(Long id, Long propertyId, String propertyCode, String propertyName, String unitNo, PmUnitKind kind, String floor, Integer bedrooms, Integer bathrooms,
                          BigDecimal sizeSqm, BigDecimal marketRent, BigDecimal defaultDeposit, boolean offMarket, String offMarketNote, String occupancy, Long leaseId, String leaseCode,
                          Long tenantId, String tenantName, BigDecimal rent, RentFrequency frequency, BigDecimal monthlyRent, LocalDate leaseStart, LocalDate leaseEnd, Long daysToEnd,
                          boolean holdingOver, LocalDate vacantSince, String notes) {
    }

    public record UnitDto(UnitRow unit, String ownerName, Long ownerId, List<LeaseRow> leases, boolean canManage) {
    }

    public record TenantRow(Long id, String code, TenantKind kind, String name, String phone, String email, boolean active, long currentLeases, String currentUnits, Long customerId) {
    }

    public record TenantDto(Long id, String code, TenantKind kind, String name, String contactPerson, String phone, String email, String idNumber, String address, String employer,
                            String emergencyName, String emergencyPhone, Long customerId, boolean active, String notes, List<LeaseRow> leases, boolean canManage, Instant createdAt) {
    }

    /**
     * state: DRAFT, UPCOMING (signed, not started), CURRENT, EXPIRING (ends within the warning period), EXPIRED (past its end date and still not ended or renewed), ENDED or
     * TERMINATED, or RENEWED (past its end date, its renewal signed). renewalId / renewalCode point at the lease that renews this one, if any.
     */
    public record LeaseRow(Long id, String code, LeaseStatus status, String state, Long unitId, String unitNo, Long propertyId, String propertyCode, String propertyName, Long ownerId,
                           String ownerName, Long tenantId, String tenantName, LocalDate startDate, LocalDate endDate, BigDecimal rent, RentFrequency frequency, BigDecimal monthlyRent,
                           int dueDay, BigDecimal deposit, Long daysToEnd, Long renewedFromId, String renewedFromCode, Long renewalId, String renewalCode) {
    }

    public record LeaseDto(Long id, String code, LeaseStatus status, String state, Long unitId, String unitNo, Long propertyId, String propertyCode, String propertyName, Long ownerId,
                           String ownerName, Long tenantId, String tenantName, Long customerId, LocalDate startDate, LocalDate endDate, BigDecimal rent, RentFrequency frequency,
                           BigDecimal monthlyRent, int dueDay, BigDecimal deposit, BigDecimal escalationPercent, int noticeDays, LocalDate signedDate, Long daysToEnd, Long renewedFromId,
                           String renewedFromCode, Long renewalId, String renewalCode, LocalDate terminatedDate, String terminationReason, String terms, BigDecimal suggestedRenewalRent,
                           String createdByName, boolean canManage, Instant createdAt) {
    }

    public record Count(String key, long count) {
    }

    public record Overview(long owners, long properties, long units, long occupied, long upcoming, long vacant, long offMarket, BigDecimal occupancyPercent, BigDecimal monthlyRentRoll,
                           BigDecimal vacantMarketRent, long leasesExpiring, long leasesExpired, long draftLeases, long tenants, int expiryWarningDays, List<LeaseRow> expiringLeases,
                           List<UnitRow> vacantUnits, long rentToInvoice, BigDecimal rentToInvoiceAmount, BigDecimal arrears, long tenantsInArrears,
                           long maintenanceOpen, long maintenanceUrgent, long maintenanceOverdue, List<PropertyMgmtMaintenanceDtos.MaintenanceRow> maintenanceAttention,
                           long statementsDraft, long statementsToPay, BigDecimal statementsToPayAmount, long documentsExpiring, long documentsExpired,
                           BigDecimal depositsHeld, long depositsRefundDue, BigDecimal depositsRefundDueAmount) {
    }
}
