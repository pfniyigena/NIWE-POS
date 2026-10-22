package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.law.dto.LawCalendarDtos.EscalationItem;
import com.posapp.law.dto.LawCalendarDtos.EscalationResult;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Warns the lead lawyer (and whoever the deadline is assigned to) by e-mail when a limitation date is close, because missing one can lose the case.
 * Three steps per deadline: the first warning N days before (the setting), another at 3 days, and one when it is overdue. Each step goes out once;
 * changing the date or reopening the deadline starts it again.
 */
@Service
public class LawEscalationService {

    private static final Logger log = LoggerFactory.getLogger(LawEscalationService.class);
    static final int LAST_DAYS = 3;

    private final LawMatterDeadlineRepository deadlineRepository;
    private final LawMatterRepository matterRepository;
    private final UserRepository userRepository;
    private final LawService lawService;
    private final MailService mailService;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;

    public LawEscalationService(LawMatterDeadlineRepository deadlineRepository, LawMatterRepository matterRepository, UserRepository userRepository, LawService lawService,
                                MailService mailService, ModuleService moduleService, AuditLogService auditLogService) {
        this.deadlineRepository = deadlineRepository;
        this.matterRepository = matterRepository;
        this.userRepository = userRepository;
        this.lawService = lawService;
        this.mailService = mailService;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
    }

    static int stageFor(long daysLeft, int firstDays) {
        if (daysLeft < 0) return 3;
        if (daysLeft <= LAST_DAYS) return 2;
        if (daysLeft <= firstDays) return 1;
        return 0;
    }

    static String stageLabel(int stage) {
        return switch (stage) {
            case 1 -> "Coming up";
            case 2 -> "Last days";
            case 3 -> "Overdue";
            default -> "";
        };
    }

    private record Work(LawMatterDeadline d, LawMatter m, int stage, long daysLeft, List<AppUser> to, String problem) {
    }

    private List<Work> pending(LocalDate today) {
        int first = lawService.settingsEntity().getEscalationDays();
        List<LawMatterDeadline> open = deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream().filter(d -> d.getType() == DeadlineType.LIMITATION).toList();
        if (open.isEmpty()) return List.of();
        Map<Long, LawMatter> matters = matterRepository.findAllById(open.stream().map(LawMatterDeadline::getMatterId).distinct().toList()).stream()
                .collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        List<Work> out = new ArrayList<>();
        for (LawMatterDeadline d : open) {
            LawMatter m = matters.get(d.getMatterId());
            if (m == null || m.getStatus() == MatterStatus.CLOSED) continue;
            long left = ChronoUnit.DAYS.between(today, d.getDueDate());
            int stage = stageFor(left, first);
            if (stage <= d.getEscalationStage()) continue;
            Map<Long, AppUser> who = new LinkedHashMap<>();
            for (Long uid : new Long[]{m.getLeadUserId(), d.getAssignedToUserId()}) {
                if (uid == null || who.containsKey(uid)) continue;
                userRepository.findById(uid).filter(AppUser::isActive).ifPresent(u -> who.put(uid, u));
            }
            List<AppUser> to = who.values().stream().filter(u -> u.getEmail() != null && !u.getEmail().isBlank()).toList();
            String problem = null;
            if (who.isEmpty()) problem = "The matter has no lead lawyer.";
            else if (to.isEmpty()) problem = "Nobody to warn has an e-mail address.";
            out.add(new Work(d, m, stage, left, to, problem));
        }
        out.sort(Comparator.comparing((Work w) -> w.d().getDueDate()));
        return out;
    }

    private static EscalationItem item(Work w) {
        return new EscalationItem(w.d().getId(), w.m().getId(), w.m().getNumber(), w.m().getTitle(), w.d().getTitle(), w.d().getDueDate(), w.daysLeft(), w.stage(), stageLabel(w.stage()),
                w.to().stream().map(LawAccess::name).toList(), w.problem());
    }

