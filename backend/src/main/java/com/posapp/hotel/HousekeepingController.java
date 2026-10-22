package com.posapp.hotel;

import com.posapp.common.ExportFiles;
import com.posapp.common.ListPaging;
import com.posapp.hotel.dto.HkDtos.*;
import com.posapp.hotel.dto.HkRequests.*;
import com.posapp.reports.ReportExportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Housekeeping v2, lost and found, minibar and linen. Working a task (start, finish, inspect, do-not-disturb), logging an item found, using the
 * minibar and logging linen need Hotel view access, like the rooms grid; planning (preparing the board, assigning, setup) and closing items need manage.
 */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HousekeepingController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final HousekeepingService hk;
    private final LostFoundService lostFound;
    private final SuppliesService supplies;
    private final ReportExportService exportService;

    public HousekeepingController(HousekeepingService hk, LostFoundService lostFound, SuppliesService supplies, ReportExportService exportService) {
        this.hk = hk;
        this.lostFound = lostFound;
        this.supplies = supplies;
        this.exportService = exportService;
    }

    // ---- board

    @GetMapping("/housekeeping/board")
    public HkBoardDto board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return hk.board(date);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/housekeeping/board/generate")
    public HkBoardDto generate(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return hk.generate(date);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/housekeeping/board/assign")
    public HkBoardDto autoAssign(@RequestBody(required = false) AutoAssignRequest request) {
        return hk.autoAssign(request == null ? null : request.date(), request == null ? null : request.staffIds());
    }

    @GetMapping("/housekeeping/board/export")
    public ResponseEntity<byte[]> exportBoard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        HkBoardDto board = hk.board(date);
        List<List<String>> body = new ArrayList<>();
        for (HkTaskDto t : board.tasks()) {
            body.add(List.of(HotelExportController.s(t.roomNumber()), HotelExportController.s(t.floor()), HotelExportController.s(t.roomTypeName()),
                    t.kind().equals("DEPARTURE") ? "Departure" : "Stay-over", t.status().replace('_', ' ').toLowerCase(), HotelExportController.s(t.staffName()),
                    HotelExportController.s(t.guestName()), t.arrivalToday() ? "Arrival today" : "", t.dnd() ? "Do not disturb" : "",
                    HotelExportController.s(t.minutes()), HotelExportController.s(t.inspectionResult())));
        }
        return ExportFiles.file(exportService, "Housekeeping " + board.date(), "housekeeping-" + board.date(),
                List.of("Room", "Floor", "Type", "Task", "Status", "Housekeeper", "Guest", "Arrival", "DND", "Minutes", "Inspection"), body, format);
    }

    // ---- working a task

    @PreAuthorize(MANAGE)
    @PutMapping("/housekeeping/tasks/{id}/assign")
    public HkTaskDto assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return hk.assign(id, request.staffId());
    }

    @PostMapping("/housekeeping/tasks/{id}/start")
    public HkTaskDto start(@PathVariable Long id, @RequestBody(required = false) StartRequest request) {
        return hk.start(id, request == null ? null : request.staffId());
    }

    @PostMapping("/housekeeping/tasks/{id}/finish")
    public HkTaskDto finish(@PathVariable Long id) {
        return hk.finish(id);
    }

    @PostMapping("/housekeeping/tasks/{id}/skip")
    public HkTaskDto skip(@PathVariable Long id, @RequestBody(required = false) SkipRequest request) {
        return hk.skip(id, request == null ? null : request.reason());
    }

    @PostMapping("/housekeeping/tasks/{id}/reopen")
    public HkTaskDto reopen(@PathVariable Long id) {
        return hk.reopen(id);
    }

    @PostMapping("/housekeeping/tasks/{id}/inspect")
    public HkTaskDto inspect(@PathVariable Long id, @RequestBody InspectRequest request) {
        return hk.inspect(id, request);
    }

    @PutMapping("/housekeeping/rooms/{roomId}/dnd")
    public HkBoardDto dnd(@PathVariable Long roomId, @Valid @RequestBody DndRequest request) {
        return hk.setDnd(roomId, request.dnd());
    }

    @GetMapping("/housekeeping/productivity")
    public List<ProductivityDto> productivity(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return hk.productivity(from, to);
    }

    // ---- setup

    @GetMapping("/housekeeping/staff")
    public List<StaffDto> staff() {
        return hk.staff();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/housekeeping/staff")
    public StaffDto addStaff(@Valid @RequestBody StaffRequest request) {
        return hk.addStaff(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/housekeeping/staff/{id}")
    public StaffDto updateStaff(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return hk.updateStaff(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/housekeeping/staff/{id}")
    public void deleteStaff(@PathVariable Long id) {
        hk.deleteStaff(id);
    }

    @GetMapping("/housekeeping/checklist")
    public List<ChecklistItemDto> checklist(@RequestParam(defaultValue = "false") boolean all) {
        return hk.checklist(!all);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/housekeeping/checklist")
    public List<ChecklistItemDto> saveChecklist(@Valid @RequestBody ChecklistRequest request) {
        return hk.saveChecklist(request);
    }

    // ---- lost and found

    @GetMapping("/lost-found")
    public ResponseEntity<List<LostFoundDto>> lostFoundList(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(lostFound.list(q, status, from, to), page, size);
    }

    @GetMapping("/lost-found/export")
    public ResponseEntity<byte[]> lostFoundExport(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (LostFoundDto i : lostFound.list(q, status, from, to)) {
            body.add(List.of(HotelExportController.s(i.itemNo()), HotelExportController.s(i.description()), HotelExportController.s(i.foundOn()),
                    HotelExportController.s(i.roomNumber()), HotelExportController.s(i.location()), HotelExportController.s(i.foundBy()),
                    HotelExportController.s(i.guestName()), i.status().substring(0, 1) + i.status().substring(1).toLowerCase(),
                    HotelExportController.s(i.claimedBy()), HotelExportController.s(i.claimedOn())));
        }
        return ExportFiles.file(exportService, HotelExportController.title("Lost and found", status == null || status.isBlank() ? null : "status " + status.toLowerCase(),
                HotelExportController.search(q)), "lost-and-found",
                List.of("Item", "Description", "Found", "Room", "Place", "Found by", "Guest", "Status", "Collected by", "Closed on"), body, format);
    }

    @GetMapping("/lost-found/{id}")
    public LostFoundDto lostFoundGet(@PathVariable Long id) {
        return lostFound.get(id);
    }

    @PostMapping("/lost-found")
    public LostFoundDto lostFoundCreate(@Valid @RequestBody LostFoundRequest request) {
        return lostFound.create(request);
    }

    @PutMapping("/lost-found/{id}")
    public LostFoundDto lostFoundUpdate(@PathVariable Long id, @Valid @RequestBody LostFoundRequest request) {
        return lostFound.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/lost-found/{id}/claim")
    public LostFoundDto lostFoundClaim(@PathVariable Long id, @RequestBody(required = false) ClaimRequest request) {
        return lostFound.claim(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/lost-found/{id}/dispose")
    public LostFoundDto lostFoundDispose(@PathVariable Long id, @RequestBody(required = false) DisposeRequest request) {
        return lostFound.dispose(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/lost-found/{id}")
    public void lostFoundDelete(@PathVariable Long id) {
        lostFound.delete(id);
    }

    // ---- minibar and linen

    @GetMapping("/minibar/items")
    public List<MinibarItemDto> minibarItems(@RequestParam(defaultValue = "false") boolean all) {
        return supplies.minibarItems(!all);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/minibar/items")
    public MinibarItemDto addMinibarItem(@Valid @RequestBody MinibarItemRequest request) {
        return supplies.addItem(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/minibar/items/{id}")
    public MinibarItemDto updateMinibarItem(@PathVariable Long id, @Valid @RequestBody MinibarItemRequest request) {
        return supplies.updateItem(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/minibar/items/{id}")
    public void deleteMinibarItem(@PathVariable Long id) {
        supplies.deleteItem(id);
    }

    @PostMapping("/minibar/use")
    public MinibarPostedDto useMinibar(@Valid @RequestBody MinibarUseRequest request) {
        return supplies.useMinibar(request);
    }

    @GetMapping("/linen")
    public List<LinenLogDto> linen(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return supplies.linenLog(from, to);
    }

    @GetMapping("/linen/balance")
    public List<LinenBalanceDto> linenBalance() {
        return supplies.linenBalance();
    }

    @PostMapping("/linen")
    public LinenLogDto addLinen(@Valid @RequestBody LinenRequest request) {
        return supplies.addLinen(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/linen/{id}")
    public void deleteLinen(@PathVariable Long id) {
        supplies.deleteLinen(id);
    }
}
