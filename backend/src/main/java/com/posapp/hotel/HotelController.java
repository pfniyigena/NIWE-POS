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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import com.posapp.common.ListPaging;

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
    private final RateService rateService;

    public HotelController(HotelSettingsService settingsService, ModuleService moduleService, RoomService roomService, GuestService guestService,
                           BookingService bookingService, FrontDeskService frontDeskService, HotelFolioService folioService,
                           MaintenanceService maintenanceService, NightAuditService nightAuditService, HotelReportService reportService,
                           RateService rateService) {
        this.rateService = rateService;
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
                request.checkInTime(), request.checkOutTime(), request.auditTime(), request.cancelFreeDays(), request.cancelFeeNights(),
                request.noShowFeeNights(), request.earlyDepartureFeeNights(), request.lateCheckoutFrom(), request.lateCheckoutPercent(),
                request.taxOnFees()));
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

    /** Rooms. Filters and paging are optional; with none, every room (the pickers rely on that). status: OCCUPIED, VACANT, OUT_OF_ORDER, INACTIVE or a housekeeping status. */
    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDto>> rooms(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) String floor,
                                               @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<RoomDto> rows = roomService.listRooms().stream()
                .filter(r -> ListPaging.has(q, r.number(), r.floor(), r.roomTypeName(), r.occupantGuest(), r.housekeeper()))
                .filter(r -> ListPaging.sameId(roomTypeId, r.roomTypeId()))
                .filter(r -> floor == null || floor.isBlank() || floor.trim().equalsIgnoreCase(r.floor()))
                .filter(r -> roomMatches(r, status)).toList();
        return ListPaging.of(rows, page, size);
    }

    private static boolean roomMatches(RoomDto r, String status) {
        if (status == null || status.isBlank()) return true;
        return switch (status.trim().toUpperCase()) {
            case "OCCUPIED" -> r.occupied();
            case "VACANT" -> r.active() && !r.occupied() && !r.outOfOrder();
            case "OUT_OF_ORDER" -> r.outOfOrder();
            case "INACTIVE" -> !r.active();
            default -> r.housekeepingStatus() != null && r.housekeepingStatus().equalsIgnoreCase(status.trim());
        };
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
    public ResponseEntity<List<GuestDto>> guests(@RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        if (size == null) {
            return ResponseEntity.ok(guestService.search(q));
        }
        Page<GuestDto> result = guestService.page(q, page == null ? 0 : page, Math.min(size, ListPaging.MAX));
        return ResponseEntity.ok().header(ListPaging.TOTAL, String.valueOf(result.getTotalElements())).body(result.getContent());
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

    // ---- rates (HO-2) ----

    @GetMapping("/rate-plans")
    public List<RatePlanDto> ratePlans() {
        return rateService.listPlans();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/rate-plans")
    public RatePlanDto createRatePlan(@Valid @RequestBody RatePlanRequest request) {
        return rateService.createPlan(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/rate-plans/{id}")
    public RatePlanDto updateRatePlan(@PathVariable Long id, @Valid @RequestBody RatePlanRequest request) {
        return rateService.updatePlan(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/rate-plans/{id}")
    public void deleteRatePlan(@PathVariable Long id) {
        rateService.deletePlan(id);
    }

    @GetMapping("/rate-rules")
    public List<RateRuleDto> rateRules() {
        return rateService.listRules();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/rate-rules")
    public RateRuleDto createRateRule(@Valid @RequestBody RateRuleRequest request) {
        return rateService.createRule(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/rate-rules/{id}")
    public RateRuleDto updateRateRule(@PathVariable Long id, @Valid @RequestBody RateRuleRequest request) {
        return rateService.updateRule(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/rate-rules/{id}")
    public void deleteRateRule(@PathVariable Long id) {
        rateService.deleteRule(id);
    }

    /** What a stay would cost, night by night — the figures a booking made now would get. */
    @GetMapping("/quote")
    public QuoteDto quote(@RequestParam Long roomTypeId, @RequestParam(required = false) Long roomId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long ratePlanId, @RequestParam(required = false) Integer adults,
                          @RequestParam(required = false) Integer children) {
        return rateService.quote(roomTypeId, roomId, from, to, ratePlanId, adults, children);
    }

    /** The nightly price of one room type for every day in the range, and how many rooms of it are still free that night. */
    @GetMapping("/rate-calendar")
    public RateCalendarDto rateCalendar(@RequestParam Long roomTypeId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long ratePlanId) {
        RoomType type = rateService.type(roomTypeId);
        RatePlan plan = rateService.planOrNull(ratePlanId);
        List<RateService.NightPrice> prices = rateService.calendarPrices(type, from, to, plan);
        Map<LocalDate, Integer> free = new HashMap<>();
        for (TypeAvailability t : bookingService.availability(from, to.plusDays(1)).types()) {
            if (t.roomTypeId().equals(roomTypeId)) t.days().forEach(day -> free.put(day.date(), day.available()));
        }
        List<RateCalendarDay> days = prices.stream().map(p -> new RateCalendarDay(p.date(), p.rate(), p.ruleName(), free.getOrDefault(p.date(), 0))).toList();
        return new RateCalendarDto(type.getId(), type.getName(), type.getBaseRate(), plan == null ? null : plan.getId(), plan == null ? null : plan.getName(), days);
    }

    // ---- bookings and availability ----

    /**
     * Bookings. With {@code size} this is one page (X-Total-Count tells how many matched); without it the old behaviour: the newest
     * {@code limit} bookings. {@code from}/{@code to} are arrival dates.
     */
    @GetMapping("/bookings")
    public ResponseEntity<List<BookingDto>> bookings(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "100") int limit,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) String source,
                                                     @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (size == null && from == null && to == null && roomTypeId == null && source == null) {
            return ResponseEntity.ok(bookingService.list(status, q, limit));
        }
        Page<BookingDto> result = bookingService.page(status, q, from, to, roomTypeId, source, page == null ? 0 : page, size == null ? 0 : Math.min(size, ListPaging.MAX));
        return ResponseEntity.ok().header(ListPaging.TOTAL, String.valueOf(result.getTotalElements())).body(result.getContent());
    }

    @GetMapping("/bookings/{id}")
    public BookingDto booking(@PathVariable Long id) {
        return bookingService.get(id);
    }

    /** The price of each planned night of the booking, as sold. */
    @GetMapping("/bookings/{id}/nights")
    public List<QuoteNightDto> bookingNights(@PathVariable Long id) {
        return folioService.nights(id);
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
    public ResponseEntity<List<MaintenanceDto>> maintenance(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String priority, @RequestParam(required = false) Long roomId,
                                                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        List<MaintenanceDto> rows = maintenanceService.list().stream()
                .filter(m -> ListPaging.has(q, m.roomNumber(), m.title(), m.description(), m.reportedBy()))
                .filter(m -> ListPaging.is(status, m.status()))
                .filter(m -> ListPaging.is(priority, m.priority()))
                .filter(m -> ListPaging.sameId(roomId, m.roomId())).toList();
        return ListPaging.of(rows, page, size);
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
