package com.posapp.propertymgmt;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning, after the rent has been invoiced, sends the rent reminders and charges the late fees that are due, when they are switched on. */
@Component
public class PmReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PmReminderJob.class);

    private final ModuleService moduleService;
    private final PmSettingsService settings;
    private final PmReminderService service;

    public PmReminderJob(ModuleService moduleService, PmSettingsService settings, PmReminderService service) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.service = service;
    }

    @Scheduled(cron = "0 50 6 * * *")
    public void run() {
        try {
            if (!moduleService.isEnabled(ModuleKey.PROPERTY_MANAGEMENT)) return;
            PmSettings s = settings.entity();
            if (!s.isRemindersEnabled() && !(s.isLateFeeEnabled() && s.isLateFeeAuto())) return;
            var r = service.run("The morning job");
            if (r.reminded() > 0 || r.fees() > 0 || r.failed() > 0) log.info("Property Management reminders: {} sent, {} late fees, {} skipped, {} failed", r.reminded(), r.fees(), r.skipped(), r.failed());
        } catch (Exception e) {
            log.warn("Property Management reminder run failed: {}", e.getMessage());
        }
    }
}
