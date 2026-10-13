package com.posapp.modules;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.HotelSettingsService;
import com.posapp.hotel.dto.HotelRequests.HotelSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Settings > Modules: which optional modules are activated. An optional module starts off (menus hidden); once an admin
 * activates it, it stays active for good — it cannot be deactivated. Hotel is special: its flag stays in HotelSettings (so Hotel > Settings keeps working) and switching it
 * also turns the "Charge to room" payment method on or off, which HotelSettingsService already does.
 */
@Service
public class ModuleService {

    private final ModuleSettingRepository repository;
    private final HotelSettingsService hotelSettingsService;
    private final AuditLogService auditLogService;

    public ModuleService(ModuleSettingRepository repository, HotelSettingsService hotelSettingsService,
                         AuditLogService auditLogService) {
        this.repository = repository;
        this.hotelSettingsService = hotelSettingsService;
        this.auditLogService = auditLogService;
    }

    public record ModuleDto(String key, String name, String icon, String description, boolean enabled, boolean core, List<String> requires) {
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
        return new ModuleDto(key.name(), key.label(), key.icon(), key.description(), isEnabled(key), key.core(),
                key.requires().stream().map(ModuleKey::name).toList());
    }
}
