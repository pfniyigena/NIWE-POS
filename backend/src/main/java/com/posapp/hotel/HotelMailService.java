package com.posapp.hotel;

import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelDtos.FolioDto;
import com.posapp.hotel.dto.HotelDtos.GuestDto;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * E-mails to the guest about a booking: the confirmation (with the confirmation letter attached), the pre-arrival reminder, the thank-you after
 * departure and the notice that an unpaid online booking was released. Each one is sent in the background and swallows its own failure — a mail
 * problem never stops a booking. A booking is marked once its mail has gone, so nothing is sent twice.
 */
@Service
public class HotelMailService {

    private static final Logger log = LoggerFactory.getLogger(HotelMailService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final MailService mailService;
    private final HotelFolioService folioService;
    private final GuestService guestService;
    private final HotelPdf pdf;
    private final HotelSettingsService settingsService;
    private final StoreSettingsService storeSettingsService;
    private final BookingRepository bookingRepository;
    private final TransactionTemplate tx;

    public HotelMailService(MailService mailService, HotelFolioService folioService, GuestService guestService, HotelPdf pdf,
                            HotelSettingsService settingsService, StoreSettingsService storeSettingsService, BookingRepository bookingRepository,
                            PlatformTransactionManager transactionManager) {
        this.mailService = mailService;
        this.folioService = folioService;
        this.guestService = guestService;
        this.pdf = pdf;
        this.settingsService = settingsService;
        this.storeSettingsService = storeSettingsService;
        this.bookingRepository = bookingRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public boolean ready() {
        return mailService.isConfigured();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String money(BigDecimal v, String symbol) {
        BigDecimal x = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.ENGLISH, "%,.2f", x) + (symbol == null || symbol.isBlank() ? "" : " " + symbol);
    }

    // ---------------------------------------------------------------- the four mails

    /** Confirmation with the letter attached. {@code to} overrides the guest's e-mail address (an online booking uses the address just typed). */
    @Async
    public void confirmation(Long bookingId, String to) {
        run(bookingId, to, "confirmation", (b, addr) -> {
            FolioDto folio = folioService.folio(bookingId);
            BookingDto bk = folio.booking();
            HotelSettings hs = settingsService.entity();
            StoreSettings store = storeSettingsService.getSettingsEntity();
            String cur = store.getCurrencySymbol();
            StringBuilder h = new StringBuilder();
            h.append("<p>Hello ").append(esc(bk.guestName())).append(",</p>");
            h.append("<p>Your stay at <strong>").append(esc(hotelName(store))).append("</strong> is booked.</p>");
            h.append(stayTable(b, bk, hs, cur));
            if (b.getDepositDue() != null && b.getDepositDue().signum() > 0 && bk.paidTotal().signum() <= 0) {
                h.append("<p>To keep the room we need a deposit of <strong>").append(money(b.getDepositDue(), cur)).append("</strong>");
                if (b.getHoldExpiresAt() != null) {
                    h.append(" by ").append(b.getHoldExpiresAt().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH)));
                }
                h.append("; after that the room is released.</p>");
                if (hs.getOnlinePaymentInstructions() != null && !hs.getOnlinePaymentInstructions().isBlank()) {
                    h.append("<p>").append(esc(hs.getOnlinePaymentInstructions()).replace("\n", "<br>")).append("</p>");
                }
            }
            if (b.getConfirmationCode() != null) {
                h.append("<p>To look up or cancel this booking, use your booking code <strong>").append(esc(b.getConfirmationCode()))
                        .append("</strong> with the phone number you gave.</p>");
            }
            h.append(contact(store));
            List<EmailAttachment> files = List.of();
            try {
                GuestDto g = guestService.get(bk.guestId());
                files = List.of(new EmailAttachment(bk.bookingNumber() + "-confirmation.pdf", "application/pdf", pdf.confirmationLetter(folio, g, settingsService.get(), store)));
            } catch (Exception e) {
                log.warn("No confirmation letter attached to {}: {}", bk.bookingNumber(), e.getMessage());
            }
            mailService.send(addr, "Booking confirmed: " + bk.bookingNumber() + " at " + hotelName(store), h.toString(), files);
        }, (b, now) -> b.setConfirmationMailedAt(now));
    }

