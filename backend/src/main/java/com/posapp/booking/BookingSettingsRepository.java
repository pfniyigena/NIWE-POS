package com.posapp.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingSettingsRepository extends JpaRepository<BookingSettings, Long> {

    /** Takes a write lock on the settings row; bookings are created one at a time, so a slot cannot be sold twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BookingSettings s order by s.id")
    List<BookingSettings> lockAll();
}
