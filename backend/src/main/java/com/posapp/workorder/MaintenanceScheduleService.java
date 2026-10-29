package com.posapp.workorder;

import com.posapp.assets.FixedAsset;
import com.posapp.assets.FixedAssetRepository;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import com.posapp.workorder.WorkOrderService.Actor;
import com.posapp.workorder.dto.WorkOrderDtos.ScheduleDto;
import com.posapp.workorder.dto.WorkOrderDtos.ScheduleRunResult;
import com.posapp.workorder.dto.WorkOrderRequests.ScheduleRequest;
import com.posapp.workorder.dto.WorkOrderRequests.WorkOrderRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Preventive maintenance: a schedule says "every 3 months, service this generator" and makes the next work order on its own, a few days ahead
 * of the date, so the job is already assigned and scheduled when its time comes. A job is not piled up on an unfinished one.
 */
@Service
public class MaintenanceScheduleService {
    private static final Set<WorkOrderStatus> ACTIVE = Set.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private final MaintenanceScheduleRepository repository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final ServicePackageRepository packageRepository;
    private final CustomerRepository customerRepository;
    private final FixedAssetRepository assetRepository;
    private final UserRepository userRepository;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public MaintenanceScheduleService(MaintenanceScheduleRepository repository, WorkOrderRepository workOrderRepository, WorkOrderService workOrderService,
                                      ServicePackageRepository packageRepository, CustomerRepository customerRepository, FixedAssetRepository assetRepository,
                                      UserRepository userRepository, ModuleService moduleService, AuditLogService auditLogService) {
        this.repository = repository;
        this.workOrderRepository = workOrderRepository;
        this.workOrderService = workOrderService;
        this.packageRepository = packageRepository;
        this.customerRepository = customerRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.moduleService = moduleService;
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

    private MaintenanceSchedule find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Schedule not found: " + id));
    }

    static String every(MaintenanceSchedule s) {
        String unit = switch (s.getIntervalUnit()) {
            case DAYS -> "day";
            case WEEKS -> "week";
            case MONTHS -> "month";
        };
        return s.getIntervalCount() == 1 ? "Every " + unit : "Every " + s.getIntervalCount() + " " + unit + "s";
    }

    private ScheduleDto dto(MaintenanceSchedule s) {
        WorkOrder last = s.getLastWorkOrderId() == null ? null : workOrderRepository.findById(s.getLastWorkOrderId()).orElse(null);
        boolean dueNow = s.isActive() && !s.getNextDueDate().minusDays(s.getLeadDays()).isAfter(LocalDate.now());
        return new ScheduleDto(s.getId(), s.getTitle(), s.getDescription(), s.getItemDescription(), s.getType(), s.getPriority(), s.getCustomerId(), s.getCustomerName(),
                s.getAssetId(), s.getAssetName(), s.getPackageId(), s.getPackageName(), s.getAssignedToUserId(), s.getAssignedToName(), s.getFixedPrice(), s.getLabourRate(),
                s.getBranchId(), s.getIntervalUnit(), s.getIntervalCount(), s.getNextDueDate(), s.getLeadDays(), s.getDueAfterDays(), s.isSkipIfOpen(), s.isActive(),
                s.getLastGeneratedAt(), s.getLastWorkOrderId(), last == null ? null : last.getNumber(), last == null ? null : last.getStatus().name(), s.getTimesGenerated(),
                dueNow, every(s));
    }

    @Transactional
    public List<ScheduleDto> list() {
        on();
        return repository.findAllByOrderByNextDueDateAscIdAsc().stream().map(this::dto).toList();
    }

    @Transactional
    public ScheduleDto get(Long id) {
        on();
        return dto(find(id));
    }

