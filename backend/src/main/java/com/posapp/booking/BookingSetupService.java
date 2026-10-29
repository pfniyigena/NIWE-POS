package com.posapp.booking;

import com.posapp.audit.AuditLogService;
import com.posapp.booking.dto.BookingDtos.*;
import com.posapp.booking.dto.BookingRequests.*;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductRequest;
import com.posapp.common.ApiException;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Booking setup: settings, bookable services, rooms and equipment, and blocked time. */
@Service
public class BookingSetupService {

    private final BookingSettingsRepository settingsRepository;
    private final BookableServiceRepository serviceRepository;
    private final BookableServiceStaffRepository serviceStaffRepository;
    private final BookableResourceRepository resourceRepository;
    private final BookingBlockRepository blockRepository;
    private final BookingStaffHoursRepository hoursRepository;
    private final BookingStaffLeaveRepository leaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public BookingSetupService(BookingSettingsRepository settingsRepository, BookableServiceRepository serviceRepository,
                               BookableServiceStaffRepository serviceStaffRepository, BookableResourceRepository resourceRepository,
                               BookingBlockRepository blockRepository, BookingStaffHoursRepository hoursRepository, BookingStaffLeaveRepository leaveRepository,
                               AppointmentRepository appointmentRepository, UserRepository userRepository,
                               CatalogService catalogService, ModuleService moduleService, AuditLogService auditLogService) {
        this.settingsRepository = settingsRepository;
        this.serviceRepository = serviceRepository;
        this.serviceStaffRepository = serviceStaffRepository;
        this.resourceRepository = resourceRepository;
        this.blockRepository = blockRepository;
        this.hoursRepository = hoursRepository;
        this.leaveRepository = leaveRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.catalogService = catalogService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    void on() {
        moduleService.requireEnabled(ModuleKey.BOOKING);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String clean(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    static String name(AppUser u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }

    // ------------------------------------------------------------------ settings

    @Transactional
    public BookingSettings settingsEntity() {
        List<BookingSettings> all = settingsRepository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return settingsRepository.save(new BookingSettings());
    }

    static Set<DayOfWeek> workDays(BookingSettings s) {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String token : s.getWorkDays().split(",")) {
            String t = token.trim().toUpperCase();
            if (t.length() < 3) continue;
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(t.substring(0, 3))) out.add(d);
            }
        }
        return out;
    }

    @Transactional
    public SettingsDto settings() {
        on();
        return toDto(settingsEntity());
    }

    static SettingsDto toDto(BookingSettings s) {
        return new SettingsDto(s.getDayStart(), s.getDayEnd(), s.getWorkDays(), s.getSlotStepMinutes(), s.getMinNoticeMinutes(), s.getMaxAdvanceDays(),
                s.getHoldMinutes(), s.getCancelNoticeHours(), s.getMaxPublicPerPhonePerDay(), s.isPublicEnabled(), s.isEmailConfirmations(),
                s.getPublicTitle(), s.getPublicIntro(), s.getPaymentInstructions(), s.isReminderEnabled(), s.getReminderHours(), s.isAllowGuestReschedule(),
                s.getMaxGuestReschedules());
    }

