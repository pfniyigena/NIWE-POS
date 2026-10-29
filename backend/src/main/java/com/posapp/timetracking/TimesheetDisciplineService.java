package com.posapp.timetracking;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.timetracking.dto.TimeDtos.*;
import com.posapp.timetracking.dto.TimeRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps timesheets honest: stops timers somebody forgot, copies last week's entries, lists who has not finished their week and
 * e-mails them. The rules (period lock, daily limit, project team, budget) are applied by {@link TimeTrackingService} on every entry
 * made here, so nothing in this class can get around them.
 */
@Service
public class TimesheetDisciplineService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetDisciplineService.class);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final TimeTrackingService time;
    private final TimeSettingsRepository settingsRepository;
    private final TimeTimerRepository timerRepository;
    private final TimeEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final ModuleService moduleService;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;

    public TimesheetDisciplineService(TimeTrackingService time, TimeSettingsRepository settingsRepository, TimeTimerRepository timerRepository,
                                      TimeEntryRepository entryRepository, UserRepository userRepository, ModuleService moduleService,
                                      AuditLogService auditLogService, MailService mailService, StoreSettingsService storeSettingsService) {
        this.time = time;
        this.settingsRepository = settingsRepository;
        this.timerRepository = timerRepository;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.moduleService = moduleService;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ forgotten timers

    /** Every quarter of an hour: a timer running longer than the limit in the settings is stopped and its time saved as a draft to check. */
    @Scheduled(cron = "0 7,22,37,52 * * * *")
    public void autoStopJob() {
        try {
            if (!moduleService.isEnabled(ModuleKey.TIME_TRACKING)) return;
            int n = autoStopLongTimers();
            if (n > 0) log.info("Time tracking: stopped {} forgotten timer(s)", n);
        } catch (Exception e) {
            log.warn("Time tracking: stopping forgotten timers failed: {}", e.getMessage());
        }
    }

    /** Returns how many timers were stopped (or dropped, when their time could not be saved). */
    public int autoStopLongTimers() {
        int limit = time.settingsEntity().getTimerAutoStopHours();
        if (limit <= 0) return 0;
        Instant cutoff = Instant.now().minus(Duration.ofHours(limit));
        int n = 0;
        for (TimeTimer t : timerRepository.findAll()) {
            if (t.getStartedAt().isAfter(cutoff)) continue;
            AppUser user = userRepository.findById(t.getUserId()).orElse(null);
            if (user == null) {
                timerRepository.delete(t);
                continue;
            }
            LocalDate date = t.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            int cap = time.settingsEntity().getMaxHoursPerDay() * 60;
            int already = entryRepository.findByUserIdAndWorkDate(user.getId(), date).stream().mapToInt(TimeEntry::getMinutes).sum();
            int minutes = Math.min(limit * 60, cap - already);
            String note = "Timer left running; stopped automatically after " + limit + " hours. Please check this time.";
            String desc = t.getDescription() == null || t.getDescription().isBlank() ? note : t.getDescription() + " (" + note + ")";
            try {
                if (minutes < 1) throw ApiException.conflict("The daily limit is already used on " + date + ".");
                time.stopTimer(user, new TimerStopRequest(desc.length() > 1000 ? desc.substring(0, 1000) : desc, minutes));
                auditLogService.record("TIME_TIMER_AUTO_STOPPED", "Stopped " + TimeTrackingService.actorName(user) + "'s timer left running since " + t.getStartedAt()
                        + "; saved " + hours(minutes) + " h on " + date, "TimeTimer", t.getId());
            } catch (ApiException ex) {
                time.discardTimer(user);
                auditLogService.record("TIME_TIMER_AUTO_DISCARDED", "Dropped " + TimeTrackingService.actorName(user) + "'s timer left running since " + t.getStartedAt()
                        + "; its time could not be saved: " + ex.getMessage(), "TimeTimer", t.getId());
            }
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ copy last week

    /** Copies last week's entries of this person to the week of {@code week} (default: this week), as drafts. Future days are not filled. */
    public CopyResult copyWeek(AppUser user, LocalDate week) {
        LocalDate target = (week != null ? week : LocalDate.now()).with(DayOfWeek.MONDAY);
        LocalDate source = target.minusDays(7);
        List<TimeEntry> from = entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), source, source.plusDays(6));
        List<TimeEntry> existing = entryRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(user.getId(), target, target.plusDays(6));
        int copied = 0, skipped = 0;
        LinkedHashSet<String> notes = new LinkedHashSet<>();
        for (TimeEntry e : from) {
            LocalDate date = e.getWorkDate().plusDays(7);
            if (date.isAfter(LocalDate.now())) { skipped++; notes.add("Days that have not happened yet were left out."); continue; }
            boolean dup = existing.stream().anyMatch(x -> x.getWorkDate().equals(date) && x.getProjectId().equals(e.getProjectId())
                    && java.util.Objects.equals(x.getTaskId(), e.getTaskId()) && x.getMinutes() == e.getMinutes()
                    && java.util.Objects.equals(x.getDescription(), e.getDescription()));
            if (dup) { skipped++; notes.add("Entries that are already there were not copied again."); continue; }
            try {
                time.createEntry(user, new EntryRequest(e.getProjectId(), e.getTaskId(), date, null, e.getMinutes(), e.getDescription(), e.isBillable()));
                copied++;
            } catch (ApiException ex) {
                skipped++;
                notes.add(ex.getMessage());
            }
        }
        if (copied > 0) auditLogService.record("TIME_WEEK_COPIED", TimeTrackingService.actorName(user) + " copied " + copied + " time entr" + (copied == 1 ? "y" : "ies")
                + " from the week of " + source + " to the week of " + target);
        return new CopyResult(copied, skipped, new ArrayList<>(notes));
    }

    // ------------------------------------------------------------------ who has not finished their week

    @Transactional(readOnly = true)
    public MissingDto missing(LocalDate week) {
        time.requireModule();
        LocalDate start = (week != null ? week : LocalDate.now()).with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
        List<TimeEntry> thisWeek = entryRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(start, end);
        Set<Long> regulars = new LinkedHashSet<>();
        entryRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(start.minusDays(56), start.minusDays(1)).forEach(e -> regulars.add(e.getUserId()));
        Map<Long, List<TimeEntry>> byUser = new HashMap<>();
        for (TimeEntry e : thisWeek) byUser.computeIfAbsent(e.getUserId(), k -> new ArrayList<>()).add(e);
        regulars.addAll(byUser.keySet());
        List<MissingRow> rows = new ArrayList<>();
        for (AppUser u : userRepository.findByActiveTrue()) {
            if (!regulars.contains(u.getId())) continue;
            List<TimeEntry> mine = byUser.getOrDefault(u.getId(), List.of());
            long total = mine.stream().mapToLong(TimeEntry::getMinutes).sum();
            long draft = mine.stream().filter(e -> e.getStatus() == TimeEntryStatus.DRAFT).mapToLong(TimeEntry::getMinutes).sum();
            long rejected = mine.stream().filter(e -> e.getStatus() == TimeEntryStatus.REJECTED).mapToLong(TimeEntry::getMinutes).sum();
            long sent = total - draft - rejected;
            String status;
            if (total == 0) status = "NOTHING";
            else if (rejected > 0) status = "SENT_BACK";
            else if (draft > 0) status = "DRAFTS";
            else continue;
            rows.add(new MissingRow(u.getId(), TimeTrackingService.actorName(u), u.getEmail() != null && !u.getEmail().isBlank(), status, hours(total),
                    hours(draft + rejected), hours(sent)));
        }
        rows.sort(Comparator.comparing(MissingRow::userName, String.CASE_INSENSITIVE_ORDER));
        return new MissingDto(start, end, rows);
    }

    /** E-mails the people listed (everyone in the list when {@code userIds} is empty). */
    public ReminderResult sendReminders(AppUser actor, LocalDate week, List<Long> userIds, String by) {
        MissingDto m = missing(week);
        if (!mailService.isConfigured()) {
            return new ReminderResult(0, 0, 0, List.of("E-mail is not set up yet (Settings > Email settings), so no reminder was sent."));
        }
        Set<Long> only = userIds == null || userIds.isEmpty() ? null : new LinkedHashSet<>(userIds);
        String business = storeSettingsService.getSettingsEntity().getBusinessName();
        int sent = 0, noEmail = 0, failed = 0;
        List<String> notes = new ArrayList<>();
        for (MissingRow r : m.rows()) {
            if (only != null && !only.contains(r.userId())) continue;
            AppUser u = userRepository.findById(r.userId()).orElse(null);
            if (u == null || u.getEmail() == null || u.getEmail().isBlank()) { noEmail++; notes.add(r.userName() + " has no e-mail address."); continue; }
            String what = switch (r.status()) {
                case "NOTHING" -> "no time is logged for that week.";
                case "SENT_BACK" -> "some of your time was sent back and needs correcting.";
                default -> hoursText(r.hoursNotSubmitted()) + " of your time is not submitted yet.";
            };
            String body = "<p>Hello " + html(r.userName()) + ",</p><p>Your timesheet for the week of " + m.weekStart().format(DAY) + " to " + m.weekEnd().format(DAY)
                    + " is not complete: " + what + "</p><p>Please open <b>Time Tracking &gt; My timesheet</b>, check your entries and submit the week.</p>"
                    + "<p>" + (business == null || business.isBlank() ? "Thank you." : "Thank you,<br>" + html(business)) + "</p>";
            try {
                mailService.send(u.getEmail().trim(), "Please complete your timesheet (week of " + m.weekStart().format(DAY) + ")"
                        + (business == null || business.isBlank() ? "" : " - " + business), body, List.of());
                sent++;
            } catch (Exception ex) {
                failed++;
                notes.add("Could not send to " + r.userName() + ": " + ex.getMessage());
            }
        }
        auditLogService.record("TIME_REMINDERS_SENT", by + " sent " + sent + " timesheet reminder(s) for the week of " + m.weekStart()
                + (noEmail > 0 ? "; " + noEmail + " without an e-mail address" : "") + (failed > 0 ? "; " + failed + " failed" : ""));
        return new ReminderResult(sent, noEmail, failed, notes);
    }

    private static String hoursText(BigDecimal h) {
        return h.stripTrailingZeros().toPlainString() + " h";
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Every morning at 08:10: on the day chosen in the settings, e-mails the people whose timesheet for last week is not complete. */
    @Scheduled(cron = "0 10 8 * * *")
    public void reminderJob() {
        try {
            if (!moduleService.isEnabled(ModuleKey.TIME_TRACKING)) return;
            TimeSettings s = time.settingsEntity();
            LocalDate today = LocalDate.now();
            if (!s.isReminderEnabled() || s.getReminderDay() != today.getDayOfWeek().getValue() || today.equals(s.getReminderLastSentOn())) return;
            s.setReminderLastSentOn(today);
            settingsRepository.save(s);
            ReminderResult r = sendReminders(null, today.minusDays(7), null, "The reminder job");
            if (r.sent() > 0 || r.failed() > 0) log.info("Time tracking reminders: sent {}, no e-mail {}, failed {}", r.sent(), r.noEmail(), r.failed());
        } catch (Exception e) {
            log.warn("Time tracking reminders failed: {}", e.getMessage());
        }
    }
}
