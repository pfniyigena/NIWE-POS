package com.posapp.onlinestore;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sending the shop's newsletter (v1.38.0). Staff write a message and choose the audience, send a test e-mail to themselves, then start sending. Starting fixes the
 * list: one delivery row per confirmed address (an address can never get the same campaign twice), and a person who unsubscribes before their turn is skipped.
 * The e-mails go out in small batches (a job runs every minute) with a daily limit, so a mail server's limits are respected. A delivery is taken (SENDING)
 * before it is handed to the mail server, so a crash can lose one mail but never send it twice. Every e-mail carries the person's own unsubscribe link.
 */
@Service
public class NewsletterCampaignService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterCampaignService.class);
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final int STOP_AFTER_FAILURES = 5;

    private final NewsletterCampaignRepository campaigns;
    private final NewsletterDeliveryRepository deliveries;
    private final NewsletterSubscriberRepository subscribers;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;
    private final OnlineStoreService storeService;
    private final AuditLogService auditLogService;
    private final String frontendUrl;
    private final int dailyLimit;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();

    public NewsletterCampaignService(NewsletterCampaignRepository campaigns, NewsletterDeliveryRepository deliveries, NewsletterSubscriberRepository subscribers,
                                     MailService mailService, StoreSettingsService storeSettingsService, OnlineStoreService storeService, AuditLogService auditLogService,
                                     @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                                     @Value("${app.online-store.newsletter.daily-limit:500}") int dailyLimit,
                                     @Value("${app.online-store.newsletter.batch-size:25}") int batchSize) {
        this.campaigns = campaigns;
        this.deliveries = deliveries;
        this.subscribers = subscribers;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
        this.storeService = storeService;
        this.auditLogService = auditLogService;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
        this.dailyLimit = Math.max(0, dailyLimit);
        this.batchSize = Math.max(1, batchSize);
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private NewsletterCampaign getOrThrow(Long id) {
        return campaigns.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Newsletter not found")));
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    // ------------------------------------------------------------------ reading

    private CampaignDto dto(NewsletterCampaign c) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (Object[] r : deliveries.counts(c.getId())) n.put((String) r[0], ((Number) r[1]).intValue());
        return new CampaignDto(c.getId(), c.getSubject(), c.getBody(), c.getButtonLabel(), c.getButtonLink(), c.getAudience(), c.getStatus(), c.getRecipients(),
                n.getOrDefault("SENT", 0), n.getOrDefault("FAILED", 0), n.getOrDefault("SKIPPED", 0), n.getOrDefault("PENDING", 0) + n.getOrDefault("SENDING", 0),
                c.getCreatedBy(), c.getCreatedAt(), c.getStartedAt(), c.getFinishedAt(), c.getLastError());
    }

    @Transactional(readOnly = true)
    public List<CampaignDto> list() {
        return campaigns.findAllByOrderByIdDesc().stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDto get(Long id) {
        return dto(getOrThrow(id));
    }

    /** What the screen needs to know before sending: who would get it, whether e-mail works, and how much of the daily limit is used. */
    @Transactional(readOnly = true)
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        long en = subscribers.countByStatusAndLanguage("CONFIRMED", "en");
        long fr = subscribers.countByStatusAndLanguage("CONFIRMED", "fr");
        m.put("confirmedAll", subscribers.countByStatus("CONFIRMED"));
        m.put("confirmedEn", en);
        m.put("confirmedFr", fr);
        m.put("mailConfigured", mailService.isConfigured());
        m.put("dailyLimit", dailyLimit);
        m.put("sentLast24h", deliveries.countByStatusAndSentAtAfter("SENT", Instant.now().minus(Duration.ofHours(24))));
        m.put("batchSize", batchSize);
        return m;
    }

    @Transactional(readOnly = true)
    public List<DeliveryDto> deliveriesOf(Long id, String status) {
        getOrThrow(id);
        List<NewsletterDelivery> l = status == null || status.isBlank() || status.equals("ALL") ? deliveries.findByCampaignIdOrderByIdAsc(id)
                : deliveries.findByCampaignIdAndStatusOrderByIdAsc(id, status.trim().toUpperCase(Locale.ROOT));
        return l.stream().map(d -> new DeliveryDto(d.getId(), d.getEmail(), d.getLanguage(), d.getStatus(), d.getError(), d.getSentAt())).toList();
    }

    // ------------------------------------------------------------------ writing the message

    private void apply(NewsletterCampaign c, CampaignRequest in) {
        String subject = clean(in.subject());
        String body = clean(in.body());
        String label = clean(in.buttonLabel());
        String link = clean(in.buttonLink());
        if (subject.isEmpty()) throw ApiException.badRequest(I18n.t("Write a subject."));
        if (body.isEmpty()) throw ApiException.badRequest(I18n.t("Write the message."));
        if (subject.contains("\n") || subject.contains("\r")) throw ApiException.badRequest(I18n.t("The subject must be on one line."));
        if (label.isEmpty() != link.isEmpty()) throw ApiException.badRequest(I18n.t("A button needs both a label and a link."));
        if (!link.isEmpty() && !(link.startsWith("https://") || link.startsWith("http://") || (link.startsWith("/") && !link.startsWith("//")))) {
            throw ApiException.badRequest(I18n.t("The button link must start with https://, http:// or /."));
        }
        String audience = clean(in.audience()).toUpperCase(Locale.ROOT);
        if (audience.isEmpty()) audience = "ALL";
        if (!List.of("ALL", "EN", "FR").contains(audience)) throw ApiException.badRequest(I18n.t("Choose who gets the newsletter."));
        c.setSubject(subject);
        c.setBody(body);
        c.setButtonLabel(label.isEmpty() ? null : label);
        c.setButtonLink(link.isEmpty() ? null : link);
        c.setAudience(audience);
    }

    @Transactional
    public CampaignDto create(CampaignRequest in) {
        NewsletterCampaign c = new NewsletterCampaign();
        apply(c, in);
        c.setCreatedBy(user());
        campaigns.save(c);
        return dto(c);
    }

    @Transactional
    public CampaignDto update(Long id, CampaignRequest in) {
        NewsletterCampaign c = getOrThrow(id);
        if (!"DRAFT".equals(c.getStatus())) throw ApiException.badRequest(I18n.t("Only a draft can be changed."));
        apply(c, in);
        campaigns.save(c);
        return dto(c);
    }

    @Transactional
    public void delete(Long id) {
        NewsletterCampaign c = getOrThrow(id);
        if (!"DRAFT".equals(c.getStatus())) throw ApiException.badRequest(I18n.t("Only a draft can be deleted. Sent newsletters stay as a record."));
        campaigns.delete(c);
    }

    // ------------------------------------------------------------------ the e-mail itself

    private String shopName() {
        OnlineStoreSettings s = storeService.settings();
        String shop = s.getShopName() != null && !s.getShopName().isBlank() ? s.getShopName() : storeSettingsService.getSettingsEntity().getBusinessName();
        return shop == null ? "" : shop;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The message as HTML: paragraphs from blank lines, line breaks kept, the optional button, and the footer with the person's own unsubscribe link. */
    static String html(String body, String buttonLabel, String buttonUrl, String shop, String language, String unsubscribeUrl) {
        StringBuilder sb = new StringBuilder("<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.5;color:#222;max-width:600px\">");
        for (String para : body.replace("\r\n", "\n").replace('\r', '\n').split("\n\\s*\n")) {
            if (para.isBlank()) continue;
            sb.append("<p>").append(escape(para.trim()).replace("\n", "<br>")).append("</p>");
        }
        if (buttonLabel != null && !buttonLabel.isBlank() && buttonUrl != null && !buttonUrl.isBlank()) {
            sb.append("<p><a href=\"").append(escape(buttonUrl)).append("\" style=\"display:inline-block;background:#2563eb;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px\">")
                    .append(escape(buttonLabel)).append("</a></p>");
        }
        sb.append("<hr style=\"border:none;border-top:1px solid #ddd;margin:24px 0 12px\"><p style=\"color:#666;font-size:12px\">")
                .append(escape(I18n.tIn(language, "You receive this e-mail because you subscribed to the newsletter of {0}.", shop))).append(" <a href=\"")
                .append(escape(unsubscribeUrl)).append("\">").append(escape(I18n.tIn(language, "Unsubscribe"))).append("</a></p></div>");
        return sb.toString();
    }

    private String buttonUrl(NewsletterCampaign c) {
        String l = c.getButtonLink();
        if (l == null || l.isBlank()) return null;
        return l.startsWith("/") ? frontendUrl + l : l;
    }

    private String unsubscribeUrl(NewsletterSubscriber s) {
        return frontendUrl + "/shop/newsletter/unsubscribe/" + s.getUnsubscribeToken();
    }

    private void requireMail() {
        if (!mailService.isConfigured()) throw ApiException.badRequest(I18n.t("E-mail is not set up yet. Ask your administrator to set it up first."));
    }

    // ------------------------------------------------------------------ test, send, stop, retry

    /** Sends the message to one address chosen by staff, so they see it before everybody does. The unsubscribe link in a test goes to the shop's page. */
    @Transactional(readOnly = true)
    public void sendTest(Long id, String email) {
        NewsletterCampaign c = getOrThrow(id);
        String to = clean(email).toLowerCase(Locale.ROOT);
        if (!to.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || to.length() > 160) throw ApiException.badRequest(I18n.t("Enter a valid e-mail address."));
        requireMail();
        String body = html(c.getBody(), c.getButtonLabel(), buttonUrl(c), shopName(), "FR".equals(c.getAudience()) ? "fr" : "en", frontendUrl + "/shop");
        try {
            mailService.send(to, "[" + I18n.t("TEST") + "] " + c.getSubject(), body, List.of());
        } catch (RuntimeException e) {
            throw ApiException.badRequest(I18n.t("The test e-mail could not be sent: {0}", shorten(e.getMessage())));
        }
        auditLogService.record("NEWSLETTER_TEST_SENT", "Sent a test of the newsletter \"" + c.getSubject() + "\"", "NewsletterCampaign", c.getId());
    }

    private List<NewsletterSubscriber> audience(String audience) {
        return switch (audience) {
            case "EN" -> subscribers.findByStatusAndLanguageOrderByIdAsc("CONFIRMED", "en");
            case "FR" -> subscribers.findByStatusAndLanguageOrderByIdAsc("CONFIRMED", "fr");
            default -> subscribers.findByStatusOrderByIdAsc("CONFIRMED");
        };
    }

    /** Fixes the list and starts sending. Only a draft can be started, so pressing twice (or two people pressing) cannot send it twice. */
    @Transactional
    public CampaignDto start(Long id) {
        NewsletterCampaign c = getOrThrow(id);
        if (!"DRAFT".equals(c.getStatus())) throw ApiException.badRequest(I18n.t("This newsletter was already started."));
        requireMail();
        List<NewsletterSubscriber> to = audience(c.getAudience());
        if (to.isEmpty()) throw ApiException.badRequest(I18n.t("Nobody has confirmed the newsletter yet, so there is nobody to send it to."));
        List<NewsletterDelivery> rows = new ArrayList<>();
        for (NewsletterSubscriber s : to) {
            NewsletterDelivery d = new NewsletterDelivery();
            d.setCampaignId(c.getId());
            d.setSubscriberId(s.getId());
            d.setEmail(s.getEmail());
            d.setLanguage(s.getLanguage());
            rows.add(d);
        }
        deliveries.saveAll(rows);
        c.setStatus("SENDING");
        c.setRecipients(rows.size());
        c.setStartedAt(Instant.now());
        c.setLastError(null);
        campaigns.save(c);
        auditLogService.record("NEWSLETTER_STARTED", "Started sending the newsletter \"" + c.getSubject() + "\" to " + rows.size() + " addresses", "NewsletterCampaign", c.getId());
        return dto(c);
    }

    /** Stops a newsletter that is being sent. What was already sent stays sent; the rest is not sent. */
    @Transactional
    public CampaignDto cancel(Long id) {
        NewsletterCampaign c = getOrThrow(id);
        if (!"SENDING".equals(c.getStatus())) throw ApiException.badRequest(I18n.t("This newsletter is not being sent."));
        c.setStatus("CANCELLED");
        c.setFinishedAt(Instant.now());
        campaigns.save(c);
        auditLogService.record("NEWSLETTER_CANCELLED", "Stopped the newsletter \"" + c.getSubject() + "\"", "NewsletterCampaign", c.getId());
        return dto(c);
    }

    /** Puts the addresses that failed back in the queue (for example after the mail server was down). */
    @Transactional
    public CampaignDto retryFailed(Long id) {
        NewsletterCampaign c = getOrThrow(id);
        if (!"SENDING".equals(c.getStatus()) && !"SENT".equals(c.getStatus())) throw ApiException.badRequest(I18n.t("This newsletter cannot be retried."));
        int n = deliveries.retryFailed(id);
        if (n == 0) throw ApiException.badRequest(I18n.t("Nothing failed."));
        c.setStatus("SENDING");
        c.setFinishedAt(null);
        c.setLastError(null);
        campaigns.save(c);
        return dto(c);
    }

    // ------------------------------------------------------------------ the batch job

    private static String shorten(String s) {
        String x = s == null ? "" : s.replace('\n', ' ').trim();
        return x.length() > 190 ? x.substring(0, 190) : x;
    }

    /**
     * One pass of the job: sends up to a batch of waiting e-mails, within the daily limit. Returns how many were sent. Public for tests. Not transactional on
     * purpose: every change is saved at once, so a mail already handed to the server is never forgotten.
     */
    public int runBatch(Instant now) {
        if (!running.compareAndSet(false, true)) return 0;
        try {
            if (!mailService.isConfigured()) return 0;
            List<NewsletterCampaign> active = campaigns.findByStatusOrderByIdAsc("SENDING");
            if (active.isEmpty()) return 0;
            for (NewsletterDelivery d : deliveries.findByStatusAndUpdatedAtBefore("SENDING", now.minus(STALE))) {
                d.setStatus("FAILED");
                d.setError("Interrupted while sending; check before retrying");
                deliveries.save(d);
            }
            int budget = batchSize;
            if (dailyLimit > 0) {
                long done = deliveries.countByStatusAndSentAtAfter("SENT", now.minus(Duration.ofHours(24)));
                budget = (int) Math.min(budget, Math.max(0, dailyLimit - done));
            }
            String shop = shopName();
            int sent = 0;
            for (NewsletterCampaign c : active) {
                if (budget > 0) {
                    List<NewsletterDelivery> next = deliveries.findByCampaignIdAndStatusOrderByIdAsc(c.getId(), "PENDING", PageRequest.of(0, budget));
                    int failedInRow = 0;
                    for (NewsletterDelivery d : next) {
                        NewsletterCampaign fresh = campaigns.findById(c.getId()).orElse(null);
                        if (fresh == null || !"SENDING".equals(fresh.getStatus())) break;
                        d.setStatus("SENDING");
                        deliveries.save(d);
                        NewsletterSubscriber s = d.getSubscriberId() == null ? null : subscribers.findById(d.getSubscriberId()).orElse(null);
                        if (s == null || !"CONFIRMED".equals(s.getStatus())) {
                            d.setStatus("SKIPPED");
                            d.setError("No longer subscribed");
                            deliveries.save(d);
                            continue;
                        }
                        budget--;
                        try {
                            mailService.send(d.getEmail(), c.getSubject(), html(c.getBody(), c.getButtonLabel(), buttonUrl(c), shop, d.getLanguage(), unsubscribeUrl(s)), List.of());
                            d.setStatus("SENT");
                            d.setSentAt(Instant.now());
                            d.setError(null);
                            failedInRow = 0;
                            sent++;
                        } catch (RuntimeException e) {
                            d.setStatus("FAILED");
                            d.setError(shorten(e.getMessage()));
                            failedInRow++;
                        }
                        deliveries.save(d);
                        if (failedInRow >= STOP_AFTER_FAILURES) {
                            NewsletterCampaign latest = campaigns.findById(c.getId()).orElse(null);
                            if (latest != null) {
                                latest.setLastError(shorten("Several e-mails failed in a row, so sending paused: " + d.getError()));
                                campaigns.save(latest);
                            }
                            break;
                        }
                    }
                }
                finishIfDone(c, now);
            }
            return sent;
        } finally {
            running.set(false);
        }
    }

    private void finishIfDone(NewsletterCampaign c, Instant now) {
        NewsletterCampaign fresh = campaigns.findById(c.getId()).orElse(null);
        if (fresh == null || !"SENDING".equals(fresh.getStatus())) return;
        if (deliveries.countByCampaignIdAndStatus(c.getId(), "PENDING") == 0 && deliveries.countByCampaignIdAndStatus(c.getId(), "SENDING") == 0) {
            fresh.setStatus("SENT");
            fresh.setFinishedAt(now);
            campaigns.save(fresh);
            log.info("Newsletter {} finished", fresh.getId());
        }
    }
}
