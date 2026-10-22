package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtMaintenanceDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.CancelMaintenanceRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.CompleteMaintenanceRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.MaintenanceRequest;
import com.posapp.workorder.WorkOrder;
import com.posapp.workorder.WorkOrderPriority;
import com.posapp.workorder.WorkOrderRepository;
import com.posapp.workorder.WorkOrderService;
import com.posapp.workorder.WorkOrderType;
import com.posapp.workorder.dto.WorkOrderDtos.WorkOrderDetail;
import com.posapp.workorder.dto.WorkOrderRequests.WorkOrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maintenance requests: a repair or upkeep job on a unit, from the report to completion, with the cost and who pays. A job can raise a Work Order (an internal job in the Work Orders
 * module), and a job the tenant pays for can be invoiced to the tenant. Jobs the owner pays for are taken off the owner's statement later.
 */
@Service
public class PmMaintenanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<MaintenanceStatus> ACTIVE = EnumSet.of(MaintenanceStatus.OPEN, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.ON_HOLD);

    private final PmMaintenanceRepository repository;
    private final PmTenantService tenantService;
    private final PmSettingsService settings;
    private final PmViews views;
    private final InvoiceService invoiceService;
    private final CatalogService catalogService;
    private final WorkOrderService workOrderService;
    private final WorkOrderRepository workOrderRepository;
    private final ModuleService moduleService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    private final PmDocumentService documentService;

    public PmMaintenanceService(PmMaintenanceRepository repository, PmTenantService tenantService, PmSettingsService settings, PmViews views, InvoiceService invoiceService,
                                CatalogService catalogService, WorkOrderService workOrderService, WorkOrderRepository workOrderRepository, ModuleService moduleService,
                                PropertyManagementAccess access, AuditLogService auditLogService, PmDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.tenantService = tenantService;
        this.settings = settings;
        this.views = views;
        this.invoiceService = invoiceService;
        this.catalogService = catalogService;
        this.workOrderService = workOrderService;
        this.workOrderRepository = workOrderRepository;
        this.moduleService = moduleService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmMaintenance find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance request not found: " + id));
    }

    private static BigDecimal cost(BigDecimal v, String what) {
        if (v.signum() < 0) throw ApiException.badRequest(what + " cannot be negative.");
        return v.signum() == 0 ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean active(PmMaintenance m) {
        return ACTIVE.contains(m.getStatus());
    }

    private static boolean overdue(PmMaintenance m, LocalDate today) {
        return active(m) && m.getDueDate() != null && m.getDueDate().isBefore(today);
    }

    // ------------------------------------------------------------------ rows

    MaintenanceRow row(Snapshot s, PmMaintenance m) {
        PmUnit u = s.units.get(m.getUnitId());
        PmProperty p = u == null ? null : s.properties.get(u.getPropertyId());
        PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
        PmTenant t = m.getTenantId() == null ? null : s.tenants.get(m.getTenantId());
        Long daysOpen = null;
        if (active(m)) daysOpen = ChronoUnit.DAYS.between(m.getReportedDate(), s.today);
        else if (m.getStatus() == MaintenanceStatus.COMPLETED && m.getCompletedDate() != null) daysOpen = ChronoUnit.DAYS.between(m.getReportedDate(), m.getCompletedDate());
        return new MaintenanceRow(m.getId(), m.getCode(), m.getUnitId(), u == null ? null : u.getUnitNo(), p == null ? null : p.getId(), p == null ? null : p.getName(),
                o == null ? null : o.getId(), o == null ? null : o.getName(), m.getTenantId(), t == null ? null : t.getName(), m.getTitle(), m.getCategory(), m.getPriority(), m.getStatus(),
                m.getReportedDate(), m.getScheduledDate(), m.getDueDate(), m.getCompletedDate(), daysOpen, overdue(m, s.today), m.getContractorName(), m.getEstimatedCost(), m.getActualCost(),
                m.getCostBearer(), m.getWorkOrderNumber(), m.getInvoiceNumber());
    }

    private static int rank(MaintenancePriority p) {
        return switch (p) {
            case URGENT -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRow> list(String q, MaintenanceStatus status, Boolean openOnly, MaintenancePriority priority, MaintenanceCategory category, Long unitId, Long propertyId,
                                     Long ownerId, Long tenantId, Boolean overdueOnly) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(m -> status == null || m.getStatus() == status)
                .filter(m -> !Boolean.TRUE.equals(openOnly) || active(m))
                .filter(m -> priority == null || m.getPriority() == priority)
                .filter(m -> category == null || m.getCategory() == category)
                .filter(m -> unitId == null || unitId.equals(m.getUnitId()))
                .filter(m -> tenantId == null || tenantId.equals(m.getTenantId()))
                .filter(m -> !Boolean.TRUE.equals(overdueOnly) || overdue(m, s.today))
                .map(m -> row(s, m))
                .filter(r -> propertyId == null || propertyId.equals(r.propertyId()))
                .filter(r -> ownerId == null || PmViews.ownsNow(s, ownerId, r.propertyId()))
                .filter(r -> needle.isEmpty() || r.code().toLowerCase(Locale.ROOT).contains(needle) || r.title().toLowerCase(Locale.ROOT).contains(needle)
                        || r.propertyName() != null && r.propertyName().toLowerCase(Locale.ROOT).contains(needle) || r.unitNo() != null && r.unitNo().toLowerCase(Locale.ROOT).contains(needle)
                        || r.contractorName() != null && r.contractorName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((MaintenanceRow r) -> ACTIVE.contains(r.status()) ? 0 : 1).thenComparing(r -> rank(r.priority())).thenComparing(MaintenanceRow::reportedDate).thenComparing(MaintenanceRow::id))
                .toList();
    }

    private InvoiceDto invoice(Long id) {
        if (id == null) return null;
        try {
            return invoiceService.getById(id);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    private static boolean live(InvoiceDto i) {
        return i != null && !"VOID".equals(i.status());
    }

    private static boolean canInvoice(PmMaintenance m, InvoiceDto inv) {
        return m.getStatus() == MaintenanceStatus.COMPLETED && m.getCostBearer() == CostBearer.TENANT && m.getActualCost() != null && m.getTenantId() != null && !live(inv);
    }

    private MaintenanceDto dto(Actor a, Snapshot s, PmMaintenance m) {
        InvoiceDto inv = invoice(m.getInvoiceId());
        WorkOrder wo = m.getWorkOrderId() == null ? null : workOrderRepository.findById(m.getWorkOrderId()).orElse(null);
        PmLease l = m.getLeaseId() == null ? null : s.byId.get(m.getLeaseId());
        return new MaintenanceDto(row(s, m), m.getDescription(), m.getReportedBy(), m.getContractorPhone(), m.getStartedDate(), m.getResolution(), m.getLeaseId(), l == null ? null : l.getCode(),
                wo == null ? null : wo.getId(), wo == null ? null : wo.getStatus().name(), live(inv) ? inv.id() : null, live(inv) ? inv.status() : null, live(inv) ? inv.totalAmount() : null,
                live(inv) ? inv.amountPaid() : null, live(inv) ? inv.amountOutstanding() : null, moduleService.isEnabled(ModuleKey.WORK_ORDERS), canInvoice(m, inv), a.manager(),
                m.getCreatedByName(), m.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public MaintenanceDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    // ------------------------------------------------------------------ writing

    private void apply(PmMaintenance m, MaintenanceRequest r, boolean isNew, Snapshot s) {
        if (isNew) {
            if (r.unitId() == null) throw ApiException.badRequest("Choose the unit.");
            PmUnit u = s.units.get(r.unitId());
            if (u == null) throw ApiException.badRequest("That unit does not exist.");
            PmProperty p = s.properties.get(u.getPropertyId());
            if (p == null || !p.isActive()) throw ApiException.badRequest("That unit's property is inactive.");
            m.setUnitId(u.getId());
            PmLease cur = PmViews.current(s.leasesByUnit.get(u.getId()), s.today);
            if (cur != null) {
                m.setLeaseId(cur.getId());
                m.setTenantId(cur.getTenantId());
                PmTenant t = s.tenants.get(cur.getTenantId());
                if (t != null) m.setReportedBy(t.getName());
            }
        } else if (r.unitId() != null && !r.unitId().equals(m.getUnitId())) {
            throw ApiException.badRequest("The unit of a request cannot be changed. Make a new request for the other unit.");
        }
        if (isNew || r.title() != null) m.setTitle(PmUtil.required(r.title(), 200, "The title"));
        if (r.description() != null) m.setDescription(PmUtil.clean(r.description(), 2000, "The description"));
        if (r.category() != null) m.setCategory(r.category());
        if (r.priority() != null) m.setPriority(r.priority());
        if (Boolean.TRUE.equals(r.clearTenant())) {
            m.setTenantId(null);
        } else if (r.tenantId() != null) {
            if (!s.tenants.containsKey(r.tenantId())) throw ApiException.badRequest("That tenant does not exist.");
            m.setTenantId(r.tenantId());
        }
        if (r.reportedBy() != null) m.setReportedBy(PmUtil.clean(r.reportedBy(), 200, "Reported by"));
        if (isNew) m.setReportedDate(r.reportedDate() != null ? r.reportedDate() : s.today);
        else if (r.reportedDate() != null) m.setReportedDate(r.reportedDate());
        if (m.getReportedDate().isAfter(s.today)) throw ApiException.badRequest("A request cannot be reported in the future.");
        if (Boolean.TRUE.equals(r.clearScheduled())) m.setScheduledDate(null);
        else if (r.scheduledDate() != null) m.setScheduledDate(r.scheduledDate());
        if (Boolean.TRUE.equals(r.clearDue())) m.setDueDate(null);
        else if (r.dueDate() != null) m.setDueDate(r.dueDate());
        if (m.getScheduledDate() != null && m.getScheduledDate().isBefore(m.getReportedDate())) throw ApiException.badRequest("The job cannot be scheduled before it was reported.");
        if (m.getDueDate() != null && m.getDueDate().isBefore(m.getReportedDate())) throw ApiException.badRequest("The due date cannot be before the report date.");
        if (r.contractorName() != null) m.setContractorName(PmUtil.clean(r.contractorName(), 200, "The contractor"));
        if (r.contractorPhone() != null) m.setContractorPhone(PmUtil.clean(r.contractorPhone(), 40, "The contractor's phone"));
        if (r.estimatedCost() != null) m.setEstimatedCost(cost(r.estimatedCost(), "The estimate"));
        if (r.actualCost() != null) m.setActualCost(cost(r.actualCost(), "The cost"));
        if (r.costBearer() != null) m.setCostBearer(r.costBearer());
    }

    private void requireActive(PmMaintenance m, String what) {
        if (!active(m)) throw ApiException.conflict("Request " + m.getCode() + " is " + m.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ", so you cannot " + what + ". Reopen it first.");
    }

    @Transactional
    public MaintenanceDto create(Actor a, MaintenanceRequest r) {
        access.on();
        Snapshot s = views.snapshot();
        PmMaintenance m = new PmMaintenance();
        apply(m, r, true, s);
        m.setCode(settings.nextCode("MR"));
        m.setCreatedByName(PropertyManagementAccess.name(a.user()));
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_CREATED", "Maintenance request " + m.getCode() + ": " + m.getTitle(), "PmMaintenance", m.getId());
        return dto(a, s, m);
    }

    @Transactional
    public MaintenanceDto update(Actor a, Long id, MaintenanceRequest r) {
        access.on();
        PmMaintenance m = find(id);
        requireActive(m, "change it");
        Snapshot s = views.snapshot();
        apply(m, r, false, s);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_UPDATED", "Changed maintenance request " + m.getCode(), "PmMaintenance", id);
        return dto(a, s, m);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmMaintenance m = find(id);
        if (m.getStatus() != MaintenanceStatus.OPEN && m.getStatus() != MaintenanceStatus.CANCELLED) throw ApiException.conflict("Only a request that has not been started, or one that was cancelled, can be deleted.");
        if (m.getWorkOrderId() != null) throw ApiException.conflict("A work order was raised from this request. Unlink it first.");
        if (m.getInvoiceId() != null) throw ApiException.conflict("An invoice was made from this request, so it is kept as a record.");
        documentService.deleteFor(PmDocSubject.MAINTENANCE, id);
        repository.delete(m);
        auditLogService.record("PM_MAINTENANCE_DELETED", "Deleted maintenance request " + m.getCode());
    }

    @Transactional
    public MaintenanceDto start(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        if (m.getStatus() != MaintenanceStatus.OPEN && m.getStatus() != MaintenanceStatus.ON_HOLD) throw ApiException.conflict("Only an open request, or one on hold, can be started.");
        m.setStatus(MaintenanceStatus.IN_PROGRESS);
        if (m.getStartedDate() == null) m.setStartedDate(LocalDate.now());
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_STARTED", "Started maintenance request " + m.getCode(), "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    @Transactional
    public MaintenanceDto hold(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        if (m.getStatus() != MaintenanceStatus.OPEN && m.getStatus() != MaintenanceStatus.IN_PROGRESS) throw ApiException.conflict("Only an open request, or one in progress, can be put on hold.");
        m.setStatus(MaintenanceStatus.ON_HOLD);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_HELD", "Put maintenance request " + m.getCode() + " on hold", "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    @Transactional
    public MaintenanceDto complete(Actor a, Long id, CompleteMaintenanceRequest r) {
        access.on();
        PmMaintenance m = find(id);
        requireActive(m, "complete it");
        LocalDate today = LocalDate.now();
        LocalDate date = r != null && r.completedDate() != null ? r.completedDate() : today;
        if (date.isAfter(today)) throw ApiException.badRequest("Record the job as done on the day it was finished: today or earlier.");
        if (date.isBefore(m.getReportedDate())) throw ApiException.badRequest("The job cannot be finished before it was reported (" + m.getReportedDate() + ").");
        if (r != null && r.actualCost() != null) m.setActualCost(cost(r.actualCost(), "The cost"));
        if (r != null && r.costBearer() != null) m.setCostBearer(r.costBearer());
        if (r != null && r.resolution() != null) m.setResolution(PmUtil.clean(r.resolution(), 2000, "What was done"));
        if (m.getCostBearer() == CostBearer.TENANT && m.getActualCost() != null && m.getTenantId() == null) {
            throw ApiException.badRequest("The tenant is to pay, but no tenant is chosen. Choose the tenant first.");
        }
        m.setStatus(MaintenanceStatus.COMPLETED);
        m.setCompletedDate(date);
        if (m.getStartedDate() == null) m.setStartedDate(date);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_COMPLETED", "Completed maintenance request " + m.getCode() + (m.getActualCost() == null ? "" : " at a cost of " + m.getActualCost()), "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    @Transactional
    public MaintenanceDto cancel(Actor a, Long id, CancelMaintenanceRequest r) {
        access.on();
        PmMaintenance m = find(id);
        requireActive(m, "cancel it");
        m.setResolution(PmUtil.required(r == null ? null : r.reason(), 2000, "The reason"));
        m.setStatus(MaintenanceStatus.CANCELLED);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_CANCELLED", "Cancelled maintenance request " + m.getCode() + ": " + m.getResolution(), "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    @Transactional
    public MaintenanceDto reopen(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        if (active(m)) throw ApiException.conflict("This request is already open.");
        if (live(invoice(m.getInvoiceId()))) throw ApiException.conflict("An invoice (" + m.getInvoiceNumber() + ") was sent to the tenant for this job. Void it in Invoices before reopening.");
        m.setStatus(MaintenanceStatus.OPEN);
        m.setCompletedDate(null);
        m.setInvoiceId(null);
        m.setInvoiceNumber(null);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_REOPENED", "Reopened maintenance request " + m.getCode(), "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    // ------------------------------------------------------------------ work orders

    private static WorkOrderPriority woPriority(MaintenancePriority p) {
        return switch (p) {
            case LOW -> WorkOrderPriority.LOW;
            case NORMAL -> WorkOrderPriority.NORMAL;
            case HIGH -> WorkOrderPriority.HIGH;
            case URGENT -> WorkOrderPriority.URGENT;
        };
    }

    /** Raises an internal job in Work Orders for the request, so the job can be assigned, costed and tracked there. */
    @Transactional
    public MaintenanceDto raiseWorkOrder(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        requireActive(m, "raise a work order");
        if (m.getWorkOrderId() != null) throw ApiException.conflict("A work order (" + m.getWorkOrderNumber() + ") was already raised for this request.");
        if (!moduleService.isEnabled(ModuleKey.WORK_ORDERS)) throw ApiException.conflict("The Work Orders module is not activated (Settings > Modules).");
        Snapshot s = views.snapshot();
        MaintenanceRow row = row(s, m);
        StringBuilder d = new StringBuilder("Maintenance request ").append(m.getCode()).append(" · ").append(row.propertyName()).append(", unit ").append(row.unitNo());
        if (row.tenantName() != null) d.append("\nTenant: ").append(row.tenantName());
        if (m.getContractorName() != null) d.append("\nContractor: ").append(m.getContractorName()).append(m.getContractorPhone() == null ? "" : " " + m.getContractorPhone());
        if (m.getDescription() != null) d.append("\n\n").append(m.getDescription());
        WorkOrderRequest req = new WorkOrderRequest(WorkOrderType.INTERNAL, woPriority(m.getPriority()), (m.getCode() + " · " + row.propertyName() + " " + row.unitNo() + ": " + m.getTitle()),
                d.toString(), null, null, null, null, null, null, null, m.getReportedDate(), m.getScheduledDate(), m.getDueDate(), null);
        WorkOrderDetail wo = workOrderService.create(new WorkOrderService.Actor(a.user(), true, true), req);
        m.setWorkOrderId(wo.id());
        m.setWorkOrderNumber(wo.number());
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_WORK_ORDER", "Raised work order " + wo.number() + " for maintenance request " + m.getCode(), "PmMaintenance", id);
        return dto(a, s, m);
    }

    /** Forgets the link to the work order (the work order itself is left as it is). */
    @Transactional
    public MaintenanceDto unlinkWorkOrder(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        if (m.getWorkOrderId() == null) throw ApiException.conflict("No work order is linked to this request.");
        m.setWorkOrderId(null);
        m.setWorkOrderNumber(null);
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_WORK_ORDER_UNLINKED", "Unlinked the work order from maintenance request " + m.getCode(), "PmMaintenance", id);
        return dto(a, views.snapshot(), m);
    }

    // ------------------------------------------------------------------ charging the tenant

    @Transactional
    public Product maintenanceProduct() {
        PmSettings st = settings.entity();
        if (st.getMaintenanceProductId() != null) {
            try {
                return catalogService.getProductOrThrow(st.getMaintenanceProductId());
            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.NOT_FOUND) throw e;
            }
        }
        ProductRequest req = new ProductRequest();
        req.setName("Maintenance recharge");
        req.setDescription("Repairs charged to a tenant");
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(0);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        st.setMaintenanceProductId(created.getId());
        settings.save(st);
        return created;
    }

    /** Invoices a finished, tenant-paid job to the tenant (as a customer), due in 14 days. */
    @Transactional
    public MaintenanceDto invoiceTenant(Actor a, Long id) {
        access.on();
        PmMaintenance m = find(id);
        InvoiceDto existing = invoice(m.getInvoiceId());
        if (live(existing)) throw ApiException.conflict("This job was already invoiced (" + m.getInvoiceNumber() + ").");
        if (m.getStatus() != MaintenanceStatus.COMPLETED) throw ApiException.conflict("Complete the job first.");
        if (m.getCostBearer() != CostBearer.TENANT) throw ApiException.conflict("This job is not charged to the tenant.");
        if (m.getActualCost() == null) throw ApiException.conflict("Enter what the job cost first.");
        if (m.getTenantId() == null) throw ApiException.conflict("No tenant is chosen for this job.");
        Snapshot s = views.snapshot();
        MaintenanceRow row = row(s, m);
        PmTenant t = tenantService.entity(m.getTenantId());
        Customer cust = tenantService.ensureCustomer(t);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(cust.getId());
        req.setDueDate(LocalDate.now().plusDays(14));
        req.setNotes("Maintenance request " + m.getCode() + ": " + m.getTitle());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(maintenanceProduct().getId());
        line.setQuantity(1);
        line.setUnitPrice(m.getActualCost());
        line.setDescription("Repair: " + m.getTitle() + " (" + row.propertyName() + ", unit " + row.unitNo() + ")");
        req.setLines(List.of(line));
        InvoiceDto inv = invoiceService.issue(invoiceService.createDraft(req).id());
        m.setInvoiceId(inv.id());
        m.setInvoiceNumber(inv.invoiceNumber());
        repository.save(m);
        auditLogService.record("PM_MAINTENANCE_INVOICED", "Invoiced " + t.getName() + " " + m.getActualCost() + " for maintenance request " + m.getCode() + " (" + inv.invoiceNumber() + ")", "PmMaintenance", id);
        return dto(a, s, m);
    }

    // ------------------------------------------------------------------ summaries

    /** For the overview: how many requests are open, urgent or late, and the ones to look at first. */
    @Transactional(readOnly = true)
    public MaintenanceSummary summary(Snapshot s) {
        List<PmMaintenance> open = repository.findAll().stream().filter(PmMaintenanceService::active).toList();
        long urgent = open.stream().filter(m -> m.getPriority() == MaintenancePriority.URGENT || m.getPriority() == MaintenancePriority.HIGH).count();
        long late = open.stream().filter(m -> overdue(m, s.today)).count();
        List<MaintenanceRow> attention = open.stream().filter(m -> m.getPriority() == MaintenancePriority.URGENT || m.getPriority() == MaintenancePriority.HIGH || overdue(m, s.today))
                .map(m -> row(s, m)).sorted(Comparator.comparing((MaintenanceRow r) -> rank(r.priority())).thenComparing(MaintenanceRow::reportedDate)).limit(8).toList();
        return new MaintenanceSummary(open.size(), urgent, late, attention);
    }

    /** Cost of finished jobs per property, by who pays. {@code costOnly} leaves out properties with no cost. */
    @Transactional(readOnly = true)
    public List<MaintenanceCostRow> costByProperty(boolean costOnly) {
        access.on();
        Snapshot s = views.snapshot();
        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
        for (PmMaintenance m : repository.findAll()) {
            if (m.getStatus() == MaintenanceStatus.CANCELLED) continue;
            PmUnit u = s.units.get(m.getUnitId());
            if (u == null) continue;
            long[] c = counts.computeIfAbsent(u.getPropertyId(), k -> new long[2]);
            BigDecimal[] v = sums.computeIfAbsent(u.getPropertyId(), k -> new BigDecimal[]{ZERO, ZERO, ZERO});
            c[0]++;
            if (m.getStatus() == MaintenanceStatus.COMPLETED) {
                c[1]++;
                if (m.getActualCost() != null) {
                    int i = m.getCostBearer() == CostBearer.OWNER ? 0 : m.getCostBearer() == CostBearer.TENANT ? 1 : 2;
                    v[i] = v[i].add(m.getActualCost());
                }
            }
        }
        return counts.entrySet().stream().map(e -> {
            PmProperty p = s.properties.get(e.getKey());
            PmOwner o = p == null ? null : s.owners.get(p.getOwnerId());
            BigDecimal[] v = sums.get(e.getKey());
            return new MaintenanceCostRow(e.getKey(), p == null ? null : p.getName(), o == null ? null : o.getName(), e.getValue()[0], e.getValue()[1], v[0], v[1], v[2], v[0].add(v[1]).add(v[2]));
        }).filter(r -> !costOnly || r.total().signum() > 0).sorted(Comparator.comparing(MaintenanceCostRow::total).reversed().thenComparing(r -> r.propertyName() == null ? "" : r.propertyName())).toList();
    }
}
