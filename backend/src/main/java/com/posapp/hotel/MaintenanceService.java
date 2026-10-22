package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.MaintenanceDto;
import com.posapp.hotel.dto.HotelRequests.MaintenanceRequestForm;
import com.posapp.hotel.dto.HotelRequests.MaintenanceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.EnumSet;
import java.util.ArrayList;
import java.time.LocalDate;

/** Room faults and repairs. A request that "blocks the room" takes it out of sale until it is done. */
@Service
public class MaintenanceService {

    private final MaintenanceRequestRepository repository;
    private final RoomService roomService;
    private final AuditLogService auditLogService;
    private final BookingRepository bookingRepository;
    private final HotelClock clock;

    public MaintenanceService(MaintenanceRequestRepository repository, RoomService roomService, AuditLogService auditLogService,
                              BookingRepository bookingRepository, HotelClock clock) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.repository = repository;
        this.roomService = roomService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> list() {
        return repository.findAllByOrderByIdDesc().stream().map(MaintenanceService::toDto).toList();
    }

    @Transactional
    public MaintenanceDto create(MaintenanceRequestForm form) {
        Room room = roomService.getOrThrow(form.roomId());
        MaintenanceRequest request = new MaintenanceRequest();
        request.setRoom(room);
        request.setTitle(form.title().trim());
        request.setDescription(form.description() == null || form.description().isBlank() ? null : form.description().trim());
        request.setPriority(parsePriority(form.priority(), MaintenancePriority.NORMAL));
        request.setBlocksRoom(Boolean.TRUE.equals(form.blocksRoom()));
        applyWindow(request, form.blockFrom(), form.blockUntil(), false);
        request.setReportedBy(form.reportedBy() == null || form.reportedBy().isBlank() ? null : form.reportedBy().trim());
        MaintenanceRequest saved = repository.save(request);
        roomService.refreshOutOfOrder(room.getId());
        auditLogService.record("MAINTENANCE_REPORTED", "Room " + room.getNumber() + ": " + saved.getTitle(), "MaintenanceRequest", saved.getId());
        return toDto(saved, conflicts(saved));
    }

    @Transactional
    public MaintenanceDto update(Long id, MaintenanceUpdateRequest update) {
        MaintenanceRequest request = repository.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance request not found: " + id));
        if (update.status() != null) {
            MaintenanceStatus status;
            try {
                status = MaintenanceStatus.valueOf(update.status().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown status: " + update.status());
            }
            request.setStatus(status);
            request.setResolvedAt(status == MaintenanceStatus.DONE ? Instant.now() : null);
        }
        if (update.priority() != null) request.setPriority(parsePriority(update.priority(), request.getPriority()));
        if (update.blocksRoom() != null) request.setBlocksRoom(update.blocksRoom());
        applyWindow(request, update.blockFrom(), update.blockUntil(), Boolean.TRUE.equals(update.clearBlockDates()));
        if (update.resolutionNote() != null) request.setResolutionNote(update.resolutionNote().isBlank() ? null : update.resolutionNote().trim());
        MaintenanceRequest saved = repository.save(request);
        roomService.refreshOutOfOrder(saved.getRoom().getId());
        auditLogService.record("MAINTENANCE_UPDATED", "Room " + saved.getRoom().getNumber() + ": " + saved.getTitle() + " is now " + saved.getStatus(),
                "MaintenanceRequest", saved.getId());
        return toDto(saved, conflicts(saved));
    }

    @Transactional
    public void delete(Long id) {
        MaintenanceRequest request = repository.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance request not found: " + id));
        Long roomId = request.getRoom().getId();
        repository.delete(request);
        roomService.refreshOutOfOrder(roomId);
        auditLogService.record("MAINTENANCE_DELETED", "Deleted maintenance request " + request.getTitle(), "MaintenanceRequest", id);
    }

    /** Sets the dates the room is out of sale (only while the request takes the room out of service). */
    private void applyWindow(MaintenanceRequest request, LocalDate from, LocalDate until, boolean clear) {
        if (clear) {
            request.setBlockFrom(null);
            request.setBlockUntil(null);
        }
        if (from != null) request.setBlockFrom(from);
        if (until != null) request.setBlockUntil(until);
        if (!request.isBlocksRoom()) {
            request.setBlockFrom(null);
            request.setBlockUntil(null);
        }
        if (request.getBlockFrom() != null && request.getBlockUntil() != null && !request.getBlockUntil().isAfter(request.getBlockFrom())) {
            throw ApiException.badRequest("The room must be back in service after the day it goes out of service");
        }
    }

    /** Bookings of this room that fall in the window the repair keeps it out of sale — the front desk has to move or tell them. */
    private List<String> conflicts(MaintenanceRequest m) {
        if (!m.isBlocksRoom() || m.getStatus() == MaintenanceStatus.DONE) return List.of();
        LocalDate today = clock.today();
        LocalDate from = m.getBlockFrom() != null && m.getBlockFrom().isAfter(today) ? m.getBlockFrom() : today;
        LocalDate to = m.getBlockUntil() != null ? m.getBlockUntil() : today.plusYears(2);
        if (!to.isAfter(from)) return List.of();
        List<String> out = new ArrayList<>();
        for (Booking b : bookingRepository.findOverlapping(from, to, EnumSet.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN))) {
            if (b.getRoom() != null && b.getRoom().getId().equals(m.getRoom().getId())) {
                out.add(b.getBookingNumber() + " — " + b.getGuest().getFullName() + ", " + b.getCheckInDate() + " to " + b.getCheckOutDate());
            }
        }
        return out;
    }

    private static MaintenancePriority parsePriority(String value, MaintenancePriority fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return MaintenancePriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown priority: " + value);
        }
    }

    private static MaintenanceDto toDto(MaintenanceRequest m) {
        return toDto(m, List.of());
    }

    private static MaintenanceDto toDto(MaintenanceRequest m, List<String> conflicts) {
        return new MaintenanceDto(m.getId(), m.getRoom().getId(), m.getRoom().getNumber(), m.getTitle(), m.getDescription(),
                m.getPriority().name(), m.getStatus().name(), m.isBlocksRoom(), m.getReportedBy(), m.getCreatedAt(), m.getResolvedAt(),
                m.getResolutionNote(), m.getBlockFrom(), m.getBlockUntil(), conflicts);
    }
}
