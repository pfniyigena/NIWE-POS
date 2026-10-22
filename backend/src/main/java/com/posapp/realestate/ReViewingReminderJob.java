package com.posapp.realestate;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every quarter of an hour, e-mails the reminders for viewings that are coming up, when an admin has switched viewing reminders on. */
@Component
public class ReViewingReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReViewingReminderJob.class);

    private final ModuleService moduleService;
    private final ReSettingsService settings;
    private final ReViewingReminderService service;

    public ReViewingReminderJob(ModuleService moduleService, ReSettingsService settings, ReViewingReminderService service) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.service = service;
    }

    @Scheduled(cron = "0 5,20,35,50 * * * *")
    public void run() {
        try {
            if (!moduleService.isEnabled(ModuleKey.REAL_ESTATE)) return;
            if (!settings.entity().isViewingReminderEnabled()) return;
            var r = service.run("The reminder job", false);
            if (r.clientsSent() > 0 || r.agentsSent() > 0 || r.failed() > 0) log.info("Real Estate viewing reminders: {}", r.summary());
        } catch (Exception e) {
            log.warn("Real Estate viewing reminders failed: {}", e.getMessage());
        }
    }
}
