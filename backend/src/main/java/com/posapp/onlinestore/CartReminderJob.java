package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Looks for abandoned carts that are due a reminder every few minutes. Nothing happens while the Online Store module or the reminders are off. */
@Component
public class CartReminderJob {

    private static final Logger log = LoggerFactory.getLogger(CartReminderJob.class);

    private final CartReminderService service;
    private final ModuleService moduleService;

    public CartReminderJob(CartReminderService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    @Scheduled(fixedDelay = 180_000L, initialDelay = 150_000L)
    public void run() {
        try {
            if (moduleService.isEnabled(ModuleKey.ONLINE_STORE)) service.runPass(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The cart reminder pass failed: {}", e.getMessage());
        }
    }
}
