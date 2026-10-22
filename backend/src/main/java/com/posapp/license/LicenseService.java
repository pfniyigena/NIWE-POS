package com.posapp.license;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.license.LicenseCodec.Payload;
import com.posapp.license.LicenseDtos.HistoryRow;
import com.posapp.license.LicenseDtos.LicenseStatus;
import com.posapp.modules.ModuleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which modules this installation has the right to use. The license is a file signed by NIWE (see {@link LicenseCodec}); the signed text
 * is stored in the database and checked again every time it is loaded. A module is licensed when it is core, or the valid, unexpired
 * license lists it. {@code ModuleService.isEnabled} asks this, so a module that is not licensed is off everywhere. The state is cached in memory because the
 * request filter asks on every call; it is reloaded when a license is installed.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    static final int SOON_DAYS = 30;

    private record State(AppLicense row, Payload payload, String problem) {
    }

    private final AppInstallationRepository installations;
    private final AppLicenseRepository licenses;
    private final ModuleRequestRepository requests;
    private final LicenseKeys keys;
    private final AuditLogService auditLogService;
    private final boolean devAll;

    private volatile State state;
    private volatile String installationId;

    public LicenseService(AppInstallationRepository installations, AppLicenseRepository licenses, ModuleRequestRepository requests, LicenseKeys keys,
                          AuditLogService auditLogService, Environment env) {
        this.installations = installations;
        this.licenses = licenses;
        this.requests = requests;
        this.keys = keys;
        this.auditLogService = auditLogService;
        // The developer switch only counts when the "dev" profile is active, so it cannot be left on by a setting alone in a deployment.
        this.devAll = env.getProperty("app.license.dev-all-modules", Boolean.class, false) && env.acceptsProfiles(Profiles.of("dev"));
        if (devAll) {
            log.warn("License check is OFF: app.license.dev-all-modules=true with the dev profile. Never use this in a deployment.");
        }
    }

    /** Creates the installation id and reads the license right after start, so no later (possibly read-only) request has to. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            installationId();
            reload();
        } catch (RuntimeException e) {
            log.warn("License could not be loaded at start: {}", e.getMessage());
        }
    }

    // ---- the question everything else asks ----

    public boolean isLicensed(ModuleKey key) {
        if (key.core() || devAll) return true;
        State s = state();
        return s.payload() != null && !expired(s.payload()) && s.payload().modules().contains(key);
    }

    /** Listed in the installed license, but the license has run out. */
    public boolean lapsed(ModuleKey key) {
        State s = state();
        return !key.core() && s.payload() != null && expired(s.payload()) && s.payload().modules().contains(key);
    }

    public Set<ModuleKey> licensedModules() {
        EnumSet<ModuleKey> set = EnumSet.noneOf(ModuleKey.class);
        for (ModuleKey k : ModuleKey.values()) if (isLicensed(k)) set.add(k);
        return set;
    }

    // ---- installation id ----

    public String installationId() {
        String id = installationId;
        if (id != null) return id;
        return loadInstallationId();
    }

    synchronized String loadInstallationId() {
        if (installationId != null) return installationId;
        AppInstallation row = installations.findAll().stream().findFirst().orElseGet(() -> {
            AppInstallation created = new AppInstallation();
            created.setInstallationId(UUID.randomUUID().toString());
            return installations.save(created);
        });
        installationId = row.getInstallationId();
        return installationId;
    }

    // ---- status ----

    public LicenseStatus status() {
        State s = state();
        List<HistoryRow> history = licenses.findAllByOrderBySerialDesc().stream()
                .map(l -> new HistoryRow(l.getLicenseId(), l.getSerial(), l.getCustomer(), l.getIssuedAt(), l.getExpiresAt(), l.getCreatedAt(), l.getInstalledBy(), l.isActive(),
                        l.getModules().isBlank() ? 0 : l.getModules().split(",").length))
                .toList();
        Payload p = s.payload();
        boolean expired = p != null && expired(p);
        Long daysLeft = p != null && p.expiresAt() != null ? ChronoUnit.DAYS.between(LocalDate.now(), p.expiresAt()) : null;
        List<String> modules = p == null ? List.of() : p.modules().stream().map(Enum::name).toList();
        return new LicenseStatus(installationId(), keys.configured(), s.row() != null, p != null, s.problem(),
                p != null ? p.customer() : s.row() != null ? s.row().getCustomer() : null,
                p != null ? p.licenseId() : s.row() != null ? s.row().getLicenseId() : null,
                p != null ? (Long) p.serial() : s.row() != null ? (Long) s.row().getSerial() : null,
                p != null ? p.issuedAt() : null, p != null ? p.expiresAt() : null,
                expired, daysLeft, p != null && !expired && daysLeft != null && daysLeft <= SOON_DAYS, modules, devAll, history);
    }

    // ---- installing a license ----

    @Transactional
    public LicenseStatus install(String text, String user) {
        Payload p = LicenseCodec.open(text, keys.publicKey());
        if (!p.installationId().equals(installationId())) {
            throw ApiException.badRequest("This license was issued for a different installation. Send NIWE this installation's id (shown on this page) and ask for a new one.");
        }
        if (expired(p)) {
            throw ApiException.badRequest("This license has already expired (" + p.expiresAt() + ").");
        }
        for (ModuleKey m : p.modules()) {
            List<ModuleKey> missing = m.requires().stream().filter(r -> !r.core() && !p.modules().contains(r)).toList();
            if (!missing.isEmpty()) {
                throw ApiException.badRequest("The license lists " + m.label() + " without the modules it needs: "
                        + missing.stream().map(ModuleKey::label).collect(Collectors.joining(", ")) + ". Ask NIWE for a corrected license.");
            }
        }
        List<AppLicense> all = licenses.findAllByOrderBySerialDesc();
        if (licenses.existsByLicenseId(p.licenseId()) && all.stream().anyMatch(l -> l.getLicenseId().equals(p.licenseId()) && l.getSerial() == p.serial())) {
            throw ApiException.conflict("This license is already installed.");
        }
        if (!all.isEmpty() && p.serial() <= all.get(0).getSerial()) {
            throw ApiException.conflict("An older license cannot replace a newer one (this is number " + p.serial() + ", installed is number " + all.get(0).getSerial() + ").");
        }
        for (AppLicense l : all) {
            if (l.isActive()) {
                l.setActive(false);
                licenses.save(l);
            }
        }
        AppLicense row = new AppLicense();
        row.setLicenseId(p.licenseId());
        row.setSerial(p.serial());
        row.setCustomer(p.customer());
        row.setInstallationId(p.installationId());
        row.setIssuedAt(p.issuedAt());
        row.setExpiresAt(p.expiresAt());
        row.setModules(p.modules().stream().map(Enum::name).collect(Collectors.joining(",")));
        row.setEnvelope(text.trim());
        row.setInstalledBy(user);
        row.setActive(true);
        licenses.save(row);
        state = new State(row, p, null);
        int closed = 0;
        for (ModuleRequest r : requests.findByStatus(ModuleRequestStatus.PENDING)) {
            if (p.modules().contains(r.getModuleKey())) {
                r.setStatus(ModuleRequestStatus.FULFILLED);
                r.setClosedAt(Instant.now());
                r.setClosedNote("License number " + p.serial() + " installed");
                requests.save(r);
                closed++;
            }
        }
        auditLogService.record("LICENSE_INSTALLED", "Installed license " + p.licenseId() + " number " + p.serial() + " for " + p.customer() + " with "
                + p.modules().size() + " module(s)" + (closed > 0 ? "; " + closed + " request(s) fulfilled" : ""));
        return status();
    }

    // ---- internals ----

    private State state() {
        State s = state;
        if (s != null) return s;
        return reload();
    }

    synchronized State reload() {
        Optional<AppLicense> row = licenses.findFirstByActiveTrueOrderBySerialDesc();
        State loaded;
        if (row.isEmpty()) {
            loaded = new State(null, null, null);
        } else {
            try {
                Payload p = LicenseCodec.open(row.get().getEnvelope(), keys.publicKey());
                loaded = p.installationId().equals(installationId())
                        ? new State(row.get(), p, null)
                        : new State(row.get(), null, "The installed license belongs to a different installation.");
            } catch (ApiException e) {
                loaded = new State(row.get(), null, "The installed license cannot be used: " + e.getMessage());
            }
        }
        state = loaded;
        return loaded;
    }

    private static boolean expired(Payload p) {
        return p.expiresAt() != null && LocalDate.now().isAfter(p.expiresAt());
    }
}
