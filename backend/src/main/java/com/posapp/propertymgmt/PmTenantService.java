package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.TenantDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.TenantRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.TenantRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Tenants: people and companies that rent or want to rent. A tenant becomes a customer when a lease starts, so rent can be invoiced to them. */
@Service
public class PmTenantService {

    private final PmTenantRepository repository;
    private final PmLeaseRepository leaseRepository;
    private final PmMaintenanceRepository maintenanceRepository;
    private final CustomerRepository customerRepository;
    private final PmSettingsService settings;
    private final PmViews views;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    private final PmDocumentService documentService;

    public PmTenantService(PmTenantRepository repository, PmLeaseRepository leaseRepository, PmMaintenanceRepository maintenanceRepository, CustomerRepository customerRepository, PmSettingsService settings, PmViews views,
                           PropertyManagementAccess access, AuditLogService auditLogService, PmDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.leaseRepository = leaseRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.customerRepository = customerRepository;
        this.settings = settings;
        this.views = views;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    public PmTenant entity(Long id) {
        return find(id);
    }

    private PmTenant find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Tenant not found: " + id));
    }

    private TenantRow row(Snapshot s, PmTenant t) {
        List<PmLease> mine = s.leases.stream().filter(l -> l.getTenantId().equals(t.getId()) && l.getStatus() == LeaseStatus.ACTIVE).toList();
        String units = mine.stream().map(l -> {
            PmUnit u = s.units.get(l.getUnitId());
            PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
            return u == null ? "" : (p == null ? "" : p.getName() + " ") + u.getUnitNo();
        }).filter(x -> !x.isBlank()).collect(Collectors.joining(", "));
        return new TenantRow(t.getId(), t.getCode(), t.getKind(), t.getName(), t.getPhone(), t.getEmail(), t.isActive(), mine.size(), units.isEmpty() ? null : units, t.getCustomerId());
    }

