package com.posapp.timetracking;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.timetracking.dto.TimeDtos.*;
import com.posapp.timetracking.dto.TimeRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Time tracking: projects (the billable container), tasks, people's rates, time entries with a timer, submit/approve, and billing
 * approved time onto an invoice. Other modules (work orders, law-firm matters) plug in by creating a project with a source.
 */
@Service
public class TimeTrackingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final TimeSettingsRepository settingsRepository;
    private final TimeProjectRepository projectRepository;
    private final TimeProjectRateRepository rateRepository;
    private final TimeTaskRepository taskRepository;
    private final TimeEntryRepository entryRepository;
    private final TimeTimerRepository timerRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final CatalogService catalogService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;
    private final org.springframework.beans.factory.ObjectProvider<TimeProjectGate> gates;

    public TimeTrackingService(TimeSettingsRepository settingsRepository, TimeProjectRepository projectRepository,
                               TimeProjectRateRepository rateRepository, TimeTaskRepository taskRepository, TimeEntryRepository entryRepository,
                               TimeTimerRepository timerRepository, UserRepository userRepository, CustomerRepository customerRepository,
                               InvoiceService invoiceService, InvoiceRepository invoiceRepository, CatalogService catalogService,
                               ModuleService moduleService, AuditLogService auditLogService,
                               org.springframework.beans.factory.ObjectProvider<TimeProjectGate> gates) {
        this.gates = gates;
        this.settingsRepository = settingsRepository;
        this.projectRepository = projectRepository;
        this.rateRepository = rateRepository;
        this.taskRepository = taskRepository;
        this.entryRepository = entryRepository;
        this.timerRepository = timerRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.catalogService = catalogService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ settings

    private void on() {
        moduleService.requireEnabled(ModuleKey.TIME_TRACKING);
    }

    @Transactional
    public TimeSettings settingsEntity() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> settingsRepository.save(new TimeSettings()));
    }

    @Transactional
    public SettingsDto settings() {
        on();
        return settingsDto(settingsEntity());
    }

    private SettingsDto settingsDto(TimeSettings s) {
        String productName = s.getBillingProductId() == null ? null
                : catalogService.getProductOrThrow(s.getBillingProductId()).getName();
        return new SettingsDto(s.isRequireApproval(), s.getTimerRoundingMinutes(), s.getDefaultHourlyRate(), s.getMaxHoursPerDay(),
                s.getDailyCapacityHours(), s.getBillingProductId(), productName);
    }

    @Transactional
    public SettingsDto updateSettings(SettingsRequest r) {
        on();
        TimeSettings s = settingsEntity();
        if (r.requireApproval() != null) s.setRequireApproval(r.requireApproval());
        if (r.timerRoundingMinutes() != null) {
            if (!Set.of(0, 5, 6, 10, 15, 30, 60).contains(r.timerRoundingMinutes())) {
                throw ApiException.badRequest("Choose 0, 5, 6, 10, 15, 30 or 60 minutes for rounding.");
            }
            s.setTimerRoundingMinutes(r.timerRoundingMinutes());
        }
        if (r.defaultHourlyRate() != null) {
            if (r.defaultHourlyRate().signum() < 0) throw ApiException.badRequest("The default rate cannot be negative.");
            s.setDefaultHourlyRate(r.defaultHourlyRate());
        }
        if (r.maxHoursPerDay() != null) {
            if (r.maxHoursPerDay() < 1 || r.maxHoursPerDay() > 24) throw ApiException.badRequest("The daily limit must be between 1 and 24 hours.");
            s.setMaxHoursPerDay(r.maxHoursPerDay());
        }
        if (r.dailyCapacityHours() != null) {
            if (r.dailyCapacityHours().signum() <= 0 || r.dailyCapacityHours().compareTo(BigDecimal.valueOf(24)) > 0) {
                throw ApiException.badRequest("Working hours per day must be between 0 and 24.");
            }
            s.setDailyCapacityHours(r.dailyCapacityHours());
        }
        if (r.billingProductId() != null) {
            Product p = catalogService.getProductOrThrow(r.billingProductId());
            if (!p.isService()) throw ApiException.badRequest("Choose a service product (not a stocked item) for billed time.");
            s.setBillingProductId(p.getId());
        }
        settingsRepository.save(s);
        auditLogService.record("TIME_SETTINGS_UPDATED", "Updated time tracking settings");
        return settingsDto(s);
    }

    /** The product billed time is invoiced under; a "Professional services" service product is created the first time. */
    private Product billingProduct(TimeSettings s) {
        if (s.getBillingProductId() != null) {
            return catalogService.getProductOrThrow(s.getBillingProductId());
        }
        ProductRequest req = new ProductRequest();
        req.setName("Professional services (time)");
        req.setDescription("Billed time");
        req.setUnitPrice(ZERO);
        req.setCostPrice(ZERO);
        req.setStockQuantity(BigDecimal.ZERO);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        s.setBillingProductId(created.getId());
        settingsRepository.save(s);
        return created;
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal amount(int minutes, BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(minutes)).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    public static String actorName(AppUser u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }

    private TimeProject project(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project not found: " + id));
    }

    /** True unless a gate (the module that owns the project) keeps this person away from it. */
    private boolean allowed(TimeProject p, AppUser user) {
        return user == null || gates.stream().allMatch(g -> g.allows(p, user));
    }

    private void requireAllowed(Long projectId, AppUser user) {
        TimeProject p = project(projectId);
        if (!allowed(p, user)) throw ApiException.notFound("Project not found: " + projectId);
    }

    private boolean allowedEntry(TimeEntry e, Map<Long, TimeProject> projects, AppUser user) {
        TimeProject p = projects.get(e.getProjectId());
        return p == null || allowed(p, user);
    }

    private TimeEntry entry(Long id) {
        return entryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Time entry not found: " + id));
    }

    private Map<Long, String> customerNames() {
        return customerRepository.findAll().stream().collect(Collectors.toMap(Customer::getId, c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
    }

    private BigDecimal resolveRate(TimeProject p, Long userId) {
        return rateRepository.findByProjectIdAndUserId(p.getId(), userId).map(TimeProjectRate::getHourlyRate)
                .or(() -> java.util.Optional.ofNullable(p.getDefaultRate()))
                .orElseGet(() -> settingsEntity().getDefaultHourlyRate());
    }

    // ------------------------------------------------------------------ projects

    @Transactional
    public List<ProjectSummaryDto> projects(ProjectStatus status, Long customerId, boolean includeArchived) {
        on();
        Map<Long, String> customers = customerNames();
        Map<Long, List<TimeEntry>> byProject = entryRepository.findAll().stream().collect(Collectors.groupingBy(TimeEntry::getProjectId));
        return projectRepository.findAllByOrderByNameAsc().stream()
                .filter(p -> status == null ? (includeArchived || p.getStatus() != ProjectStatus.ARCHIVED) : p.getStatus() == status)
                .filter(p -> customerId == null || customerId.equals(p.getCustomerId()))
                .map(p -> summary(p, customers, byProject.getOrDefault(p.getId(), List.of()))).toList();
    }

    private ProjectSummaryDto summary(TimeProject p, Map<Long, String> customers, List<TimeEntry> entries) {
        long total = entries.stream().mapToLong(TimeEntry::getMinutes).sum();
        long billable = entries.stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum();
        BigDecimal unbilled = entries.stream().filter(e -> e.isBillable() && e.getStatus() == TimeEntryStatus.APPROVED && e.getInvoiceId() == null)
                .map(e -> amount(e.getMinutes(), e.getHourlyRate())).reduce(ZERO, BigDecimal::add);
        BigDecimal percent = null;
        if (p.getBudgetHours() != null && p.getBudgetHours().signum() > 0) {
            percent = hours(total).multiply(BigDecimal.valueOf(100)).divide(p.getBudgetHours(), 1, RoundingMode.HALF_UP);
        }
        return new ProjectSummaryDto(p.getId(), p.getName(), p.getCode(), p.getCustomerId(),
                p.getCustomerId() == null ? null : customers.get(p.getCustomerId()), p.getStatus(), p.isBillable(), p.getDefaultRate(),
                p.getBudgetHours(), hours(total), hours(billable), unbilled, percent, p.getSourceType(), p.getSourceId(), p.getStartDate(), p.getEndDate());
    }

    @Transactional
    public ProjectDetailDto projectDetail(Long id) {
        on();
        TimeProject p = project(id);
        List<TimeEntry> entries = entryRepository.findByProjectId(id);
        Map<Long, Long> minutesByTask = entries.stream().filter(e -> e.getTaskId() != null)
                .collect(Collectors.groupingBy(TimeEntry::getTaskId, Collectors.summingLong(TimeEntry::getMinutes)));
        List<TaskDto> tasks = taskRepository.findByProjectIdOrderByNameAsc(id).stream().map(t -> taskDto(t, minutesByTask.getOrDefault(t.getId(), 0L))).toList();
        Map<Long, String> names = userNames();
        List<RateDto> rates = rateRepository.findByProjectId(id).stream()
                .map(r -> new RateDto(r.getUserId(), names.getOrDefault(r.getUserId(), "User " + r.getUserId()), r.getHourlyRate())).toList();
        return new ProjectDetailDto(summary(p, customerNames(), entries), p.getDescription(), p.getBranchId(), tasks, rates);
    }

    private static TaskDto taskDto(TimeTask t, long minutes) {
        return new TaskDto(t.getId(), t.getProjectId(), t.getName(), t.getEstimatedHours(), t.isActive(), hours(minutes));
    }

    private Map<Long, String> userNames() {
        return userRepository.findAll().stream().collect(Collectors.toMap(AppUser::getId, TimeTrackingService::actorName, (a, b) -> a));
    }

    @Transactional
    public ProjectDetailDto createProject(ProjectRequest r) {
        on();
        TimeProject p = new TimeProject();
        applyProject(p, r);
        projectRepository.save(p);
        auditLogService.record("TIME_PROJECT_CREATED", "Created time project " + p.getName(), "TimeProject", p.getId());
        return projectDetail(p.getId());
    }

    @Transactional
    public ProjectDetailDto updateProject(Long id, ProjectRequest r) {
        on();
        TimeProject p = project(id);
        applyProject(p, r);
        projectRepository.save(p);
        auditLogService.record("TIME_PROJECT_UPDATED", "Updated time project " + p.getName(), "TimeProject", p.getId());
        return projectDetail(p.getId());
    }

    private void applyProject(TimeProject p, ProjectRequest r) {
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Give the project a name.");
        p.setName(r.name().trim());
        p.setCode(r.code() == null || r.code().isBlank() ? null : r.code().trim());
        if (r.customerId() != null) {
            customerRepository.findById(r.customerId()).orElseThrow(() -> ApiException.badRequest("Customer not found."));
        }
        p.setCustomerId(r.customerId());
        p.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        if (r.status() != null) p.setStatus(r.status());
        if (r.billable() != null) p.setBillable(r.billable());
        if (r.defaultRate() != null && r.defaultRate().signum() < 0) throw ApiException.badRequest("The rate cannot be negative.");
        p.setDefaultRate(r.defaultRate());
        if (r.budgetHours() != null && r.budgetHours().signum() <= 0) throw ApiException.badRequest("The budget must be more than zero hours (or empty).");
        p.setBudgetHours(r.budgetHours());
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("The end date is before the start date.");
        }
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
        p.setBranchId(r.branchId());
    }

    @Transactional
    public void deleteProject(Long id) {
        on();
        TimeProject p = project(id);
        if (entryRepository.countByProjectId(id) > 0) {
            throw ApiException.conflict("This project already has time logged. Archive it instead of deleting it.");
        }
        timerRepository.findAll().stream().filter(t -> t.getProjectId().equals(id)).forEach(timerRepository::delete);
        taskRepository.deleteAll(taskRepository.findByProjectIdOrderByNameAsc(id));
        rateRepository.deleteByProjectId(id);
        projectRepository.delete(p);
        auditLogService.record("TIME_PROJECT_DELETED", "Deleted time project " + p.getName(), "TimeProject", id);
    }

    /**
     * For other modules: the project that time on a work order or a matter is logged to. Created on first use, then reused.
     * The caller passes the customer so billing works without more questions.
     */
    @Transactional
    public TimeProject ensureProjectForSource(String sourceType, Long sourceId, String name, Long customerId, Long branchId) {
        return projectRepository.findFirstBySourceTypeAndSourceId(sourceType, sourceId).orElseGet(() -> {
            TimeProject p = new TimeProject();
            p.setName(name);
            p.setCustomerId(customerId);
            p.setBranchId(branchId);
            p.setSourceType(sourceType);
            p.setSourceId(sourceId);
            return projectRepository.save(p);
        });
    }

    /**
     * For other modules: keeps the project behind a work order or matter in step with it (name, customer, rate, whether its time is
     * billable, open or closed). Does nothing when the source has no project yet.
     */
    @Transactional
    public void configureSourceProject(String sourceType, Long sourceId, String name, Long customerId, boolean billable, BigDecimal rate, ProjectStatus status) {
        projectRepository.findFirstBySourceTypeAndSourceId(sourceType, sourceId).ifPresent(p -> {
            p.setName(name);
            p.setCustomerId(customerId);
            p.setBillable(billable);
            p.setDefaultRate(rate);
            p.setStatus(status);
            projectRepository.save(p);
        });
    }

    /** The project behind a source (work order, matter), if it has one. */
    @Transactional(readOnly = true)
    public java.util.Optional<TimeProject> projectForSource(String sourceType, Long sourceId) {
        return projectRepository.findFirstBySourceTypeAndSourceId(sourceType, sourceId);
    }

    /** Hours and unbilled labour of one project, for the screens of the module that owns it. */
    @Transactional(readOnly = true)
    public BigDecimal hoursOf(Long projectId) {
        return hours(entryRepository.findByProjectId(projectId).stream().mapToLong(TimeEntry::getMinutes).sum());
    }

    /** For other modules that put time on their own invoice: marks these approved entries as invoiced. */
    @Transactional
    public void markInvoiced(List<Long> entryIds, Long invoiceId) {
        List<TimeEntry> entries = entryRepository.findByIdIn(entryIds);
        entries.forEach(e -> {
            e.setStatus(TimeEntryStatus.INVOICED);
            e.setInvoiceId(invoiceId);
        });
        entryRepository.saveAll(entries);
    }

    /** For other modules: takes these entries off an invoice (or a retainer) so they can be billed again. */
    @Transactional
    public void releaseEntries(List<Long> entryIds) {
        List<TimeEntry> entries = entryRepository.findByIdIn(entryIds);
        entries.forEach(e -> {
            if (e.getStatus() == TimeEntryStatus.INVOICED) {
                e.setStatus(TimeEntryStatus.APPROVED);
                e.setInvoiceId(null);
            }
        });
        entryRepository.saveAll(entries);
    }

    /** For other modules: the entries of one project, newest first, for the module's own screens. */
    @Transactional
    public List<EntryDto> entriesOfProject(Long projectId) {
        List<TimeEntry> list = new ArrayList<>(entryRepository.findByProjectId(projectId));
        list.sort(Comparator.comparing(TimeEntry::getWorkDate).thenComparing(TimeEntry::getId).reversed());
        return entryDtos(list);
    }

    /** For other modules: approved billable time not yet billed on one project, as entries. */
    @Transactional
    public List<EntryDto> unbilledEntries(Long projectId, LocalDate to) {
        return entryDtos(billable(projectId, null, to, null));
    }

    /** The service product that billed time and other service charges are invoiced under. */
    @Transactional
    public Product serviceProductForInvoices() {
        return billingProduct(settingsEntity());
    }

    // ---- tasks and rates

    @Transactional
    public TaskDto addTask(Long projectId, TaskRequest r) {
        on();
        project(projectId);
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Give the task a name.");
        TimeTask t = new TimeTask();
        t.setProjectId(projectId);
        t.setName(r.name().trim());
        t.setEstimatedHours(r.estimatedHours());
        taskRepository.save(t);
        return taskDto(t, 0);
    }

    @Transactional
    public TaskDto updateTask(Long taskId, TaskRequest r) {
        on();
        TimeTask t = taskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found: " + taskId));
        if (r.name() != null && !r.name().isBlank()) t.setName(r.name().trim());
        t.setEstimatedHours(r.estimatedHours());
        if (r.active() != null) t.setActive(r.active());
        taskRepository.save(t);
        long minutes = entryRepository.findByTaskId(taskId).stream().mapToLong(TimeEntry::getMinutes).sum();
        return taskDto(t, minutes);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        on();
        TimeTask t = taskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found: " + taskId));
        if (entryRepository.countByTaskId(taskId) > 0) {
            t.setActive(false);   // keeps the history readable
            taskRepository.save(t);
        } else {
            taskRepository.delete(t);
        }
    }

    @Transactional
    public List<RateDto> setRate(Long projectId, RateRequest r) {
        on();
        project(projectId);
        if (r.userId() == null) throw ApiException.badRequest("Choose a person.");
        userRepository.findById(r.userId()).orElseThrow(() -> ApiException.badRequest("Person not found."));
        if (r.hourlyRate() == null || r.hourlyRate().signum() < 0) throw ApiException.badRequest("Enter the hourly rate.");
        TimeProjectRate rate = rateRepository.findByProjectIdAndUserId(projectId, r.userId()).orElseGet(() -> {
            TimeProjectRate n = new TimeProjectRate();
            n.setProjectId(projectId);
            n.setUserId(r.userId());
            return n;
        });
        rate.setHourlyRate(r.hourlyRate());
        rateRepository.save(rate);
        return projectDetail(projectId).rates();
    }

    @Transactional
    public List<RateDto> removeRate(Long projectId, Long userId) {
        on();
        rateRepository.findByProjectIdAndUserId(projectId, userId).ifPresent(rateRepository::delete);
        return projectDetail(projectId).rates();
    }

    // ------------------------------------------------------------------ staff and pickers

    @Transactional
    public List<StaffOption> staff() {
        on();
        return userRepository.findByActiveTrue().stream().map(u -> new StaffOption(u.getId(), actorName(u)))
                .sorted(Comparator.comparing(StaffOption::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Active projects (with their active tasks) for the "log time" pickers. */
    @Transactional
    public List<ProjectChoice> projectChoices(AppUser user) {
        on();
        Map<Long, String> customers = customerNames();
        Map<Long, List<TimeTask>> tasks = taskRepository.findByActiveTrueOrderByNameAsc().stream().collect(Collectors.groupingBy(TimeTask::getProjectId));
        return projectRepository.findByStatusOrderByNameAsc(ProjectStatus.ACTIVE).stream().filter(p -> allowed(p, user))
                .map(p -> new ProjectChoice(p.getId(), p.getName(), p.getCode(), p.getCustomerId() == null ? null : customers.get(p.getCustomerId()), p.isBillable(),
                        tasks.getOrDefault(p.getId(), List.of()).stream().map(t -> taskDto(t, 0)).toList())).toList();
    }

    // ------------------------------------------------------------------ entries

    private EntryDto entryDto(TimeEntry e, Map<Long, TimeProject> projects, Map<Long, String> customers, Map<Long, String> tasks, Map<Long, String> invoices) {
        TimeProject p = projects.get(e.getProjectId());
        String customer = p == null || p.getCustomerId() == null ? null : customers.get(p.getCustomerId());
        return new EntryDto(e.getId(), e.getUserId(), e.getUserName(), e.getProjectId(), p == null ? "?" : p.getName(), customer, e.getTaskId(),
                e.getTaskId() == null ? null : tasks.get(e.getTaskId()), e.getWorkDate(), e.getMinutes(), hours(e.getMinutes()), e.getDescription(),
                e.isBillable(), e.getHourlyRate(), amount(e.getMinutes(), e.getHourlyRate()), e.getStatus(), e.getOrigin(), e.getSubmittedAt(),
                e.getDecidedByName(), e.getDecidedAt(), e.getRejectReason(), e.getInvoiceId(), e.getInvoiceId() == null ? null : invoices.get(e.getInvoiceId()));
    }

    private List<EntryDto> entryDtos(List<TimeEntry> entries) {
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, p -> p));
        Map<Long, String> customers = customerNames();
        Map<Long, String> tasks = taskRepository.findAll().stream().collect(Collectors.toMap(TimeTask::getId, TimeTask::getName));
        Map<Long, String> invoices = new HashMap<>();
        entries.stream().map(TimeEntry::getInvoiceId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> invoiceRepository.findById(id).ifPresent(i -> invoices.put(id, i.getInvoiceNumber())));
        return entries.stream().map(e -> entryDto(e, projects, customers, tasks, invoices)).toList();
    }

    @Transactional
    public WeekDto myEntries(AppUser user, LocalDate from, LocalDate to) {
        on();
        LocalDate start = from != null ? from : LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start) || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 366) {
            throw ApiException.badRequest("Choose a valid date range (up to one year).");
        }
        List<TimeEntry> list = entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), start, end);
        return new WeekDto(start, end, entryDtos(list), hours(list.stream().mapToLong(TimeEntry::getMinutes).sum()),
                hours(list.stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum()),
                count(list, TimeEntryStatus.DRAFT), count(list, TimeEntryStatus.SUBMITTED), count(list, TimeEntryStatus.APPROVED),
                count(list, TimeEntryStatus.REJECTED), count(list, TimeEntryStatus.INVOICED));
    }

    private static int count(List<TimeEntry> list, TimeEntryStatus s) {
        return (int) list.stream().filter(e -> e.getStatus() == s).count();
    }

    @Transactional
    public MySummaryDto mySummary(AppUser user) {
        on();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        List<TimeEntry> month = entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), monthStart.isBefore(weekStart) ? monthStart : weekStart, today);
        long dayMin = month.stream().filter(e -> e.getWorkDate().equals(today)).mapToLong(TimeEntry::getMinutes).sum();
        long weekMin = month.stream().filter(e -> !e.getWorkDate().isBefore(weekStart)).mapToLong(TimeEntry::getMinutes).sum();
        long weekBill = month.stream().filter(e -> !e.getWorkDate().isBefore(weekStart) && e.isBillable()).mapToLong(TimeEntry::getMinutes).sum();
        long monthMin = month.stream().filter(e -> !e.getWorkDate().isBefore(monthStart)).mapToLong(TimeEntry::getMinutes).sum();
        int toSubmit = (int) entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), today.minusDays(60), today).stream()
                .filter(e -> e.getStatus() == TimeEntryStatus.DRAFT).count();
        int rejected = (int) entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), today.minusDays(60), today).stream()
                .filter(e -> e.getStatus() == TimeEntryStatus.REJECTED).count();
        return new MySummaryDto(hours(dayMin), hours(weekMin), hours(weekBill), hours(monthMin), toSubmit, rejected);
    }

    @Transactional
    public EntryDto createEntry(AppUser user, EntryRequest r) {
        on();
        if (r.projectId() != null) requireAllowed(r.projectId(), user);
        TimeEntry e = new TimeEntry();
        e.setUserId(user.getId());
        e.setUserName(actorName(user));
        applyEntry(e, r);
        entryRepository.save(e);
        return entryDtos(List.of(e)).get(0);
    }

    @Transactional
    public EntryDto updateEntry(AppUser user, Long id, EntryRequest r) {
        on();
        TimeEntry e = entry(id);
        if (!e.getUserId().equals(user.getId())) throw ApiException.forbidden("You can only change your own time.");
        if (e.getStatus() != TimeEntryStatus.DRAFT && e.getStatus() != TimeEntryStatus.REJECTED) {
            throw ApiException.conflict("This time is already " + e.getStatus().name().toLowerCase() + " and can no longer be changed.");
        }
        if (r.projectId() != null) requireAllowed(r.projectId(), user);
        applyEntry(e, r);
        if (e.getStatus() == TimeEntryStatus.REJECTED) {
            e.setStatus(TimeEntryStatus.DRAFT);
            e.setRejectReason(null);
        }
        entryRepository.save(e);
        return entryDtos(List.of(e)).get(0);
    }

    /** A manager corrects someone's time before it is billed (minutes, rate follows, billable flag, description). */
    @Transactional
    public EntryDto managerUpdateEntry(AppUser actor, Long id, EntryRequest r) {
        on();
        TimeEntry e = entry(id);
        if (e.getStatus() == TimeEntryStatus.INVOICED) throw ApiException.conflict("This time is already on an invoice.");
        requireAllowed(e.getProjectId(), actor);
        if (r.projectId() != null) requireAllowed(r.projectId(), actor);
        applyEntry(e, r);
        entryRepository.save(e);
        auditLogService.record("TIME_ENTRY_CORRECTED", actorName(actor) + " corrected " + e.getUserName() + "'s time on " + e.getWorkDate(), "TimeEntry", e.getId());
        return entryDtos(List.of(e)).get(0);
    }

    @Transactional
    public void deleteEntry(AppUser user, Long id) {
        on();
        TimeEntry e = entry(id);
        if (!e.getUserId().equals(user.getId())) throw ApiException.forbidden("You can only delete your own time.");
        if (e.getStatus() != TimeEntryStatus.DRAFT && e.getStatus() != TimeEntryStatus.REJECTED) {
            throw ApiException.conflict("Only draft or rejected time can be deleted.");
        }
        entryRepository.delete(e);
    }

    private void applyEntry(TimeEntry e, EntryRequest r) {
        if (r.projectId() == null) throw ApiException.badRequest("Choose a project.");
        TimeProject p = project(r.projectId());
        boolean projectChanged = e.getProjectId() == null || !e.getProjectId().equals(p.getId());
        if (projectChanged && p.getStatus() != ProjectStatus.ACTIVE) {
            throw ApiException.badRequest("Time can only be logged on an active project.");
        }
        if (r.workDate() == null) throw ApiException.badRequest("Choose the date.");
        if (r.workDate().isAfter(LocalDate.now())) throw ApiException.badRequest("You cannot log time for a future date.");
        int minutes;
        if (r.minutes() != null) {
            minutes = r.minutes();
        } else if (r.hours() != null) {
            minutes = r.hours().multiply(SIXTY).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } else {
            throw ApiException.badRequest("Enter the time worked.");
        }
        if (minutes < 1) throw ApiException.badRequest("The time must be at least one minute.");
        TimeSettings s = settingsEntity();
        int cap = s.getMaxHoursPerDay() * 60;
        int already = entryRepository.findByUserIdAndWorkDate(e.getUserId(), r.workDate()).stream()
                .filter(x -> !x.getId().equals(e.getId())).mapToInt(TimeEntry::getMinutes).sum();
        if (already + minutes > cap) {
            throw ApiException.badRequest("That would be more than " + s.getMaxHoursPerDay() + " hours on " + r.workDate() + " (" + hours(already) + " h already logged).");
        }
        if (r.taskId() != null) {
            TimeTask t = taskRepository.findById(r.taskId()).orElseThrow(() -> ApiException.badRequest("Task not found."));
            if (!t.getProjectId().equals(p.getId())) throw ApiException.badRequest("That task belongs to another project.");
            if (!t.isActive() && !r.taskId().equals(e.getTaskId())) throw ApiException.badRequest("That task is no longer active.");
        }
        e.setProjectId(p.getId());
        e.setTaskId(r.taskId());
        e.setWorkDate(r.workDate());
        e.setMinutes(minutes);
        e.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        e.setBillable(p.isBillable() && (r.billable() == null || r.billable()));
        e.setHourlyRate(resolveRate(p, e.getUserId()));
    }

    // ---- submit / approve

    @Transactional
    public int submit(AppUser user, SubmitRequest r) {
        on();
        List<TimeEntry> targets;
        if (r.ids() != null && !r.ids().isEmpty()) {
            targets = entryRepository.findByIdIn(r.ids());
        } else {
            LocalDate from = r.from() != null ? r.from() : LocalDate.now().with(DayOfWeek.MONDAY);
            LocalDate to = r.to() != null ? r.to() : from.plusDays(6);
            targets = entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), from, to);
        }
        boolean approval = settingsEntity().isRequireApproval();
        int n = 0;
        for (TimeEntry e : targets) {
            if (!e.getUserId().equals(user.getId())) throw ApiException.forbidden("You can only submit your own time.");
            if (e.getStatus() != TimeEntryStatus.DRAFT && e.getStatus() != TimeEntryStatus.REJECTED) continue;
            TimeProject p = project(e.getProjectId());
            e.setHourlyRate(resolveRate(p, e.getUserId()));
            e.setSubmittedAt(Instant.now());
            e.setRejectReason(null);
            if (approval) {
                e.setStatus(TimeEntryStatus.SUBMITTED);
            } else {
                e.setStatus(TimeEntryStatus.APPROVED);
                e.setDecidedByName("Approval not required");
                e.setDecidedAt(Instant.now());
            }
            n++;
        }
        entryRepository.saveAll(targets);
        return n;
    }

    @Transactional
    public int approve(AppUser actor, List<Long> ids) {
        on();
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("Choose the entries to approve.");
        int n = 0;
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, x -> x));
        for (TimeEntry e : entryRepository.findByIdIn(ids)) {
            if (e.getStatus() != TimeEntryStatus.SUBMITTED || !allowedEntry(e, projects, actor)) continue;
            e.setStatus(TimeEntryStatus.APPROVED);
            e.setDecidedById(actor.getId());
            e.setDecidedByName(actorName(actor));
            e.setDecidedAt(Instant.now());
            e.setRejectReason(null);
            entryRepository.save(e);
            n++;
        }
        if (n > 0) auditLogService.record("TIME_APPROVED", actorName(actor) + " approved " + n + " time entr" + (n == 1 ? "y" : "ies"));
        return n;
    }

    @Transactional
    public int reject(AppUser actor, List<Long> ids, String reason) {
        on();
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("Choose the entries to send back.");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Say why the time is sent back.");
        int n = 0;
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, x -> x));
        for (TimeEntry e : entryRepository.findByIdIn(ids)) {
            if ((e.getStatus() != TimeEntryStatus.SUBMITTED && e.getStatus() != TimeEntryStatus.APPROVED) || !allowedEntry(e, projects, actor)) continue;
            e.setStatus(TimeEntryStatus.REJECTED);
            e.setDecidedById(actor.getId());
            e.setDecidedByName(actorName(actor));
            e.setDecidedAt(Instant.now());
            e.setRejectReason(reason.trim());
            entryRepository.save(e);
            n++;
        }
        if (n > 0) auditLogService.record("TIME_REJECTED", actorName(actor) + " sent back " + n + " time entr" + (n == 1 ? "y" : "ies") + ": " + reason.trim());
        return n;
    }

    @Transactional
    public List<EntryDto> allEntries(AppUser viewer, TimeEntryStatus status, Long userId, Long projectId, LocalDate from, LocalDate to) {
        on();
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, x -> x));
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(31);
        List<TimeEntry> list = entryRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(start, end).stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> userId == null || userId.equals(e.getUserId()))
                .filter(e -> projectId == null || projectId.equals(e.getProjectId()))
                .filter(e -> allowedEntry(e, projects, viewer)).toList();
        return entryDtos(list);
    }

    /** Everything waiting for a decision, whatever the date. */
    @Transactional
    public List<EntryDto> pendingApprovals(AppUser viewer) {
        on();
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, x -> x));
        return entryDtos(entryRepository.findByStatusOrderByWorkDateAscIdAsc(TimeEntryStatus.SUBMITTED).stream().filter(e -> allowedEntry(e, projects, viewer)).toList());
    }

    @Transactional
    public SummaryDto summary() {
        on();
        LocalDate today = LocalDate.now();
        List<TimeEntry> month = entryRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(today.withDayOfMonth(1), today);
        List<TimeEntry> all = entryRepository.findAll();
        Map<Long, Long> minutes = all.stream().collect(Collectors.groupingBy(TimeEntry::getProjectId, Collectors.summingLong(TimeEntry::getMinutes)));
        List<TimeProject> projects = projectRepository.findAll();
        int over = (int) projects.stream().filter(p -> p.getStatus() == ProjectStatus.ACTIVE && p.getBudgetHours() != null
                && hours(minutes.getOrDefault(p.getId(), 0L)).compareTo(p.getBudgetHours()) > 0).count();
        BigDecimal unbilled = all.stream().filter(e -> e.isBillable() && e.getStatus() == TimeEntryStatus.APPROVED && e.getInvoiceId() == null)
                .map(e -> amount(e.getMinutes(), e.getHourlyRate())).reduce(ZERO, BigDecimal::add);
        return new SummaryDto((int) all.stream().filter(e -> e.getStatus() == TimeEntryStatus.SUBMITTED).count(),
                hours(month.stream().mapToLong(TimeEntry::getMinutes).sum()), hours(month.stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum()),
                unbilled, (int) projects.stream().filter(p -> p.getStatus() == ProjectStatus.ACTIVE).count(), over);
    }

    // ------------------------------------------------------------------ timer

    @Transactional
    public TimerDto timer(AppUser user) {
        on();
        return timerRepository.findByUserId(user.getId()).map(this::timerDto).orElse(new TimerDto(false, null, null, null, null, null, true, null, 0));
    }

    private TimerDto timerDto(TimeTimer t) {
        String task = t.getTaskId() == null ? null : taskRepository.findById(t.getTaskId()).map(TimeTask::getName).orElse(null);
        return new TimerDto(true, t.getProjectId(), project(t.getProjectId()).getName(), t.getTaskId(), task, t.getDescription(), t.isBillable(),
                t.getStartedAt(), Math.max(0, Duration.between(t.getStartedAt(), Instant.now()).getSeconds()));
    }

    /** Starting a timer while another is running stops the old one first and saves its time. */
    @Transactional
    public TimerDto startTimer(AppUser user, TimerStartRequest r) {
        on();
        if (r.projectId() == null) throw ApiException.badRequest("Choose a project.");
        TimeProject p = project(r.projectId());
        if (!allowed(p, user)) throw ApiException.notFound("Project not found: " + r.projectId());
        if (p.getStatus() != ProjectStatus.ACTIVE) throw ApiException.badRequest("Time can only be logged on an active project.");
        if (r.taskId() != null) {
            TimeTask t = taskRepository.findById(r.taskId()).orElseThrow(() -> ApiException.badRequest("Task not found."));
            if (!t.getProjectId().equals(p.getId()) || !t.isActive()) throw ApiException.badRequest("That task cannot be used on this project.");
        }
        timerRepository.findByUserId(user.getId()).ifPresent(old -> {
            stopTimer(user, new TimerStopRequest(null, null));
            timerRepository.flush();
        });
        TimeTimer t = new TimeTimer();
        t.setUserId(user.getId());
        t.setProjectId(p.getId());
        t.setTaskId(r.taskId());
        t.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        t.setBillable(p.isBillable() && (r.billable() == null || r.billable()));
        t.setStartedAt(Instant.now());
        timerRepository.save(t);
        return timerDto(t);
    }

    @Transactional
    public EntryDto stopTimer(AppUser user, TimerStopRequest r) {
        on();
        TimeTimer t = timerRepository.findByUserId(user.getId()).orElseThrow(() -> ApiException.conflict("No timer is running."));
        long seconds = Math.max(0, Duration.between(t.getStartedAt(), Instant.now()).getSeconds());
        int minutes;
        if (r != null && r.minutes() != null) {
            minutes = r.minutes();
        } else {
            minutes = (int) Math.max(1, (seconds + 59) / 60);
            int step = settingsEntity().getTimerRoundingMinutes();
            if (step > 1) minutes = ((minutes + step - 1) / step) * step;
        }
        LocalDate date = t.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        String description = r != null && r.description() != null && !r.description().isBlank() ? r.description() : t.getDescription();
        TimeEntry e = new TimeEntry();
        e.setUserId(user.getId());
        e.setUserName(actorName(user));
        e.setOrigin("TIMER");
        applyEntry(e, new EntryRequest(t.getProjectId(), t.getTaskId(), date, null, minutes, description, t.isBillable()));
        entryRepository.save(e);
        timerRepository.delete(t);
        return entryDtos(List.of(e)).get(0);
    }

    @Transactional
    public void discardTimer(AppUser user) {
        on();
        timerRepository.findByUserId(user.getId()).ifPresent(timerRepository::delete);
    }

    // ------------------------------------------------------------------ billing

    private List<TimeEntry> billable(Long projectId, LocalDate from, LocalDate to, List<Long> ids) {
        List<TimeEntry> list = ids != null && !ids.isEmpty() ? entryRepository.findByIdIn(ids) : entryRepository.findByProjectId(projectId);
        return list.stream()
                .filter(e -> e.getProjectId().equals(projectId) && e.isBillable() && e.getStatus() == TimeEntryStatus.APPROVED && e.getInvoiceId() == null)
                .filter(e -> from == null || !e.getWorkDate().isBefore(from))
                .filter(e -> to == null || !e.getWorkDate().isAfter(to))
                .sorted(Comparator.comparing(TimeEntry::getWorkDate).thenComparing(TimeEntry::getId)).toList();
    }

    @Transactional
    public BillingPreviewDto billingPreview(BillingRequest r) {
        on();
        if (r.projectId() == null) throw ApiException.badRequest("Choose a project.");
        TimeProject p = project(r.projectId());
        List<TimeEntry> entries = billable(p.getId(), r.from(), r.to(), r.entryIds());
        Long customerId = r.customerId() != null ? r.customerId() : p.getCustomerId();
        String customerName = customerId == null ? null : customerRepository.findById(customerId).map(Customer::getName).orElse(null);
        Map<String, List<TimeEntry>> groups = new LinkedHashMap<>();
        for (TimeEntry e : entries) {
            groups.computeIfAbsent(e.getUserId() + "|" + e.getHourlyRate().stripTrailingZeros().toPlainString(), k -> new ArrayList<>()).add(e);
        }
        List<InvoiceLinePreview> lines = new ArrayList<>();
        BigDecimal totalHours = ZERO;
        BigDecimal total = ZERO;
        for (List<TimeEntry> g : groups.values()) {
            int minutes = g.stream().mapToInt(TimeEntry::getMinutes).sum();
            BigDecimal rate = g.get(0).getHourlyRate();
            BigDecimal amount = amount(minutes, rate);
            LocalDate first = g.stream().map(TimeEntry::getWorkDate).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate last = g.stream().map(TimeEntry::getWorkDate).max(Comparator.naturalOrder()).orElseThrow();
            String range = first.equals(last) ? first.format(SHORT_DATE) : first.format(SHORT_DATE) + " – " + last.format(SHORT_DATE);
            String description = p.getName() + " — " + g.get(0).getUserName() + ": " + hours(minutes).stripTrailingZeros().toPlainString() + " h @ "
                    + rate.stripTrailingZeros().toPlainString() + " (" + range + ")";
            lines.add(new InvoiceLinePreview(g.get(0).getUserId(), g.get(0).getUserName(), rate, hours(minutes), amount, description, g.size()));
            totalHours = totalHours.add(hours(minutes));
            total = total.add(amount);
        }
        return new BillingPreviewDto(p.getId(), p.getName(), customerId, customerName, entries.stream().map(TimeEntry::getId).toList(), lines, totalHours, total);
    }

    /** Creates a DRAFT invoice from approved billable time and marks that time as invoiced. */
    @Transactional
    public BillingResultDto invoiceTime(AppUser actor, BillingRequest r) {
        on();
        if (project(r.projectId()).getSourceType().equals("WORK_ORDER")) {
            throw ApiException.badRequest("This project belongs to a work order. Bill it from the work order, together with its parts.");
        }
        if (project(r.projectId()).getSourceType().equals("MATTER")) {
            throw ApiException.badRequest("This project belongs to a law-firm matter. Bill it from the matter, together with its costs and fees.");
        }
        BillingPreviewDto preview = billingPreview(r);
        if (preview.entryIds().isEmpty()) throw ApiException.badRequest("There is no approved billable time to invoice.");
        if (preview.customerId() == null) throw ApiException.badRequest("Choose the customer to invoice (the project has none).");
        if (preview.lines().stream().anyMatch(l -> l.hourlyRate().signum() <= 0)) {
            throw ApiException.badRequest("Some of this time has no hourly rate. Set a rate on the project (or a default in the settings), then ask the person to resubmit it.");
        }
        Product product = billingProduct(settingsEntity());
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(preview.customerId());
        req.setDueDate(r.dueDate());
        req.setNotes(r.notes() != null && !r.notes().isBlank() ? r.notes().trim() : "Time worked on " + preview.projectName());
        List<InvoiceRequest.LineEntry> lines = new ArrayList<>();
        for (InvoiceLinePreview l : preview.lines()) {
            InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
            line.setProductId(product.getId());
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(l.amount());
            line.setDescription(l.description());
            lines.add(line);
        }
        req.setLines(lines);
        TimeProject p = project(preview.projectId());
        if (p.getBranchId() != null) req.setBranchId(p.getBranchId());
        InvoiceDto invoice = invoiceService.createDraft(req);
        List<TimeEntry> entries = entryRepository.findByIdIn(preview.entryIds());
        entries.forEach(e -> {
            e.setStatus(TimeEntryStatus.INVOICED);
            e.setInvoiceId(invoice.id());
        });
        entryRepository.saveAll(entries);
        auditLogService.record("TIME_INVOICED", actorName(actor) + " invoiced " + entries.size() + " time entries of " + p.getName() + " on " + invoice.invoiceNumber(),
                "Invoice", invoice.id());
        return new BillingResultDto(invoice.id(), invoice.invoiceNumber(), entries.size(), preview.totalAmount());
    }

    /** When the draft invoice was deleted or the invoice voided, the time becomes billable again. */
    @Transactional
    public int releaseInvoice(Long invoiceId) {
        on();
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice != null && invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw ApiException.conflict("This invoice is issued. Void it first to bill the time again.");
        }
        List<TimeEntry> entries = entryRepository.findByInvoiceId(invoiceId);
        entries.forEach(e -> {
            e.setStatus(TimeEntryStatus.APPROVED);
            e.setInvoiceId(null);
        });
        entryRepository.saveAll(entries);
        if (!entries.isEmpty()) auditLogService.record("TIME_RELEASED", "Released " + entries.size() + " time entries from invoice " + invoiceId, "Invoice", invoiceId);
        return entries.size();
    }

    /** Approved billable time that is not on an invoice yet, per project (the "ready to invoice" list). */
    @Transactional
    public List<ProjectSummaryDto> readyToInvoice() {
        on();
        return projects(null, null, false).stream().filter(p -> p.unbilledAmount().signum() > 0 && !"WORK_ORDER".equals(p.sourceType()) && !"MATTER".equals(p.sourceType()))
                .sorted(Comparator.comparing(ProjectSummaryDto::unbilledAmount).reversed()).toList();
    }
}
