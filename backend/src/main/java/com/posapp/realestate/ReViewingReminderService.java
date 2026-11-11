package com.posapp.realestate;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.realestate.dto.RealEstateReminderDtos.ReminderLine;
import com.posapp.realestate.dto.RealEstateReminderDtos.ReminderResult;
import com.posapp.realestate.dto.RealEstateReminderDtos.RemindOne;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * E-mail reminders before a viewing. The lead gets one a set number of hours ahead, and the agent gets one e-mail listing all their viewings coming up. Every reminder is recorded on the
 * viewing, so nobody is reminded twice, and moving a viewing makes it due again. The job runs every quarter of an hour when an admin has switched reminders on; a manager can also send
 * the lead's reminder at once from the viewing, and an admin can preview a run.
 */
@Service
public class ReViewingReminderService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");
    static final int MAX_PER_RUN = 200;
    private static DateTimeFormatter whenFmt() { return I18n.pattern("EEEE d MMMM 'at' HH:mm"); }

    private final ReViewingRepository viewings;
    private final RePropertyRepository properties;
    private final ReLeadRepository leads;
    private final UserRepository users;
    private final ReSettingsService settingsService;
    private final MailService mailService;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;

    public ReViewingReminderService(ReViewingRepository viewings, RePropertyRepository properties, ReLeadRepository leads, UserRepository users, ReSettingsService settingsService,
                                    MailService mailService, RealEstateAccess access, AuditLogService auditLogService) {
        this.viewings = viewings;
        this.properties = properties;
        this.leads = leads;
        this.users = users;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private static boolean valid(String e) {
        return e != null && EMAIL.matcher(e.trim()).matches();
    }

    private static String when(Instant t) {
        return whenFmt().format(t.atZone(ZoneId.systemDefault()));
    }

    /** Sends one reminder run. With {@code preview} nothing is sent or recorded. */
    @Transactional
    public ReminderResult run(String by, boolean preview) {
        access.on();
        ReSettings st = settingsService.entity();
        boolean configured = mailService.isConfigured();
        Instant now = Instant.now();
        List<ReViewing> due = viewings.findByStatusAndStartAtBetweenOrderByStartAtAsc(ViewingStatus.SCHEDULED, now, now.plus(st.getViewingReminderHours(), ChronoUnit.HOURS));
        List<ReminderLine> lines = new ArrayList<>();
        Map<Long, List<ReViewing>> byAgent = new LinkedHashMap<>();
        Map<Long, ReViewing> agentRows = new LinkedHashMap<>();
        int clientsSent = 0, agentsSent = 0, skipped = 0, failed = 0, mails = 0;
        Map<Long, String> agentStatus = new LinkedHashMap<>();
        Map<Long, AppUser> agentUser = new LinkedHashMap<>();

        // the agents first, one e-mail each
        for (ReViewing v : due) {
            if (v.getAgentRemindedAt() == null && v.getAgentUserId() != null) byAgent.computeIfAbsent(v.getAgentUserId(), k -> new ArrayList<>()).add(v);
        }
        for (Map.Entry<Long, List<ReViewing>> e : byAgent.entrySet()) {
            AppUser u = users.findById(e.getKey()).orElse(null);
            agentUser.put(e.getKey(), u);
            String email = u == null || u.getEmail() == null ? null : u.getEmail().trim();
            String status;
            if (u == null || !u.isActive() || !valid(email)) {
                status = "NO_EMAIL";
                skipped++;
            } else if (preview) {
                status = "WOULD_SEND";
            } else if (!configured) {
                status = "NOT_CONFIGURED";
                skipped++;
            } else if (mails >= MAX_PER_RUN) {
                status = "FAILED";
                failed++;
            } else {
                mails++;
                try {
                    mailService.send(email, agentSubject(e.getValue()), agentBody(RealEstateAccess.name(u), e.getValue()), List.of());
                    e.getValue().forEach(v -> v.setAgentRemindedAt(now));
                    status = "SENT";
                    agentsSent++;
                } catch (Exception ex) {
                    status = "FAILED";
                    failed++;
                }
            }
            agentStatus.put(e.getKey(), status);
        }

        // then the leads, one e-mail per viewing
        for (ReViewing v : due) {
            ReLead l = leads.findById(v.getLeadId()).orElse(null);
            ReProperty p = properties.findById(v.getPropertyId()).orElse(null);
            String clientStatus = "";
            String clientEmail = l == null || l.getEmail() == null ? null : l.getEmail().trim();
            if (v.getClientRemindedAt() == null) {
                if (l == null || !valid(clientEmail)) {
                    clientStatus = "NO_EMAIL";
                    skipped++;
                } else if (preview) {
                    clientStatus = "WOULD_SEND";
                } else if (!configured) {
                    clientStatus = "NOT_CONFIGURED";
                    skipped++;
                } else if (mails >= MAX_PER_RUN) {
                    clientStatus = "FAILED";
                    failed++;
                } else {
                    mails++;
                    try {
                        sendToClient(v, l, p, st);
                        v.setClientRemindedAt(now);
                        clientStatus = "SENT";
                        clientsSent++;
                    } catch (Exception ex) {
                        clientStatus = "FAILED";
                        failed++;
                    }
                }
            }
            String as = agentStatus.getOrDefault(v.getAgentUserId(), "");
            if (v.getAgentRemindedAt() != null && !as.equals("SENT")) as = "";
            if (clientStatus.isEmpty() && as.isEmpty()) continue;
            AppUser au = agentUser.get(v.getAgentUserId());
            lines.add(new ReminderLine(v.getId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), l == null ? null : l.getName(), v.getStartAt(), clientEmail, clientStatus,
                    v.getAgentName(), au == null ? null : au.getEmail(), as));
        }
        viewings.saveAll(due);
        String summary = (preview ? I18n.t("Preview: ") : "") + (preview ? lines.size() + I18n.t(" viewings need a reminder") : clientsSent + I18n.t(" clients and ") + agentsSent + I18n.t(" agents e-mailed"))
                + ", " + skipped + I18n.t(" skipped (no e-mail address or e-mail not set up), ") + failed + " failed.";
        if (!preview && (clientsSent > 0 || agentsSent > 0 || failed > 0)) auditLogService.record("RE_VIEWING_REMINDERS", by + " sent viewing reminders: " + summary);
        return new ReminderResult(preview, configured, lines.size(), clientsSent, agentsSent, skipped, failed, summary, lines);
    }

    /** Sends the lead's reminder for one viewing now. */
    @Transactional
    public RemindOne remindClient(Long viewingId) {
        access.on();
        ReViewing v = viewings.findById(viewingId).orElseThrow(() -> ApiException.notFound(I18n.t("Viewing not found.")));
        if (v.getStatus() != ViewingStatus.SCHEDULED) throw ApiException.conflict(I18n.t("Only a scheduled viewing can be reminded."));
        if (v.getStartAt().isBefore(Instant.now())) throw ApiException.conflict(I18n.t("This viewing is already over."));
        ReLead l = leads.findById(v.getLeadId()).orElseThrow(() -> ApiException.notFound(I18n.t("Lead not found.")));
        ReProperty p = properties.findById(v.getPropertyId()).orElse(null);
        String email = l.getEmail() == null ? null : l.getEmail().trim();
        if (!valid(email)) return new RemindOne("NO_EMAIL", null, I18n.t("This lead has no e-mail address. Call or message ") + (l.getPhone() == null ? "them" : l.getPhone()) + " instead.");
        if (!mailService.isConfigured()) return new RemindOne("NOT_CONFIGURED", email, I18n.t("E-mail is not set up yet. Set up SMTP in Settings > Email first."));
        try {
            sendToClient(v, l, p, settingsService.entity());
        } catch (Exception ex) {
            return new RemindOne("FAILED", email, I18n.t("The e-mail could not be sent."));
        }
        v.setClientRemindedAt(Instant.now());
        viewings.save(v);
        auditLogService.record("RE_VIEWING_REMINDER", "Reminded " + l.getName() + " of the viewing on " + when(v.getStartAt()), "ReViewing", v.getId());
        return new RemindOne("SENT", email, I18n.t("Reminder sent to ") + email + ".");
    }

    private void sendToClient(ReViewing v, ReLead l, ReProperty p, ReSettings st) {
        mailService.send(l.getEmail().trim(), I18n.t("Reminder: your viewing on ") + when(v.getStartAt()), clientBody(v, l, p, st), List.of());
    }

    static String clientBody(ReViewing v, ReLead l, ReProperty p, ReSettings st) {
        StringBuilder b = new StringBuilder(I18n.t("<p>Hello ") + HtmlUtils.htmlEscape(l.getName()) + ",</p>");
        b.append(I18n.t("<p>This is a reminder of your viewing:</p><ul>"));
        if (p != null) {
            b.append("<li><strong>").append(HtmlUtils.htmlEscape(p.getTitle())).append("</strong> (").append(HtmlUtils.htmlEscape(p.getCode())).append(")</li>");
            String where = p.getAddress() != null && !p.getAddress().isBlank() ? p.getAddress() : p.getArea();
            if (where != null && !where.isBlank()) b.append(I18n.t("<li>Where: ")).append(HtmlUtils.htmlEscape(where)).append(p.getArea() != null && p.getAddress() != null && !p.getAddress().isBlank() ? ", " + HtmlUtils.htmlEscape(p.getArea()) : "").append("</li>");
        }
        b.append(I18n.t("<li>When: ")).append(when(v.getStartAt())).append("</li>");
        if (v.getAgentName() != null) b.append(I18n.t("<li>You will be met by: ")).append(HtmlUtils.htmlEscape(v.getAgentName())).append("</li>");
        b.append("</ul>");
        String phone = st.getWebsitePhone(), mail = st.getWebsiteEmail();
        if ((phone != null && !phone.isBlank()) || (mail != null && !mail.isBlank())) {
            b.append(I18n.t("<p>If you cannot come, please let us know"));
            if (phone != null && !phone.isBlank()) b.append(I18n.t(" on ")).append(HtmlUtils.htmlEscape(phone));
            if (mail != null && !mail.isBlank()) b.append(phone != null && !phone.isBlank() ? I18n.t(" or at ") : " at ").append(HtmlUtils.htmlEscape(mail));
            b.append(I18n.t(" so we can arrange another time.</p>"));
        } else {
            b.append(I18n.t("<p>If you cannot come, please let us know so we can arrange another time.</p>"));
        }
        b.append(I18n.t("<p>We look forward to seeing you.</p>"));
        return b.toString();
    }

    static String agentSubject(List<ReViewing> list) {
        return list.size() == 1 ? I18n.t("Viewing coming up: ") + when(list.get(0).getStartAt()) : list.size() + I18n.t(" viewings coming up");
    }

    String agentBody(String name, List<ReViewing> list) {
        StringBuilder b = new StringBuilder(I18n.t("<p>Hello ") + HtmlUtils.htmlEscape(name) + I18n.t(",</p><p>You have ") + (list.size() == 1 ? I18n.t("a viewing") : list.size() + " viewings") + I18n.t(" coming up:</p><ul>"));
        for (ReViewing v : list) {
            ReProperty p = properties.findById(v.getPropertyId()).orElse(null);
            ReLead l = leads.findById(v.getLeadId()).orElse(null);
            b.append("<li><strong>").append(when(v.getStartAt())).append("</strong> · ");
            b.append(p == null ? I18n.t("a property") : HtmlUtils.htmlEscape(p.getCode() + " " + p.getTitle()));
            if (p != null && p.getArea() != null && !p.getArea().isBlank()) b.append(" (").append(HtmlUtils.htmlEscape(p.getArea())).append(")");
            if (l != null) {
                b.append(I18n.t(" · with ")).append(HtmlUtils.htmlEscape(l.getName()));
                if (l.getPhone() != null && !l.getPhone().isBlank()) b.append(", ").append(HtmlUtils.htmlEscape(l.getPhone()));
            }
            b.append("</li>");
        }
        return b.append("</ul>").toString();
    }
}
