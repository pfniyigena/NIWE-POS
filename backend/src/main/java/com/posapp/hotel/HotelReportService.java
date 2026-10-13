package com.posapp.hotel;

import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HotelDtos.DashboardDto;
import com.posapp.hotel.dto.HotelDtos.OccupancyReportDto;
import com.posapp.hotel.dto.HotelDtos.RevenueByType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The hotel dashboard and the occupancy / ADR / RevPAR report. */
@Service
public class HotelReportService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final FolioChargeRepository chargeRepository;
    private final HotelClock clock;

    public HotelReportService(RoomRepository roomRepository, BookingRepository bookingRepository, FolioChargeRepository chargeRepository, HotelClock clock) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        LocalDate today = clock.today();
        List<Room> rooms = roomRepository.findByActiveTrueOrderByNumberAsc();
        int total = rooms.size();
        int occupied = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN).size();
        int outOfOrder = (int) rooms.stream().filter(Room::isOutOfOrder).count();
        int dirty = (int) rooms.stream().filter(r -> r.getHousekeepingStatus() == HousekeepingStatus.DIRTY).count();
        int arrivals = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.BOOKED).stream()
                .filter(b -> !b.getCheckInDate().isAfter(today)).toList().size();
        int departures = bookingRepository.findByStatusAndCheckOutDateLessThanEqualOrderByCheckOutDateAscIdAsc(BookingStatus.CHECKED_IN, today).size();
        int sellable = total - outOfOrder;
        int available = Math.max(0, sellable - occupied);
        BigDecimal pct = sellable <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(occupied * 100.0 / sellable).setScale(1, RoundingMode.HALF_UP);
        return new DashboardDto(total, occupied, available, outOfOrder, arrivals, departures, dirty, pct);
    }

    /**
     * Occupancy and room revenue over [from, to] inclusive. A "room night sold" is a non-voided ROOM line dated in the range, so the report
     * agrees with the bills; revenue is the line's amount before tax. ADR = revenue / nights sold; RevPAR = revenue / nights available.
     */
    @Transactional(readOnly = true)
    public OccupancyReportDto occupancy(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw ApiException.badRequest("Choose a date range");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366) throw ApiException.badRequest("Choose a range of at most a year");
        int roomCount = roomRepository.findByActiveTrueOrderByNumberAsc().size();
        long available = roomCount * days;

        Map<String, long[]> nightsByType = new LinkedHashMap<>();
        Map<String, BigDecimal> revenueByType = new LinkedHashMap<>();
        long sold = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (FolioCharge line : chargeRepository.findByChargeTypeAndNightDateBetweenAndVoidedFalse(ChargeType.ROOM, from, to)) {
            Booking b = line.getBooking();
            String type = (b.getRoom() != null ? b.getRoom().getRoomType() : b.getRoomType()).getName();
            nightsByType.computeIfAbsent(type, k -> new long[1])[0]++;
            revenueByType.merge(type, line.getNet(), BigDecimal::add);
            sold++;
            revenue = revenue.add(line.getNet());
        }
        List<RevenueByType> byType = nightsByType.entrySet().stream()
                .map(e -> new RevenueByType(e.getKey(), e.getValue()[0], revenueByType.get(e.getKey())))
                .sorted((a, b) -> b.revenue().compareTo(a.revenue())).toList();
        BigDecimal occupancy = available == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(sold * 100.0 / available).setScale(1, RoundingMode.HALF_UP);
        BigDecimal adr = sold == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP);
        BigDecimal revPar = available == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
        return new OccupancyReportDto(from, to, roomCount, available, sold, occupancy, revenue, adr, revPar, byType);
    }
}