    @Transactional
    public SettingsDto updateSettings(SettingsRequest r) {
        on();
        BookingSettings s = settingsEntity();
        if (r.dayStart() != null) s.setDayStart(r.dayStart());
        if (r.dayEnd() != null) s.setDayEnd(r.dayEnd());
        if (!s.getDayEnd().isAfter(s.getDayStart())) throw ApiException.badRequest("The closing time must be after the opening time.");
        if (r.workDays() != null) {
            String days = r.workDays().trim().toUpperCase();
            BookingSettings probe = new BookingSettings();
            probe.setWorkDays(days);
            if (days.isEmpty() || workDays(probe).isEmpty()) throw ApiException.badRequest("Choose at least one working day.");
            s.setWorkDays(days);
        }
        if (r.slotStepMinutes() != null) {
            if (r.slotStepMinutes() < 5 || r.slotStepMinutes() > 240) throw ApiException.badRequest("The slot step must be between 5 and 240 minutes.");
            s.setSlotStepMinutes(r.slotStepMinutes());
        }
        if (r.minNoticeMinutes() != null) s.setMinNoticeMinutes(Math.max(0, r.minNoticeMinutes()));
        if (r.maxAdvanceDays() != null) s.setMaxAdvanceDays(Math.min(730, Math.max(1, r.maxAdvanceDays())));
        if (r.holdMinutes() != null) s.setHoldMinutes(Math.min(10080, Math.max(5, r.holdMinutes())));
        if (r.cancelNoticeHours() != null) s.setCancelNoticeHours(Math.max(0, r.cancelNoticeHours()));
        if (r.maxPublicPerPhonePerDay() != null) s.setMaxPublicPerPhonePerDay(Math.min(50, Math.max(1, r.maxPublicPerPhonePerDay())));
        if (r.publicEnabled() != null) s.setPublicEnabled(r.publicEnabled());
        if (r.emailConfirmations() != null) s.setEmailConfirmations(r.emailConfirmations());
        if (r.reminderEnabled() != null) s.setReminderEnabled(r.reminderEnabled());
        if (r.reminderHours() != null) {
            if (r.reminderHours() < 1 || r.reminderHours() > 168) throw ApiException.badRequest("Send the reminder between 1 and 168 hours before the visit.");
            s.setReminderHours(r.reminderHours());
        }
        if (r.allowGuestReschedule() != null) s.setAllowGuestReschedule(r.allowGuestReschedule());
        if (r.maxGuestReschedules() != null) {
            if (r.maxGuestReschedules() < 0 || r.maxGuestReschedules() > 10) throw ApiException.badRequest("A guest can move a booking between 0 and 10 times.");
            s.setMaxGuestReschedules(r.maxGuestReschedules());
        }
        s.setPublicTitle(clean(r.publicTitle()));
        s.setPublicIntro(clean(r.publicIntro()));
        s.setPaymentInstructions(clean(r.paymentInstructions()));
        settingsRepository.save(s);
        auditLogService.record("BOOKING_SETTINGS_UPDATED", "Updated booking settings");
        return toDto(s);
    }

    /** The service product that appointment invoices are raised under; "Appointments and bookings" is created the first time. */
    @Transactional
    public Product billingProduct() {
        BookingSettings s = settingsEntity();
        if (s.getBillingProductId() != null) {
            return catalogService.getProductOrThrow(s.getBillingProductId());
        }
        ProductRequest req = new ProductRequest();
        req.setName("Appointments and bookings");
        req.setDescription("Booked services");
        req.setUnitPrice(BigDecimal.ZERO);
        req.setCostPrice(BigDecimal.ZERO);
        req.setStockQuantity(BigDecimal.ZERO);
        req.setTrackStock(false);
        req.setService(true);
        req.setActive(true);
        Product created = catalogService.getProductOrThrow(catalogService.createProduct(req).id());
        s.setBillingProductId(created.getId());
        settingsRepository.save(s);
        return created;
    }

    // ------------------------------------------------------------------ staff

