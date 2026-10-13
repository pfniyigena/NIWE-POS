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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

    public BookingService(BookingRepository bookingRepository, RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                          GuestService guestService, RoomService roomService, HotelFolioService folioService, HotelClock clock,
                          AuditLogService auditLogService) {
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

    @Transactional(readOnly = true)
    public BookingDto get(Long id) {
        return folioService.toDto(folioService.getBooking(id));
    }

    // ---------------------------------------------------------------- create / change

    @Transactional
    public BookingDto create(BookingRequest request) {
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
        ensureAvailable(type, from, to, null);

        Booking booking = new Booking();
        booking.setGuest(guestService.resolve(request.guestId(), request.guest()));
        booking.setRoomType(type);
        booking.setRoom(room);
        booking.setCheckInDate(from);
        booking.setCheckOutDate(to);
        booking.setAdults(adults);
        booking.setChildren(children);
        booking.setBaseOccupancy(type.getBaseOccupancy());
        booking.setExtraPersonRate(type.getExtraPersonRate());
        booking.setNightlyRate(request.nightlyRate() != null ? checkedRate(request.nightlyRate())
                : room != null ? room.effectiveRate() : type.getBaseRate());
        booking.setSource(blank(request.source()));
        booking.setNotes(blank(request.notes()));
        booking.setStatus(BookingStatus.BOOKED);
        Booking saved = bookingRepository.save(booking);
        saved.setBookingNumber(String.format("BK-%05d", saved.getId()));
        bookingRepository.save(saved);

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

        int adults = request.adults() != null ? request.adults() : booking.getAdults();
        int children = request.children() != null ? request.children() : booking.getChildren();
        validateGuests(type, adults, children);
        if (room != null) {
            validateRoomForType(room, type);
            ensureRoomFree(room, inHouse ? today : from, to, booking.getId());
        }
        ensureAvailable(type, inHouse ? today : from, to, booking.getId());

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
        if (request.nightlyRate() != null) booking.setNightlyRate(checkedRate(request.nightlyRate()));
        if (request.source() != null) booking.setSource(blank(request.source()));
        if (request.notes() != null) booking.setNotes(blank(request.notes()));
        bookingRepository.save(booking);
        folioService.syncDeferral(booking);
        auditLogService.record("BOOKING_UPDATED", booking.getBookingNumber() + " updated (" + from + " to " + to + ")", "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    /** Change the departure date of a booked or in-house stay. */
    @Transactional
    public BookingDto extend(Long id, ExtendStayRequest request) {
        return update(id, new BookingUpdateRequest(null, null, null, request.checkOutDate(), null, null, null, null, null));
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
        folioService.settleDepositOnCancel(booking, request == null ? null : request.depositAction(), request == null ? null : request.refundMethod());
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
        folioService.settleDepositOnCancel(booking, request == null ? null : request.depositAction(), request == null ? null : request.refundMethod());
        auditLogService.record("BOOKING_NO_SHOW", booking.getBookingNumber() + " marked no-show", "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    // ---------------------------------------------------------------- availability

    /** Rooms of a type, per night, over [from, to). */
    @Transactional(readOnly = true)
    public AvailabilityDto availability(LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<Room> rooms = roomRepository.findByActiveTrueOrderByNumberAsc();
        List<TypeAvailability> types = new ArrayList<>();
        for (RoomType type : roomTypeRepository.findAllByOrderByNameAsc()) {
            if (!type.isActive()) continue;
            int total = (int) rooms.stream().filter(r -> r.getRoomType().getId().equals(type.getId()) && !r.isOutOfOrder()).count();
            List<DayAvailability> days = new ArrayList<>();
            for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
                int booked = countOccupying(live, type.getId(), night, null);
                days.add(new DayAvailability(night, total, booked, Math.max(0, total - booked)));
            }
            types.add(new TypeAvailability(type.getId(), type.getName(), total, days));
        }
        return new AvailabilityDto(from, to, types);
    }

    /** Rooms of a type that are free for the whole of [from, to) — used to pick a room when booking or checking in. */
    @Transactional(readOnly = true)
    public List<RoomDto> freeRooms(Long roomTypeId, LocalDate from, LocalDate to, Long excludeBookingId) {
        if (from == null || to == null || !to.isAfter(from)) throw ApiException.badRequest("Departure must be after arrival");
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        List<RoomDto> result = new ArrayList<>();
        for (Room room : roomRepository.findByActiveTrueOrderByNumberAsc()) {
            if (room.isOutOfOrder()) continue;
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
        int total = (int) roomRepository.findByRoomTypeIdAndActiveTrueOrderByNumberAsc(type.getId()).stream().filter(r -> !r.isOutOfOrder()).count();
        if (total == 0) {
            throw ApiException.conflict("There are no rooms available in " + type.getName() + " (none set up, or all out of order)");
        }
        List<Booking> live = bookingRepository.findOverlapping(from, to, LIVE);
        for (LocalDate night = from; night.isBefore(to); night = night.plusDays(1)) {
            if (countOccupying(live, type.getId(), night, excludeBookingId) >= total) {
                throw ApiException.conflict("No " + type.getName() + " room is free on " + night + " — choose other dates or another room type");
            }
        }
    }

    /** Throws if the room is held by another booking at any time in [from, to). */
    void ensureRoomFree(Room room, LocalDate from, LocalDate to, Long excludeBookingId) {
        if (!room.isActive()) throw ApiException.badRequest("Room " + room.getNumber() + " is not in use");
        if (room.isOutOfOrder()) throw ApiException.badRequest("Room " + room.getNumber() + " is out of order");
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
