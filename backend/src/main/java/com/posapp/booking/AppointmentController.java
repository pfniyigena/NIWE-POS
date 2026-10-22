package com.posapp.booking;

import com.posapp.booking.AppointmentService.Actor;
import com.posapp.booking.dto.BookingDtos.*;
import com.posapp.booking.dto.BookingRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Booking for staff. Everyone signed in sees and works their own appointments ({@code /mine}). Seeing the whole calendar follows the
 * Operations area (view), booking and changing any appointment and the setup follow Operations (manage); the settings are admin-only.
 */
@RestController
@RequestMapping("/api/booking")
@PreAuthorize("isAuthenticated()")
public class AppointmentController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final AppointmentService service;
    private final BookingSetupService setup;

    public AppointmentController(AppointmentService service, BookingSetupService setup) {
        this.service = service;
        this.setup = setup;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean manage = auth.contains("ROLE_ADMIN") || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean view = manage || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), view, manage);
    }

    // ---- calendar and lists

    @PreAuthorize(VIEW)
    @GetMapping("/day")
    public DayDto day(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) LocalDate date) {
        return service.day(actor(me), date);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/appointments")
    public List<AppointmentDto> list(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                     @RequestParam(required = false) Long staffId, @RequestParam(required = false) Long resourceId,
                                     @RequestParam(required = false) AppointmentStatus status) {
        return service.list(actor(me), from, to, staffId, resourceId, status);
    }

    @GetMapping("/mine")
    public List<AppointmentDto> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.mine(actor(me));
    }

    @PreAuthorize(VIEW)
    @GetMapping("/summary")
    public SummaryDto summary() {
        return service.summary();
    }

    @GetMapping("/appointments/{id}")
    public AppointmentDto get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.get(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @GetMapping("/slots")
    public List<SlotDto> slots(@RequestParam Long serviceId, @RequestParam LocalDate date, @RequestParam(required = false) Long staffId,
                               @RequestParam(required = false) Long ignoreAppointmentId) {
        return service.availability(serviceId, date, staffId, ignoreAppointmentId);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/staff")
    public List<Option> staff() {
        return setup.staffOptions();
    }

    // ---- appointments

    @PreAuthorize(MANAGE)
    @PostMapping("/appointments")
    public AppointmentDto book(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody BookRequest request) {
        return service.book(actor(me), request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/appointments/{id}/reschedule")
    public AppointmentDto reschedule(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody RescheduleRequest request) {
        return service.reschedule(actor(me), id, request);
    }

    @PutMapping("/appointments/{id}/notes")
    public AppointmentDto notes(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody NotesRequest request) {
        return service.saveNotes(actor(me), id, request.notes());
    }

    @PostMapping("/appointments/{id}/complete")
    public AppointmentDto complete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.complete(actor(me), id);
    }

    @PostMapping("/appointments/{id}/no-show")
    public AppointmentDto noShow(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.noShow(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/appointments/{id}/confirm")
    public AppointmentDto confirm(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.confirmWithoutPayment(actor(me), id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/appointments/{id}/cancel")
    public AppointmentDto cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) ReasonRequest request) {
        return service.cancel(actor(me), id, request == null ? null : request.reason());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/appointments/{id}/invoice")
    public AppointmentDto invoice(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.createInvoice(actor(me), id);
    }

    // ---- setup

    @PreAuthorize(VIEW)
    @GetMapping("/services")
    public List<ServiceDto> services(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return setup.services(activeOnly);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/services/{id}")
    public ServiceDto serviceById(@PathVariable Long id) {
        return setup.service(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/services")
    public ServiceDto createService(@RequestBody ServiceRequest request) {
        return setup.saveService(null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/services/{id}")
    public ServiceDto updateService(@PathVariable Long id, @RequestBody ServiceRequest request) {
        return setup.saveService(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/services/{id}")
    public void deleteService(@PathVariable Long id) {
        setup.deleteService(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/resources")
    public List<ResourceDto> resources() {
        return setup.resources();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/resources")
    public ResourceDto createResource(@RequestBody ResourceRequest request) {
        return setup.saveResource(null, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/resources/{id}")
    public ResourceDto updateResource(@PathVariable Long id, @RequestBody ResourceRequest request) {
        return setup.saveResource(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/resources/{id}")
    public void deleteResource(@PathVariable Long id) {
        setup.deleteResource(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/blocks")
    public List<BlockDto> blocks(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return setup.blocks(from, to);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/blocks")
    public BlockDto addBlock(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody BlockRequest request) {
        return setup.addBlock(me.getUser(), request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/blocks/{id}")
    public void deleteBlock(@PathVariable Long id) {
        setup.deleteBlock(id);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/settings")
    public SettingsDto settings() {
        return setup.settings();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return setup.updateSettings(request);
    }
}
