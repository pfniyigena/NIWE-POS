package com.posapp.onlinestore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Removes expired sessions and old sign-in codes once an hour. */
@Component
public class ShopAccountJob {

    private static final Logger log = LoggerFactory.getLogger(ShopAccountJob.class);
    private final ShopAccountService accounts;

    public ShopAccountJob(ShopAccountService accounts) {
        this.accounts = accounts;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 300_000L)
    public void run() {
        try {
            accounts.purge(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The shop account clean-up failed: {}", e.getMessage());
        }
    }
}