    @Transactional
    public ScheduleDto save(Actor actor, Long id, ScheduleRequest r) {
        on();
        MaintenanceSchedule s = id == null ? new MaintenanceSchedule() : find(id);
        if (id == null) s.setCreatedByName(TimeTrackingService.actorName(actor.user()));
        if (!notBlank(r.title())) throw ApiException.badRequest("Give the schedule a title.");
        if (r.title().trim().length() > 255) throw ApiException.badRequest("The title can be at most 255 characters.");
        s.setTitle(r.title().trim());
        s.setDescription(clean(r.description()));
        s.setItemDescription(clean(r.itemDescription()));
        s.setType(r.type() != null ? r.type() : WorkOrderType.INTERNAL);
        s.setPriority(r.priority() != null ? r.priority() : WorkOrderPriority.NORMAL);
        if (s.getType() == WorkOrderType.CUSTOMER) {
            if (r.customerId() == null) throw ApiException.badRequest("Choose the customer for a customer job.");
            Customer c = customerRepository.findById(r.customerId()).orElseThrow(() -> ApiException.notFound("Customer not found."));
            s.setCustomerId(c.getId());
            s.setCustomerName(c.getName());
        } else {
            s.setCustomerId(null);
            s.setCustomerName(null);
        }
        if (r.assetId() != null) {
            FixedAsset a = assetRepository.findById(r.assetId()).orElseThrow(() -> ApiException.notFound("Asset not found."));
            s.setAssetId(a.getId());
            s.setAssetName(a.getCode() + " — " + a.getName());
        } else {
            s.setAssetId(null);
            s.setAssetName(null);
        }
        if (r.packageId() != null) {
            ServicePackage p = packageRepository.findById(r.packageId()).orElseThrow(() -> ApiException.notFound("Service package not found."));
            s.setPackageId(p.getId());
            s.setPackageName(p.getName());
        } else {
            s.setPackageId(null);
            s.setPackageName(null);
        }
        if (r.assignedToUserId() != null) {
            AppUser u = userRepository.findById(r.assignedToUserId()).orElseThrow(() -> ApiException.notFound("User not found."));
            s.setAssignedToUserId(u.getId());
            s.setAssignedToName(TimeTrackingService.actorName(u));
        } else {
            s.setAssignedToUserId(null);
            s.setAssignedToName(null);
        }
        if (r.fixedPrice() != null && r.fixedPrice().signum() < 0) throw ApiException.badRequest("The fixed price cannot be negative.");
        s.setFixedPrice(r.fixedPrice() == null || s.getType() == WorkOrderType.INTERNAL ? BigDecimal.ZERO : r.fixedPrice().setScale(2, java.math.RoundingMode.HALF_UP));
        s.setLabourRate(r.labourRate() != null && r.labourRate().signum() > 0 ? r.labourRate().setScale(2, java.math.RoundingMode.HALF_UP) : null);
        s.setBranchId(r.branchId());
        if (r.intervalUnit() == null) throw ApiException.badRequest("Choose how often: days, weeks or months.");
        int count = r.intervalCount() == null ? 1 : r.intervalCount();
        if (count < 1 || count > 120) throw ApiException.badRequest("Repeat every 1 to 120 " + r.intervalUnit().name().toLowerCase() + ".");
        s.setIntervalUnit(r.intervalUnit());
        s.setIntervalCount(count);
        if (r.nextDueDate() == null) throw ApiException.badRequest("Enter the date of the next job.");
        s.setNextDueDate(r.nextDueDate());
        int lead = r.leadDays() == null ? 0 : r.leadDays();
        int dueAfter = r.dueAfterDays() == null ? 0 : r.dueAfterDays();
        if (lead < 0 || lead > 365) throw ApiException.badRequest("Make the job 0 to 365 days ahead.");
        if (dueAfter < 0 || dueAfter > 365) throw ApiException.badRequest("The job can be due 0 to 365 days after its date.");
        s.setLeadDays(lead);
        s.setDueAfterDays(dueAfter);
        if (r.skipIfOpen() != null) s.setSkipIfOpen(r.skipIfOpen());
        if (r.active() != null) s.setActive(r.active());
        repository.save(s);
        auditLogService.record(id == null ? "MAINTENANCE_SCHEDULE_CREATED" : "MAINTENANCE_SCHEDULE_UPDATED",
                (id == null ? "Created" : "Updated") + " preventive maintenance schedule '" + s.getTitle() + "' (" + every(s).toLowerCase() + ", next " + s.getNextDueDate() + ")",
                "MaintenanceSchedule", s.getId());
        return dto(s);
    }

    @Transactional
    public ScheduleDto setActive(Long id, boolean active) {
        on();
        MaintenanceSchedule s = find(id);
        s.setActive(active);
        repository.save(s);
        auditLogService.record(active ? "MAINTENANCE_SCHEDULE_RESUMED" : "MAINTENANCE_SCHEDULE_PAUSED", (active ? "Resumed" : "Paused") + " preventive maintenance schedule '" + s.getTitle() + "'",
                "MaintenanceSchedule", id);
        return dto(s);
    }

    @Transactional
    public void delete(Long id) {
        on();
        MaintenanceSchedule s = find(id);
        repository.delete(s);
        auditLogService.record("MAINTENANCE_SCHEDULE_DELETED", "Deleted preventive maintenance schedule '" + s.getTitle() + "' (jobs it made are kept)", "MaintenanceSchedule", id);
    }

    /** Makes the job for this schedule now, whatever the date, and moves the schedule on to its next date. */
    @Transactional
    public ScheduleRunResult runNow(Long id) {
        on();
        MaintenanceSchedule s = find(id);
        ScheduleRunResult r = generate(s, true);
        if (r.created() == 0 && !r.notes().isEmpty()) throw ApiException.conflict(r.notes().get(0));
        return r;
    }

