package com.posapp.restaurant;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 15 minutes: e-mails the reminders that have come due (guests with an address, when e-mail is set up). */
@Component
public class ReservationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationReminderJob.class);

    private final ModuleService moduleService;
    private final ReservationService reservationService;

    public ReservationReminderJob(ModuleService moduleService, ReservationService reservationService) {
        this.moduleService = moduleService;
        this.reservationService = reservationService;
    }

    @Scheduled(cron = "0 7,22,37,52 * * * *")
    public void run() {
        try {
            if (!moduleService.isEnabled(ModuleKey.RESTAURANT)) return;
            int sent = reservationService.sendDueReminders();
            if (sent > 0) log.info("Restaurant reservation reminders e-mailed: {}", sent);
        } catch (Exception e) {
            log.warn("Restaurant reservation reminders failed: {}", e.getMessage());
        }
    }
}
