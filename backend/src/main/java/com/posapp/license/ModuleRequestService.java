package com.posapp.license;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.license.LicenseDtos.LicenseStatus;
import com.posapp.license.LicenseDtos.ModuleRequestDto;
import com.posapp.license.LicenseDtos.RequestResult;
import com.posapp.modules.ModuleKey;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * "Activated by request": an admin asks for a module that is not in the license. The request is saved and, when e-mail is set up and
 * {@code app.license.vendor-email} names an address, e-mailed to NIWE. Approval is NIWE sending a new signed license; installing it closes the
 * request ({@link LicenseService#install}). Nothing here can switch a module on.
 */
@Service
public class ModuleRequestService {

    private static final Logger log = LoggerFactory.getLogger(ModuleRequestService.class);

    private final ModuleRequestRepository requests;
    private final LicenseService licenseService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final StoreSettingsService storeSettingsService;
    private final String vendorEmail;

    public ModuleRequestService(ModuleRequestRepository requests, LicenseService licenseService, MailService mailService, AuditLogService auditLogService, StoreSettingsService storeSettingsService,
                                @Value("${app.license.vendor-email:}") String vendorEmail) {
        this.requests = requests;
        this.licenseService = licenseService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.storeSettingsService = storeSettingsService;
        this.vendorEmail = vendorEmail == null ? "" : vendorEmail.trim();
    }

    @Transactional(readOnly = true)
    public List<ModuleRequestDto> list() {
        return requests.findAllByOrderByCreatedAtDesc().stream().map(ModuleRequestService::toDto).toList();
    }

    public RequestResult create(ModuleKey key, String note, String user) {
        if (key.core()) {
            throw ApiException.badRequest(key.label() + I18n.t(" is a core module and is always available."));
        }
        if (licenseService.isLicensed(key)) {
            throw ApiException.conflict(key.label() + I18n.t(" is already part of the license."));
        }
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) {
            throw ApiException.badRequest(I18n.t("The note is too long (500 characters at most)."));
        }
        if (cleanNote != null && cleanNote.isEmpty()) cleanNote = null;
        if (requests.findFirstByModuleKeyAndStatus(key, ModuleRequestStatus.PENDING).isPresent()) {
            throw ApiException.conflict(key.label() + I18n.t(" has already been requested. Wait for NIWE's new license, or withdraw the request first."));
        }
        ModuleRequest r = new ModuleRequest();
        r.setModuleKey(key);
        r.setNote(cleanNote);
        r.setRequestedBy(user == null || user.isBlank() ? "unknown" : user);
        r = requests.save(r);
        auditLogService.record("MODULE_REQUESTED", "Requested module " + key.label() + (cleanNote != null ? ": " + cleanNote : ""));

        LicenseStatus status = licenseService.status();
        String business = businessName();
        String text = requestText(key, cleanNote, r.getRequestedBy(), status, business);
        boolean sent = false;
        String emailNote;
        if (vendorEmail.isEmpty()) {
            emailNote = I18n.t("No NIWE address is set up (app.license.vendor-email), so nothing was e-mailed. Send the text below to NIWE.");
        } else if (!mailService.isConfigured()) {
            emailNote = I18n.t("E-mail is not set up on this system (Settings > Email), so nothing was sent. Send the text below to NIWE.");
        } else {
            try {
                mailService.send(vendorEmail, I18n.t("NIWE ERP module request: ") + key.label() + (business.isEmpty() ? "" : " - " + business) + " (" + status.installationId() + ")", html(text), List.of());
                sent = true;
                emailNote = I18n.t("The request was e-mailed to NIWE.");
            } catch (RuntimeException e) {
                log.warn("Module request e-mail failed: {}", e.getMessage());
                emailNote = I18n.t("The request is saved but the e-mail to NIWE failed (") + e.getMessage() + I18n.t("). Send the text below to NIWE.");
            }
        }
        if (sent) {
            r.setEmailed(true);
            r = requests.save(r);
        }
        return new RequestResult(toDto(r), sent, emailNote, text);
    }

    @Transactional
    public ModuleRequestDto withdraw(Long id, String user) {
        ModuleRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Request not found")));
        if (r.getStatus() != ModuleRequestStatus.PENDING) {
            throw ApiException.conflict(I18n.t("Only a pending request can be withdrawn."));
        }
        r.setStatus(ModuleRequestStatus.WITHDRAWN);
        r.setClosedAt(Instant.now());
        r.setClosedNote("Withdrawn by " + user);
        r = requests.save(r);
        auditLogService.record("MODULE_REQUEST_WITHDRAWN", "Withdrew the request for " + r.getModuleKey().label());
        return toDto(r);
    }

    /** The name of the business as entered in Settings > Business details; empty when nothing was entered yet. */
    private String businessName() {
        try {
            String n = storeSettingsService.getSettingsEntity().getBusinessName();
            return n == null ? "" : n.trim().replaceAll("[\\r\\n]+", " ");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String requestText(ModuleKey key, String note, String user, LicenseStatus s, String business) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("NIWE ERP: request to add a module to the license\n"));
        sb.append(I18n.t("Module: ")).append(key.label()).append(" (").append(key.name()).append(")\n");
        if (!key.requires().isEmpty()) {
            sb.append(I18n.t("It also needs: ")).append(String.join(", ", key.requires().stream().map(ModuleKey::label).toList())).append("\n");
        }
        sb.append(I18n.t("Business: ")).append(business.isEmpty() ? I18n.t("(not entered in Settings > Business details)") : business).append("\n");
        sb.append(I18n.t("Installation id: ")).append(s.installationId()).append("\n");
        sb.append(I18n.t("Customer: ")).append(s.customer() == null ? I18n.t("(no license installed yet)") : s.customer()).append("\n");
        if (s.licenseId() != null) sb.append(I18n.t("Current license: ")).append(s.licenseId()).append(" number ").append(s.serial()).append("\n");
        sb.append(I18n.t("Requested by: ")).append(user).append("\n");
        if (note != null) sb.append(I18n.t("Note: ")).append(note).append("\n");
        return sb.toString();
    }

    private static String html(String text) {
        String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<pre style=\"font-family:Arial,sans-serif;font-size:14px\">" + esc + "</pre>";
    }

    private static ModuleRequestDto toDto(ModuleRequest r) {
        return new ModuleRequestDto(r.getId(), r.getModuleKey().name(), r.getModuleKey().label(), r.getNote(), r.getStatus().name(), r.getRequestedBy(), r.getCreatedAt(),
                r.getClosedAt(), r.getClosedNote(), r.isEmailed());
    }
}
