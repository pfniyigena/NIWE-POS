package com.posapp.onlinestore;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineSlotService.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Delivery windows: the staff screens (Shop settings) and what the customer sees at checkout. */
@RestController
public class OnlineSlotController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineSlotService service;
    private final ModuleService moduleService;

    public OnlineSlotController(OnlineSlotService service, ModuleService moduleService) {
        this.service = service;
        this.moduleService = moduleService;
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/slots")
    public List<SlotDto> list() {
        return service.list();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/slots")
    public SlotDto create(@Valid @RequestBody SlotRequest request) {
        return service.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/slots/{id}")
    public SlotDto update(@PathVariable Long id, @Valid @RequestBody SlotRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/slots/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/slot-settings")
    public SlotSettings settings() {
        return service.slotSettings();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/slot-settings")
    public SlotSettings saveSettings(@RequestBody SlotSettings request) {
        return service.saveSettings(request);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/closed-days")
    public List<ClosedDayDto> closedDays() {
        return service.closedDays();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/closed-days")
    public ClosedDayDto addClosed(@Valid @RequestBody ClosedDayRequest request) {
        return service.addClosed(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/closed-days/{id}")
    public ResponseEntity<Void> removeClosed(@PathVariable Long id) {
        service.removeClosed(id);
        return ResponseEntity.noContent().build();
    }

    /** What the customer can choose at checkout. */
    @GetMapping("/api/public/store/delivery-slots")
    public Board board() {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return service.board();
    }
}