    @Transactional(readOnly = true)
    public List<TenantRow> list(String q, Boolean activeOnly, Boolean withLeaseOnly) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String digits = PmUtil.digits(q);
        return s.tenants.values().stream().filter(t -> !Boolean.TRUE.equals(activeOnly) || t.isActive())
                .filter(t -> needle.isEmpty() || t.getName().toLowerCase(Locale.ROOT).contains(needle) || t.getCode().toLowerCase(Locale.ROOT).contains(needle)
                        || t.getEmail() != null && t.getEmail().toLowerCase(Locale.ROOT).contains(needle) || digits != null && digits.equals(t.getPhoneKey())
                        || t.getIdNumber() != null && t.getIdNumber().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((PmTenant t) -> t.getName().toLowerCase(Locale.ROOT)))
                .map(t -> row(s, t)).filter(r -> !Boolean.TRUE.equals(withLeaseOnly) || r.currentLeases() > 0).toList();
    }

    @Transactional(readOnly = true)
    public TenantDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    private TenantDto dto(Actor a, Snapshot s, PmTenant t) {
        List<LeaseRow> leases = s.leases.stream().filter(l -> l.getTenantId().equals(t.getId()))
                .sorted(Comparator.comparing(PmLease::getStartDate).thenComparing(PmLease::getId).reversed()).map(l -> views.leaseRow(s, l)).toList();
        return new TenantDto(t.getId(), t.getCode(), t.getKind(), t.getName(), t.getContactPerson(), t.getPhone(), t.getEmail(), t.getIdNumber(), t.getAddress(), t.getEmployer(),
                t.getEmergencyName(), t.getEmergencyPhone(), t.getCustomerId(), t.isActive(), t.getNotes(), leases, a.manager(), t.getCreatedAt());
    }

    private void apply(PmTenant t, TenantRequest r, boolean isNew) {
        if (r.kind() != null) t.setKind(r.kind());
        if (isNew || r.name() != null) t.setName(PmUtil.required(r.name(), 200, "The name"));
        if (r.contactPerson() != null) t.setContactPerson(PmUtil.clean(r.contactPerson(), 200, "The contact person"));
        if (r.phone() != null) {
            t.setPhone(PmUtil.clean(r.phone(), 40, "The phone number"));
            t.setPhoneKey(PmUtil.digits(t.getPhone()));
            if (t.getPhoneKey() != null) {
                PmTenant same = repository.findAll().stream().filter(x -> !x.getId().equals(t.getId()) && t.getPhoneKey().equals(x.getPhoneKey())).findFirst().orElse(null);
                if (same != null) throw ApiException.badRequest("A tenant with that phone number already exists: " + same.getName() + " (" + same.getCode() + ").");
            }
        }
        if (r.email() != null) t.setEmail(PmUtil.clean(r.email(), 255, "The e-mail"));
        if (r.idNumber() != null) t.setIdNumber(PmUtil.clean(r.idNumber(), 60, "The ID number"));
        if (r.address() != null) t.setAddress(PmUtil.clean(r.address(), 500, "The address"));
        if (r.employer() != null) t.setEmployer(PmUtil.clean(r.employer(), 200, "The employer"));
        if (r.emergencyName() != null) t.setEmergencyName(PmUtil.clean(r.emergencyName(), 200, "The emergency contact"));
        if (r.emergencyPhone() != null) t.setEmergencyPhone(PmUtil.clean(r.emergencyPhone(), 40, "The emergency phone"));
        if (r.notes() != null) t.setNotes(PmUtil.clean(r.notes(), 2000, "The notes"));
        if (r.active() != null) t.setActive(r.active());
    }

    @Transactional
    public TenantDto create(Actor a, TenantRequest r) {
        access.on();
        PmTenant t = new PmTenant();
        apply(t, r, true);
        t.setCode(settings.nextCode("TN"));
        repository.save(t);
        auditLogService.record("PM_TENANT_CREATED", "Added tenant " + t.getName() + " (" + t.getCode() + ")", "PmTenant", t.getId());
        return dto(a, views.snapshot(), t);
    }

    @Transactional
    public TenantDto update(Actor a, Long id, TenantRequest r) {
        access.on();
        PmTenant t = find(id);
        apply(t, r, false);
        if (Boolean.FALSE.equals(r.active())) {
            boolean inUse = leaseRepository.findByTenantIdOrderByStartDateDescIdDesc(id).stream().anyMatch(l -> l.getStatus() == LeaseStatus.ACTIVE);
            if (inUse) throw ApiException.badRequest("This tenant has a lease in force. End or terminate it before marking the tenant inactive.");
        }
        repository.save(t);
        auditLogService.record("PM_TENANT_UPDATED", "Changed tenant " + t.getName() + " (" + t.getCode() + ")", "PmTenant", id);
        return dto(a, views.snapshot(), t);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmTenant t = find(id);
        if (leaseRepository.existsByTenantId(id)) throw ApiException.badRequest("This tenant has leases. Leases are kept as a record: mark the tenant inactive instead.");
        if (maintenanceRepository.existsByTenantId(id)) throw ApiException.badRequest("This tenant has maintenance requests. They are kept as a record: mark the tenant inactive instead.");
        documentService.deleteFor(PmDocSubject.TENANT, id);
        repository.delete(t);
        auditLogService.record("PM_TENANT_DELETED", "Deleted tenant " + t.getName() + " (" + t.getCode() + ")");
    }

    /** The tenant's customer record (made, or matched by phone number, the first time it is needed). Rent invoices go to this customer. */
    @Transactional
    public Customer ensureCustomer(PmTenant t) {
        if (t.getCustomerId() != null) {
            Customer c = customerRepository.findById(t.getCustomerId()).orElse(null);
            if (c != null) return c;
            t.setCustomerId(null);
        }
        Customer c = null;
        String key = t.getPhoneKey();
        if (key != null) c = customerRepository.findAll().stream().filter(x -> key.equals(PmUtil.digits(x.getPhone()))).findFirst().orElse(null);
        if (c == null) {
            c = new Customer();
            c.setName(t.getName());
            c.setPhone(t.getPhone());
            c.setEmail(t.getEmail());
            customerRepository.save(c);
        }
        t.setCustomerId(c.getId());
        repository.save(t);
        return c;
    }
}
