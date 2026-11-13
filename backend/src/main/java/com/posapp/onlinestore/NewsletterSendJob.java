package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Sends the next batch of a newsletter that is being sent, once a minute. Nothing happens while the Online Store module is off. */
@Component
public class NewsletterSendJob {

    private static final Logger log = LoggerFactory.getLogger(NewsletterSendJob.class);

    private final NewsletterCampaignService service;
    private final ModuleService moduleService;

    public NewsletterSendJob(NewsletterCampaignService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 120_000L)
    public void run() {
        try {
            if (moduleService.isEnabled(ModuleKey.ONLINE_STORE)) service.runBatch(Instant.now());
        } catch (RuntimeException e) {
            log.warn("The newsletter sending pass failed: {}", e.getMessage());
        }
    }
}
