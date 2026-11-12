package com.posapp.onlinestore;

import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Tells the shop by e-mail when online orders have waited too long for an answer (Online Store settings: "Alert after", 0 = never).
 * Each order is reported once, and several late orders go in one e-mail. Nothing happens while the module or the shop is off.
 */
@Component
public class OnlineOrderAlertJob {

    private static final Logger log = LoggerFactory.getLogger(OnlineOrderAlertJob.class);

    private final OnlineOrderRepository orderRepository;
    private final OnlineStoreSettingsRepository settingsRepository;
    private final ModuleService moduleService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;

    public OnlineOrderAlertJob(OnlineOrderRepository orderRepository, OnlineStoreSettingsRepository settingsRepository, ModuleService moduleService,
                               MailService mailService, StoreSettingsService storeSettingsService) {
        this.orderRepository = orderRepository;
        this.settingsRepository = settingsRepository;
        this.moduleService = moduleService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    @Scheduled(fixedDelay = 120_000L, initialDelay = 90_000L)
    public void run() {
        try {
            check(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The unanswered online orders check failed: {}", e.getMessage());
        }
    }

    /** One pass: returns how many orders were reported. Public for tests. */
    @Transactional
    public int check(Instant now) {
        if (!moduleService.isEnabled(ModuleKey.ONLINE_STORE)) return 0;
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || !s.isEnabled() || s.getUnansweredAlertMinutes() <= 0) return 0;
        List<OnlineOrder> late = orderRepository.findByStatusAndAlertedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                "PLACED", now.minusSeconds(60L * s.getUnansweredAlertMinutes()));
        if (late.isEmpty()) return 0;
        // Marked first, so a mail that cannot be sent does not turn into a mail every two minutes.
        for (OnlineOrder o : late) o.setAlertedAt(now);
        orderRepository.saveAll(late);
        OnlineMessages.alertShop(mailService, storeSettingsService, s, late, s.getUnansweredAlertMinutes(), now);
        return late.size();
    }
}
