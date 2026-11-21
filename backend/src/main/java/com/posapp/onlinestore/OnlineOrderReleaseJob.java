package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.66.0: frees the stock held by shop orders nobody answered. Shop orders hold their units from the moment they are placed (before staff accept, no sale
 * order reserves them yet), so an order left waiting forever would hide stock from every other customer. After "Release unanswered orders after" hours
 * (Online Store settings, default 24, 0 = never) an order still waiting for its payment, or a cash order staff did not accept, is cancelled and the customer
 * is told. An order paid online is never cancelled by this job.
 */
@Component
public class OnlineOrderReleaseJob {

    private static final Logger log = LoggerFactory.getLogger(OnlineOrderReleaseJob.class);

    private final OnlineOrderRepository orderRepository;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final ModuleService moduleService;
    private final OnlineOrderService orderService;

    public OnlineOrderReleaseJob(OnlineOrderRepository orderRepository, OnlineStoreSettingsRepository settingsRepository, ModuleService moduleService,
                                 OnlineOrderService orderService) {
        this.orderRepository = orderRepository;
        this.settingsRepository = settingsRepository;
        this.moduleService = moduleService;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 150_000L)
    public void run() {
        try {
            check(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The release of unanswered online orders failed: {}", e.getMessage());
        }
    }

    /** One pass: returns how many orders were released. Public for tests. Each order is released in its own transaction, so one failure does not stop the rest. */
    public int check(Instant now) {
        if (!moduleService.isEnabled(ModuleKey.ONLINE_STORE)) return 0;
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || !s.isEnabled() || s.getAutoReleaseHours() <= 0) return 0;
        Instant before = now.minusSeconds(3600L * s.getAutoReleaseHours());
        List<Long> ids = new ArrayList<>();
        for (String status : List.of("AWAITING_PAYMENT", "PLACED")) {
            for (OnlineOrder o : orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(status, before)) ids.add(o.getId());
        }
        int released = 0;
        for (Long id : ids) {
            try {
                if (orderService.autoRelease(id, s.getAutoReleaseHours())) released++;
            } catch (RuntimeException e) {
                log.warn("Could not release online order {}: {}", id, e.getMessage());
            }
        }
        return released;
    }
}
