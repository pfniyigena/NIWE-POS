package com.posapp.hotel;

import com.posapp.accounting.AccountService;
import com.posapp.accounting.JournalEntryRepository;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.AvailabilityDto;
import com.posapp.hotel.dto.HotelDtos.DayAvailability;
import com.posapp.hotel.dto.HotelDtos.OccupancyReportDto;
import com.posapp.hotel.dto.HotelDtos.TypeAvailability;
import com.posapp.hotel.dto.ReportDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The manager's reports: the day report, revenue by booking source, the forecast with pick-up, and the night-audit report that checks the
 * hotel's own records against what Accounting holds. Reads only; nothing here changes a booking or posts anything.
 */
@Service
public class HotelInsightService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<BookingStatus> LIVE = List.of(BookingStatus.BOOKED, BookingStatus.CHECKED_IN);
    private static final String UNSPECIFIED = "Unspecified";

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingNightRepository nightRepository;
    private final FolioChargeRepository chargeRepository;
    private final FolioPaymentRepository paymentRepository;
    private final CompanyBillRepository companyBillRepository;
    private final CompanyReceiptRepository companyReceiptRepository;
    private final GroupEntryRepository groupEntryRepository;
    private final HotelReportService reportService;
    private final HotelFolioService folioService;
    private final HotelSettingsService settingsService;
    private final JournalEntryRepository journalRepository;
    private final HotelClock clock;

    public HotelInsightService(RoomRepository roomRepository, RoomService roomService, BookingRepository bookingRepository, BookingService bookingService,
                               BookingNightRepository nightRepository, FolioChargeRepository chargeRepository, FolioPaymentRepository paymentRepository,
                               CompanyBillRepository companyBillRepository, CompanyReceiptRepository companyReceiptRepository, GroupEntryRepository groupEntryRepository,
                               HotelReportService reportService, HotelFolioService folioService, HotelSettingsService settingsService,
                               JournalEntryRepository journalRepository, HotelClock clock) {
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.nightRepository = nightRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.companyBillRepository = companyBillRepository;
        this.companyReceiptRepository = companyReceiptRepository;
        this.groupEntryRepository = groupEntryRepository;
        this.reportService = reportService;
        this.folioService = folioService;
        this.settingsService = settingsService;
        this.journalRepository = journalRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- helpers

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(long part, long whole) {
        return whole <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal div(BigDecimal a, long b) {
        return b <= 0 ? BigDecimal.ZERO : a.divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(clock.zone()).toInstant();
    }

    private LocalDate dayOf(Instant at) {
        return at.atZone(clock.zone()).toLocalDate();
    }

    private PeriodDto period(String label, LocalDate from, LocalDate to) {
        OccupancyReportDto o = reportService.occupancy(from, to);
        return new PeriodDto(label, from, to, o.roomNightsSold(), o.roomNightsAvailable(), o.occupancyPercent(), o.roomRevenue(), o.adr(), o.revPar());
    }

    private static String typeLabel(ChargeType t) {
        return switch (t) {
            case ROOM -> "Room nights";
            case EXTRA -> "Extras";
            case FEE -> "Fees";
            case RESTAURANT -> "Restaurant (to room)";
            case POS -> "Shop (to room)";
            case ROOM_SERVICE -> "Room service";
        };
    }

    private List<RevenueLineDto> revenueLines(List<FolioCharge> lines) {
        Map<ChargeType, BigDecimal[]> sums = new EnumMap<>(ChargeType.class);
        Map<ChargeType, long[]> counts = new EnumMap<>(ChargeType.class);
        for (FolioCharge c : lines) {
            BigDecimal[] s = sums.computeIfAbsent(c.getChargeType(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(nz(c.getNet()));
            s[1] = s[1].add(nz(c.getTax()));
            s[2] = s[2].add(nz(c.getAmount()));
            counts.computeIfAbsent(c.getChargeType(), k -> new long[1])[0]++;
        }
        List<RevenueLineDto> out = new ArrayList<>();
        for (ChargeType t : ChargeType.values()) {
            BigDecimal[] s = sums.get(t);
            if (s != null) out.add(new RevenueLineDto(typeLabel(t), counts.get(t)[0], money(s[0]), money(s[1]), money(s[2])));
        }
        return out;
    }

    private List<MoneyLineDto> moneyLines(List<FolioPayment> payments) {
        Map<String, BigDecimal[]> by = new TreeMap<>();
        for (FolioPayment p : payments) {
            BigDecimal[] s = by.computeIfAbsent(p.getMethod() == null ? "?" : p.getMethod(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            int i = p.getKind() == PaymentKind.DEPOSIT ? 0 : p.getKind() == PaymentKind.PAYMENT ? 1 : 2;
            s[i] = s[i].add(nz(p.getAmount()));
        }
        List<MoneyLineDto> out = new ArrayList<>();
        by.forEach((m, s) -> out.add(new MoneyLineDto(m, money(s[0]), money(s[1]), money(s[2]), money(s[0].add(s[1]).subtract(s[2])))));
        return out;
    }

    private static BigDecimal sumNet(List<MoneyLineDto> lines) {
        return lines.stream().map(MoneyLineDto::net).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- the day

    /** Rooms that held a guest on the night of {@code day}, from the real arrival and departure (or the planned departure for a guest still in the house). */
    private List<Booking> staysOnNight(LocalDate day) {
        Map<Long, Booking> found = new LinkedHashMap<>();
        for (Booking b : bookingRepository.findTouching(day, day, List.of(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT))) found.put(b.getId(), b);
        for (Booking b : bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN)) found.putIfAbsent(b.getId(), b);
        List<Booking> out = new ArrayList<>();
        for (Booking b : found.values()) {
            LocalDate arrival = b.getActualCheckIn() == null ? b.getCheckInDate() : dayOf(b.getActualCheckIn());
            LocalDate departure = b.getStatus() == BookingStatus.CHECKED_OUT && b.getActualCheckOut() != null ? dayOf(b.getActualCheckOut()) : b.getCheckOutDate();
            if (b.getStatus() == BookingStatus.CHECKED_IN && departure.isBefore(clock.today().plusDays(1))) departure = clock.today().plusDays(1);
            if (!arrival.isAfter(day) && departure.isAfter(day)) out.add(b);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DailyReportDto daily(LocalDate date) {
        LocalDate today = clock.today();
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today.plusDays(1))) throw ApiException.badRequest("The day report covers today and earlier days");
        List<Room> rooms = roomRepository.findByActiveTrueOrderByNumberAsc();
        Map<Long, List<MaintenanceRequest>> blocks = roomService.openBlocks();
        int outOfOrder = (int) rooms.stream().filter(r -> RoomService.blockedOn(blocks, r.getId(), day)).count();
        int sellable = rooms.size() - outOfOrder;
        List<Booking> onNight = staysOnNight(day);
        int occupied = onNight.size();
        List<FolioCharge> roomLines = chargeRepository.findByChargeTypeAndNightDateBetweenAndVoidedFalse(ChargeType.ROOM, day, day);
        BigDecimal roomRevenue = roomLines.stream().map(c -> nz(c.getNet())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = null;
        if (!day.isBefore(today)) {
            expected = BigDecimal.ZERO;
            for (Booking b : onNight) expected = expected.add(b.nightlyTotal());
        }
        long arrivals = bookingRepository.findByActualCheckInGreaterThanEqualAndActualCheckInLessThan(startOf(day), startOf(day.plusDays(1))).size();
        long departures = bookingRepository.findByActualCheckOutGreaterThanEqualAndActualCheckOutLessThan(startOf(day), startOf(day.plusDays(1))).size();
        long arrivalsTomorrow = bookingRepository.findByStatusAndCheckInDateOrderByIdAsc(BookingStatus.BOOKED, day.plusDays(1)).size();
        long departuresTomorrow = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).stream().filter(b -> b.getCheckOutDate().equals(day.plusDays(1))).count();
        List<Booking> inHouse = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN);
        BigDecimal balance = BigDecimal.ZERO;
        for (Booking b : inHouse) balance = balance.add(nz(folioService.folio(b.getId()).balance()));

        List<FolioCharge> earned = new ArrayList<>(roomLines);
        for (FolioCharge c : chargeRepository.findByVoidedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(startOf(day), startOf(day.plusDays(1)))) {
            if (c.getChargeType() != ChargeType.ROOM) earned.add(c);
        }
        List<RevenueLineDto> revenue = revenueLines(earned);
        BigDecimal revenueTotal = revenue.stream().map(RevenueLineDto::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MoneyLineDto> money = moneyLines(paymentRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startOf(day), startOf(day.plusDays(1))));

        long sold = roomLines.size();
        PeriodDto mtd = period("Month to date", day.withDayOfMonth(1), day);
        PeriodDto ytd = period("Year to date", day.withDayOfYear(1), day);
        return new DailyReportDto(day, day.equals(today), rooms.size(), outOfOrder, sellable, occupied, pct(occupied, sellable), money(roomRevenue),
                expected == null ? null : money(expected), div(roomRevenue, sold), div(roomRevenue, sellable), (int) arrivals, (int) departures,
                Math.max(0, occupied - (int) arrivals), (int) arrivalsTomorrow, (int) departuresTomorrow, inHouse.size(), money(balance), revenue, money(revenueTotal),
                money, money(sumNet(money)), mtd, ytd);
    }

    // ---------------------------------------------------------------- by booking source

    @Transactional(readOnly = true)
    public SourceReportDto sources(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw ApiException.badRequest("Choose a date range");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.badRequest("Choose a range of at most a year");
        List<Booking> bookings = bookingRepository.findByCheckInDateBetween(from, to);
        Map<Long, List<FolioCharge>> charges = new HashMap<>();
        if (!bookings.isEmpty()) {
            for (FolioCharge c : chargeRepository.findByBookingIdIn(bookings.stream().map(Booking::getId).toList())) {
                if (!c.isVoided()) charges.computeIfAbsent(c.getBooking().getId(), k -> new ArrayList<>()).add(c);
            }
        }
        Map<String, Acc> by = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Acc total = new Acc("Total");
        for (Booking b : bookings) {
            String source = b.getSource() == null || b.getSource().isBlank() ? UNSPECIFIED : b.getSource().trim();
            Acc a = by.computeIfAbsent(source, Acc::new);
            for (Acc x : new Acc[]{a, total}) {
                x.bookings++;
                if (b.getStatus() == BookingStatus.CANCELLED) x.cancelled++;
                if (b.getStatus() == BookingStatus.NO_SHOW) x.noShows++;
                if (b.getStatus() == BookingStatus.CHECKED_IN || b.getStatus() == BookingStatus.CHECKED_OUT) {
                    x.stays++;
                    x.stayNights += b.plannedNights();
                }
                long lead = ChronoUnit.DAYS.between(dayOf(b.getCreatedAt()), b.getCheckInDate());
                x.leadDays += Math.max(0, lead);
                for (FolioCharge c : charges.getOrDefault(b.getId(), List.of())) {
                    if (c.getChargeType() == ChargeType.ROOM) {
                        x.roomNights++;
                        x.roomRevenue = x.roomRevenue.add(nz(c.getNet()));
                    } else {
                        x.otherRevenue = x.otherRevenue.add(nz(c.getNet()));
                    }
                }
            }
        }
        List<SourceRowDto> rows = new ArrayList<>();
        by.values().stream().sorted(Comparator.comparing((Acc a) -> a.roomRevenue).reversed().thenComparing(a -> a.source, String.CASE_INSENSITIVE_ORDER)).forEach(a -> rows.add(a.row()));
        return new SourceReportDto(from, to, rows, total.row());
    }

    private static final class Acc {
        final String source;
        long bookings, stays, cancelled, noShows, roomNights, stayNights, leadDays;
        BigDecimal roomRevenue = BigDecimal.ZERO, otherRevenue = BigDecimal.ZERO;

        Acc(String source) {
            this.source = source;
        }

        SourceRowDto row() {
            return new SourceRowDto(source, bookings, stays, cancelled, noShows, roomNights, money(roomRevenue), money(otherRevenue), div(roomRevenue, roomNights),
                    div(BigDecimal.valueOf(stayNights), stays), div(BigDecimal.valueOf(leadDays), bookings), pct(cancelled + noShows, bookings));
        }
    }

    // ---------------------------------------------------------------- forecast and pick-up

    private BigDecimal priceOn(Booking b, LocalDate night, Map<Long, Map<LocalDate, BigDecimal>> stored) {
        BigDecimal extras = b.nightlyTotal().subtract(nz(b.getNightlyRate()));
        BigDecimal base = stored.getOrDefault(b.getId(), Map.of()).get(night);
        return (base != null ? base : nz(b.getNightlyRate())).add(extras);
    }

    @Transactional(readOnly = true)
    public ForecastDto forecast(LocalDate from, Integer days, Integer pickupDays) {
        LocalDate start = from == null ? clock.today() : from;
        int span = days == null ? 30 : days;
        if (span < 1 || span > 92) throw ApiException.badRequest("Choose between 1 and 92 days");
        int window = pickupDays == null ? 7 : pickupDays;
        if (window < 1 || window > 90) throw ApiException.badRequest("Pick-up covers between 1 and 90 days");
        LocalDate end = start.plusDays(span);
        AvailabilityDto av = bookingService.availability(start, end);
        List<Booking> live = bookingRepository.findOverlapping(start, end, LIVE);
        Map<Long, Map<LocalDate, BigDecimal>> stored = new HashMap<>();
        for (Booking b : live) {
            Map<LocalDate, BigDecimal> m = new HashMap<>();
            for (BookingNight n : nightRepository.findByBookingIdOrderByNightDateAsc(b.getId())) m.put(n.getNightDate(), nz(n.getRate()));
            stored.put(b.getId(), m);
        }
        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);
        List<ForecastDayDto> out = new ArrayList<>();
        long bookedNights = 0, capacityNights = 0, pickupNights = 0;
        BigDecimal revenue = BigDecimal.ZERO, pickupRevenue = BigDecimal.ZERO;
        for (int i = 0; i < span; i++) {
            LocalDate night = start.plusDays(i);
            int capacity = 0, held = 0;
            for (TypeAvailability t : av.types()) {
                DayAvailability d = t.days().get(i);
                capacity += d.total();
                held += d.held();
            }
            int booked = 0, arrivals = 0, departures = 0, pickup = 0;
            BigDecimal dayRevenue = BigDecimal.ZERO, dayPickup = BigDecimal.ZERO;
            for (Booking b : live) {
                if (b.getCheckInDate().equals(night)) arrivals++;
                if (b.getCheckOutDate().equals(night)) departures++;
                boolean covers = !b.getCheckInDate().isAfter(night) && (b.getCheckOutDate().isAfter(night) || (b.getStatus() == BookingStatus.CHECKED_IN && night.equals(clock.today())));
                if (!covers) continue;
                booked++;
                BigDecimal price = priceOn(b, night, stored);
                dayRevenue = dayRevenue.add(price);
                if (b.getCreatedAt().isAfter(since)) {
                    pickup++;
                    dayPickup = dayPickup.add(price);
                }
            }
            out.add(new ForecastDayDto(night, night.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), capacity, booked, held, pct(booked, capacity),
                    money(dayRevenue), arrivals, departures, pickup, money(dayPickup)));
            bookedNights += booked;
            capacityNights += capacity;
            pickupNights += pickup;
            revenue = revenue.add(dayRevenue);
            pickupRevenue = pickupRevenue.add(dayPickup);
        }
        return new ForecastDto(start, end.minusDays(1), window, out,
                new ForecastTotalsDto(bookedNights, capacityNights, pct(bookedNights, capacityNights), money(revenue), div(revenue, bookedNights), pickupNights, money(pickupRevenue)));
    }

    // ---------------------------------------------------------------- night audit

    private static String accountLabel(String key) {
        if (key == null) return "Other";
        return switch (key) {
            case AccountService.CASH -> "Cash";
            case AccountService.ACCOUNTS_RECEIVABLE -> "Accounts receivable";
            case AccountService.ROOM_REVENUE -> "Room revenue";
            case AccountService.TAX_PAYABLE -> "Tax payable";
            case AccountService.GUEST_DEPOSITS -> "Guest deposits";
            case AccountService.UNEARNED_ROOM_REVENUE -> "Unearned room revenue";
            default -> key.charAt(0) + key.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private record Posting(String source, String account, String label, BigDecimal debit, BigDecimal credit) {
    }

    private List<Posting> postings(Instant from, Instant to) {
        List<Posting> out = new ArrayList<>();
        for (Object[] r : journalRepository.hotelPostings(from, to)) {
            out.add(new Posting((String) r[0], (String) r[1], (String) r[2], (BigDecimal) r[3], (BigDecimal) r[4]));
        }
        return out;
    }

    private static BigDecimal credit(List<Posting> ps, String account, String label, String... sources) {
        Set<String> src = sources.length == 0 ? null : new HashSet<>(Arrays.asList(sources));
        BigDecimal sum = BigDecimal.ZERO;
        for (Posting p : ps) {
            if (!Objects.equals(p.account(), account)) continue;
            if (label != null && !label.equals(p.label())) continue;
            if (src != null && !src.contains(p.source())) continue;
            sum = sum.add(p.credit()).subtract(p.debit());
        }
        return sum;
    }

    private static AuditCheckDto check(String code, String label, BigDecimal hotel, BigDecimal accounting, String note) {
        BigDecimal diff = money(hotel).subtract(money(accounting));
        return new AuditCheckDto(code, label, money(hotel), money(accounting), diff, diff.abs().compareTo(new BigDecimal("0.01")) < 0, note);
    }

    @Transactional(readOnly = true)
    public NightAuditReportDto nightAudit(LocalDate date) {
        LocalDate today = clock.today();
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today)) throw ApiException.badRequest("Choose today or an earlier day");
        HotelSettings settings = settingsService.entity();
        Instant from = startOf(day), to = startOf(day.plusDays(1));

        List<FolioCharge> posted = chargeRepository.findByVoidedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        List<RevenueLineDto> charged = revenueLines(posted);
        BigDecimal chargedTotal = charged.stream().map(RevenueLineDto::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        long roomNights = posted.stream().filter(c -> c.getChargeType() == ChargeType.ROOM).count();
        BigDecimal roomRevenue = posted.stream().filter(c -> c.getChargeType() == ChargeType.ROOM).map(c -> nz(c.getNet())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<FolioPayment> payments = paymentRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        List<MoneyLineDto> money = moneyLines(payments);
        int arrivals = bookingRepository.findByActualCheckInGreaterThanEqualAndActualCheckInLessThan(from, to).size();
        int departures = bookingRepository.findByActualCheckOutGreaterThanEqualAndActualCheckOutLessThan(from, to).size();
        int inHouse = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).size();

        // what the hotel posted to Accounting that day
        List<Posting> dayPostings = postings(from, to);
        Map<String, BigDecimal[]> byAccount = new TreeMap<>();
        for (Posting p : dayPostings) {
            String name = accountLabel(p.account()) + (p.label() != null && (AccountService.ACCOUNTS_RECEIVABLE.equals(p.account())) ? " — " + p.label() : "");
            BigDecimal[] s = byAccount.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(p.debit());
            s[1] = s[1].add(p.credit());
        }
        List<AccountMovementDto> movements = new ArrayList<>();
        byAccount.forEach((k, s) -> movements.add(new AccountMovementDto(k, money(s[0]), money(s[1]))));

        // checks
        List<AuditCheckDto> checks = new ArrayList<>();
        BigDecimal dayCashHotel = payments.stream().map(p -> p.getKind() == PaymentKind.REFUND ? nz(p.getAmount()).negate() : nz(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (CompanyReceipt r : companyReceiptRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)) dayCashHotel = dayCashHotel.add(nz(r.getAmount()));
        for (GroupEntry e : groupEntryRepository.findByEntryDate(day)) {
            if (e.getKind() == GroupEntryKind.DEPOSIT || e.getKind() == GroupEntryKind.PAYMENT) dayCashHotel = dayCashHotel.add(nz(e.getAmount()));
            if (e.getKind() == GroupEntryKind.REFUND) dayCashHotel = dayCashHotel.subtract(nz(e.getAmount()));
        }
        checks.add(check("CASH_DAY", "Cash taken on the day", dayCashHotel, credit(dayPostings, AccountService.CASH, null).negate(),
                "Deposits, payments, company and group receipts less refunds, on folios and ledgers, against the cash the hotel posted to Accounting that day."));

        List<Posting> all = postings(Instant.EPOCH, startOf(today.plusDays(2)));
        BigDecimal roomNet = BigDecimal.ZERO, roomTax = BigDecimal.ZERO;
        for (Object[] r : bookingRepository.postedRoomTotals()) {
            roomNet = roomNet.add((BigDecimal) r[0]);
            roomTax = roomTax.add((BigDecimal) r[1]);
        }
        for (Object[] r : chargeRepository.postedExtrasTotals()) {
            roomNet = roomNet.add((BigDecimal) r[0]);
            roomTax = roomTax.add((BigDecimal) r[1]);
        }
        String[] revenueSources = {"HOTEL_ROOM", "HOTEL_EXTRA", "HOTEL_FEE", "HOTEL_DEPOSIT_FORFEIT"};
        checks.add(check("REVENUE", "Revenue posted (room, extras, fees)", roomNet, credit(all, AccountService.ROOM_REVENUE, null, revenueSources),
                "Everything the hotel's folios say has been posted as revenue, against the Room revenue account (all time, before tax)."));
        checks.add(check("TAX", "Tax posted on that revenue", roomTax, credit(all, AccountService.TAX_PAYABLE, null, revenueSources),
                "The tax on those lines, against the Tax payable account."));

        BigDecimal held = BigDecimal.ZERO;
        for (Object[] r : paymentRepository.totalsByKindForStatuses(LIVE)) {
            PaymentKind k = (PaymentKind) r[0];
            BigDecimal amt = (BigDecimal) r[1];
            if (k == PaymentKind.DEPOSIT) held = held.add(amt);
            if (k == PaymentKind.REFUND) held = held.subtract(amt);
        }
        BigDecimal groupBilled = BigDecimal.ZERO, groupDeposits = BigDecimal.ZERO, groupApplied = BigDecimal.ZERO, groupPaid = BigDecimal.ZERO, groupRefunded = BigDecimal.ZERO;
        for (Object[] r : groupEntryRepository.totalsByKind()) {
            BigDecimal amt = (BigDecimal) r[1];
            switch ((GroupEntryKind) r[0]) {
                case BILL -> groupBilled = amt;
                case DEPOSIT -> groupDeposits = amt;
                case APPLIED -> groupApplied = amt;
                case PAYMENT -> groupPaid = amt;
                case REFUND -> groupRefunded = amt;
            }
        }
        BigDecimal groupHeld = groupDeposits.subtract(groupApplied).subtract(groupRefunded);
        checks.add(check("DEPOSITS", "Deposits held for guests and groups", held.add(groupHeld), credit(all, AccountService.GUEST_DEPOSITS, null),
                "Deposits on bookings still waiting or in the house, plus group deposits not yet applied, against the Guest deposits account."));
        checks.add(check("CITY_LEDGER", "Owed by companies (city ledger)", nz(companyBillRepository.outstandingTotal()), credit(all, AccountService.ACCOUNTS_RECEIVABLE, "City ledger").negate(),
                "Company bills still unpaid, against Accounts receivable — City ledger."));
        checks.add(check("GROUP_LEDGER", "Owed by groups (group ledger)", groupBilled.subtract(groupApplied).subtract(groupPaid), credit(all, AccountService.ACCOUNTS_RECEIVABLE, "Group ledger").negate(),
                "Group master accounts still unpaid, against Accounts receivable — Group ledger."));

        LocalDate lastRun = settings.getAuditLastRun();
        boolean balanced = checks.stream().allMatch(AuditCheckDto::ok);
        return new NightAuditReportDto(day, lastRun, settings.getAuditLastSummary(), settingsService.postingMode().name(), lastRun != null && !lastRun.isBefore(day),
                inHouse, arrivals, departures, roomNights, money(roomRevenue), charged, money(chargedTotal), money, money(sumNet(money)), movements, checks, balanced);
    }
}
