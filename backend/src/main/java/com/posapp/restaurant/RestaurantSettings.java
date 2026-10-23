package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The restaurant's own pricing rules (one row): the service charge and which kinds of order carry it, the largest discount a
 * waiter may give without a manager, tips, and the rounding of the bill. Each order takes a copy of the rules when it is opened,
 * so changing them never changes a tab that is already running.
 */
@Getter
@Setter
@Entity
public class RestaurantSettings extends BaseEntity {

    private BigDecimal serviceChargePercent = BigDecimal.ZERO;
    private boolean serviceChargeDineIn = true;
    private boolean serviceChargeTakeaway = false;
    private boolean serviceChargeDelivery = false;
    /** VAT charged on the service charge itself (0 = the service charge carries no tax). */
    private BigDecimal serviceChargeVatPercent = BigDecimal.ZERO;
    /** The biggest discount (percent of the bill) anyone may give; more needs a manager. */
    private BigDecimal discountLimitPercent = BigDecimal.valueOf(10);
    private boolean tipsEnabled = true;
    /** Comma separated percentages offered as quick tips at the till, for example 5,10,15. */
    private String tipPresets = "5,10,15";
    /** Round the amount due to a multiple of this (for example 50 or 100); 0 = no rounding. */
    private BigDecimal roundingUnit = BigDecimal.ZERO;
    /** Comma separated quick notes the waiter can tap for the kitchen. */
    /** Opening hours for bookings, local time HH:mm. */
    private String openTime = "11:00";
    private String closeTime = "23:00";
    /** Bookable times are this many minutes apart. */
    private int slotMinutes = 30;
    /** How long a party keeps a table unless the booking says otherwise. */
    private int turnMinutes = 90;
    private int bookingDaysAhead = 60;
    /** Deposit asked per guest (0 = no deposit). */
    private BigDecimal depositPerGuest = BigDecimal.ZERO;
    /** A deposit is refunded automatically when the booking is cancelled at least this many hours ahead. */
    private int depositRefundHours = 24;
    private int reminderHours = 24;
    /** Minutes after the booked time before a party can be marked as a no-show. */
    private int graceMinutes = 15;
    /** Online bookings are refused for a phone number with this many no-shows (0 = never). */
    private int noShowBlockCount = 0;
    private String reminderTemplate = "Hello {name}, this is a reminder of your table for {party} at {restaurant} on {date} at {time}. Reservation number {code}. Please tell us if your plans change.";
    /** Minutes a kitchen ticket should take when the menu item has no time of its own. */
    private int defaultPrepMinutes = 15;
    /** A ticket turns amber once this share of its time has passed, and red when the time is up. */
    private int kitchenWarnPercent = 75;
    /** Online ordering (takeaway and delivery pages), QR menus at the tables, and how requests are handled. */
    private boolean onlineEnabled = false;
    private boolean qrEnabled = false;
    private boolean onlineTakeaway = true;
    private boolean onlineDelivery = true;
    private boolean onlinePaused = false;
    private boolean onlineAutoAcceptTables = false;
    private int onlinePrepMinutes = 25;
    private BigDecimal onlineMinOrder = BigDecimal.ZERO;
    private int onlineMaxPending = 3;
    private int onlineExpireMinutes = 20;
    private String onlineMessage;
    /** "Allow public website": when off, the public Reserve a Table page is closed. */
    private boolean publicEnabled = true;
    private String notePresets = "No onions,No spice,Extra spicy,Well done,Medium,Sauce on the side,Allergy - ask guest";
}
