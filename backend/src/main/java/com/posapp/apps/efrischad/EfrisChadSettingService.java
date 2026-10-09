package com.posapp.apps.efrischad;

import com.posapp.apps.efris.EfrisSettingRepository;
import com.posapp.apps.efrischad.dto.EfrisChadConnection;
import com.posapp.apps.efrischad.dto.EfrisChadLogosDto;
import com.posapp.apps.efrischad.dto.EfrisChadTerminalDto;
import com.posapp.apps.efrischad.dto.UpdateEfrisChadSettingRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.register.Register;
import com.posapp.register.RegisterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Task: "Now I want to add new app (EFRIS Chad)..." — same shape as {@code EfrisSettingService}
 * (Rwanda's own settings service): EFRIS Chad's per-terminal connection details are structured,
 * one-row-per-Register data, so this gets its own small table/service rather than the (now-removed)
 * generic Apps key/value store.
 *
 * <p>Task: "if EFRIS Chad is set then call configured with the shared sample request" — {@link
 * #getConnectionForRegister} is the settings-storage half of that: the connection lookup {@code
 * OrderService} calls before submitting a sale, mirroring {@code
 * EfrisSettingService.getConnectionForRegister} (Rwanda's equivalent). The actual submission call
 * itself lives in {@code EfrisChadSubmissionService}.</p>
 *
 * <p>Task: "One Terminal can have only one EFRIS mapped with" — mirrors {@code EfrisSettingService}
 * (Rwanda's) own check the other way: {@link #saveTerminalSettings} refuses to newly map a register
 * to EFRIS Chad while it already has an {@code EfrisSetting} (Rwanda) row.</p>
 */
@Service
public class EfrisChadSettingService {

    private final RegisterRepository registerRepository;
    private final EfrisChadSettingRepository efrisChadSettingRepository;
    private final EfrisSettingRepository efrisSettingRepository;
    private final AuditLogService auditLogService;

    public EfrisChadSettingService(
            RegisterRepository registerRepository,
            EfrisChadSettingRepository efrisChadSettingRepository,
            EfrisSettingRepository efrisSettingRepository,
            AuditLogService auditLogService
    ) {
        this.registerRepository = registerRepository;
        this.efrisChadSettingRepository = efrisChadSettingRepository;
        this.efrisSettingRepository = efrisSettingRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<EfrisChadTerminalDto> listTerminals() {
        return registerRepository.findAll().stream()
                .map(r -> EfrisChadTerminalDto.from(r, efrisChadSettingRepository.findByRegisterId(r.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public EfrisChadTerminalDto saveTerminalSettings(Long registerId, UpdateEfrisChadSettingRequest request) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + registerId));
        EfrisChadSetting setting = efrisChadSettingRepository.findByRegisterId(registerId).orElseGet(() -> {
            // Task: "One Terminal can have only one EFRIS mapped with" — only checked when this
            // register doesn't already have its own EfrisChadSetting row (i.e. this save would
            // newly map it to Chad); re-saving an already-mapped Chad terminal is always fine.
            if (efrisSettingRepository.findByRegisterId(registerId).isPresent()) {
                throw ApiException.badRequest(
                        "This terminal already has EFRIS Rwanda settings saved — a terminal can only be "
                                + "mapped to one EFRIS integration at a time. Clear its EFRIS Rwanda settings "
                                + "first, then map it to EFRIS Chad.");
            }
            EfrisChadSetting created = new EfrisChadSetting();
            created.setRegister(register);
            return created;
        });
        setting.setUrl(request.getUrl());
        setting.setBasicAuthUsername(request.getBasicAuthUsername());
        setting.setBasicPassword(request.getBasicPassword());
        setting.setSdcNumber(request.getSdcNumber());
        setting.setBranchCode(request.getBranchCode());
        setting.setMrc(request.getMrc());
        setting.setIntegrationKey(request.getIntegrationKey());
        setting.setCraLogoDataUrl(request.getCraLogoDataUrl());
        setting.setEfrisLogoDataUrl(request.getEfrisLogoDataUrl());
        EfrisChadSetting saved = efrisChadSettingRepository.save(setting);
        // Task: "Implement an audit log of who did what and when" — EFRIS terminal settings are a curated action, mirrors EfrisSettingService's own treatment.
        auditLogService.record("EFRIS_CHAD_SETTINGS_SAVED", "Saved EFRIS Chad settings for terminal " + register.getName(),
                "EfrisChadSetting", saved.getId());
        return EfrisChadTerminalDto.from(register, saved);
    }

    /**
     * Task: "One Terminal can have only one EFRIS mapped with" — the way an admin unmaps a terminal
     * from EFRIS Chad, either to map it to EFRIS Rwanda instead or just to stop using EFRIS Chad on
     * that till. Also what lets {@code AppsService.uninstall} ever succeed for EFRIS Chad once at
     * least one terminal has settings saved. Idempotent — clearing an already-unmapped terminal is
     * a no-op, not an error.
     */
    @Transactional
    public EfrisChadTerminalDto clearTerminalSettings(Long registerId) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + registerId));
        // Only logged when there was actually something to clear — same reasoning as
        // EfrisSettingService.clearTerminalSettings (Task: "Implement an audit log of who did what
        // and when").
        efrisChadSettingRepository.findByRegisterId(registerId).ifPresent(setting -> {
            efrisChadSettingRepository.delete(setting);
            auditLogService.record("EFRIS_CHAD_SETTINGS_CLEARED", "Cleared EFRIS Chad settings for terminal " + register.getName());
        });
        return EfrisChadTerminalDto.from(register, null);
    }

    /**
     * Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — the
     * narrow, non-admin-gated lookup the receipt page calls, mirrors {@code
     * EfrisSettingService.getLogosForRegister} (Rwanda's equivalent) exactly. Returns {@link
     * EfrisChadLogosDto#EMPTY}, never null/an error, for a terminal with no EfrisChadSetting row
     * yet — same "nothing configured, nothing to show" degradation as the rest of this app's
     * optional branding.
     */
    @Transactional(readOnly = true)
    public EfrisChadLogosDto getLogosForRegister(Long registerId) {
        if (registerId == null) {
            return EfrisChadLogosDto.EMPTY;
        }
        return efrisChadSettingRepository.findByRegisterId(registerId)
                .map(s -> new EfrisChadLogosDto(s.getCraLogoDataUrl(), s.getEfrisLogoDataUrl()))
                .orElse(EfrisChadLogosDto.EMPTY);
    }

    /**
     * The connection details {@code OrderService} needs to submit a sale to EFRIS Chad for one
     * terminal, reduced to a plain {@link EfrisChadConnection} record — mirrors {@code
     * EfrisSettingService.getConnectionForRegister} (Rwanda's equivalent). Empty when the terminal
     * has no {@code EfrisChadSetting} row yet, or its URL is blank (EFRIS Chad not configured for
     * that till) — either way, the caller should skip submission silently rather than treat it as
     * a failure.
     */
    @Transactional(readOnly = true)
    public Optional<EfrisChadConnection> getConnectionForRegister(Long registerId) {
        if (registerId == null) {
            return Optional.empty();
        }
        return efrisChadSettingRepository.findByRegisterId(registerId)
                .filter(s -> s.getUrl() != null && !s.getUrl().isBlank())
                .map(s -> new EfrisChadConnection(
                        s.getUrl(), s.getBasicAuthUsername(), s.getBasicPassword(),
                        s.getSdcNumber(), s.getBranchCode(), s.getMrc(), s.getIntegrationKey()));
    }
}