    @Async
    public void prearrival(Long bookingId) {
        run(bookingId, null, "pre-arrival", (b, addr) -> {
            HotelSettings hs = settingsService.entity();
            StoreSettings store = storeSettingsService.getSettingsEntity();
            BookingDto bk = folioService.folio(bookingId).booking();
            StringBuilder h = new StringBuilder();
            h.append("<p>Hello ").append(esc(bk.guestName())).append(",</p>");
            h.append("<p>We look forward to welcoming you on <strong>").append(b.getCheckInDate().format(DAY)).append("</strong>. Check-in is from ")
                    .append(esc(hs.getCheckInTime())).append("; check-out is by ").append(esc(hs.getCheckOutTime())).append(".</p>");
            h.append(stayTable(b, bk, hs, store.getCurrencySymbol()));
            if (b.getGuest().getPreferences() != null && !b.getGuest().getPreferences().isBlank()) {
                h.append("<p>We have noted your preferences: ").append(esc(b.getGuest().getPreferences())).append(".</p>");
            }
            h.append("<p>Please bring a valid passport or national ID — we register every guest on arrival. Tell us if your arrival time changes.</p>");
            h.append(contact(store));
            mailService.send(addr, "See you on " + b.getCheckInDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)) + " — " + hotelName(store), h.toString(), List.of());
        }, (b, now) -> b.setPrearrivalMailedAt(now));
    }

    @Async
    public void thanks(Long bookingId) {
        run(bookingId, null, "thank-you", (b, addr) -> {
            HotelSettings hs = settingsService.entity();
            StoreSettings store = storeSettingsService.getSettingsEntity();
            StringBuilder h = new StringBuilder();
            h.append("<p>Hello ").append(esc(b.getGuest().getFullName())).append(",</p>");
            if (hs.getThanksMessage() != null && !hs.getThanksMessage().isBlank()) {
                h.append("<p>").append(esc(hs.getThanksMessage()).replace("\n", "<br>")).append("</p>");
            } else {
                h.append("<p>Thank you for staying with us at ").append(esc(hotelName(store))).append(". We hope you enjoyed your stay and would love to welcome you back.</p>");
            }
            if (hs.getReviewUrl() != null && !hs.getReviewUrl().isBlank()) {
                h.append("<p><a href=\"").append(esc(hs.getReviewUrl())).append("\">Tell us how it went</a></p>");
            }
            h.append(contact(store));
            mailService.send(addr, "Thank you for staying with us — " + hotelName(store), h.toString(), List.of());
        }, (b, now) -> b.setThanksMailedAt(now));
    }

    /** Tells the guest an unpaid online booking was released. */
    @Async
    public void released(Long bookingId) {
        run(bookingId, null, "release notice", (b, addr) -> {
            StoreSettings store = storeSettingsService.getSettingsEntity();
            String h = "<p>Hello " + esc(b.getGuest().getFullName()) + ",</p><p>We did not receive the deposit for booking <strong>" + esc(b.getBookingNumber())
                    + "</strong> (" + b.getCheckInDate().format(DAY) + " to " + b.getCheckOutDate().format(DAY) + ") in time, so the room has been released.</p>"
                    + "<p>You are welcome to book again on our website, or contact us.</p>" + contact(store);
            mailService.send(addr, "Booking released: " + b.getBookingNumber(), h, List.of());
        }, null);
    }

    // ---------------------------------------------------------------- plumbing

    private void run(Long bookingId, String to, String what, MailJob job, BiConsumer<Booking, Instant> mark) {
        try {
            Booking b = bookingRepository.findById(bookingId).orElse(null);
            if (b == null) return;
            String addr = to != null && !to.isBlank() ? to : b.getGuest().getEmail();
            if (addr == null || addr.isBlank() || !mailService.isConfigured()) return;
            job.send(b, addr);
            if (mark != null) {
                tx.executeWithoutResult(s -> bookingRepository.findById(bookingId).ifPresent(x -> {
                    mark.accept(x, Instant.now());
                    bookingRepository.save(x);
                }));
            }
        } catch (Exception e) {
            log.warn("Could not send the {} e-mail for booking {}: {}", what, bookingId, e.getMessage());
        }
    }

    private interface MailJob {
        void send(Booking booking, String address) throws Exception;
    }

    private static String hotelName(StoreSettings s) {
        return s.getBusinessName() == null || s.getBusinessName().isBlank() ? "our hotel" : s.getBusinessName();
    }

    private static String contact(StoreSettings s) {
        StringBuilder c = new StringBuilder("<p style=\"color:#555\">");
        c.append(esc(hotelName(s)));
        if (s.getAddress() != null && !s.getAddress().isBlank()) c.append("<br>").append(esc(s.getAddress()));
        if (s.getPhone() != null && !s.getPhone().isBlank()) c.append("<br>Phone: ").append(esc(s.getPhone()));
        if (s.getEmail() != null && !s.getEmail().isBlank()) c.append("<br>").append(esc(s.getEmail()));
        return c.append("</p>").toString();
    }

    private static String stayTable(Booking b, BookingDto bk, HotelSettings hs, String cur) {
        long nights = bk.nights();
        BigDecimal total = bk.chargesTotal();
        return "<table cellpadding=\"4\" style=\"border-collapse:collapse\">"
                + row("Booking", esc(bk.bookingNumber()))
                + row("Room", esc(bk.roomTypeName()))
                + row("Arrival", b.getCheckInDate().format(DAY) + " (from " + esc(hs.getCheckInTime()) + ")")
                + row("Departure", b.getCheckOutDate().format(DAY) + " (by " + esc(hs.getCheckOutTime()) + ")")
                + row("Nights", String.valueOf(nights))
                + row("Guests", bk.adults() + (bk.children() > 0 ? " adults, " + bk.children() + " children" : bk.adults() == 1 ? " adult" : " adults"))
                + (total != null && total.signum() > 0 ? row("Price for the stay", money(total, cur) + " (tax included)") : "")
                + "</table>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"color:#555\">" + label + "</td><td><strong>" + value + "</strong></td></tr>";
    }
}