    /** Daily, early morning: every active schedule whose date minus its lead days has come makes its job. */
    @Scheduled(cron = "0 20 5 * * *")
    @Transactional
    public void runDue() {
        if (!moduleService.isEnabled(ModuleKey.WORK_ORDERS)) return;
        runDueNow();
    }

    /** The sweep itself (also called by tests and by the "Run due schedules" button). */
    @Transactional
    public ScheduleRunResult runDueNow() {
        on();
        LocalDate today = LocalDate.now();
        int created = 0;
        int skipped = 0;
        List<String> numbers = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (MaintenanceSchedule s : repository.findByActiveTrueAndNextDueDateLessThanEqual(today.plusDays(365))) {
            if (s.getNextDueDate().minusDays(s.getLeadDays()).isAfter(today)) continue;
            try {
                ScheduleRunResult r = generate(s, false);
                created += r.created();
                skipped += r.skipped();
                numbers.addAll(r.numbers());
                notes.addAll(r.notes());
            } catch (Exception ex) {
                notes.add(s.getTitle() + ": " + ex.getMessage());
                auditLogService.record("MAINTENANCE_SCHEDULE_FAILED", "Could not make the job for schedule '" + s.getTitle() + "': " + ex.getMessage(), "MaintenanceSchedule", s.getId());
            }
        }
        return new ScheduleRunResult(created, skipped, numbers, notes);
    }

    private ScheduleRunResult generate(MaintenanceSchedule s, boolean force) {
        LocalDate today = LocalDate.now();
        LocalDate due = s.getNextDueDate();
        if (!force && s.isSkipIfOpen() && s.getLastWorkOrderId() != null) {
            WorkOrder last = workOrderRepository.findById(s.getLastWorkOrderId()).orElse(null);
            if (last != null && ACTIVE.contains(last.getStatus())) {
                s.setNextDueDate(advance(s, due, today));
                repository.save(s);
                String why = "The last job from '" + s.getTitle() + "' (" + last.getNumber() + ") is still " + last.getStatus().name().toLowerCase().replace('_', ' ') + ", so no new job was made for " + due + ".";
                auditLogService.record("MAINTENANCE_SCHEDULE_SKIPPED", why, "MaintenanceSchedule", s.getId());
                return new ScheduleRunResult(0, 1, List.of(), List.of(why));
            }
        }
        if (force && s.getLastWorkOrderId() != null && s.isSkipIfOpen()) {
            WorkOrder last = workOrderRepository.findById(s.getLastWorkOrderId()).orElse(null);
            if (last != null && ACTIVE.contains(last.getStatus())) {
                return new ScheduleRunResult(0, 0, List.of(), List.of("The last job from this schedule (" + last.getNumber() + ") is still open. Finish or cancel it first, or switch off \"don't make a new job while the last one is open\"."));
            }
        }
        LocalDate scheduled = due.isBefore(today) ? today : due;
        WorkOrderRequest req = new WorkOrderRequest(s.getType(), s.getPriority(), s.getTitle(), s.getDescription(), s.getItemDescription(), s.getCustomerId(), s.getAssetId(),
                s.getPackageId(), s.getFixedPrice().signum() > 0 ? s.getFixedPrice() : null, s.getLabourRate(), s.getAssignedToUserId(), today, scheduled,
                scheduled.plusDays(s.getDueAfterDays()), s.getBranchId());
        WorkOrder w = workOrderService.createEntity("Preventive maintenance schedule", req, s.getId());
        s.setLastGeneratedAt(Instant.now());
        s.setLastWorkOrderId(w.getId());
        s.setTimesGenerated(s.getTimesGenerated() + 1);
        s.setNextDueDate(advance(s, due, today));
        repository.save(s);
        auditLogService.record("MAINTENANCE_SCHEDULE_RAN", "Made work order " + w.getNumber() + " from schedule '" + s.getTitle() + "' (next on " + s.getNextDueDate() + ")", "MaintenanceSchedule", s.getId());
        return new ScheduleRunResult(1, 0, List.of(w.getNumber()), List.of());
    }

    /** One interval on from the date just served, and past today (a server that was off does not make a backlog). */
    private static LocalDate advance(MaintenanceSchedule s, LocalDate from, LocalDate today) {
        LocalDate next = s.getIntervalUnit().addTo(from, s.getIntervalCount());
        int guard = 0;
        while (!next.isAfter(today) && guard++ < 1000) next = s.getIntervalUnit().addTo(next, s.getIntervalCount());
        return next;
    }
}
