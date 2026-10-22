package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDocDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Onboarding and offboarding checklists: reusable templates, and the copy started for each new starter or leaver. */
@Service
public class ChecklistService {

    static final int MAX_ITEMS = 60;

    private final ChecklistTemplateRepository templateRepository;
    private final EmployeeChecklistRepository checklistRepository;
    private final EmployeeChecklistItemRepository itemRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public ChecklistService(ChecklistTemplateRepository templateRepository, EmployeeChecklistRepository checklistRepository, EmployeeChecklistItemRepository itemRepository,
                            EmployeeRepository employeeRepository, BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.templateRepository = templateRepository;
        this.checklistRepository = checklistRepository;
        this.itemRepository = itemRepository;
        this.employeeRepository = employeeRepository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    // ---- templates ----

    private static final String[][] ONBOARDING = {
            {"Collect a copy of the national ID and a passport photo", "HR", "0"},
            {"Sign the employment contract", "HR", "0"},
            {"Collect bank account, tax number and social security number", "HR", "0"},
            {"Create the system login and e-mail account", "IT", "0"},
            {"Hand over equipment, uniform and access card", "Manager", "1"},
            {"Introduce to the team and explain the duties", "Manager", "1"},
            {"Safety and company policy induction", "Manager", "3"},
            {"Register with the social security fund and the tax authority", "HR", "7"},
            {"Review at the end of the probation period", "Manager", "90"},
    };
    private static final String[][] OFFBOARDING = {
            {"Record the resignation or notice letter", "HR", "-14"},
            {"Hand over duties and files", "Manager", "-3"},
            {"Return equipment, keys, uniform and access card", "Manager", "0"},
            {"Switch off the system login and e-mail", "IT", "0"},
            {"Exit interview", "HR", "0"},
            {"Clear any staff loan or advance", "Payroll", "0"},
            {"Pay the final salary and unused leave", "Payroll", "5"},
            {"Give the certificate of service", "HR", "5"},
    };

    /** Adds the two standard checklists when there are no templates at all (so an HR team that deleted them is not given them back while it has others). */
    @Transactional
    public void ensureDefaults() {
        if (templateRepository.count() > 0) return;
        seed("Standard onboarding", ChecklistKind.ONBOARDING, "Tasks for a new starter. Due dates count from the hire date.", ONBOARDING);
        seed("Standard offboarding", ChecklistKind.OFFBOARDING, "Tasks for a leaver. Due dates count from the last working day.", OFFBOARDING);
    }

    private void seed(String name, ChecklistKind kind, String description, String[][] rows) {
        ChecklistTemplate t = new ChecklistTemplate();
        t.setName(name);
        t.setKind(kind);
        t.setDescription(description);
        int i = 0;
        for (String[] r : rows) {
            ChecklistTemplateItem it = new ChecklistTemplateItem();
            it.setTemplate(t);
            it.setSortOrder(i++);
            it.setTitle(r[0]);
            it.setResponsible(r[1]);
            it.setDueOffsetDays(Integer.parseInt(r[2]));
            t.getItems().add(it);
        }
        templateRepository.save(t);
    }

    private static TemplateDto dto(ChecklistTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getKind(), t.getDescription(), t.isActive(),
                t.getItems().stream().map(i -> new TemplateItemDto(i.getId(), i.getSortOrder(), i.getTitle(), i.getResponsible(), i.getDueOffsetDays())).toList());
    }

    @Transactional
    public List<TemplateDto> templates() {
        ensureDefaults();
        return templateRepository.findAllByOrderByKindDescNameAsc().stream().map(ChecklistService::dto).toList();
    }

    private ChecklistTemplate template(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Checklist template not found"));
    }

    private void apply(ChecklistTemplate t, TemplateRequest r) {
        String name = clean(r.name(), 120, "The name");
        if (name == null) throw ApiException.badRequest("Give the checklist a name.");
        if (r.kind() == null) throw ApiException.badRequest("Choose whether it is for new starters (onboarding) or leavers (offboarding).");
        for (ChecklistTemplate o : templateRepository.findAll()) {
            if (!o.getId().equals(t.getId()) && o.getName().equalsIgnoreCase(name)) {
                throw ApiException.conflict("There is already a checklist called " + name + ".");
            }
        }
        t.setName(name);
        t.setKind(r.kind());
        t.setDescription(clean(r.description(), 300, "The description"));
        if (r.active() != null) t.setActive(r.active());
        List<TemplateItemRequest> items = r.items() == null ? List.of() : r.items();
        if (items.size() > MAX_ITEMS) throw ApiException.badRequest("A checklist can have at most " + MAX_ITEMS + " tasks.");
        t.getItems().clear();
        int i = 0;
        for (TemplateItemRequest ir : items) {
            String title = clean(ir.title(), 200, "A task title");
            if (title == null) throw ApiException.badRequest("Every task needs a title.");
            int offset = ir.dueOffsetDays() == null ? 0 : ir.dueOffsetDays();
            if (offset < -365 || offset > 730) throw ApiException.badRequest("A due date can be at most 365 days before or 730 days after the start date.");
            ChecklistTemplateItem it = new ChecklistTemplateItem();
            it.setTemplate(t);
            it.setSortOrder(i++);
            it.setTitle(title);
            it.setResponsible(clean(ir.responsible(), 60, "The responsible person"));
            it.setDueOffsetDays(offset);
            t.getItems().add(it);
        }
    }

