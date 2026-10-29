package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** One working day of one person (1 = Monday ... 7 = Sunday). A person with no rows at all follows the business opening hours. */
@Getter
@Setter
@Entity
@Table(name = "booking_staff_hours", uniqueConstraints = @UniqueConstraint(name = "uk_booking_staff_hours", columnNames = {"user_id", "weekday"}))
public class BookingStaffHours extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
