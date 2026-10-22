package com.posapp.hotel;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelDtos.FolioChargeDto;
import com.posapp.hotel.dto.HotelDtos.FolioDto;
import com.posapp.hotel.dto.HotelDtos.FolioPaymentDto;
import com.posapp.hotel.dto.HotelRequests.ChargeRequest;
import com.posapp.hotel.dto.HotelRequests.CheckOutRequest;
import com.posapp.hotel.dto.HotelRequests.FolioPaymentRequest;
import com.posapp.hotel.dto.HotelRequests.PaymentLine;
import com.posapp.credit.CustomerCreditService;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.hotel.dto.HotelDtos.BillDto;
import com.posapp.hotel.dto.HotelDtos.CancelPreviewDto;
import com.posapp.hotel.dto.HotelDtos.CheckoutPreviewDto;
import com.posapp.hotel.dto.HotelDtos.FeeQuoteDto;
import com.posapp.hotel.dto.HotelRequests.BillRequest;
import com.posapp.paymentmethod.PaymentMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * The guest's bill, and the one place that turns it into accounting entries.
 *
 * Room revenue is handled by a single "sync" rule that works for every accounting mode: what has been posted so far
 * ({@link Booking#getRevenuePostedNet()} / Tax) is compared with what the non-voided ROOM lines say it should be, and only
 * the difference is posted. The mode only decides WHEN that sync runs (each night, or at check-out).
 */
@Service
public class HotelFolioService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BookingRepository bookingRepository;
    private final FolioChargeRepository chargeRepository;
    private final FolioPaymentRepository paymentRepository;
    private final HotelSettingsService settingsService;
    private final AccountingService accountingService;
    private final PaymentMethodService paymentMethodService;
    private final AuditLogService auditLogService;
    private final BookingNightRepository nightRepository;
    private final BookingBillRepository billRepository;
    private final CompanyBillRepository companyBillRepository;
    private final CustomerRepository customerRepository;
    private final CustomerCreditService creditService;
    private final HotelClock clock;

    /** A fee the policy asks for: nothing is stored until it is charged. */
    record FeeQuote(String kind, String description, int nights, BigDecimal net, BigDecimal tax) {
        BigDecimal gross() {
            return net.add(tax);
        }

        FeeQuoteDto dto() {
            return new FeeQuoteDto(kind, description, nights, net, tax, gross());
        }
    }

    static final int MAX_BILLS = 6;

    private final GroupLedger groupLedger;

    public HotelFolioService(BookingRepository bookingRepository, FolioChargeRepository chargeRepository,
                             FolioPaymentRepository paymentRepository, HotelSettingsService settingsService,
                             AccountingService accountingService, PaymentMethodService paymentMethodService,
                             AuditLogService auditLogService, BookingNightRepository nightRepository,
                             BookingBillRepository billRepository, CompanyBillRepository companyBillRepository,
                             CustomerRepository customerRepository, CustomerCreditService creditService, HotelClock clock,
                             GroupLedger groupLedger) {
        this.groupLedger = groupLedger;
        this.nightRepository = nightRepository;
        this.billRepository = billRepository;
        this.companyBillRepository = companyBillRepository;
        this.customerRepository = customerRepository;
        this.creditService = creditService;
        this.clock = clock;
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.settingsService = settingsService;
        this.accountingService = accountingService;
        this.paymentMethodService = paymentMethodService;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public FolioDto folio(Long bookingId) {
        return toFolio(getBooking(bookingId));
    }

    FolioDto toFolio(Booking booking) {
        List<FolioCharge> charges = chargeRepository.findByBookingIdOrderByIdAsc(booking.getId());
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId());
        BigDecimal chargesTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (FolioCharge c : charges) {
            if (!c.isVoided()) {
                chargesTotal = chargesTotal.add(c.getAmount());
                taxTotal = taxTotal.add(c.getTax());
            }
        }
        BigDecimal deposits = sum(payments, PaymentKind.DEPOSIT);
        BigDecimal paid = sum(payments, PaymentKind.PAYMENT);
        BigDecimal refunds = sum(payments, PaymentKind.REFUND);
        BigDecimal balance = chargesTotal.subtract(deposits).subtract(paid).add(refunds);
        return new FolioDto(toDto(booking), charges.stream().map(HotelFolioService::toDto).toList(),
                payments.stream().map(HotelFolioService::toDto).toList(), chargesTotal, taxTotal, deposits, paid, refunds, balance,
                bills(booking, charges, payments));
    }

    /** Booking with its money summary. Before arrival the "charges" figure is the quote for the whole stay. */
    BookingDto toDto(Booking b) {
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(b.getId());
        BigDecimal charges;
        if (b.getStatus() == BookingStatus.BOOKED) {
            charges = stayGross(b);
        } else {
            charges = chargeRepository.findByBookingIdOrderByIdAsc(b.getId()).stream()
                    .filter(c -> !c.isVoided()).map(FolioCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal paid = sum(payments, PaymentKind.DEPOSIT).add(sum(payments, PaymentKind.PAYMENT)).subtract(sum(payments, PaymentKind.REFUND));
        return new BookingDto(b.getId(), b.getBookingNumber(), b.getGuest().getId(), b.getGuest().getFullName(), b.getGuest().getPhone(),
                b.getRoomType().getId(), b.getRoomType().getName(), b.getRoom() != null ? b.getRoom().getId() : null,
                b.getRoom() != null ? b.getRoom().getNumber() : null, b.getCheckInDate(), b.getCheckOutDate(), b.plannedNights(),
                b.getActualCheckIn(), b.getActualCheckOut(), b.getAdults(), b.getChildren(), b.getNightlyRate(), b.getExtraPersonRate(),
                b.nightlyTotal(), b.getStatus().name(), b.getSource(), b.getNotes(), b.getCancelReason(), charges, paid, charges.subtract(paid),
                b.getRatePlan() == null ? null : b.getRatePlan().getId(), b.getRatePlan() == null ? null : b.getRatePlan().getName(), b.getRateMode().name(),
                b.getGroup() == null ? null : b.getGroup().getId(), b.getGroup() == null ? null : b.getGroup().getGroupCode(),
                b.getGroup() == null ? null : b.getGroup().getName(),
                b.getGuest().isVip(), b.getGuest().isBlacklisted(), b.getGuest().getPreferences(),
                b.getConfirmationCode(), b.isOnline(), b.getDepositDue(), b.getHoldExpiresAt());
    }

    private static BigDecimal sum(List<FolioPayment> payments, PaymentKind kind) {
        return payments.stream().filter(p -> p.getKind() == kind).map(FolioPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FolioChargeDto toDto(FolioCharge c) {
        return new FolioChargeDto(c.getId(), c.getChargeType().name(), c.getDescription(), c.getNightDate(), c.getNet(), c.getTax(),
                c.getAmount(), c.getSourceType(), c.getSourceId(), c.isVoided(), c.getVoidReason(), c.getCreatedAt(), c.getBillNo());
    }

    private static FolioPaymentDto toDto(FolioPayment p) {
        return new FolioPaymentDto(p.getId(), p.getKind().name(), p.getMethod(), p.getAmount(), p.getReference(), p.getNote(), p.getCreatedAt(), p.getBillNo());
    }

    // ---------------------------------------------------------------- money helpers

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal tax(BigDecimal net) {
        return taxFor(net, settingsService.taxPercent());
    }

    /** Tax on an amount at a percentage, rounded to cents — the one rule used by bills, quotes and the booking-time quote. */
    static BigDecimal taxFor(BigDecimal net, BigDecimal percent) {
        return money(net.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** The extra-guest charge for one night of this booking (guests above the type's included number). */
    private BigDecimal extraPerNight(Booking b) {
        int extra = Math.max(0, b.guests() - b.getBaseOccupancy());
        return b.getExtraPersonRate() == null ? BigDecimal.ZERO : b.getExtraPersonRate().multiply(BigDecimal.valueOf(extra));
    }

    /** The stored night prices of an AUTO booking by date (empty for a FIXED one). */
    Map<LocalDate, BookingNight> nightMap(Booking b) {
        Map<LocalDate, BookingNight> map = new HashMap<>();
        if (b.getRateMode() == RateMode.AUTO && b.getId() != null) {
            for (BookingNight n : nightRepository.findByBookingIdOrderByNightDateAsc(b.getId())) map.put(n.getNightDate(), n);
        }
        return map;
    }

    /** One night's room price before tax, including the extra-guest charge. */
    BigDecimal nightNet(Booking b, LocalDate night, Map<LocalDate, BookingNight> nights) {
        BookingNight stored = nights.get(night);
        BigDecimal rate = stored != null ? stored.getRate() : (b.getNightlyRate() == null ? BigDecimal.ZERO : b.getNightlyRate());
        return money(rate.add(extraPerNight(b)));
    }

    /** The price of every planned night of a booking as stored (or at the agreed rate), with extra-guest charge and tax. */
    @Transactional(readOnly = true)
    public List<com.posapp.hotel.dto.HotelDtos.QuoteNightDto> nights(Long bookingId) {
        Booking b = getBooking(bookingId);
        Map<LocalDate, BookingNight> stored = nightMap(b);
        BigDecimal extra = money(extraPerNight(b));
        List<com.posapp.hotel.dto.HotelDtos.QuoteNightDto> out = new java.util.ArrayList<>();
        LocalDate night = b.getCheckInDate();
        for (long i = 0; i < b.plannedNights(); i++, night = night.plusDays(1)) {
            BookingNight bn = stored.get(night);
            BigDecimal rate = bn != null ? bn.getRate() : (b.getNightlyRate() == null ? BigDecimal.ZERO : b.getNightlyRate());
            BigDecimal net = money(rate.add(extra));
            BigDecimal tax = tax(net);
            out.add(new com.posapp.hotel.dto.HotelDtos.QuoteNightDto(night, rate, extra, net, tax, net.add(tax), bn == null ? null : bn.getRuleName()));
        }
        return out;
    }

    /** What the whole planned stay costs including tax, night by night — the quote before arrival and the unearned amount in the booking-and-check-out mode. */
    BigDecimal stayGross(Booking b) {
        Map<LocalDate, BookingNight> nights = nightMap(b);
        BigDecimal total = BigDecimal.ZERO;
        LocalDate night = b.getCheckInDate();
        for (long i = 0; i < b.plannedNights(); i++, night = night.plusDays(1)) {
            BigDecimal net = nightNet(b, night, nights);
            total = total.add(net).add(tax(net));
        }
        return total;
    }

    // ---------------------------------------------------------------- room charges and room revenue

    /** Adds the missing ROOM lines for every night from arrival up to (not including) {@code upToExclusive}. Returns how many were added. */
    @Transactional
    public int ensureRoomCharges(Booking booking, LocalDate upToExclusive) {
        int created = 0;
        Map<LocalDate, BookingNight> nights = nightMap(booking);
        String room = booking.getRoom() != null ? booking.getRoom().getNumber() : "?";
        for (LocalDate night = booking.getCheckInDate(); night.isBefore(upToExclusive); night = night.plusDays(1)) {
            if (chargeRepository.existsByBookingIdAndChargeTypeAndNightDate(booking.getId(), ChargeType.ROOM, night)) continue;
            BigDecimal net = nightNet(booking, night, nights);
            BigDecimal tax = tax(net);
            BookingNight stored = nights.get(night);
            FolioCharge line = new FolioCharge();
            line.setBooking(booking);
            line.setChargeType(ChargeType.ROOM);
            line.setBillNo(booking.getRoomBillNo());
            line.setNightDate(night);
            line.setDescription("Room " + room + " — night of " + night + (stored != null && stored.getRuleName() != null ? " (" + stored.getRuleName() + ")" : ""));
            line.setNet(net);
            line.setTax(tax);
            line.setAmount(net.add(tax));
            chargeRepository.save(line);
            created++;
        }
        if (created > 0 && settingsService.postingMode() == PostingMode.NIGHTLY) {
            syncRoomRevenue(booking, false);
        }
        return created;
    }

    /**
     * Posts the difference between the room revenue the bill says exists and what was already posted.
     * {@code releaseDeferred} (check-out in the booking-and-check-out mode) also turns the unearned amount held at booking into revenue.
     */
    @Transactional
    public void syncRoomRevenue(Booking booking, boolean releaseDeferred) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (FolioCharge c : chargeRepository.findByBookingIdOrderByIdAsc(booking.getId())) {
            if (c.getChargeType() == ChargeType.ROOM && !c.isVoided()) {
                net = net.add(c.getNet());
                tax = tax.add(c.getTax());
            }
        }
        BigDecimal deltaNet = net.subtract(nz(booking.getRevenuePostedNet()));
        BigDecimal deltaTax = tax.subtract(nz(booking.getRevenuePostedTax()));
        BigDecimal release = releaseDeferred ? nz(booking.getDeferredPosted()) : BigDecimal.ZERO;
        if (deltaNet.signum() != 0 || deltaTax.signum() != 0 || release.signum() != 0) {
            accountingService.postHotelRevenue("HOTEL_ROOM", booking.getId(), "Room revenue " + booking.getBookingNumber(), deltaNet, deltaTax, release);
        }
        booking.setRevenuePostedNet(net);
        booking.setRevenuePostedTax(tax);
        if (releaseDeferred) booking.setDeferredPosted(BigDecimal.ZERO);
        bookingRepository.save(booking);
    }

    /**
     * Booking-and-check-out mode only: keeps the "unearned room revenue" held for this booking equal to the value of the planned stay
     * (so it follows date, rate and guest-count changes, and drops to zero on cancel / no-show).
     */
    @Transactional
    public void syncDeferral(Booking booking) {
        if (settingsService.postingMode() != PostingMode.BOOKING_AND_CHECKOUT) return;
        boolean live = booking.getStatus() == BookingStatus.BOOKED || booking.getStatus() == BookingStatus.CHECKED_IN;
        BigDecimal desired = live ? stayGross(booking) : BigDecimal.ZERO;
        BigDecimal delta = desired.subtract(nz(booking.getDeferredPosted()));
        if (delta.signum() != 0) {
            accountingService.postHotelBookingDeferral(booking.getId(), "Booked stay " + booking.getBookingNumber(), delta);
            booking.setDeferredPosted(desired);
            bookingRepository.save(booking);
        }
    }

    // ---------------------------------------------------------------- other charges

    /** A manual line (laundry, minibar, late check-out...) — revenue is posted straight away; the amount entered is before tax. */
    @Transactional
    public FolioDto addExtraCharge(Long bookingId, ChargeRequest request) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "add charges to", BookingStatus.CHECKED_IN);
        if (request.amount().signum() == 0) throw ApiException.badRequest("The amount cannot be zero");
        BigDecimal net = money(request.amount());
        BigDecimal tax = Boolean.FALSE.equals(request.taxable()) ? BigDecimal.ZERO : tax(net);
        FolioCharge line = new FolioCharge();
        line.setBooking(booking);
        line.setChargeType(ChargeType.EXTRA);
        line.setBillNo(request.billNo() == null ? defaultGuestBill(booking) : requireBill(booking, request.billNo()));
        line.setDescription(request.description().trim());
        line.setNet(net);
        line.setTax(tax);
        line.setAmount(net.add(tax));
        line.setRevenuePosted(true);
        FolioCharge saved = chargeRepository.save(line);
        accountingService.postHotelRevenue("HOTEL_EXTRA", saved.getId(), saved.getDescription() + " — " + booking.getBookingNumber(), net, tax, BigDecimal.ZERO);
        auditLogService.record("HOTEL_CHARGE_ADDED", booking.getBookingNumber() + ": " + saved.getDescription() + " " + saved.getAmount(), "Booking", booking.getId());
        return toFolio(booking);
    }

    /**
     * Called by the Restaurant and POS modules when a bill is put on a guest's room. The other module has already posted the sale
     * (owed by the guest, i.e. Accounts Receivable), so this only adds the line to the guest's bill.
     */
    @Transactional
    public FolioCharge chargeToRoom(Long bookingId, ChargeType type, String description, BigDecimal amount, String sourceType, Long sourceId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> ApiException.badRequest("Guest booking not found"));
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.badRequest("Only a guest who is checked in can be charged to a room");
        }
        if (amount.signum() <= 0) throw ApiException.badRequest("Nothing to charge to the room");
        FolioCharge line = new FolioCharge();
        line.setBooking(booking);
        line.setChargeType(type);
        line.setBillNo(defaultGuestBill(booking));
        line.setDescription(description);
        line.setNet(money(amount));
        line.setTax(BigDecimal.ZERO);
        line.setAmount(money(amount));
        line.setSourceType(sourceType);
        line.setSourceId(sourceId);
        line.setRevenuePosted(true);
        FolioCharge saved = chargeRepository.save(line);
        auditLogService.record("HOTEL_CHARGED_TO_ROOM", description + " " + saved.getAmount() + " to room "
                + (booking.getRoom() != null ? booking.getRoom().getNumber() : "?") + " (" + booking.getBookingNumber() + ")", "Booking", booking.getId());
        return saved;
    }

    @Transactional
    public FolioDto voidCharge(Long bookingId, Long chargeId, String reason) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "change the bill of", BookingStatus.CHECKED_IN, BookingStatus.BOOKED);
        FolioCharge line = chargeRepository.findById(chargeId).orElseThrow(() -> ApiException.notFound("Charge not found: " + chargeId));
        if (!line.getBooking().getId().equals(bookingId)) throw ApiException.notFound("Charge not found: " + chargeId);
        if (line.isVoided()) throw ApiException.badRequest("This charge is already voided");
        switch (line.getChargeType()) {
            case RESTAURANT, POS, ROOM_SERVICE -> throw ApiException.badRequest(
                    "This line came from the " + line.getChargeType().name().toLowerCase().replace('_', ' ')
                            + " — reverse it there (return / void the sale) rather than here");
            default -> { }
        }
        line.setVoided(true);
        line.setVoidReason(reason == null || reason.isBlank() ? null : reason.trim());
        chargeRepository.save(line);
        if (line.getChargeType() == ChargeType.EXTRA && line.isRevenuePosted()) {
            accountingService.postHotelRevenue("HOTEL_EXTRA", line.getId(), "Voided: " + line.getDescription() + " — " + booking.getBookingNumber(),
                    line.getNet().negate(), line.getTax().negate(), BigDecimal.ZERO);
            line.setRevenuePosted(false);
            chargeRepository.save(line);
        } else if (line.getChargeType() == ChargeType.ROOM && settingsService.postingMode() == PostingMode.NIGHTLY) {
            syncRoomRevenue(booking, false);
        }
        auditLogService.record("HOTEL_CHARGE_VOIDED", booking.getBookingNumber() + ": " + line.getDescription() + " voided", "Booking", booking.getId());
        return toFolio(booking);
    }

    // ---------------------------------------------------------------- payments

    /** A deposit or a payment taken while the guest is booked or in-house (or, for a fee still owed, after a cancellation or no-show). */
    @Transactional
    public FolioDto addPayment(Long bookingId, FolioPaymentRequest request) {
        Booking booking = getBooking(bookingId);
        PaymentKind kind;
        try {
            kind = PaymentKind.valueOf(request.kind().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown payment kind: " + request.kind());
        }
        if (kind == PaymentKind.REFUND) throw ApiException.badRequest("Refunds are made when cancelling or checking out");
        boolean closedWithFee = booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.NO_SHOW;
        if (closedWithFee) {
            if (kind != PaymentKind.PAYMENT) throw ApiException.badRequest("Only the fee still owed can be paid on a " + booking.getStatus().name().toLowerCase().replace('_', ' ') + " booking");
            BigDecimal owed = toFolio(booking).balance();
            if (owed.signum() <= 0) throw ApiException.badRequest("Nothing is owed on this booking");
            if (request.amount() != null && request.amount().compareTo(owed) > 0) {
                throw ApiException.badRequest("Only " + owed.setScale(2, RoundingMode.HALF_UP) + " is owed on this booking");
            }
        } else {
            requireStatus(booking, "take a payment for", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
            if (kind == PaymentKind.PAYMENT && booking.getStatus() != BookingStatus.CHECKED_IN) {
                throw ApiException.badRequest("Take a deposit before arrival; payments are taken once the guest is checked in");
            }
        }
        recordPayment(booking, kind, request.method(), request.amount(), request.reference(), request.note(), request.billNo());
        return toFolio(booking);
    }

    @Transactional
    public FolioPayment recordPayment(Booking booking, PaymentKind kind, String method, BigDecimal amount, String reference, String note) {
        return recordPayment(booking, kind, method, amount, reference, note, null);
    }

    /** Stores the payment and posts it. DEPOSIT: cash in / held for the guest. PAYMENT: cash in / guest ledger down. */
    @Transactional
    public FolioPayment recordPayment(Booking booking, PaymentKind kind, String method, BigDecimal amount, String reference, String note, Integer billNo) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("The amount must be greater than zero");
        int bill = billNo == null ? defaultGuestBill(booking) : requireBill(booking, billNo);
        BookingBill row = billRepository.findByBookingIdAndBillNo(booking.getId(), bill).orElse(null);
        if (row != null && row.getCompany() != null) {
            throw ApiException.badRequest("Bill " + bill + " is sent to " + row.getCompany().getName() + " — take the payment on a guest bill");
        }
        if (row != null && row.isToGroup()) {
            throw ApiException.badRequest("Bill " + bill + " is paid by the group — take the payment on the group's account or on a guest bill");
        }
        String code = payableMethod(method);
        FolioPayment p = new FolioPayment();
        p.setBooking(booking);
        p.setKind(kind);
        p.setBillNo(bill);
        p.setMethod(code);
        p.setAmount(money(amount));
        p.setReference(reference == null || reference.isBlank() ? null : reference.trim());
        p.setNote(note == null || note.isBlank() ? null : note.trim());
        FolioPayment saved = paymentRepository.save(p);
        String memo = (kind == PaymentKind.DEPOSIT ? "Deposit " : "Payment ") + booking.getBookingNumber();
        if (kind == PaymentKind.DEPOSIT) {
            accountingService.postHotelDeposit(booking.getId(), memo, saved.getAmount());
        } else {
            accountingService.postHotelPayment(booking.getId(), memo, saved.getAmount());
        }
        auditLogService.record("HOTEL_" + kind.name() + "_TAKEN", memo + " " + saved.getAmount() + " " + code, "Booking", booking.getId());
        return saved;
    }

    /** A real tender: active, and neither "charge to room" nor store credit. */
    String payableMethod(String method) {
        String code = paymentMethodService.validateActiveCodeOrThrow(method);
        Set<String> blocked = new java.util.HashSet<>(paymentMethodService.listRoomChargeCodes());
        blocked.addAll(paymentMethodService.listCreditCodes());
        if (blocked.stream().anyMatch(code::equalsIgnoreCase)) {
            throw ApiException.badRequest("'" + code + "' cannot be used to pay a hotel bill");
        }
        return code;
    }

    // ---------------------------------------------------------------- cancel / settle

    /**
     * Cancel or no-show: charge the policy's fee (unless waived), let the held deposit pay for it, and either give back or keep what is left.
     * What the fee is not covered by the deposit stays on the booking as an amount owed.
     */
    @Transactional
    public void settleDepositOnCancel(Booking booking, String action, String refundMethod, boolean waiveFee, boolean noShow) {
        FeeQuote fee = waiveFee ? null : cancellationFee(booking, clock.today(), noShow);
        BigDecimal deposits = sum(paymentRepository.findByBookingIdOrderByIdAsc(booking.getId()), PaymentKind.DEPOSIT);
        BigDecimal feeGross = BigDecimal.ZERO;
        if (fee != null) {
            addFee(booking, fee);
            feeGross = fee.gross();
        }
        BigDecimal applied = deposits.min(feeGross);
        if (applied.signum() > 0) {
            accountingService.postHotelDepositApplied(booking.getId(), "Deposit applied to fee " + booking.getBookingNumber(), applied);
        }
        BigDecimal left = deposits.subtract(applied);
        if (left.signum() <= 0) return;
        boolean forfeit = "FORFEIT".equalsIgnoreCase(action);
        if (forfeit) {
            accountingService.postHotelDepositForfeited(booking.getId(), "Deposit kept " + booking.getBookingNumber(), left);
            FolioCharge line = new FolioCharge();
            line.setBooking(booking);
            line.setChargeType(ChargeType.EXTRA);
            line.setDescription("Deposit kept (cancellation)");
            line.setNet(left);
            line.setTax(BigDecimal.ZERO);
            line.setAmount(left);
            line.setRevenuePosted(true);
            chargeRepository.save(line);
            // the held deposit pays that line, so the bill still closes at zero
        } else {
            String code = refundMethod == null || refundMethod.isBlank() ? "CASH" : refundMethod.trim();
            code = paymentMethodService.validateActiveCodeOrThrow(code);
            FolioPayment refund = new FolioPayment();
            refund.setBooking(booking);
            refund.setKind(PaymentKind.REFUND);
            refund.setMethod(code);
            refund.setAmount(left);
            refund.setNote("Deposit refunded (cancellation)");
            paymentRepository.save(refund);
            accountingService.postHotelRefund(booking.getId(), "Deposit refund " + booking.getBookingNumber(), left, BigDecimal.ZERO);
        }
    }

    /**
     * Closes the bill at check-out. Room nights are completed first, the policy's early / late fees are added (unless waived), room revenue
     * is posted for the whole stay, the guest's payments are recorded, held deposits are applied, and anything owed back is refunded.
     * Bills sent to a company are not collected here: they move to the company's account. Throws (rolling the whole check-out back) if the
     * guest has not paid enough or a company is over its credit limit.
     */
    @Transactional
    public void settleAtCheckout(Booking booking, CheckOutRequest request, LocalDate today) {
        LocalDate upTo = stayUpTo(booking, today);
        ensureRoomCharges(booking, upTo);
        if (request == null || !Boolean.TRUE.equals(request.waiveFees())) {
            for (FeeQuote fee : checkoutFees(booking, today, clock.now())) addFee(booking, fee);
        }
        syncRoomRevenue(booking, true);

        Map<Integer, Customer> companies = companyBills(booking);
        Set<Integer> groups = groupBills(booking);
        List<FolioCharge> charges = chargeRepository.findByBookingIdOrderByIdAsc(booking.getId());
        BigDecimal due = BigDecimal.ZERO;
        Map<Integer, BigDecimal> companyDue = new LinkedHashMap<>();
        Map<Integer, BigDecimal> groupDue = new LinkedHashMap<>();
        for (FolioCharge c : charges) {
            if (c.isVoided()) continue;
            if (companies.containsKey(c.getBillNo())) companyDue.merge(c.getBillNo(), c.getAmount(), BigDecimal::add);
            else if (groups.contains(c.getBillNo())) groupDue.merge(c.getBillNo(), c.getAmount(), BigDecimal::add);
            else due = due.add(c.getAmount());
        }

        if (request != null && request.payments() != null) {
            for (PaymentLine line : request.payments()) {
                if (line.amount() == null || line.amount().signum() <= 0) continue;
                recordPayment(booking, PaymentKind.PAYMENT, line.method(), line.amount(), line.reference(), "Paid at check-out");
            }
        }
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId());
        BigDecimal deposits = sum(payments, PaymentKind.DEPOSIT);
        BigDecimal paid = sum(payments, PaymentKind.PAYMENT);
        BigDecimal refunded = sum(payments, PaymentKind.REFUND);
        if (deposits.add(paid).subtract(refunded).compareTo(due) < 0) {
            BigDecimal owing = due.subtract(deposits).subtract(paid).add(refunded);
            throw ApiException.badRequest("The bill is not settled — " + owing.setScale(2, RoundingMode.HALF_UP) + " is still owed. Add a payment to check out.");
        }
        BigDecimal applied = deposits.min(due.subtract(paid).max(BigDecimal.ZERO));
        BigDecimal refundFromDeposits = deposits.subtract(applied);
        BigDecimal refundFromBill = paid.subtract(due).max(BigDecimal.ZERO);
        if (applied.signum() > 0) {
            accountingService.postHotelDepositApplied(booking.getId(), "Deposit applied " + booking.getBookingNumber(), applied);
        }
        BigDecimal refund = refundFromDeposits.add(refundFromBill);
        if (refund.signum() > 0) {
            String code = request == null || request.refundMethod() == null || request.refundMethod().isBlank() ? "CASH" : request.refundMethod().trim();
            code = paymentMethodService.validateActiveCodeOrThrow(code);
            FolioPayment r = new FolioPayment();
            r.setBooking(booking);
            r.setKind(PaymentKind.REFUND);
            r.setMethod(code);
            r.setAmount(refund);
            r.setNote("Refund at check-out");
            paymentRepository.save(r);
            accountingService.postHotelRefund(booking.getId(), "Refund " + booking.getBookingNumber(), refundFromDeposits, refundFromBill);
        }
        for (Map.Entry<Integer, BigDecimal> e : companyDue.entrySet()) {
            if (e.getValue().signum() > 0) issueCompanyBill(booking, e.getKey(), companies.get(e.getKey()), money(e.getValue()), today);
        }
        for (Map.Entry<Integer, BigDecimal> e : groupDue.entrySet()) {
            if (e.getValue().signum() > 0) groupLedger.billFromStay(booking, e.getKey(), money(e.getValue()), today);
        }
    }

    /** The night the stay is billed up to (not including) when the guest leaves today: at least the arrival night. */
    LocalDate stayUpTo(Booking booking, LocalDate today) {
        return today.isAfter(booking.getCheckInDate()) ? today : booking.getCheckInDate().plusDays(1);
    }

    // ---------------------------------------------------------------- fees

    /** The fee for cancelling (or not turning up) under the hotel's policy, or null when none applies. */
    FeeQuote cancellationFee(Booking booking, LocalDate today, boolean noShow) {
        HotelSettings s = settingsService.entity();
        int nights = noShow ? s.getNoShowFeeNights() : s.getCancelFeeNights();
        if (nights <= 0) return null;
        if (!noShow && ChronoUnit.DAYS.between(today, booking.getCheckInDate()) >= s.getCancelFreeDays()) return null;
        nights = (int) Math.min(nights, booking.plannedNights());
        Map<LocalDate, BookingNight> map = nightMap(booking);
        BigDecimal net = BigDecimal.ZERO;
        LocalDate night = booking.getCheckInDate();
        for (int i = 0; i < nights; i++, night = night.plusDays(1)) net = net.add(nightNet(booking, night, map));
        if (net.signum() <= 0) return null;
        return new FeeQuote(noShow ? "NO_SHOW" : "CANCELLATION", (noShow ? "No-show fee" : "Late cancellation fee") + " (" + nights + (nights == 1 ? " night)" : " nights)"),
                nights, net, s.isTaxOnFees() ? tax(net) : BigDecimal.ZERO);
    }

    /** Fees due when the guest leaves now: leaving before the planned departure, and leaving after the late check-out time. */
    List<FeeQuote> checkoutFees(Booking booking, LocalDate today, java.time.LocalDateTime now) {
        HotelSettings s = settingsService.entity();
        List<FeeQuote> fees = new ArrayList<>();
        Map<LocalDate, BookingNight> map = nightMap(booking);
        LocalDate upTo = stayUpTo(booking, today);
        long unslept = ChronoUnit.DAYS.between(upTo, booking.getCheckOutDate());
        if (s.getEarlyDepartureFeeNights() > 0 && unslept > 0) {
            int nights = (int) Math.min(s.getEarlyDepartureFeeNights(), unslept);
            BigDecimal net = BigDecimal.ZERO;
            LocalDate night = upTo;
            for (int i = 0; i < nights; i++, night = night.plusDays(1)) net = net.add(nightNet(booking, night, map));
            if (net.signum() > 0) {
                fees.add(new FeeQuote("EARLY_DEPARTURE", "Early departure fee (" + nights + (nights == 1 ? " night)" : " nights)"), nights, net,
                        s.isTaxOnFees() ? tax(net) : BigDecimal.ZERO));
            }
        }
        BigDecimal pct = s.getLateCheckoutPercent() == null ? BigDecimal.ZERO : s.getLateCheckoutPercent();
        if (pct.signum() > 0 && s.getLateCheckoutFrom() != null && !today.isBefore(booking.getCheckOutDate())
                && !now.toLocalTime().isBefore(LocalTime.parse(s.getLateCheckoutFrom()))) {
            LocalDate last = booking.getCheckOutDate().minusDays(1);
            if (last.isBefore(booking.getCheckInDate())) last = booking.getCheckInDate();
            BigDecimal net = money(nightNet(booking, last, map).multiply(pct).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            if (net.signum() > 0) {
                fees.add(new FeeQuote("LATE_CHECKOUT", "Late check-out fee (after " + s.getLateCheckoutFrom() + ")", 0, net,
                        s.isTaxOnFees() ? tax(net) : BigDecimal.ZERO));
            }
        }
        return fees;
    }

    /** Puts a fee on the bill and posts its revenue. */
    private FolioCharge addFee(Booking booking, FeeQuote fee) {
        FolioCharge line = new FolioCharge();
        line.setBooking(booking);
        line.setChargeType(ChargeType.FEE);
        line.setBillNo(defaultGuestBill(booking));
        line.setDescription(fee.description());
        line.setNet(fee.net());
        line.setTax(fee.tax());
        line.setAmount(fee.gross());
        line.setRevenuePosted(true);
        FolioCharge saved = chargeRepository.save(line);
        accountingService.postHotelRevenue("HOTEL_FEE", saved.getId(), fee.description() + " — " + booking.getBookingNumber(), fee.net(), fee.tax(), BigDecimal.ZERO);
        auditLogService.record("HOTEL_FEE_CHARGED", booking.getBookingNumber() + ": " + fee.description() + " " + fee.gross(), "Booking", booking.getId());
        return saved;
    }

    /** What cancelling (or marking a no-show) would cost, and what would happen to the deposit — for the confirmation dialog. */
    @Transactional(readOnly = true)
    public CancelPreviewDto cancelPreview(Long bookingId, boolean noShow) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "cancel", BookingStatus.BOOKED);
        FeeQuote fee = cancellationFee(booking, clock.today(), noShow);
        BigDecimal held = sum(paymentRepository.findByBookingIdOrderByIdAsc(booking.getId()), PaymentKind.DEPOSIT);
        BigDecimal gross = fee == null ? BigDecimal.ZERO : fee.gross();
        BigDecimal fromDeposit = held.min(gross);
        return new CancelPreviewDto(fee == null ? null : fee.dto(), held, fromDeposit, held.subtract(fromDeposit), gross.subtract(fromDeposit));
    }

    /** What checking out now would add (fees) and collect, split between the guest and companies — for the check-out panel. */
    @Transactional(readOnly = true)
    public CheckoutPreviewDto checkoutPreview(Long bookingId) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "check out", BookingStatus.CHECKED_IN);
        LocalDate today = clock.today();
        List<FeeQuote> fees = checkoutFees(booking, today, clock.now());
        Map<Integer, Customer> companies = companyBills(booking);
        Set<Integer> groups = groupBills(booking);
        BigDecimal guest = BigDecimal.ZERO;
        BigDecimal company = BigDecimal.ZERO;
        for (FolioCharge c : chargeRepository.findByBookingIdOrderByIdAsc(booking.getId())) {
            if (c.isVoided()) continue;
            if (companies.containsKey(c.getBillNo()) || groups.contains(c.getBillNo())) company = company.add(c.getAmount());
            else guest = guest.add(c.getAmount());
        }
        Map<LocalDate, BookingNight> map = nightMap(booking);
        for (LocalDate night = booking.getCheckInDate(); night.isBefore(stayUpTo(booking, today)); night = night.plusDays(1)) {
            if (chargeRepository.existsByBookingIdAndChargeTypeAndNightDate(booking.getId(), ChargeType.ROOM, night)) continue;
            BigDecimal net = nightNet(booking, night, map);
            BigDecimal gross = net.add(tax(net));
            if (companies.containsKey(booking.getRoomBillNo()) || groups.contains(booking.getRoomBillNo())) company = company.add(gross);
            else guest = guest.add(gross);
        }
        for (FeeQuote f : fees) guest = guest.add(f.gross());
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId());
        BigDecimal received = sum(payments, PaymentKind.DEPOSIT).add(sum(payments, PaymentKind.PAYMENT)).subtract(sum(payments, PaymentKind.REFUND));
        return new CheckoutPreviewDto(fees.stream().map(FeeQuote::dto).toList(), guest, company, guest.subtract(received).max(BigDecimal.ZERO));
    }

    /** The guest behind a booking, if (and only if) they are checked in — used by room service and "Charge to room". */
    @Transactional(readOnly = true)
    public com.posapp.hotel.dto.HotelDtos.InHouseDto inHouseGuest(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId).orElseThrow(() -> ApiException.badRequest("Guest booking not found"));
        if (b.getStatus() != BookingStatus.CHECKED_IN || b.getRoom() == null) {
            throw ApiException.badRequest("That guest is not checked in");
        }
        return new com.posapp.hotel.dto.HotelDtos.InHouseDto(b.getId(), b.getBookingNumber(), b.getRoom().getNumber(), b.getGuest().getFullName());
    }

    // ---------------------------------------------------------------- bills (split folio, company bills)

    /** The numbers of the bills this stay has: bill 1 always, plus any added by splitting. */
    TreeSet<Integer> billNumbers(Booking booking) {
        TreeSet<Integer> numbers = new TreeSet<>();
        numbers.add(1);
        if (booking.getId() != null) {
            for (BookingBill b : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) numbers.add(b.getBillNo());
        }
        return numbers;
    }

    /** The bills sent to a company, by bill number. */
    Map<Integer, Customer> companyBills(Booking booking) {
        Map<Integer, Customer> map = new HashMap<>();
        if (booking.getId() == null) return map;
        for (BookingBill b : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) {
            if (b.getCompany() != null) map.put(b.getBillNo(), b.getCompany());
        }
        return map;
    }

    /** The bills charged to the stay's group, by bill number. */
    Set<Integer> groupBills(Booking booking) {
        Set<Integer> set = new HashSet<>();
        if (booking.getId() == null) return set;
        for (BookingBill b : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) {
            if (b.isToGroup()) set.add(b.getBillNo());
        }
        return set;
    }

    /** Where new extras, fees and room-service charges go: the first bill that is not sent to a company or the group (bill 1 if every bill is). */
    int defaultGuestBill(Booking booking) {
        Map<Integer, Customer> companies = companyBills(booking);
        Set<Integer> groups = groupBills(booking);
        for (int n : billNumbers(booking)) {
            if (!companies.containsKey(n) && !groups.contains(n)) return n;
        }
        return 1;
    }

    /** Takes a booking's bills off the group (before billing is set up again). */
    @Transactional
    public void clearGroupBills(Booking booking) {
        List<FolioCharge> charges = chargeRepository.findByBookingIdOrderByIdAsc(booking.getId());
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId());
        for (BookingBill b : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) {
            boolean wasGroup = b.isToGroup();
            if (wasGroup) {
                b.setToGroup(false);
                billRepository.save(b);
            }
            boolean used = charges.stream().anyMatch(c -> !c.isVoided() && c.getBillNo() == b.getBillNo())
                    || payments.stream().anyMatch(p -> p.getBillNo() == b.getBillNo());
            if (b.getBillNo() > 1 && !used && "Guest extras".equals(b.getLabel()) && booking.getRoomBillNo() != b.getBillNo()) billRepository.delete(b);
        }
    }

    /** Sets up the bills of a booking made for a group: the rooms (and, for MASTER_ALL, everything) on the group's bill. */
    @Transactional
    public void setupGroupBills(Booking booking, GroupBilling billing) {
        if (billing == null || billing == GroupBilling.INDIVIDUAL) return;
        BookingBill one = billRepository.findByBookingIdAndBillNo(booking.getId(), 1).orElseGet(() -> {
            BookingBill created = new BookingBill();
            created.setBooking(booking);
            created.setBillNo(1);
            return created;
        });
        one.setToGroup(true);
        one.setLabel("Group account");
        billRepository.save(one);
        if (billing == GroupBilling.MASTER_ALL) {
            List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(booking.getId());
            for (BookingBill other : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) {
                if (other.getBillNo() == 1 || other.getCompany() != null) continue;
                if (payments.stream().noneMatch(p -> p.getBillNo() == other.getBillNo())) {
                    other.setToGroup(true);
                    billRepository.save(other);
                }
            }
        }
        if (billing == GroupBilling.MASTER_ROOMS && billRepository.findByBookingIdAndBillNo(booking.getId(), 2).isEmpty()) {
            BookingBill two = new BookingBill();
            two.setBooking(booking);
            two.setBillNo(2);
            two.setLabel("Guest extras");
            billRepository.save(two);
        }
    }

    int requireBill(Booking booking, int billNo) {
        if (!billNumbers(booking).contains(billNo)) throw ApiException.badRequest("Bill " + billNo + " does not exist on this booking");
        return billNo;
    }

    private List<BillDto> bills(Booking booking, List<FolioCharge> charges, List<FolioPayment> payments) {
        Map<Integer, BookingBill> rows = new HashMap<>();
        for (BookingBill b : billRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) rows.put(b.getBillNo(), b);
        Map<Integer, CompanyBill> issued = new HashMap<>();
        for (CompanyBill cb : companyBillRepository.findByBookingIdOrderByBillNoAsc(booking.getId())) issued.put(cb.getBillNo(), cb);
        List<BillDto> out = new ArrayList<>();
        for (int n : billNumbers(booking)) {
            BigDecimal due = BigDecimal.ZERO;
            for (FolioCharge c : charges) if (!c.isVoided() && c.getBillNo() == n) due = due.add(c.getAmount());
            BigDecimal received = BigDecimal.ZERO;
            for (FolioPayment p : payments) {
                if (p.getBillNo() != n) continue;
                received = p.getKind() == PaymentKind.REFUND ? received.subtract(p.getAmount()) : received.add(p.getAmount());
            }
            BookingBill row = rows.get(n);
            Customer company = row == null ? null : row.getCompany();
            CompanyBill cb = issued.get(n);
            out.add(new BillDto(n, row == null ? null : row.getLabel(), company == null ? null : company.getId(), company == null ? null : company.getName(),
                    booking.getRoomBillNo() == n, due, received, due.subtract(received), cb == null ? null : cb.getId(),
                    cb == null ? null : cb.getBillNumber(), cb == null ? null : cb.getStatus(), row != null && row.isToGroup()));
        }
        return out;
    }

    private Customer companyOrThrow(Long id) {
        Customer c = customerRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Company not found"));
        if (!c.isCreditEnabled()) {
            throw ApiException.badRequest(c.getName() + " is not set up for credit — open the customer and enable credit (with a limit) first");
        }
        return c;
    }

    @Transactional
    public FolioDto createBill(Long bookingId, BillRequest request) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "split the bill of", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        TreeSet<Integer> numbers = billNumbers(booking);
        if (numbers.size() >= MAX_BILLS) throw ApiException.badRequest("A stay can have at most " + MAX_BILLS + " bills");
        BookingBill b = new BookingBill();
        b.setBooking(booking);
        b.setBillNo(numbers.last() + 1);
        b.setLabel(blankToNull(request == null ? null : request.label()));
        if (request != null && request.companyId() != null) b.setCompany(companyOrThrow(request.companyId()));
        if (request != null && Boolean.TRUE.equals(request.toGroup())) {
            if (booking.getGroup() == null) throw ApiException.badRequest("This booking is not part of a group");
            b.setCompany(null);
            b.setToGroup(true);
        }
        billRepository.save(b);
        auditLogService.record("HOTEL_BILL_ADDED", booking.getBookingNumber() + ": bill " + b.getBillNo() + (b.getCompany() == null ? "" : " to " + b.getCompany().getName()), "Booking", booking.getId());
        return toFolio(booking);
    }

    @Transactional
    public FolioDto updateBill(Long bookingId, int billNo, BillRequest request) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "change the bill of", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        requireBill(booking, billNo);
        BookingBill b = billRepository.findByBookingIdAndBillNo(bookingId, billNo).orElseGet(() -> {
            BookingBill created = new BookingBill();
            created.setBooking(booking);
            created.setBillNo(billNo);
            return created;
        });
        if (request.label() != null) b.setLabel(blankToNull(request.label()));
        if (Boolean.TRUE.equals(request.toGroup())) {
            if (booking.getGroup() == null) throw ApiException.badRequest("This booking is not part of a group");
            if (paymentRepository.findByBookingIdOrderByIdAsc(bookingId).stream().anyMatch(p -> p.getBillNo() == billNo)) {
                throw ApiException.badRequest("Bill " + billNo + " already has payments — move the charges to another bill instead");
            }
            b.setCompany(null);
            b.setToGroup(true);
        } else if (Boolean.TRUE.equals(request.clearCompany())) {
            b.setCompany(null);
            b.setToGroup(false);
        } else if (request.companyId() != null) {
            boolean hasMoney = paymentRepository.findByBookingIdOrderByIdAsc(bookingId).stream().anyMatch(p -> p.getBillNo() == billNo);
            if (hasMoney) throw ApiException.badRequest("Bill " + billNo + " already has payments — move the charges to another bill instead");
            b.setCompany(companyOrThrow(request.companyId()));
            b.setToGroup(false);
        }
        billRepository.save(b);
        auditLogService.record("HOTEL_BILL_CHANGED", booking.getBookingNumber() + ": bill " + billNo + (b.getCompany() == null ? " to the guest" : " to " + b.getCompany().getName()), "Booking", booking.getId());
        return toFolio(booking);
    }

    @Transactional
    public FolioDto deleteBill(Long bookingId, int billNo) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "change the bill of", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        if (billNo == 1) throw ApiException.badRequest("Bill 1 cannot be removed");
        requireBill(booking, billNo);
        boolean used = chargeRepository.findByBookingIdOrderByIdAsc(bookingId).stream().anyMatch(c -> !c.isVoided() && c.getBillNo() == billNo)
                || paymentRepository.findByBookingIdOrderByIdAsc(bookingId).stream().anyMatch(p -> p.getBillNo() == billNo);
        if (used) throw ApiException.badRequest("Bill " + billNo + " still has charges or payments — move them to another bill first");
        if (booking.getRoomBillNo() == billNo) throw ApiException.badRequest("The room nights are on bill " + billNo + " — put them on another bill first");
        billRepository.findByBookingIdAndBillNo(bookingId, billNo).ifPresent(billRepository::delete);
        return toFolio(booking);
    }

    /** Puts the room nights (existing and future) on a bill — e.g. the company's — leaving extras on the guest's. */
    @Transactional
    public FolioDto setRoomBill(Long bookingId, int billNo) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "change the bill of", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        requireBill(booking, billNo);
        booking.setRoomBillNo(billNo);
        bookingRepository.save(booking);
        for (FolioCharge c : chargeRepository.findByBookingIdOrderByIdAsc(bookingId)) {
            if (c.getChargeType() == ChargeType.ROOM && c.getBillNo() != billNo) {
                c.setBillNo(billNo);
                chargeRepository.save(c);
            }
        }
        auditLogService.record("HOTEL_ROOM_BILL_SET", booking.getBookingNumber() + ": room nights on bill " + billNo, "Booking", booking.getId());
        return toFolio(booking);
    }

    /** Moves one line to another bill of the same stay. */
    @Transactional
    public FolioDto assignCharge(Long bookingId, Long chargeId, int billNo) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "change the bill of", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        requireBill(booking, billNo);
        FolioCharge line = chargeFor(bookingId, chargeId);
        if (line.isVoided()) throw ApiException.badRequest("This charge is voided");
        line.setBillNo(billNo);
        chargeRepository.save(line);
        return toFolio(booking);
    }

    /** Moves a charge to another in-house guest's bill (the minibar was billed to the wrong room). Room nights cannot be moved. */
    @Transactional
    public FolioDto moveCharge(Long bookingId, Long chargeId, Long targetBookingId, String reason) {
        Booking source = getBooking(bookingId);
        Booking target = getBooking(targetBookingId);
        if (source.getId().equals(target.getId())) throw ApiException.badRequest("Choose a different guest — use the bill number to move a line within one stay");
        requireStatus(source, "move charges off", BookingStatus.CHECKED_IN);
        requireStatus(target, "move charges onto", BookingStatus.CHECKED_IN);
        FolioCharge line = chargeFor(bookingId, chargeId);
        if (line.isVoided()) throw ApiException.badRequest("This charge is voided");
        if (line.getChargeType() == ChargeType.ROOM || line.getChargeType() == ChargeType.FEE) {
            throw ApiException.badRequest("Room nights and fees stay with the booking they belong to");
        }
        String note = " (from " + source.getBookingNumber() + ")";
        String description = line.getDescription() == null ? "" : line.getDescription();
        if (description.length() + note.length() <= 250) line.setDescription(description + note);
        line.setBooking(target);
        line.setBillNo(defaultGuestBill(target));
        chargeRepository.save(line);
        String why = reason == null || reason.isBlank() ? "" : " — " + reason.trim();
        auditLogService.record("HOTEL_CHARGE_MOVED", description + " " + line.getAmount() + " moved from " + source.getBookingNumber() + " to " + target.getBookingNumber() + why, "Booking", source.getId());
        auditLogService.record("HOTEL_CHARGE_MOVED", description + " " + line.getAmount() + " received from " + source.getBookingNumber() + why, "Booking", target.getId());
        return toFolio(source);
    }

    private FolioCharge chargeFor(Long bookingId, Long chargeId) {
        FolioCharge line = chargeRepository.findById(chargeId).orElseThrow(() -> ApiException.notFound("Charge not found: " + chargeId));
        if (!line.getBooking().getId().equals(bookingId)) throw ApiException.notFound("Charge not found: " + chargeId);
        return line;
    }

    /** Moves a company's bill from the guest ledger to the company ledger, after checking its credit limit. */
    private void issueCompanyBill(Booking booking, int billNo, Customer company, BigDecimal amount, LocalDate today) {
        BigDecimal limit = company.getCreditLimit() == null ? BigDecimal.ZERO : company.getCreditLimit();
        BigDecimal owed = creditService.outstandingBalance(company.getId()).add(companyBillRepository.outstandingFor(company.getId()));
        if (owed.add(amount).compareTo(limit) > 0) {
            throw ApiException.badRequest("This bill would take " + company.getName() + " over its credit limit (limit " + money(limit) + ", already owing "
                    + money(owed) + ", this bill " + amount + "). Raise the limit, or ask the guest to pay.");
        }
        CompanyBill cb = new CompanyBill();
        cb.setCompany(company);
        cb.setBooking(booking);
        cb.setBillNo(billNo);
        cb.setDescription("Stay " + booking.getBookingNumber() + " — " + booking.getGuest().getFullName() + (billNo > 1 ? " (bill " + billNo + ")" : ""));
        cb.setAmount(amount);
        cb.setIssuedOn(today);
        if (company.getDefaultCreditTermDays() != null) cb.setDueDate(today.plusDays(company.getDefaultCreditTermDays()));
        CompanyBill saved = companyBillRepository.save(cb);
        saved.setBillNumber(String.format("CL-%05d", saved.getId()));
        companyBillRepository.save(saved);
        accountingService.postHotelCityLedger(saved.getId(), "City ledger " + saved.getBillNumber() + " " + company.getName(), amount);
        auditLogService.record("HOTEL_COMPANY_BILLED", saved.getBillNumber() + " " + amount + " to " + company.getName() + " (" + booking.getBookingNumber() + ")", "Booking", booking.getId());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ---------------------------------------------------------------- helpers

    Booking getBooking(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found: " + id));
    }

    private static void requireStatus(Booking booking, String what, BookingStatus... allowed) {
        for (BookingStatus s : allowed) {
            if (booking.getStatus() == s) return;
        }
        throw ApiException.badRequest("You cannot " + what + " a booking that is " + booking.getStatus().name().toLowerCase().replace('_', ' '));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