    /** Active users, for the pickers. */
    @Transactional(readOnly = true)
    public List<Option> staffOptions() {
        on();
        return userRepository.findByActiveTrue().stream().map(u -> new Option(u.getId(), name(u)))
                .sorted(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // ------------------------------------------------------------------ services

    static BigDecimal upfront(BookableService s) {
        return switch (s.getPaymentMode()) {
            case NONE -> BigDecimal.ZERO;
            case FULL -> s.getPrice();
            case DEPOSIT -> s.getPrice().multiply(BigDecimal.valueOf(s.getDepositPercent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        };
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> services(boolean activeOnly) {
        on();
        List<BookableService> list = activeOnly ? serviceRepository.findByActiveTrueOrderByNameAsc() : serviceRepository.findAllByOrderByNameAsc();
        if (list.isEmpty()) return List.of();
        Map<Long, String> names = userRepository.findAll().stream().collect(Collectors.toMap(AppUser::getId, BookingSetupService::name, (a, b) -> a));
        Map<Long, List<BookableServiceStaff>> staff = serviceStaffRepository.findByServiceIdIn(list.stream().map(BookableService::getId).toList()).stream()
                .collect(Collectors.groupingBy(BookableServiceStaff::getServiceId));
        return list.stream().map(s -> toDto(s, staff.getOrDefault(s.getId(), List.of()).stream()
                .map(x -> new Option(x.getUserId(), names.getOrDefault(x.getUserId(), "User " + x.getUserId()))).toList())).toList();
    }

    @Transactional(readOnly = true)
    public ServiceDto service(Long id) {
        on();
        BookableService s = findService(id);
        Map<Long, String> names = userRepository.findAll().stream().collect(Collectors.toMap(AppUser::getId, BookingSetupService::name, (a, b) -> a));
        return toDto(s, serviceStaffRepository.findByServiceId(id).stream()
                .map(x -> new Option(x.getUserId(), names.getOrDefault(x.getUserId(), "User " + x.getUserId()))).toList());
    }

    private ServiceDto toDto(BookableService s, List<Option> staff) {
        return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getBufferMinutes(), s.getPrice(), s.getPaymentMode(),
                s.getDepositPercent(), upfront(s), s.getResourceKind(), s.getColor(), s.isActive(), s.isPublicVisible(), staff,
                appointmentRepository.countByServiceId(s.getId()));
    }

    BookableService findService(Long id) {
        return serviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service not found: " + id));
    }

    @Transactional
    public ServiceDto saveService(Long id, ServiceRequest r) {
        on();
        if (!notBlank(r.name())) throw ApiException.badRequest("Give the service a name.");
        BookableService s = id == null ? new BookableService() : findService(id);
        int duration = r.durationMinutes() == null ? 30 : r.durationMinutes();
        if (duration < 5 || duration > 1440) throw ApiException.badRequest("The duration must be between 5 minutes and 24 hours.");
        int buffer = r.bufferMinutes() == null ? 0 : r.bufferMinutes();
        if (buffer < 0 || buffer > 240) throw ApiException.badRequest("The buffer must be between 0 and 240 minutes.");
        BigDecimal price = r.price() == null ? BigDecimal.ZERO : r.price().setScale(2, RoundingMode.HALF_UP);
        if (price.signum() < 0) throw ApiException.badRequest("The price cannot be negative.");
        PaymentMode mode = r.paymentMode() == null ? PaymentMode.NONE : r.paymentMode();
        if (mode != PaymentMode.NONE && price.signum() <= 0) throw ApiException.badRequest("A service that takes a deposit or payment needs a price.");
        int pct = r.depositPercent() == null ? 30 : r.depositPercent();
        if (mode == PaymentMode.DEPOSIT && (pct < 1 || pct > 100)) throw ApiException.badRequest("The deposit must be between 1% and 100% of the price.");
        List<Long> staffIds = r.staffIds() == null ? List.of() : r.staffIds().stream().distinct().toList();
        if (staffIds.isEmpty() && !notBlank(r.resourceKind())) {
            throw ApiException.badRequest("Choose who performs this service, or which kind of room or equipment it needs. Otherwise any number of people could book the same time.");
        }
        for (Long uid : staffIds) {
            userRepository.findById(uid).orElseThrow(() -> ApiException.notFound("User not found: " + uid));
        }
        s.setName(r.name().trim());
        s.setDescription(clean(r.description()));
        s.setDurationMinutes(duration);
        s.setBufferMinutes(buffer);
        s.setPrice(price);
        s.setPaymentMode(mode);
        s.setDepositPercent(pct);
        s.setResourceKind(clean(r.resourceKind()));
        s.setColor(clean(r.color()));
        s.setActive(r.active() == null || r.active());
        s.setPublicVisible(r.publicVisible() == null || r.publicVisible());
        BookableService saved = serviceRepository.save(s);
        serviceStaffRepository.deleteAll(serviceStaffRepository.findByServiceId(saved.getId()));
        serviceStaffRepository.flush();
        for (Long uid : staffIds) {
            BookableServiceStaff link = new BookableServiceStaff();
            link.setServiceId(saved.getId());
            link.setUserId(uid);
            serviceStaffRepository.save(link);
        }
        auditLogService.record(id == null ? "BOOKING_SERVICE_CREATED" : "BOOKING_SERVICE_UPDATED", (id == null ? "Created" : "Updated") + " bookable service " + saved.getName(),
                "BookableService", saved.getId());
        return service(saved.getId());
    }

    @Transactional
    public void deleteService(Long id) {
        on();
        BookableService s = findService(id);
        if (appointmentRepository.countByServiceId(id) > 0) {
            throw ApiException.conflict("This service has appointments. Mark it inactive instead of deleting it.");
        }
        serviceStaffRepository.deleteAll(serviceStaffRepository.findByServiceId(id));
        serviceRepository.delete(s);
        auditLogService.record("BOOKING_SERVICE_DELETED", "Deleted bookable service " + s.getName(), "BookableService", id);
    }

    // ------------------------------------------------------------------ resources

    @Transactional(readOnly = true)
    public List<ResourceDto> resources() {
        on();
        return resourceRepository.findAllByOrderByKindAscNameAsc().stream().map(this::toDto).toList();
    }

    private ResourceDto toDto(BookableResource r) {
        return new ResourceDto(r.getId(), r.getName(), r.getKind(), r.getCapacity(), r.getNotes(), r.isActive(), r.getBranchId(),
                appointmentRepository.countByResourceId(r.getId()));
    }

    @Transactional
    public ResourceDto saveResource(Long id, ResourceRequest r) {
        on();
        if (!notBlank(r.name())) throw ApiException.badRequest("Give the room or equipment a name.");
        BookableResource res = id == null ? new BookableResource() : resourceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Resource not found: " + id));
        res.setName(r.name().trim());
        res.setKind(notBlank(r.kind()) ? r.kind().trim() : "Room");
        res.setCapacity(r.capacity());
        res.setNotes(clean(r.notes()));
        res.setActive(r.active() == null || r.active());
        res.setBranchId(r.branchId());
        BookableResource saved = resourceRepository.save(res);
        auditLogService.record(id == null ? "BOOKING_RESOURCE_CREATED" : "BOOKING_RESOURCE_UPDATED", (id == null ? "Created" : "Updated") + " bookable resource " + saved.getName(),
                "BookableResource", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteResource(Long id) {
        on();
        BookableResource res = resourceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Resource not found: " + id));
        if (appointmentRepository.countByResourceId(id) > 0) {
            throw ApiException.conflict("This room or equipment has appointments. Mark it inactive instead of deleting it.");
        }
        resourceRepository.delete(res);
        auditLogService.record("BOOKING_RESOURCE_DELETED", "Deleted bookable resource " + res.getName(), "BookableResource", id);
    }

    // ------------------------------------------------------------------ blocks

    @Transactional(readOnly = true)
    public List<BlockDto> blocks(LocalDate from, LocalDate to) {
        on();
        LocalDate start = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate end = to != null ? to : start.plusDays(120);
        return blockDtos(blockRepository.findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(end.plusDays(1).atStartOfDay(), start.atStartOfDay()));
    }

    List<BlockDto> blockDtos(List<BookingBlock> blocks) {
        if (blocks.isEmpty()) return List.of();
        Map<Long, String> users = userRepository.findAll().stream().collect(Collectors.toMap(AppUser::getId, BookingSetupService::name, (a, b) -> a));
        Map<Long, String> resources = resourceRepository.findAll().stream().collect(Collectors.toMap(BookableResource::getId, BookableResource::getName, (a, b) -> a));
        List<BlockDto> out = new ArrayList<>();
        for (BookingBlock b : blocks) {
            String target = switch (b.getTargetType()) {
                case ALL -> "Everyone";
                case STAFF -> users.getOrDefault(b.getTargetId(), "Staff " + b.getTargetId());
                case RESOURCE -> resources.getOrDefault(b.getTargetId(), "Resource " + b.getTargetId());
            };
            out.add(new BlockDto(b.getId(), b.getTargetType(), b.getTargetId(), target, b.getStartAt(), b.getEndAt(), b.getReason(), b.getCreatedByName()));
        }
        return out;
    }

    @Transactional
    public BlockDto addBlock(AppUser actor, BlockRequest r) {
        on();
        if (r.startAt() == null || r.endAt() == null || !r.endAt().isAfter(r.startAt())) throw ApiException.badRequest("The end must be after the start.");
        BlockTarget type = r.targetType() == null ? BlockTarget.ALL : r.targetType();
        if (type != BlockTarget.ALL && r.targetId() == null) throw ApiException.badRequest("Choose the person or the room/equipment to block.");
        if (type == BlockTarget.STAFF) userRepository.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("User not found."));
        if (type == BlockTarget.RESOURCE) resourceRepository.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("Resource not found."));
        BookingBlock b = new BookingBlock();
        b.setTargetType(type);
        b.setTargetId(type == BlockTarget.ALL ? null : r.targetId());
        b.setStartAt(r.startAt());
        b.setEndAt(r.endAt());
        b.setReason(clean(r.reason()));
        b.setCreatedByName(name(actor));
        BookingBlock saved = blockRepository.save(b);
        auditLogService.record("BOOKING_BLOCK_ADDED", "Blocked booking time " + r.startAt() + " to " + r.endAt() + (b.getReason() != null ? " — " + b.getReason() : ""),
                "BookingBlock", saved.getId());
        return blockDtos(List.of(saved)).get(0);
    }

    @Transactional
    public void deleteBlock(Long id) {
        on();
        BookingBlock b = blockRepository.findById(id).orElseThrow(() -> ApiException.notFound("Block not found: " + id));
        blockRepository.delete(b);
        auditLogService.record("BOOKING_BLOCK_REMOVED", "Removed a blocked period", "BookingBlock", id);
    }

    // ------------------------------------------------------------------ staff schedules

    /** One person's availability on one day. */
    record StaffWindow(boolean working, LocalTime start, LocalTime end, String note) {
    }

    private static final List<String> DAY_CODES = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private static String hhmm(LocalTime t) {
        return t.withSecond(0).withNano(0).toString();
    }

    /** Availability of each person on a day: leave and days off come first, then their own hours, otherwise the business opening hours. */
    Map<Long, StaffWindow> staffWindows(java.util.Collection<Long> userIds, LocalDate date) {
        Map<Long, StaffWindow> out = new java.util.LinkedHashMap<>();
        if (userIds.isEmpty()) return out;
        BookingSettings s = settingsEntity();
        Map<Long, List<BookingStaffHours>> hours = hoursRepository.findByUserIdIn(userIds).stream().collect(Collectors.groupingBy(BookingStaffHours::getUserId));
        Map<Long, BookingStaffLeave> leave = new java.util.HashMap<>();
        for (BookingStaffLeave l : leaveRepository.findByUserIdInAndFromDateLessThanEqualAndToDateGreaterThanEqual(userIds, date, date)) leave.putIfAbsent(l.getUserId(), l);
        for (Long id : userIds) {
            BookingStaffLeave l = leave.get(id);
            if (l != null) {
                out.put(id, new StaffWindow(false, null, null, "On leave" + (notBlank(l.getReason()) ? ": " + l.getReason() : "")));
                continue;
            }
            List<BookingStaffHours> rows = hours.get(id);
            if (rows == null || rows.isEmpty()) {
                boolean working = workDays(s).contains(date.getDayOfWeek());
                out.put(id, new StaffWindow(working, s.getDayStart(), s.getDayEnd(), working ? null : "Closed"));
                continue;
            }
            BookingStaffHours row = rows.stream().filter(h -> h.getWeekday() == date.getDayOfWeek().getValue()).findFirst().orElse(null);
            out.put(id, row == null ? new StaffWindow(false, null, null, "Day off") : new StaffWindow(true, row.getStartTime(), row.getEndTime(), null));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> schedules() {
        on();
        Set<Long> ids = new java.util.LinkedHashSet<>();
        serviceStaffRepository.findAll().forEach(x -> ids.add(x.getUserId()));
        List<BookingStaffHours> allHours = hoursRepository.findAll();
        allHours.forEach(h -> ids.add(h.getUserId()));
        List<BookingStaffLeave> allLeave = leaveRepository.findByToDateGreaterThanEqualOrderByFromDateAsc(LocalDate.now().minusDays(1));
        allLeave.forEach(l -> ids.add(l.getUserId()));
        List<ScheduleDto> out = new ArrayList<>();
        for (AppUser u : userRepository.findAllById(ids)) {
            if (!u.isActive()) continue;
            List<HoursDto> days = allHours.stream().filter(h -> h.getUserId().equals(u.getId())).sorted(Comparator.comparingInt(BookingStaffHours::getWeekday))
                    .map(h -> new HoursDto(DAY_CODES.get(h.getWeekday() - 1), hhmm(h.getStartTime()), hhmm(h.getEndTime()))).toList();
            List<LeaveDto> leave = allLeave.stream().filter(l -> l.getUserId().equals(u.getId())).map(BookingSetupService::toDto).toList();
            out.add(new ScheduleDto(u.getId(), name(u), !days.isEmpty(), days, leave));
        }
        out.sort(Comparator.comparing(x -> x.userName().toLowerCase()));
        return out;
    }

    private static LeaveDto toDto(BookingStaffLeave l) {
        return new LeaveDto(l.getId(), l.getFromDate(), l.getToDate(), l.getReason(), l.getCreatedByName());
    }

    @Transactional
    public ScheduleDto saveSchedule(Long userId, ScheduleRequest r) {
        on();
        AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        BookingSettings s = settingsEntity();
        hoursRepository.deleteByUserId(userId);
        hoursRepository.flush();
        if (Boolean.TRUE.equals(r.custom())) {
            if (r.days() == null || r.days().isEmpty()) throw ApiException.badRequest("Choose at least one working day, or switch to the business opening hours.");
            Set<Integer> seen = new java.util.HashSet<>();
            Set<DayOfWeek> open = workDays(s);
            for (DayHoursRequest d : r.days()) {
                int idx = d.day() == null ? -1 : DAY_CODES.indexOf(d.day().trim().toUpperCase());
                if (idx < 0) throw ApiException.badRequest("Unknown day: " + d.day());
                DayOfWeek dow = DayOfWeek.of(idx + 1);
                if (!seen.add(idx + 1)) throw ApiException.badRequest("The same day is listed twice: " + d.day());
                if (!open.contains(dow)) throw ApiException.badRequest(DAY_CODES.get(idx) + " is not a working day in the business opening hours (Booking settings).");
                if (d.start() == null || d.end() == null || !d.end().isAfter(d.start())) throw ApiException.badRequest("On " + DAY_CODES.get(idx) + ", the end must be after the start.");
                if (d.start().isBefore(s.getDayStart()) || d.end().isAfter(s.getDayEnd())) {
                    throw ApiException.badRequest("On " + DAY_CODES.get(idx) + ", the hours must be inside the business opening hours (" + hhmm(s.getDayStart()) + " to " + hhmm(s.getDayEnd()) + ").");
                }
                BookingStaffHours h = new BookingStaffHours();
                h.setUserId(userId);
                h.setWeekday(idx + 1);
                h.setStartTime(d.start());
                h.setEndTime(d.end());
                hoursRepository.save(h);
            }
        }
        auditLogService.record("BOOKING_SCHEDULE_UPDATED", name(user) + (Boolean.TRUE.equals(r.custom()) ? " now has their own working hours" : " now follows the business opening hours"), "User", userId);
        hoursRepository.flush();
        return schedules().stream().filter(x -> x.userId().equals(userId)).findFirst()
                .orElse(new ScheduleDto(userId, name(user), Boolean.TRUE.equals(r.custom()), List.of(), List.of()));
    }

    @Transactional
    public LeaveAddedDto addLeave(AppUser actor, Long userId, LeaveRequest r) {
        on();
        AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (r.fromDate() == null || r.toDate() == null) throw ApiException.badRequest("Enter the first and the last day.");
        if (r.toDate().isBefore(r.fromDate())) throw ApiException.badRequest("The last day cannot be before the first day.");
        if (r.toDate().isBefore(LocalDate.now().minusDays(1))) throw ApiException.badRequest("That leave is already in the past.");
        if (java.time.temporal.ChronoUnit.DAYS.between(r.fromDate(), r.toDate()) > 366) throw ApiException.badRequest("Leave can be at most a year long.");
        BookingStaffLeave l = new BookingStaffLeave();
        l.setUserId(userId);
        l.setFromDate(r.fromDate());
        l.setToDate(r.toDate());
        l.setReason(clean(r.reason()));
        l.setCreatedByName(name(actor));
        BookingStaffLeave saved = leaveRepository.save(l);
        long booked = appointmentRepository.findByStaffUserIdAndStartAtGreaterThanEqualOrderByStartAtAsc(userId, r.fromDate().atStartOfDay()).stream()
                .filter(a -> a.getStartAt().isBefore(r.toDate().plusDays(1).atStartOfDay()))
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING_PAYMENT).count();
        auditLogService.record("BOOKING_LEAVE_ADDED", name(user) + " is away " + r.fromDate() + " to " + r.toDate() + (l.getReason() != null ? " — " + l.getReason() : "")
                + (booked > 0 ? " (" + booked + " appointment(s) already booked in that period)" : ""), "User", userId);
        return new LeaveAddedDto(toDto(saved), booked);
    }

    @Transactional
    public void deleteLeave(Long id) {
        on();
        BookingStaffLeave l = leaveRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leave not found: " + id));
        leaveRepository.delete(l);
        auditLogService.record("BOOKING_LEAVE_REMOVED", "Removed a leave period " + l.getFromDate() + " to " + l.getToDate(), "User", l.getUserId());
    }
}
