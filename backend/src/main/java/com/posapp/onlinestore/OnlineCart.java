package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The cart of a visitor who typed an e-mail address at checkout, kept (one per address) so the shop can remind them once or twice.
 * ACTIVE: waiting for the first reminder. REMINDED: the first reminder went out. DONE: both went out. RECOVERED: an order was placed with that address.
 * STOPPED: the customer asked for no more reminders (it stays, so the address is not reminded again). EXPIRED: nothing left to remind about.
 */
@Getter
@Setter
@Entity
public class OnlineCart extends BaseEntity {
    private String email;
    private String language = "en";
    /** Random; it is in the links of the reminder e-mails (back to the cart, stop the reminders). */
    private String token;
    private String status = "ACTIVE";
    /** The last time the customer changed the cart; the reminders are counted from it. */
    private Instant savedAt = Instant.now();
    private int reminderCount;
    private Instant firstReminderAt;
    private Instant secondReminderAt;
    private Instant recoveredAt;
    /** Failed tries to send the current reminder; three in a row end the cart as EXPIRED. */
    private int attempts;
    private Instant nextTryAt;
    private String lastError;
}
