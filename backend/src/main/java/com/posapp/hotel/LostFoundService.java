package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HkDtos.LostFoundDto;
import com.posapp.hotel.dto.HkRequests.ClaimRequest;
import com.posapp.hotel.dto.HkRequests.DisposeRequest;
import com.posapp.hotel.dto.HkRequests.LostFoundRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Property guests leave behind: logged with the room, linked to the room's last guest, then claimed or disposed of. */
@Service
public class LostFoundService {

    private final LostFoundItemRepository repository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public LostFoundService(LostFoundItemRepository repository, RoomRepository roomRepository, BookingRepository bookingRepository,
                            GuestRepository guestRepository, AuditLogService auditLogService, HotelClock clock) {
        this.repository = repository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Newest first. status: FOUND, CLAIMED, DISPOSED; q matches the number, description, room, location, finder, guest; from/to are found dates. */
    @Transactional(readOnly = true)
    public List<LostFoundDto> list(String q, String status, LocalDate from, LocalDate to) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<LostFoundDto> out = new ArrayList<>();
        for (LostFoundItem i : repository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("foundOn"),
                org.springframework.data.domain.Sort.Order.desc("id")))) {
            if (!st.isEmpty() && !i.getStatus().name().equals(st)) continue;
            if (from != null && i.getFoundOn().isBefore(from)) continue;
            if (to != null && i.getFoundOn().isAfter(to)) continue;
            LostFoundDto d = toDto(i);
            if (!needle.isEmpty() && !(low(d.itemNo()).contains(needle) || low(d.description()).contains(needle) || low(d.roomNumber()).contains(needle)
                    || low(d.location()).contains(needle) || low(d.foundBy()).contains(needle) || low(d.guestName()).contains(needle)
                    || low(d.claimedBy()).contains(needle))) continue;
            out.add(d);
        }
        return out;
    }

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public LostFoundDto get(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public LostFoundDto create(LostFoundRequest r) {
        LostFoundItem i = new LostFoundItem();
        LocalDate found = r.foundOn() != null ? r.foundOn() : clock.today();
        if (found.isAfter(clock.today())) throw ApiException.badRequest("The found date cannot be in the future");
        i.setFoundOn(found);
        fill(i, r);
        if (i.getGuest() == null && i.getRoom() != null) {
            bookingRepository.findFirstByRoomIdAndStatusInAndCheckInDateLessThanEqualOrderByCheckInDateDescIdDesc(i.getRoom().getId(),
                    EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT), found).ifPresent(b -> i.setGuest(b.getGuest()));
        }
        LostFoundItem saved = repository.save(i);
        saved.setItemNo(String.format("LF-%04d", saved.getId()));
        repository.save(saved);
        auditLogService.record("HOTEL_LOST_FOUND", saved.getItemNo() + " " + saved.getDescription(), "LostFoundItem", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public LostFoundDto update(Long id, LostFoundRequest r) {
        LostFoundItem i = getOrThrow(id);
        if (i.getStatus() != LostFoundStatus.FOUND) throw ApiException.badRequest("This item has been " + i.getStatus().name().toLowerCase(Locale.ROOT) + " — it can no longer be changed");
        if (r.foundOn() != null) {
            if (r.foundOn().isAfter(clock.today())) throw ApiException.badRequest("The found date cannot be in the future");
            i.setFoundOn(r.foundOn());
        }
        fill(i, r);
        repository.save(i);
        return toDto(i);
    }

    private void fill(LostFoundItem i, LostFoundRequest r) {
        i.setDescription(r.description().trim());
        i.setRoom(r.roomId() == null ? null : roomRepository.findById(r.roomId()).orElseThrow(() -> ApiException.badRequest("Room not found")));
        i.setLocation(clean(r.location()));
        i.setFoundBy(clean(r.foundBy()));
        i.setNotes(clean(r.notes()));
        if (r.guestId() != null) i.setGuest(guestRepository.findById(r.guestId()).orElseThrow(() -> ApiException.badRequest("Guest not found")));
    }

    @Transactional
    public LostFoundDto claim(Long id, ClaimRequest r) {
        LostFoundItem i = getOrThrow(id);
        if (i.getStatus() != LostFoundStatus.FOUND) throw ApiException.badRequest("This item has already been " + i.getStatus().name().toLowerCase(Locale.ROOT));
        String by = clean(r == null ? null : r.claimedBy());
        if (r != null && r.guestId() != null) {
            Guest g = guestRepository.findById(r.guestId()).orElseThrow(() -> ApiException.badRequest("Guest not found"));
            i.setGuest(g);
            if (by == null) by = g.getFullName();
        }
        if (by == null) throw ApiException.badRequest("Say who collected it");
        LocalDate on = r != null && r.claimedOn() != null ? r.claimedOn() : clock.today();
        if (on.isBefore(i.getFoundOn())) throw ApiException.badRequest("It cannot be claimed before it was found");
        if (on.isAfter(clock.today())) throw ApiException.badRequest("The claim date cannot be in the future");
        i.setStatus(LostFoundStatus.CLAIMED);
        i.setClaimedBy(by);
        i.setClaimedOn(on);
        if (r != null && clean(r.notes()) != null) i.setNotes(i.getNotes() == null ? clean(r.notes()) : i.getNotes() + "\n" + clean(r.notes()));
        repository.save(i);
        auditLogService.record("HOTEL_LOST_FOUND_CLAIMED", i.getItemNo() + " collected by " + by, "LostFoundItem", id);
        return toDto(i);
    }

    @Transactional
    public LostFoundDto dispose(Long id, DisposeRequest r) {
        LostFoundItem i = getOrThrow(id);
        if (i.getStatus() != LostFoundStatus.FOUND) throw ApiException.badRequest("This item has already been " + i.getStatus().name().toLowerCase(Locale.ROOT));
        i.setStatus(LostFoundStatus.DISPOSED);
        i.setClaimedOn(clock.today());
        if (r != null && clean(r.notes()) != null) i.setNotes(i.getNotes() == null ? clean(r.notes()) : i.getNotes() + "\n" + clean(r.notes()));
        repository.save(i);
        auditLogService.record("HOTEL_LOST_FOUND_DISPOSED", i.getItemNo() + " disposed of", "LostFoundItem", id);
        return toDto(i);
    }

    @Transactional
    public void delete(Long id) {
        LostFoundItem i = getOrThrow(id);
        if (i.getStatus() != LostFoundStatus.FOUND) throw ApiException.conflict("Only an item still in storage can be deleted");
        repository.delete(i);
        auditLogService.record("HOTEL_LOST_FOUND_DELETED", i.getItemNo() + " deleted", "LostFoundItem", id);
    }

    private LostFoundItem getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Item not found: " + id));
    }

    private LostFoundDto toDto(LostFoundItem i) {
        Guest g = i.getGuest();
        return new LostFoundDto(i.getId(), i.getItemNo(), i.getDescription(), i.getFoundOn(), i.getRoom() == null ? null : i.getRoom().getId(),
                i.getRoom() == null ? null : i.getRoom().getNumber(), i.getLocation(), i.getFoundBy(), i.getStatus().name(), g == null ? null : g.getId(),
                g == null ? null : g.getFullName(), g == null ? null : g.getPhone(), i.getClaimedBy(), i.getClaimedOn(), i.getNotes());
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
