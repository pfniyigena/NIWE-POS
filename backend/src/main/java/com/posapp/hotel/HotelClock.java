package com.posapp.hotel;

import com.posapp.settings.StoreSettingsService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** "Today" for the hotel: the store's time zone from Settings (falls back to the server's). */
@Component
public class HotelClock {

    private final StoreSettingsService storeSettingsService;

    public HotelClock(StoreSettingsService storeSettingsService) {
        this.storeSettingsService = storeSettingsService;
    }

    public ZoneId zone() {
        try {
            String id = storeSettingsService.getSettingsEntity().getTimeZone();
            if (id != null && !id.isBlank()) {
                return ZoneId.of(id.trim());
            }
        } catch (Exception ignored) {
            // fall through to the server zone
        }
        return ZoneId.systemDefault();
    }

    public LocalDate today() {
        return LocalDate.now(zone());
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone());
    }
}
