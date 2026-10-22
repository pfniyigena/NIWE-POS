package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawCalendarDtos.CalendarEntry;
import com.posapp.law.dto.LawCalendarDtos.EscalationItem;
import com.posapp.law.dto.LawCalendarDtos.EscalationResult;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The deadlines calendar (screen and .ics file) and the limitation-date warnings. Open to any login; what people see follows the same rules as the lists. */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawCalendarController {

    private final LawCalendarService calendar;
    private final LawEscalationService escalation;
    private final LawAccess access;

    public LawCalendarController(LawCalendarService calendar, LawEscalationService escalation, LawAccess access) {
        this.calendar = calendar;
        this.escalation = escalation;
        this.access = access;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    @GetMapping("/calendar")
    public List<CalendarEntry> entries(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean mine, @RequestParam(required = false) Long assigneeId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(defaultValue = "true") boolean tasks) {
        return calendar.entries(actor(me), mine, assigneeId, from, to, includeDone, tasks);
    }

    /** A calendar file for Outlook, Google Calendar or a phone. Open deadlines (and tasks, unless {@code tasks=false}) from {@code from} to {@code to}. */
    @GetMapping("/calendar/ics")
    public ResponseEntity<byte[]> ics(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean mine, @RequestParam(required = false) Long assigneeId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "true") boolean tasks) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate t = to != null ? to : LocalDate.now().plusDays(180);
        byte[] body = calendar.ics(calendar.entries(actor(me), mine, assigneeId, f, t, false, tasks));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("law-firm-calendar.ics", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).body(body);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/escalations")
    public List<EscalationItem> escalations() {
        access.on();
        return escalation.preview();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/escalations/send")
    public EscalationResult send() {
        access.on();
        return escalation.runNow();
    }
}
