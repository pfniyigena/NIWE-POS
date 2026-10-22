package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** The one settings row of the Real Estate module. */
@Getter
@Setter
@Entity
public class ReSettings extends BaseEntity {

    /** Areas (districts, neighbourhoods) offered as suggestions on properties and leads; comma separated. */
    @Column(length = 4000)
    private String areas = "";

    /** How long a viewing lasts unless the person sets another end time. */
    @Column(nullable = false)
    private int viewingMinutes = 30;

    /** A lead with no contact for this many days shows as stale. */
    @Column(nullable = false)
    private int staleLeadDays = 14;

    /** The public listings website (/properties on the site) is open to visitors only while this is on. */
    @Column(nullable = false)
    private boolean websiteEnabled = false;

    @Column(length = 120)
    private String websiteTitle;

    @Column(length = 1000)
    private String websiteIntro;

    @Column(length = 60)
    private String websitePhone;

    @Column(length = 200)
    private String websiteEmail;

    /** How many website enquiries one phone number may send in a day. */
    @Column(nullable = false)
    private int enquiriesPerPhonePerDay = 3;

    /** The agency's commission, as a percentage of the price, that a new deal starts with. */
    @Column(precision = 7, scale = 2)
    private java.math.BigDecimal defaultCommissionPercent;

    /** The expense account that commission paid out is posted to. */
    private Long commissionAccountId;

    /** When on, each agent gets a morning e-mail with the follow-ups that are due or late and the leads that have gone quiet. */
    @Column(nullable = false)
    private boolean followUpEmailEnabled = false;

    private java.time.LocalDate followUpLastRun;

    @Column(length = 500)
    private String followUpLastSummary;

    /** When on, the lead and the agent are e-mailed a reminder shortly before a viewing. */
    @Column(nullable = false)
    private boolean viewingReminderEnabled = false;

    /** How many hours before a viewing the reminder goes out. */
    @Column(nullable = false)
    private int viewingReminderHours = 24;

    /** The agency's own wording for the printed offer letter, reservation letter and sale agreement. Empty means the standard wording. */
    @Column(length = 3000)
    private String offerTerms;

    @Column(length = 3000)
    private String reservationTerms;

    @Column(length = 3000)
    private String agreementTerms;

    /** The service product that deal invoices are raised under (made the first time one is needed). */
    private Long saleProductId;
}
