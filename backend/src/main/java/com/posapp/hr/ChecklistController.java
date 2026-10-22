package com.posapp.hr;

import com.posapp.hr.dto.HrDocDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Onboarding and offboarding checklists. Under /api/hr/employees so the Employees license gate applies. */
@RestController
@RequestMapping("/api/hr/employees")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
public class ChecklistController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HR_MANAGE')";

    private final ChecklistService service;

    public ChecklistController(ChecklistService service) {
        this.service = service;
    }

    @GetMapping("/checklist-templates")
    public List<TemplateDto> templates() {
        return service.templates();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/checklist-templates")
    public TemplateDto createTemplate(@RequestBody TemplateRequest request) {
        return service.createTemplate(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/checklist-templates/{id}")
    public TemplateDto updateTemplate(@PathVariable Long id, @RequestBody TemplateRequest request) {
        return service.updateTemplate(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/checklist-templates/{id}")
    public void deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
    }

    /** Tasks not done yet, across all employees. */
    @GetMapping("/checklists/open")
    public List<OpenTaskDto> open(@RequestParam(required = false) String responsible, @RequestParam(defaultValue = "false") boolean overdueOnly,
                                  @RequestParam(required = false) ChecklistKind kind, @RequestParam(required = false) Long employeeId) {
        return service.openTasks(responsible, overdueOnly, kind, employeeId);
    }

    @GetMapping("/{id}/checklists")
    public List<ChecklistDto> forEmployee(@PathVariable Long id) {
        return service.forEmployee(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/checklists")
    public ChecklistDto start(@PathVariable Long id, @RequestBody StartChecklistRequest request) {
        return service.start(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/checklists/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteChecklist(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/checklists/{id}/tasks")
    public ChecklistDto addTask(@PathVariable Long id, @RequestBody AddTaskRequest request) {
        return service.addTask(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/checklist-tasks/{taskId}/done")
    public ChecklistDto done(@PathVariable Long taskId, @RequestBody(required = false) DoneRequest request) {
        return service.setDone(taskId, request == null ? new DoneRequest(true, null) : request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/checklist-tasks/{taskId}")
    public ChecklistDto deleteTask(@PathVariable Long taskId) {
        return service.deleteTask(taskId);
    }
}
