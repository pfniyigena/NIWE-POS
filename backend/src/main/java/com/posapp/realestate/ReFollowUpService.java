package com.posapp.realestate;

import com.posapp.i18n.I18n;
import com.posapp.audit.AuditLogService;
import com.posapp.email.MailService;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.Board;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.DigestAgent;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.DigestResult;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Follow-up discipline for leads. The board shows an agent (or everyone) the calls that are late, due today and coming up, and the leads that have gone quiet. The morning e-mail
 * sends each agent the same lists. It is off until an admin switches it on in the settings; an admin can also preview it or send it at once.
 */
@Service
public class ReFollowUpService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");
    /** Most e-mails one run sends, so a run cannot go on for minutes. */
    static final int MAX_PER_RUN = 200;
    /** Most leads listed under one heading of an e-mail; the rest are counted. */
    static final int MAX_LISTED = 25;

    private final ReLeadService leadService;
    private final ReSettingsService settingsService;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;

    public ReFollowUpService(ReLeadService leadService, ReSettingsService settingsService, UserRepository userRepository, MailService mailService, RealEstateAccess access,
                             AuditLogService auditLogService) {
        this.leadService = leadService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private static List<LeadRow> sorted(List<LeadRow> rows, Comparator<LeadRow> by) {
        List<LeadRow> l = new ArrayList<>(rows);
        l.sort(by);
        return l;
    }

    private static final Comparator<LeadRow> BY_DATE_THEN_NAME = Comparator.comparing(LeadRow::nextFollowUp, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(LeadRow::name,
            String.CASE_INSENSITIVE_ORDER);

    /** The lists for one agent ({@code agentId}), or for everyone when {@code all} is set. Without either, the person asking. */
    @Transactional(readOnly = true)
    public Board board(Actor a, Long agentId, boolean all, Integer days) {
        access.on();
        int ahead = days == null ? 7 : Math.max(1, Math.min(days, 60));
        Long who = all ? null : agentId != null ? agentId : a.user().getId();
        String name = null;
        if (who != null) name = userRepository.findById(who).map(RealEstateAccess::name).orElse(null);
        List<LeadRow> open = leadService.list(null, null, null, who, null, false, true);
        return split(open, ahead, settingsService.entity().getStaleLeadDays(), who, name, all, settingsService.entity().isFollowUpEmailEnabled());
    }

    static Board split(List<LeadRow> open, int ahead, int staleDays, Long who, String name, boolean all, boolean mailEnabled) {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(ahead);
        List<LeadRow> overdue = new ArrayList<>(), due = new ArrayList<>(), upcoming = new ArrayList<>(), quiet = new ArrayList<>();
        for (LeadRow r : open) {
            LocalDate d = r.nextFollowUp();
            if (d != null && d.isBefore(today)) overdue.add(r);
            else if (d != null && d.isEqual(today)) due.add(r);
            else if (d != null && !d.isAfter(until)) upcoming.add(r);
            else if (r.stale()) quiet.add(r);
        }
        return new Board(who, name, all, ahead, staleDays, mailEnabled, sorted(overdue, BY_DATE_THEN_NAME), sorted(due, BY_DATE_THEN_NAME), sorted(upcoming, BY_DATE_THEN_NAME),
                sorted(quiet, Comparator.comparing((LeadRow r) -> r.lastContactAt() != null ? r.lastContactAt() : r.createdAt(), Comparator.nullsLast(Comparator.naturalOrder()))));
    }

    /**
     * One run of the morning e-mail: every agent with something late, due today or gone quiet gets one e-mail. With {@code preview} nothing is sent and nothing is recorded, and the result
     * shows who would get what. Leads nobody looks after are counted, not e-mailed.
     */
    @Transactional
    public DigestResult run(String by, boolean preview) {
        access.on();
        ReSettings st = settingsService.entity();
        boolean configured = mailService.isConfigured();
        Board all = split(leadService.list(null, null, null, null, null, false, true), 7, st.getStaleLeadDays(), null, null, true, st.isFollowUpEmailEnabled());
        Map<Long, List<LeadRow>[]> byAgent = new LinkedHashMap<>();
        int unassigned = 0;
        List<List<LeadRow>> buckets = List.of(all.overdue(), all.today(), all.quiet());
        for (int i = 0; i < buckets.size(); i++) {
            for (LeadRow r : buckets.get(i)) {
                if (r.assignedUserId() == null) {
                    unassigned++;
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<LeadRow>[] slot = byAgent.computeIfAbsent(r.assignedUserId(), k -> new List[]{new ArrayList<LeadRow>(), new ArrayList<LeadRow>(), new ArrayList<LeadRow>()});
                slot[i].add(r);
            }
        }
        List<DigestAgent> details = new ArrayList<>();
        int sent = 0, skipped = 0, failed = 0, mails = 0;
        for (Map.Entry<Long, List<LeadRow>[]> e : byAgent.entrySet()) {
            AppUser u = userRepository.findById(e.getKey()).orElse(null);
            List<LeadRow>[] x = e.getValue();
            String name = u == null ? I18n.t("Someone") : RealEstateAccess.name(u);
            String email = u == null || u.getEmail() == null ? null : u.getEmail().trim();
            String status;
            if (u == null || !u.isActive() || email == null || !EMAIL.matcher(email).matches()) {
                status = "NO_EMAIL";
                skipped++;
            } else if (!preview && !configured) {
                status = "NOT_CONFIGURED";
                skipped++;
            } else if (preview) {
                status = "WOULD_SEND";
            } else if (mails >= MAX_PER_RUN) {
                status = "FAILED";
                failed++;
            } else {
                mails++;
                try {
                    mailService.send(email, subject(x), body(name, x, st.getStaleLeadDays()), List.of());
                    status = "SENT";
                    sent++;
                } catch (Exception ex) {
                    status = "FAILED";
                    failed++;
                }
            }
            details.add(new DigestAgent(e.getKey(), name, email, x[0].size(), x[1].size(), x[2].size(), status));
        }
        String summary = (preview ? I18n.t("Preview: ") : "") + (preview ? details.stream().filter(d -> "WOULD_SEND".equals(d.status())).count() + I18n.t(" agents would get an e-mail") : sent + " e-mailed")
                + ", " + skipped + I18n.t(" skipped (no e-mail address or e-mail not set up), ") + failed + " failed, " + unassigned + I18n.t(" leads with nobody assigned.");
        if (!preview) {
            st.setFollowUpLastRun(LocalDate.now());
            st.setFollowUpLastSummary(by + ": " + summary);
            auditLogService.record("RE_FOLLOWUP_DIGEST", by + " sent the follow-up e-mails: " + summary);
        }
        return new DigestResult(preview, configured, byAgent.size(), sent, skipped, failed, unassigned, summary, details);
    }

    static String subject(List<LeadRow>[] x) {
        List<String> parts = new ArrayList<>();
        if (!x[0].isEmpty()) parts.add(x[0].size() + " late");
        if (!x[1].isEmpty()) parts.add(x[1].size() + I18n.t(" due today"));
        if (!x[2].isEmpty()) parts.add(x[2].size() + I18n.t(" gone quiet"));
        return I18n.t("Your lead follow-ups: ") + String.join(", ", parts);
    }

    static String body(String name, List<LeadRow>[] x, int staleDays) {
        StringBuilder b = new StringBuilder(I18n.t("<p>Good morning ") + HtmlUtils.htmlEscape(name) + I18n.t(",</p><p>These are the leads that need a call from you today.</p>"));
        section(b, I18n.t("Late follow-ups"), x[0], true);
        section(b, I18n.t("Due today"), x[1], false);
        section(b, I18n.t("Gone quiet (no contact for ") + staleDays + I18n.t(" days or more)"), x[2], false);
        b.append(I18n.t("<p>Open <strong>Real Estate &gt; My follow-ups</strong> to log the call and set the next date.</p>"));
        return b.toString();
    }

    private static void section(StringBuilder b, String title, List<LeadRow> rows, boolean showDate) {
        if (rows.isEmpty()) return;
        b.append("<h3>").append(HtmlUtils.htmlEscape(title)).append(" (").append(rows.size()).append(")</h3><ul>");
        LocalDate today = LocalDate.now();
        int n = 0;
        for (LeadRow r : rows) {
            if (n++ >= MAX_LISTED) break;
            b.append("<li><strong>").append(HtmlUtils.htmlEscape(r.name())).append("</strong>");
            String contact = r.phone() != null ? r.phone() : r.email();
            if (contact != null) b.append(" · ").append(HtmlUtils.htmlEscape(contact));
            if (r.propertyCode() != null) b.append(" · ").append(HtmlUtils.htmlEscape(r.propertyCode()));
            if (showDate && r.nextFollowUp() != null) {
                long late = ChronoUnit.DAYS.between(r.nextFollowUp(), today);
                b.append(I18n.t(" · was due ")).append(r.nextFollowUp()).append(" (").append(late).append(late == 1 ? " day" : " days").append(" late)");
            }
            b.append("</li>");
        }
        b.append("</ul>");
        if (rows.size() > MAX_LISTED) b.append(I18n.t("<p>… and ")).append(rows.size() - MAX_LISTED).append(" more.</p>");
    }
}
