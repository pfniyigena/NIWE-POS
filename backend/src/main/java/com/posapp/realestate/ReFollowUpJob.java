package com.posapp.realestate;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every morning at 06:30, e-mails each agent the lead follow-ups that are late or due, when an admin has switched that on. */
@Component
public class ReFollowUpJob {

    private static final Logger log = LoggerFactory.getLogger(ReFollowUpJob.class);

    private final ModuleService moduleService;
    private final ReSettingsService settings;
    private final ReFollowUpService service;

    public ReFollowUpJob(ModuleService moduleService, ReSettingsService settings, ReFollowUpService service) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.service = service;
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void run() {
        try {
            if (!moduleService.isEnabled(ModuleKey.REAL_ESTATE)) return;
            if (!settings.entity().isFollowUpEmailEnabled()) return;
            var r = service.run("The morning job", false);
            if (r.sent() > 0 || r.failed() > 0) log.info("Real Estate follow-up e-mails: {}", r.summary());
        } catch (Exception e) {
            log.warn("Real Estate follow-up e-mails failed: {}", e.getMessage());
        }
    }
}
