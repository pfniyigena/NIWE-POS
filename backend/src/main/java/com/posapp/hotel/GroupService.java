package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.hotel.dto.GroupDtos.GroupBlockDto;
import com.posapp.hotel.dto.GroupDtos.GroupDetailDto;
import com.posapp.hotel.dto.GroupDtos.GroupEntryDto;
import com.posapp.hotel.dto.GroupDtos.GroupSummaryDto;
import com.posapp.hotel.dto.GroupDtos.GroupTotalsDto;
import com.posapp.hotel.dto.GroupDtos.NamesResultDto;
import com.posapp.hotel.dto.GroupDtos.RoomingDto;
import com.posapp.hotel.dto.GroupRequests.AddRoomsRequest;
import com.posapp.hotel.dto.GroupRequests.BlockRequest;
import com.posapp.hotel.dto.GroupRequests.GroupCancelRequest;
import com.posapp.hotel.dto.GroupRequests.GroupMoneyRequest;
import com.posapp.hotel.dto.GroupRequests.GroupRequest;
import com.posapp.hotel.dto.GroupRequests.NamesRequest;
import com.posapp.hotel.dto.GroupRequests.RoomingUpdateRequest;
import com.posapp.hotel.dto.HotelDtos.BookingDto;
import com.posapp.hotel.dto.HotelRequests.BookingRequest;
import com.posapp.hotel.dto.HotelRequests.BookingUpdateRequest;
import com.posapp.hotel.dto.HotelRequests.CancelRequest;
import com.posapp.hotel.dto.HotelRequests.GuestRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Group bookings: a named group with room blocks, a rooming list of ordinary bookings, and an optional master account. The rooms are
 * normal bookings (so check-in, billing and housekeeping work as usual); the group adds the holding of rooms and the shared account.
 */
@Service
public class GroupService {

    private static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
    private static final int MAX_ROOMS_PER_CALL = 100;

