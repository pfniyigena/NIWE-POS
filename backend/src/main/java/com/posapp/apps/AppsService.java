package com.posapp.apps;

import com.posapp.apps.dto.AppDto;
import com.posapp.apps.efris.EfrisSettingRepository;
import com.posapp.apps.efrischad.EfrisChadSettingRepository;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Task: "This will work as a plugin (or apps) to be installed and configured" — the generic
 * install/enable/configure lifecycle every built-in app (see AppKey/AppCatalog) shares. What an
 * installed+enabled app actually *does* (e.g. EFRIS submitting a fiscal invoice at checkout) is
 * deliberately not built here yet — see {@link #isEnabled} for the hook a future pass wires real
 * behavior onto once EFRIS's field/table requirements are provided.
 *
 * <p>Task: "The app installed and used can not be unstalled." — {@link #uninstall} is the one
 * narrow exception to this class staying generic: it has to know, for each app, whether any
 * terminal has settings saved for it, to refuse an uninstall that would silently orphan a
 * terminal's configuration. Direct {@link EfrisSettingRepository}/{@link EfrisChadSettingRepository}
 * counts, not a plugin-style interface — this codebase has exactly two apps, both EFRIS variants,
 * and AppCatalog's own javadoc is explicit that adding a third means "one more enum value...
 * nothing structural changes"; building a generic usage-checker abstraction for two call sites
 * would be exactly the kind of speculative structure this session has otherwise avoided (see the
 * generic key/value Apps settings removal).</p>
 */
@Service
public class AppsService {

    private final InstalledAppRepository installedAppRepository;
    private final EfrisSettingRepository efrisSettingRepository;
    private final EfrisChadSettingRepository efrisChadSettingRepository;
    private final AuditLogService auditLogService;

    public AppsService(
            InstalledAppRepository installedAppRepository,
            EfrisSettingRepository efrisSettingRepository,
            EfrisChadSettingRepository efrisChadSettingRepository,
            AuditLogService auditLogService
    ) {
        this.installedAppRepository = installedAppRepository;
        this.efrisSettingRepository = efrisSettingRepository;
        this.efrisChadSettingRepository = efrisChadSettingRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AppDto> list() {
        return AppCatalog.all().stream()
                .map(def -> installedAppRepository.findByAppKey(def.key())
                        .map(installedApp -> AppDto.from(def, installedApp))
                        .orElseGet(() -> AppDto.notInstalled(def)))
                .toList();
    }

    /** Idempotent — installing an already-installed app just returns its current state rather than erroring. */
    @Transactional
    public AppDto install(AppKey key) {
        AppCatalog.Definition def = AppCatalog.getOrThrow(key);
        InstalledApp installedApp = installedAppRepository.findByAppKey(key).orElseGet(() -> {
            InstalledApp created = new InstalledApp();
            created.setAppKey(key);
            created.setInstalled(true);
            // Defaults disabled — see InstalledApp's javadoc: installing is "start setting this
            // up", enabling is a distinct, explicit "start actually using this" step.
            created.setEnabled(false);
            return created;
        });
        installedApp.setInstalled(true);
        AppDto saved = AppDto.from(def, installedAppRepository.save(installedApp));
        // Task: "Implement an audit log of who did what and when" — app install/enable/disable/uninstall is a curated action, see AuditLogService's javadoc.
        auditLogService.record("APP_INSTALLED", "Installed app " + def.name());
        return saved;
    }

    /**
     * Removes this app's install state. Task: "The app installed and used can not be unstalled." —
     * refuses when at least one terminal has this app's settings saved (a {@code count() > 0} check
     * against its dedicated settings table), so uninstalling can never silently orphan a terminal's
     * configuration. "Used" here means "has terminal settings saved", not "currently enabled" — an
     * app can be disabled and still have settings on file, and that's still enough to block
     * uninstall; the admin has to clear every terminal's settings for this app first (see
     * EfrisSettingController/EfrisChadSettingController's {@code DELETE .../terminals/{id}}).
     */
    @Transactional
    public void uninstall(AppKey key) {
        InstalledApp installedApp = getInstalledOrThrow(key);
        if (hasTerminalSettings(key)) {
            throw ApiException.badRequest(
                    AppCatalog.getOrThrow(key).name() + " has terminal settings saved for at least one "
                            + "register and can't be uninstalled — clear its terminal settings first, "
                            + "then try again.");
        }
        installedAppRepository.delete(installedApp);
        auditLogService.record("APP_UNINSTALLED", "Uninstalled app " + AppCatalog.getOrThrow(key).name());
    }

    private boolean hasTerminalSettings(AppKey key) {
        return switch (key) {
            case EFRIS_RW -> efrisSettingRepository.count() > 0;
            case EFRIS_TD -> efrisChadSettingRepository.count() > 0;
        };
    }

    @Transactional
    public AppDto setEnabled(AppKey key, boolean enabled) {
        InstalledApp installedApp = getInstalledOrThrow(key);
        installedApp.setEnabled(enabled);
        AppDto saved = AppDto.from(AppCatalog.getOrThrow(key), installedAppRepository.save(installedApp));
        auditLogService.record(enabled ? "APP_ENABLED" : "APP_DISABLED",
                (enabled ? "Enabled app " : "Disabled app ") + AppCatalog.getOrThrow(key).name());
        return saved;
    }

    /**
     * The hook other services will call once real per-app behavior exists (e.g. "does a completed
     * sale need to be submitted to EFRIS?"). False for an app that's never been installed at all,
     * not just a disabled one — same "no row means never touched" reasoning as InstalledApp's
     * javadoc.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(AppKey key) {
        return installedAppRepository.findByAppKey(key).map(InstalledApp::isEnabled).orElse(false);
    }

    private InstalledApp getInstalledOrThrow(AppKey key) {
        InstalledApp installedApp = installedAppRepository.findByAppKey(key)
                .orElseThrow(() -> ApiException.badRequest(AppCatalog.getOrThrow(key).name() + " is not installed"));
        if (!installedApp.isInstalled()) {
            throw ApiException.badRequest(AppCatalog.getOrThrow(key).name() + " is not installed");
        }
        return installedApp;
    }
}
