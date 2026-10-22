package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.GuestDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** The guest register: a guest's history and spend, duplicate detection and merging, and the police / immigration register. */
@Service
public class GuestProfileService {

    private static final int MAX_STAYS_SHOWN = 100;
    private static final int MAX_REGISTER_ROWS = 5000;

    private final GuestService guestService;
    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final FolioChargeRepository chargeRepository;
    private final HotelGroupRepository groupRepository;
    private final LostFoundItemRepository lostFoundRepository;
    private final GuestDocumentService documentService;
    private final GuestDocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public GuestProfileService(GuestService guestService, GuestRepository guestRepository, BookingRepository bookingRepository, FolioChargeRepository chargeRepository,
                               HotelGroupRepository groupRepository, LostFoundItemRepository lostFoundRepository, GuestDocumentService documentService,
                               GuestDocumentRepository documentRepository, AuditLogService auditLogService, HotelClock clock) {
        this.guestService = guestService;
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
        this.groupRepository = groupRepository;
        this.lostFoundRepository = lostFoundRepository;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- history

    private Map<Long, BigDecimal> totals(Collection<Long> bookingIds) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (bookingIds.isEmpty()) return out;
        for (Object[] row : chargeRepository.totalsByBooking(bookingIds)) out.put((Long) row[0], (BigDecimal) row[1]);
        return out;
    }

    private static boolean counts(Booking b) {
        return b.getStatus() == BookingStatus.CHECKED_OUT || b.getStatus() == BookingStatus.CHECKED_IN;
    }

