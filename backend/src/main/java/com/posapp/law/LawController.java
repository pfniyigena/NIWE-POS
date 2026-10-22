package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDtos.*;
import com.posapp.law.dto.LawRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The law firm module. Everyone signed in can open "my work" and the matters they are on; what else they see is decided matter by matter
 * (see {@link LawAccess}). Opening matters, the client list and the settings follow the Operations area; the settings are admin-only.
 */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final LawService service;
    private final LawWorkService work;
    private final LawConflictService conflicts;

    public LawController(LawService service, LawWorkService work, LawConflictService conflicts) {
        this.service = service;
        this.work = work;
        this.conflicts = conflicts;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    // ---- options and settings

    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return service.updateSettings(request);
    }

    @GetMapping("/staff")
    public List<Option> staff() {
        return service.staff();
    }

    // ---- matters

    @PreAuthorize(VIEW)
    @GetMapping("/matters")
    public ResponseEntity<List<MatterRow>> matters(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) MatterStatus status, @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String practiceArea, @RequestParam(required = false) Long leadId, @RequestParam(required = false) Long clientId,
                                   @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return LawPaging.of(service.list(actor(me), status, q, practiceArea, leadId, clientId), page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/summary")
    public SummaryDto summary(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.summary(actor(me));
    }

    @GetMapping("/mine")
    public MineDto mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return work.mine(actor(me));
    }

    @GetMapping("/matters/{id}")
    public MatterDto matter(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.get(actor(me), id);
    }

    @PostMapping("/matters")
    public MatterDto open(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody MatterRequest request) {
        return service.create(actor(me), request);
    }

    @PutMapping("/matters/{id}")
    public MatterDto update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody MatterRequest request) {
        return service.update(actor(me), id, request);
    }

    @PostMapping("/matters/{id}/status")
    public MatterDto status(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody StatusRequest request) {
        return service.setStatus(actor(me), id, request);
    }

    @DeleteMapping("/matters/{id}")
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.delete(actor(me), id);
    }

    @PutMapping("/matters/{id}/team")
    public MatterDto team(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TeamRequest request) {
        return service.setTeam(actor(me), id, request);
    }

    @PostMapping("/matters/{id}/parties")
    public MatterDto addParty(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PartyRequest request) {
        return service.addParty(actor(me), id, request);
    }

    @DeleteMapping("/matters/{id}/parties/{partyId}")
    public MatterDto removeParty(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long partyId) {
        return service.removeParty(actor(me), id, partyId);
    }

    @PostMapping("/matters/{id}/notes")
    public MatterDto addNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody NoteRequest request) {
        return service.addNote(actor(me), id, request);
    }

    @DeleteMapping("/matters/{id}/notes/{noteId}")
    public MatterDto deleteNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long noteId) {
        return service.deleteNote(actor(me), id, noteId);
    }

    // ---- tasks and deadlines

    @PostMapping("/matters/{id}/tasks")
    public MatterDto addTask(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TaskRequest request) {
        return work.addTask(actor(me), id, request);
    }

    @PutMapping("/tasks/{taskId}")
    public MatterDto updateTask(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long taskId, @RequestBody TaskRequest request) {
        return work.updateTask(actor(me), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/status")
    public MatterDto taskStatus(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long taskId, @RequestBody TaskStatusRequest request) {
        return work.setTaskStatus(actor(me), taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}")
    public MatterDto deleteTask(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long taskId) {
        return work.deleteTask(actor(me), taskId);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskDto>> tasks(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) Long assigneeId,
                               @RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return LawPaging.of(work.tasks(actor(me), mine, assigneeId, includeDone), page, size);
    }

    @PostMapping("/matters/{id}/deadlines")
    public MatterDto addDeadline(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DeadlineRequest request) {
        return work.addDeadline(actor(me), id, request);
    }

    @PutMapping("/deadlines/{deadlineId}")
    public MatterDto updateDeadline(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long deadlineId, @RequestBody DeadlineRequest request) {
        return work.updateDeadline(actor(me), deadlineId, request);
    }

    @PostMapping("/deadlines/{deadlineId}/done")
    public MatterDto deadlineDone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long deadlineId) {
        return work.setDeadlineStatus(actor(me), deadlineId, DeadlineStatus.DONE);
    }

    @PostMapping("/deadlines/{deadlineId}/reopen")
    public MatterDto deadlineReopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long deadlineId) {
        return work.setDeadlineStatus(actor(me), deadlineId, DeadlineStatus.OPEN);
    }

    @PostMapping("/deadlines/{deadlineId}/cancel")
    public MatterDto deadlineCancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long deadlineId) {
        return work.setDeadlineStatus(actor(me), deadlineId, DeadlineStatus.CANCELLED);
    }

    @DeleteMapping("/deadlines/{deadlineId}")
    public MatterDto deleteDeadline(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long deadlineId) {
        return work.deleteDeadline(actor(me), deadlineId);
    }

    @GetMapping("/deadlines")
    public ResponseEntity<List<DeadlineDto>> deadlines(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean mine, @RequestParam(required = false) Long assigneeId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "false") boolean includeDone, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return LawPaging.of(work.deadlines(actor(me), mine, assigneeId, from, to, includeDone), page, size);
    }

    // ---- conflict check

    @PostMapping("/conflicts/search")
    public ConflictResult search(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ConflictSearchRequest request) {
        return conflicts.search(actor(me), request);
    }

    @PostMapping("/matters/{id}/conflicts/check")
    public ConflictResult check(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return conflicts.check(actor(me), id);
    }

    @PostMapping("/matters/{id}/conflicts/clear")
    public MatterDto clear(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ClearRequest request) {
        conflicts.clear(actor(me), id, request);
        return service.get(actor(me), id);
    }

    @GetMapping("/conflicts/log")
    public List<ConflictLogDto> conflictLog(@AuthenticationPrincipal AppUserPrincipal me) {
        return conflicts.log(actor(me));
    }

    // ---- contacts and clients

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactDto>> contacts(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return LawPaging.of(service.contacts(actor(me), q), page, size);
    }

    @PostMapping("/contacts")
    public ContactDto createContact(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ContactRequest request) {
        return service.createContact(actor(me), request);
    }

    @PutMapping("/contacts/{id}")
    public ContactDto updateContact(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ContactRequest request) {
        return service.updateContact(actor(me), id, request);
    }

    @DeleteMapping("/contacts/{id}")
    public void deleteContact(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        service.deleteContact(actor(me), id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/clients")
    public ResponseEntity<List<ClientRow>> clients(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return LawPaging.of(service.clients(actor(me)), page, size);
    }
}
