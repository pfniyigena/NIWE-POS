package com.posapp.subscription;

import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import com.posapp.settings.dto.SubscriptionAutomationRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin > Subscriptions > Job settings: read/save the daily job's settings and run it now. */
@RestController
@RequestMapping("/api/subscriptions/automation")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionAutomationController {

    public record SettingsDto(boolean billingEnabled, String jobTime, boolean reminderEnabled, int reminderDays,
                              boolean reminderCustomer, boolean reminderStore, boolean digestEnabled,
                              String overdueAction, int overdueDays, String lastRunDate, String lastSummary) {
        static SettingsDto from(StoreSettings s) {
            return new SettingsDto(s.isSubscriptionBillingEnabled(),
                    s.getSubscriptionJobTime() != null ? s.getSubscriptionJobTime() : "06:30",
                    s.isSubscriptionReminderEnabled(), s.getSubscriptionReminderDays(),
                    s.isSubscriptionReminderCustomer(), s.isSubscriptionReminderStore(), s.isSubscriptionDigestEnabled(),
                    s.getSubscriptionOverdueAction() != null ? s.getSubscriptionOverdueAction() : "NONE", s.getSubscriptionOverdueDays(),
                    s.getSubscriptionJobLastRun() != null ? s.getSubscriptionJobLastRun().toString() : null,
                    s.getSubscriptionJobLastSummary());
        }
    }

    private final StoreSettingsService settingsService;
    private final SubscriptionAutomationService automationService;

    public SubscriptionAutomationController(StoreSettingsService settingsService, SubscriptionAutomationService automationService) {
        this.settingsService = settingsService;
        this.automationService = automationService;
    }

    @GetMapping
    public SettingsDto get() {
        return SettingsDto.from(settingsService.getSettingsEntity());
    }

    @PutMapping
    public SettingsDto save(@RequestBody SubscriptionAutomationRequest request) {
        return SettingsDto.from(settingsService.updateSubscriptionAutomation(request));
    }

    @PostMapping("/run")
    public SubscriptionAutomationService.RunResult run() {
        return automationService.runNow();
    }
}