    /** What the guest has spent: charges (tax included, voided lines left out) on stays that have been lived or are in the house. */
    @Transactional(readOnly = true)
    public GuestProfileDto profile(Long id) {
        Guest guest = guestService.getOrThrow(id);
        List<Booking> bookings = bookingRepository.findByGuestIdOrderByCheckInDateDescIdDesc(id);
        Map<Long, BigDecimal> totals = totals(bookings.stream().map(Booking::getId).toList());
        long stays = 0, nights = 0, upcoming = 0, inHouse = 0, cancelled = 0, noShows = 0;
        BigDecimal spend = BigDecimal.ZERO;
        LocalDate first = null, last = null;
        Map<String, Long> types = new HashMap<>(), sources = new HashMap<>();
        List<StayDto> shown = new ArrayList<>();
        for (Booking b : bookings) {
            BigDecimal total = totals.getOrDefault(b.getId(), BigDecimal.ZERO);
            switch (b.getStatus()) {
                case BOOKED -> upcoming++;
                case CANCELLED -> cancelled++;
                case NO_SHOW -> noShows++;
                case CHECKED_IN -> inHouse++;
                default -> {
                }
            }
            if (counts(b)) {
                stays++;
                nights += b.plannedNights();
                spend = spend.add(total);
                if (first == null || b.getCheckInDate().isBefore(first)) first = b.getCheckInDate();
                if (last == null || b.getCheckInDate().isAfter(last)) last = b.getCheckInDate();
                types.merge(b.getRoomType().getName(), 1L, Long::sum);
                if (b.getSource() != null && !b.getSource().isBlank()) sources.merge(b.getSource(), 1L, Long::sum);
            }
            if (shown.size() < MAX_STAYS_SHOWN) {
                shown.add(new StayDto(b.getId(), b.getBookingNumber(), b.getCheckInDate(), b.getCheckOutDate(), b.plannedNights(), b.getStatus().name(),
                        b.getRoom() == null ? null : b.getRoom().getNumber(), b.getRoomType().getName(), b.getSource(), total));
            }
        }
        BigDecimal avg = stays == 0 ? BigDecimal.ZERO : spend.divide(BigDecimal.valueOf(stays), 2, RoundingMode.HALF_UP);
        BigDecimal avgNight = nights == 0 ? BigDecimal.ZERO : spend.divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP);
        GuestStatsDto stats = new GuestStatsDto(stays, nights, spend.setScale(2, RoundingMode.HALF_UP), avg, avgNight, first, last, upcoming, inHouse, cancelled, noShows,
                top(types), top(sources));
        return new GuestProfileDto(guestService.toDto(guest), stats, shown, documentService.list(id));
    }

    private static String top(Map<String, Long> counts) {
        return counts.entrySet().stream().max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder()))).map(Map.Entry::getKey).orElse(null);
    }

    // ---------------------------------------------------------------- duplicates

    private static String digits(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("\\D", "");
        return d.length() < 6 ? null : d;
    }

    private static String low(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Guests who share an ID number, a phone number, an e-mail address or a name; each cluster lists what they have in common. */
    @Transactional(readOnly = true)
    public List<DuplicateClusterDto> duplicates() {
        List<Guest> all = guestRepository.findAllByOrderByIdAsc();
        int n = all.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        Map<String, List<Integer>> byKey = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Guest g = all.get(i);
            String id = low(g.getIdNumber()), ph = digits(g.getPhone()), em = low(g.getEmail()), nm = low(g.getFullName());
            if (id != null) byKey.computeIfAbsent("ID number|" + id, k -> new ArrayList<>()).add(i);
            if (ph != null) byKey.computeIfAbsent("phone|" + ph, k -> new ArrayList<>()).add(i);
            if (em != null) byKey.computeIfAbsent("e-mail|" + em, k -> new ArrayList<>()).add(i);
            if (nm != null) byKey.computeIfAbsent("name|" + nm, k -> new ArrayList<>()).add(i);
        }
        Map<Integer, Set<String>> why = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : byKey.entrySet()) {
            if (e.getValue().size() < 2) continue;
            int root = find(parent, e.getValue().get(0));
            for (int idx : e.getValue()) parent[find(parent, idx)] = root;
        }
        for (Map.Entry<String, List<Integer>> e : byKey.entrySet()) {
            if (e.getValue().size() < 2) continue;
            int root = find(parent, e.getValue().get(0));
            why.computeIfAbsent(root, k -> new TreeSet<>()).add("same " + e.getKey().substring(0, e.getKey().indexOf('|')));
        }
        Map<Integer, List<Guest>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            if (why.containsKey(root)) clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(all.get(i));
        }
        List<Long> ids = clusters.values().stream().flatMap(List::stream).map(Guest::getId).toList();
        Map<Long, List<Booking>> bookingsByGuest = new HashMap<>();
        for (Long gid : ids) bookingsByGuest.put(gid, bookingRepository.findByGuestIdOrderByCheckInDateDescIdDesc(gid));
        Map<Long, BigDecimal> totals = totals(bookingsByGuest.values().stream().flatMap(List::stream).map(Booking::getId).toList());
        List<DuplicateClusterDto> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Guest>> c : clusters.entrySet()) {
            List<DuplicateGuestDto> members = new ArrayList<>();
            for (Guest g : c.getValue()) {
                List<Booking> bs = bookingsByGuest.getOrDefault(g.getId(), List.of());
                BigDecimal spend = bs.stream().filter(GuestProfileService::counts).map(b -> totals.getOrDefault(b.getId(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
                members.add(new DuplicateGuestDto(g.getId(), g.getFullName(), g.getPhone(), g.getEmail(), g.getIdNumber(), bs.size(), spend, g.isVip(), g.isBlacklisted()));
            }
            members.sort(Comparator.comparingLong(DuplicateGuestDto::stays).reversed().thenComparing(DuplicateGuestDto::id));
            out.add(new DuplicateClusterDto(new ArrayList<>(why.get(c.getKey())), members));
        }
        out.sort(Comparator.comparing((DuplicateClusterDto d) -> d.guests().size()).reversed().thenComparing(d -> d.guests().get(0).fullName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ---------------------------------------------------------------- merge

    private static String keep(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** Moves everything of {@code duplicateId} to {@code keeperId} (bookings, groups, lost & found, ID documents), fills gaps in the keeper's details, then removes the duplicate. */
    @Transactional
    public MergeResultDto merge(Long keeperId, Long duplicateId) {
        if (duplicateId == null) throw ApiException.badRequest("Choose the guest to merge");
        if (keeperId.equals(duplicateId)) throw ApiException.badRequest("A guest cannot be merged into themselves");
        Guest keeper = guestService.getOrThrow(keeperId);
        Guest dup = guestService.getOrThrow(duplicateId);
        keeper.setPhone(keep(keeper.getPhone(), dup.getPhone()));
        keeper.setEmail(keep(keeper.getEmail(), dup.getEmail()));
        keeper.setNationality(keep(keeper.getNationality(), dup.getNationality()));
        keeper.setIdType(keeper.getIdNumber() != null && !keeper.getIdNumber().isBlank() ? keeper.getIdType() : keep(keeper.getIdType(), dup.getIdType()));
        keeper.setIdNumber(keep(keeper.getIdNumber(), dup.getIdNumber()));
        keeper.setAddress(keep(keeper.getAddress(), dup.getAddress()));
        keeper.setProfession(keep(keeper.getProfession(), dup.getProfession()));
        if (keeper.getDateOfBirth() == null) keeper.setDateOfBirth(dup.getDateOfBirth());
        keeper.setNotes(join(keeper.getNotes(), dup.getNotes()));
        keeper.setPreferences(join(keeper.getPreferences(), dup.getPreferences()));
        keeper.setVip(keeper.isVip() || dup.isVip());
        if (dup.isBlacklisted() && !keeper.isBlacklisted()) {
            keeper.setBlacklisted(true);
            keeper.setBlacklistReason(dup.getBlacklistReason());
        }
        guestRepository.save(keeper);
        long bookings = bookingRepository.moveGuest(duplicateId, keeper);
        long groups = groupRepository.moveContact(duplicateId, keeper);
        long found = lostFoundRepository.moveGuest(duplicateId, keeper);
        long docs = documentRepository.moveGuest(duplicateId, keeperId);
        guestRepository.deleteById(duplicateId);
        guestRepository.flush();
        auditLogService.record("GUEST_MERGED", "Merged guest " + dup.getFullName() + " (#" + duplicateId + ") into " + keeper.getFullName() + " (#" + keeperId + "): "
                + bookings + " booking(s), " + docs + " document(s) moved", "Guest", keeperId);
        return new MergeResultDto(guestService.toDto(guestService.getOrThrow(keeperId)), bookings, docs, groups, found);
    }

    private static String join(String a, String b) {
        if (b == null || b.isBlank()) return a;
        if (a == null || a.isBlank()) return b;
        return a.trim().equalsIgnoreCase(b.trim()) || a.toLowerCase(Locale.ROOT).contains(b.trim().toLowerCase(Locale.ROOT)) ? a : a.trim() + "\n" + b.trim();
    }

    // ---------------------------------------------------------------- police / immigration register

    /**
     * Everyone who was in the house on any night from {@code from} to {@code to} (arrival date through the night before departure, or
     * the actual departure for those who left early), one line per stay, ordered by arrival then room.
     */
    @Transactional(readOnly = true)
    public List<RegisterRowDto> register(LocalDate from, LocalDate to, String q, String nationality) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end : from;
        if (end.isBefore(start)) throw ApiException.badRequest("The end date is before the start date");
        if (start.plusDays(366).isBefore(end)) throw ApiException.badRequest("Choose a period of at most one year");
        List<Booking> all = bookingRepository.findTouching(start, end, List.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT));
        String needle = low(q);
        String nat = low(nationality);
        List<RegisterRowDto> rows = new ArrayList<>();
        for (Booking b : all) {
            // Only stays that were really lived: the guest must have arrived, and the night count must reach into the period.
            LocalDate arrival = b.getActualCheckIn() == null ? b.getCheckInDate() : b.getActualCheckIn().atZone(clock.zone()).toLocalDate();
            LocalDate departure = b.getStatus() == BookingStatus.CHECKED_OUT && b.getActualCheckOut() != null ? b.getActualCheckOut().atZone(clock.zone()).toLocalDate() : b.getCheckOutDate();
            boolean inPeriod = !arrival.isAfter(end) && (departure.isAfter(start) || (departure.equals(arrival) && !arrival.isBefore(start)));
            if (!inPeriod) continue;
            Guest g = b.getGuest();
            if (nat != null && !nat.equals(low(g.getNationality()))) continue;
            if (needle != null) {
                String hay = (g.getFullName() + " " + nz(g.getIdNumber()) + " " + nz(g.getNationality()) + " " + nz(g.getPhone()) + " " + b.getBookingNumber() + " " + (b.getRoom() == null ? "" : b.getRoom().getNumber())).toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            rows.add(new RegisterRowDto(b.getId(), b.getBookingNumber(), g.getId(), g.getFullName(), g.getNationality(), g.getIdType(), g.getIdNumber(), g.getDateOfBirth(),
                    g.getAddress(), g.getProfession(), g.getPhone(), arrival, departure, b.getRoom() == null ? null : b.getRoom().getNumber(), b.getAdults(), b.getChildren(), b.getStatus().name()));
            if (rows.size() >= MAX_REGISTER_ROWS) break;
        }
        rows.sort(Comparator.comparing(RegisterRowDto::arrival).thenComparing(r -> r.roomNumber() == null ? "" : r.roomNumber()).thenComparing(RegisterRowDto::bookingId));
        return rows;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
