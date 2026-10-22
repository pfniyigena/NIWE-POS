package com.posapp.register;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The point-of-sale rules (one row): how cash at the till is controlled. Later releases add the void, return, discount and receipt rules here. */
@Getter
@Setter
@Entity
public class PosSettings extends BaseEntity {

    /** A count off by no more than this is accepted without comment (0 = any difference needs a note). */
    private BigDecimal varianceTolerance = BigDecimal.ZERO;
    /** A count off by more than the tolerance needs a note saying why. */
    private boolean varianceNoteRequired = false;
    /** Closing a shift needs the notes and coins counted, not just a total. */
    private boolean denominationCountRequired = false;
    /** A cashier paying out more than this from the drawer needs a manager's PIN (0 = no limit). */
    private BigDecimal paidOutApprovalOver = BigDecimal.ZERO;
    /** The till shows a reminder to drop cash into the safe once the drawer holds more than this (0 = off). */
    private BigDecimal dropAlertAmount = BigDecimal.ZERO;

    /** Voiding a completed sale needs a manager (the manager signs in, or types their PIN at the till). */
    private boolean voidRequiresApproval = true;
    /** A completed sale can only be voided while its shift is still open; after that it is a return. */
    private boolean voidSameShiftOnly = true;
    /** A return after this many days needs a manager's approval (0 = no limit). */
    private int returnWindowDays = 0;
    /** A refund above this amount needs a manager's approval (0 = no limit). */
    private BigDecimal returnApprovalOver = BigDecimal.ZERO;
    private boolean returnReasonRequired = false;
    /** The reasons offered for a return or void, separated by commas. */
    private String returnReasons = "Wrong size,Damaged or faulty,Changed mind,Wrong item,Other";

    /** A cashier without the admin or discount-override right may still give a discount up to this percent alone; above it a manager approves (0 = every discount needs a manager). */
    private BigDecimal cashierDiscountMax = BigDecimal.ZERO;
    /** A discount needs a reason (chosen from {@link #discountReasons} or typed). */
    private boolean discountReasonRequired = false;
    private String discountReasons = "Loyal customer,Damaged item,Staff,Manager special,Price match,Other";
    /** A manager may change the price of a line at the till (with a reason). */
    private boolean priceOverrideAllowed = true;
    /** A price below the item's cost is refused, unless the approver holds the discount-override permission. */
    private boolean priceBelowCostBlocked = true;

    /** Printed under the business details at the top of every receipt (blank = nothing extra; a branch's own header still prints). */
    private String receiptHeader;
    /** Printed at the bottom of every receipt. */
    private String receiptFooter = "Thank you!";
    /** Paper the receipt is laid out for: "80" or "58" (thermal roll width in mm). */
    private String receiptPaper = "80";
    private boolean receiptShowLogo = true;
    /** Print a QR code of the invoice number (EFRIS sales keep their own fiscal QR code). */
    private boolean receiptShowQr = false;
}