    @Transactional
    public TemplateDto createTemplate(TemplateRequest r) {
        ChecklistTemplate t = new ChecklistTemplate();
        apply(t, r);
        ChecklistTemplate saved = templateRepository.save(t);
        auditLogService.record("CHECKLIST_TEMPLATE_SAVED", "Created checklist template " + saved.getName(), "ChecklistTemplate", saved.getId());
        return dto(saved);
    }

    @Transactional
    public TemplateDto updateTemplate(Long id, TemplateRequest r) {
        ChecklistTemplate t = template(id);
        apply(t, r);
        ChecklistTemplate saved = templateRepository.save(t);
        auditLogService.record("CHECKLIST_TEMPLATE_SAVED", "Updated checklist template " + saved.getName(), "ChecklistTemplate", id);
        return dto(saved);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ChecklistTemplate t = template(id);
        templateRepository.delete(t);
        auditLogService.record("CHECKLIST_TEMPLATE_DELETED", "Deleted checklist template " + t.getName(), "ChecklistTemplate", id);
    }

    // ---- checklists of employees ----

    private Employee employee(Long id) {
        Employee e = employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
        branchAccessService.assertCanAccess(e.getBranchId());
        return e;
    }

    private static ChecklistItemDto itemDto(EmployeeChecklistItem i, LocalDate today) {
        return new ChecklistItemDto(i.getId(), i.getSortOrder(), i.getTitle(), i.getResponsible(), i.getDueDate(), i.isDone(), i.getDoneAt(), i.getDoneByName(), i.getNote(),
                !i.isDone() && i.getDueDate() != null && i.getDueDate().isBefore(today));
    }

    private ChecklistDto dto(EmployeeChecklist c, Employee e, LocalDate today) {
        int done = (int) c.getItems().stream().filter(EmployeeChecklistItem::isDone).count();
        return new ChecklistDto(c.getId(), c.getEmployeeId(), e == null ? null : e.getEmployeeNumber(), e == null ? null : e.fullName(), c.getName(), c.getKind(), c.getStartDate(),
                c.getItems().size(), done, !c.getItems().isEmpty() && done == c.getItems().size(), c.getCreatedByName(),
                c.getItems().stream().map(i -> itemDto(i, today)).toList());
    }

    @Transactional(readOnly = true)
    public List<ChecklistDto> forEmployee(Long employeeId) {
        Employee e = employee(employeeId);
        LocalDate today = LocalDate.now();
        return checklistRepository.findByEmployeeIdOrderByIdDesc(employeeId).stream().map(c -> dto(c, e, today)).toList();
    }

    @Transactional
    public ChecklistDto start(Long employeeId, StartChecklistRequest r) {
        Employee e = employee(employeeId);
        if (r.templateId() == null) throw ApiException.badRequest("Choose a checklist.");
        ChecklistTemplate t = template(r.templateId());
        if (!t.isActive()) throw ApiException.badRequest("That checklist is switched off.");
        if (t.getItems().isEmpty()) throw ApiException.badRequest("That checklist has no tasks yet.");
        LocalDate start = r.startDate() != null ? r.startDate()
                : t.getKind() == ChecklistKind.OFFBOARDING && e.getTerminationDate() != null ? e.getTerminationDate() : t.getKind() == ChecklistKind.ONBOARDING ? e.getHireDate() : LocalDate.now();
        EmployeeChecklist c = new EmployeeChecklist();
        c.setEmployeeId(employeeId);
        c.setTemplateId(t.getId());
        c.setName(t.getName());
        c.setKind(t.getKind());
        c.setStartDate(start);
        c.setCreatedByName(HrSupport.currentUserName());
        int i = 0;
        for (ChecklistTemplateItem ti : t.getItems()) {
            EmployeeChecklistItem it = new EmployeeChecklistItem();
            it.setChecklist(c);
            it.setSortOrder(i++);
            it.setTitle(ti.getTitle());
            it.setResponsible(ti.getResponsible());
            it.setDueDate(start.plusDays(ti.getDueOffsetDays()));
            c.getItems().add(it);
        }
        EmployeeChecklist saved = checklistRepository.save(c);
        auditLogService.record("CHECKLIST_STARTED", "Started \"" + saved.getName() + "\" for " + e.fullName(), "EmployeeChecklist", saved.getId());
        return dto(saved, e, LocalDate.now());
    }

