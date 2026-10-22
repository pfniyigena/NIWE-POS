package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDtos.*;
import com.posapp.law.dto.LawRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tasks and deadlines on matters, the lists across matters, and "my work". */
@Service
public class LawWorkService {

    private final LawAccess access;
    private final LawService lawService;
    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawMatterTaskRepository taskRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public LawWorkService(LawAccess access, LawService lawService, LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository,
                          LawMatterTaskRepository taskRepository, LawMatterDeadlineRepository deadlineRepository, UserRepository userRepository,
                          AuditLogService auditLogService) {
        this.access = access;
        this.lawService = lawService;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.taskRepository = taskRepository;
        this.deadlineRepository = deadlineRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static LocalDate today() {
        return LocalDate.now();
    }

    private AppUser assignee(LawMatter m, Long userId) {
        if (userId == null) return null;
        AppUser u = userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("That person was not found."));
        if (!teamRepository.existsByMatterIdAndUserId(m.getId(), userId)) {
            throw ApiException.badRequest(LawAccess.name(u) + " is not on this matter's team. Add them to the team first.");
        }
        return u;
    }

    // ------------------------------------------------------------------ tasks

    private void applyTask(LawMatter m, LawMatterTask t, TaskRequest r) {
        if (r.title() == null || r.title().isBlank()) throw ApiException.badRequest("Give the task a title.");
        t.setTitle(r.title().trim());
        t.setDescription(clean(r.description()));
        t.setDueDate(r.dueDate());
        if (r.priority() != null) t.setPriority(r.priority());
        AppUser u = assignee(m, r.assignedToUserId());
        t.setAssignedToUserId(u != null ? u.getId() : null);
        t.setAssignedToName(u != null ? LawAccess.name(u) : null);
    }

    @Transactional
    public MatterDto addTask(Actor a, Long matterId, TaskRequest r) {
        LawMatter m = access.workable(a, matterId);
        LawMatterTask t = new LawMatterTask();
        t.setMatterId(matterId);
        applyTask(m, t, r);
        t.setCreatedByName(LawAccess.name(a.user()));
        taskRepository.save(t);
        return lawService.dto(a, m);
    }

    private LawMatterTask task(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    @Transactional
    public MatterDto updateTask(Actor a, Long taskId, TaskRequest r) {
        LawMatterTask t = task(taskId);
        LawMatter m = access.workable(a, t.getMatterId());
        applyTask(m, t, r);
        taskRepository.save(t);
        return lawService.dto(a, m);
    }

    @Transactional
    public MatterDto setTaskStatus(Actor a, Long taskId, TaskStatusRequest r) {
        LawMatterTask t = task(taskId);
        LawMatter m = access.workable(a, t.getMatterId());
        if (r.status() == null) throw ApiException.badRequest("Choose a status.");
        t.setStatus(r.status());
        t.setCompletedAt(r.status() == TaskStatus.DONE ? Instant.now() : null);
        taskRepository.save(t);
        return lawService.dto(a, m);
    }

    @Transactional
    public MatterDto deleteTask(Actor a, Long taskId) {
        LawMatterTask t = task(taskId);
        LawMatter m = access.workable(a, t.getMatterId());
        taskRepository.delete(t);
        return lawService.dto(a, m);
    }

    // ------------------------------------------------------------------ deadlines

    private void applyDeadline(LawMatter m, LawMatterDeadline d, DeadlineRequest r) {
        if (r.title() == null || r.title().isBlank()) throw ApiException.badRequest("Give the deadline a title.");
        if (r.dueDate() == null) throw ApiException.badRequest("Choose the date.");
        d.setTitle(r.title().trim());
        d.setType(r.type() != null ? r.type() : DeadlineType.OTHER);
        if (d.getDueDate() == null || !d.getDueDate().equals(r.dueDate())) d.setEscalationStage(0);
        d.setDueDate(r.dueDate());
        d.setDueTime(r.dueTime());
        d.setNotes(clean(r.notes()));
        AppUser u = assignee(m, r.assignedToUserId());
        d.setAssignedToUserId(u != null ? u.getId() : null);
        d.setAssignedToName(u != null ? LawAccess.name(u) : null);
    }

    @Transactional
    public MatterDto addDeadline(Actor a, Long matterId, DeadlineRequest r) {
        LawMatter m = access.workable(a, matterId);
        LawMatterDeadline d = new LawMatterDeadline();
        d.setMatterId(matterId);
        applyDeadline(m, d, r);
        deadlineRepository.save(d);
        auditLogService.record("LAW_DEADLINE_ADDED", "Deadline '" + d.getTitle() + "' on " + m.getNumber() + " for " + d.getDueDate(), "LawMatter", matterId);
        return lawService.dto(a, m);
    }

    private LawMatterDeadline deadline(Long id) {
        return deadlineRepository.findById(id).orElseThrow(() -> ApiException.notFound("Deadline not found."));
    }

    @Transactional
    public MatterDto updateDeadline(Actor a, Long deadlineId, DeadlineRequest r) {
        LawMatterDeadline d = deadline(deadlineId);
        LawMatter m = access.workable(a, d.getMatterId());
        applyDeadline(m, d, r);
        deadlineRepository.save(d);
        return lawService.dto(a, m);
    }

    @Transactional
    public MatterDto setDeadlineStatus(Actor a, Long deadlineId, DeadlineStatus status) {
        LawMatterDeadline d = deadline(deadlineId);
        LawMatter m = access.workable(a, d.getMatterId());
        if (status == DeadlineStatus.OPEN && d.getStatus() != DeadlineStatus.OPEN) d.setEscalationStage(0);
        d.setStatus(status);
        d.setCompletedAt(status == DeadlineStatus.DONE ? Instant.now() : null);
        d.setCompletedByName(status == DeadlineStatus.DONE ? LawAccess.name(a.user()) : null);
        deadlineRepository.save(d);
        return lawService.dto(a, m);
    }

    @Transactional
    public MatterDto deleteDeadline(Actor a, Long deadlineId) {
        LawMatterDeadline d = deadline(deadlineId);
        LawMatter m = access.workable(a, d.getMatterId());
        deadlineRepository.delete(d);
        auditLogService.record("LAW_DEADLINE_DELETED", "Deleted deadline '" + d.getTitle() + "' on " + m.getNumber(), "LawMatter", m.getId());
        return lawService.dto(a, m);
    }

    // ------------------------------------------------------------------ lists

    private Map<Long, LawMatter> visibleOpen(Actor a) {
        return lawService.visible(a).stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).collect(Collectors.toMap(LawMatter::getId, Function.identity()));
    }

