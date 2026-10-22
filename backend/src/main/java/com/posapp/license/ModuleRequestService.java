package com.posapp.license;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.license.LicenseDtos.LicenseStatus;
import com.posapp.license.LicenseDtos.ModuleRequestDto;
import com.posapp.license.LicenseDtos.RequestResult;
import com.posapp.modules.ModuleKey;
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
    private final String vendorEmail;

    public ModuleRequestService(ModuleRequestRepository requests, LicenseService licenseService, MailService mailService, AuditLogService auditLogService,
                                @Value("${app.license.vendor-email:}") String vendorEmail) {
        this.requests = requests;
        this.licenseService = licenseService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.vendorEmail = vendorEmail == null ? "" : vendorEmail.trim();
    }

    @Transactional(readOnly = true)
    public List<ModuleRequestDto> list() {
        return requests.findAllByOrderByCreatedAtDesc().stream().map(ModuleRequestService::toDto).toList();
    }

    public RequestResult create(ModuleKey key, String note, String user) {
        if (key.core()) {
            throw ApiException.badRequest(key.label() + " is a core module and is always available.");
        }
        if (licenseService.isLicensed(key)) {
            throw ApiException.conflict(key.label() + " is already part of the license.");
        }
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) {
            throw ApiException.badRequest("The note is too long (500 characters at most).");
        }
        if (cleanNote != null && cleanNote.isEmpty()) cleanNote = null;
        if (requests.findFirstByModuleKeyAndStatus(key, ModuleRequestStatus.PENDING).isPresent()) {
            throw ApiException.conflict(key.label() + " has already been requested. Wait for NIWE's new license, or withdraw the request first.");
        }
        ModuleRequest r = new ModuleRequest();
        r.setModuleKey(key);
        r.setNote(cleanNote);
        r.setRequestedBy(user == null || user.isBlank() ? "unknown" : user);
        r = requests.save(r);
        auditLogService.record("MODULE_REQUESTED", "Requested module " + key.label() + (cleanNote != null ? ": " + cleanNote : ""));

        LicenseStatus status = licenseService.status();
        String text = requestText(key, cleanNote, r.getRequestedBy(), status);
        boolean sent = false;
        String emailNote;
        if (vendorEmail.isEmpty()) {
            emailNote = "No NIWE address is set up (app.license.vendor-email), so nothing was e-mailed. Send the text below to NIWE.";
        } else if (!mailService.isConfigured()) {
            emailNote = "E-mail is not set up on this system (Settings > Email), so nothing was sent. Send the text below to NIWE.";
        } else {
            try {
                mailService.send(vendorEmail, "NIWE ERP module request: " + key.label() + " (" + status.installationId() + ")", html(text), List.of());
                sent = true;
                emailNote = "The request was e-mailed to NIWE.";
            } catch (RuntimeException e) {
                log.warn("Module request e-mail failed: {}", e.getMessage());
                emailNote = "The request is saved but the e-mail to NIWE failed (" + e.getMessage() + "). Send the text below to NIWE.";
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
        ModuleRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (r.getStatus() != ModuleRequestStatus.PENDING) {
            throw ApiException.conflict("Only a pending request can be withdrawn.");
        }
        r.setStatus(ModuleRequestStatus.WITHDRAWN);
        r.setClosedAt(Instant.now());
        r.setClosedNote("Withdrawn by " + user);
        r = requests.save(r);
        auditLogService.record("MODULE_REQUEST_WITHDRAWN", "Withdrew the request for " + r.getModuleKey().label());
        return toDto(r);
    }

    private static String requestText(ModuleKey key, String note, String user, LicenseStatus s) {
        StringBuilder sb = new StringBuilder();
        sb.append("NIWE ERP: request to add a module to the license\n");
        sb.append("Module: ").append(key.label()).append(" (").append(key.name()).append(")\n");
        if (!key.requires().isEmpty()) {
            sb.append("It also needs: ").append(String.join(", ", key.requires().stream().map(ModuleKey::label).toList())).append("\n");
        }
        sb.append("Installation id: ").append(s.installationId()).append("\n");
        sb.append("Customer: ").append(s.customer() == null ? "(no license installed yet)" : s.customer()).append("\n");
        if (s.licenseId() != null) sb.append("Current license: ").append(s.licenseId()).append(" number ").append(s.serial()).append("\n");
        sb.append("Requested by: ").append(user).append("\n");
        if (note != null) sb.append("Note: ").append(note).append("\n");
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