    private EmployeeChecklist checklist(Long id) {
        EmployeeChecklist c = checklistRepository.findById(id).orElseThrow(() -> ApiException.notFound("Checklist not found"));
        employee(c.getEmployeeId());
        return c;
    }

    @Transactional
    public void deleteChecklist(Long id) {
        EmployeeChecklist c = checklist(id);
        checklistRepository.delete(c);
        auditLogService.record("CHECKLIST_DELETED", "Removed checklist \"" + c.getName() + "\"", "EmployeeChecklist", id);
    }

    @Transactional
    public ChecklistDto addTask(Long checklistId, AddTaskRequest r) {
        EmployeeChecklist c = checklist(checklistId);
        String title = clean(r.title(), 200, "The task");
        if (title == null) throw ApiException.badRequest("Write the task.");
        if (c.getItems().size() >= MAX_ITEMS) throw ApiException.badRequest("A checklist can have at most " + MAX_ITEMS + " tasks.");
        EmployeeChecklistItem it = new EmployeeChecklistItem();
        it.setChecklist(c);
        it.setSortOrder(c.getItems().stream().mapToInt(EmployeeChecklistItem::getSortOrder).max().orElse(-1) + 1);
        it.setTitle(title);
        it.setResponsible(clean(r.responsible(), 60, "The responsible person"));
        it.setDueDate(r.dueDate());
        c.getItems().add(it);
        checklistRepository.save(c);
        return dto(c, employeeRepository.findById(c.getEmployeeId()).orElse(null), LocalDate.now());
    }

    private EmployeeChecklistItem item(Long id) {
        EmployeeChecklistItem i = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found"));
        employee(i.getChecklist().getEmployeeId());
        return i;
    }

    @Transactional
    public ChecklistDto setDone(Long itemId, DoneRequest r) {
        EmployeeChecklistItem i = item(itemId);
        boolean done = r.done() == null || r.done();
        i.setDone(done);
        i.setDoneAt(done ? Instant.now() : null);
        i.setDoneByName(done ? HrSupport.currentUserName() : null);
        if (r.note() != null) i.setNote(clean(r.note(), 300, "The note"));
        itemRepository.save(i);
        EmployeeChecklist c = i.getChecklist();
        auditLogService.record("CHECKLIST_TASK", (done ? "Ticked" : "Reopened") + " \"" + i.getTitle() + "\" of " + c.getName(), "EmployeeChecklistItem", itemId);
        return dto(c, employeeRepository.findById(c.getEmployeeId()).orElse(null), LocalDate.now());
    }

    @Transactional
    public ChecklistDto deleteTask(Long itemId) {
        EmployeeChecklistItem i = item(itemId);
        EmployeeChecklist c = i.getChecklist();
        c.getItems().remove(i);
        checklistRepository.save(c);
        return dto(c, employeeRepository.findById(c.getEmployeeId()).orElse(null), LocalDate.now());
    }

    /** Tasks not done yet across all employees (overdue first); {@code responsible} matches the text of the task's responsible person. */
    @Transactional(readOnly = true)
    public List<OpenTaskDto> openTasks(String responsible, boolean overdueOnly, ChecklistKind kind, Long employeeId) {
        LocalDate today = LocalDate.now();
        String who = responsible == null || responsible.isBlank() ? null : responsible.trim().toLowerCase(Locale.ROOT);
        List<OpenTaskDto> out = new ArrayList<>();
        for (EmployeeChecklist c : employeeId != null ? checklistRepository.findByEmployeeIdOrderByIdDesc(employeeId) : checklistRepository.findAllByOrderByIdDesc()) {
            if (kind != null && c.getKind() != kind) continue;
            Employee e = employeeRepository.findById(c.getEmployeeId()).orElse(null);
            if (e == null || !branchAccessService.canAccess(e.getBranchId())) continue;
            for (EmployeeChecklistItem i : c.getItems()) {
                if (i.isDone()) continue;
                boolean overdue = i.getDueDate() != null && i.getDueDate().isBefore(today);
                if (overdueOnly && !overdue) continue;
                if (who != null && (i.getResponsible() == null || !i.getResponsible().toLowerCase(Locale.ROOT).contains(who))) continue;
                out.add(new OpenTaskDto(i.getId(), c.getId(), e.getId(), e.getEmployeeNumber(), e.fullName(), c.getName(), c.getKind(), i.getTitle(), i.getResponsible(), i.getDueDate(), overdue, i.getNote()));
            }
        }
        out.sort(Comparator.comparing(OpenTaskDto::dueDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(OpenTaskDto::itemId));
        return out;
    }
}
