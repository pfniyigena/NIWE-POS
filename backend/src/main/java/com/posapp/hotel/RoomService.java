package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.RoomDto;
import com.posapp.hotel.dto.HotelDtos.RoomTypeDto;
import com.posapp.hotel.dto.HotelRequests.HousekeepingRequest;
import com.posapp.hotel.dto.HotelRequests.RoomRequest;
import com.posapp.hotel.dto.HotelRequests.RoomTypeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Room types, rooms and the housekeeping state of each room. */
@Service
public class RoomService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final MaintenanceRequestRepository maintenanceRepository;
    private final AuditLogService auditLogService;

    public RoomService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository, BookingRepository bookingRepository,
                       MaintenanceRequestRepository maintenanceRepository, AuditLogService auditLogService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.auditLogService = auditLogService;
    }

    // ---- room types ----

    @Transactional(readOnly = true)
    public List<RoomTypeDto> listTypes() {
        return roomTypeRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public RoomTypeDto createType(RoomTypeRequest request) {
        String name = request.name().trim();
        if (roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A room type named '" + name + "' already exists");
        }
        RoomType type = new RoomType();
        applyType(type, request, name);
        RoomType saved = roomTypeRepository.save(type);
        auditLogService.record("ROOM_TYPE_CREATED", "Created room type " + saved.getName(), "RoomType", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public RoomTypeDto updateType(Long id, RoomTypeRequest request) {
        RoomType type = roomTypeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room type not found: " + id));
        String name = request.name().trim();
        if (roomTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A room type named '" + name + "' already exists");
        }
        applyType(type, request, name);
        RoomType saved = roomTypeRepository.save(type);
        auditLogService.record("ROOM_TYPE_UPDATED", "Updated room type " + saved.getName(), "RoomType", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteType(Long id) {
        RoomType type = roomTypeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room type not found: " + id));
        if (roomRepository.countByRoomTypeId(id) > 0 || bookingRepository.countByRoomTypeId(id) > 0) {
            throw ApiException.conflict("This room type has rooms or bookings — set it inactive instead of deleting it");
        }
        roomTypeRepository.delete(type);
        auditLogService.record("ROOM_TYPE_DELETED", "Deleted room type " + type.getName(), "RoomType", id);
    }

    private void applyType(RoomType type, RoomTypeRequest request, String name) {
        if (request.baseRate().signum() < 0) throw ApiException.badRequest("The nightly rate cannot be negative");
        int baseOcc = request.baseOccupancy() != null ? request.baseOccupancy() : 2;
        int maxOcc = request.maxOccupancy() != null ? request.maxOccupancy() : Math.max(baseOcc, 2);
        if (baseOcc < 1 || maxOcc < baseOcc) throw ApiException.badRequest("Maximum guests must be at least the guests included in the rate");
        type.setName(name);
        type.setDescription(blankToNull(request.description()));
        type.setBaseRate(request.baseRate());
        type.setBaseOccupancy(baseOcc);
        type.setMaxOccupancy(maxOcc);
        type.setExtraPersonRate(request.extraPersonRate() != null ? request.extraPersonRate() : BigDecimal.ZERO);
        type.setAmenities(blankToNull(request.amenities()));
        if (request.active() != null) type.setActive(request.active());
    }

    private RoomTypeDto toDto(RoomType t) {
        return new RoomTypeDto(t.getId(), t.getName(), t.getDescription(), t.getBaseRate(), t.getBaseOccupancy(), t.getMaxOccupancy(),
                t.getExtraPersonRate(), t.getAmenities(), t.isActive(), roomRepository.countByRoomTypeId(t.getId()));
    }

    // ---- rooms ----

    /** Every room with who is in it right now. */
    @Transactional(readOnly = true)
    public List<RoomDto> listRooms() {
        Map<Long, Booking> occupants = occupantsByRoom();
        return roomRepository.findAllByOrderByNumberAsc().stream().map(r -> toDto(r, occupants.get(r.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public RoomDto getRoom(Long id) {
        Room room = getOrThrow(id);
        return toDto(room, occupantsByRoom().get(id));
    }

    @Transactional
    public RoomDto createRoom(RoomRequest request) {
        String number = request.number().trim();
        if (roomRepository.existsByNumberIgnoreCase(number)) {
            throw ApiException.conflict("Room " + number + " already exists");
        }
        Room room = new Room();
        applyRoom(room, request, number);
        Room saved = roomRepository.save(room);
        auditLogService.record("ROOM_CREATED", "Created room " + saved.getNumber(), "Room", saved.getId());
        return toDto(saved, null);
    }

    @Transactional
    public RoomDto updateRoom(Long id, RoomRequest request) {
        Room room = getOrThrow(id);
        String number = request.number().trim();
        if (roomRepository.existsByNumberIgnoreCaseAndIdNot(number, id)) {
            throw ApiException.conflict("Room " + number + " already exists");
        }
        applyRoom(room, request, number);
        Room saved = roomRepository.save(room);
        auditLogService.record("ROOM_UPDATED", "Updated room " + saved.getNumber(), "Room", saved.getId());
        return toDto(saved, occupantsByRoom().get(id));
    }

    @Transactional
    public void deleteRoom(Long id) {
        Room room = getOrThrow(id);
        if (bookingRepository.countByRoomId(id) > 0 || maintenanceRepository.countByRoomId(id) > 0) {
            throw ApiException.conflict("This room has history — set it inactive instead of deleting it");
        }
        roomRepository.delete(room);
        auditLogService.record("ROOM_DELETED", "Deleted room " + room.getNumber(), "Room", id);
    }

    private void applyRoom(Room room, RoomRequest request, String number) {
        RoomType type = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(() -> ApiException.badRequest("Room type not found: " + request.roomTypeId()));
        if (request.rateOverride() != null && request.rateOverride().signum() < 0) {
            throw ApiException.badRequest("The nightly rate cannot be negative");
        }
        room.setNumber(number);
        room.setFloor(blankToNull(request.floor()));
        room.setRoomType(type);
        room.setRateOverride(request.rateOverride());
        room.setNotes(blankToNull(request.notes()));
        if (request.active() != null) room.setActive(request.active());
    }

    // ---- housekeeping ----

    /** Any hotel viewer may update cleaning status (housekeeping staff do not need the right to edit bookings). */
    @Transactional
    public RoomDto updateHousekeeping(Long id, HousekeepingRequest request) {
        Room room = getOrThrow(id);
        HousekeepingStatus status;
        try {
            status = HousekeepingStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown housekeeping status: " + request.status());
        }
        room.setHousekeepingStatus(status);
        if (request.housekeeper() != null) room.setHousekeeper(blankToNull(request.housekeeper()));
        if (request.note() != null) room.setHousekeepingNote(blankToNull(request.note()));
        Room saved = roomRepository.save(room);
        auditLogService.record("HOUSEKEEPING_UPDATED", "Room " + saved.getNumber() + " marked " + status, "Room", saved.getId());
        return toDto(saved, occupantsByRoom().get(id));
    }

    // ---- shared helpers ----

    Room getOrThrow(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room not found: " + id));
    }

    /** Checked-in bookings by room id. */
    Map<Long, Booking> occupantsByRoom() {
        Map<Long, Booking> map = new HashMap<>();
        for (Booking b : bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN)) {
            if (b.getRoom() != null) map.put(b.getRoom().getId(), b);
        }
        return map;
    }

    /** Recomputes the out-of-order flag from open blocking maintenance requests. */
    @Transactional
    public void refreshOutOfOrder(Long roomId) {
        Room room = getOrThrow(roomId);
        boolean blocked = maintenanceRepository.existsByRoomIdAndBlocksRoomTrueAndStatusNot(roomId, MaintenanceStatus.DONE);
        if (room.isOutOfOrder() != blocked) {
            room.setOutOfOrder(blocked);
            roomRepository.save(room);
        }
    }

    RoomDto toDto(Room r, Booking occupant) {
        return new RoomDto(r.getId(), r.getNumber(), r.getFloor(), r.getRoomType().getId(), r.getRoomType().getName(), r.effectiveRate(),
                r.getRateOverride(), r.getHousekeepingStatus() == null ? HousekeepingStatus.CLEAN.name() : r.getHousekeepingStatus().name(),
                r.getHousekeeper(), r.getHousekeepingNote(), r.isOutOfOrder(), r.isActive(), r.getNotes(),
                occupant != null, occupant != null ? occupant.getId() : null,
                occupant != null ? occupant.getGuest().getFullName() : null, occupant != null ? occupant.getCheckOutDate() : null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
