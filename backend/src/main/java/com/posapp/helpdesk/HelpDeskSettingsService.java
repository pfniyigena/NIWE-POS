package com.posapp.helpdesk;

import com.posapp.common.ApiException;
import com.posapp.helpdesk.dto.HelpDeskSettingsDto;
import com.posapp.helpdesk.dto.HelpDeskSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fourier: "Once the ticket is created it should send email to the helpdesk configured
 * emails(this should be set to the helpdesk setting and the email can be a list." — same
 * singleton "exactly one row, create-on-demand" convention as {@code
 * com.posapp.settings.StoreSettingsService.getOrCreate}, just for {@link HelpDeskSettings}
 * instead. The entity stores the list as one comma-separated TEXT column (see {@link
 * HelpDeskSettings#getNotificationEmails()}'s own javadoc for why); this service is the only
 * place that string is ever split or joined — every other caller (the admin settings screen via
 * {@link #getSettings}, and {@link HelpDeskMailService} via {@link #getNotificationEmailList})
 * only ever sees the parsed {@code List<String>} form.
 */
@Service
public class HelpDeskSettingsService {

    /** Deliberately loose — this just rejects obvious typos ("notanemail") before they're saved, not full RFC 5322 validation. Same bar as the one other hand-rolled email check in this app (PasswordResetService doesn't have one; this is simpler than the jakarta `@Email` annotation route since the field is a List, not a single String). */
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final HelpDeskSettingsRepository repository;

    public HelpDeskSettingsService(HelpDeskSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * Not {@code readOnly}, unlike most "just reading settings" methods elsewhere in this app
     * (e.g. {@code StoreSettingsService.getSettings}) — {@link #getOrCreate} may need to INSERT
     * the one settings row the very first time this is ever called, and PostgreSQL rejects any
     * write, including that first-time row creation, inside a read-only transaction.
     */
    @Transactional
    public HelpDeskSettingsDto getSettings() {
        HelpDeskSettings settings = getOrCreate();
        return HelpDeskSettingsDto.from(settings, parse(settings.getNotificationEmails()));
    }

    /**
     * {@link HelpDeskMailService}'s own entry point — the parsed, trimmed, never-null list every
     * notification send actually mails to. Same not-{@code readOnly} reasoning as {@link
     * #getSettings}: this can also be the very first touch of the settings row.
     */
    @Transactional
    public List<String> getNotificationEmailList() {
        return parse(getOrCreate().getNotificationEmails());
    }

    @Transactional
    public HelpDeskSettingsDto updateSettings(HelpDeskSettingsRequest request) {
        List<String> emails = request.getNotificationEmails() == null ? List.of() : request.getNotificationEmails();
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

        HelpDeskSettings settings = getOrCreate();
        settings.setNotificationEmails(cleaned.isEmpty() ? null : String.join(",", cleaned));
        HelpDeskSettings saved = repository.save(settings);
        return HelpDeskSettingsDto.from(saved, cleaned);
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

    private HelpDeskSettings getOrCreate() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new HelpDeskSettings()));
    }
}
