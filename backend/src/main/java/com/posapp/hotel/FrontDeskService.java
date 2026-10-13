package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelDtos.FolioDto;
import com.posapp.hotel.dto.HotelDtos.InHouseDto;
import com.posapp.hotel.dto.HotelRequests.BookingRequest;
import com.posapp.hotel.dto.HotelRequests.CheckInRequest;
import com.posapp.hotel.dto.HotelRequests.CheckOutRequest;
import com.posapp.hotel.dto.HotelRequests.MoveRoomRequest;
import com.posapp.hotel.dto.HotelRequests.WalkInRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The front desk: today's arrivals and departures, check-in, check-out (with the bill), and room moves. */
@Service
public class FrontDeskService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final BookingService bookingService;
    private final RoomService roomService;
    private final HotelFolioService folioService;
    private final HotelClock clock;
    private final AuditLogService auditLogService;

    public FrontDeskService(BookingRepository bookingRepository, RoomRepository roomRepository, BookingService bookingService,
                            RoomService roomService, HotelFolioService folioService, HotelClock clock, AuditLogService auditLogService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.bookingService = bookingService;
        this.roomService = roomService;
        this.folioService = folioService;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- lists

    /** Guests due to arrive today, plus any earlier booking that never turned up and is still open. */
    @Transactional(readOnly = true)
    public List<BookingDto> arrivals() {
        LocalDate today = clock.today();
        return bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.BOOKED).stream()
                .filter(b -> !b.getCheckInDate().isAfter(today)).map(folioService::toDto).toList();
    }

    /** In-house guests due to leave today or already overdue. */
    @Transactional(readOnly = true)
    public List<BookingDto> departures() {
        return bookingRepository.findByStatusAndCheckOutDateLessThanEqualOrderByCheckOutDateAscIdAsc(BookingStatus.CHECKED_IN, clock.today())
                .stream().map(folioService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> inHouse() {
        return bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).stream().map(folioService::toDto).toList();
    }

    /** What the POS and the restaurant show when choosing whose room to charge. */
    @Transactional(readOnly = true)
    public List<InHouseDto> inHouseGuests() {
        return bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).stream()
                .filter(b -> b.getRoom() != null)
                .map(b -> new InHouseDto(b.getId(), b.getBookingNumber(), b.getRoom().getNumber(), b.getGuest().getFullName()))
                .sorted((a, b) -> a.roomNumber().compareToIgnoreCase(b.roomNumber()))
                .toList();
    }

    // ---------------------------------------------------------------- check-in

    @Transactional
    public BookingDto checkIn(Long bookingId, CheckInRequest request) {
        Booking booking = folioService.getBooking(bookingId);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.badRequest("Only a booking that is waiting for the guest can be checked in");
        }
        LocalDate today = clock.today();
        if (booking.getCheckInDate().isAfter(today)) {
            throw ApiException.badRequest("This guest is not due until " + booking.getCheckInDate()
                    + ". Change the arrival date first if they are arriving early.");
        }
        Room room = request != null && request.roomId() != null ? roomService.getOrThrow(request.roomId()) : booking.getRoom();
        if (room == null) throw ApiException.badRequest("Choose a room for the guest");
        if (!room.isActive()) throw ApiException.badRequest("Room " + room.getNumber() + " is not in use");
        if (room.isOutOfOrder()) throw ApiException.badRequest("Room " + room.getNumber() + " is out of order");
        if (!bookingRepository.findByStatusAndRoomId(BookingStatus.CHECKED_IN, room.getId()).isEmpty()) {
            throw ApiException.conflict("Room " + room.getNumber() + " is still occupied");
        }
        bookingService.ensureRoomFree(room, today, today.plusDays(1).isAfter(booking.getCheckOutDate()) ? today.plusDays(1) : booking.getCheckOutDate(), booking.getId());
        boolean ready = room.getHousekeepingStatus() == HousekeepingStatus.CLEAN || room.getHousekeepingStatus() == HousekeepingStatus.INSPECTED;
        if (!ready && !(request != null && Boolean.TRUE.equals(request.allowNotReady()))) {
            throw ApiException.badRequest("Room " + room.getNumber() + " is not ready (" + room.getHousekeepingStatus().name().toLowerCase()
                    + "). Have it cleaned, or check in anyway.");
        }
        int adults = request != null && request.adults() != null ? request.adults() : booking.getAdults();
        int children = request != null && request.children() != null ? request.children() : booking.getChildren();
        if (adults < 1 || children < 0) throw ApiException.badRequest("At least one adult is required");
        if (adults + children > room.getRoomType().getMaxOccupancy()) {
            throw ApiException.badRequest("Room " + room.getNumber() + " sleeps at most " + room.getRoomType().getMaxOccupancy() + " guests");
        }
        booking.setRoom(room);
        booking.setAdults(adults);
        booking.setChildren(children);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setActualCheckIn(Instant.now());
        bookingRepository.save(booking);
        folioService.ensureRoomCharges(booking, today.plusDays(1));
        folioService.syncDeferral(booking);
        auditLogService.record("HOTEL_CHECK_IN", booking.getGuest().getFullName() + " checked in to room " + room.getNumber(), "Booking", booking.getId());
        return folioService.toDto(booking);
    }

    /** A guest with no booking: books the room for today and checks in, in one step. */
    @Transactional
    public BookingDto walkIn(WalkInRequest request) {
        Room room = roomService.getOrThrow(request.roomId());
        BookingRequest booking = new BookingRequest(request.guestId(), request.guest(), room.getRoomType().getId(), room.getId(),
                clock.today(), request.checkOutDate(), request.adults(), request.children(), request.nightlyRate(), "Walk-in",
                request.notes(), request.depositAmount(), request.depositMethod());
        BookingDto created = bookingService.create(booking);
        return checkIn(created.id(), new CheckInRequest(room.getId(), request.adults(), request.children(), false));
    }

    // ---------------------------------------------------------------- check-out

    /** Settles the bill (see {@link HotelFolioService#settleAtCheckout}), closes the stay and leaves the room dirty. Returns the final bill. */
    @Transactional
    public FolioDto checkOut(Long bookingId, CheckOutRequest request) {
        Booking booking = folioService.getBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.badRequest("Only a guest who is checked in can be checked out");
        }
        LocalDate today = clock.today();
        folioService.settleAtCheckout(booking, request, today);
        booking.setStatus(BookingStatus.CHECKED_OUT);
        booking.setActualCheckOut(Instant.now());
        booking.setCheckOutDate(today.isAfter(booking.getCheckInDate()) ? today : booking.getCheckInDate().plusDays(1));
        bookingRepository.save(booking);
        Room room = booking.getRoom();
        if (room != null) {
            room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
            roomRepository.save(room);
        }
        auditLogService.record("HOTEL_CHECK_OUT", booking.getGuest().getFullName() + " checked out of room "
                + (room != null ? room.getNumber() : "?") + " (" + booking.getBookingNumber() + ")", "Booking", booking.getId());
        return folioService.toFolio(booking);
    }

    // ---------------------------------------------------------------- move

    @Transactional
    public BookingDto moveRoom(Long bookingId, MoveRoomRequest request) {
        Booking booking = folioService.getBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.badRequest("Only a guest who is checked in can be moved");
        }
        Room target = roomService.getOrThrow(request.roomId());
        Room old = booking.getRoom();
        if (old != null && old.getId().equals(target.getId())) throw ApiException.badRequest("The guest is already in that room");
        LocalDate today = clock.today();
        if (!bookingRepository.findByStatusAndRoomId(BookingStatus.CHECKED_IN, target.getId()).isEmpty()) {
            throw ApiException.conflict("Room " + target.getNumber() + " is occupied");
        }
        bookingService.ensureRoomFree(target, today, bookingService.endOf(booking), booking.getId());
        if (booking.guests() > target.getRoomType().getMaxOccupancy()) {
            throw ApiException.badRequest("Room " + target.getNumber() + " sleeps at most " + target.getRoomType().getMaxOccupancy() + " guests");
        }
        booking.setRoom(target);
        String note = "Moved " + (old != null ? "from room " + old.getNumber() + " " : "") + "to room " + target.getNumber() + " on " + today
                + (request.reason() == null || request.reason().isBlank() ? "" : " (" + request.reason().trim() + ")");
        booking.setNotes(booking.getNotes() == null || booking.getNotes().isBlank() ? note : booking.getNotes() + "\n" + note);
        bookingRepository.save(booking);
        if (old != null) {
            old.setHousekeepingStatus(HousekeepingStatus.DIRTY);
            roomRepository.save(old);
        }
        auditLogService.record("HOTEL_ROOM_MOVE", booking.getBookingNumber() + ": " + note, "Booking", booking.getId());
        return folioService.toDto(booking);
    }
}
