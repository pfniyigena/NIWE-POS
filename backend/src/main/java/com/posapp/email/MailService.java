package com.posapp.email;

import com.posapp.common.ApiException;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

/**
 * Fourier: "Configure smtp for sending email. This also goes to sending other documents like
 * quotations, invoices, receipts, etc to the customer's email" — the one place any of that
 * actually talks SMTP. Deliberately does *not* rely on Spring Boot's own {@code spring.mail.*}
 * auto-configured {@code JavaMailSender} bean, because that's fixed at startup from
 * application.properties; the whole point of Admin &gt; Settings &gt; Email is that an admin can
 * set/change SMTP credentials at runtime with no redeploy, so this builds a fresh
 * {@link JavaMailSenderImpl} from whatever's currently saved on {@link StoreSettings} every time
 * it sends. Sending is inherently occasional (a handful of emails per day at most, never a hot
 * path like checkout), so paying JavaMailSenderImpl's small construction cost per send is a
 * non-issue in exchange for that runtime-configurability.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final StoreSettingsRepository storeSettingsRepository;

    public MailService(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    /** Whether an admin has actually turned email on and given it a host to talk to — every caller checks this first so a missing/incomplete setup fails with a clear message rather than a raw connection error. */
    public boolean isConfigured() {
        StoreSettings s = getSettings();
        return s.isSmtpEnabled() && s.getSmtpHost() != null && !s.getSmtpHost().isBlank();
    }

    /**
     * Sends one HTML email with optional attachments (a generated PDF, for the
     * quotation/invoice/receipt callers). Throws {@link ApiException#badRequest} — never a raw
     * mail exception — for every failure mode: not configured, bad recipient, or the SMTP server
     * itself rejecting the connection/credentials/message, so every caller (QuotationService,
     * InvoiceService, OrderService) gets one consistent, user-facing error shape whichever of
     * those actually went wrong.
     */
    public void send(String toEmail, String subject, String htmlBody, List<EmailAttachment> attachments) {
        if (toEmail == null || toEmail.isBlank()) {
            throw ApiException.badRequest("No email address to send to");
        }
        StoreSettings settings = getSettings();
        if (!settings.isSmtpEnabled() || settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()) {
            throw ApiException.badRequest("Email isn't configured yet — set up SMTP in Admin > Settings > Email first");
        }
        try {
            JavaMailSenderImpl sender = buildSender(settings);
            MimeMessage message = sender.createMimeMessage();
            // multipart=true unconditionally — even a caller with no attachments (a plain
            // password-reset email) sends fine as a one-part "multipart" message; keeping this
            // one code path for every send is simpler than branching on attachments.isEmpty().
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            String fromAddress = settings.getSmtpFromEmail() != null && !settings.getSmtpFromEmail().isBlank()
                    ? settings.getSmtpFromEmail() : settings.getSmtpUsername();
            String fromName = settings.getSmtpFromName() != null && !settings.getSmtpFromName().isBlank()
                    ? settings.getSmtpFromName() : settings.getBusinessName();
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }
            for (EmailAttachment attachment : attachments) {
                helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.bytes()), attachment.contentType());
            }
            sender.send(message);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to send email to {}: {}", toEmail, ex.getMessage());
            throw ApiException.badRequest("Failed to send email: " + ex.getMessage());
        }
    }

    private JavaMailSenderImpl buildSender(StoreSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getSmtpHost());
        sender.setPort(settings.getSmtpPort() != null ? settings.getSmtpPort() : 587);
        sender.setUsername(settings.getSmtpUsername());
        sender.setPassword(settings.getSmtpPassword());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        // starttls.enable/required are set either way — several real cPanel/Plesk-style mail
        // hosts (mangatek.rw included, per Fourier's own working spring.mail.* recipe for it)
        // expect both properties present regardless of which one actually ends up negotiating
        // the handshake; setting both matches that recipe exactly rather than guessing which one
        // the server cares about.
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        if (settings.isSmtpUseSsl()) {
            // Implicit TLS from the first byte (the port 465 convention).
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (settings.isSmtpSkipCertCheck()) {
            // Fourier: hit a genuine certificate-hostname mismatch on a real mail host
            // (mangatek.rw) — its certificate simply doesn't list the configured host name, and
            // that's true regardless of which JavaMail SSL setup connects to it (confirmed by
            // trying two different approaches here with the identical failure both times). This
            // is the deliberate, opt-in escape hatch for that — see
            // StoreSettings.smtpSkipCertCheck's javadoc for the tradeoff being accepted.
            // mail.smtp.ssl.trust scoped to just this host makes JavaMail use its own
            // MailSSLSocketFactory with a trust-all TrustManager *for this host only* (also
            // skips its hostname check for it) rather than disabling verification globally for
            // every SSL connection this JVM might ever make; checkserveridentity=false is set
            // too as a belt-and-suspenders in case that separate check still fires on some
            // JavaMail versions even with ssl.trust set.
            props.put("mail.smtp.ssl.trust", settings.getSmtpHost());
            props.put("mail.smtp.ssl.checkserveridentity", "false");
        }
        // Never let one send hang the caller's own request indefinitely on an unreachable/
        // misconfigured host — 15s is generous for a real SMTP server, short enough that a wrong
        // host/port fails fast with a clear error instead of the request timing out upstream first.
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    /** Same "singleton row, create-on-demand" pattern as StoreSettingsService.getOrCreate — deliberately not reusing that service here to avoid a circular dependency (StoreSettingsService has no reason to depend on mail, but keeping this self-contained avoids ever creating one even by accident). */
    private StoreSettings getSettings() {
        return storeSettingsRepository.findAll().stream().findFirst().orElseGet(() -> storeSettingsRepository.save(new StoreSettings()));
    }
}
