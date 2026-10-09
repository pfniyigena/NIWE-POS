package com.posapp.salessettings;

import com.posapp.common.ApiException;
import com.posapp.salessettings.dto.SalesSettingsDto;
import com.posapp.salessettings.dto.SalesSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fourier: "Every time invoice is ISSUED send try to send email to customer and to configured
 * emails. (Add invoices receiver emails in sales settings)" — same singleton "exactly one row,
 * create-on-demand" convention as {@code com.posapp.helpdesk.HelpDeskSettingsService}, just for
 * {@link SalesSettings} instead. The entity stores the list as one comma-separated TEXT column
 * (see {@link SalesSettings#getInvoiceReceiverEmails()}'s own javadoc for why); this service is
 * the only place that string is ever split or joined — every other caller (the admin settings
 * screen via {@link #getSettings}, and {@code com.posapp.invoice.InvoiceMailService} via {@link
 * #getInvoiceReceiverEmailList}) only ever sees the parsed {@code List<String>} form.
 */
@Service
public class SalesSettingsService {

    /** Deliberately loose — same bar as {@code HelpDeskSettingsService.SIMPLE_EMAIL}: this just rejects obvious typos before they're saved, not full RFC 5322 validation. */
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SalesSettingsRepository repository;

    public SalesSettingsService(SalesSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * Not {@code readOnly} — {@link #getOrCreate} may need to INSERT the one settings row the
     * very first time this is ever called, and PostgreSQL rejects any write, including that
     * first-time row creation, inside a read-only transaction.
     */
    @Transactional
    public SalesSettingsDto getSettings() {
        SalesSettings settings = getOrCreate();
        return SalesSettingsDto.from(settings, parse(settings.getInvoiceReceiverEmails()));
    }

    /**
     * {@code InvoiceMailService}'s own entry point — the parsed, trimmed, never-null list every
     * issued-invoice email actually copies in, alongside the customer's own address.
     */
    @Transactional
    public List<String> getInvoiceReceiverEmailList() {
        return parse(getOrCreate().getInvoiceReceiverEmails());
    }

    @Transactional
    public SalesSettingsDto updateSettings(SalesSettingsRequest request) {
        List<String> emails = request.getInvoiceReceiverEmails() == null ? List.of() : request.getInvoiceReceiverEmails();
        List<String> cleaned = new ArrayList<>();
        for (String raw : emails) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            if (!SIMPLE_EMAIL.matcher(trimmed).matches()) {
                throw ApiException.badRequest("\"" + trimmed + "\" doesn't look like a valid email address");
            }
            cleaned.add(trimmed);
        }

        SalesSettings settings = getOrCreate();
        settings.setInvoiceReceiverEmails(cleaned.isEmpty() ? null : String.join(",", cleaned));
        SalesSettings saved = repository.save(settings);
        return SalesSettingsDto.from(saved, cleaned);
    }

    private List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private SalesSettings getOrCreate() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new SalesSettings()));
    }
}
