package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.hotel.dto.HotelDtos.AuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * The hotel's end-of-day job. Once per store-local day, from the configured time onward, it charges every in-house guest for the nights that
 * have ended (which, in the "post nightly" accounting mode, is also when that room revenue reaches Accounting) and sends occupied rooms back
 * to "dirty" so housekeeping makes the stay-over visit. "Run now" on the Settings screen does the same on demand and is safe to repeat:
 * a night is never charged twice.
 */
@Service
public class NightAuditService {

    private static final Logger log = LoggerFactory.getLogger(NightAuditService.class);

    private final HotelSettingsService settingsService;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final HotelFolioService folioService;
    private final HotelClock clock;
    private final AuditLogService auditLogService;

    public NightAuditService(HotelSettingsService settingsService, BookingRepository bookingRepository, RoomRepository roomRepository,
                             HotelFolioService folioService, HotelClock clock, AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.folioService = folioService;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 90_000L)
    public void tick() {
        try {
            HotelSettings settings = settingsService.entity();
            if (!settings.isEnabled()) return;
            LocalDateTime now = clock.now();
            LocalDate today = now.toLocalDate();
            if (today.equals(settings.getAuditLastRun())) return;
            LocalTime at;
            try {
                at = LocalTime.parse(settings.getAuditTime());
            } catch (Exception ex) {
                at = LocalTime.of(3, 0);
            }
            if (now.toLocalTime().isBefore(at)) return;
            AuditResult result = runNow();
            log.info("Hotel night audit: {}", result.message());
        } catch (Exception ex) {
            log.warn("Hotel night audit failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public AuditResult runNow() {
        LocalDate today = clock.today();
        List<Booking> inHouse = bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN);
        int nights = 0;
        for (Booking booking : inHouse) {
            nights += folioService.ensureRoomCharges(booking, today);
            Room room = booking.getRoom();
            if (room != null && (room.getHousekeepingStatus() == HousekeepingStatus.CLEAN || room.getHousekeepingStatus() == HousekeepingStatus.INSPECTED)) {
                room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
                roomRepository.save(room);
            }
        }
        String message = "Night audit for " + today + ": " + inHouse.size() + " in-house stay" + (inHouse.size() == 1 ? "" : "s") + " checked, "
                + nights + " night" + (nights == 1 ? "" : "s") + " charged.";
        settingsService.markAuditRan(today, message);
        auditLogService.record("HOTEL_NIGHT_AUDIT", message);
        return new AuditResult(inHouse.size(), nights, message);
    }
}
