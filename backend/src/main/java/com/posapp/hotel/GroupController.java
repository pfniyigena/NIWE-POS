package com.posapp.hotel;

import com.posapp.common.ExportFiles;
import com.posapp.common.ListPaging;
import com.posapp.hotel.dto.GroupDtos.GroupDetailDto;
import com.posapp.hotel.dto.GroupDtos.GroupSummaryDto;
import com.posapp.hotel.dto.GroupDtos.NamesResultDto;
import com.posapp.hotel.dto.GroupDtos.RoomingDto;
import com.posapp.hotel.dto.GroupRequests.AddRoomsRequest;
import com.posapp.hotel.dto.GroupRequests.BlockRequest;
import com.posapp.hotel.dto.GroupRequests.GroupCancelRequest;
import com.posapp.hotel.dto.GroupRequests.GroupMoneyRequest;
import com.posapp.hotel.dto.GroupRequests.GroupRequest;
import com.posapp.hotel.dto.GroupRequests.NamesRequest;
import com.posapp.hotel.dto.GroupRequests.RoomingUpdateRequest;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** Group bookings: the groups, their room blocks, the rooming list and the master account. Reading needs the Hotel area; changing needs manage. */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class GroupController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final GroupService groupService;
    private final HotelPdf pdf;
    private final StoreSettingsService storeSettingsService;
    private final ReportExportService exportService;

    public GroupController(GroupService groupService, HotelPdf pdf, StoreSettingsService storeSettingsService, ReportExportService exportService) {
        this.groupService = groupService;
        this.pdf = pdf;
        this.storeSettingsService = storeSettingsService;
        this.exportService = exportService;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<GroupSummaryDto>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(groupService.list(q, status), page, size);
    }

    @GetMapping("/groups/export")
    public ResponseEntity<byte[]> exportGroups(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (GroupSummaryDto g : groupService.list(q, status)) {
            body.add(List.of(HotelExportController.s(g.groupCode()), HotelExportController.s(g.name()), HotelExportController.s(g.contactName()),
                    HotelExportController.s(g.arrivalDate()), HotelExportController.s(g.departureDate()), HotelExportController.s(g.status()),
                    HotelExportController.s(g.billing()), String.valueOf(g.roomsBlocked()), String.valueOf(g.roomsBooked()),
                    HotelExportController.num(g.owed()), HotelExportController.num(g.held())));
        }
        return ExportFiles.file(exportService, HotelExportController.title("Groups", status == null || status.isBlank() ? null : "status " + status.toLowerCase(),
                HotelExportController.search(q)), "groups",
                List.of("Group", "Name", "Contact", "Arrival", "Departure", "Status", "Billing", "Rooms held", "Rooms booked", "Owed", "Deposit held"), body, format);
    }

    @GetMapping("/groups/{id}")
    public GroupDetailDto get(@PathVariable Long id) {
        return groupService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups")
    public GroupDetailDto create(@Valid @RequestBody GroupRequest request) {
        return groupService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/groups/{id}")
    public GroupDetailDto update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return groupService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/confirm")
    public GroupDetailDto confirm(@PathVariable Long id) {
        return groupService.confirm(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/cancel")
    public GroupDetailDto cancel(@PathVariable Long id, @RequestBody(required = false) GroupCancelRequest request) {
        return groupService.cancel(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/complete")
    public GroupDetailDto complete(@PathVariable Long id) {
        return groupService.complete(id);
    }

    // ---- blocks

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/blocks")
    public GroupDetailDto addBlock(@PathVariable Long id, @Valid @RequestBody BlockRequest request) {
        return groupService.addBlock(id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/groups/{id}/blocks/{blockId}")
    public GroupDetailDto updateBlock(@PathVariable Long id, @PathVariable Long blockId, @Valid @RequestBody BlockRequest request) {
        return groupService.updateBlock(id, blockId, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/groups/{id}/blocks/{blockId}")
    public GroupDetailDto deleteBlock(@PathVariable Long id, @PathVariable Long blockId) {
        return groupService.deleteBlock(id, blockId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/blocks/{blockId}/release")
    public GroupDetailDto releaseBlock(@PathVariable Long id, @PathVariable Long blockId) {
        return groupService.releaseBlock(id, blockId);
    }

    // ---- rooming list

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/rooms")
    public GroupDetailDto addRooms(@PathVariable Long id, @Valid @RequestBody AddRoomsRequest request) {
        return groupService.addRooms(id, request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/groups/{id}/rooming/{bookingId}")
    public GroupDetailDto updateRoom(@PathVariable Long id, @PathVariable Long bookingId, @Valid @RequestBody RoomingUpdateRequest request) {
        return groupService.updateRoom(id, bookingId, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/rooming/names")
    public NamesResultDto assignNames(@PathVariable Long id, @RequestBody NamesRequest request) {
        return groupService.assignNames(id, request);
    }

    @GetMapping("/groups/{id}/rooming/export")
    public ResponseEntity<byte[]> exportRooming(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format) {
        GroupDetailDto g = groupService.get(id);
        List<List<String>> body = new ArrayList<>();
        for (RoomingDto r : g.rooming()) {
            if ("CANCELLED".equals(r.status()) || "NO_SHOW".equals(r.status())) continue;
            body.add(List.of(HotelExportController.s(r.bookingNumber()), r.unnamed() ? "(to be named)" : HotelExportController.s(r.guestName()),
                    HotelExportController.s(r.guestPhone()), HotelExportController.s(r.roomTypeName()), HotelExportController.s(r.roomNumber()),
                    HotelExportController.s(r.checkInDate()), HotelExportController.s(r.checkOutDate()), String.valueOf(r.adults()),
                    String.valueOf(r.children()), HotelExportController.s(r.status()), HotelExportController.s(r.billTo())));
        }
        return ExportFiles.file(exportService, "Rooming list " + g.groupCode() + " " + g.name(), "rooming-" + g.groupCode(),
                List.of("Booking", "Guest", "Phone", "Room type", "Room", "Arrival", "Departure", "Adults", "Children", "Status", "Bill to"), body, format);
    }

    // ---- master account

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/deposits")
    public GroupDetailDto deposit(@PathVariable Long id, @Valid @RequestBody GroupMoneyRequest request) {
        return groupService.deposit(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/payments")
    public GroupDetailDto payment(@PathVariable Long id, @Valid @RequestBody GroupMoneyRequest request) {
        return groupService.payment(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/groups/{id}/refunds")
    public GroupDetailDto refund(@PathVariable Long id, @Valid @RequestBody GroupMoneyRequest request) {
        return groupService.refund(id, request);
    }

    @GetMapping("/groups/{id}/statement/pdf")
    public ResponseEntity<byte[]> statement(@PathVariable Long id) {
        GroupDetailDto g = groupService.get(id);
        byte[] bytes = pdf.groupStatement(g, storeSettingsService.getSettingsEntity());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename("statement-" + g.groupCode() + ".pdf").build().toString())
                .body(bytes);
    }
}
