package com.posapp.booking;

import com.posapp.i18n.I18n;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * E-mails to the guest about a booking. The asynchronous methods swallow their own failures: a mail problem never blocks a booking.
 * Confirmations, moves and reminders carry a calendar file (.ics) so the guest can add the visit to a calendar.
 */
@Service
public class BookingMailService {

    private static final Logger log = LoggerFactory.getLogger(BookingMailService.class);
    private static DateTimeFormatter whenFmt() { return I18n.pattern("EEEE d MMMM yyyy 'at' HH:mm"); }

    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;

    public BookingMailService(MailService mailService, StoreSettingsService storeSettingsService) {
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean mailConfigured() {
        return mailService.isConfigured();
    }

    @Async
    public void confirmed(Appointment a) {
        send(a, I18n.t("Booking confirmed: ") + a.getServiceName(),
                I18n.t("<p>Hello ") + esc(a.getGuestName()) + I18n.t(",</p><p>Your booking is <strong>confirmed</strong>.</p>") + details(a)
                        + I18n.t("<p>Keep this code if you need to look up, move or cancel your booking: <strong>") + esc(a.getConfirmationCode()) + "</strong></p>", true);
    }

    @Async
    public void moved(Appointment a, LocalDateTime oldStart) {
        send(a, I18n.t("Booking moved: ") + a.getServiceName(),
                I18n.t("<p>Hello ") + esc(a.getGuestName()) + I18n.t(",</p><p>Your booking has been <strong>moved</strong>")
                        + (oldStart != null ? I18n.t(" from ") + oldStart.format(whenFmt()) : "") + I18n.t(". The new time is:</p>") + details(a)
                        + I18n.t("<p>Your booking code: <strong>") + esc(a.getConfirmationCode()) + "</strong></p>", true);
    }

    @Async
    public void awaitingPayment(Appointment a, String instructions) {
        send(a, I18n.t("Booking received — payment needed: ") + a.getServiceName(),
                I18n.t("<p>Hello ") + esc(a.getGuestName()) + I18n.t(",</p><p>We are holding your time, but the booking is confirmed only once <strong>")
                        + a.getAmountDue().toPlainString() + I18n.t("</strong> is paid") + (a.getInvoiceNumber() != null ? " (invoice " + esc(a.getInvoiceNumber()) + ")" : "") + ".</p>"
                        + details(a) + (instructions != null ? "<p>" + esc(instructions).replace("\n", "<br>") + "</p>" : "")
                        + I18n.t("<p>Your booking code: <strong>") + esc(a.getConfirmationCode()) + "</strong></p>", false);
    }

    @Async
    public void cancelled(Appointment a) {
        send(a, I18n.t("Booking cancelled: ") + a.getServiceName(),
                I18n.t("<p>Hello ") + esc(a.getGuestName()) + I18n.t(",</p><p>Your booking has been <strong>cancelled</strong>.</p>") + details(a)
                        + (a.getCancelReason() != null ? "<p>" + esc(a.getCancelReason()) + "</p>" : ""), false);
    }

    /** Sent straight away (not asynchronously) so the caller knows whether it went. Returns false when there is no address or the mail failed. */
    public boolean reminder(Appointment a) {
        if (a.getGuestEmail() == null || a.getGuestEmail().isBlank()) return false;
        try {
            mailService.send(a.getGuestEmail(), I18n.t("Reminder: ") + a.getServiceName() + I18n.t(" on ") + a.getStartAt().format(I18n.pattern("d MMM 'at' HH:mm")),
                    I18n.t("<p>Hello ") + esc(a.getGuestName()) + I18n.t(",</p><p>This is a reminder of your booking.</p>") + details(a)
                            + I18n.t("<p>Need to change it? Use your booking code <strong>") + esc(a.getConfirmationCode()) + I18n.t("</strong> on our booking page, or call us.</p>"),
                    attachments(a));
            return true;
        } catch (Exception e) {
            log.warn("Could not send the reminder for booking {}: {}", a.getNumber(), e.getMessage());
            return false;
        }
    }

    private String details(Appointment a) {
        return "<p>" + esc(a.getServiceName()) + "<br>" + a.getStartAt().format(whenFmt())
                + (a.getStaffName() != null ? I18n.t("<br>With ") + esc(a.getStaffName()) : "")
                + (a.getResourceName() != null ? "<br>" + esc(a.getResourceName()) : "") + "</p>";
    }

    private List<EmailAttachment> attachments(Appointment a) {
        try {
            StoreSettings st = storeSettingsService.getSettingsEntity();
            ZoneId zone = st.getTimeZone() != null && !st.getTimeZone().isBlank() ? ZoneId.of(st.getTimeZone().trim()) : ZoneId.systemDefault();
            return List.of(new EmailAttachment(BookingIcs.filename(a), "text/calendar; charset=utf-8; method=PUBLISH", BookingIcs.build(a, zone, st.getBusinessName())));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void send(Appointment a, String subject, String html, boolean withCalendar) {
        if (a.getGuestEmail() == null || a.getGuestEmail().isBlank()) return;
        try {
            mailService.send(a.getGuestEmail(), subject, html, withCalendar ? attachments(a) : List.of());
        } catch (Exception e) {
            log.warn("Could not e-mail booking {}: {}", a.getNumber(), e.getMessage());
        }
    }
}