    /** What would go out if the run happened now. */
    @Transactional(readOnly = true)
    public List<EscalationItem> preview() {
        return pending(LocalDate.now()).stream().map(LawEscalationService::item).toList();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String subject(Work w) {
        String what = w.d().getTitle() + " (" + w.m().getNumber() + ")";
        if (w.stage() == 3) return "OVERDUE limitation date: " + what;
        return "Limitation date " + (w.daysLeft() == 0 ? "is today" : w.daysLeft() == 1 ? "is tomorrow" : "in " + w.daysLeft() + " days") + ": " + what;
    }

    private static String body(Work w, AppUser to) {
        String when = w.stage() == 3 ? "<strong style=\"color:#c0392b\">The date has passed (" + (-w.daysLeft()) + " day" + (w.daysLeft() == -1 ? "" : "s") + " ago).</strong>"
                : w.daysLeft() == 0 ? "<strong style=\"color:#c0392b\">It falls today.</strong>" : "It falls in <strong>" + w.daysLeft() + "</strong> day" + (w.daysLeft() == 1 ? "" : "s") + ".";
        return "<p>Hello " + esc(LawAccess.name(to)) + ",</p>"
                + "<p>A limitation date is close. Missing one can lose the case, so please check that the step is done or on its way.</p>"
                + "<table cellpadding=\"4\" style=\"border-collapse:collapse;\">"
                + "<tr><td><strong>Matter</strong></td><td>" + esc(w.m().getNumber()) + " " + esc(w.m().getTitle()) + "</td></tr>"
                + "<tr><td><strong>What</strong></td><td>" + esc(w.d().getTitle()) + "</td></tr>"
                + "<tr><td><strong>Date</strong></td><td>" + w.d().getDueDate() + (w.d().getDueTime() != null ? " " + w.d().getDueTime() : "") + "</td></tr>"
                + (w.d().getAssignedToName() != null ? "<tr><td><strong>Assigned to</strong></td><td>" + esc(w.d().getAssignedToName()) + "</td></tr>" : "")
                + (w.d().getNotes() != null ? "<tr><td><strong>Notes</strong></td><td>" + esc(w.d().getNotes()) + "</td></tr>" : "")
                + "</table><p>" + when + "</p><p>When the step is done, mark the deadline as done on the matter so these warnings stop.</p>";
    }

    /** Sends every warning that is due and remembers how far each deadline has got. A mail that cannot be sent leaves that deadline for the next run. */
    @Transactional
    public EscalationResult run() {
        List<Work> todo = pending(LocalDate.now());
        int mails = 0, deadlines = 0, skipped = 0;
        List<EscalationItem> sent = new ArrayList<>();
        for (Work w : todo) {
            if (w.to().isEmpty()) {
                skipped++;
                continue;
            }
            int ok = 0;
            for (AppUser u : w.to()) {
                try {
                    mailService.send(u.getEmail(), subject(w), body(w, u), List.of());
                    ok++;
                } catch (Exception e) {
                    log.warn("Could not send the limitation warning for deadline {} to {}: {}", w.d().getId(), u.getUsername(), e.getMessage());
                }
            }
            if (ok == 0) {
                skipped++;
                continue;
            }
            mails += ok;
            deadlines++;
            w.d().setEscalationStage(w.stage());
            w.d().setEscalatedAt(Instant.now());
            deadlineRepository.save(w.d());
            auditLogService.record("LAW_ESCALATION_SENT", "Limitation warning (" + stageLabel(w.stage()).toLowerCase() + ") for '" + w.d().getTitle() + "' on " + w.m().getNumber() + " sent to "
                    + w.to().stream().map(LawAccess::name).collect(Collectors.joining(", ")), "LawMatter", w.m().getId());
            sent.add(item(w));
        }
        return new EscalationResult(mails, deadlines, skipped, sent);
    }

    /** The button on the settings screen: runs now, whether or not the daily run is switched on. Needs e-mail to be set up. */
    @Transactional
    public EscalationResult runNow() {
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Fill in the e-mail settings first.");
        return run();
    }

    @Scheduled(cron = "0 25 6 * * *")
    public void daily() {
        try {
            if (!moduleService.isEnabled(ModuleKey.LAW_FIRM)) return;
            if (!lawService.settingsEntity().isEscalationEnabled()) return;
            if (!mailService.isConfigured()) return;
            EscalationResult r = run();
            if (r.deadlines() > 0) log.info("Law limitation warnings: {} deadlines, {} mails", r.deadlines(), r.mailsSent());
        } catch (Exception e) {
            log.warn("Law limitation warnings skipped: {}", e.getMessage());
        }
    }
}
