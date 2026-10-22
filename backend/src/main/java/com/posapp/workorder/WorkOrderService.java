package com.posapp.workorder;

import com.posapp.accounting.AccountingService;
import com.posapp.accounting.ExpenseService;
import com.posapp.accounting.PayeeType;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.accounting.dto.ExpenseRequest;
import com.posapp.assets.FixedAsset;
import com.posapp.assets.FixedAssetRepository;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.manufacturing.ManufacturingOrder;
import com.posapp.manufacturing.ManufacturingOrderRepository;
import com.posapp.manufacturing.ManufacturingOrderService;
import com.posapp.manufacturing.ManufacturingOrderStatus;
import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.timetracking.ProjectStatus;
import com.posapp.timetracking.TimeProject;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.timetracking.dto.TimeDtos.BillingPreviewDto;
import com.posapp.timetracking.dto.TimeRequests.BillingRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import com.posapp.workorder.dto.WorkOrderDtos.*;
import com.posapp.workorder.dto.WorkOrderRequests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Work orders: customer jobs and internal maintenance or repair. Hours come from Time Tracking (each job has a project there),
 * parts come out of stock and their cost is posted when the job is completed (an invoice does not move stock), and a customer job is
 * billed by building a draft invoice from the fixed price or hours, the parts and any re-charged costs.
 */
