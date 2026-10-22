package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** The firm's law-module settings; one row. */
@Getter
@Setter
@Entity
public class LawSettings extends BaseEntity {

    /** One practice area per line; offered when opening a matter. */
    @Column(nullable = false, length = 2000)
    private String practiceAreas = "Litigation\nCorporate & commercial\nFamily & succession\nLand & property\nCriminal\nLabour & employment\nTax\nArbitration & mediation\nOther";

    /** One jurisdiction per line. */
    @Column(nullable = false, length = 1000)
    private String jurisdictions = "Rwanda\nChad\nOther";

    /** A deadline or task falling within this many days is "due soon" and goes into the daily e-mail. */
    @Column(nullable = false)
    private int reminderDays = 7;

    /** Send each lawyer a daily e-mail of what is due soon or overdue. */
    @Column(nullable = false)
    private boolean digestEnabled = true;

    /** New matters start as restricted (only their team and admins can open them). */
    @Column(nullable = false)
    private boolean restrictedByDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingType defaultBillingType = BillingType.HOURLY;

    /** The firm holds money for clients in a trust account and keeps a ledger of it. */
    @Column(nullable = false)
    private boolean trustEnabled = false;

    /** The bank and account number of the trust account, as shown on the trust screens. */
    private String trustBankName;

    @Column(length = 100)
    private String trustAccountNumber;

    /** Warn the lead lawyer by e-mail when a limitation date is close. */
    @Column(nullable = false)
    private boolean escalationEnabled = false;

    /** The first warning goes out when a limitation date is this many days away; more follow at 3 days and when it is overdue. */
    @Column(nullable = false)
    private int escalationDays = 14;

    /** Remind clients by e-mail about overdue fees, once a day. */
    @Column(nullable = false)
    private boolean collectionsEnabled = false;

    /** The first reminder goes out when an invoice is this many days past its due date. */
    @Column(nullable = false)
    private int collectionsFirstDays = 7;

    /** A further reminder follows this many days after the last one. */
    @Column(nullable = false)
    private int collectionsRepeatDays = 14;

    /** No more than this many automatic reminders per invoice. */
    @Column(nullable = false)
    private int collectionsMax = 3;

    /** An invoice with no due date is treated as due this many days after its date (unless the client has a credit term of their own). */
    @Column(nullable = false)
    private int defaultTermDays = 30;

    /** The client portal is switched on for the firm. Each matter still has to be opened to it. */
    @Column(nullable = false)
    private boolean portalEnabled = false;
}
