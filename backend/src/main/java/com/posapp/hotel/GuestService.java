package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.GuestDto;
import com.posapp.hotel.dto.HotelRequests.GuestRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;
    private final GuestDocumentRepository documentRepository;

    public GuestService(GuestRepository guestRepository, BookingRepository bookingRepository, AuditLogService auditLogService, GuestDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
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

    /** One page of guests (or all of them when {@code size} is 0 or less), by name, phone, e-mail, ID number or nationality, alphabetical. */
    @Transactional(readOnly = true)
    public Page<GuestDto> page(String query, int page, int size) {
        String needle = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        Specification<Guest> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (needle != null) {
                ps.add(cb.or(cb.like(cb.lower(root.<String>get("fullName")), needle),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("phone"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("email"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("idNumber"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("nationality"), "")), needle)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Order.asc("fullName").ignoreCase(), Sort.Order.asc("id"));
        if (size <= 0) {
            return new PageImpl<>(guestRepository.findAll(spec, sort).stream().map(this::toDto).toList());
        }
        return guestRepository.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(size, 500), sort)).map(this::toDto);
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
        Guest guest = getOrThrow(id);
        boolean wasBlacklisted = guest.isBlacklisted(), wasVip = guest.isVip();
        Guest saved = guestRepository.save(apply(guest, request));
        auditLogService.record("GUEST_UPDATED", "Updated guest " + saved.getFullName(), "Guest", saved.getId());
        if (saved.isBlacklisted() != wasBlacklisted) {
            auditLogService.record(saved.isBlacklisted() ? "GUEST_BLACKLISTED" : "GUEST_UNBLACKLISTED",
                    saved.getFullName() + (saved.isBlacklisted() ? " blacklisted: " + saved.getBlacklistReason() : " removed from the blacklist"), "Guest", saved.getId());
        }
        if (saved.isVip() != wasVip) {
            auditLogService.record(saved.isVip() ? "GUEST_VIP_SET" : "GUEST_VIP_CLEARED", saved.getFullName() + (saved.isVip() ? " marked VIP" : " is no longer VIP"), "Guest", saved.getId());
        }
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Guest guest = getOrThrow(id);
        if (bookingRepository.countByGuestId(id) > 0) {
            throw ApiException.conflict("This guest has bookings and cannot be deleted");
        }
        if (documentRepository.countByGuestId(id) > 0) {
            throw ApiException.conflict("This guest has ID documents on file. Delete the documents first, or merge the guest into another one.");
        }
        guestRepository.delete(guest);
        auditLogService.record("GUEST_DELETED", "Deleted guest " + guest.getFullName(), "Guest", id);
    }

    /** Used by bookings: an existing guest by id, or a new one from the given details. */
    @Transactional
    public Guest resolve(Long guestId, GuestRequest details) {
        if (guestId != null) {
            Guest existing = getOrThrow(guestId);
            refuseIfBlacklisted(existing);
            return existing;
        }
        if (details == null || details.fullName() == null || details.fullName().isBlank()) {
            throw ApiException.badRequest("Choose a guest or enter the guest's name");
        }
        // A blacklisted guest cannot come back under a fresh record with the same ID document or phone number.
        String idNo = clean(details.idNumber());
        if (idNo != null) guestRepository.findByBlacklistedTrueAndIdNumberIgnoreCase(idNo).stream().findFirst().ifPresent(this::refuseIfBlacklisted);
        String phone = clean(details.phone());
        if (phone != null) guestRepository.findByBlacklistedTrueAndPhone(phone).stream().findFirst().ifPresent(this::refuseIfBlacklisted);
        return guestRepository.save(apply(new Guest(), details));
    }

    /** Bookings and check-in are refused for a blacklisted guest. */
    void refuseIfBlacklisted(Guest g) {
        if (g.isBlacklisted()) {
            String why = g.getBlacklistReason() == null || g.getBlacklistReason().isBlank() ? "" : ": " + g.getBlacklistReason();
            throw ApiException.conflict(g.getFullName() + " is on the hotel's blacklist" + why + ". Remove them from the blacklist first if this should go ahead.");
        }
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
        guest.setPreferences(clean(r.preferences()));
        guest.setProfession(clean(r.profession()));
        if (r.dateOfBirth() != null && (r.dateOfBirth().isAfter(java.time.LocalDate.now()) || r.dateOfBirth().getYear() < 1900)) {
            throw ApiException.badRequest("The date of birth is not valid");
        }
        guest.setDateOfBirth(r.dateOfBirth());
        if (r.vip() != null) guest.setVip(r.vip());
        if (r.blacklisted() != null) {
            if (r.blacklisted()) {
                String reason = clean(r.blacklistReason());
                if (reason == null) reason = guest.isBlacklisted() ? guest.getBlacklistReason() : null;
                if (reason == null) throw ApiException.badRequest("Say why this guest is being blacklisted");
                if (reason.length() > 500) throw ApiException.badRequest("The reason can be at most 500 characters");
                guest.setBlacklisted(true);
                guest.setBlacklistReason(reason);
            } else {
                guest.setBlacklisted(false);
                guest.setBlacklistReason(null);
            }
        }
        return guest;
    }

    GuestDto toDto(Guest g) {
        return new GuestDto(g.getId(), g.getFullName(), g.getPhone(), g.getEmail(), g.getNationality(), g.getIdType(), g.getIdNumber(),
                g.getAddress(), g.getNotes(), bookingRepository.countByGuestId(g.getId()),
                g.isVip(), g.isBlacklisted(), g.getBlacklistReason(), g.getPreferences(), g.getDateOfBirth(), g.getProfession());
    }

    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