@Service
public class WorkOrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String SOURCE = "WORK_ORDER";
    private static final Set<WorkOrderStatus> ACTIVE = Set.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    /** Who is asking: {@code viewer} sees every job, {@code manager} changes any job; anyone else only works the jobs assigned to them. */
    public record Actor(AppUser user, boolean viewer, boolean manager) {
    }

    private final WorkOrderRepository repository;
    private final WorkOrderLineRepository lineRepository;
    private final WorkOrderChecklistItemRepository checklistRepository;
    private final WorkOrderCostRepository costRepository;
    private final ServicePackageRepository packageRepository;
    private final ServicePackageItemRepository packageItemRepository;
    private final ServicePackagePartRepository packagePartRepository;
    private final TimeTrackingService timeService;
    private final CatalogService catalogService;
    private final AccountingService accountingService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final FixedAssetRepository assetRepository;
    private final UserRepository userRepository;
    private final BranchService branchService;
    private final ModuleService moduleService;
    private final StoreSettingsService storeSettingsService;
    private final ExpenseService expenseService;
    private final ManufacturingOrderService manufacturingOrderService;
    private final ManufacturingOrderRepository manufacturingOrderRepository;
    private final AuditLogService auditLogService;

    public WorkOrderService(WorkOrderRepository repository, WorkOrderLineRepository lineRepository, WorkOrderChecklistItemRepository checklistRepository,
                            WorkOrderCostRepository costRepository, ServicePackageRepository packageRepository,
                            ServicePackageItemRepository packageItemRepository, ServicePackagePartRepository packagePartRepository,
                            TimeTrackingService timeService, CatalogService catalogService, AccountingService accountingService,
                            InvoiceService invoiceService, InvoiceRepository invoiceRepository, CustomerRepository customerRepository,
                            FixedAssetRepository assetRepository, UserRepository userRepository, BranchService branchService, ModuleService moduleService,
                            StoreSettingsService storeSettingsService, ExpenseService expenseService, ManufacturingOrderService manufacturingOrderService,
                            ManufacturingOrderRepository manufacturingOrderRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.checklistRepository = checklistRepository;
        this.costRepository = costRepository;
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.packagePartRepository = packagePartRepository;
        this.timeService = timeService;
        this.catalogService = catalogService;
        this.accountingService = accountingService;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.branchService = branchService;
        this.moduleService = moduleService;
        this.storeSettingsService = storeSettingsService;
        this.expenseService = expenseService;
        this.manufacturingOrderService = manufacturingOrderService;
        this.manufacturingOrderRepository = manufacturingOrderRepository;
        this.auditLogService = auditLogService;
    }

    private void on() {
        moduleService.requireEnabled(ModuleKey.WORK_ORDERS);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String clean(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static boolean isOverdue(WorkOrder w) {
        return w.getDueDate() != null && w.getDueDate().isBefore(LocalDate.now()) && ACTIVE.contains(w.getStatus());
    }

    private static boolean readyToInvoice(WorkOrder w) {
        return w.getType() == WorkOrderType.CUSTOMER && w.getStatus() == WorkOrderStatus.COMPLETED && w.getInvoiceId() == null && w.getCustomerId() != null;
    }

    private WorkOrder find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Work order not found: " + id));
    }

    private static boolean canWork(Actor a, WorkOrder w) {
        return a.manager() || (w.getAssignedToUserId() != null && w.getAssignedToUserId().equals(a.user().getId()));
    }

    private void requireWorker(Actor a, WorkOrder w) {
        if (!canWork(a, w)) throw ApiException.forbidden("Only the person this job is assigned to, or a manager, can do that.");
    }

    private void requireManager(Actor a) {
        if (!a.manager()) throw ApiException.forbidden("Only a manager can do that.");
    }

    private static void requireEditable(WorkOrder w) {
        if (w.getStatus() == WorkOrderStatus.COMPLETED || w.getStatus() == WorkOrderStatus.CANCELLED) {
            throw ApiException.conflict("This work order is " + w.getStatus().name().toLowerCase() + ". Reopen it to change it.");
        }
    }

    // ------------------------------------------------------------------ lists

    @Transactional
    public List<WorkOrderSummary> list(WorkOrderStatus status, WorkOrderType type, Long assetId, Long assigneeId, Long customerId, boolean overdueOnly, String q) {
        on();
        String needle = notBlank(q) ? q.trim().toLowerCase() : null;
        List<WorkOrder> list = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(w -> status == null || w.getStatus() == status)
                .filter(w -> type == null || w.getType() == type)
                .filter(w -> assetId == null || assetId.equals(w.getAssetId()))
                .filter(w -> assigneeId == null || assigneeId.equals(w.getAssignedToUserId()))
                .filter(w -> customerId == null || customerId.equals(w.getCustomerId()))
                .filter(w -> !overdueOnly || isOverdue(w))
                .filter(w -> needle == null || (w.getNumber() + " " + w.getTitle() + " " + nz(w.getCustomerName()) + " " + nz(w.getAssetName()) + " " + nz(w.getItemDescription()))
                        .toLowerCase().contains(needle))
                .toList();
        return summaries(list);
    }

    @Transactional
    public List<WorkOrderSummary> mine(AppUser user) {
        on();
        return summaries(repository.findByAssignedToUserIdOrderByDueDateAscCreatedAtDesc(user.getId()).stream()
                .filter(w -> w.getStatus() != WorkOrderStatus.CANCELLED).toList());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private List<WorkOrderSummary> summaries(List<WorkOrder> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<WorkOrderChecklistItem>> checks = checklistRepository.findByWorkOrderIdIn(list.stream().map(WorkOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkOrderChecklistItem::getWorkOrderId));
        List<WorkOrderSummary> out = new ArrayList<>();
        for (WorkOrder w : list) {
            List<WorkOrderChecklistItem> items = checks.getOrDefault(w.getId(), List.of());
            BigDecimal hours = w.getTimeProjectId() == null ? ZERO : timeService.hoursOf(w.getTimeProjectId());
            out.add(new WorkOrderSummary(w.getId(), w.getNumber(), w.getType(), w.getStatus(), w.getPriority(), w.getTitle(), w.getCustomerId(),
                    w.getCustomerName(), w.getAssetId(), w.getAssetName(), w.getPackageName(), w.getAssignedToUserId(), w.getAssignedToName(),
                    w.getScheduledDate(), w.getDueDate(), isOverdue(w), (int) items.stream().filter(WorkOrderChecklistItem::isDone).count(), items.size(),
                    hours, w.getInvoiceId(), w.getInvoiceNumber(), readyToInvoice(w), w.getCreatedAt()));
        }
        return out;
    }

    @Transactional
    public SummaryDto summary(AppUser user) {
        on();
        List<WorkOrder> all = repository.findAll();
        return new SummaryDto(all.stream().filter(w -> w.getStatus() == WorkOrderStatus.OPEN).count(),
                all.stream().filter(w -> w.getStatus() == WorkOrderStatus.IN_PROGRESS).count(),
                all.stream().filter(w -> w.getStatus() == WorkOrderStatus.ON_HOLD).count(),
                all.stream().filter(WorkOrderService::isOverdue).count(),
                all.stream().filter(WorkOrderService::readyToInvoice).count(),
                all.stream().filter(w -> ACTIVE.contains(w.getStatus()) && user.getId().equals(w.getAssignedToUserId())).count());
    }

    // ------------------------------------------------------------------ detail

    @Transactional
    public WorkOrderDetail detail(Actor actor, Long id) {
        on();
        WorkOrder w = find(id);
        if (!actor.viewer() && !canWork(actor, w)) throw ApiException.forbidden("This work order is not assigned to you.");
        return toDetail(actor, w);
    }

    private WorkOrderDetail toDetail(Actor actor, WorkOrder w) {
        List<WorkOrderLine> lines = lineRepository.findByWorkOrderIdOrderByIdAsc(w.getId());
        List<WorkOrderChecklistItem> checklist = checklistRepository.findByWorkOrderIdOrderBySeqAsc(w.getId());
        List<WorkOrderCost> costs = costRepository.findByWorkOrderIdOrderByCostDateAscIdAsc(w.getId());
        BigDecimal hours = w.getTimeProjectId() == null ? ZERO : timeService.hoursOf(w.getTimeProjectId());
        BigDecimal billableHours = ZERO;
        BigDecimal labourBillable = ZERO;
        if (w.getTimeProjectId() != null && w.getType() == WorkOrderType.CUSTOMER && w.getInvoiceId() == null) {
            BillingPreviewDto p = timeService.billingPreview(new BillingRequest(w.getTimeProjectId(), null, null, null, null, null, null));
            billableHours = p.totalHours();
            labourBillable = p.totalAmount();
        }
        boolean fixed = w.getFixedPrice().signum() > 0;
        BigDecimal partsCost = lines.stream().map(l -> l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity()))).reduce(ZERO, BigDecimal::add);
        BigDecimal externalCost = costs.stream().map(WorkOrderCost::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal partsCharged = lines.stream().filter(l -> !(l.isIncluded() && fixed)).map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal costsCharged = costs.stream().map(WorkOrderCost::getChargeAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal expected = w.getType() == WorkOrderType.INTERNAL ? ZERO
                : (fixed ? w.getFixedPrice() : labourBillable).add(partsCharged).add(costsCharged);
        BigDecimal invoiceTotal = w.getInvoiceId() == null ? null
                : invoiceRepository.findById(w.getInvoiceId()).map(Invoice::getTotalAmount).orElse(null);
        String moStatus = null;
        if (w.getManufacturingOrderId() != null) {
            moStatus = manufacturingOrderRepository.findById(w.getManufacturingOrderId()).map(m -> m.getStatus().name()).orElse(null);
        }
        Money money = new Money(money(partsCost), money(externalCost), money(partsCost.add(externalCost)), money(partsCharged), money(costsCharged),
                money(labourBillable), money(expected), invoiceTotal == null ? null : money(invoiceTotal));
        return new WorkOrderDetail(w.getId(), w.getNumber(), w.getType(), w.getStatus(), w.getPriority(), w.getTitle(), w.getDescription(),
                w.getItemDescription(), w.getCustomerId(), w.getCustomerName(), w.getAssetId(), w.getAssetName(), w.getPackageId(), w.getPackageName(),
                w.getFixedPrice(), w.getLabourRate(), w.getAssignedToUserId(), w.getAssignedToName(), w.getRequestedDate(), w.getScheduledDate(),
                w.getDueDate(), w.getStartedAt(), w.getCompletedAt(), w.getTimeProjectId(), hours, billableHours, w.getInvoiceId(), w.getInvoiceNumber(),
                w.getManufacturingOrderId(), w.getManufacturingOrderNumber(), moStatus, w.getBranchId(), w.isPartsPosted(), w.getCompletionNote(),
                w.getCreatedByName(), w.getCreatedAt(), isOverdue(w), canWork(actor, w), actor.manager(), readyToInvoice(w),
                lines.stream().map(l -> new LineDto(l.getId(), l.getProductId(), l.getProductName(), l.getQuantity(), l.getUnitPrice(), l.getUnitCost(),
                        l.isIncluded(), l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))).toList(),
                checklist.stream().map(c -> new ChecklistDto(c.getId(), c.getSeq(), c.getText(), c.isDone(), c.getDoneAt(), c.getDoneByName())).toList(),
                costs.stream().map(c -> new CostDto(c.getId(), c.getCostDate(), c.getDescription(), c.getSupplierName(), c.getAmount(), c.getChargeAmount(),
                        c.getExpenseId())).toList(),
                money);
    }

    // ------------------------------------------------------------------ create / edit

    @Transactional
    public WorkOrderDetail create(Actor actor, WorkOrderRequest r) {
        on();
        requireManager(actor);
        if (!notBlank(r.title())) throw ApiException.badRequest("Give the work order a title.");
        WorkOrder w = new WorkOrder();
        w.setNumber("WO-TMP-" + System.nanoTime());
        w.setCreatedByName(TimeTrackingService.actorName(actor.user()));
        w.setRequestedDate(r.requestedDate() != null ? r.requestedDate() : LocalDate.now());
        apply(w, r);
        ServicePackage pkg = null;
        if (r.packageId() != null) {
            pkg = packageRepository.findById(r.packageId()).orElseThrow(() -> ApiException.notFound("Service package not found."));
            w.setPackageId(pkg.getId());
            w.setPackageName(pkg.getName());
            if (r.fixedPrice() == null) w.setFixedPrice(pkg.getFixedPrice());
        }
        WorkOrder saved = repository.save(w);
        saved.setNumber(branchService.prefixedNumber(saved.getBranchId(), String.format("WO-%05d", saved.getId())));
        if (pkg != null) copyPackage(saved, pkg);
        syncProject(saved);
        repository.save(saved);
        auditLogService.record("WORK_ORDER_CREATED", "Created work order " + saved.getNumber() + " — " + saved.getTitle(), "WorkOrder", saved.getId());
        return toDetail(actor, saved);
    }

    @Transactional
    public WorkOrderDetail update(Actor actor, Long id, WorkOrderRequest r) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        requireEditable(w);
        if (!notBlank(r.title())) throw ApiException.badRequest("Give the work order a title.");
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is on an invoice.");
        apply(w, r);
        repository.save(w);
        syncProject(w);
        auditLogService.record("WORK_ORDER_UPDATED", "Updated work order " + w.getNumber(), "WorkOrder", w.getId());
        return toDetail(actor, w);
    }

    private void apply(WorkOrder w, WorkOrderRequest r) {
        w.setType(r.type() != null ? r.type() : WorkOrderType.CUSTOMER);
        w.setPriority(r.priority() != null ? r.priority() : WorkOrderPriority.NORMAL);
        w.setTitle(r.title().trim());
        w.setDescription(clean(r.description()));
        w.setItemDescription(clean(r.itemDescription()));
        if (w.getType() == WorkOrderType.CUSTOMER) {
            if (r.customerId() == null) throw ApiException.badRequest("Choose the customer for a customer job.");
            Customer c = customerRepository.findById(r.customerId()).orElseThrow(() -> ApiException.notFound("Customer not found."));
            w.setCustomerId(c.getId());
            w.setCustomerName(c.getName());
            w.setFixedPrice(r.fixedPrice() != null ? money(r.fixedPrice()) : w.getFixedPrice());
        } else {
            w.setCustomerId(null);
            w.setCustomerName(null);
            w.setFixedPrice(ZERO);
        }
        if (r.assetId() != null) {
            FixedAsset a = assetRepository.findById(r.assetId()).orElseThrow(() -> ApiException.notFound("Asset not found."));
            w.setAssetId(a.getId());
            w.setAssetName(a.getCode() + " — " + a.getName());
        } else {
            w.setAssetId(null);
            w.setAssetName(null);
        }
        if (r.fixedPrice() != null && r.fixedPrice().signum() < 0) throw ApiException.badRequest("The fixed price cannot be negative.");
        w.setLabourRate(r.labourRate() != null && r.labourRate().signum() > 0 ? money(r.labourRate()) : null);
        if (r.assignedToUserId() != null) {
            AppUser u = userRepository.findById(r.assignedToUserId()).orElseThrow(() -> ApiException.notFound("User not found."));
            w.setAssignedToUserId(u.getId());
            w.setAssignedToName(TimeTrackingService.actorName(u));
        } else {
            w.setAssignedToUserId(null);
            w.setAssignedToName(null);
        }
        if (r.requestedDate() != null) w.setRequestedDate(r.requestedDate());
        w.setScheduledDate(r.scheduledDate());
        w.setDueDate(r.dueDate());
        if (r.branchId() != null || w.getId() == null) w.setBranchId(r.branchId());
    }

    private void copyPackage(WorkOrder w, ServicePackage pkg) {
        int seq = 0;
        for (ServicePackageItem item : packageItemRepository.findByPackageIdOrderBySeqAsc(pkg.getId())) {
            WorkOrderChecklistItem c = new WorkOrderChecklistItem();
            c.setWorkOrderId(w.getId());
            c.setSeq(seq++);
            c.setText(item.getText());
            checklistRepository.save(c);
        }
        for (ServicePackagePart part : packagePartRepository.findByPackageIdOrderByIdAsc(pkg.getId())) {
            Product p = catalogService.getProductOrThrow(part.getProductId());
            WorkOrderLine l = new WorkOrderLine();
            l.setWorkOrderId(w.getId());
            l.setProductId(p.getId());
            l.setProductName(p.getName());
            l.setQuantity(part.getQuantity());
            l.setUnitPrice(p.getUnitPrice());
            l.setUnitCost(p.getCostPrice());
            l.setIncluded(part.isIncluded());
            lineRepository.save(l);
        }
    }

    /** Keeps the Time Tracking project behind the job in step: customer jobs with hourly billing are billable, everything else is not. */
    private void syncProject(WorkOrder w) {
        TimeProject p = timeService.ensureProjectForSource(SOURCE, w.getId(), w.getNumber() + " — " + w.getTitle(), w.getCustomerId(), w.getBranchId());
        w.setTimeProjectId(p.getId());
        ProjectStatus status = switch (w.getStatus()) {
            case COMPLETED -> ProjectStatus.COMPLETED;
            case CANCELLED -> ProjectStatus.ARCHIVED;
            default -> ProjectStatus.ACTIVE;
        };
        boolean billable = w.getType() == WorkOrderType.CUSTOMER && w.getFixedPrice().signum() <= 0;
        timeService.configureSourceProject(SOURCE, w.getId(), w.getNumber() + " — " + w.getTitle(), w.getCustomerId(), billable, w.getLabourRate(), status);
    }

    @Transactional
    public void delete(Actor actor, Long id) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        if ((w.getStatus() != WorkOrderStatus.OPEN && w.getStatus() != WorkOrderStatus.CANCELLED) || w.getInvoiceId() != null || w.isPartsPosted()) {
            throw ApiException.conflict("Only an open or cancelled work order that has not been invoiced can be deleted. Cancel it instead.");
        }
        if (w.getTimeProjectId() != null && timeService.hoursOf(w.getTimeProjectId()).signum() > 0) {
            throw ApiException.conflict("Time has been logged on this work order. Cancel it instead of deleting it.");
        }
        lineRepository.deleteAll(lineRepository.findByWorkOrderIdOrderByIdAsc(id));
        checklistRepository.deleteAll(checklistRepository.findByWorkOrderIdOrderBySeqAsc(id));
        costRepository.deleteAll(costRepository.findByWorkOrderIdOrderByCostDateAscIdAsc(id));
        repository.delete(w);
        if (w.getTimeProjectId() != null) timeService.deleteProject(w.getTimeProjectId());
        auditLogService.record("WORK_ORDER_DELETED", "Deleted work order " + w.getNumber(), "WorkOrder", id);
    }

    // ------------------------------------------------------------------ parts, checklist, costs

    @Transactional
    public WorkOrderDetail saveLines(Actor actor, Long id, LinesRequest req) {
        on();
        WorkOrder w = find(id);
        requireWorker(actor, w);
        requireEditable(w);
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is on an invoice.");
        List<WorkOrderLine> existing = lineRepository.findByWorkOrderIdOrderByIdAsc(id);
        Map<Long, WorkOrderLine> byProduct = new HashMap<>();
        existing.forEach(l -> byProduct.putIfAbsent(l.getProductId(), l));
        List<WorkOrderLine> keep = new ArrayList<>();
        for (LineRequest lr : req.lines() == null ? List.<LineRequest>of() : req.lines()) {
            if (lr.productId() == null) throw ApiException.badRequest("Choose a product for every part.");
            int qty = lr.quantity() == null ? 1 : lr.quantity();
            if (qty <= 0) throw ApiException.badRequest("The quantity of a part must be at least 1.");
            Product p = catalogService.getProductOrThrow(lr.productId());
            WorkOrderLine l = byProduct.remove(p.getId());
            if (l == null) {
                l = new WorkOrderLine();
                l.setWorkOrderId(id);
                l.setProductId(p.getId());
            }
            l.setProductName(p.getName());
            l.setQuantity(qty);
            l.setUnitPrice(lr.unitPrice() != null ? money(lr.unitPrice()) : p.getUnitPrice());
            l.setUnitCost(p.getCostPrice());
            l.setIncluded(Boolean.TRUE.equals(lr.included()));
            keep.add(lineRepository.save(l));
        }
        lineRepository.deleteAll(existing.stream().filter(l -> keep.stream().noneMatch(k -> k.getId().equals(l.getId()))).toList());
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail saveChecklist(Actor actor, Long id, ChecklistRequest req) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        requireEditable(w);
        List<WorkOrderChecklistItem> existing = checklistRepository.findByWorkOrderIdOrderBySeqAsc(id);
        Map<String, WorkOrderChecklistItem> byText = new LinkedHashMap<>();
        existing.forEach(c -> byText.putIfAbsent(c.getText(), c));
        List<WorkOrderChecklistItem> keep = new ArrayList<>();
        int seq = 0;
        for (String raw : req.items() == null ? List.<String>of() : req.items()) {
            if (!notBlank(raw)) continue;
            String text = raw.trim();
            WorkOrderChecklistItem c = byText.remove(text);
            if (c == null) {
                c = new WorkOrderChecklistItem();
                c.setWorkOrderId(id);
                c.setText(text.length() > 500 ? text.substring(0, 500) : text);
            }
            c.setSeq(seq++);
            keep.add(checklistRepository.save(c));
        }
        checklistRepository.deleteAll(byText.values());
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail toggleChecklist(Actor actor, Long id, Long itemId) {
        on();
        WorkOrder w = find(id);
        requireWorker(actor, w);
        requireEditable(w);
        WorkOrderChecklistItem c = checklistRepository.findById(itemId).filter(i -> i.getWorkOrderId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Checklist step not found."));
        c.setDone(!c.isDone());
        c.setDoneAt(c.isDone() ? Instant.now() : null);
        c.setDoneByName(c.isDone() ? TimeTrackingService.actorName(actor.user()) : null);
        checklistRepository.save(c);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail addCost(Actor actor, Long id, CostRequest r) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        if (w.getStatus() == WorkOrderStatus.CANCELLED) throw ApiException.conflict("This work order is cancelled.");
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is on an invoice.");
        if (!notBlank(r.description())) throw ApiException.badRequest("Describe the cost.");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.badRequest("Enter the amount spent.");
        if (r.chargeAmount() != null && r.chargeAmount().signum() < 0) throw ApiException.badRequest("The amount charged cannot be negative.");
        WorkOrderCost c = new WorkOrderCost();
        c.setWorkOrderId(id);
        c.setCostDate(r.costDate() != null ? r.costDate() : LocalDate.now());
        c.setDescription(r.description().trim());
        c.setSupplierName(clean(r.supplierName()));
        c.setAmount(money(r.amount()));
        c.setChargeAmount(w.getType() == WorkOrderType.CUSTOMER ? money(r.chargeAmount()) : ZERO);
        if (r.expenseAccountId() != null) {
            if (!moduleService.isEnabled(ModuleKey.EXPENSES)) throw ApiException.badRequest("Switch on the Expenses module to record this as an expense.");
            ExpenseRequest er = new ExpenseRequest();
            er.setExpenseDate(c.getCostDate());
            er.setAccountId(r.expenseAccountId());
            er.setPayeeType(PayeeType.VENDOR);
            er.setPayeeName(c.getSupplierName() != null ? c.getSupplierName() : "Work order " + w.getNumber());
            er.setAmount(c.getAmount());
            er.setMemo(w.getNumber() + ": " + c.getDescription());
            er.setBranchId(w.getBranchId());
            ExpenseDto e = expenseService.create(er);
            c.setExpenseId(e.id());
        }
        costRepository.save(c);
        auditLogService.record("WORK_ORDER_COST_ADDED", "Added a cost of " + c.getAmount() + " to " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail deleteCost(Actor actor, Long id, Long costId) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is on an invoice.");
        WorkOrderCost c = costRepository.findById(costId).filter(x -> x.getWorkOrderId().equals(id)).orElseThrow(() -> ApiException.notFound("Cost not found."));
        if (c.getExpenseId() != null) {
            throw ApiException.conflict("This cost was recorded as an expense. Void the expense in Expenses first, then it can be removed here.");
        }
        costRepository.delete(c);
        return toDetail(actor, w);
    }

    // ------------------------------------------------------------------ status

    @Transactional
    public WorkOrderDetail start(Actor actor, Long id) {
        on();
        WorkOrder w = find(id);
        requireWorker(actor, w);
        if (w.getStatus() != WorkOrderStatus.OPEN && w.getStatus() != WorkOrderStatus.ON_HOLD) {
            throw ApiException.conflict("Only an open or held work order can be started.");
        }
        w.setStatus(WorkOrderStatus.IN_PROGRESS);
        if (w.getStartedAt() == null) w.setStartedAt(Instant.now());
        repository.save(w);
        auditLogService.record("WORK_ORDER_STARTED", "Started work order " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail hold(Actor actor, Long id) {
        on();
        WorkOrder w = find(id);
        requireWorker(actor, w);
        if (w.getStatus() != WorkOrderStatus.OPEN && w.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw ApiException.conflict("Only an open or in-progress work order can be put on hold.");
        }
        w.setStatus(WorkOrderStatus.ON_HOLD);
        repository.save(w);
        auditLogService.record("WORK_ORDER_HELD", "Put work order " + w.getNumber() + " on hold", "WorkOrder", id);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail complete(Actor actor, Long id, CompleteRequest r) {
        on();
        WorkOrder w = find(id);
        requireWorker(actor, w);
        requireEditable(w);
        boolean force = r != null && Boolean.TRUE.equals(r.force());
        List<WorkOrderChecklistItem> checklist = checklistRepository.findByWorkOrderIdOrderBySeqAsc(id);
        long open = checklist.stream().filter(c -> !c.isDone()).count();
        if (open > 0 && !(force && actor.manager())) {
            throw ApiException.badRequest(open + " checklist step" + (open == 1 ? " is" : "s are") + " not done yet."
                    + (actor.manager() ? " Tick them, or complete anyway." : ""));
        }
        if (w.getManufacturingOrderId() != null && moduleService.isEnabled(ModuleKey.MANUFACTURING)) {
            ManufacturingOrder mo = manufacturingOrderRepository.findById(w.getManufacturingOrderId()).orElse(null);
            if (mo != null && mo.getStatus() != ManufacturingOrderStatus.PRODUCED && mo.getStatus() != ManufacturingOrderStatus.CANCELLED) {
                throw ApiException.conflict("The linked manufacturing order " + mo.getOrderNumber() + " is " + mo.getStatus() + ". Finish it first, or unlink it.");
            }
        }
        postParts(w);
        w.setStatus(WorkOrderStatus.COMPLETED);
        w.setCompletedAt(Instant.now());
        if (w.getStartedAt() == null) w.setStartedAt(w.getCompletedAt());
        if (r != null && notBlank(r.note())) w.setCompletionNote(r.note().trim());
        repository.save(w);
        syncProject(w);
        auditLogService.record("WORK_ORDER_COMPLETED", "Completed work order " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    /** Takes the parts out of stock and posts their cost (cost of goods for a customer job, repairs for an internal one). */
    private void postParts(WorkOrder w) {
        List<WorkOrderLine> lines = lineRepository.findByWorkOrderIdOrderByIdAsc(w.getId());
        boolean allowNegative = storeSettingsService.getSettings().allowNegativeStock();
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Integer> needed = new LinkedHashMap<>();
        for (WorkOrderLine l : lines) {
            Product p = catalogService.getProductOrThrow(l.getProductId());
            products.put(p.getId(), p);
            if (p.isTrackStock()) needed.merge(p.getId(), l.getQuantity(), Integer::sum);
        }
        if (!allowNegative) {
            for (Map.Entry<Long, Integer> e : needed.entrySet()) {
                Product p = products.get(e.getKey());
                if (p.getStockQuantity() < e.getValue()) {
                    throw ApiException.badRequest("Not enough stock of " + p.getName() + ": " + e.getValue() + " needed, " + p.getStockQuantity() + " in stock.");
                }
            }
        }
        BigDecimal cost = ZERO;
        for (WorkOrderLine l : lines) {
            Product p = products.get(l.getProductId());
            if (!p.isTrackStock()) {
                l.setUnitCost(ZERO);
            } else {
                l.setUnitCost(p.getCostPrice() == null ? ZERO : p.getCostPrice());
                catalogService.adjustStock(p, -l.getQuantity(), null);
                cost = cost.add(l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity())));
            }
            lineRepository.save(l);
        }
        cost = money(cost);
        w.setPartsPosted(true);
        w.setPartsCostPosted(cost);
        if (cost.signum() > 0) {
            accountingService.postWorkOrderParts(w.getId(), "Parts used on " + w.getNumber() + " — " + w.getTitle(), cost, w.getType() == WorkOrderType.CUSTOMER,
                    false, w.getBranchId());
        }
    }

    @Transactional
    public WorkOrderDetail reopen(Actor actor, Long id) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        if (w.getStatus() != WorkOrderStatus.COMPLETED && w.getStatus() != WorkOrderStatus.CANCELLED) {
            throw ApiException.conflict("Only a completed or cancelled work order can be reopened.");
        }
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is on invoice " + w.getInvoiceNumber() + ". Release the invoice first.");
        if (w.isPartsPosted()) {
            for (WorkOrderLine l : lineRepository.findByWorkOrderIdOrderByIdAsc(id)) {
                Product p = catalogService.getProductOrThrow(l.getProductId());
                if (p.isTrackStock()) catalogService.adjustStock(p, l.getQuantity(), null);
            }
            if (w.getPartsCostPosted().signum() > 0) {
                accountingService.postWorkOrderParts(w.getId(), "Reopened " + w.getNumber() + " — parts returned to stock", w.getPartsCostPosted(),
                        w.getType() == WorkOrderType.CUSTOMER, true, w.getBranchId());
            }
            w.setPartsPosted(false);
            w.setPartsCostPosted(ZERO);
        }
        w.setStatus(w.getStartedAt() != null ? WorkOrderStatus.IN_PROGRESS : WorkOrderStatus.OPEN);
        w.setCompletedAt(null);
        repository.save(w);
        syncProject(w);
        auditLogService.record("WORK_ORDER_REOPENED", "Reopened work order " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail cancel(Actor actor, Long id) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        requireEditable(w);
        w.setStatus(WorkOrderStatus.CANCELLED);
        repository.save(w);
        syncProject(w);
        auditLogService.record("WORK_ORDER_CANCELLED", "Cancelled work order " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    // ------------------------------------------------------------------ manufacturing

    @Transactional
    public WorkOrderDetail linkManufacturing(Actor actor, Long id, ManufacturingRequest r) {
        on();
        requireManager(actor);
        moduleService.requireEnabled(ModuleKey.MANUFACTURING);
        WorkOrder w = find(id);
        requireEditable(w);
        if (w.getManufacturingOrderId() != null) throw ApiException.conflict("A manufacturing order is already linked. Unlink it first.");
        if (r.bomId() == null) throw ApiException.badRequest("Choose the bill of materials to make.");
        CreateManufacturingOrderRequest req = new CreateManufacturingOrderRequest();
        req.setBomId(r.bomId());
        req.setQuantityPlanned(r.quantity() == null ? 1 : r.quantity());
        req.setScheduledDate(r.scheduledDate() != null ? r.scheduledDate() : w.getScheduledDate());
        req.setNote("For work order " + w.getNumber() + " — " + w.getTitle());
        ManufacturingOrderDto mo = manufacturingOrderService.create(req);
        w.setManufacturingOrderId(mo.id());
        w.setManufacturingOrderNumber(mo.orderNumber());
        repository.save(w);
        auditLogService.record("WORK_ORDER_MANUFACTURING_LINKED", w.getNumber() + " linked to manufacturing order " + mo.orderNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    @Transactional
    public WorkOrderDetail unlinkManufacturing(Actor actor, Long id) {
        on();
        requireManager(actor);
        WorkOrder w = find(id);
        requireEditable(w);
        w.setManufacturingOrderId(null);
        w.setManufacturingOrderNumber(null);
        repository.save(w);
        return toDetail(actor, w);
    }

    // ------------------------------------------------------------------ billing

    private record Built(List<InvoiceLinePreview> lines, List<Long> productIds, List<Long> entryIds, BigDecimal total, String warning) {
    }

    private Built build(WorkOrder w) {
        List<InvoiceLinePreview> out = new ArrayList<>();
        List<Long> pids = new ArrayList<>();
        boolean fixed = w.getFixedPrice().signum() > 0;
        if (fixed) {
            String d = w.getTitle() + (w.getPackageName() != null ? " (" + w.getPackageName() + ")" : "") + " — fixed price";
            out.add(new InvoiceLinePreview("FIXED", d, 1, w.getFixedPrice(), w.getFixedPrice()));
            pids.add(null);
        }
        for (WorkOrderLine l : lineRepository.findByWorkOrderIdOrderByIdAsc(w.getId())) {
            if (fixed && l.isIncluded()) continue;
            out.add(new InvoiceLinePreview("PART", l.getProductName(), l.getQuantity(), l.getUnitPrice(),
                    l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()))));
            pids.add(l.getProductId());
        }
        for (WorkOrderCost c : costRepository.findByWorkOrderIdOrderByCostDateAscIdAsc(w.getId())) {
            if (c.getChargeAmount().signum() > 0) {
                out.add(new InvoiceLinePreview("COST", c.getDescription() + (c.getSupplierName() != null ? " (" + c.getSupplierName() + ")" : ""), 1,
                        c.getChargeAmount(), c.getChargeAmount()));
                pids.add(null);
            }
        }
        List<Long> entryIds = new ArrayList<>();
        String warning = null;
        if (!fixed && w.getTimeProjectId() != null) {
            BillingPreviewDto time = timeService.billingPreview(new BillingRequest(w.getTimeProjectId(), null, null, null, null, null, null));
            if (time.lines().stream().anyMatch(l -> l.hourlyRate().signum() <= 0)) {
                warning = "Some hours have no hourly rate. Set a labour rate on the work order (or a default in the Time Tracking settings) and have the person resubmit the time.";
            }
            time.lines().forEach(l -> {
                out.add(new InvoiceLinePreview("LABOUR", l.description(), 1, l.amount(), l.amount()));
                pids.add(null);
            });
            entryIds.addAll(time.entryIds());
        }
        BigDecimal total = out.stream().map(InvoiceLinePreview::amount).reduce(ZERO, BigDecimal::add);
        return new Built(out, pids, entryIds, money(total), warning);
    }

    @Transactional
    public InvoicePreview invoicePreview(Long id) {
        on();
        WorkOrder w = find(id);
        Built b = build(w);
        return new InvoicePreview(w.getId(), w.getNumber(), w.getCustomerId(), w.getCustomerName(), b.lines(), b.total(), b.entryIds().size(), b.warning());
    }

    @Transactional
    public InvoiceResult createInvoice(Actor actor, Long id, InvoiceRequestBody body) {
        on();
        WorkOrder w = find(id);
        if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("An internal job is not billed.");
        if (w.getStatus() != WorkOrderStatus.COMPLETED) throw ApiException.badRequest("Complete the work order before invoicing it.");
        if (w.getInvoiceId() != null) throw ApiException.conflict("This work order is already on invoice " + w.getInvoiceNumber() + ".");
        if (w.getCustomerId() == null) throw ApiException.badRequest("This work order has no customer.");
        Built b = build(w);
        if (b.lines().isEmpty()) throw ApiException.badRequest("There is nothing to invoice: no price, parts, charged costs or approved billable hours.");
        if (b.warning() != null && b.warning().startsWith("Some hours")) throw ApiException.badRequest(b.warning());
        Product service = timeService.serviceProductForInvoices();
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(w.getCustomerId());
        req.setDueDate(body == null ? null : body.dueDate());
        req.setNotes(body != null && notBlank(body.notes()) ? body.notes().trim() : "Work order " + w.getNumber() + " — " + w.getTitle());
        if (w.getBranchId() != null) req.setBranchId(w.getBranchId());
        List<InvoiceRequest.LineEntry> lines = new ArrayList<>();
        for (int i = 0; i < b.lines().size(); i++) {
            InvoiceLinePreview l = b.lines().get(i);
            InvoiceRequest.LineEntry e = new InvoiceRequest.LineEntry();
            if ("PART".equals(l.kind())) {
                e.setProductId(b.productIds().get(i));
                e.setQuantity(l.quantity());
                e.setUnitPrice(l.unitPrice());
            } else {
                e.setProductId(service.getId());
                e.setQuantity(1);
                e.setUnitPrice(l.amount());
            }
            e.setDescription(l.description());
            lines.add(e);
        }
        req.setLines(lines);
        InvoiceDto invoice = invoiceService.createDraft(req);
        w.setInvoiceId(invoice.id());
        w.setInvoiceNumber(invoice.invoiceNumber());
        repository.save(w);
        if (!b.entryIds().isEmpty()) timeService.markInvoiced(b.entryIds(), invoice.id());
        auditLogService.record("WORK_ORDER_INVOICED", "Invoiced work order " + w.getNumber() + " on " + invoice.invoiceNumber(), "WorkOrder", id);
        return new InvoiceResult(invoice.id(), invoice.invoiceNumber(), b.total());
    }

    /** The draft invoice was deleted or voided: the job (and its hours) can be billed again. */
    @Transactional
    public WorkOrderDetail releaseInvoice(Actor actor, Long id) {
        on();
        WorkOrder w = find(id);
        if (w.getInvoiceId() == null) throw ApiException.badRequest("This work order is not on an invoice.");
        timeService.releaseInvoice(w.getInvoiceId());
        w.setInvoiceId(null);
        w.setInvoiceNumber(null);
        repository.save(w);
        auditLogService.record("WORK_ORDER_INVOICE_RELEASED", "Released the invoice of work order " + w.getNumber(), "WorkOrder", id);
        return toDetail(actor, w);
    }

    // ------------------------------------------------------------------ service packages

    @Transactional
    public List<PackageSummary> packages(boolean activeOnly) {
        on();
        return (activeOnly ? packageRepository.findByActiveTrueOrderByNameAsc() : packageRepository.findAllByOrderByNameAsc()).stream()
                .map(p -> new PackageSummary(p.getId(), p.getCode(), p.getName(), p.getFixedPrice(), p.getEstimatedHours(), p.isActive(),
                        packageItemRepository.findByPackageIdOrderBySeqAsc(p.getId()).size(), packagePartRepository.findByPackageIdOrderByIdAsc(p.getId()).size(),
                        repository.countByPackageId(p.getId())))
                .toList();
    }

    @Transactional
    public PackageDetail packageDetail(Long id) {
        on();
        return toPackage(findPackage(id));
    }

    private ServicePackage findPackage(Long id) {
        return packageRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service package not found: " + id));
    }

    private PackageDetail toPackage(ServicePackage p) {
        return new PackageDetail(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getFixedPrice(), p.getEstimatedHours(), p.isActive(),
                packageItemRepository.findByPackageIdOrderBySeqAsc(p.getId()).stream().map(i -> new PackageItemDto(i.getId(), i.getText())).toList(),
                packagePartRepository.findByPackageIdOrderByIdAsc(p.getId()).stream()
                        .map(x -> new PackagePartDto(x.getId(), x.getProductId(), x.getProductName(), x.getQuantity(), x.isIncluded())).toList());
    }

    @Transactional
    public PackageDetail savePackage(Long id, PackageRequest r) {
        on();
        if (!notBlank(r.name())) throw ApiException.badRequest("Give the package a name.");
        if (r.fixedPrice() != null && r.fixedPrice().signum() < 0) throw ApiException.badRequest("The price cannot be negative.");
        ServicePackage p = id == null ? new ServicePackage() : findPackage(id);
        p.setCode(clean(r.code()));
        p.setName(r.name().trim());
        p.setDescription(clean(r.description()));
        p.setFixedPrice(money(r.fixedPrice()));
        p.setEstimatedHours(r.estimatedHours());
        p.setActive(r.active() == null || r.active());
        ServicePackage saved = packageRepository.save(p);
        packageItemRepository.deleteAll(packageItemRepository.findByPackageIdOrderBySeqAsc(saved.getId()));
        packagePartRepository.deleteAll(packagePartRepository.findByPackageIdOrderByIdAsc(saved.getId()));
        packageItemRepository.flush();
        int seq = 0;
        for (String text : r.items() == null ? List.<String>of() : r.items()) {
            if (!notBlank(text)) continue;
            ServicePackageItem i = new ServicePackageItem();
            i.setPackageId(saved.getId());
            i.setSeq(seq++);
            i.setText(text.trim().length() > 500 ? text.trim().substring(0, 500) : text.trim());
            packageItemRepository.save(i);
        }
        for (PackagePartRequest pr : r.parts() == null ? List.<PackagePartRequest>of() : r.parts()) {
            if (pr.productId() == null) continue;
            Product product = catalogService.getProductOrThrow(pr.productId());
            ServicePackagePart part = new ServicePackagePart();
            part.setPackageId(saved.getId());
            part.setProductId(product.getId());
            part.setProductName(product.getName());
            part.setQuantity(pr.quantity() == null || pr.quantity() < 1 ? 1 : pr.quantity());
            part.setIncluded(pr.included() == null || pr.included());
            packagePartRepository.save(part);
        }
        auditLogService.record(id == null ? "SERVICE_PACKAGE_CREATED" : "SERVICE_PACKAGE_UPDATED", (id == null ? "Created" : "Updated") + " service package " + saved.getName(),
                "ServicePackage", saved.getId());
        return toPackage(saved);
    }

    @Transactional
    public void deletePackage(Long id) {
        on();
        ServicePackage p = findPackage(id);
        if (repository.countByPackageId(id) > 0) {
            throw ApiException.conflict("This package has been used on work orders. Mark it inactive instead of deleting it.");
        }
        packageItemRepository.deleteAll(packageItemRepository.findByPackageIdOrderBySeqAsc(id));
        packagePartRepository.deleteAll(packagePartRepository.findByPackageIdOrderByIdAsc(id));
        packageRepository.delete(p);
        auditLogService.record("SERVICE_PACKAGE_DELETED", "Deleted service package " + p.getName(), "ServicePackage", id);
    }
}
