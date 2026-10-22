package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.ActivateRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EndRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.LeaseRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.RenewRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.TerminateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Leases. A lease is drafted, signed (activated: the unit is let and the tenant becomes a customer), and then runs until it is ended, terminated early or renewed. A unit can have
 * only one lease in force at any time.
 */
@Service
public class PmLeaseService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PmLeaseRepository repository;
    private final PmUnitRepository unitRepository;
    private final PmPropertyRepository propertyRepository;
    private final PmTenantRepository tenantRepository;
    private final PmTenantService tenantService;
    private final PmSettingsService settings;
    private final PmViews views;
    private final PmRentService rentService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    private final PmDocumentService documentService;
    private final PmDepositService depositService;
    private final PmInspectionService inspectionService;
    private final PmChargeService chargeService;

    public PmLeaseService(PmLeaseRepository repository, PmUnitRepository unitRepository, PmPropertyRepository propertyRepository, PmTenantRepository tenantRepository,
                          PmTenantService tenantService, PmSettingsService settings, PmViews views, PmRentService rentService, PropertyManagementAccess access, AuditLogService auditLogService, PmDocumentService documentService,
                          PmDepositService depositService, PmInspectionService inspectionService, PmChargeService chargeService) {
        this.chargeService = chargeService;
        this.documentService = documentService;
        this.depositService = depositService;
        this.inspectionService = inspectionService;
        this.repository = repository;
        this.unitRepository = unitRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.settings = settings;
        this.views = views;
        this.rentService = rentService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmLease find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Lease not found: " + id));
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<LeaseRow> list(String q, LeaseStatus status, String state, Long unitId, Long tenantId, Long propertyId, Long ownerId, Boolean expiringOnly) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return s.leases.stream().filter(l -> status == null || l.getStatus() == status)
                .filter(l -> unitId == null || unitId.equals(l.getUnitId()))
                .filter(l -> tenantId == null || tenantId.equals(l.getTenantId()))
                .map(l -> views.leaseRow(s, l))
                .filter(r -> state == null || state.isBlank() || state.equalsIgnoreCase(r.state()))
                .filter(r -> propertyId == null || propertyId.equals(r.propertyId()))
                .filter(r -> ownerId == null || PmViews.ownsNow(s, ownerId, r.propertyId()))
                .filter(r -> !Boolean.TRUE.equals(expiringOnly) || "EXPIRING".equals(r.state()) || "EXPIRED".equals(r.state()))
                .filter(r -> needle.isEmpty() || r.code().toLowerCase(Locale.ROOT).contains(needle) || r.tenantName() != null && r.tenantName().toLowerCase(Locale.ROOT).contains(needle)
                        || r.propertyName() != null && r.propertyName().toLowerCase(Locale.ROOT).contains(needle) || r.unitNo() != null && r.unitNo().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(LeaseRow::endDate).thenComparing(LeaseRow::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public LeaseDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    /** The rent a renewal starts with: this rent raised by the lease's yearly increase. */
    static BigDecimal renewalRent(PmLease l) {
        if (l.getEscalationPercent() == null || l.getEscalationPercent().signum() == 0) return l.getRent();
        return l.getRent().multiply(HUNDRED.add(l.getEscalationPercent())).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private LeaseDto dto(Actor a, Snapshot s, PmLease l) {
        LeaseRow r = views.leaseRow(s, l);
        PmTenant t = s.tenants.get(l.getTenantId());
        return new LeaseDto(l.getId(), l.getCode(), l.getStatus(), r.state(), l.getUnitId(), r.unitNo(), r.propertyId(), r.propertyCode(), r.propertyName(), r.ownerId(), r.ownerName(),
                l.getTenantId(), r.tenantName(), t == null ? null : t.getCustomerId(), l.getStartDate(), l.getEndDate(), l.getRent(), l.getFrequency(), r.monthlyRent(), l.getDueDay(),
                l.getDeposit(), l.getEscalationPercent(), l.getNoticeDays(), l.getSignedDate(), r.daysToEnd(), l.getRenewedFromId(), r.renewedFromCode(), r.renewalId(), r.renewalCode(),
                l.getTerminatedDate(), l.getTerminationReason(), l.getTerms(), l.getStatus() == LeaseStatus.ACTIVE ? renewalRent(l) : null, l.getCreatedByName(), a.manager(), l.getCreatedAt());
    }

    // ------------------------------------------------------------------ drafting

    private static BigDecimal amount(BigDecimal v, String what) {
        if (v.signum() < 0) throw ApiException.badRequest(what + " cannot be negative.");
        return v.signum() == 0 ? null : v;
    }

    private void check(PmLease l) {
        if (l.getEndDate().isBefore(l.getStartDate())) throw ApiException.badRequest("The lease cannot end before it starts.");
        if (l.getStartDate().plusYears(50).isBefore(l.getEndDate())) throw ApiException.badRequest("A lease can run for at most 50 years.");
        if (l.getRent() == null || l.getRent().signum() <= 0) throw ApiException.badRequest("The rent must be more than zero.");
        if (l.getDueDay() < 1 || l.getDueDay() > 28) throw ApiException.badRequest("Rent can fall due on the 1st to the 28th of the month.");
        if (l.getNoticeDays() < 0 || l.getNoticeDays() > 365) throw ApiException.badRequest("The notice period is 0 to 365 days.");
    }

    private void apply(PmLease l, LeaseRequest r, boolean isNew) {
        boolean draft = isNew || l.getStatus() == LeaseStatus.DRAFT;
        if (!draft) {
            if (l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.badRequest("A lease that has ended cannot be changed.");
            boolean locked = r.unitId() != null && !r.unitId().equals(l.getUnitId()) || r.tenantId() != null && !r.tenantId().equals(l.getTenantId())
                    || r.startDate() != null && !r.startDate().equals(l.getStartDate()) || r.endDate() != null && !r.endDate().equals(l.getEndDate())
                    || r.rent() != null && r.rent().compareTo(l.getRent()) != 0 || r.frequency() != null && r.frequency() != l.getFrequency()
                    || r.deposit() != null && r.deposit().compareTo(l.getDeposit() == null ? BigDecimal.ZERO : l.getDeposit()) != 0;
            if (locked) throw ApiException.badRequest("The unit, tenant, dates, rent and deposit cannot be changed on a lease in force. Renew it, or terminate it and make a new one.");
        }
        if (draft) {
            if (isNew || r.unitId() != null) {
                if (r.unitId() == null) throw ApiException.badRequest("Choose the unit.");
                PmUnit u = unitRepository.findById(r.unitId()).orElseThrow(() -> ApiException.badRequest("That unit does not exist."));
                PmProperty p = propertyRepository.findById(u.getPropertyId()).orElseThrow(() -> ApiException.badRequest("That unit's property does not exist."));
                if (!p.isActive()) throw ApiException.badRequest("That property is inactive.");
                l.setUnitId(u.getId());
                if (isNew && r.deposit() == null && u.getDefaultDeposit() != null) l.setDeposit(u.getDefaultDeposit());
            }
            if (isNew || r.tenantId() != null) {
                if (r.tenantId() == null) throw ApiException.badRequest("Choose the tenant.");
                PmTenant t = tenantRepository.findById(r.tenantId()).orElseThrow(() -> ApiException.badRequest("That tenant does not exist."));
                if (!t.isActive() && (isNew || !t.getId().equals(l.getTenantId()))) throw ApiException.badRequest("That tenant is inactive.");
                l.setTenantId(t.getId());
            }
            if (isNew && (r.startDate() == null || r.endDate() == null)) throw ApiException.badRequest("Give the start and end dates.");
            if (r.startDate() != null) l.setStartDate(r.startDate());
            if (r.endDate() != null) l.setEndDate(r.endDate());
            if (isNew && r.rent() == null) throw ApiException.badRequest("Enter the rent.");
            if (r.rent() != null) l.setRent(r.rent().setScale(2, RoundingMode.HALF_UP));
            if (r.frequency() != null) l.setFrequency(r.frequency());
            if (r.deposit() != null) l.setDeposit(amount(r.deposit(), "The deposit"));
        }
        if (r.dueDay() != null) l.setDueDay(r.dueDay());
        else if (isNew) l.setDueDay(settings.entity().getDefaultDueDay());
        if (r.escalationPercent() != null) {
            PmUtil.percent(r.escalationPercent(), "The yearly increase");
            l.setEscalationPercent(r.escalationPercent().signum() == 0 ? null : r.escalationPercent());
        }
        if (r.noticeDays() != null) l.setNoticeDays(r.noticeDays());
        if (Boolean.TRUE.equals(r.clearSignedDate())) l.setSignedDate(null);
        else if (r.signedDate() != null) l.setSignedDate(r.signedDate());
        if (r.terms() != null) l.setTerms(PmUtil.clean(r.terms(), 2000, "The terms"));
        check(l);
    }

    @Transactional
    public LeaseDto create(Actor a, LeaseRequest r) {
        access.on();
        PmLease l = new PmLease();
        apply(l, r, true);
        l.setCode(settings.nextCode("LS"));
        l.setCreatedByName(PropertyManagementAccess.name(a.user()));
        repository.save(l);
        auditLogService.record("PM_LEASE_CREATED", "Drafted lease " + l.getCode(), "PmLease", l.getId());
        return dto(a, views.snapshot(), l);
    }

    @Transactional
    public LeaseDto update(Actor a, Long id, LeaseRequest r) {
        access.on();
        PmLease l = find(id);
        int oldDueDay = l.getDueDay();
        apply(l, r, false);
        repository.save(l);
        if (l.getStatus() == LeaseStatus.ACTIVE && l.getDueDay() != oldDueDay) rentService.refreshDueDates(l);
        auditLogService.record("PM_LEASE_UPDATED", "Changed lease " + l.getCode(), "PmLease", id);
        return dto(a, views.snapshot(), l);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmLease l = find(id);
        if (l.getStatus() != LeaseStatus.DRAFT) throw ApiException.badRequest("Only a draft lease can be deleted. A lease that was signed is kept as a record.");
        depositService.requireNone(id);
        inspectionService.deleteForLease(id);
        chargeService.deleteForLease(id);
        documentService.deleteFor(PmDocSubject.LEASE, id);
        repository.delete(l);
        auditLogService.record("PM_LEASE_DELETED", "Deleted draft lease " + l.getCode());
    }

    // ------------------------------------------------------------------ life of a lease

    @Transactional
    public LeaseDto activate(Actor a, Long id, ActivateRequest r) {
        access.on();
        PmLease l = find(id);
        if (l.getStatus() != LeaseStatus.DRAFT) throw ApiException.badRequest("Only a draft lease can be signed.");
        PmUnit u = unitRepository.findById(l.getUnitId()).orElseThrow(() -> ApiException.badRequest("The unit no longer exists."));
        PmProperty p = propertyRepository.findById(u.getPropertyId()).orElseThrow(() -> ApiException.badRequest("The property no longer exists."));
        PmTenant t = tenantRepository.findById(l.getTenantId()).orElseThrow(() -> ApiException.badRequest("The tenant no longer exists."));
        if (!p.isActive()) throw ApiException.badRequest("The property is inactive.");
        if (!t.isActive()) throw ApiException.badRequest("The tenant is inactive.");
        if (u.isOffMarket()) throw ApiException.badRequest("Unit " + u.getUnitNo() + " is off the market" + (u.getOffMarketNote() == null ? "" : " (" + u.getOffMarketNote() + ")") + ". Put it back on the market first.");
        LocalDate today = LocalDate.now();
        PmLease predecessor = l.getRenewedFromId() == null ? null : repository.findById(l.getRenewedFromId()).orElse(null);
        for (PmLease o : repository.findByUnitIdOrderByStartDateDescIdDesc(u.getId())) {
            if (o.getId().equals(l.getId()) || o.getStatus() != LeaseStatus.ACTIVE) continue;
            if (predecessor != null && o.getId().equals(predecessor.getId())) {
                if (!o.getEndDate().isBefore(l.getStartDate())) throw ApiException.badRequest("This renewal starts on " + l.getStartDate() + ", before the lease it renews ends (" + o.getEndDate() + ").");
                continue;
            }
            boolean overlaps = !o.getEndDate().isBefore(l.getStartDate()) && !o.getStartDate().isAfter(l.getEndDate());
            if (overlaps) throw ApiException.badRequest("Lease " + o.getCode() + " is in force on this unit from " + o.getStartDate() + " to " + o.getEndDate() + ". The dates overlap.");
            if (o.getEndDate().isBefore(today)) throw ApiException.badRequest("Lease " + o.getCode() + " is past its end date and still open. End it first.");
        }
        l.setStatus(LeaseStatus.ACTIVE);
        l.setSignedDate(r != null && r.signedDate() != null ? r.signedDate() : l.getSignedDate() != null ? l.getSignedDate() : today);
        // The lease being renewed stays in force until its own end date; it is closed here only if that date has already passed.
        if (predecessor != null && predecessor.getStatus() == LeaseStatus.ACTIVE && predecessor.getEndDate().isBefore(today)) {
            predecessor.setStatus(LeaseStatus.ENDED);
            repository.save(predecessor);
        }
        tenantService.ensureCustomer(t);
        repository.save(l);
        rentService.generate(l, r == null ? null : r.billFrom());
        auditLogService.record("PM_LEASE_SIGNED", "Lease " + l.getCode() + " signed: " + t.getName() + ", unit " + u.getUnitNo() + " at " + p.getName(), "PmLease", id);
        return dto(a, views.snapshot(), l);
    }

    @Transactional
    public LeaseDto terminate(Actor a, Long id, TerminateRequest r) {
        access.on();
        PmLease l = find(id);
        if (l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.badRequest("Only a lease in force can be terminated.");
        String reason = PmUtil.required(r == null ? null : r.reason(), 1000, "The reason");
        LocalDate date = r.date() != null ? r.date() : LocalDate.now();
        if (date.isBefore(l.getStartDate())) throw ApiException.badRequest("The lease cannot be terminated before it started.");
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("Record the termination on the day the tenant leaves: today or earlier.");
        l.setStatus(LeaseStatus.TERMINATED);
        l.setTerminatedDate(date);
        l.setTerminationReason(reason);
        repository.save(l);
        rentService.endRentOn(l, date);
        auditLogService.record("PM_LEASE_TERMINATED", "Lease " + l.getCode() + " terminated on " + date + ": " + reason, "PmLease", id);
        return dto(a, views.snapshot(), l);
    }

    @Transactional
    public LeaseDto end(Actor a, Long id, EndRequest r) {
        access.on();
        PmLease l = find(id);
        if (l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.badRequest("Only a lease in force can be ended.");
        if (!l.getEndDate().isBefore(LocalDate.now())) {
            if (!(r != null && Boolean.TRUE.equals(r.force()) && a.admin())) throw ApiException.badRequest("The lease runs until " + l.getEndDate() + ". Terminate it if the tenant leaves early.");
        }
        l.setStatus(LeaseStatus.ENDED);
        repository.save(l);
        if (!l.getEndDate().isBefore(LocalDate.now())) rentService.endRentOn(l, LocalDate.now());
        auditLogService.record("PM_LEASE_ENDED", "Lease " + l.getCode() + " ended", "PmLease", id);
        return dto(a, views.snapshot(), l);
    }

    @Transactional
    public LeaseDto renew(Actor a, Long id, RenewRequest r) {
        access.on();
        PmLease src = find(id);
        if (src.getStatus() != LeaseStatus.ACTIVE) throw ApiException.badRequest("Only a lease in force can be renewed.");
        if (repository.existsByRenewedFromId(id)) throw ApiException.badRequest("This lease already has a renewal. Open it instead.");
        LocalDate start = r != null && r.startDate() != null ? r.startDate() : src.getEndDate().plusDays(1);
        LocalDate end;
        if (r != null && r.endDate() != null) {
            end = r.endDate();
        } else {
            Period term = Period.between(src.getStartDate(), src.getEndDate().plusDays(1));
            end = start.plus(term).minusDays(1);
        }
        PmLease n = new PmLease();
        n.setUnitId(src.getUnitId());
        n.setTenantId(src.getTenantId());
        n.setStartDate(start);
        n.setEndDate(end);
        n.setRent((r != null && r.rent() != null ? r.rent() : renewalRent(src)).setScale(2, RoundingMode.HALF_UP));
        n.setFrequency(src.getFrequency());
        n.setDueDay(src.getDueDay());
        n.setDeposit(r != null && r.deposit() != null ? amount(r.deposit(), "The deposit") : src.getDeposit());
        n.setEscalationPercent(src.getEscalationPercent());
        n.setNoticeDays(src.getNoticeDays());
        n.setTerms(r != null && r.terms() != null ? PmUtil.clean(r.terms(), 2000, "The terms") : src.getTerms());
        n.setRenewedFromId(src.getId());
        n.setCreatedByName(PropertyManagementAccess.name(a.user()));
        if (!n.getStartDate().isAfter(src.getEndDate())) throw ApiException.badRequest("The renewal must start after the lease ends (" + src.getEndDate() + ").");
        check(n);
        n.setCode(settings.nextCode("LS"));
        repository.save(n);
        auditLogService.record("PM_LEASE_RENEWAL", "Drafted renewal " + n.getCode() + " of lease " + src.getCode(), "PmLease", n.getId());
        return dto(a, views.snapshot(), n);
    }
}
