package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.CostBearer;
import com.posapp.propertymgmt.MaintenanceCategory;
import com.posapp.propertymgmt.MaintenancePriority;
import com.posapp.propertymgmt.PmPropertyKind;
import com.posapp.propertymgmt.PmUnitKind;
import com.posapp.propertymgmt.RentFrequency;
import com.posapp.propertymgmt.TenantKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the Property Management screens send. On an edit, anything left out (null) stays as it was; a blank text clears it. Optional amounts and percentages are cleared with a
 * value of 0, optional dates with an explicit clear flag.
 */
public final class PropertyMgmtRequests {

    private PropertyMgmtRequests() {
    }

    public record SettingsRequest(String areas, Integer expiryWarningDays, Integer defaultDueDay, BigDecimal defaultFeePercent, Boolean clearFee, Integer invoiceAheadDays, Boolean autoInvoice,
                                  BigDecimal feeVatPercent, Boolean clearFeeVat, Boolean autoStatements, Integer autoStatementsDay, Boolean autoSendStatements) {
    }

    public record OwnerRequest(String name, String phone, String email, String idNumber, String taxNumber, String address, String bankName, String bankAccount, BigDecimal feePercent,
                               Boolean clearFee, Boolean active, String notes) {
    }

    public record PropertyRequest(String name, PmPropertyKind kind, Long ownerId, String area, String address, String titleReference, BigDecimal feePercent, Boolean clearFee,
                                  LocalDate managedFrom, Boolean clearManagedFrom, Boolean active, String notes) {
    }

    public record UnitRequest(String unitNo, PmUnitKind kind, String floor, Integer bedrooms, Integer bathrooms, BigDecimal sizeSqm, BigDecimal marketRent, BigDecimal defaultDeposit,
                              Boolean offMarket, String offMarketNote, String notes) {
    }

    /** Makes the units prefix+from ... prefix+to (numbers padded with zeros to {@code pad} digits), all alike. At most 200. */
    public record BulkUnitsRequest(String prefix, Integer from, Integer to, Integer pad, PmUnitKind kind, String floor, Integer bedrooms, Integer bathrooms, BigDecimal sizeSqm,
                                   BigDecimal marketRent, BigDecimal defaultDeposit) {
    }

    public record TenantRequest(TenantKind kind, String name, String contactPerson, String phone, String email, String idNumber, String address, String employer, String emergencyName,
                                String emergencyPhone, Boolean active, String notes) {
    }

    /** unitId, tenantId, startDate, endDate and rent are needed for a new lease. */
    public record LeaseRequest(Long unitId, Long tenantId, LocalDate startDate, LocalDate endDate, BigDecimal rent, RentFrequency frequency, Integer dueDay, BigDecimal deposit,
                               BigDecimal escalationPercent, Integer noticeDays, LocalDate signedDate, Boolean clearSignedDate, String terms) {
    }

    /** billFrom: rent is billed only for periods that end on or after this date (for a lease signed late, whose earlier rent was settled outside). */
    public record ActivateRequest(LocalDate signedDate, LocalDate billFrom) {
    }

    public record TerminateRequest(LocalDate date, String reason) {
    }

    /** Marks a lease whose term has run out as ended. Only an administrator can end it earlier, with {@code force}. */
    public record EndRequest(Boolean force) {
    }

    /** The renewal starts the day after the lease ends and runs for the same length unless told otherwise; the rent is raised by the lease's yearly increase. */
    public record RenewRequest(LocalDate startDate, LocalDate endDate, BigDecimal rent, BigDecimal deposit, String terms) {
    }

    /** through: invoice rent that falls due on or before this date (default: today plus the invoicing lead time). leaseId: only this lease. */
    public record InvoiceRentRequest(Long leaseId, LocalDate through) {
    }

    public record WaiveRequest(String reason) {
    }

    /** unitId and title are needed for a new request. The unit cannot be changed on an existing one. clearTenant, clearScheduled and clearDue clear those; 0 clears a cost. */
    public record MaintenanceRequest(Long unitId, Long tenantId, Boolean clearTenant, String title, String description, MaintenanceCategory category, MaintenancePriority priority,
                                     LocalDate reportedDate, String reportedBy, LocalDate scheduledDate, Boolean clearScheduled, LocalDate dueDate, Boolean clearDue, String contractorName,
                                     String contractorPhone, BigDecimal estimatedCost, BigDecimal actualCost, CostBearer costBearer) {
    }

    public record CompleteMaintenanceRequest(LocalDate completedDate, BigDecimal actualCost, String resolution, CostBearer costBearer) {
    }

    public record CancelMaintenanceRequest(String reason) {
    }

    /** Statement for one owner over a period (to may not be in the future). */
    public record GenerateStatementRequest(Long ownerId, LocalDate periodFrom, LocalDate periodTo, String notes) {
    }

    /** One statement for every active owner that had rent collected or repairs paid in the period. */
    public record GenerateAllStatementsRequest(LocalDate periodFrom, LocalDate periodTo) {
    }

    /** A manual line. amount is signed from the owner's side: positive adds to what the owner gets, negative takes from it. */
    public record StatementAdjustmentRequest(Long propertyId, String description, BigDecimal amount, LocalDate date) {
    }

    public record StatementNotesRequest(String notes) {
    }

    /** method is needed when something is paid to the owner. */
    public record PayStatementRequest(LocalDate paidDate, String method, String reference) {
    }

    /** includePaid: also send paid statements. resend: also send those already e-mailed. ids: only these statements (leave out for every one that qualifies). */
    public record EmailAllStatementsRequest(Boolean includePaid, Boolean resend, List<Long> ids) {
    }

    /** to: an address to use instead of the one on the owner. message: an optional note shown above the statement in the e-mail. */
    public record EmailStatementRequest(String to, String message) {
    }
}
