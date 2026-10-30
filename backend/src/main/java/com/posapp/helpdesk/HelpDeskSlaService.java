package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deadlines. A ticket gets a first-response deadline and a resolution deadline from its priority when "SLA" is on in the
 * Help Desk settings. Hours are calendar hours. Time spent waiting for the customer does not count: the deadlines move
 * later by exactly that long. A check every five minutes e-mails the first time a deadline is missed (or is close) and
 * closes resolved tickets that have been quiet long enough.
 */
@Service
public class HelpDeskSlaService {

    private static final Logger log = LoggerFactory.getLogger(HelpDeskSlaService.class);

    private final HelpDeskSettingsService settingsService;
    private final HelpDeskTicketRepository repository;
    private final HelpDeskEvents events;
    private final HelpDeskMailService mailService;
    private final AuditLogService auditLogService;
    private final ModuleService moduleService;

    public HelpDeskSlaService(HelpDeskSettingsService settingsService, HelpDeskTicketRepository repository, HelpDeskEvents events,
                              HelpDeskMailService mailService, AuditLogService auditLogService, ModuleService moduleService) {
        this.settingsService = settingsService;
        this.repository = repository;
        this.events = events;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.moduleService = moduleService;
    }

    public record RunResult(int responseBreaches, int resolutionWarnings, int resolutionBreaches, int autoClosed) {
        public String message() {
            return responseBreaches + " response breach(es), " + resolutionWarnings + " deadline warning(s), "
                    + resolutionBreaches + " resolution breach(es), " + autoClosed + " ticket(s) closed automatically.";
        }
    }

    public int warnHours() {
        return settingsService.entity().getWarnHours();
    }

    /** Sets (or moves) the deadlines of a ticket from its priority; does nothing when SLA is off or the ticket is outside it. */
    public void applyTargets(HelpDeskTicket t) {
        HelpDeskSettings s = settingsService.entity();
        if (!s.isSlaEnabled()) return;
        if (t.getType() != HelpDeskTicketType.CUSTOMER && !s.isSlaInternal()) return;
        Instant base = t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now();
        int resp = switch (t.getPriority()) {
            case LOW -> s.getSlaResponseLow();
            case MEDIUM -> s.getSlaResponseMedium();
            case HIGH -> s.getSlaResponseHigh();
            case URGENT -> s.getSlaResponseUrgent();
        };
        int reso = switch (t.getPriority()) {
            case LOW -> s.getSlaResolutionLow();
            case MEDIUM -> s.getSlaResolutionMedium();
            case HIGH -> s.getSlaResolutionHigh();
            case URGENT -> s.getSlaResolutionUrgent();
        };
        long waited = t.getWaitingSeconds();
        t.setFirstResponseDueAt(base.plus(resp, ChronoUnit.HOURS).plusSeconds(waited));
        t.setResolutionDueAt(base.plus(reso, ChronoUnit.HOURS).plusSeconds(waited));
        // a new deadline starts a new warning round
        t.setResponseEscalatedAt(null);
        t.setResolutionWarnedAt(null);
        t.setResolutionEscalatedAt(null);
    }

    /** Priority changed on a ticket that already has deadlines: recompute them from the new priority. */
    public void priorityChanged(HelpDeskTicket t) {
        if (t.getResolutionDueAt() == null) return;
        Instant keptResponseDue = t.getFirstRespondedAt() != null ? t.getFirstResponseDueAt() : null;
        applyTargets(t);
        if (keptResponseDue != null) {
            t.setFirstResponseDueAt(keptResponseDue);   // the first response already happened against the old deadline
        }
    }

    public void beginWaiting(HelpDeskTicket t, Instant now) {
        if (t.getWaitingSince() == null) t.setWaitingSince(now);
    }

    /** The clock starts again; the deadlines move later by the time spent waiting. */
    public void endWaiting(HelpDeskTicket t, Instant now) {
        if (t.getWaitingSince() == null) return;
        long secs = Math.max(0, Duration.between(t.getWaitingSince(), now).getSeconds());
        t.setWaitingSeconds(t.getWaitingSeconds() + secs);
        if (t.getResolutionDueAt() != null) t.setResolutionDueAt(t.getResolutionDueAt().plusSeconds(secs));
        if (t.getFirstResponseDueAt() != null && t.getFirstRespondedAt() == null) t.setFirstResponseDueAt(t.getFirstResponseDueAt().plusSeconds(secs));
        t.setWaitingSince(null);
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 150_000L)
    public void scheduledRun() {
        try {
            if (!moduleService.isEnabled(ModuleKey.HELPDESK)) return;
            run(Instant.now());
        } catch (Exception ex) {
            log.warn("Help desk SLA check failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public RunResult run(Instant now) {
        HelpDeskSettings s = settingsService.entity();
        int respBreach = 0;
        int warn = 0;
        int resoBreach = 0;
        int closed = 0;
        if (s.isSlaEnabled() && s.isEscalationEnabled()) {
            List<HelpDeskTicket> open = repository.findByStatusIn(List.of(HelpDeskStatus.OPEN, HelpDeskStatus.IN_PROGRESS));
            for (HelpDeskTicket t : open) {
                Instant respDue = t.getFirstResponseDueAt();
                if (respDue != null && t.getFirstRespondedAt() == null && now.isAfter(respDue) && t.getResponseEscalatedAt() == null) {
                    t.setResponseEscalatedAt(now);
                    repository.save(t);
                    events.record(t, HelpDeskEventKind.SLA_BREACH, "No first response by the deadline", "System");
                    mailService.notifySla(t, "RESPONSE_BREACH");
                    respBreach++;
                }
                Instant due = t.getResolutionDueAt();
                if (due == null) continue;
                if (now.isAfter(due)) {
                    if (t.getResolutionEscalatedAt() == null) {
                        t.setResolutionEscalatedAt(now);
                        repository.save(t);
                        events.record(t, HelpDeskEventKind.SLA_BREACH, "Resolution deadline passed", "System");
                        mailService.notifySla(t, "RESOLUTION_BREACH");
                        resoBreach++;
                    }
                } else if (s.getWarnHours() > 0 && t.getResolutionWarnedAt() == null && !now.isBefore(due.minus(s.getWarnHours(), ChronoUnit.HOURS))) {
                    t.setResolutionWarnedAt(now);
                    repository.save(t);
                    events.record(t, HelpDeskEventKind.SLA_WARNING, "Resolution deadline is within " + s.getWarnHours() + " hour(s)", "System");
                    mailService.notifySla(t, "RESOLUTION_WARNING");
                    warn++;
                }
            }
        }
        if (s.getAutoCloseDays() > 0) {
            Instant cutoff = now.minus(s.getAutoCloseDays(), ChronoUnit.DAYS);
            for (HelpDeskTicket t : repository.findByStatusAndResolvedAtBefore(HelpDeskStatus.RESOLVED, cutoff)) {
                t.setStatus(HelpDeskStatus.CLOSED);
                t.setClosedAt(now);
                repository.save(t);
                events.record(t, HelpDeskEventKind.AUTO_CLOSED, "Closed automatically after " + s.getAutoCloseDays() + " day(s) resolved", "System");
                auditLogService.record("HELPDESK_TICKET_CLOSED", "Closed ticket " + t.getTicketNumber() + " automatically", "HelpDeskTicket", t.getId());
                closed++;
            }
        }
        return new RunResult(respBreach, warn, resoBreach, closed);
    }
}
