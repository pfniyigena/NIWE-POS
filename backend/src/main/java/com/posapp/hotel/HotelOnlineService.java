package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.AvailabilityDto;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelDtos.DayAvailability;
import com.posapp.hotel.dto.HotelDtos.QuoteDto;
import com.posapp.hotel.dto.HotelDtos.TypeAvailability;
import com.posapp.hotel.dto.HotelRequests.BookingRequest;
import com.posapp.hotel.dto.HotelRequests.CancelRequest;
import com.posapp.hotel.dto.HotelRequests.GuestRequest;
import com.posapp.hotel.dto.OnlineDtos.*;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The hotel's online side: the manager's settings, the public booking page (what is free and at what price, taking a booking, looking it up and
 * cancelling it), the deposit hold that releases rooms nobody paid for, the guest e-mail reminders, and the iCal feed per room type.
 * The public methods answer "not found" while the module or the online switch is off, and never reveal anyone else's booking.
 */
@Service
public class HotelOnlineService {

    private static final Logger log = LoggerFactory.getLogger(HotelOnlineService.class);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<BookingStatus> LIVE = List.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
    private static final String SOURCE = "Website";

    private final HotelSettingsService settingsService;
    private final StoreSettingsService storeSettingsService;
    private final ModuleService moduleService;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final FolioPaymentRepository paymentRepository;
    private final GuestRepository guestRepository;
    private final BookingService bookingService;
    private final RateService rateService;
    private final HotelFolioService folioService;
    private final RoomService roomService;
    private final HotelMailService mailService;
    private final HotelClock clock;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();

