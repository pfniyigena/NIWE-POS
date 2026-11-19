package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every five minutes: tells the customers waiting for a product that can be sold again (see {@link OnlineStockAlertService}). */
@Component
public class OnlineStockAlertJob {

    private static final Logger log = LoggerFactory.getLogger(OnlineStockAlertJob.class);
    private final OnlineStockAlertService alerts;
    private final ModuleService moduleService;

    public OnlineStockAlertJob(OnlineStockAlertService alerts, ModuleService moduleService) {
        this.alerts = alerts;
        this.moduleService = moduleService;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 150_000L)
    public void run() {
        try {
            if (moduleService.isEnabled(ModuleKey.ONLINE_STORE)) alerts.notifyAvailable();
        } catch (RuntimeException e) {
            log.warn("The back-in-stock notices failed: {}", e.getMessage());
        }
    }
}
