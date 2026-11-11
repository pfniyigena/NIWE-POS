package com.posapp.booking;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.booking.dto.BookingDtos.*;
import com.posapp.booking.dto.BookingRequests.*;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.invoice.dto.InvoiceRequest;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appointments: which times are free, booking them without ever double-booking a person or a room, holding a slot while a deposit is
 * unpaid, matching the payment to an invoice, and the public booking page. Times are local to the business (the store time zone).
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static DateTimeFormatter whenFmt() { return I18n.pattern("d MMM yyyy HH:mm"); }
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Who is asking: a viewer sees every appointment, a manager changes any; anyone else works only their own. */
    public record Actor(AppUser user, boolean viewer, boolean manager) {
    }

    private final AppointmentRepository repository;
    private final BookingBlockRepository blockRepository;
    private final BookingSettingsRepository settingsRepository;
    private final BookableServiceStaffRepository serviceStaffRepository;
    private final BookableResourceRepository resourceRepository;
    private final BookingSetupService setup;
    private final BookingMailService mail;
    private final InvoiceService invoiceService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final BranchService branchService;
    private final StoreSettingsService storeSettingsService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public AppointmentService(AppointmentRepository repository, BookingBlockRepository blockRepository, BookingSettingsRepository settingsRepository,
                              BookableServiceStaffRepository serviceStaffRepository, BookableResourceRepository resourceRepository, BookingSetupService setup,
                              BookingMailService mail, InvoiceService invoiceService, CustomerRepository customerRepository, UserRepository userRepository,
                              BranchService branchService, StoreSettingsService storeSettingsService, ModuleService moduleService, AuditLogService auditLogService) {
        this.repository = repository;
        this.blockRepository = blockRepository;
        this.settingsRepository = settingsRepository;
        this.serviceStaffRepository = serviceStaffRepository;
        this.resourceRepository = resourceRepository;
        this.setup = setup;
        this.mail = mail;
        this.invoiceService = invoiceService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.branchService = branchService;
        this.storeSettingsService = storeSettingsService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    private void on() {
        moduleService.requireEnabled(ModuleKey.BOOKING);
    }

    private ZoneId zone() {
        try {
            String id = storeSettingsService.getSettingsEntity().getTimeZone();
            if (id != null && !id.isBlank()) return ZoneId.of(id.trim());
        } catch (Exception ignored) {
            // fall through to the server zone
        }
        return ZoneId.systemDefault();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String clean(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** Does this appointment currently take its person and room? Unpaid holds count until they expire. */
    private static boolean occupies(Appointment a, Instant nowInstant) {
        return switch (a.getStatus()) {
            case CONFIRMED, COMPLETED -> true;
            case PENDING_PAYMENT -> a.getHoldExpiresAt() == null || a.getHoldExpiresAt().isAfter(nowInstant);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ availability

    private record Candidates(List<Option> staff, List<Option> resources, boolean staffRequired, boolean resourceRequired) {
    }

    private Candidates candidates(BookableService svc, Long staffFilter) {
        List<Long> staffIds = serviceStaffRepository.findByServiceId(svc.getId()).stream().map(BookableServiceStaff::getUserId).toList();
        List<Option> staff = new ArrayList<>();
        for (AppUser u : userRepository.findAllById(staffIds)) {
            if (u.isActive() && (staffFilter == null || staffFilter.equals(u.getId()))) staff.add(new Option(u.getId(), BookingSetupService.name(u)));
        }
        staff.sort(Comparator.comparing(Option::id));
        List<Option> resources = new ArrayList<>();
        boolean resourceRequired = notBlank(svc.getResourceKind());
        if (resourceRequired) {
            for (BookableResource r : resourceRepository.findByActiveTrueOrderByNameAsc()) {
                if (r.getKind().equalsIgnoreCase(svc.getResourceKind().trim())) resources.add(new Option(r.getId(), r.getName()));
            }
        }
        return new Candidates(staff, resources, !staffIds.isEmpty(), resourceRequired);
    }

    private List<SlotDto> slots(BookableService svc, LocalDate date, Long staffFilter, Long ignoreId, boolean publicRules) {
        BookingSettings settings = setup.settingsEntity();
        if (!BookingSetupService.workDays(settings).contains(date.getDayOfWeek())) return List.of();
        LocalDateTime now = now();
        if (date.isBefore(now.toLocalDate())) return List.of();
        if (publicRules && date.isAfter(now.toLocalDate().plusDays(settings.getMaxAdvanceDays()))) return List.of();
        LocalDateTime earliest = publicRules ? now.plusMinutes(settings.getMinNoticeMinutes()) : now;
        Candidates c = candidates(svc, staffFilter);
        if ((c.staffRequired() && c.staff().isEmpty()) || (c.resourceRequired() && c.resources().isEmpty())) return List.of();

        LocalDateTime dayFrom = date.atStartOfDay();
        LocalDateTime dayTo = date.plusDays(1).atStartOfDay();
        Instant nowInstant = Instant.now();
        List<Appointment> busy = repository.findByStartAtLessThanAndBusyUntilGreaterThanOrderByStartAtAsc(dayTo, dayFrom).stream()
                .filter(a -> occupies(a, nowInstant) && (ignoreId == null || !ignoreId.equals(a.getId()))).toList();
        List<BookingBlock> blocks = blockRepository.findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(dayTo, dayFrom);

        Map<Long, BookingSetupService.StaffWindow> windows = setup.staffWindows(c.staff().stream().map(Option::id).toList(), date);
        List<SlotDto> out = new ArrayList<>();
        LocalTime t = settings.getDayStart();
        int step = settings.getSlotStepMinutes();
        while (true) {
            LocalDateTime start = date.atTime(t);
            LocalDateTime end = start.plusMinutes(svc.getDurationMinutes());
            LocalDateTime busyUntil = end.plusMinutes(svc.getBufferMinutes());
            if (end.isAfter(date.atTime(settings.getDayEnd())) || !end.toLocalDate().equals(date)) break;
            if (!start.isBefore(earliest) && blocks.stream().noneMatch(b -> b.getTargetType() == BlockTarget.ALL && overlaps(start, end, b.getStartAt(), b.getEndAt()))) {
                List<Option> freeStaff = c.staff().stream().filter(o -> inWindow(windows.get(o.id()), start, end) && isFree(o.id(), true, start, busyUntil, busy, blocks)).toList();
                List<Option> freeRes = c.resources().stream().filter(o -> isFree(o.id(), false, start, busyUntil, busy, blocks)).toList();
                boolean ok = (!c.staffRequired() || !freeStaff.isEmpty()) && (!c.resourceRequired() || !freeRes.isEmpty());
                if (ok) out.add(new SlotDto(t.toString(), freeStaff, freeRes));
            }
            LocalTime next = t.plusMinutes(step);
            if (next.isBefore(t)) break;
            t = next;
        }
        return out;
    }

    /** Is the person working for the whole visit (their own hours, or the opening hours; not on leave)? */
    private static boolean inWindow(BookingSetupService.StaffWindow w, LocalDateTime start, LocalDateTime end) {
        if (w == null) return true;
        if (!w.working() || w.start() == null || w.end() == null) return false;
        return !start.toLocalTime().isBefore(w.start()) && !end.toLocalTime().isAfter(w.end());
    }

    private static boolean isFree(Long id, boolean staff, LocalDateTime start, LocalDateTime busyUntil, List<Appointment> busy, List<BookingBlock> blocks) {
        for (Appointment a : busy) {
            Long who = staff ? a.getStaffUserId() : a.getResourceId();
            if (id.equals(who) && overlaps(start, busyUntil, a.getStartAt(), a.getBusyUntil())) return false;
        }
        BlockTarget type = staff ? BlockTarget.STAFF : BlockTarget.RESOURCE;
        for (BookingBlock b : blocks) {
            if (b.getTargetType() == type && id.equals(b.getTargetId()) && overlaps(start, busyUntil, b.getStartAt(), b.getEndAt())) return false;
        }
        return true;
    }

    @Transactional
    public List<SlotDto> availability(Long serviceId, LocalDate date, Long staffId, Long ignoreAppointmentId) {
        on();
        return slots(setup.findService(serviceId), date, staffId, ignoreAppointmentId, false);
    }

    // ------------------------------------------------------------------ booking

    private record Chosen(Option staff, Option resource) {
    }

    /** Re-checks the wanted time against the live bookings and picks the person and room. Call with the settings row locked. */
    private Chosen choose(BookableService svc, LocalDateTime start, Long staffId, Long resourceId, Long ignoreId, boolean publicRules) {
        if (start == null) throw ApiException.badRequest(I18n.t("Choose a time."));
        if (!svc.isActive()) throw ApiException.badRequest(I18n.t("This service is not available for booking."));
        SlotDto slot = slots(svc, start.toLocalDate(), staffId, ignoreId, publicRules).stream()
                .filter(s -> LocalTime.parse(s.time()).equals(start.toLocalTime())).findFirst()
                .orElseThrow(() -> ApiException.conflict(I18n.t("That time is not available (taken, outside opening or working hours, on leave, in the past or blocked). Please pick another.")));
        Option staff = null;
        if (!slot.staff().isEmpty() || staffId != null) {
            staff = slot.staff().stream().filter(o -> staffId == null || o.id().equals(staffId)).findFirst()
                    .orElseThrow(() -> ApiException.conflict(I18n.t("That person is not free at this time.")));
        }
        Option resource = null;
        if (!slot.resources().isEmpty() || resourceId != null) {
            resource = slot.resources().stream().filter(o -> resourceId == null || o.id().equals(resourceId)).findFirst()
                    .orElseThrow(() -> ApiException.conflict(I18n.t("That room or equipment is not free at this time.")));
        }
        return new Chosen(staff, resource);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            if (repository.findByConfirmationCode(sb.toString()).isEmpty()) return sb.toString();
        }
        throw ApiException.conflict(I18n.t("Could not create a booking code. Please try again."));
    }

    @Transactional
    public AppointmentDto book(Actor actor, BookRequest r) {
        on();
        if (r.serviceId() == null) throw ApiException.badRequest(I18n.t("Choose a service."));
        if (!notBlank(r.guestName()) && r.customerId() == null) throw ApiException.badRequest(I18n.t("Enter the customer's name or choose a customer."));
        String name = clean(r.guestName());
        String phone = clean(r.guestPhone());
        String email = clean(r.guestEmail());
        Long customerId = r.customerId();
        if (customerId != null) {
            Customer c = customerRepository.findById(customerId).orElseThrow(() -> ApiException.notFound(I18n.t("Customer not found.")));
            if (name == null) name = c.getName();
            if (phone == null) phone = c.getPhone();
            if (email == null) email = c.getEmail();
        }
        Appointment a = create(setup.findService(r.serviceId()), r.start(), r.staffUserId(), r.resourceId(), customerId, name, phone, email, clean(r.notes()),
                AppointmentSource.STAFF, Boolean.TRUE.equals(r.skipPayment()), false, BookingSetupService.name(actor.user()));
        return toDto(a, actor);
    }

    private Appointment create(BookableService svc, LocalDateTime start, Long staffId, Long resourceId, Long customerId, String guestName, String phone,
                               String email, String notes, AppointmentSource source, boolean skipPayment, boolean publicRules, String createdBy) {
        setup.settingsEntity();
        settingsRepository.lockAll();
        BookingSettings settings = setup.settingsEntity();
        Chosen chosen = choose(svc, start, staffId, resourceId, null, publicRules);
        Appointment a = new Appointment();
        a.setNumber("AP-TMP-" + System.nanoTime());
        a.setConfirmationCode(newCode());
        a.setSource(source);
        a.setServiceId(svc.getId());
        a.setServiceName(svc.getName());
        a.setServiceColor(svc.getColor());
        a.setStartAt(start);
        a.setEndAt(start.plusMinutes(svc.getDurationMinutes()));
        a.setBusyUntil(a.getEndAt().plusMinutes(svc.getBufferMinutes()));
        a.setDurationMinutes(svc.getDurationMinutes());
        if (chosen.staff() != null) {
            a.setStaffUserId(chosen.staff().id());
            a.setStaffName(chosen.staff().name());
        }
        if (chosen.resource() != null) {
            a.setResourceId(chosen.resource().id());
            a.setResourceName(chosen.resource().name());
            resourceRepository.findById(chosen.resource().id()).ifPresent(res -> a.setBranchId(res.getBranchId()));
        }
        a.setCustomerId(customerId);
        a.setGuestName(guestName);
        a.setGuestPhone(phone);
        a.setGuestEmail(email);
        a.setNotes(notes);
        a.setPrice(svc.getPrice());
        a.setPaymentMode(svc.getPaymentMode());
        a.setAmountDue(BookingSetupService.upfront(svc));
        a.setCreatedByName(createdBy);
        boolean needsPayment = svc.getPaymentMode() != PaymentMode.NONE && svc.getPrice().signum() > 0;
        a.setStatus(needsPayment && !skipPayment ? AppointmentStatus.PENDING_PAYMENT : AppointmentStatus.CONFIRMED);
        if (a.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            a.setHoldExpiresAt(Instant.now().plusSeconds(settings.getHoldMinutes() * 60L));
        }
        Appointment saved = repository.save(a);
        saved.setNumber(branchService.prefixedNumber(saved.getBranchId(), String.format("AP-%05d", saved.getId())));
        if (needsPayment) {
            raiseInvoice(saved, settings);
        }
        repository.save(saved);
        auditLogService.record("APPOINTMENT_BOOKED", "Booked " + saved.getNumber() + " — " + saved.getServiceName() + " for " + saved.getGuestName() + " on " + saved.getStartAt().format(whenFmt()),
                "Appointment", saved.getId());
        if (settings.isEmailConfirmations()) {
            if (saved.getStatus() == AppointmentStatus.PENDING_PAYMENT) mail.awaitingPayment(saved, settings.getPaymentInstructions());
            else mail.confirmed(saved);
        }
        return saved;
    }

    private Customer resolveCustomer(Appointment a) {
        if (a.getCustomerId() != null) {
            return customerRepository.findById(a.getCustomerId()).orElseThrow(() -> ApiException.notFound(I18n.t("Customer not found.")));
        }
        Customer found = null;
        if (notBlank(a.getGuestEmail())) found = customerRepository.findFirstByEmailIgnoreCase(a.getGuestEmail()).orElse(null);
        if (found == null && notBlank(a.getGuestPhone())) found = customerRepository.findFirstByPhone(a.getGuestPhone()).orElse(null);
        if (found == null) {
            Customer c = new Customer();
            c.setName(a.getGuestName());
            c.setPhone(a.getGuestPhone());
            c.setEmail(a.getGuestEmail());
            found = customerRepository.save(c);
        }
        a.setCustomerId(found.getId());
        return found;
    }

    /** Issues an invoice for the full price of the appointment and ties it to the appointment. */
    private void raiseInvoice(Appointment a, BookingSettings settings) {
        Customer customer = resolveCustomer(a);
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(customer.getId());
        req.setNotes("Appointment " + a.getNumber() + " — " + a.getServiceName() + " on " + a.getStartAt().format(whenFmt()));
        if (a.getBranchId() != null) req.setBranchId(a.getBranchId());
        InvoiceRequest.LineEntry line = new InvoiceRequest.LineEntry();
        line.setProductId(setup.billingProduct().getId());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(a.getPrice());
        line.setDescription(a.getServiceName() + " — " + a.getStartAt().format(whenFmt()) + (a.getStaffName() != null ? " (" + a.getStaffName() + ")" : ""));
        req.setLines(List.of(line));
        InvoiceDto draft = invoiceService.createDraft(req);
        InvoiceDto issued = invoiceService.issue(draft.id());
        a.setInvoiceId(issued.id());
        a.setInvoiceNumber(issued.invoiceNumber());
    }

    @Transactional
    public AppointmentDto createInvoice(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (a.getInvoiceId() != null) throw ApiException.conflict(I18n.t("This appointment already has invoice ") + a.getInvoiceNumber() + ".");
        if (a.getPrice().signum() <= 0) throw ApiException.badRequest(I18n.t("This service has no price, so there is nothing to invoice."));
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.NO_SHOW) throw ApiException.conflict(I18n.t("This appointment was cancelled or missed."));
        raiseInvoice(a, setup.settingsEntity());
        repository.save(a);
        auditLogService.record("APPOINTMENT_INVOICED", "Invoiced appointment " + a.getNumber() + " on " + a.getInvoiceNumber(), "Appointment", id);
        return toDto(a, actor);
    }

    // ------------------------------------------------------------------ payment matching and holds

    /** Reads the payment on the appointment's invoice; confirms a held booking once the required amount is in, cancels it if the invoice was voided. */
    private boolean syncPayment(Appointment a) {
        if (a.getInvoiceId() == null) return false;
        InvoiceDto inv;
        try {
            inv = invoiceService.getById(a.getInvoiceId());
        } catch (ApiException e) {
            return false;
        }
        boolean changed = false;
        BigDecimal paid = inv.amountPaid() == null ? BigDecimal.ZERO : inv.amountPaid();
        if (paid.compareTo(a.getAmountPaid()) != 0) {
            a.setAmountPaid(paid);
            changed = true;
        }
        if (a.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            if ("VOID".equals(inv.status())) {
                applyCancel(a, "The invoice was voided.", false);
                changed = true;
            } else if (paid.compareTo(a.getAmountDue()) >= 0) {
                a.setStatus(AppointmentStatus.CONFIRMED);
                a.setHoldExpiresAt(null);
                changed = true;
                auditLogService.record("APPOINTMENT_CONFIRMED", "Appointment " + a.getNumber() + " confirmed — payment of " + paid + " received", "Appointment", a.getId());
                if (setup.settingsEntity().isEmailConfirmations()) mail.confirmed(a);
            }
        }
        if (changed) repository.save(a);
        return changed;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void sweep() {
        try {
            if (!moduleService.isEnabled(ModuleKey.BOOKING)) return;
            sweepInTransaction();
        } catch (Exception e) {
            log.warn("Booking sweep failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void sweepInTransaction() {
        Instant now = Instant.now();
        for (Appointment a : repository.findByStatus(AppointmentStatus.PENDING_PAYMENT)) {
            syncPayment(a);
            if (a.getStatus() == AppointmentStatus.PENDING_PAYMENT && a.getHoldExpiresAt() != null && a.getHoldExpiresAt().isBefore(now)) {
                applyCancel(a, "Not paid in time, so the held slot was released.", true);
                repository.save(a);
            }
        }
    }

    // ------------------------------------------------------------------ status changes

    private Appointment find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Appointment not found: ") + id));
    }

    private static boolean canWork(Actor actor, Appointment a) {
        return actor.manager() || (a.getStaffUserId() != null && a.getStaffUserId().equals(actor.user().getId()));
    }

    private void applyCancel(Appointment a, String reason, boolean notify) {
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelledAt(Instant.now());
        a.setHoldExpiresAt(null);
        String note = reason;
        if (a.getInvoiceId() != null) {
            try {
                InvoiceDto inv = invoiceService.getById(a.getInvoiceId());
                BigDecimal paid = inv.amountPaid() == null ? BigDecimal.ZERO : inv.amountPaid();
                a.setAmountPaid(paid);
                if (paid.signum() == 0) {
                    if ("DRAFT".equals(inv.status())) invoiceService.deleteDraft(inv.id());
                    else if ("ISSUED".equals(inv.status())) invoiceService.voidInvoice(inv.id(), I18n.t("Appointment ") + a.getNumber() + " cancelled");
                } else {
                    note = reason + I18n.t(" A payment of ") + paid.toPlainString() + I18n.t(" remains on invoice ") + a.getInvoiceNumber() + I18n.t(" — refund or keep it from the invoice.");
                }
            } catch (ApiException e) {
                note = reason + I18n.t(" The invoice ") + a.getInvoiceNumber() + I18n.t(" could not be voided automatically: ") + e.getMessage();
            }
        }
        a.setCancelReason(note.length() > 500 ? note.substring(0, 500) : note);
        auditLogService.record("APPOINTMENT_CANCELLED", "Cancelled appointment " + a.getNumber() + " — " + reason, "Appointment", a.getId());
        if (notify && setup.settingsEntity().isEmailConfirmations()) mail.cancelled(a);
    }

    @Transactional
    public AppointmentDto cancel(Actor actor, Long id, String reason) {
        on();
        if (!actor.manager()) throw ApiException.forbidden(I18n.t("Only a manager can cancel an appointment."));
        Appointment a = find(id);
        if (a.getStatus() != AppointmentStatus.PENDING_PAYMENT && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict(I18n.t("Only a pending or confirmed appointment can be cancelled."));
        }
        applyCancel(a, notBlank(reason) ? reason.trim() : "Cancelled by staff.", true);
        repository.save(a);
        return toDto(a, actor);
    }

    @Transactional
    public AppointmentDto confirmWithoutPayment(Actor actor, Long id) {
        on();
        if (!actor.manager()) throw ApiException.forbidden(I18n.t("Only a manager can do that."));
        Appointment a = find(id);
        if (a.getStatus() != AppointmentStatus.PENDING_PAYMENT) throw ApiException.conflict(I18n.t("This appointment is not waiting for payment."));
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setHoldExpiresAt(null);
        repository.save(a);
        auditLogService.record("APPOINTMENT_CONFIRMED", "Confirmed " + a.getNumber() + " without waiting for payment", "Appointment", id);
        if (setup.settingsEntity().isEmailConfirmations()) mail.confirmed(a);
        return toDto(a, actor);
    }

    @Transactional
    public AppointmentDto complete(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (!canWork(actor, a)) throw ApiException.forbidden(I18n.t("Only the person this is booked with, or a manager, can do that."));
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw ApiException.conflict(I18n.t("Only a confirmed appointment can be completed."));
        a.setStatus(AppointmentStatus.COMPLETED);
        repository.save(a);
        auditLogService.record("APPOINTMENT_COMPLETED", "Completed appointment " + a.getNumber(), "Appointment", id);
        return toDto(a, actor);
    }

    @Transactional
    public AppointmentDto noShow(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (!canWork(actor, a)) throw ApiException.forbidden(I18n.t("Only the person this is booked with, or a manager, can do that."));
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw ApiException.conflict(I18n.t("Only a confirmed appointment can be marked as a no-show."));
        a.setStatus(AppointmentStatus.NO_SHOW);
        repository.save(a);
        auditLogService.record("APPOINTMENT_NO_SHOW", "Marked appointment " + a.getNumber() + " as a no-show", "Appointment", id);
        return toDto(a, actor);
    }

    @Transactional
    public AppointmentDto reschedule(Actor actor, Long id, RescheduleRequest r) {
        on();
        if (!actor.manager()) throw ApiException.forbidden(I18n.t("Only a manager can move an appointment."));
        Appointment a = find(id);
        if (a.getStatus() != AppointmentStatus.PENDING_PAYMENT && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict(I18n.t("Only a pending or confirmed appointment can be moved."));
        }
        setup.settingsEntity();
        settingsRepository.lockAll();
        BookableService svc = setup.findService(a.getServiceId());
        Chosen chosen = choose(svc, r.start(), r.staffUserId() != null ? r.staffUserId() : a.getStaffUserId(), r.resourceId() != null ? r.resourceId() : a.getResourceId(), id, false);
        LocalDateTime oldStart = a.getStartAt();
        applyMove(a, svc, r.start(), chosen);
        repository.save(a);
        auditLogService.record("APPOINTMENT_MOVED", "Moved appointment " + a.getNumber() + " to " + a.getStartAt().format(whenFmt()), "Appointment", id);
        if (setup.settingsEntity().isEmailConfirmations() && a.getStatus() == AppointmentStatus.CONFIRMED) mail.moved(a, oldStart);
        return toDto(a, actor);
    }

    private void applyMove(Appointment a, BookableService svc, LocalDateTime start, Chosen chosen) {
        a.setStartAt(start);
        a.setEndAt(start.plusMinutes(a.getDurationMinutes()));
        a.setBusyUntil(a.getEndAt().plusMinutes(svc.getBufferMinutes()));
        if (chosen.staff() != null) {
            a.setStaffUserId(chosen.staff().id());
            a.setStaffName(chosen.staff().name());
        }
        if (chosen.resource() != null) {
            a.setResourceId(chosen.resource().id());
            a.setResourceName(chosen.resource().name());
        }
        a.setReminderSentAt(null);
    }

    @Transactional
    public AppointmentDto saveNotes(Actor actor, Long id, String notes) {
        on();
        Appointment a = find(id);
        if (!canWork(actor, a)) throw ApiException.forbidden(I18n.t("Only the person this is booked with, or a manager, can do that."));
        a.setNotes(clean(notes));
        repository.save(a);
        return toDto(a, actor);
    }

    // ------------------------------------------------------------------ reading

    private AppointmentDto toDto(Appointment a, Actor actor) {
        return new AppointmentDto(a.getId(), a.getNumber(), a.getConfirmationCode(), a.getStatus(), a.getSource(), a.getServiceId(), a.getServiceName(),
                a.getServiceColor(), a.getStartAt(), a.getEndAt(), a.getDurationMinutes(), a.getStaffUserId(), a.getStaffName(), a.getResourceId(),
                a.getResourceName(), a.getCustomerId(), a.getGuestName(), a.getGuestPhone(), a.getGuestEmail(), a.getNotes(), a.getPrice(), a.getPaymentMode(),
                a.getAmountDue(), a.getAmountPaid(), a.getInvoiceId(), a.getInvoiceNumber(), a.getHoldExpiresAt(), a.getCancelReason(), a.getCreatedAt(),
                a.getCreatedByName(), canWork(actor, a), actor.manager(), a.getReminderSentAt(), a.getRescheduleCount(), null);
    }

    private AppointmentDto toDto(Appointment a, Actor actor, GuestHistoryDto history) {
        AppointmentDto d = toDto(a, actor);
        return new AppointmentDto(d.id(), d.number(), d.confirmationCode(), d.status(), d.source(), d.serviceId(), d.serviceName(), d.serviceColor(), d.startAt(),
                d.endAt(), d.durationMinutes(), d.staffUserId(), d.staffName(), d.resourceId(), d.resourceName(), d.customerId(), d.guestName(), d.guestPhone(),
                d.guestEmail(), d.notes(), d.price(), d.paymentMode(), d.amountDue(), d.amountPaid(), d.invoiceId(), d.invoiceNumber(), d.holdExpiresAt(),
                d.cancelReason(), d.createdAt(), d.createdByName(), d.canWork(), d.canManage(), d.reminderSentAt(), d.rescheduleCount(), history);
    }

    /** What is already known about the guest (matched by customer or phone number), not counting this appointment. */
    private GuestHistoryDto history(Appointment a) {
        Map<Long, Appointment> others = new LinkedHashMap<>();
        if (a.getCustomerId() != null) repository.findByCustomerId(a.getCustomerId()).forEach(x -> others.put(x.getId(), x));
        if (notBlank(a.getGuestPhone())) repository.findByGuestPhone(a.getGuestPhone()).forEach(x -> others.put(x.getId(), x));
        others.remove(a.getId());
        LocalDateTime now = now();
        long completed = others.values().stream().filter(x -> x.getStatus() == AppointmentStatus.COMPLETED).count();
        long noShows = others.values().stream().filter(x -> x.getStatus() == AppointmentStatus.NO_SHOW).count();
        long cancelled = others.values().stream().filter(x -> x.getStatus() == AppointmentStatus.CANCELLED).count();
        long upcoming = others.values().stream().filter(x -> (x.getStatus() == AppointmentStatus.CONFIRMED || x.getStatus() == AppointmentStatus.PENDING_PAYMENT) && x.getStartAt().isAfter(now)).count();
        LocalDateTime last = others.values().stream().filter(x -> x.getStatus() == AppointmentStatus.COMPLETED).map(Appointment::getStartAt).max(Comparator.naturalOrder()).orElse(null);
        return new GuestHistoryDto(completed, noShows, cancelled, upcoming, last);
    }

    @Transactional
    public AppointmentDto get(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (!actor.viewer() && !canWork(actor, a)) throw ApiException.forbidden(I18n.t("This appointment is not yours."));
        syncPayment(a);
        return toDto(a, actor, history(a));
    }

    @Transactional
    public List<AppointmentDto> list(Actor actor, LocalDate from, LocalDate to, Long staffId, Long resourceId, AppointmentStatus status) {
        on();
        LocalDate start = from != null ? from : now().toLocalDate();
        LocalDate end = to != null ? to : start.plusDays(30);
        List<Appointment> list = repository.findByStartAtBetweenOrderByStartAtAsc(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        list.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING_PAYMENT).forEach(this::syncPayment);
        return list.stream().filter(a -> staffId == null || staffId.equals(a.getStaffUserId())).filter(a -> resourceId == null || resourceId.equals(a.getResourceId()))
                .filter(a -> status == null || a.getStatus() == status).map(a -> toDto(a, actor)).toList();
    }

    @Transactional
    public List<AppointmentDto> mine(Actor actor) {
        on();
        List<Appointment> list = repository.findByStaffUserIdAndStartAtGreaterThanEqualOrderByStartAtAsc(actor.user().getId(), now().toLocalDate().atStartOfDay());
        return list.stream().filter(a -> a.getStatus() != AppointmentStatus.CANCELLED).map(a -> toDto(a, actor)).toList();
    }

    @Transactional
    public DayDto day(Actor actor, LocalDate date) {
        on();
        BookingSettings settings = setup.settingsEntity();
        LocalDate d = date != null ? date : now().toLocalDate();
        List<Appointment> list = repository.findByStartAtBetweenOrderByStartAtAsc(d.atStartOfDay(), d.plusDays(1).atStartOfDay());
        list.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING_PAYMENT).forEach(this::syncPayment);
        List<Appointment> shown = list.stream().filter(a -> a.getStatus() != AppointmentStatus.CANCELLED).toList();
        // columns: everyone who performs a service, plus anyone with an appointment that day
        Map<Long, Option> staff = new LinkedHashMap<>();
        Set<Long> performers = serviceStaffRepository.findAll().stream().map(BookableServiceStaff::getUserId).collect(Collectors.toSet());
        userRepository.findAllById(performers).stream().filter(AppUser::isActive).sorted(Comparator.comparing(u -> BookingSetupService.name(u).toLowerCase()))
                .forEach(u -> staff.put(u.getId(), new Option(u.getId(), BookingSetupService.name(u))));
        shown.stream().filter(a -> a.getStaffUserId() != null).forEach(a -> staff.putIfAbsent(a.getStaffUserId(), new Option(a.getStaffUserId(), a.getStaffName())));
        Map<Long, Option> resources = new LinkedHashMap<>();
        resourceRepository.findByActiveTrueOrderByNameAsc().forEach(r -> resources.put(r.getId(), new Option(r.getId(), r.getName())));
        shown.stream().filter(a -> a.getResourceId() != null).forEach(a -> resources.putIfAbsent(a.getResourceId(), new Option(a.getResourceId(), a.getResourceName())));
        List<BlockDto> blocks = setup.blockDtos(blockRepository.findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(d.plusDays(1).atStartOfDay(), d.atStartOfDay()));
        List<StaffDayDto> staffDays = new ArrayList<>();
        setup.staffWindows(new ArrayList<>(staff.keySet()), d).forEach((id, w) -> staffDays.add(new StaffDayDto(id, w.working(),
                w.start() == null ? null : w.start().toString(), w.end() == null ? null : w.end().toString(), w.note())));
        return new DayDto(d.toString(), shown.stream().map(a -> toDto(a, actor)).toList(), blocks, new ArrayList<>(staff.values()), new ArrayList<>(resources.values()),
                settings.getDayStart().toString(), settings.getDayEnd().toString(), BookingSetupService.workDays(settings).contains(d.getDayOfWeek()), staffDays);
    }

    @Transactional
    public SummaryDto summary() {
        on();
        LocalDate today = now().toLocalDate();
        List<Appointment> week = repository.findByStartAtBetweenOrderByStartAtAsc(today.atStartOfDay(), today.plusDays(8).atStartOfDay());
        long todayCount = week.stream().filter(a -> a.getStartAt().toLocalDate().equals(today) && (a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING_PAYMENT || a.getStatus() == AppointmentStatus.COMPLETED)).count();
        long upcoming = week.stream().filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING_PAYMENT).count();
        long awaiting = repository.findByStatus(AppointmentStatus.PENDING_PAYMENT).size();
        LocalDate monthStart = today.withDayOfMonth(1);
        long noShows = repository.findByStartAtBetweenOrderByStartAtAsc(monthStart.atStartOfDay(), today.plusDays(1).atStartOfDay()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        return new SummaryDto(todayCount, upcoming, awaiting, noShows);
    }

    // ------------------------------------------------------------------ public page

    private static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    @Transactional
    public PublicInfoDto publicInfo() {
        on();
        BookingSettings s = setup.settingsEntity();
        if (!s.isPublicEnabled()) throw ApiException.notFound(I18n.t("Online booking is not available."));
        List<PublicServiceDto> services = setup.services(true).stream().filter(ServiceDto::publicVisible)
                .map(x -> new PublicServiceDto(x.id(), x.name(), x.description(), x.durationMinutes(), x.price(), x.paymentMode(), x.depositAmount(), x.staff())).toList();
        return new PublicInfoDto(s.getPublicTitle(), s.getPublicIntro(), s.getPaymentInstructions(), s.getMaxAdvanceDays(), s.getWorkDays(), services);
    }

    @Transactional
    public List<String> publicSlots(Long serviceId, LocalDate date, Long staffId) {
        on();
        BookingSettings s = setup.settingsEntity();
        if (!s.isPublicEnabled()) throw ApiException.notFound(I18n.t("Online booking is not available."));
        BookableService svc = setup.findService(serviceId);
        if (!svc.isActive() || !svc.isPublicVisible()) throw ApiException.notFound(I18n.t("Service not found."));
        return slots(svc, date, staffId, null, true).stream().map(SlotDto::time).toList();
    }

    @Transactional
    public PublicConfirmationDto publicBook(PublicBookRequest r) {
        on();
        BookingSettings s = setup.settingsEntity();
        if (!s.isPublicEnabled()) throw ApiException.notFound(I18n.t("Online booking is not available."));
        if (notBlank(r.website())) throw ApiException.badRequest(I18n.t("Could not make the booking."));
        if (r.serviceId() == null || r.date() == null || !notBlank(r.time())) throw ApiException.badRequest(I18n.t("Choose a service, a day and a time."));
        String name = clean(r.name());
        String phone = clean(r.phone());
        String email = clean(r.email());
        if (name == null || name.length() > 120) throw ApiException.badRequest(I18n.t("Enter your name."));
        if (phone == null || digits(phone).length() < 7 || digits(phone).length() > 15) throw ApiException.badRequest(I18n.t("Enter a phone number we can reach you on."));
        if (email != null && (email.length() > 160 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) throw ApiException.badRequest(I18n.t("That e-mail address does not look right."));
        String notes = clean(r.notes());
        if (notes != null && notes.length() > 500) notes = notes.substring(0, 500);
        LocalDateTime start;
        try {
            start = r.date().atTime(LocalTime.parse(r.time()));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(I18n.t("Choose a time from the list."));
        }
        BookableService svc = setup.findService(r.serviceId());
        if (!svc.isPublicVisible()) throw ApiException.notFound(I18n.t("Service not found."));
        long today = repository.countBySourceAndGuestPhoneAndCreatedAtGreaterThanEqual(AppointmentSource.PUBLIC, phone, Instant.now().minusSeconds(24 * 3600L));
        if (today >= s.getMaxPublicPerPhonePerDay()) {
            throw ApiException.conflict(I18n.t("There are already ") + today + I18n.t(" bookings from this phone number today. Please call us to book more."));
        }
        Appointment a = create(svc, start, r.staffUserId(), null, null, name, phone, email, notes, AppointmentSource.PUBLIC, false, true, "Online booking");
        return publicView(a, s);
    }

    private PublicConfirmationDto publicView(Appointment a, BookingSettings s) {
        boolean open = a.getStatus() == AppointmentStatus.PENDING_PAYMENT || a.getStatus() == AppointmentStatus.CONFIRMED;
        boolean canCancel = open && a.getStartAt().minusHours(s.getCancelNoticeHours()).isAfter(now());
        boolean canReschedule = canCancel && s.isAllowGuestReschedule() && a.getRescheduleCount() < s.getMaxGuestReschedules();
        return new PublicConfirmationDto(a.getConfirmationCode(), a.getNumber(), a.getStatus(), a.getServiceName(), a.getStartAt(), a.getEndAt(), a.getStaffName(),
                a.getPrice(), a.getAmountDue(), a.getAmountPaid(), a.getInvoiceNumber(), a.getHoldExpiresAt(),
                a.getStatus() == AppointmentStatus.PENDING_PAYMENT ? s.getPaymentInstructions() : null, canCancel, canReschedule);
    }

    private Appointment byCodeAndPhone(String code, String phone) {
        Appointment a = repository.findByConfirmationCode(code == null ? "" : code.trim().toUpperCase()).orElse(null);
        if (a == null || a.getGuestPhone() == null || !digits(a.getGuestPhone()).equals(digits(phone))) {
            throw ApiException.notFound(I18n.t("No booking found with that code and phone number."));
        }
        return a;
    }

    @Transactional
    public PublicConfirmationDto publicLookup(String code, String phone) {
        on();
        Appointment a = byCodeAndPhone(code, phone);
        syncPayment(a);
        return publicView(a, setup.settingsEntity());
    }

    @Transactional
    public PublicConfirmationDto publicCancel(String code, String phone) {
        on();
        Appointment a = byCodeAndPhone(code, phone);
        BookingSettings s = setup.settingsEntity();
        if (a.getStatus() != AppointmentStatus.PENDING_PAYMENT && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict(I18n.t("This booking can no longer be cancelled."));
        }
        if (!a.getStartAt().minusHours(s.getCancelNoticeHours()).isAfter(now())) {
            throw ApiException.conflict(I18n.t("It is too late to cancel online (less than ") + s.getCancelNoticeHours() + I18n.t(" hours to go). Please call us."));
        }
        applyCancel(a, "Cancelled by the guest.", true);
        repository.save(a);
        return publicView(a, s);
    }

    // ------------------------------------------------------------------ guests moving their own booking

    private void requireGuestMovable(Appointment a, BookingSettings s) {
        if (a.getStatus() != AppointmentStatus.PENDING_PAYMENT && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict(I18n.t("This booking can no longer be moved."));
        }
        if (!s.isAllowGuestReschedule()) throw ApiException.conflict(I18n.t("Bookings cannot be moved online. Please call us."));
        if (a.getRescheduleCount() >= s.getMaxGuestReschedules()) {
            throw ApiException.conflict(I18n.t("This booking has already been moved ") + a.getRescheduleCount() + I18n.t(" time(s) online. Please call us to change it again."));
        }
        if (!a.getStartAt().minusHours(s.getCancelNoticeHours()).isAfter(now())) {
            throw ApiException.conflict(I18n.t("It is too late to move this online (less than ") + s.getCancelNoticeHours() + I18n.t(" hours to go). Please call us."));
        }
    }

    /** The free times of a day for moving a booking: its own current time counts as free. */
    @Transactional
    public List<String> publicRescheduleSlots(String code, PublicRescheduleRequest r) {
        on();
        Appointment a = byCodeAndPhone(code, r.phone());
        requireGuestMovable(a, setup.settingsEntity());
        if (r.date() == null) throw ApiException.badRequest(I18n.t("Choose a day."));
        return slots(setup.findService(a.getServiceId()), r.date(), null, a.getId(), true).stream().map(SlotDto::time).toList();
    }

    @Transactional
    public PublicConfirmationDto publicReschedule(String code, PublicRescheduleRequest r) {
        on();
        Appointment a = byCodeAndPhone(code, r.phone());
        BookingSettings s = setup.settingsEntity();
        requireGuestMovable(a, s);
        if (r.date() == null || !notBlank(r.time())) throw ApiException.badRequest(I18n.t("Choose a day and a time."));
        LocalDateTime start;
        try {
            start = r.date().atTime(LocalTime.parse(r.time()));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(I18n.t("Choose a time from the list."));
        }
        settingsRepository.lockAll();
        BookableService svc = setup.findService(a.getServiceId());
        Chosen chosen;
        try {
            chosen = choose(svc, start, a.getStaffUserId(), a.getResourceId(), a.getId(), true);
        } catch (ApiException first) {
            chosen = choose(svc, start, null, null, a.getId(), true);
        }
        LocalDateTime oldStart = a.getStartAt();
        applyMove(a, svc, start, chosen);
        a.setRescheduleCount(a.getRescheduleCount() + 1);
        repository.save(a);
        auditLogService.record("APPOINTMENT_MOVED", "Appointment " + a.getNumber() + " moved by the guest from " + oldStart.format(whenFmt()) + " to " + start.format(whenFmt()), "Appointment", a.getId());
        if (s.isEmailConfirmations() && a.getStatus() == AppointmentStatus.CONFIRMED) mail.moved(a, oldStart);
        return publicView(a, s);
    }

    // ------------------------------------------------------------------ calendar file

    public record CalendarFile(String filename, byte[] bytes) {
    }

    private CalendarFile calendarFile(Appointment a) {
        String business = null;
        try {
            business = storeSettingsService.getSettingsEntity().getBusinessName();
        } catch (Exception ignored) {
            // the file works without it
        }
        return new CalendarFile(BookingIcs.filename(a), BookingIcs.build(a, zone(), business));
    }

    @Transactional
    public CalendarFile calendar(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (!actor.viewer() && !canWork(actor, a)) throw ApiException.forbidden(I18n.t("This appointment is not yours."));
        return calendarFile(a);
    }

    @Transactional
    public CalendarFile publicCalendar(String code, String phone) {
        on();
        return calendarFile(byCodeAndPhone(code, phone));
    }

    // ------------------------------------------------------------------ reminders

    /** Every 15 minutes: reminds the guests whose confirmed visit has just come within the reminder period. */
    @Scheduled(cron = "0 11,26,41,56 * * * *")
    public void reminderJob() {
        try {
            if (!moduleService.isEnabled(ModuleKey.BOOKING)) return;
            sendDueReminders();
        } catch (Exception e) {
            log.warn("Booking reminders failed: {}", e.getMessage());
        }
    }

    /**
     * Visits that start in the last 45 minutes of the reminder period get their reminder now (so one that was booked after the
     * period began is not reminded, the guest was only just told about it). Returns how many reminders went out.
     */
    @Transactional
    public int sendDueReminders() {
        BookingSettings s = setup.settingsEntity();
        if (!s.isReminderEnabled() || !mail.mailConfigured()) return 0;
        LocalDateTime to = now().plusHours(s.getReminderHours());
        int sent = 0;
        for (Appointment a : repository.findByStatusAndReminderSentAtIsNullAndStartAtBetweenOrderByStartAtAsc(AppointmentStatus.CONFIRMED, to.minusMinutes(45), to)) {
            if (!notBlank(a.getGuestEmail())) continue;
            if (mail.reminder(a)) {
                a.setReminderSentAt(Instant.now());
                repository.save(a);
                auditLogService.record("APPOINTMENT_REMINDER_SENT", "Reminder e-mailed for " + a.getNumber() + " (" + a.getStartAt().format(whenFmt()) + ")", "Appointment", a.getId());
                sent++;
            }
        }
        return sent;
    }

    @Transactional
    public int runReminders(Actor actor) {
        on();
        if (!actor.manager()) throw ApiException.forbidden(I18n.t("Only a manager can do that."));
        return sendDueReminders();
    }

    /** Sends the reminder now, whatever the schedule says. */
    @Transactional
    public AppointmentDto remindNow(Actor actor, Long id) {
        on();
        Appointment a = find(id);
        if (!canWork(actor, a)) throw ApiException.forbidden(I18n.t("Only the person this is booked with, or a manager, can do that."));
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw ApiException.conflict(I18n.t("Only a confirmed appointment can be reminded."));
        if (!notBlank(a.getGuestEmail())) throw ApiException.conflict(I18n.t("This guest has no e-mail address."));
        if (!mail.mailConfigured()) throw ApiException.conflict(I18n.t("E-mail is not set up yet (Settings > Email settings), so no reminder was sent."));
        if (!mail.reminder(a)) throw ApiException.conflict(I18n.t("The reminder could not be sent. Check the e-mail settings."));
        a.setReminderSentAt(Instant.now());
        repository.save(a);
        auditLogService.record("APPOINTMENT_REMINDER_SENT", "Reminder e-mailed for " + a.getNumber() + " by " + BookingSetupService.name(actor.user()), "Appointment", id);
        return toDto(a, actor);
    }
}
