package com.posapp.apps.efris;

import com.posapp.apps.efris.dto.EfrisConnection;
import com.posapp.apps.efris.dto.EfrisLogosDto;
import com.posapp.apps.efris.dto.EfrisTerminalDto;
import com.posapp.apps.efris.dto.UpdateEfrisSettingRequest;
import com.posapp.apps.efrischad.EfrisChadSettingRepository;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.register.Register;
import com.posapp.register.RegisterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Task: "EFRIS_setting for one terminal" — EFRIS's per-terminal URL is structured,
 * one-row-per-Register data, so it gets its own small table/service rather than a free-form
 * key/value pair. (The Apps framework used to also offer a generic key/value settings editor for
 * whatever an app needed before its real fields were known — see AppsService/AppCatalog's own
 * javadoc; it was removed once nothing, including EFRIS, still used it.)
 *
 * <p>Task: "One Terminal can have only one EFRIS mapped with" — a terminal can be mapped to EFRIS
 * Rwanda or EFRIS Chad, never both at once, so {@link #saveTerminalSettings} refuses to create a
 * row here for a register that already has an {@code EfrisChadSetting} row. Direct dependency on
 * {@link EfrisChadSettingRepository} (mirrored the other way by {@code EfrisChadSettingService}'s
 * own dependency on {@link EfrisSettingRepository}) rather than a shared coordinating class — this
 * is a two-app rule, not a general N-app framework concern, and the direct check is the more
 * legible option for exactly two participants.</p>
 */
@Service
public class EfrisSettingService {

    private final RegisterRepository registerRepository;
    private final EfrisSettingRepository efrisSettingRepository;
    private final EfrisChadSettingRepository efrisChadSettingRepository;
    private final AuditLogService auditLogService;

    public EfrisSettingService(
            RegisterRepository registerRepository,
            EfrisSettingRepository efrisSettingRepository,
            EfrisChadSettingRepository efrisChadSettingRepository,
            AuditLogService auditLogService
    ) {
        this.registerRepository = registerRepository;
        this.efrisSettingRepository = efrisSettingRepository;
        this.efrisChadSettingRepository = efrisChadSettingRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<EfrisTerminalDto> listTerminals() {
        return registerRepository.findAll().stream()
                .map(r -> EfrisTerminalDto.from(r, efrisSettingRepository.findByRegisterId(r.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public EfrisTerminalDto saveTerminalSettings(Long registerId, UpdateEfrisSettingRequest request) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + registerId));
        EfrisSetting setting = efrisSettingRepository.findByRegisterId(registerId).orElseGet(() -> {
            // Task: "One Terminal can have only one EFRIS mapped with" — only enforced when this
            // register doesn't already have its own EfrisSetting row (i.e. this save would newly
            // map it to Rwanda); re-saving an already-mapped Rwanda terminal's settings is always
            // fine regardless of what else exists.
            if (efrisChadSettingRepository.findByRegisterId(registerId).isPresent()) {
                throw ApiException.badRequest(
                        "This terminal already has EFRIS Chad settings saved — a terminal can only be "
                                + "mapped to one EFRIS integration at a time. Clear its EFRIS Chad settings "
                                + "first, then map it to EFRIS Rwanda.");
            }
            EfrisSetting created = new EfrisSetting();
            created.setRegister(register);
            return created;
        });
        setting.setEfrisUrl(request.getEfrisUrl());
        setting.setEfrisDb(request.getEfrisDb());
        setting.setEfrisUid(request.getEfrisUid());
        setting.setEfrisPassword(request.getEfrisPassword());
        setting.setRraLogoDataUrl(request.getRraLogoDataUrl());
        setting.setEfrisLogoDataUrl(request.getEfrisLogoDataUrl());
        EfrisSetting saved = efrisSettingRepository.save(setting);
        // Task: "Implement an audit log of who did what and when" — EFRIS terminal settings are a curated action, see AuditLogService's javadoc.
        auditLogService.record("EFRIS_RWANDA_SETTINGS_SAVED", "Saved EFRIS Rwanda settings for terminal " + register.getName(),
                "EfrisSetting", saved.getId());
        return EfrisTerminalDto.from(register, saved);
    }

    /**
     * Task: "One Terminal can have only one EFRIS mapped with" — the way an admin unmaps a terminal
     * from EFRIS Rwanda, either to map it to EFRIS Chad instead or just to stop using EFRIS Rwanda
     * on that till. Also what lets {@code AppsService.uninstall} ever succeed for EFRIS Rwanda once
     * at least one terminal has settings saved. Idempotent — clearing an already-unmapped terminal
     * is a no-op, not an error.
     */
    @Transactional
    public EfrisTerminalDto clearTerminalSettings(Long registerId) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> ApiException.notFound("Register not found: " + registerId));
        // Only logged when there was actually something to clear — idempotently re-clearing an
        // already-unmapped terminal isn't a real event worth a trail (Task: "Implement an audit
        // log of who did what and when").
        efrisSettingRepository.findByRegisterId(registerId).ifPresent(setting -> {
            efrisSettingRepository.delete(setting);
            auditLogService.record("EFRIS_RWANDA_SETTINGS_CLEARED", "Cleared EFRIS Rwanda settings for terminal " + register.getName());
        });
        return EfrisTerminalDto.from(register, null);
    }

    /**
     * Task: "add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the printout" — the
     * narrow, non-admin-gated lookup the receipt page calls (see EfrisSettingController's own
     * endpoint split). Returns {@link EfrisLogosDto#EMPTY}, never null/an error, for a terminal
     * with no EfrisSetting row yet — same "nothing configured, nothing to show" degradation as the
     * rest of this app's optional branding (StoreSettings.logoDataUrl).
     */
    @Transactional(readOnly = true)
    public EfrisLogosDto getLogosForRegister(Long registerId) {
        if (registerId == null) {
            return EfrisLogosDto.EMPTY;
        }
        return efrisSettingRepository.findByRegisterId(registerId)
                .map(s -> new EfrisLogosDto(s.getRraLogoDataUrl(), s.getEfrisLogoDataUrl()))
                .orElse(EfrisLogosDto.EMPTY);
    }

    /**
     * The connection details {@code OrderService} needs to submit an invoice for one terminal,
     * reduced to a plain {@link EfrisConnection} record so the caller never needs to depend on
     * {@link EfrisSetting}/{@link Register} themselves (see the {@code order}→{@code apps.efris}
     * one-directional dependency convention documented on {@code EfrisConnection}). Empty when the
     * terminal has no {@link EfrisSetting} row yet, or its URL is blank (EFRIS not configured for
     * that till) — either way, the caller should skip submission silently rather than treat it as
     * a failure.
     */
    @Transactional(readOnly = true)
    public Optional<EfrisConnection> getConnectionForRegister(Long registerId) {
        if (registerId == null) {
            return Optional.empty();
        }
        return efrisSettingRepository.findByRegisterId(registerId)
                .filter(s -> s.getEfrisUrl() != null && !s.getEfrisUrl().isBlank())
                .map(s -> new EfrisConnection(s.getEfrisUrl(), s.getEfrisDb(), s.getEfrisUid(), s.getEfrisPassword()));
    }
}
