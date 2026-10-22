package com.posapp.restaurant;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute: online requests nobody answered in time lapse, so a guest is not left waiting for ever. */
@Component
public class RestaurantOnlineOrderJob {

    private static final Logger log = LoggerFactory.getLogger(RestaurantOnlineOrderJob.class);

    private final ModuleService moduleService;
    private final RestaurantOnlineOrderService service;

    public RestaurantOnlineOrderJob(ModuleService moduleService, RestaurantOnlineOrderService service) {
        this.moduleService = moduleService;
        this.service = service;
    }

    @Scheduled(cron = "30 * * * * *")
    public void run() {
        try {
            if (!moduleService.isEnabled(ModuleKey.RESTAURANT)) return;
            int n = service.expireStale();
            if (n > 0) log.info("Restaurant online orders expired unanswered: {}", n);
        } catch (Exception e) {
            log.warn("Restaurant online order expiry failed: {}", e.getMessage());
        }
    }
}
