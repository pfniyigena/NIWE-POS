package com.posapp.onlinestore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Brings every product with variants on the shop up to date every five minutes: a variant added in Products appears on the shop without anyone opening a screen. */
@Component
public class OnlineVariantSyncJob {

    private static final Logger log = LoggerFactory.getLogger(OnlineVariantSyncJob.class);
    private final OnlineVariants variants;

    public OnlineVariantSyncJob(OnlineVariants variants) {
        this.variants = variants;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void run() {
        try {
            variants.syncAll();
        } catch (RuntimeException e) {
            log.warn("The shop's variants could not be brought up to date: {}", e.getMessage());
        }
    }
}