    /** Open tasks (or, with {@code includeDone}, all) on matters the person can see. {@code mine} = only the ones assigned to them; anyone may ask for that. */
    @Transactional(readOnly = true)
    public List<TaskDto> tasks(Actor a, boolean mine, Long assigneeId, boolean includeDone) {
        access.on();
        if (!mine && !a.admin() && !a.viewer()) throw ApiException.forbidden("You cannot see everyone's tasks.");
        Long who = mine ? Long.valueOf(a.user().getId()) : assigneeId;
        Map<Long, LawMatter> matters = visibleOpen(a);
        LocalDate today = today();
        return (includeDone ? taskRepository.findAll() : taskRepository.findByStatusNot(TaskStatus.DONE)).stream()
                .filter(t -> matters.containsKey(t.getMatterId()))
                .filter(t -> who == null || who.equals(t.getAssignedToUserId()))
                .sorted(Comparator.comparing((LawMatterTask t) -> t.getStatus() == TaskStatus.DONE).thenComparing(LawMatterTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(t -> LawMapper.task(t, matters.get(t.getMatterId()), today)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeadlineDto> deadlines(Actor a, boolean mine, Long assigneeId, LocalDate from, LocalDate to, boolean includeDone) {
        access.on();
        if (!mine && !a.admin() && !a.viewer()) throw ApiException.forbidden("You cannot see everyone's deadlines.");
        Long who = mine ? Long.valueOf(a.user().getId()) : assigneeId;
        Map<Long, LawMatter> matters = visibleOpen(a);
        LocalDate today = today();
        return (includeDone ? deadlineRepository.findAll() : deadlineRepository.findByStatus(DeadlineStatus.OPEN)).stream()
                .filter(d -> matters.containsKey(d.getMatterId()))
                .filter(d -> who == null || who.equals(d.getAssignedToUserId()))
                .filter(d -> from == null || !d.getDueDate().isBefore(from))
                .filter(d -> to == null || !d.getDueDate().isAfter(to))
                .sorted(Comparator.comparing(LawMatterDeadline::getDueDate).thenComparing(LawMatterDeadline::getDueTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> LawMapper.deadline(d, matters.get(d.getMatterId()), today)).toList();
    }

    /** Matters I am on, tasks assigned to me, and deadlines assigned to me or unassigned on matters I lead (overdue and the next 30 days). */
    @Transactional(readOnly = true)
    public MineDto mine(Actor a) {
        access.on();
        Long me = a.user().getId();
        List<Long> myMatterIds = teamRepository.findByUserId(me).stream().map(LawMatterTeam::getMatterId).toList();
        List<LawMatter> mine = matterRepository.findAllById(myMatterIds).stream().filter(m -> m.getStatus() != MatterStatus.CLOSED)
                .sorted(Comparator.comparing(LawMatter::getOpenedDate, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        Map<Long, LawMatter> byId = mine.stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        LocalDate today = today();
        LocalDate horizon = today.plusDays(30);
        List<TaskDto> tasks = taskRepository.findByStatusNot(TaskStatus.DONE).stream()
                .filter(t -> byId.containsKey(t.getMatterId()) && me.equals(t.getAssignedToUserId()))
                .sorted(Comparator.comparing(LawMatterTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(t -> LawMapper.task(t, byId.get(t.getMatterId()), today)).toList();
        List<DeadlineDto> deadlines = deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream()
                .filter(d -> byId.containsKey(d.getMatterId()))
                .filter(d -> me.equals(d.getAssignedToUserId()) || (d.getAssignedToUserId() == null && me.equals(byId.get(d.getMatterId()).getLeadUserId())))
                .filter(d -> !d.getDueDate().isAfter(horizon))
                .sorted(Comparator.comparing(LawMatterDeadline::getDueDate).thenComparing(LawMatterDeadline::getDueTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> LawMapper.deadline(d, byId.get(d.getMatterId()), today)).toList();
        return new MineDto(lawService.rows(mine), tasks, deadlines);
    }

    // ------------------------------------------------------------------ the daily e-mail

    /** What one person should be reminded of: deadlines and tasks that are overdue or due within the reminder window. Null when there is nothing. */
    @Transactional(readOnly = true)
    public String digestHtml(AppUser user, int reminderDays) {
        Long me = user.getId();
        List<Long> ids = teamRepository.findByUserId(me).stream().map(LawMatterTeam::getMatterId).toList();
        if (ids.isEmpty()) return null;
        Map<Long, LawMatter> byId = matterRepository.findAllById(ids).stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        LocalDate today = today();
        LocalDate horizon = today.plusDays(reminderDays);
        List<LawMatterDeadline> ds = deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream()
                .filter(d -> byId.containsKey(d.getMatterId()) && !d.getDueDate().isAfter(horizon))
                .filter(d -> me.equals(d.getAssignedToUserId()) || (d.getAssignedToUserId() == null && me.equals(byId.get(d.getMatterId()).getLeadUserId())))
                .sorted(Comparator.comparing(LawMatterDeadline::getDueDate)).toList();
        List<LawMatterTask> ts = taskRepository.findByStatusNot(TaskStatus.DONE).stream()
                .filter(t -> byId.containsKey(t.getMatterId()) && me.equals(t.getAssignedToUserId()) && t.getDueDate() != null && !t.getDueDate().isAfter(horizon))
                .sorted(Comparator.comparing(LawMatterTask::getDueDate)).toList();
        if (ds.isEmpty() && ts.isEmpty()) return null;
        StringBuilder h = new StringBuilder("<p>Hello " + esc(LawAccess.name(user)) + ",</p><p>Here is what needs your attention.</p>");
        if (!ds.isEmpty()) {
            h.append("<h3>Deadlines</h3><ul>");
            for (LawMatterDeadline d : ds) {
                LawMatter m = byId.get(d.getMatterId());
                h.append("<li>").append(d.getDueDate().isBefore(today) ? "<strong style=\"color:#c0392b\">OVERDUE</strong> " : "").append(d.getDueDate()).append(d.getDueTime() != null ? " " + d.getDueTime() : "")
                        .append(" — ").append(esc(d.getTitle())).append(" (").append(esc(m.getNumber())).append(" ").append(esc(m.getTitle())).append(")</li>");
            }
            h.append("</ul>");
        }
        if (!ts.isEmpty()) {
            h.append("<h3>Tasks</h3><ul>");
            for (LawMatterTask t : ts) {
                LawMatter m = byId.get(t.getMatterId());
                h.append("<li>").append(t.getDueDate().isBefore(today) ? "<strong style=\"color:#c0392b\">OVERDUE</strong> " : "").append(t.getDueDate()).append(" — ").append(esc(t.getTitle()))
                        .append(" (").append(esc(m.getNumber())).append(" ").append(esc(m.getTitle())).append(")</li>");
            }
            h.append("</ul>");
        }
        return h.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
