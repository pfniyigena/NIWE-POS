package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** The single settings row of the Booking module. Also the row that is locked while an appointment is created, so two bookings cannot take the same slot. */
@Getter
@Setter
@Entity
public class BookingSettings extends BaseEntity {

    @Column(nullable = false)
    private LocalTime dayStart = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime dayEnd = LocalTime.of(17, 0);

    /** Comma separated three-letter days, e.g. MON,TUE,WED,THU,FRI. */
    @Column(nullable = false, length = 60)
    private String workDays = "MON,TUE,WED,THU,FRI";

    /** Start times are offered every this many minutes. */
    @Column(nullable = false)
    private int slotStepMinutes = 30;

    /** The public page does not offer a slot starting sooner than this from now. */
    @Column(nullable = false)
    private int minNoticeMinutes = 60;

    @Column(nullable = false)
    private int maxAdvanceDays = 60;

    /** How long an unpaid booking holds its slot. */
    @Column(nullable = false)
    private int holdMinutes = 120;

    /** A guest can cancel on the public page up to this many hours before the start. */
    @Column(nullable = false)
    private int cancelNoticeHours = 24;

    @Column(nullable = false)
    private int maxPublicPerPhonePerDay = 3;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean publicEnabled = true;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean emailConfirmations = true;

    @Column(length = 120)
    private String publicTitle;

    @Column(length = 2000)
    private String publicIntro;

    /** Shown to a guest who must pay: how to pay (mobile money number, bank details, "at the desk"...). */
    @Column(length = 2000)
    private String paymentInstructions;

    private Long billingProductId;
}