    private final HotelGroupRepository groupRepository;
    private final GroupBlockRepository blockRepository;
    private final GroupEntryRepository entryRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final HotelFolioService folioService;
    private final GuestService guestService;
    private final GroupLedger ledger;
    private final RoomTypeRepository roomTypeRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public GroupService(HotelGroupRepository groupRepository, GroupBlockRepository blockRepository, GroupEntryRepository entryRepository,
                        BookingRepository bookingRepository, BookingService bookingService, HotelFolioService folioService,
                        GuestService guestService, GroupLedger ledger, RoomTypeRepository roomTypeRepository,
                        CustomerRepository customerRepository, AuditLogService auditLogService, HotelClock clock) {
        this.groupRepository = groupRepository;
        this.blockRepository = blockRepository;
        this.entryRepository = entryRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.folioService = folioService;
        this.guestService = guestService;
        this.ledger = ledger;
        this.roomTypeRepository = roomTypeRepository;
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- read

    /** Groups, newest arrival first. status: TENTATIVE, CONFIRMED, CANCELLED, COMPLETED, or ACTIVE (tentative + confirmed). */
    @Transactional(readOnly = true)
    public List<GroupSummaryDto> list(String q, String status) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<GroupSummaryDto> out = new ArrayList<>();
        List<HotelGroup> all = groupRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("arrivalDate"), org.springframework.data.domain.Sort.Order.desc("id")));
        for (HotelGroup g : all) {
            if (!st.isEmpty()) {
                boolean match = "ACTIVE".equals(st) ? g.getStatus() == GroupStatus.TENTATIVE || g.getStatus() == GroupStatus.CONFIRMED
                        : g.getStatus().name().equals(st);
                if (!match) continue;
            }
            if (!needle.isEmpty() && !(low(g.getGroupCode()).contains(needle) || low(g.getName()).contains(needle) || low(g.getContactName()).contains(needle)
                    || low(g.getContactEmail()).contains(needle) || low(g.getContactPhone()).contains(needle))) continue;
            out.add(summary(g));
        }
        return out;
    }

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public GroupDetailDto get(Long id) {
        return detail(getOrThrow(id));
    }

    HotelGroup getOrThrow(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> ApiException.notFound("Group not found: " + id));
    }

    private GroupSummaryDto summary(HotelGroup g) {
        List<Booking> members = bookingRepository.findByGroupIdOrderByIdAsc(g.getId());
        GroupLedger.Totals totals = ledger.totals(g.getId());
        LocalDate today = clock.today();
        int blocked = 0;
        for (GroupBlock b : blockRepository.findByGroupIdOrderByStartDateAscIdAsc(g.getId())) if (holding(b, today)) blocked += b.getRooms();
        return new GroupSummaryDto(g.getId(), g.getGroupCode(), g.getName(), g.getStatus().name(), g.getBilling().name(), g.getArrivalDate(),
                g.getDepartureDate(), g.getCompany() == null ? null : g.getCompany().getName(), g.getContactName(), blocked,
                (int) members.stream().filter(m -> LIVE.contains(m.getStatus())).count(),
                (int) members.stream().filter(m -> m.getStatus() == BookingStatus.CHECKED_IN).count(), totals.owed(), totals.held());
    }

    private boolean holding(GroupBlock b, LocalDate today) {
        if (b.isReleased()) return false;
        if (b.getReleaseDate() != null && !today.isBefore(b.getReleaseDate())) return false;
        GroupStatus s = b.getGroup().getStatus();
        return s == GroupStatus.TENTATIVE || s == GroupStatus.CONFIRMED;
    }

    private GroupDetailDto detail(HotelGroup g) {
        LocalDate today = clock.today();
        List<Booking> members = bookingRepository.findByGroupIdOrderByIdAsc(g.getId());
        List<GroupBlockDto> blocks = new ArrayList<>();
        int blocked = 0;
        for (GroupBlock b : blockRepository.findByGroupIdOrderByStartDateAscIdAsc(g.getId())) {
            int booked = (int) members.stream().filter(m -> LIVE.contains(m.getStatus()) && effectiveType(m).equals(b.getRoomType().getId())).count();
            boolean holds = holding(b, today);
            if (holds) blocked += b.getRooms();
            blocks.add(new GroupBlockDto(b.getId(), b.getRoomType().getId(), b.getRoomType().getName(), b.getRooms(), b.getRate(), b.getStartDate(),
                    b.getEndDate(), b.getReleaseDate(), b.isReleased(), holds, booked, Math.max(0, b.getRooms() - booked)));
        }
        List<RoomingDto> rooming = new ArrayList<>();
        Long placeholder = g.getContactGuest() == null ? null : g.getContactGuest().getId();
        for (Booking m : members) {
            BookingDto dto = folioService.toDto(m);
            rooming.add(new RoomingDto(m.getId(), m.getBookingNumber(), m.getGuest().getId(), m.getGuest().getFullName(), m.getGuest().getPhone(),
                    m.getGuest().getId().equals(placeholder), m.getRoomType().getId(), m.getRoomType().getName(), dto.roomId(), dto.roomNumber(),
                    m.getCheckInDate(), m.getCheckOutDate(), m.getAdults(), m.getChildren(), m.getStatus().name(), dto.chargesTotal(), dto.paidTotal(),
                    billTo(m)));
        }
        List<GroupEntryDto> entries = new ArrayList<>();
        List<GroupEntry> rows = entryRepository.findByGroupIdOrderByIdAsc(g.getId());
        for (GroupEntry e : rows) {
            entries.add(new GroupEntryDto(e.getId(), e.getKind().name(), e.getBooking() == null ? null : e.getBooking().getId(),
                    e.getBooking() == null ? null : e.getBooking().getBookingNumber(), e.getBillNo(), e.getDescription(), e.getAmount(), e.getMethod(),
                    e.getReference(), e.getEntryDate()));
        }
        GroupLedger.Totals t = GroupLedger.totals(rows);
        Customer c = g.getCompany();
        return new GroupDetailDto(g.getId(), g.getGroupCode(), g.getName(), g.getContactName(), g.getContactPhone(), g.getContactEmail(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), g.getArrivalDate(), g.getDepartureDate(), g.getStatus().name(),
                g.getBilling().name(), g.getNotes(), blocked, (int) members.stream().filter(m -> LIVE.contains(m.getStatus())).count(),
                (int) members.stream().filter(m -> m.getStatus() == BookingStatus.CHECKED_IN).count(), blocks, rooming, entries,
                new GroupTotalsDto(t.billed(), t.deposits(), t.applied(), t.paid(), t.refunded(), t.owed(), t.held()));
    }

    private static Long effectiveType(Booking m) {
        return m.getRoom() != null ? m.getRoom().getRoomType().getId() : m.getRoomType().getId();
    }

    /** "Group" when the room's rooms are on the group's account, "Group (rooms)" when only the room nights are, otherwise the guest. */
    private String billTo(Booking m) {
        Set<Integer> groups = folioService.groupBills(m);
        if (groups.isEmpty()) return "Guest";
        return groups.contains(m.getRoomBillNo()) && groups.size() == folioService.billNumbers(m).size() ? "Group" : "Group (rooms)";
    }

    // ---------------------------------------------------------------- groups

    @Transactional
    public GroupDetailDto create(GroupRequest request) {
        validateDates(request.arrivalDate(), request.departureDate(), true);
        HotelGroup g = new HotelGroup();
        apply(g, request);
        g.setStatus(GroupStatus.TENTATIVE);
        String contact = clean(request.contactName());
        g.setContactGuest(guestService.resolve(null, new GuestRequest(contact != null ? contact : g.getName() + " (group)", clean(request.contactPhone()),
                clean(request.contactEmail()), null, null, null, null, "Group contact")));
        HotelGroup saved = groupRepository.save(g);
        saved.setGroupCode(String.format("GR-%05d", saved.getId()));
        groupRepository.save(saved);
        auditLogService.record("HOTEL_GROUP_CREATED", saved.getGroupCode() + " " + saved.getName(), "HotelGroup", saved.getId());
        return detail(saved);
    }

    @Transactional
    public GroupDetailDto update(Long id, GroupRequest request) {
        HotelGroup g = getOrThrow(id);
        if (g.getStatus() == GroupStatus.CANCELLED || g.getStatus() == GroupStatus.COMPLETED) {
            throw ApiException.badRequest("A " + g.getStatus().name().toLowerCase(Locale.ROOT) + " group can no longer be changed");
        }
        validateDates(request.arrivalDate(), request.departureDate(), false);
        List<Booking> members = bookingRepository.findByGroupIdOrderByIdAsc(id);
        for (Booking m : members) {
            if (!LIVE.contains(m.getStatus())) continue;
            if (m.getCheckInDate().isBefore(request.arrivalDate()) || m.getCheckOutDate().isAfter(request.departureDate())) {
                throw ApiException.badRequest("Room " + m.getBookingNumber() + " stays " + m.getCheckInDate() + " to " + m.getCheckOutDate()
                        + " — the group dates must cover it");
            }
        }
        for (GroupBlock b : blockRepository.findByGroupIdOrderByStartDateAscIdAsc(id)) {
            if (b.getStartDate().isBefore(request.arrivalDate()) || b.getEndDate().isAfter(request.departureDate())) {
                throw ApiException.badRequest("A block of " + b.getRoomType().getName() + " runs " + b.getStartDate() + " to " + b.getEndDate()
                        + " — change or remove it before shortening the group");
            }
        }
        GroupBilling before = g.getBilling();
        apply(g, request);
        if (g.getBilling() != before) changeBilling(g, members);
        if (g.getContactGuest() != null && clean(request.contactName()) != null) {
            // the placeholder guest follows the contact's name until a real contact guest is chosen
            Guest cg = g.getContactGuest();
            cg.setFullName(request.contactName().trim());
            cg.setPhone(clean(request.contactPhone()));
            cg.setEmail(clean(request.contactEmail()));
        }
        groupRepository.save(g);
        auditLogService.record("HOTEL_GROUP_CHANGED", g.getGroupCode() + " " + g.getName(), "HotelGroup", g.getId());
        return detail(g);
    }

    private void changeBilling(HotelGroup g, List<Booking> members) {
        for (Booking m : members) {
            if (m.getStatus() == BookingStatus.CHECKED_IN || entryRepository.findByGroupIdOrderByIdAsc(g.getId()).stream()
                    .anyMatch(e -> e.getKind() == GroupEntryKind.BILL && e.getBooking() != null && e.getBooking().getId().equals(m.getId()))) {
                throw ApiException.badRequest("Billing cannot be changed once guests have checked in or been billed — it applies to the rooms still to arrive");
            }
        }
        for (Booking m : members) {
            if (m.getStatus() != BookingStatus.BOOKED) continue;
            folioService.clearGroupBills(m);
            folioService.setupGroupBills(m, g.getBilling());
        }
    }

    private void apply(HotelGroup g, GroupRequest r) {
        g.setName(r.name().trim());
        g.setContactName(clean(r.contactName()));
        g.setContactPhone(clean(r.contactPhone()));
        g.setContactEmail(clean(r.contactEmail()));
        g.setArrivalDate(r.arrivalDate());
        g.setDepartureDate(r.departureDate());
        g.setNotes(clean(r.notes()));
        g.setCompany(r.companyId() == null ? null : customerRepository.findById(r.companyId())
                .orElseThrow(() -> ApiException.badRequest("Customer not found")));
        g.setBilling(parseBilling(r.billing(), g.getBilling()));
    }

    private static GroupBilling parseBilling(String value, GroupBilling fallback) {
        if (value == null || value.isBlank()) return fallback == null ? GroupBilling.INDIVIDUAL : fallback;
        try {
            return GroupBilling.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Billing must be INDIVIDUAL, MASTER_ROOMS or MASTER_ALL");
        }
    }

    private void validateDates(LocalDate arrival, LocalDate departure, boolean creating) {
        if (!departure.isAfter(arrival)) throw ApiException.badRequest("The departure must be after the arrival");
        if (creating && arrival.isBefore(clock.today())) throw ApiException.badRequest("The arrival cannot be in the past");
        if (ChronoUnit.DAYS.between(arrival, departure) > 90) throw ApiException.badRequest("A group stay can be at most 90 nights");
    }

    @Transactional
    public GroupDetailDto confirm(Long id) {
        HotelGroup g = getOrThrow(id);
        if (g.getStatus() != GroupStatus.TENTATIVE) throw ApiException.badRequest("Only a tentative group can be confirmed");
        g.setStatus(GroupStatus.CONFIRMED);
        groupRepository.save(g);
        auditLogService.record("HOTEL_GROUP_CONFIRMED", g.getGroupCode(), "HotelGroup", g.getId());
        return detail(g);
    }

    /** Cancels the group and every room that has not arrived. Refused while anyone is in the house. */
    @Transactional
    public GroupDetailDto cancel(Long id, GroupCancelRequest request) {
        HotelGroup g = getOrThrow(id);
        if (g.getStatus() == GroupStatus.CANCELLED || g.getStatus() == GroupStatus.COMPLETED) {
            throw ApiException.badRequest("This group is already " + g.getStatus().name().toLowerCase(Locale.ROOT));
        }
        List<Booking> members = bookingRepository.findByGroupIdOrderByIdAsc(id);
        if (members.stream().anyMatch(m -> m.getStatus() == BookingStatus.CHECKED_IN)) {
            throw ApiException.badRequest("Some of the group are checked in — check them out first, then cancel the rooms that remain");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank() ? "Group cancelled" : request.reason().trim();
        boolean waive = request != null && Boolean.TRUE.equals(request.waiveFees());
        for (Booking m : members) {
            if (m.getStatus() == BookingStatus.BOOKED) bookingService.cancel(m.getId(), new CancelRequest(reason, "REFUND", null, waive));
        }
        g.setStatus(GroupStatus.CANCELLED);
        groupRepository.save(g);
        auditLogService.record("HOTEL_GROUP_CANCELLED", g.getGroupCode() + ": " + reason, "HotelGroup", g.getId());
        return detail(g);
    }

    /** Closes a finished group: nobody left in the house or still to arrive, and the account settled. */
    @Transactional
    public GroupDetailDto complete(Long id) {
        HotelGroup g = getOrThrow(id);
        if (g.getStatus() != GroupStatus.CONFIRMED && g.getStatus() != GroupStatus.TENTATIVE) {
            throw ApiException.badRequest("Only an open group can be completed");
        }
        if (bookingRepository.findByGroupIdOrderByIdAsc(id).stream().anyMatch(m -> LIVE.contains(m.getStatus()))) {
            throw ApiException.badRequest("Some rooms of the group are still booked or in the house");
        }
        GroupLedger.Totals t = ledger.totals(id);
        if (t.owed().signum() != 0) throw ApiException.badRequest("The group still owes " + t.owed().setScale(2) + " — take the payment first");
        if (t.held().signum() != 0) throw ApiException.badRequest("The group has " + t.held().setScale(2) + " of deposit unused — refund it first");
        g.setStatus(GroupStatus.COMPLETED);
        groupRepository.save(g);
        auditLogService.record("HOTEL_GROUP_COMPLETED", g.getGroupCode(), "HotelGroup", g.getId());
        return detail(g);
    }

    // ---------------------------------------------------------------- blocks

    @Transactional
    public GroupDetailDto addBlock(Long groupId, BlockRequest request) {
        HotelGroup g = openGroup(groupId);
        GroupBlock b = new GroupBlock();
        b.setGroup(g);
        fillBlock(b, g, request, null);
        blockRepository.save(b);
        auditLogService.record("HOTEL_GROUP_BLOCK", g.getGroupCode() + ": " + b.getRooms() + " x " + b.getRoomType().getName() + " " + b.getStartDate() + " to " + b.getEndDate(),
                "HotelGroup", g.getId());
        return detail(g);
    }

    @Transactional
    public GroupDetailDto updateBlock(Long groupId, Long blockId, BlockRequest request) {
        HotelGroup g = openGroup(groupId);
        GroupBlock b = blockOf(g, blockId);
        fillBlock(b, g, request, b.getId());
        blockRepository.save(b);
        auditLogService.record("HOTEL_GROUP_BLOCK_CHANGED", g.getGroupCode() + ": " + b.getRooms() + " x " + b.getRoomType().getName(), "HotelGroup", g.getId());
        return detail(g);
    }

    @Transactional
    public GroupDetailDto deleteBlock(Long groupId, Long blockId) {
        HotelGroup g = getOrThrow(groupId);
        GroupBlock b = blockOf(g, blockId);
        blockRepository.delete(b);
        auditLogService.record("HOTEL_GROUP_BLOCK_REMOVED", g.getGroupCode() + ": " + b.getRoomType().getName(), "HotelGroup", g.getId());
        return detail(g);
    }

    /** Gives the unbooked rooms of a block back to general sale now, instead of waiting for the release date. */
    @Transactional
    public GroupDetailDto releaseBlock(Long groupId, Long blockId) {
        HotelGroup g = getOrThrow(groupId);
        GroupBlock b = blockOf(g, blockId);
        b.setReleased(true);
        blockRepository.save(b);
        auditLogService.record("HOTEL_GROUP_BLOCK_RELEASED", g.getGroupCode() + ": " + b.getRoomType().getName(), "HotelGroup", g.getId());
        return detail(g);
    }

    private GroupBlock blockOf(HotelGroup g, Long blockId) {
        GroupBlock b = blockRepository.findById(blockId).orElseThrow(() -> ApiException.notFound("Block not found: " + blockId));
        if (!b.getGroup().getId().equals(g.getId())) throw ApiException.notFound("Block not found: " + blockId);
        return b;
    }

    private void fillBlock(GroupBlock b, HotelGroup g, BlockRequest r, Long excludeId) {
        RoomType type = roomTypeRepository.findById(r.roomTypeId()).filter(RoomType::isActive)
                .orElseThrow(() -> ApiException.badRequest("Room type not found"));
        int rooms = r.rooms();
        if (rooms < 1 || rooms > 500) throw ApiException.badRequest("Rooms must be between 1 and 500");
        LocalDate from = r.startDate() != null ? r.startDate() : g.getArrivalDate();
        LocalDate to = r.endDate() != null ? r.endDate() : g.getDepartureDate();
        if (!to.isAfter(from)) throw ApiException.badRequest("The block must end after it starts");
        if (from.isBefore(g.getArrivalDate()) || to.isAfter(g.getDepartureDate())) {
            throw ApiException.badRequest("The block must lie within the group's dates (" + g.getArrivalDate() + " to " + g.getDepartureDate() + ")");
        }
        if (r.releaseDate() != null && r.releaseDate().isAfter(from)) {
            throw ApiException.badRequest("The release date must be on or before the block starts");
        }
        if (r.rate() != null && r.rate().signum() < 0) throw ApiException.badRequest("The rate cannot be negative");
        bookingService.ensureBlockFits(type, from, to, rooms, g, excludeId, r.releaseDate());
        b.setRoomType(type);
        b.setRooms(rooms);
        b.setRate(r.rate() == null ? null : r.rate().setScale(2, java.math.RoundingMode.HALF_UP));
        b.setStartDate(from);
        b.setEndDate(to);
        b.setReleaseDate(r.releaseDate());
    }

    // ---------------------------------------------------------------- rooming list

    /** Books rooms for the group: one booking each, under the group's contact (or the names given), on the block's rate when there is one. */
    @Transactional
    public GroupDetailDto addRooms(Long groupId, AddRoomsRequest request) {
        HotelGroup g = openGroup(groupId);
        List<String> names = new ArrayList<>();
        if (request.names() != null) for (String n : request.names()) if (clean(n) != null) names.add(n.trim());
        int count = !names.isEmpty() ? names.size() : request.count() == null ? 1 : request.count();
        if (count < 1 || count > MAX_ROOMS_PER_CALL) throw ApiException.badRequest("Add between 1 and " + MAX_ROOMS_PER_CALL + " rooms at a time");
        LocalDate from = request.checkInDate() != null ? request.checkInDate() : g.getArrivalDate();
        LocalDate to = request.checkOutDate() != null ? request.checkOutDate() : g.getDepartureDate();
        if (from.isBefore(g.getArrivalDate()) || to.isAfter(g.getDepartureDate())) {
            throw ApiException.badRequest("The stay must lie within the group's dates (" + g.getArrivalDate() + " to " + g.getDepartureDate() + ")");
        }
        BigDecimal rate = request.nightlyRate() != null ? request.nightlyRate() : blockRate(g, request.roomTypeId(), from, to);
        for (int i = 0; i < count; i++) {
            BookingRequest br = new BookingRequest(names.isEmpty() ? g.getContactGuest().getId() : null,
                    names.isEmpty() ? null : new GuestRequest(names.get(i), null, null, null, null, null, null, null),
                    request.roomTypeId(), null, from, to, request.adults(), request.children(), rate, "Group", "Group " + g.getGroupCode(), null, null,
                    request.ratePlanId());
            BookingDto created = bookingService.create(br, g);
            folioService.setupGroupBills(folioService.getBooking(created.id()), g.getBilling());
        }
        auditLogService.record("HOTEL_GROUP_ROOMS", g.getGroupCode() + ": " + count + " room(s) added", "HotelGroup", g.getId());
        return detail(g);
    }

    private BigDecimal blockRate(HotelGroup g, Long typeId, LocalDate from, LocalDate to) {
        for (GroupBlock b : blockRepository.findByGroupIdOrderByStartDateAscIdAsc(g.getId())) {
            if (b.getRoomType().getId().equals(typeId) && b.getRate() != null && !b.getStartDate().isAfter(from) && !b.getEndDate().isBefore(to)) return b.getRate();
        }
        return null;
    }

    @Transactional
    public GroupDetailDto updateRoom(Long groupId, Long bookingId, RoomingUpdateRequest request) {
        HotelGroup g = getOrThrow(groupId);
        Booking m = memberOf(g, bookingId);
        if (!LIVE.contains(m.getStatus())) throw ApiException.badRequest("This room can no longer be changed");
        if (request.guestId() != null || request.guest() != null) {
            m.setGuest(guestService.resolve(request.guestId(), request.guest()));
            bookingRepository.save(m);
        }
        if (request.adults() != null || request.children() != null || request.roomId() != null) {
            bookingService.update(bookingId, new BookingUpdateRequest(null, request.roomId(), null, null, request.adults(), request.children(),
                    null, null, null, null, null));
        }
        auditLogService.record("HOTEL_GROUP_ROOMING", g.getGroupCode() + ": " + m.getBookingNumber() + " updated", "HotelGroup", g.getId());
        return detail(g);
    }

    /** Puts names, one per line, on the rooms that are still booked under the group's contact, in booking order. */
    @Transactional
    public NamesResultDto assignNames(Long groupId, NamesRequest request) {
        HotelGroup g = getOrThrow(groupId);
        List<String> names = new ArrayList<>();
        if (request != null && request.names() != null) for (String n : request.names()) if (clean(n) != null) names.add(n.trim());
        if (names.isEmpty()) throw ApiException.badRequest("Enter at least one name");
        Long placeholder = g.getContactGuest() == null ? null : g.getContactGuest().getId();
        int assigned = 0;
        for (Booking m : bookingRepository.findByGroupIdOrderByIdAsc(groupId)) {
            if (assigned >= names.size()) break;
            if (m.getStatus() != BookingStatus.BOOKED || !m.getGuest().getId().equals(placeholder)) continue;
            m.setGuest(guestService.resolve(null, new GuestRequest(names.get(assigned), null, null, null, null, null, null, null)));
            bookingRepository.save(m);
            assigned++;
        }
        auditLogService.record("HOTEL_GROUP_ROOMING", g.getGroupCode() + ": " + assigned + " name(s) assigned", "HotelGroup", g.getId());
        return new NamesResultDto(assigned, names.size() - assigned, detail(g));
    }

    private Booking memberOf(HotelGroup g, Long bookingId) {
        Booking m = folioService.getBooking(bookingId);
        if (m.getGroup() == null || !m.getGroup().getId().equals(g.getId())) throw ApiException.notFound("That booking is not part of this group");
        return m;
    }

    private HotelGroup openGroup(Long id) {
        HotelGroup g = getOrThrow(id);
        if (g.getStatus() != GroupStatus.TENTATIVE && g.getStatus() != GroupStatus.CONFIRMED) {
            throw ApiException.badRequest("A " + g.getStatus().name().toLowerCase(Locale.ROOT) + " group can no longer be changed");
        }
        return g;
    }

    // ---------------------------------------------------------------- money

    @Transactional
    public GroupDetailDto deposit(Long id, GroupMoneyRequest r) {
        HotelGroup g = getOrThrow(id);
        ledger.deposit(g, r.method(), r.amount(), r.reference(), r.note());
        return detail(g);
    }

    @Transactional
    public GroupDetailDto payment(Long id, GroupMoneyRequest r) {
        HotelGroup g = getOrThrow(id);
        ledger.payment(g, r.method(), r.amount(), r.reference(), r.note());
        return detail(g);
    }

    @Transactional
    public GroupDetailDto refund(Long id, GroupMoneyRequest r) {
        HotelGroup g = getOrThrow(id);
        ledger.refund(g, r.method(), r.amount(), r.note());
        return detail(g);
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
