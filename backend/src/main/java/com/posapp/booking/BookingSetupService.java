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
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public BookingSetupService(BookingSettingsRepository settingsRepository, BookableServiceRepository serviceRepository,
                               BookableServiceStaffRepository serviceStaffRepository, BookableResourceRepository resourceRepository,
                               BookingBlockRepository blockRepository, AppointmentRepository appointmentRepository, UserRepository userRepository,
                               CatalogService catalogService, ModuleService moduleService, AuditLogService auditLogService) {
        this.settingsRepository = settingsRepository;
        this.serviceRepository = serviceRepository;
        this.serviceStaffRepository = serviceStaffRepository;
        this.resourceRepository = resourceRepository;
        this.blockRepository = blockRepository;
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
                s.getPublicTitle(), s.getPublicIntro(), s.getPaymentInstructions());
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
}
