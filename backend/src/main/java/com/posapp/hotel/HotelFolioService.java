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
import com.posapp.paymentmethod.PaymentMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
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

    public HotelFolioService(BookingRepository bookingRepository, FolioChargeRepository chargeRepository,
                             FolioPaymentRepository paymentRepository, HotelSettingsService settingsService,
                             AccountingService accountingService, PaymentMethodService paymentMethodService,
                             AuditLogService auditLogService) {
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
                payments.stream().map(HotelFolioService::toDto).toList(), chargesTotal, taxTotal, deposits, paid, refunds, balance);
    }

    /** Booking with its money summary. Before arrival the "charges" figure is the quote for the whole stay. */
    BookingDto toDto(Booking b) {
        List<FolioPayment> payments = paymentRepository.findByBookingIdOrderByIdAsc(b.getId());
        BigDecimal charges;
        if (b.getStatus() == BookingStatus.BOOKED) {
            charges = nightGross(b).multiply(BigDecimal.valueOf(b.plannedNights()));
        } else {
            charges = chargeRepository.findByBookingIdOrderByIdAsc(b.getId()).stream()
                    .filter(c -> !c.isVoided()).map(FolioCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal paid = sum(payments, PaymentKind.DEPOSIT).add(sum(payments, PaymentKind.PAYMENT)).subtract(sum(payments, PaymentKind.REFUND));
        return new BookingDto(b.getId(), b.getBookingNumber(), b.getGuest().getId(), b.getGuest().getFullName(), b.getGuest().getPhone(),
                b.getRoomType().getId(), b.getRoomType().getName(), b.getRoom() != null ? b.getRoom().getId() : null,
                b.getRoom() != null ? b.getRoom().getNumber() : null, b.getCheckInDate(), b.getCheckOutDate(), b.plannedNights(),
                b.getActualCheckIn(), b.getActualCheckOut(), b.getAdults(), b.getChildren(), b.getNightlyRate(), b.getExtraPersonRate(),
                b.nightlyTotal(), b.getStatus().name(), b.getSource(), b.getNotes(), b.getCancelReason(), charges, paid, charges.subtract(paid));
    }

    private static BigDecimal sum(List<FolioPayment> payments, PaymentKind kind) {
        return payments.stream().filter(p -> p.getKind() == kind).map(FolioPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FolioChargeDto toDto(FolioCharge c) {
        return new FolioChargeDto(c.getId(), c.getChargeType().name(), c.getDescription(), c.getNightDate(), c.getNet(), c.getTax(),
                c.getAmount(), c.getSourceType(), c.getSourceId(), c.isVoided(), c.getVoidReason(), c.getCreatedAt());
    }

    private static FolioPaymentDto toDto(FolioPayment p) {
        return new FolioPaymentDto(p.getId(), p.getKind().name(), p.getMethod(), p.getAmount(), p.getReference(), p.getNote(), p.getCreatedAt());
    }

    // ---------------------------------------------------------------- money helpers

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal tax(BigDecimal net) {
        return money(net.multiply(settingsService.taxPercent()).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** One night's room price before tax. */
    BigDecimal nightNet(Booking b) {
        return money(b.nightlyTotal());
    }

    /** One night's room price including tax. */
    BigDecimal nightGross(Booking b) {
        BigDecimal net = nightNet(b);
        return net.add(tax(net));
    }

    // ---------------------------------------------------------------- room charges and room revenue

    /** Adds the missing ROOM lines for every night from arrival up to (not including) {@code upToExclusive}. Returns how many were added. */
    @Transactional
    public int ensureRoomCharges(Booking booking, LocalDate upToExclusive) {
        int created = 0;
        BigDecimal net = nightNet(booking);
        BigDecimal tax = tax(net);
        String room = booking.getRoom() != null ? booking.getRoom().getNumber() : "?";
        for (LocalDate night = booking.getCheckInDate(); night.isBefore(upToExclusive); night = night.plusDays(1)) {
            if (chargeRepository.existsByBookingIdAndChargeTypeAndNightDate(booking.getId(), ChargeType.ROOM, night)) continue;
            FolioCharge line = new FolioCharge();
            line.setBooking(booking);
            line.setChargeType(ChargeType.ROOM);
            line.setNightDate(night);
            line.setDescription("Room " + room + " — night of " + night);
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
        BigDecimal desired = live ? nightGross(booking).multiply(BigDecimal.valueOf(booking.plannedNights())) : BigDecimal.ZERO;
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

    /** A deposit or a payment taken while the guest is booked or in-house. (Refunds only happen through cancel and check-out.) */
    @Transactional
    public FolioDto addPayment(Long bookingId, FolioPaymentRequest request) {
        Booking booking = getBooking(bookingId);
        requireStatus(booking, "take a payment for", BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
        PaymentKind kind;
        try {
            kind = PaymentKind.valueOf(request.kind().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown payment kind: " + request.kind());
        }
        if (kind == PaymentKind.REFUND) throw ApiException.badRequest("Refunds are made when cancelling or checking out");
        if (kind == PaymentKind.PAYMENT && booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.badRequest("Take a deposit before arrival; payments are taken once the guest is checked in");
        }
        recordPayment(booking, kind, request.method(), request.amount(), request.reference(), request.note());
        return toFolio(booking);
    }

    /** Stores the payment and posts it. DEPOSIT: cash in / held for the guest. PAYMENT: cash in / guest ledger down. */
    @Transactional
    public FolioPayment recordPayment(Booking booking, PaymentKind kind, String method, BigDecimal amount, String reference, String note) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("The amount must be greater than zero");
        String code = payableMethod(method);
        FolioPayment p = new FolioPayment();
        p.setBooking(booking);
        p.setKind(kind);
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
    private String payableMethod(String method) {
        String code = paymentMethodService.validateActiveCodeOrThrow(method);
        Set<String> blocked = new java.util.HashSet<>(paymentMethodService.listRoomChargeCodes());
        blocked.addAll(paymentMethodService.listCreditCodes());
        if (blocked.stream().anyMatch(code::equalsIgnoreCase)) {
            throw ApiException.badRequest("'" + code + "' cannot be used to pay a hotel bill");
        }
        return code;
    }

    // ---------------------------------------------------------------- cancel / settle

    /** Cancel or no-show: reverse the booking-time posting and either give the deposit back or keep it. */
    @Transactional
    public void settleDepositOnCancel(Booking booking, String action, String refundMethod) {
        BigDecimal deposits = sum(paymentRepository.findByBookingIdOrderByIdAsc(booking.getId()), PaymentKind.DEPOSIT);
        if (deposits.signum() <= 0) return;
        boolean forfeit = "FORFEIT".equalsIgnoreCase(action);
        if (forfeit) {
            accountingService.postHotelDepositForfeited(booking.getId(), "Deposit kept " + booking.getBookingNumber(), deposits);
            FolioCharge line = new FolioCharge();
            line.setBooking(booking);
            line.setChargeType(ChargeType.EXTRA);
            line.setDescription("Deposit kept (cancellation)");
            line.setNet(deposits);
            line.setTax(BigDecimal.ZERO);
            line.setAmount(deposits);
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
            refund.setAmount(deposits);
            refund.setNote("Deposit refunded (cancellation)");
            paymentRepository.save(refund);
            accountingService.postHotelRefund(booking.getId(), "Deposit refund " + booking.getBookingNumber(), deposits, BigDecimal.ZERO);
        }
    }

    /**
     * Closes the bill at check-out. Room nights are completed first, room revenue is posted for the whole stay, the guest's payments are
     * recorded, held deposits are applied, and anything owed back is refunded. Throws (rolling the whole check-out back) if the guest has
     * not paid enough.
     */
    @Transactional
    public void settleAtCheckout(Booking booking, CheckOutRequest request, LocalDate today) {
        LocalDate upTo = today.isAfter(booking.getCheckInDate()) ? today : booking.getCheckInDate().plusDays(1);
        ensureRoomCharges(booking, upTo);
        syncRoomRevenue(booking, true);

        List<FolioCharge> charges = chargeRepository.findByBookingIdOrderByIdAsc(booking.getId());
        BigDecimal due = charges.stream().filter(c -> !c.isVoided()).map(FolioCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

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
