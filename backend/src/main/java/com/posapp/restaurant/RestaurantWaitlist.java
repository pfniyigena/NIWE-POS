package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A walk-in party waiting for a table (Fourier: "Reservations" — the line at the door). */
@Getter
@Setter
@Entity
public class RestaurantWaitlist extends BaseEntity {

    private String guestName;
    private String guestPhone;
    private int partySize = 2;
    private String notes;
    /** WAITING, NOTIFIED, SEATED or LEFT. */
    private String status = "WAITING";
    private Integer quotedMinutes;
    private Instant desiredAt;
    private Instant notifiedAt;
    private Instant closedAt;
    private Long orderId;
}
