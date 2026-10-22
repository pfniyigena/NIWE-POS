package com.posapp.propertymgmt;

import com.posapp.common.ApiException;
import com.posapp.propertymgmt.dto.PropertyMgmtReminderDtos.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** {@code /api/propertymgmt}: rent reminders and late fees. Everyone who can view Property Management can read; managers send and charge; the settings are for administrators. */
@RestController
@RequestMapping("/api/propertymgmt")
public class PmReminderController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmReminderService service;
    private final PmSettingsService settings;

    public PmReminderController(PmReminderService service, PmSettingsService settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping("/reminders/settings")
    @PreAuthorize(VIEW)
    public ReminderSettingsDto settings() {
        return service.settings();
    }

    @PutMapping("/reminders/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ReminderSettingsDto saveSettings(@RequestBody ReminderSettingsRequest r) {
        return service.saveSettings(r);
    }

    /** What the morning job would do if it ran now. */
    @GetMapping("/reminders/due")
    @PreAuthorize(VIEW)
    public List<PlanRow> due() {
        return service.due();
    }

    /** Runs the morning job now (the settings still decide what it sends). */
    @PostMapping("/reminders/run")
    @PreAuthorize(MANAGE)
    public RunResult run(@AuthenticationPrincipal AppUserPrincipal me) {
        PmSettings s = settings.entity();
        if (!s.isRemindersEnabled() && !(s.isLateFeeEnabled() && s.isLateFeeAuto())) throw ApiException.conflict("Reminders and automatic late fees are both switched off in the settings.");
        return service.run(PropertyManagementAccess.name(PropertyManagementController.actor(me).user()));
    }

    @PostMapping("/rent/{id}/remind")
    @PreAuthorize(MANAGE)
    public ReminderSent remind(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) RemindRequest r) {
        return service.remind(PropertyManagementController.actor(me), id, r);
    }

    @GetMapping("/rent/{id}/reminders")
    @PreAuthorize(VIEW)
    public List<ReminderRow> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PostMapping("/rent/{id}/late-fee")
    @PreAuthorize(MANAGE)
    public LateFeeDto lateFee(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.lateFee(PropertyManagementController.actor(me), id);
    }
}
