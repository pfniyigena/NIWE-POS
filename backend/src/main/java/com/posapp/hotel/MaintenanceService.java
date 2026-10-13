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

/** Room faults and repairs. A request that "blocks the room" takes it out of sale until it is done. */
@Service
public class MaintenanceService {

    private final MaintenanceRequestRepository repository;
    private final RoomService roomService;
    private final AuditLogService auditLogService;

    public MaintenanceService(MaintenanceRequestRepository repository, RoomService roomService, AuditLogService auditLogService) {
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
        request.setReportedBy(form.reportedBy() == null || form.reportedBy().isBlank() ? null : form.reportedBy().trim());
        MaintenanceRequest saved = repository.save(request);
        roomService.refreshOutOfOrder(room.getId());
        auditLogService.record("MAINTENANCE_REPORTED", "Room " + room.getNumber() + ": " + saved.getTitle(), "MaintenanceRequest", saved.getId());
        return toDto(saved);
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
        if (update.resolutionNote() != null) request.setResolutionNote(update.resolutionNote().isBlank() ? null : update.resolutionNote().trim());
        MaintenanceRequest saved = repository.save(request);
        roomService.refreshOutOfOrder(saved.getRoom().getId());
        auditLogService.record("MAINTENANCE_UPDATED", "Room " + saved.getRoom().getNumber() + ": " + saved.getTitle() + " is now " + saved.getStatus(),
                "MaintenanceRequest", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        MaintenanceRequest request = repository.findById(id).orElseThrow(() -> ApiException.notFound("Maintenance request not found: " + id));
        Long roomId = request.getRoom().getId();
        repository.delete(request);
        roomService.refreshOutOfOrder(roomId);
        auditLogService.record("MAINTENANCE_DELETED", "Deleted maintenance request " + request.getTitle(), "MaintenanceRequest", id);
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
        return new MaintenanceDto(m.getId(), m.getRoom().getId(), m.getRoom().getNumber(), m.getTitle(), m.getDescription(),
                m.getPriority().name(), m.getStatus().name(), m.isBlocksRoom(), m.getReportedBy(), m.getCreatedAt(), m.getResolvedAt(),
                m.getResolutionNote());
    }
}
