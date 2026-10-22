package com.posapp.modules;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.HotelSettingsService;
import com.posapp.hotel.dto.HotelRequests.HotelSettingsRequest;
import com.posapp.license.LicenseService;
import com.posapp.license.ModuleRequest;
import com.posapp.license.ModuleRequestRepository;
import com.posapp.license.ModuleRequestStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Settings > Modules: which optional modules are activated (a module also has to be in the license, see {@link LicenseService}). An optional module starts off (menus hidden); once an admin
 * activates it, it stays active for good — it cannot be deactivated. Hotel is special: its flag stays in HotelSettings (so Hotel > Settings keeps working) and switching it
 * also turns the "Charge to room" payment method on or off, which HotelSettingsService already does.
 */
@Service
public class ModuleService {

    private final ModuleSettingRepository repository;
    private final HotelSettingsService hotelSettingsService;
    private final AuditLogService auditLogService;
    private final LicenseService licenseService;
    private final ModuleRequestRepository requestRepository;

    public ModuleService(ModuleSettingRepository repository, HotelSettingsService hotelSettingsService,
                         AuditLogService auditLogService, LicenseService licenseService, ModuleRequestRepository requestRepository) {
        this.repository = repository;
        this.hotelSettingsService = hotelSettingsService;
        this.auditLogService = auditLogService;
        this.licenseService = licenseService;
        this.requestRepository = requestRepository;
    }

    /**
     * {@code enabled} means usable: licensed and switched on. {@code state} is one of CORE, ACTIVE, AVAILABLE (licensed, not yet activated), LOCKED (not in the license),
     * REQUESTED (locked, and a request to NIWE is waiting) or EXPIRED (in a license that has run out).
     */
    public record ModuleDto(String key, String name, String icon, String description, boolean enabled, boolean core, List<String> requires,
                            boolean licensed, String state, Instant requestedAt) {
    }

    @Transactional
    public List<ModuleDto> list() {
        return Arrays.stream(ModuleKey.values()).map(this::toDto).toList();
    }

    @Transactional
    public boolean isEnabled(ModuleKey key) {
        if (key.core()) {
            return true;
        }
        if (!licenseService.isLicensed(key)) {
            return false;
        }
        if (key == ModuleKey.HOTEL) {
            return hotelSettingsService.entity().isEnabled();
        }
        return repository.findByModuleKey(key).map(ModuleSetting::isEnabled).orElse(key.defaultEnabled());
    }

    /**
     * Activates a module (and, first, every module it is integrated with). Activation is permanent: once a module is on it
     * can never be switched off again, so asking for "off" on an active module is refused. Asking for "off" on a module that
     * was never activated is a harmless no-op.
     */
    @Transactional
    public List<ModuleDto> setEnabled(ModuleKey key, boolean enabled) {
        if (!enabled) {
            if (isEnabled(key)) {
                throw ApiException.conflict(key.label() + (key.core() ? " is a core module and is always on."
                        : " is already activated — an activated module can't be deactivated."));
            }
            return list();
        }
        if (isEnabled(key)) {
            return list();
        }
        // A module can only be activated when the license has it, and every module it needs.
        List<ModuleKey> notLicensed = java.util.stream.Stream.concat(java.util.stream.Stream.of(key), key.requires().stream())
                .filter(k -> !licenseService.isLicensed(k)).toList();
        if (!notLicensed.isEmpty()) {
            throw ApiException.forbidden(notLicensed.stream().map(ModuleKey::label).collect(Collectors.joining(", "))
                    + (notLicensed.size() == 1 ? " is" : " are") + " not part of your license. Request " + (notLicensed.size() == 1 ? "it" : "them") + " here and install the new license NIWE sends.");
        }
        // Integrated modules come on together with this one (Hotel brings POS and Restaurant).
        for (ModuleKey needed : key.requires()) {
            if (!isEnabled(needed)) {
                apply(needed);
                auditLogService.record("MODULE_ENABLED", "Activated module " + needed.label() + " (needed by " + key.label() + ")");
            }
        }
        apply(key);
        auditLogService.record("MODULE_ENABLED", "Activated module " + key.label());
        return list();
    }

    private void apply(ModuleKey key) {
        if (key == ModuleKey.HOTEL) {
            hotelSettingsService.update(new HotelSettingsRequest(true, null, null, null, null, null));
        } else {
            ModuleSetting setting = repository.findByModuleKey(key).orElseGet(() -> {
                ModuleSetting created = new ModuleSetting();
                created.setModuleKey(key);
                return created;
            });
            setting.setEnabled(true);
            repository.save(setting);
        }
    }

    /** For the public (no-login) pages of a module: they answer "not found" while the module is switched off. */
    @Transactional
    public void requireEnabled(ModuleKey key) {
        if (!isEnabled(key)) {
            throw ApiException.notFound("This service is not available.");
        }
    }

    private ModuleDto toDto(ModuleKey key) {
        boolean licensed = licenseService.isLicensed(key);
        boolean enabled = isEnabled(key);
        ModuleRequest pending = licensed ? null : requestRepository.findFirstByModuleKeyAndStatus(key, ModuleRequestStatus.PENDING).orElse(null);
        String state = key.core() ? "CORE"
                : licensed ? (enabled ? "ACTIVE" : "AVAILABLE")
                : licenseService.lapsed(key) ? "EXPIRED"
                : pending != null ? "REQUESTED" : "LOCKED";
        return new ModuleDto(key.name(), key.label(), key.icon(), key.description(), enabled, key.core(),
                key.requires().stream().map(ModuleKey::name).toList(), licensed, state, pending == null ? null : pending.getCreatedAt());
    }
}
