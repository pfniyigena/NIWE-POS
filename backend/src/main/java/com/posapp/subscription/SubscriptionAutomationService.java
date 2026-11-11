package com.posapp.subscription;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily subscription job. It replaces the fixed 06:30 billing sweep that used to live in
 * {@link SubscriptionService}, and adds three optional steps, all configured in Admin &gt; Subscriptions &gt; Job settings:
 * <ol>
 *   <li><b>Overdue handling</b> — pause or cancel an ACTIVE subscription whose issued invoice has stayed unpaid
 *       for N days (done first, so a subscription that has just been paused is not billed again today).</li>
 *   <li><b>Billing</b> — bill every ACTIVE subscription whose next billing date has arrived. Each one is its own
 *       transaction, so one failure never stops the rest.</li>
 *   <li><b>Reminders</b> — email the customer (and/or the store) a few days before a subscription is billed, once
 *       per billing date.</li>
 *   <li><b>Digest</b> — one email to the notifications address summarising what the run did.</li>
 * </ol>
 * A check runs every five minutes and starts the job once per store-local day from the configured time, so a server
 * that was down at that minute catches up later the same day.
 */
@Service
public class SubscriptionAutomationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionAutomationService.class);

    private final StoreSettingsService storeSettingsService;
    private final SubscriptionService subscriptionService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final ModuleService moduleService;

    public SubscriptionAutomationService(StoreSettingsService storeSettingsService,
                                         SubscriptionService subscriptionService,
                                         MailService mailService,
                                         AuditLogService auditLogService,
                                         ModuleService moduleService) {
        this.storeSettingsService = storeSettingsService;
        this.subscriptionService = subscriptionService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.moduleService = moduleService;
    }

    /** What one run did; also what "Run now" shows. */
    public record RunResult(int billed, int failed, int remindersSent, int overdueActioned, boolean digestSent, String message, int resumed, int overdueReminders) {
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 75_000L)
    public void tick() {
        try {
            // The daily job only runs while the Subscriptions module is licensed and activated.
            if (!moduleService.isEnabled(ModuleKey.SUBSCRIPTIONS)) return;
            StoreSettings settings = storeSettingsService.getSettingsEntity();
            if (!anythingEnabled(settings) && !subscriptionService.settingsEntity().isDunningEnabled()) return;
            LocalDateTime now = LocalDateTime.now(zoneOf(settings));
            LocalDate today = now.toLocalDate();
            if (today.equals(settings.getSubscriptionJobLastRun())) return;
            if (now.toLocalTime().isBefore(timeOf(settings))) return;
            RunResult result = run(settings, today);
            log.info("Subscription job: {}", result.message());
        } catch (Exception ex) {
            log.warn("Subscription job failed: {}", ex.getMessage(), ex);
        }
    }

    /** Runs the job right now using the saved settings. Counts as today's run, so the scheduled run is skipped today. */
    public RunResult runNow() {
        StoreSettings settings = storeSettingsService.getSettingsEntity();
        return run(settings, LocalDateTime.now(zoneOf(settings)).toLocalDate());
    }

    private static boolean anythingEnabled(StoreSettings s) {
        return s.isSubscriptionBillingEnabled() || s.isSubscriptionReminderEnabled() || s.isSubscriptionDigestEnabled()
                || !"NONE".equals(s.getSubscriptionOverdueAction());
    }

    private RunResult run(StoreSettings settings, LocalDate today) {
        // Mark first: a run that fails part-way should not be retried every five minutes all day.
        storeSettingsService.markSubscriptionJobRan(today, "Running…");

        List<String[]> actioned = new ArrayList<>();   // customer, invoice, what happened
        List<SubscriptionService.BillResult> billed = new ArrayList<>();
        List<String[]> failed = new ArrayList<>();      // customer, error
        int reminders = 0;

        // 0. Paused subscriptions whose resume date has come go back to ACTIVE (before billing, so they are billed today if due).
        int resumed = 0;
        try {
            resumed = subscriptionService.resumeDue(today).size();
        } catch (Exception ex) {
            log.warn("Subscription auto-resume failed: {}", ex.getMessage(), ex);
        }

        // 0b. Overdue-invoice reminders to the customer (before the pause/cancel step below, so the customer is warned first).
        int overdueReminders = 0;
        try {
            overdueReminders = sendDunning(settings, today);
        } catch (Exception ex) {
            log.warn("Subscription overdue reminders failed: {}", ex.getMessage(), ex);
        }

        // 1. Overdue subscriptions
        String action = settings.getSubscriptionOverdueAction();
        if (!"NONE".equals(action)) {
            try {
                for (SubscriptionService.OverdueInfo o : subscriptionService.overdueCandidates(today, settings.getSubscriptionOverdueDays(), zoneOf(settings))) {
                    try {
                        String reason = I18n.t("Invoice ") + o.invoiceNumber() + I18n.t(" has been unpaid for more than ") + settings.getSubscriptionOverdueDays() + " days";
                        if ("CANCEL".equals(action)) {
                            subscriptionService.cancel(o.subscriptionId(), I18n.t("Cancelled automatically: ") + reason);
                        } else {
                            subscriptionService.pause(o.subscriptionId());
                        }
                        auditLogService.record("CANCEL".equals(action) ? "SUBSCRIPTION_AUTO_CANCELLED" : "SUBSCRIPTION_AUTO_PAUSED",
                                ("CANCEL".equals(action) ? "Cancelled" : "Paused") + " the subscription for " + o.customerName() + " automatically: " + reason,
                                "Subscription", o.subscriptionId());
                        actioned.add(new String[]{o.customerName(), o.invoiceNumber(), "CANCEL".equals(action) ? "cancelled" : "paused"});
                    } catch (Exception ex) {
                        failed.add(new String[]{o.customerName(), I18n.t("Could not ") + action.toLowerCase() + ": " + ex.getMessage()});
                    }
                }
            } catch (Exception ex) {
                log.warn("Overdue subscription check failed: {}", ex.getMessage(), ex);
                failed.add(new String[]{I18n.t("(overdue check)"), ex.getMessage()});
            }
        }

        // 2. Billing (up to the configured number of missed cycles per subscription per run)
        if (settings.isSubscriptionBillingEnabled()) {
            int catchUp = Math.max(1, subscriptionService.settingsEntity().getCatchUpMax());
            for (Long id : subscriptionService.dueSubscriptionIds(today)) {
                for (int n = 0; n < catchUp; n++) {
                    try {
                        SubscriptionService.BillResult r = subscriptionService.billDue(id, today);
                        if ("CANCELLED".equals(r.outcome())) {
                            actioned.add(new String[]{r.customerName(), "-", I18n.t("cancelled as scheduled")});
                        } else {
                            billed.add(r);
                            if ("BILLED_AND_CANCELLED".equals(r.outcome())) actioned.add(new String[]{r.customerName(), nz(r.invoiceNumber()), I18n.t("cancelled as scheduled")});
                        }
                    } catch (Exception ex) {
                        String name = I18n.t("Subscription #") + id;
                        auditLogService.record("SUBSCRIPTION_BILLING_FAILED", "Failed to bill " + name + ": " + ex.getMessage(), "Subscription", id);
                        failed.add(new String[]{name, ex.getMessage()});
                        break;
                    }
                    if (!subscriptionService.isDue(id, today)) break;
                }
            }
        }

        // 3. Reminders
        if (settings.isSubscriptionReminderEnabled()) {
            try {
                reminders = sendReminders(settings, today);
            } catch (Exception ex) {
                log.warn("Subscription reminders failed: {}", ex.getMessage(), ex);
            }
        }

        // 4. Digest
        boolean digestSent = false;
        if (settings.isSubscriptionDigestEnabled() && (!billed.isEmpty() || !failed.isEmpty() || !actioned.isEmpty() || reminders > 0 || overdueReminders > 0)) {
            digestSent = sendDigest(settings, today, billed, failed, actioned, reminders, overdueReminders);
        }

        StringBuilder m = new StringBuilder(I18n.t("Billed ")).append(billed.size()).append(I18n.t(", failed ")).append(failed.size());
        if (settings.isSubscriptionReminderEnabled()) m.append(I18n.t(", reminders sent ")).append(reminders);
        if (resumed > 0) m.append(", ").append(resumed).append(" resumed");
        if (overdueReminders > 0) m.append(", ").append(overdueReminders).append(I18n.t(" overdue reminder(s) sent"));
        if (!"NONE".equals(action)) {
            m.append(", ").append(actioned.size()).append(" subscription(s) ").append("CANCEL".equals(action) ? "cancelled" : "paused").append(I18n.t(" for non-payment"));
        }
        if (settings.isSubscriptionDigestEnabled()) m.append(digestSent ? I18n.t("; digest emailed") : I18n.t("; digest not sent"));
        String message = m.append(".").toString();
        storeSettingsService.markSubscriptionJobRan(today, today + ": " + message);
        return new RunResult(billed.size(), failed.size(), reminders, actioned.size(), digestSent, message, resumed, overdueReminders);
    }

    private int sendReminders(StoreSettings settings, LocalDate today) {
        boolean canMail = mailService.isConfigured();
        String storeEmail = settings.getNotificationsEmail();
        int sent = 0;
        for (SubscriptionService.ReminderInfo r : subscriptionService.reminderCandidates(today, settings.getSubscriptionReminderDays())) {
            if (!canMail) break;
            String subject = I18n.t("Upcoming subscription billing: ") + r.planName() + I18n.t(" on ") + r.nextBillingDate();
            String business = settings.getBusinessName() == null ? "" : settings.getBusinessName();
            String currency = settings.getCurrencySymbol() == null ? "" : settings.getCurrencySymbol();
            boolean delivered = false;
            if (settings.isSubscriptionReminderCustomer() && r.customerEmail() != null && !r.customerEmail().isBlank()) {
                String html = I18n.t("<p>Hello ") + escape(r.customerName()) + I18n.t(",</p>"
                        + "<p>This is a reminder that your subscription to <b>") + escape(r.planName()) + I18n.t("</b> will be billed on <b>")
                        + r.nextBillingDate() + "</b> (" + escape(currency) + r.price() + I18n.t(").</p>"
                        + "<p>Thank you,<br/>") + escape(business) + "</p>";
                delivered |= trySend(r.customerEmail().trim(), subject, html);
            }
            if (settings.isSubscriptionReminderStore() && storeEmail != null && !storeEmail.isBlank()) {
                String html = I18n.t("<p>Subscription for <b>") + escape(r.customerName()) + "</b> (" + escape(r.planName()) + ", "
                        + escape(currency) + r.price() + I18n.t(") will be billed on <b>") + r.nextBillingDate() + "</b>.</p>";
                delivered |= trySend(storeEmail.trim(), subject, html);
            }
            if (delivered) {
                subscriptionService.markReminderSent(r.subscriptionId(), r.nextBillingDate());
                sent++;
            }
        }
        return sent;
    }

    private int sendDunning(StoreSettings settings, LocalDate today) {
        if (!mailService.isConfigured()) return 0;
        String business = settings.getBusinessName() == null ? "" : settings.getBusinessName();
        String currency = settings.getCurrencySymbol() == null ? "" : settings.getCurrencySymbol();
        int sent = 0;
        for (SubscriptionService.DunningInfo d : subscriptionService.dunningCandidates(today, zoneOf(settings))) {
            if (d.customerEmail() == null || d.customerEmail().isBlank()) continue;
            String subject = I18n.t("Payment reminder: invoice ") + d.invoiceNumber();
            String html = I18n.t("<p>Hello ") + escape(d.customerName()) + I18n.t(",</p>"
                    + "<p>Our records show that invoice <b>") + escape(d.invoiceNumber()) + I18n.t("</b> for your subscription still has <b>") + escape(currency) + d.outstanding()
                    + I18n.t("</b> outstanding (") + d.daysLate() + I18n.t(" day(s) past its due date). Please arrange payment, or get in touch if you already have.</p>"
                    + "<p>Thank you,<br/>") + escape(business) + "</p>";
            if (trySend(d.customerEmail().trim(), subject, html)) {
                subscriptionService.markDunned(d.subscriptionId(), d.invoiceId(), today, d.reminderNumber(), d.invoiceNumber());
                sent++;
            }
        }
        return sent;
    }

    private boolean sendDigest(StoreSettings settings, LocalDate today, List<SubscriptionService.BillResult> billed,
                               List<String[]> failed, List<String[]> actioned, int reminders, int overdueReminders) {
        String to = settings.getNotificationsEmail();
        if (to == null || to.isBlank() || !mailService.isConfigured()) return false;
        StringBuilder html = new StringBuilder(I18n.t("<p>Subscription job results for ")).append(today).append(".</p>");
        if (!billed.isEmpty()) {
            List<List<String>> rows = new ArrayList<>();
            for (SubscriptionService.BillResult b : billed) {
                rows.add(List.of(nz(b.customerName()), String.valueOf(b.price()), nz(b.invoiceNumber()), String.valueOf(b.nextBillingDate())));
            }
            html.append(I18n.t("<h3>Billed (")).append(billed.size()).append(")</h3>")
                    .append(table(List.of(I18n.t("Customer"), I18n.t("Amount"), I18n.t("Invoice"), I18n.t("Next billing")), rows));
        }
        if (!failed.isEmpty()) {
            List<List<String>> rows = new ArrayList<>();
            for (String[] f : failed) rows.add(List.of(nz(f[0]), nz(f[1])));
            html.append(I18n.t("<h3>Needs attention (")).append(failed.size()).append(")</h3>").append(table(List.of(I18n.t("Subscription"), I18n.t("Problem")), rows));
        }
        if (!actioned.isEmpty()) {
            List<List<String>> rows = new ArrayList<>();
            for (String[] a : actioned) rows.add(List.of(nz(a[0]), nz(a[1]), nz(a[2])));
            html.append(I18n.t("<h3>Paused or cancelled (")).append(actioned.size()).append(")</h3>").append(table(List.of(I18n.t("Customer"), I18n.t("Invoice"), I18n.t("Action")), rows));
        }
        if (reminders > 0) {
            html.append("<p>").append(reminders).append(I18n.t(" upcoming-billing reminder(s) were sent.</p>"));
        }
        if (overdueReminders > 0) {
            html.append("<p>").append(overdueReminders).append(I18n.t(" overdue-invoice reminder(s) were sent to customers.</p>"));
        }
        return trySend(to.trim(), I18n.t("Subscription job: ") + billed.size() + " billed, " + failed.size() + I18n.t(" need attention"), html.toString());
    }

    private boolean trySend(String to, String subject, String html) {
        try {
            mailService.send(to, subject, html, List.of());
            return true;
        } catch (Exception ex) {
            log.warn("Could not send subscription email to {}: {}", to, ex.getMessage());
            return false;
        }
    }

    private static String table(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse:collapse\" border=\"1\" cellpadding=\"6\"><tr>");
        for (String h : headers) sb.append("<th align=\"left\">").append(escape(h)).append("</th>");
        sb.append("</tr>");
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (String cell : row) sb.append("<td>").append(escape(cell)).append("</td>");
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ZoneId zoneOf(StoreSettings settings) {
        try {
            if (settings.getTimeZone() != null && !settings.getTimeZone().isBlank()) {
                return ZoneId.of(settings.getTimeZone().trim());
            }
        } catch (Exception ignored) {
            // fall back to the server's own zone
        }
        return ZoneId.systemDefault();
    }

    private static LocalTime timeOf(StoreSettings settings) {
        try {
            return LocalTime.parse(settings.getSubscriptionJobTime() == null ? "06:30" : settings.getSubscriptionJobTime().trim());
        } catch (Exception ex) {
            return LocalTime.of(6, 30);
        }
    }
}
