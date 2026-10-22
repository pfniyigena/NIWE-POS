package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.AvailabilityDto;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelDtos.DayAvailability;
import com.posapp.hotel.dto.HotelDtos.GridBooking;
import com.posapp.hotel.dto.HotelDtos.GridRoom;
import com.posapp.hotel.dto.HotelDtos.RoomDto;
import com.posapp.hotel.dto.HotelDtos.RoomGridDto;
import com.posapp.hotel.dto.HotelDtos.TypeAvailability;
import com.posapp.hotel.dto.HotelRequests.BookingRequest;
import com.posapp.hotel.dto.HotelRequests.BookingUpdateRequest;
import com.posapp.hotel.dto.HotelRequests.CancelRequest;
import com.posapp.hotel.dto.HotelRequests.ExtendStayRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Reservations: creating and changing bookings, availability, and the room grid. */
@Service
public class BookingService {

    private static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
    private static final int MAX_RANGE_DAYS = 92;

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final GuestService guestService;
    private final RoomService roomService;
    private final HotelFolioService folioService;
    private final HotelClock clock;
    private final AuditLogService auditLogService;
    private final RateService rateService;
    private final BookingNightRepository nightRepository;
    private final GroupBlockRepository blockRepository;

    public BookingService(BookingRepository bookingRepository, RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                          GuestService guestService, RoomService roomService, HotelFolioService folioService, HotelClock clock,
                          AuditLogService auditLogService, RateService rateService, BookingNightRepository nightRepository,
                          GroupBlockRepository blockRepository) {
        this.blockRepository = blockRepository;
        this.rateService = rateService;
        this.nightRepository = nightRepository;
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.guestService = guestService;
        this.roomService = roomService;
        this.folioService = folioService;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- list / read

    @Transactional(readOnly = true)
    public List<BookingDto> list(String status, String query, int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 300));
        List<Booking> rows;
        if (query != null && !query.isBlank()) {
            rows = bookingRepository.search(query.trim(), PageRequest.of(0, size));
            if (status != null && !status.isBlank()) {
                BookingStatus wanted = parseStatus(status);
                rows = rows.stream().filter(b -> b.getStatus() == wanted).toList();
            }
        } else if (status != null && !status.isBlank()) {
            rows = bookingRepository.findByStatusOrderByCheckInDateDescIdDesc(parseStatus(status), PageRequest.of(0, size));
        } else {
            rows = bookingRepository.findAllByOrderByCheckInDateDescIdDesc(PageRequest.of(0, size));
        }
        return rows.stream().map(folioService::toDto).toList();
    }

    /**
     * One page of bookings for the Bookings screen and its export: filtered by status, arrival date range, room type, source and a search
     * over booking number, guest, phone and room; newest arrival first. {@code size} 0 or less returns everything that matches.
     */
    @Transactional(readOnly = true)
    public Page<BookingDto> page(String status, String query, LocalDate from, LocalDate to, Long roomTypeId, String source, int page, int size) {
        BookingStatus wanted = status == null || status.isBlank() ? null : parseStatus(status);
        String needle = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String src = source == null || source.isBlank() ? null : source.trim().toLowerCase();
        Specification<Booking> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (wanted != null) ps.add(cb.equal(root.get("status"), wanted));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("checkInDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.<LocalDate>get("checkInDate"), to));
            if (roomTypeId != null) ps.add(cb.equal(root.get("roomType").get("id"), roomTypeId));
            if (src != null) ps.add(cb.equal(cb.lower(root.<String>get("source")), src));
            if (needle != null) {
                Join<Booking, Guest> guest = root.join("guest", JoinType.INNER);
                Join<Booking, Room> room = root.join("room", JoinType.LEFT);
                ps.add(cb.or(cb.like(cb.lower(root.<String>get("bookingNumber")), needle),
                        cb.like(cb.lower(guest.<String>get("fullName")), needle),
                        cb.like(cb.lower(cb.coalesce(guest.<String>get("phone"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(room.<String>get("number"), "")), needle)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.desc("checkInDate"), Sort.Order.desc("id"));
        if (size <= 0) {
            List<BookingDto> all = bookingRepository.findAll(spec, sort).stream().map(folioService::toDto).toList();
            return new org.springframework.data.domain.PageImpl<>(all);
        }
        return bookingRepository.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(size, 500), sort)).map(folioService::toDto);
    }

    @Transactional(readOnly = true)
    public BookingDto get(Long id) {
        return folioService.toDto(folioService.getBooking(id));
    }

    // ---------------------------------------------------------------- create / change

    @Transactional
    public BookingDto create(BookingRequest request) {
        return create(request, null);
    }

    /** Creates a booking; a booking of a group draws on the group's room block first. */
    @Transactional
    public BookingDto create(BookingRequest request, HotelGroup group) {
        LocalDate today = clock.today();
        LocalDate from = request.checkInDate();
        LocalDate to = request.checkOutDate();
        validateDates(from, to, today, true);
        RoomType type = activeType(request.roomTypeId());
        int adults = request.adults() != null ? request.adults() : 1;
        int children = request.children() != null ? request.children() : 0;
        validateGuests(type, adults, children);
        Room room = null;
        if (request.roomId() != null) {
            room = roomService.getOrThrow(request.roomId());
            validateRoomForType(room, type);
            ensureRoomFree(room, from, to, null);
        }
        ensureAvailable(type, from, to, null, group != null ? group.getId() : null);

        Booking booking = new Booking();
        booking.setGroup(group);
        booking.setGuest(guestService.resolve(request.guestId(), request.guest()));
        booking.setRoomType(type);
        booking.setRoom(room);
        booking.setCheckInDate(from);
        booking.setCheckOutDate(to);
        booking.setAdults(adults);
        booking.setChildren(children);
        booking.setBaseOccupancy(type.getBaseOccupancy());
        booking.setExtraPersonRate(type.getExtraPersonRate());
        RatePlan plan = sellablePlan(request.ratePlanId(), from, to);
        booking.setRatePlan(plan);
        boolean agreed = request.nightlyRate() != null;
        booking.setRateMode(agreed ? RateMode.FIXED : RateMode.AUTO);
        booking.setNightlyRate(agreed ? checkedRate(request.nightlyRate()) : room != null ? room.effectiveRate() : type.getBaseRate());
        booking.setSource(blank(request.source()));
        booking.setNotes(blank(request.notes()));
        booking.setStatus(BookingStatus.BOOKED);
        Booking saved = bookingRepository.save(booking);
        saved.setBookingNumber(String.format("BK-%05d", saved.getId()));
        bookingRepository.save(saved);
        if (!agreed) repriceNights(saved);

        folioService.syncDeferral(saved);
        if (request.depositAmount() != null && request.depositAmount().signum() > 0) {
            folioService.recordPayment(saved, PaymentKind.DEPOSIT, request.depositMethod(), request.depositAmount(), null, "Booking deposit");
        }
        auditLogService.record("BOOKING_CREATED", saved.getBookingNumber() + " for " + saved.getGuest().getFullName() + " " + from + " to " + to,
                "Booking", saved.getId());
        return folioService.toDto(saved);
    }

    @Transactional
    public BookingDto update(Long id, BookingUpdateRequest request) {
        Booking booking = folioService.getBooking(id);
        if (booking.getStatus() != BookingStatus.BOOKED && booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.badRequest("A " + booking.getStatus().name().toLowerCase().replace('_', ' ') + " booking can no longer be changed");
        }
        boolean inHouse = booking.getStatus() == BookingStatus.CHECKED_IN;
        LocalDate today = clock.today();
        LocalDate from = booking.getCheckInDate();
        LocalDate to = booking.getCheckOutDate();
        RoomType type = booking.getRoomType();
        Room room = booking.getRoom();

        if (!inHouse) {
            if (request.checkInDate() != null) from = request.checkInDate();
            if (request.roomTypeId() != null && !request.roomTypeId().equals(type.getId())) {
                type = activeType(request.roomTypeId());
                if (request.roomId() == null) room = null;
            }
            if (request.roomId() != null) {
                room = roomService.getOrThrow(request.roomId());
            }
        } else if ((request.checkInDate() != null && !request.checkInDate().equals(from))
                || (request.roomTypeId() != null && !request.roomTypeId().equals(type.getId()))
                || (request.roomId() != null && !request.roomId().equals(room != null ? room.getId() : null))) {
            throw ApiException.badRequest("A guest who is already checked in can only change the departure date, guests, rate and notes — use Move room to change rooms");
        }
        if (request.checkOutDate() != null) to = request.checkOutDate();
        validateDates(from, to, today, !from.equals(booking.getCheckInDate()));
        if (booking.getGroup() != null && (from.isBefore(booking.getGroup().getArrivalDate()) || to.isAfter(booking.getGroup().getDepartureDate()))) {
            throw ApiException.badRequest("This room belongs to group " + booking.getGroup().getGroupCode() + " — keep the stay within the group's dates ("
                    + booking.getGroup().getArrivalDate() + " to " + booking.getGroup().getDepartureDate() + ")");
        }

        int adults = request.adults() != null ? request.adults() : booking.getAdults();
        int children = request.children() != null ? request.children() : booking.getChildren();
        validateGuests(type, adults, children);
        if (room != null) {
            validateRoomForType(room, type);
            ensureRoomFree(room, inHouse ? today : from, to, booking.getId());
        }
        ensureAvailable(type, inHouse ? today : from, to, booking.getId(), booking.getGroup() != null ? booking.getGroup().getId() : null);

        RatePlan plan = booking.getRatePlan();
        boolean planChanged = false;
        if (request.ratePlanId() != null && (plan == null || !plan.getId().equals(request.ratePlanId()))) {
            if (inHouse) throw ApiException.badRequest("The rate plan of a guest who is already checked in cannot be changed");
            plan = sellablePlan(request.ratePlanId(), from, to);
            planChanged = true;
        } else if (plan != null) {
            checkPlanNights(plan, from, to);
        }
        boolean repriceNeeded = planChanged || !from.equals(booking.getCheckInDate()) || !to.equals(booking.getCheckOutDate())
                || !type.getId().equals(booking.getRoomType().getId())
                || (room != null ? booking.getRoom() == null || !room.getId().equals(booking.getRoom().getId()) : booking.getRoom() != null);
        booking.setRatePlan(plan);
        booking.setRoomType(type);
        booking.setRoom(room);
        booking.setCheckInDate(from);
        booking.setCheckOutDate(to);
        booking.setAdults(adults);
        booking.setChildren(children);
        if (!inHouse && request.roomTypeId() != null) {
            booking.setBaseOccupancy(type.getBaseOccupancy());
            booking.setExtraPersonRate(type.getExtraPersonRate());
        }
        if (request.nightlyRate() != null) {
            booking.setNightlyRate(checkedRate(request.nightlyRate()));
            booking.setRateMode(RateMode.FIXED);
            nightRepository.deleteByBookingId(booking.getId());
        }
        if (request.source() != null) booking.setSource(blank(request.source()));
        if (request.notes() != null) booking.setNotes(blank(request.notes()));
        bookingRepository.save(booking);
        boolean backToAuto = Boolean.TRUE.equals(request.autoRate()) && request.nightlyRate() == null;
        if (backToAuto || (repriceNeeded && request.nightlyRate() == null && booking.getRateMode() == RateMode.AUTO)) {
            booking.setRateMode(RateMode.AUTO);
            repriceNights(booking);
        }
        folioService.syncDeferral(booking);
        auditLogService.record("BOOKING_UPDATED", booking.getBookingNumber() + " updated (" + from + " to " + to + ")", "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    /** Change the departure date of a booked or in-house stay. */
    @Transactional
    public BookingDto extend(Long id, ExtendStayRequest request) {
        return update(id, new BookingUpdateRequest(null, null, null, request.checkOutDate(), null, null, null, null, null, null, null));
    }

    @Transactional
    public BookingDto cancel(Long id, CancelRequest request) {
        Booking booking = folioService.getBooking(id);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.badRequest("Only a booking that has not arrived yet can be cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(request == null ? null : blank(request.reason()));
        bookingRepository.save(booking);
        folioService.syncDeferral(booking);
        folioService.settleDepositOnCancel(booking, request == null ? null : request.depositAction(), request == null ? null : request.refundMethod(), request != null && Boolean.TRUE.equals(request.waiveFee()), false);
        auditLogService.record("BOOKING_CANCELLED", booking.getBookingNumber() + " cancelled", "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    @Transactional
    public BookingDto noShow(Long id, CancelRequest request) {
        Booking booking = folioService.getBooking(id);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.badRequest("Only a booking that has not arrived yet can be marked as a no-show");
        }
        if (booking.getCheckInDate().isAfter(clock.today())) {
            throw ApiException.badRequest("The guest is not due until " + booking.getCheckInDate() + " — cancel the booking instead");
        }
        booking.setStatus(BookingStatus.NO_SHOW);
        booking.setCancelReason(request == null ? null : blank(request.reason()));
        bookingRepository.save(booking);
        folioService.syncDeferral(booking);
        folioService.settleDepositOnCancel(booking, request == null ? null : request.depositAction(), request == null ? null : request.refundMethod(), request != null && Boolean.TRUE.equals(request.waiveFee()), true);
        auditLogService.record("BOOKING_NO_SHOW", booking.getBookingNumber() + " marked no-show", "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    // ---------------------------------------------------------------- pricing

    private RatePlan sellablePlan(Long id, LocalDate from, LocalDate to) {
        RatePlan plan = rateService.planOrNull(id);
        if (plan == null) return null;
        if (!plan.isActive()) throw ApiException.badRequest("The rate plan '" + plan.getName() + "' is not being sold at the moment");
        checkPlanNights(plan, from, to);
        return plan;
    }

    private void checkPlanNights(RatePlan plan, LocalDate from, LocalDate to) {
        long nights = ChronoUnit.DAYS.between(from, to);
        if (nights < plan.getMinNights()) {
            throw ApiException.badRequest("The rate plan '" + plan.getName() + "' needs a stay of at least " + plan.getMinNights() + " nights");
        }
    }

    /**
     * (Re)prices an AUTO booking night by night from the rate rules. A checked-in stay keeps the price of the nights already lived
     * (today and before); every other night gets today's price. The booking's nightly rate becomes the average, for display.
     */
    void repriceNights(Booking booking) {
        booking.setRateMode(RateMode.AUTO);
        LocalDate today = clock.today();
        Map<LocalDate, BookingNight> old = new HashMap<>();
        for (BookingNight n : nightRepository.findByBookingIdOrderByNightDateAsc(booking.getId())) old.put(n.getNightDate(), n);
        List<RateService.NightPrice> prices = rateService.price(booking.getRoomType(), booking.getRoom(), booking.getRatePlan(),
                booking.getCheckInDate(), booking.getCheckOutDate());
        nightRepository.deleteByBookingId(booking.getId());
        nightRepository.flush();
        BigDecimal sum = BigDecimal.ZERO;
        for (RateService.NightPrice p : prices) {
            BookingNight kept = booking.getStatus() == BookingStatus.CHECKED_IN && !p.date().isAfter(today) ? old.get(p.date()) : null;
            BookingNight night = new BookingNight();
            night.setBooking(booking);
            night.setNightDate(p.date());
            night.setRate(kept != null ? kept.getRate() : p.rate());
            night.setRuleName(kept != null ? kept.getRuleName() : p.ruleName());
            nightRepository.save(night);
            sum = sum.add(night.getRate());
        }
        booking.setNightlyRate(prices.isEmpty() ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(prices.size()), 2, java.math.RoundingMode.HALF_UP));
        bookingRepository.save(booking);
    }

    // ---------------------------------------------------------------- availability

    /** Rooms of a type, per night, over [from, to). */
    @Transactional(readOnly = true)
    public AvailabilityDto availability(LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<GroupBlock> held = blockRepository.findCandidatesAll(from, to);
        LocalDate today = clock.today();
        List<Room> rooms = roomRepository.findByActiveTrueOrderByNumberAsc();
        Map<Long, List<MaintenanceRequest>> blocks = roomService.openBlocks();
        List<TypeAvailability> types = new ArrayList<>();
        for (RoomType type : roomTypeRepository.findAllByOrderByNameAsc()) {
            if (!type.isActive()) continue;
            List<Room> ofType = rooms.stream().filter(r -> r.getRoomType().getId().equals(type.getId())).toList();
            int sellableNow = (int) ofType.stream().filter(r -> !r.isOutOfOrder()).count();
            List<DayAvailability> days = new ArrayList<>();
            for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
                final LocalDate n = night;
                int total = (int) ofType.stream().filter(r -> !RoomService.blockedOn(blocks, r.getId(), n)).count();
                int booked = countOccupying(live, type.getId(), night, null);
                int hold = heldFor(held, live, type.getId(), night, today, null, null);
                days.add(new DayAvailability(night, total, booked, hold, Math.max(0, total - booked - hold)));
            }
            types.add(new TypeAvailability(type.getId(), type.getName(), sellableNow, days));
        }
        return new AvailabilityDto(from, to, types);
    }

    /** Rooms of a type that are free for the whole of [from, to) — used to pick a room when booking or checking in. */
    @Transactional(readOnly = true)
    public List<RoomDto> freeRooms(Long roomTypeId, LocalDate from, LocalDate to, Long excludeBookingId) {
        if (from == null || to == null || !to.isAfter(from)) throw ApiException.badRequest("Departure must be after arrival");
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<RoomDto> result = new ArrayList<>();
        Map<Long, List<MaintenanceRequest>> blocks = roomService.openBlocks();
        for (Room room : roomRepository.findByActiveTrueOrderByNumberAsc()) {
            if (RoomService.firstBlockedNight(blocks, room.getId(), from, to) != null) continue;
            if (roomTypeId != null && !room.getRoomType().getId().equals(roomTypeId)) continue;
            boolean taken = live.stream().anyMatch(b -> b.getRoom() != null && b.getRoom().getId().equals(room.getId())
                    && !b.getId().equals(excludeBookingId) && overlaps(b, from, to));
            if (!taken) result.add(roomService.toDto(room, null));
        }
        return result;
    }

    /** The reservation chart: every room with the stays that touch [from, to). */
    @Transactional(readOnly = true)
    public RoomGridDto grid(LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<Booking> touching = bookingRepository.findTouching(from, to, LIVE);
        List<GridRoom> rows = new ArrayList<>();
        for (Room room : roomRepository.findByActiveTrueOrderByNumberAsc()) {
            List<GridBooking> bookings = touching.stream()
                    .filter(b -> b.getRoom() != null && b.getRoom().getId().equals(room.getId()))
                    .map(b -> new GridBooking(b.getId(), b.getBookingNumber(), b.getGuest().getFullName(), b.getCheckInDate(), endOf(b),
                            b.getStatus().name()))
                    .toList();
            rows.add(new GridRow(room, bookings).toGrid());
        }
        // bookings still waiting for a room appear as an "Unassigned" row
        List<GridBooking> unassigned = touching.stream().filter(b -> b.getRoom() == null)
                .map(b -> new GridBooking(b.getId(), b.getBookingNumber(), b.getGuest().getFullName() + " (" + b.getRoomType().getName() + ")",
                        b.getCheckInDate(), b.getCheckOutDate(), b.getStatus().name()))
                .toList();
        if (!unassigned.isEmpty()) {
            rows.add(new GridRoom(null, "Unassigned", "", false, unassigned));
        }
        return new RoomGridDto(from, to, rows);
    }

    private record GridRow(Room room, List<GridBooking> bookings) {
        GridRoom toGrid() {
            return new GridRoom(room.getId(), room.getNumber(), room.getRoomType().getName(), room.isOutOfOrder(), bookings);
        }
    }

    // ---------------------------------------------------------------- shared checks

    /** Last night held + 1: the planned departure, or tomorrow for a guest who has stayed past it. */
    LocalDate endOf(Booking b) {
        if (b.getStatus() == BookingStatus.CHECKED_IN) {
            LocalDate tomorrow = clock.today().plusDays(1);
            return b.getCheckOutDate().isAfter(tomorrow) ? b.getCheckOutDate() : tomorrow;
        }
        return b.getCheckOutDate();
    }

    boolean overlaps(Booking b, LocalDate from, LocalDate to) {
        return b.getCheckInDate().isBefore(to) && endOf(b).isAfter(from);
    }

    private boolean occupies(Booking b, LocalDate night) {
        return !b.getCheckInDate().isAfter(night) && night.isBefore(endOf(b));
    }

    private int countOccupying(List<Booking> live, Long typeId, LocalDate night, Long excludeId) {
        int n = 0;
        for (Booking b : live) {
            if (b.getId().equals(excludeId)) continue;
            Long effectiveType = b.getRoom() != null ? b.getRoom().getRoomType().getId() : b.getRoomType().getId();
            if (effectiveType.equals(typeId) && occupies(b, night)) n++;
        }
        return n;
    }

    /** Throws if on any night of [from, to) every room of the type is already taken. */
    void ensureAvailable(RoomType type, LocalDate from, LocalDate to, Long excludeBookingId) {
        ensureAvailable(type, from, to, excludeBookingId, null);
    }

    /** Rooms held for groups but not yet booked by them, on a night (the group {@code exceptGroupId} is left out). */
    private int heldFor(List<GroupBlock> blocks, List<Booking> live, Long typeId, LocalDate night, LocalDate today,
                        Long exceptGroupId, Long excludeBookingId) {
        Map<Long, Integer> rooms = new HashMap<>();
        for (GroupBlock b : blocks) {
            if (!b.getRoomType().getId().equals(typeId) || !b.holdsOn(night, today)) continue;
            Long gid = b.getGroup().getId();
            if (gid.equals(exceptGroupId)) continue;
            rooms.merge(gid, b.getRooms(), Integer::sum);
        }
        int held = 0;
        for (Map.Entry<Long, Integer> e : rooms.entrySet()) {
            held += Math.max(0, e.getValue() - pickedBy(live, e.getKey(), typeId, night, excludeBookingId));
        }
        return held;
    }

    /** Live bookings of the group on a night in a room type. */
    private int pickedBy(List<Booking> live, Long groupId, Long typeId, LocalDate night, Long excludeBookingId) {
        int n = 0;
        for (Booking b : live) {
            if (b.getGroup() == null || !b.getGroup().getId().equals(groupId) || b.getId().equals(excludeBookingId)) continue;
            Long t = b.getRoom() != null ? b.getRoom().getRoomType().getId() : b.getRoomType().getId();
            if (t.equals(typeId) && occupies(b, night)) n++;
        }
        return n;
    }

    /**
     * Throws if a booking cannot be added. A group booking uses a room of its own block when one is still unbooked;
     * otherwise it needs a room that no other group holds.
     */
    void ensureAvailable(RoomType type, LocalDate from, LocalDate to, Long excludeBookingId, Long groupId) {
        List<Room> ofType = roomRepository.findByRoomTypeIdAndActiveTrueOrderByNumberAsc(type.getId());
        if (ofType.isEmpty()) {
            throw ApiException.conflict("There are no rooms available in " + type.getName() + " (none set up, or all out of order)");
        }
        Map<Long, List<MaintenanceRequest>> blocks = roomService.openBlocks();
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<GroupBlock> held = blockRepository.findCandidatesAll(from, to);
        LocalDate today = clock.today();
        for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
            final LocalDate n = night;
            int total = (int) ofType.stream().filter(r -> !RoomService.blockedOn(blocks, r.getId(), n)).count();
            if (total == 0) {
                throw ApiException.conflict("There are no rooms available in " + type.getName() + " on " + night + " (all out of order)");
            }
            if (groupId != null) {
                int own = 0;
                for (GroupBlock b : held) {
                    if (b.getRoomType().getId().equals(type.getId()) && b.getGroup().getId().equals(groupId) && b.holdsOn(night, today)) own += b.getRooms();
                }
                if (own - pickedBy(live, groupId, type.getId(), night, excludeBookingId) > 0) continue;
            }
            int occupied = countOccupying(live, type.getId(), night, excludeBookingId);
            int hold = heldFor(held, live, type.getId(), night, today, groupId, excludeBookingId);
            if (occupied + hold >= total) {
                throw ApiException.conflict(hold > 0 && occupied < total
                        ? "The remaining " + type.getName() + " rooms on " + night + " are held for a group — choose other dates or another room type"
                        : "No " + type.getName() + " room is free on " + night + " — choose other dates or another room type");
            }
        }
    }

    /**
     * Throws if holding {@code rooms} rooms of a type for a group over [from, to) would leave too few for what is already booked or held
     * for others. The group's own bookings count towards its block.
     */
    void ensureBlockFits(RoomType type, LocalDate from, LocalDate to, int rooms, HotelGroup group, Long excludeBlockId, LocalDate releaseDate) {
        List<Room> ofType = roomRepository.findByRoomTypeIdAndActiveTrueOrderByNumberAsc(type.getId());
        Map<Long, List<MaintenanceRequest>> blocks = roomService.openBlocks();
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<GroupBlock> held = blockRepository.findCandidatesAll(from, to);
        LocalDate today = clock.today();
        for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
            final LocalDate n = night;
            if (releaseDate != null && !today.isBefore(releaseDate)) break;
            int total = (int) ofType.stream().filter(r -> !RoomService.blockedOn(blocks, r.getId(), n)).count();
            int groupRooms = rooms;
            for (GroupBlock b : held) {
                if (b.getRoomType().getId().equals(type.getId()) && b.getGroup().getId().equals(group.getId()) && !b.getId().equals(excludeBlockId)
                        && b.holdsOn(night, today)) groupRooms += b.getRooms();
            }
            int picked = pickedBy(live, group.getId(), type.getId(), night, null);
            int others = countOccupying(live, type.getId(), night, null) - picked;
            int hold = heldFor(held, live, type.getId(), night, today, group.getId(), null);
            if (others + hold + Math.max(picked, groupRooms) > total) {
                throw ApiException.conflict("Not enough " + type.getName() + " rooms to hold " + rooms + " on " + night + " — "
                        + Math.max(0, total - others - hold - Math.max(picked, groupRooms - rooms)) + " left");
            }
        }
    }

    /** Throws if the room is held by another booking at any time in [from, to). */
    void ensureRoomFree(Room room, LocalDate from, LocalDate to, Long excludeBookingId) {
        if (!room.isActive()) throw ApiException.badRequest("Room " + room.getNumber() + " is not in use");
        LocalDate blockedNight = RoomService.firstBlockedNight(roomService.openBlocks(), room.getId(), from, to);
        if (blockedNight != null) {
            throw ApiException.badRequest("Room " + room.getNumber() + " is out of order" + (blockedNight.equals(from) ? "" : " from " + blockedNight));
        }
        for (Booking b : bookingRepository.findOverlapping(from, to, LIVE)) {
            if (b.getRoom() != null && b.getRoom().getId().equals(room.getId()) && !b.getId().equals(excludeBookingId) && overlaps(b, from, to)) {
                throw ApiException.conflict("Room " + room.getNumber() + " is already taken by " + b.getBookingNumber() + " ("
                        + b.getGuest().getFullName() + ") for those dates");
            }
        }
    }

    private void validateRoomForType(Room room, RoomType type) {
        if (!room.getRoomType().getId().equals(type.getId())) {
            throw ApiException.badRequest("Room " + room.getNumber() + " is a " + room.getRoomType().getName() + ", not a " + type.getName());
        }
        if (!room.isActive()) throw ApiException.badRequest("Room " + room.getNumber() + " is not in use");
    }

    private RoomType activeType(Long id) {
        RoomType type = roomTypeRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Room type not found: " + id));
        if (!type.isActive()) throw ApiException.badRequest(type.getName() + " is not being sold at the moment");
        return type;
    }

    private void validateDates(LocalDate from, LocalDate to, LocalDate today, boolean arrivalMustNotBePast) {
        if (from == null || to == null) throw ApiException.badRequest("Arrival and departure dates are required");
        if (!to.isAfter(from)) throw ApiException.badRequest("Departure must be after arrival");
        if (arrivalMustNotBePast && from.isBefore(today)) throw ApiException.badRequest("Arrival cannot be in the past");
        if (ChronoUnit.DAYS.between(from, to) > 365) throw ApiException.badRequest("A stay cannot be longer than a year");
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) throw ApiException.badRequest("Choose a date range");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) throw ApiException.badRequest("Choose a range of at most " + MAX_RANGE_DAYS + " days");
    }

    private void validateGuests(RoomType type, int adults, int children) {
        if (adults < 1) throw ApiException.badRequest("At least one adult is required");
        if (children < 0) throw ApiException.badRequest("Children cannot be negative");
        if (adults + children > type.getMaxOccupancy()) {
            throw ApiException.badRequest(type.getName() + " sleeps at most " + type.getMaxOccupancy() + " guests");
        }
    }

    private static BigDecimal checkedRate(BigDecimal rate) {
        if (rate.signum() < 0) throw ApiException.badRequest("The nightly rate cannot be negative");
        return HotelFolioService.money(rate);
    }

    private static BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + status);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
