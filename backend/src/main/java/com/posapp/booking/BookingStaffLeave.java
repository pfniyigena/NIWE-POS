package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Days a person cannot be booked (leave, training, a day off), from and to inclusive. */
@Getter
@Setter
@Entity
@Table(name = "booking_staff_leave")
public class BookingStaffLeave extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_by_name")
    private String createdByName;
}