    public HotelOnlineService(HotelSettingsService settingsService, StoreSettingsService storeSettingsService, ModuleService moduleService,
                              RoomTypeRepository roomTypeRepository, RoomRepository roomRepository, BookingRepository bookingRepository,
                              FolioPaymentRepository paymentRepository, GuestRepository guestRepository, BookingService bookingService,
                              RateService rateService, HotelFolioService folioService, RoomService roomService, HotelMailService mailService,
                              HotelClock clock, AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.storeSettingsService = storeSettingsService;
        this.moduleService = moduleService;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.guestRepository = guestRepository;
        this.bookingService = bookingService;
        this.rateService = rateService;
        this.folioService = folioService;
        this.roomService = roomService;
        this.mailService = mailService;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    // ---------------------------------------------------------------- the manager's settings

    private OnlineSettingsDto toDto(HotelSettings s) {
        return new OnlineSettingsDto(s.isOnlineEnabled(), s.getOnlineIntro(), s.getOnlineDepositPercent(), s.getOnlineHoldHours(), s.getOnlineMaxNights(),
                s.getOnlineMaxDaysAhead(), s.getOnlineMaxPerPhonePerDay(), s.getOnlinePaymentInstructions(), s.isMailConfirmation(), s.isMailPrearrival(),
                s.getMailPrearrivalDays(), s.isMailThanks(), s.getThanksMessage(), s.getReviewUrl(), mailService.ready());
    }

    @Transactional
    public OnlineSettingsDto settings() {
        return toDto(settingsService.entity());
    }

    private static int between(Integer v, int min, int max, String what) {
        if (v < min || v > max) throw ApiException.badRequest(what + " must be between " + min + " and " + max);
        return v;
    }

    @Transactional
    public OnlineSettingsDto update(OnlineSettingsRequest r) {
        HotelSettings s = settingsService.entity();
        if (r.depositPercent() != null) {
            if (r.depositPercent().signum() < 0 || r.depositPercent().compareTo(new BigDecimal("100")) > 0) throw ApiException.badRequest("The deposit must be between 0 and 100 percent");
            s.setOnlineDepositPercent(r.depositPercent().setScale(2, RoundingMode.HALF_UP));
        }
        if (r.holdHours() != null) s.setOnlineHoldHours(between(r.holdHours(), 1, 720, "The hold time (hours)"));
        if (r.maxNights() != null) s.setOnlineMaxNights(between(r.maxNights(), 1, 90, "The longest online stay (nights)"));
        if (r.maxDaysAhead() != null) s.setOnlineMaxDaysAhead(between(r.maxDaysAhead(), 1, 730, "How far ahead guests can book (days)"));
        if (r.maxPerPhonePerDay() != null) s.setOnlineMaxPerPhonePerDay(between(r.maxPerPhonePerDay(), 1, 20, "Bookings per phone number per day"));
        if (r.mailPrearrivalDays() != null) s.setMailPrearrivalDays(between(r.mailPrearrivalDays(), 1, 14, "Days before arrival for the reminder"));
        if (r.reviewUrl() != null) {
            String url = clean(r.reviewUrl());
            if (url != null && (!url.matches("^https?://\\S+$") || url.length() > 300)) throw ApiException.badRequest("The review link must start with http:// or https://");
            s.setReviewUrl(url);
        }
        if (r.intro() != null) s.setOnlineIntro(limit(clean(r.intro()), 1000));
        if (r.paymentInstructions() != null) s.setOnlinePaymentInstructions(limit(clean(r.paymentInstructions()), 1000));
        if (r.thanksMessage() != null) s.setThanksMessage(limit(clean(r.thanksMessage()), 1000));
        if (r.enabled() != null) s.setOnlineEnabled(r.enabled());
        if (r.mailConfirmation() != null) s.setMailConfirmation(r.mailConfirmation());
        if (r.mailPrearrival() != null) s.setMailPrearrival(r.mailPrearrival());
        if (r.mailThanks() != null) s.setMailThanks(r.mailThanks());
        if (s.isOnlineEnabled() && s.getOnlineDepositPercent().signum() > 0 && s.getOnlinePaymentInstructions() == null) {
            throw ApiException.badRequest("Say how guests should pay the deposit before asking for one online");
        }
        auditLogService.record("HOTEL_ONLINE_SETTINGS", "Online booking settings changed (" + (s.isOnlineEnabled() ? "on" : "off") + ")", "HotelSettings", s.getId());
        return toDto(s);
    }

    private static String limit(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    @Transactional(readOnly = true)
    public List<OnlineRoomTypeDto> roomTypes() {
        List<OnlineRoomTypeDto> out = new ArrayList<>();
        for (RoomType t : roomTypeRepository.findAllByOrderByNameAsc()) out.add(toDto(t));
        return out;
    }

    private static OnlineRoomTypeDto toDto(RoomType t) {
        return new OnlineRoomTypeDto(t.getId(), t.getName(), t.getDescription(), t.isActive(), t.isOnlineVisible(), t.getIcalToken(),
                t.getIcalToken() == null ? null : "/api/public/hotel/ical/" + t.getIcalToken() + ".ics");
    }

    private RoomType type(Long id) {
        return roomTypeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room type not found: " + id));
    }

    @Transactional
    public OnlineRoomTypeDto updateRoomType(Long id, OnlineRoomTypeRequest r) {
        RoomType t = type(id);
        if (r.onlineVisible() != null) t.setOnlineVisible(r.onlineVisible());
        if (r.description() != null) t.setDescription(limit(clean(r.description()), 2000));
        roomTypeRepository.save(t);
        return toDto(t);
    }

    /** Makes the feed's secret address for the room type, or replaces it (the old address stops working at once). */
    @Transactional
    public OnlineRoomTypeDto newFeed(Long id) {
        RoomType t = type(id);
        byte[] raw = new byte[18];
        random.nextBytes(raw);
        t.setIcalToken(java.util.HexFormat.of().formatHex(raw));
        roomTypeRepository.save(t);
        auditLogService.record("HOTEL_ICAL_FEED", "Calendar feed address made for " + t.getName(), "RoomType", t.getId());
        return toDto(t);
    }

    @Transactional
    public OnlineRoomTypeDto removeFeed(Long id) {
        RoomType t = type(id);
        t.setIcalToken(null);
        roomTypeRepository.save(t);
        auditLogService.record("HOTEL_ICAL_FEED", "Calendar feed switched off for " + t.getName(), "RoomType", t.getId());
        return toDto(t);
    }

    // ---------------------------------------------------------------- the public page

    private HotelSettings open() {
        moduleService.requireEnabled(ModuleKey.HOTEL);
        HotelSettings s = settingsService.entity();
        if (!s.isEnabled() || !s.isOnlineEnabled()) throw ApiException.notFound("Online booking is not available.");
        return s;
    }

    /** True when the public booking page should be offered (the home page asks). */
    @Transactional
    public boolean isOpen() {
        if (!moduleService.isEnabled(ModuleKey.HOTEL)) return false;
        HotelSettings s = settingsService.entity();
        return s.isEnabled() && s.isOnlineEnabled();
    }

    private static String hotelName(StoreSettings st) {
        return st.getBusinessName() == null || st.getBusinessName().isBlank() ? "Our hotel" : st.getBusinessName();
    }

    @Transactional
    public PublicInfoDto info() {
        HotelSettings s = open();
        StoreSettings st = storeSettingsService.getSettingsEntity();
        return new PublicInfoDto(hotelName(st), st.getAddress(), st.getPhone(), st.getEmail(), s.getOnlineIntro(), s.getCheckInTime(), s.getCheckOutTime(),
                settingsService.taxPercent(), st.getCurrencySymbol(), s.getOnlineDepositPercent(), s.getOnlineHoldHours(), s.getOnlineMaxNights(),
                s.getOnlineMaxDaysAhead(), s.getOnlineDepositPercent().signum() > 0 ? s.getOnlinePaymentInstructions() : null);
    }

    private void checkStay(HotelSettings s, LocalDate from, LocalDate to, int adults, int children) {
        LocalDate today = clock.today();
        if (from == null || to == null) throw ApiException.badRequest("Choose your arrival and departure dates.");
        if (from.isBefore(today)) throw ApiException.badRequest("The arrival date cannot be in the past.");
        if (!to.isAfter(from)) throw ApiException.badRequest("The departure must be after the arrival.");
        if (ChronoUnit.DAYS.between(from, to) > s.getOnlineMaxNights()) {
            throw ApiException.badRequest("Online bookings are for up to " + s.getOnlineMaxNights() + " nights. Please contact us for a longer stay.");
        }
        if (ChronoUnit.DAYS.between(today, from) > s.getOnlineMaxDaysAhead()) {
            throw ApiException.badRequest("You can book up to " + s.getOnlineMaxDaysAhead() + " days ahead.");
        }
        if (adults < 1 || adults > 20 || children < 0 || children > 20) throw ApiException.badRequest("Enter the number of guests.");
    }

    private BigDecimal depositFor(HotelSettings s, BigDecimal gross) {
        if (s.getOnlineDepositPercent().signum() <= 0) return BigDecimal.ZERO;
        return gross.multiply(s.getOnlineDepositPercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public PublicAvailabilityDto availability(LocalDate from, LocalDate to, Integer adultsIn, Integer childrenIn) {
        HotelSettings s = openRead();
        int adults = adultsIn == null ? 1 : adultsIn;
        int children = childrenIn == null ? 0 : childrenIn;
        checkStay(s, from, to, adults, children);
        AvailabilityDto av = bookingService.availability(from, to);
        List<PublicOfferDto> offers = new ArrayList<>();
        for (TypeAvailability t : av.types()) {
            RoomType rt = roomTypeRepository.findById(t.roomTypeId()).orElse(null);
            if (rt == null || !rt.isActive() || !rt.isOnlineVisible() || rt.getMaxOccupancy() < adults + children) continue;
            int left = t.days().stream().mapToInt(DayAvailability::available).min().orElse(0);
            if (left < 1) continue;
            QuoteDto q = rateService.quote(rt.getId(), null, from, to, null, adults, children);
            List<PublicNightDto> items = q.items().stream().map(i -> new PublicNightDto(i.date(), i.gross())).toList();
            offers.add(new PublicOfferDto(rt.getId(), rt.getName(), rt.getDescription(), rt.getAmenities(), rt.getMaxOccupancy(), Math.min(left, 9), q.nights(),
                    q.net(), q.tax(), q.gross(), q.averageRate(), depositFor(s, q.gross()), items));
        }
        offers.sort(java.util.Comparator.comparing(PublicOfferDto::gross));
        return new PublicAvailabilityDto(from, to, (int) ChronoUnit.DAYS.between(from, to), adults, children, offers);
    }

    private HotelSettings openRead() {
        moduleService.requireEnabled(ModuleKey.HOTEL);
        List<HotelSettings> all = settingsService.entityIfAny();
        if (all.isEmpty() || !all.get(0).isEnabled() || !all.get(0).isOnlineEnabled()) throw ApiException.notFound("Online booking is not available.");
        return all.get(0);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder c = new StringBuilder(8);
            for (int i = 0; i < 8; i++) c.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            if (bookingRepository.findByConfirmationCode(c.toString()).isEmpty()) return c.toString();
        }
        throw ApiException.conflict("Could not make a booking code. Please try again.");
    }

    @Transactional
    public PublicBookingDto book(PublicBookRequest r) {
        HotelSettings s = open();
        if (clean(r.website()) != null) throw ApiException.badRequest("Could not make the booking.");
        int adults = r.adults() == null ? 1 : r.adults();
        int children = r.children() == null ? 0 : r.children();
        checkStay(s, r.checkInDate(), r.checkOutDate(), adults, children);
        if (r.roomTypeId() == null) throw ApiException.badRequest("Choose a room.");
        String name = clean(r.name());
        String phone = clean(r.phone());
        if (phone != null) phone = phone.replaceAll("[\\s\\-().]", "");
        String email = clean(r.email());
        if (name == null || name.length() > 120) throw ApiException.badRequest("Enter your name.");
        if (phone == null || digits(phone).length() < 7 || digits(phone).length() > 15 || phone.length() > 40) throw ApiException.badRequest("Enter a phone number we can reach you on.");
        if (email != null && (email.length() > 160 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) throw ApiException.badRequest("That e-mail address does not look right.");
        String notes = limit(clean(r.notes()), 500);
        RoomType rt = type(r.roomTypeId());
        if (!rt.isActive() || !rt.isOnlineVisible()) throw ApiException.notFound("That room is not available online.");
        Instant dayAgo = Instant.now().minusSeconds(24 * 3600L);
        long today = bookingRepository.countByOnlineTrueAndGuestPhoneAndCreatedAtGreaterThanEqual(phone, dayAgo);
        if (today >= s.getOnlineMaxPerPhonePerDay()) {
            throw ApiException.conflict("There are already " + today + " online bookings from this phone number today. Please call us to book more.");
        }
        if (bookingRepository.countByOnlineTrueAndCreatedAtGreaterThanEqual(Instant.now().minusSeconds(3600L)) >= 60) {
            throw ApiException.conflict("We are receiving a lot of bookings right now. Please try again in a little while, or call us.");
        }
        QuoteDto q = rateService.quote(rt.getId(), null, r.checkInDate(), r.checkOutDate(), null, adults, children);

        Guest existing = guestRepository.findByPhoneAndFullNameIgnoreCaseOrderByIdAsc(phone, name).stream().findFirst().orElse(null);
        BookingRequest req = new BookingRequest(existing == null ? null : existing.getId(),
                existing == null ? new GuestRequest(name, phone, email, null, null, null, null, null) : null,
                rt.getId(), null, r.checkInDate(), r.checkOutDate(), adults, children, null, SOURCE, notes, null, null, null);
        BookingDto created = bookingService.create(req);
        Booking b = folioService.getBooking(created.id());
        if (existing != null && email != null && (existing.getEmail() == null || existing.getEmail().isBlank())) {
            existing.setEmail(email);
            guestRepository.save(existing);
        }
        BigDecimal due = depositFor(s, q.gross());
        b.setOnline(true);
        b.setConfirmationCode(newCode());
        if (due.signum() > 0) {
            b.setDepositDue(due);
            b.setHoldExpiresAt(Instant.now().plus(s.getOnlineHoldHours(), ChronoUnit.HOURS));
        }
        bookingRepository.save(b);
        auditLogService.record("HOTEL_ONLINE_BOOKING", b.getBookingNumber() + " booked online for " + name + " " + r.checkInDate() + " to " + r.checkOutDate(), "Booking", b.getId());
        Long id = b.getId();
        String to = email;
        afterCommit(() -> mailService.confirmation(id, to));
        return view(b, s);
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private BigDecimal depositPaid(Booking b) {
        return paymentRepository.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .map(p -> p.getKind() == PaymentKind.DEPOSIT ? p.getAmount() : p.getKind() == PaymentKind.REFUND ? p.getAmount().negate() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PublicBookingDto view(Booking b, HotelSettings s) {
        BigDecimal paid = depositPaid(b);
        BookingDto dto = folioService.toDto(b);
        boolean waiting = b.getStatus() == BookingStatus.BOOKED && b.getDepositDue() != null && paid.signum() <= 0;
        String why = cancelBlock(b, paid);
        return new PublicBookingDto(b.getConfirmationCode(), b.getBookingNumber(), b.getStatus().name(), b.getRoomType().getName(), b.getCheckInDate(), b.getCheckOutDate(),
                b.plannedNights(), b.getAdults(), b.getChildren(), b.getGuest().getFullName(), dto.chargesTotal(), b.getDepositDue(), paid, waiting ? b.getHoldExpiresAt() : null,
                waiting ? s.getOnlinePaymentInstructions() : null, b.getStatus() == BookingStatus.BOOKED && why == null, b.getStatus() == BookingStatus.BOOKED ? why : null,
                s.getCheckInTime(), s.getCheckOutTime());
    }

    /** Why a guest cannot cancel online, or null when they can: only a booking nobody has paid on, before any cancellation fee applies. */
    private String cancelBlock(Booking b, BigDecimal paid) {
        if (b.getStatus() != BookingStatus.BOOKED) return null;
        if (paid.signum() != 0) return "A deposit has been paid on this booking. Please contact us to cancel, and we will arrange your refund.";
        if (folioService.cancellationFee(b, clock.today(), false) != null) return "This booking is inside the free-cancellation period. Please contact us to cancel.";
        return null;
    }

    private Booking byCode(String code, String phone) {
        Booking b = bookingRepository.findByConfirmationCode(code == null ? "" : code.trim().toUpperCase()).orElse(null);
        if (b == null || !b.isOnline() || digits(phone).isEmpty() || !digits(b.getGuest().getPhone()).equals(digits(phone))) {
            throw ApiException.notFound("No booking found with that code and phone number.");
        }
        return b;
    }

    @Transactional
    public PublicBookingDto lookup(String code, String phone) {
        HotelSettings s = open();
        return view(byCode(code, phone), s);
    }

    @Transactional
    public PublicBookingDto cancel(String code, String phone) {
        HotelSettings s = open();
        Booking b = byCode(code, phone);
        if (b.getStatus() != BookingStatus.BOOKED) throw ApiException.conflict("This booking can no longer be cancelled.");
        String why = cancelBlock(b, depositPaid(b));
        if (why != null) throw ApiException.conflict(why);
        bookingService.cancel(b.getId(), new CancelRequest("Cancelled by the guest online", null, null, false));
        b.setHoldExpiresAt(null);
        bookingRepository.save(b);
        return view(folioService.getBooking(b.getId()), s);
    }

    // ---------------------------------------------------------------- unpaid holds and reminders

    @Scheduled(fixedDelay = 300_000L, initialDelay = 100_000L)
    public void sweep() {
        try {
            if (!moduleService.isEnabled(ModuleKey.HOTEL)) return;
            releaseUnpaid();
        } catch (Exception e) {
            log.warn("Online booking sweep failed: {}", e.getMessage());
        }
    }

    /**
     * Online bookings waiting for a deposit: the hold ends as soon as any deposit is recorded (the front desk takes over from there);
     * with none by the deadline the booking is cancelled, no fee, and the guest is told.
     */
    @Transactional
    public int releaseUnpaid() {
        int released = 0;
        Instant now = Instant.now();
        for (Booking b : bookingRepository.findByOnlineTrueAndStatusAndHoldExpiresAtNotNull(BookingStatus.BOOKED)) {
            if (depositPaid(b).signum() > 0) {
                b.setHoldExpiresAt(null);
                bookingRepository.save(b);
            } else if (b.getHoldExpiresAt().isBefore(now)) {
                bookingService.cancel(b.getId(), new CancelRequest("Deposit not received in time, so the room was released", null, null, true));
                b.setHoldExpiresAt(null);
                bookingRepository.save(b);
                Long id = b.getId();
                afterCommit(() -> mailService.released(id));
                released++;
            }
        }
        return released;
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 150_000L)
    public void remind() {
        try {
            if (!moduleService.isEnabled(ModuleKey.HOTEL) || !mailService.ready()) return;
            sendDueMails();
        } catch (Exception e) {
            log.warn("Guest e-mail run failed: {}", e.getMessage());
        }
    }

    /** Sends the confirmations, pre-arrival reminders and thank-yous that are due and switched on. Returns how many were handed to the mailer. */
    @Transactional
    public int sendDueMails() {
        HotelSettings s = settingsService.entity();
        LocalDate today = clock.today();
        Instant now = Instant.now();
        int sent = 0;
        if (s.isMailConfirmation()) {
            for (Booking b : bookingRepository.findByStatusAndConfirmationMailedAtIsNullAndCreatedAtGreaterThanEqual(BookingStatus.BOOKED, now.minus(48, ChronoUnit.HOURS))) {
                if (b.isOnline() || b.getGroup() != null || b.getCreatedAt().isAfter(now.minusSeconds(60))) continue;
                if (hasEmail(b)) {
                    mailService.confirmation(b.getId(), null);
                    sent++;
                }
            }
        }
        if (s.isMailPrearrival()) {
            for (int d = 0; d <= s.getMailPrearrivalDays(); d++) {
                for (Booking b : bookingRepository.findByStatusAndCheckInDateAndPrearrivalMailedAtIsNull(BookingStatus.BOOKED, today.plusDays(d))) {
                    if (b.getCreatedAt().isAfter(now.minus(24, ChronoUnit.HOURS)) || !hasEmail(b)) continue;
                    mailService.prearrival(b.getId());
                    sent++;
                }
            }
        }
        if (s.isMailThanks()) {
            for (Booking b : bookingRepository.findByStatusAndActualCheckOutGreaterThanEqualAndThanksMailedAtIsNull(BookingStatus.CHECKED_OUT, now.minus(3, ChronoUnit.DAYS))) {
                if (!hasEmail(b)) continue;
                mailService.thanks(b.getId());
                sent++;
            }
        }
        return sent;
    }

    private static boolean hasEmail(Booking b) {
        return b.getGuest().getEmail() != null && !b.getGuest().getEmail().isBlank();
    }

    /** The front desk sends (or re-sends) the confirmation to the guest's e-mail address. */
    @Transactional(readOnly = true)
    public void sendConfirmationNow(Long bookingId) {
        Booking b = folioService.getBooking(bookingId);
        if (!mailService.ready()) throw ApiException.badRequest("E-mail is not set up. Add the mail server in Settings first.");
        if (!hasEmail(b)) throw ApiException.badRequest("The guest has no e-mail address.");
        if (b.getStatus() != BookingStatus.BOOKED && b.getStatus() != BookingStatus.CHECKED_IN) throw ApiException.badRequest("A confirmation is sent only for a booking that is waiting for the guest or in the house.");
        mailService.confirmation(bookingId, null);
    }

    // ---------------------------------------------------------------- iCal feed

    /** A calendar of the nights taken in one room type: no guest details, just "Reserved". For a type with one room, repair blocks show too. */
    @Transactional(readOnly = true)
    public String ical(String token) {
        if (token == null || token.isBlank()) throw ApiException.notFound("Calendar not found.");
        moduleService.requireEnabled(ModuleKey.HOTEL);
        RoomType t = roomTypeRepository.findByIcalToken(token).orElseThrow(() -> ApiException.notFound("Calendar not found."));
        LocalDate today = clock.today();
        LocalDate from = today.minusDays(7);
        LocalDate to = today.plusDays(731);
        DateTimeFormatter d = DateTimeFormatter.BASIC_ISO_DATE;
        String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//NIWE//Hotel//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + t.getName().replace(",", " ").replace(";", " ").replace("\n", " "));
        for (Booking b : bookingRepository.findOverlapping(from, to, LIVE)) {
            if (!b.getRoomType().getId().equals(t.getId())) continue;
            LocalDate end = b.getCheckOutDate().isAfter(b.getCheckInDate()) ? b.getCheckOutDate() : b.getCheckInDate().plusDays(1);
            event(sb, "booking-" + b.getId(), stamp, b.getCheckInDate().format(d), end.format(d), "Reserved");
        }
        List<Room> rooms = roomRepository.findByActiveTrueOrderByNumberAsc().stream().filter(r -> r.getRoomType().getId().equals(t.getId())).toList();
        if (rooms.size() == 1) {
            for (MaintenanceRequest m : roomService.openBlocks().getOrDefault(rooms.get(0).getId(), List.of())) {
                LocalDate s0 = m.getBlockFrom() == null || m.getBlockFrom().isBefore(today) ? today : m.getBlockFrom();
                LocalDate e0 = m.getBlockUntil() == null ? today.plusDays(365) : m.getBlockUntil();
                if (e0.isAfter(s0)) event(sb, "block-" + m.getId(), stamp, s0.format(d), e0.format(d), "Not available");
            }
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    private static void event(StringBuilder sb, String uid, String stamp, String start, String end, String summary) {
        line(sb, "BEGIN:VEVENT");
        line(sb, "UID:" + uid + "@niwe-hotel");
        line(sb, "DTSTAMP:" + stamp);
        line(sb, "DTSTART;VALUE=DATE:" + start);
        line(sb, "DTEND;VALUE=DATE:" + end);
        line(sb, "SUMMARY:" + summary);
        line(sb, "TRANSP:OPAQUE");
        line(sb, "END:VEVENT");
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append("\r\n");
    }
}
