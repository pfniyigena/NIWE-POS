package com.posapp.onlinestore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Looks after payments the provider has not reported: asks about each payment still pending (the webhook can be lost), closes the ones whose time
 * is up, and cancels orders nobody paid for in a day. It does not depend on the module switch, so money is never left unrecorded.
 * Every payment and every order is its own transaction, so one failing provider call stops no other.
 */
@Component
public class OnlinePaymentJob {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentJob.class);

    private final OnlinePaymentService payments;

    public OnlinePaymentJob(OnlinePaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
    public void run() {
        try {
            check(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The online payments check failed: {}", e.getMessage());
        }
    }

    /** One pass. Public for tests. */
    public void check(Instant now) {
        for (Long id : payments.pendingIds()) {
            try {
                payments.checkOne(id, now);
            } catch (RuntimeException e) {
                log.warn("Could not check online payment {}: {}", id, e.getMessage());
            }
        }
        for (Long id : payments.staleOrderIds(now)) {
            try {
                payments.abandon(id, now);
            } catch (RuntimeException e) {
                log.warn("Could not close unpaid online order {}: {}", id, e.getMessage());
            }
        }
    }
}
