package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.GuestDto;
import com.posapp.hotel.dto.HotelRequests.GuestRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;

    public GuestService(GuestRepository guestRepository, BookingRepository bookingRepository, AuditLogService auditLogService) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogService = auditLogService;
    }

    /** Search by name, phone, email or ID number; blank = the first 100 alphabetically. */
    @Transactional(readOnly = true)
    public List<GuestDto> search(String query) {
        List<Guest> guests = query == null || query.isBlank()
                ? guestRepository.findAllByOrderByFullNameAsc(PageRequest.of(0, 100))
                : guestRepository.search(query.trim(), PageRequest.of(0, 100));
        return guests.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public GuestDto get(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public GuestDto create(GuestRequest request) {
        Guest saved = guestRepository.save(apply(new Guest(), request));
        auditLogService.record("GUEST_CREATED", "Added guest " + saved.getFullName(), "Guest", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public GuestDto update(Long id, GuestRequest request) {
        Guest saved = guestRepository.save(apply(getOrThrow(id), request));
        auditLogService.record("GUEST_UPDATED", "Updated guest " + saved.getFullName(), "Guest", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Guest guest = getOrThrow(id);
        if (bookingRepository.countByGuestId(id) > 0) {
            throw ApiException.conflict("This guest has bookings and cannot be deleted");
        }
        guestRepository.delete(guest);
        auditLogService.record("GUEST_DELETED", "Deleted guest " + guest.getFullName(), "Guest", id);
    }

    /** Used by bookings: an existing guest by id, or a new one from the given details. */
    @Transactional
    public Guest resolve(Long guestId, GuestRequest details) {
        if (guestId != null) return getOrThrow(guestId);
        if (details == null || details.fullName() == null || details.fullName().isBlank()) {
            throw ApiException.badRequest("Choose a guest or enter the guest's name");
        }
        return guestRepository.save(apply(new Guest(), details));
    }

    Guest getOrThrow(Long id) {
        return guestRepository.findById(id).orElseThrow(() -> ApiException.notFound("Guest not found: " + id));
    }

    private Guest apply(Guest guest, GuestRequest r) {
        guest.setFullName(r.fullName().trim());
        guest.setPhone(clean(r.phone()));
        guest.setEmail(clean(r.email()));
        guest.setNationality(clean(r.nationality()));
        guest.setIdType(clean(r.idType()));
        guest.setIdNumber(clean(r.idNumber()));
        guest.setAddress(clean(r.address()));
        guest.setNotes(clean(r.notes()));
        return guest;
    }

    private GuestDto toDto(Guest g) {
        return new GuestDto(g.getId(), g.getFullName(), g.getPhone(), g.getEmail(), g.getNationality(), g.getIdType(), g.getIdNumber(),
                g.getAddress(), g.getNotes(), bookingRepository.countByGuestId(g.getId()));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
