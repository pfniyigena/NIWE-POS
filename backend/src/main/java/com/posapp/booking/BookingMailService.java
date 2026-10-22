package com.posapp.booking;

import com.posapp.email.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** E-mails to the guest about a booking. Every method is asynchronous and swallows its own failures: a mail problem never blocks a booking. */
@Service
public class BookingMailService {

    private static final Logger log = LoggerFactory.getLogger(BookingMailService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm");

    private final MailService mailService;

    public BookingMailService(MailService mailService) {
        this.mailService = mailService;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Async
    public void confirmed(Appointment a) {
        send(a, "Booking confirmed: " + a.getServiceName(),
                "<p>Hello " + esc(a.getGuestName()) + ",</p><p>Your booking is <strong>confirmed</strong>.</p>" + details(a)
                        + "<p>Keep this code if you need to look up or cancel your booking: <strong>" + esc(a.getConfirmationCode()) + "</strong></p>");
    }

    @Async
    public void awaitingPayment(Appointment a, String instructions) {
        send(a, "Booking received — payment needed: " + a.getServiceName(),
                "<p>Hello " + esc(a.getGuestName()) + ",</p><p>We are holding your time, but the booking is confirmed only once <strong>"
                        + a.getAmountDue().toPlainString() + "</strong> is paid" + (a.getInvoiceNumber() != null ? " (invoice " + esc(a.getInvoiceNumber()) + ")" : "") + ".</p>"
                        + details(a) + (instructions != null ? "<p>" + esc(instructions).replace("\n", "<br>") + "</p>" : "")
                        + "<p>Your booking code: <strong>" + esc(a.getConfirmationCode()) + "</strong></p>");
    }

    @Async
    public void cancelled(Appointment a) {
        send(a, "Booking cancelled: " + a.getServiceName(),
                "<p>Hello " + esc(a.getGuestName()) + ",</p><p>Your booking has been <strong>cancelled</strong>.</p>" + details(a)
                        + (a.getCancelReason() != null ? "<p>" + esc(a.getCancelReason()) + "</p>" : ""));
    }

    private String details(Appointment a) {
        return "<p>" + esc(a.getServiceName()) + "<br>" + a.getStartAt().format(WHEN)
                + (a.getStaffName() != null ? "<br>With " + esc(a.getStaffName()) : "")
                + (a.getResourceName() != null ? "<br>" + esc(a.getResourceName()) : "") + "</p>";
    }

    private void send(Appointment a, String subject, String html) {
        if (a.getGuestEmail() == null || a.getGuestEmail().isBlank()) return;
        try {
            mailService.send(a.getGuestEmail(), subject, html, List.of());
        } catch (Exception e) {
            log.warn("Could not e-mail booking {}: {}", a.getNumber(), e.getMessage());
        }
    }
}
