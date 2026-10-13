package com.posapp.hotel;

import com.posapp.hotel.dto.HotelDtos.*;
import com.posapp.hotel.dto.HotelRequests.*;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything under /api/hotel. Reading needs the Hotel area (view); changing things needs manage. Exceptions, on purpose:
 * the guest picker for "Charge to room" and the settings read are open to any signed-in user (cashiers and waiters need them),
 * housekeeping status can be updated with view access, and hotel settings can only be changed by an admin.
 */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HotelController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HOTEL_MANAGE')";

    private final HotelSettingsService settingsService;
    private final ModuleService moduleService;
    private final RoomService roomService;
    private final GuestService guestService;
    private final BookingService bookingService;
    private final FrontDeskService frontDeskService;
    private final HotelFolioService folioService;
    private final MaintenanceService maintenanceService;
    private final NightAuditService nightAuditService;
    private final HotelReportService reportService;

    public HotelController(HotelSettingsService settingsService, ModuleService moduleService, RoomService roomService, GuestService guestService,
                           BookingService bookingService, FrontDeskService frontDeskService, HotelFolioService folioService,
                           MaintenanceService maintenanceService, NightAuditService nightAuditService, HotelReportService reportService) {
        this.settingsService = settingsService;
        this.moduleService = moduleService;
        this.roomService = roomService;
        this.guestService = guestService;
        this.bookingService = bookingService;
        this.frontDeskService = frontDeskService;
        this.folioService = folioService;
        this.maintenanceService = maintenanceService;
        this.nightAuditService = nightAuditService;
        this.reportService = reportService;
    }

    // ---- settings and pickers open to every signed-in user ----

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/settings")
    public HotelSettingsDto settings() {
        return settingsService.get();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public HotelSettingsDto updateSettings(@Valid @RequestBody HotelSettingsRequest request) {
        // Activating Hotel goes through Settings > Modules' rules (brings POS and Restaurant along); once active it can't be switched off.
        if (request.enabled() != null) {
            moduleService.setEnabled(ModuleKey.HOTEL, request.enabled());
        }
        return settingsService.update(new HotelSettingsRequest(null, request.postingMode(), request.taxPercent(),
                request.checkInTime(), request.checkOutTime(), request.auditTime()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/in-house")
    public List<InHouseDto> inHouseGuests() {
        return frontDeskService.inHouseGuests();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/night-audit/run")
    public AuditResult runNightAudit() {
        return nightAuditService.runNow();
    }

    // ---- dashboard and reports ----

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/reports/occupancy")
    public OccupancyReportDto occupancy(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.occupancy(from, to);
    }

    // ---- room types and rooms ----

    @GetMapping("/room-types")
    public List<RoomTypeDto> roomTypes() {
        return roomService.listTypes();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/room-types")
    public RoomTypeDto createRoomType(@Valid @RequestBody RoomTypeRequest request) {
        return roomService.createType(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/room-types/{id}")
    public RoomTypeDto updateRoomType(@PathVariable Long id, @Valid @RequestBody RoomTypeRequest request) {
        return roomService.updateType(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/room-types/{id}")
    public void deleteRoomType(@PathVariable Long id) {
        roomService.deleteType(id);
    }

    @GetMapping("/rooms")
    public List<RoomDto> rooms() {
        return roomService.listRooms();
    }

    @GetMapping("/rooms/{id}")
    public RoomDto room(@PathVariable Long id) {
        return roomService.getRoom(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/rooms")
    public RoomDto createRoom(@Valid @RequestBody RoomRequest request) {
        return roomService.createRoom(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/rooms/{id}")
    public RoomDto updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.updateRoom(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/rooms/{id}")
    public void deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
    }

    /** Cleaning status is housekeeping's job, so view access is enough. */
    @PutMapping("/rooms/{id}/housekeeping")
    public RoomDto housekeeping(@PathVariable Long id, @Valid @RequestBody HousekeepingRequest request) {
        return roomService.updateHousekeeping(id, request);
    }

    // ---- guests ----

    @GetMapping("/guests")
    public List<GuestDto> guests(@RequestParam(required = false) String q) {
        return guestService.search(q);
    }

    @GetMapping("/guests/{id}")
    public GuestDto guest(@PathVariable Long id) {
        return guestService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/guests")
    public GuestDto createGuest(@Valid @RequestBody GuestRequest request) {
        return guestService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/guests/{id}")
    public GuestDto updateGuest(@PathVariable Long id, @Valid @RequestBody GuestRequest request) {
        return guestService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/guests/{id}")
    public void deleteGuest(@PathVariable Long id) {
        guestService.delete(id);
    }

    // ---- bookings and availability ----

    @GetMapping("/bookings")
    public List<BookingDto> bookings(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "100") int limit) {
        return bookingService.list(status, q, limit);
    }

    @GetMapping("/bookings/{id}")
    public BookingDto booking(@PathVariable Long id) {
        return bookingService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings")
    public BookingDto createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/bookings/{id}")
    public BookingDto updateBooking(@PathVariable Long id, @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/extend")
    public BookingDto extend(@PathVariable Long id, @Valid @RequestBody ExtendStayRequest request) {
        return bookingService.extend(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return bookingService.cancel(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/no-show")
    public BookingDto noShow(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return bookingService.noShow(id, request);
    }

    @GetMapping("/availability")
    public AvailabilityDto availability(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.availability(from, to);
    }

    @GetMapping("/free-rooms")
    public List<RoomDto> freeRooms(@RequestParam(required = false) Long roomTypeId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long excludeBookingId) {
        return bookingService.freeRooms(roomTypeId, from, to, excludeBookingId);
    }

    @GetMapping("/grid")
    public RoomGridDto grid(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.grid(from, to);
    }

    // ---- front desk ----

    @GetMapping("/front-desk/arrivals")
    public List<BookingDto> arrivals() {
        return frontDeskService.arrivals();
    }

    @GetMapping("/front-desk/departures")
    public List<BookingDto> departures() {
        return frontDeskService.departures();
    }

    @GetMapping("/front-desk/in-house")
    public List<BookingDto> inHouse() {
        return frontDeskService.inHouse();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/check-in")
    public BookingDto checkIn(@PathVariable Long id, @RequestBody(required = false) CheckInRequest request) {
        return frontDeskService.checkIn(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/walk-in")
    public BookingDto walkIn(@Valid @RequestBody WalkInRequest request) {
        return frontDeskService.walkIn(request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/check-out")
    public FolioDto checkOut(@PathVariable Long id, @Valid @RequestBody(required = false) CheckOutRequest request) {
        return frontDeskService.checkOut(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/move")
    public BookingDto move(@PathVariable Long id, @Valid @RequestBody MoveRoomRequest request) {
        return frontDeskService.moveRoom(id, request);
    }

    // ---- guest bill ----

    @GetMapping("/bookings/{id}/folio")
    public FolioDto folio(@PathVariable Long id) {
        return folioService.folio(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/charges")
    public FolioDto addCharge(@PathVariable Long id, @Valid @RequestBody ChargeRequest request) {
        return folioService.addExtraCharge(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/charges/{chargeId}/void")
    public FolioDto voidCharge(@PathVariable Long id, @PathVariable Long chargeId, @RequestBody(required = false) VoidRequest request) {
        return folioService.voidCharge(id, chargeId, request == null ? null : request.reason());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bookings/{id}/payments")
    public FolioDto addPayment(@PathVariable Long id, @Valid @RequestBody FolioPaymentRequest request) {
        return folioService.addPayment(id, request);
    }

    // ---- maintenance ----

    @GetMapping("/maintenance")
    public List<MaintenanceDto> maintenance() {
        return maintenanceService.list();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/maintenance")
    public MaintenanceDto createMaintenance(@Valid @RequestBody MaintenanceRequestForm request) {
        return maintenanceService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/maintenance/{id}")
    public MaintenanceDto updateMaintenance(@PathVariable Long id, @RequestBody MaintenanceUpdateRequest request) {
        return maintenanceService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/maintenance/{id}")
    public void deleteMaintenance(@PathVariable Long id) {
        maintenanceService.delete(id);
    }
}
