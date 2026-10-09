package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * Accounting Module (Fourier: "We need to add Accounting module. Then all POS, Sale, Inventory
 * and Manufacturing module will be integrated when accounting is enabled") — one line of the
 * Chart of Accounts. Deliberately a flat lookup, same shape as {@code Workstation}/{@code
 * PaymentMethodEntity}: a stable {@code code} (never shown to a customer, but here it's what an
 * admin actually reads — the code is the account number in the traditional bookkeeping sense,
 * e.g. "1000" for Cash), a display {@code name}, and {@code active} so one can be retired without
 * breaking a past {@link JournalEntryLine} that already referenced it.
 *
 * <p>{@code systemAccount} marks the small set of accounts {@link AccountingService} posts to —
 * these can be renamed but never deactivated ({@link AccountService#update} enforces this), since
 * deactivating one out from under an automatic posting would silently break every module's
 * integration. Every other account (anything the admin adds themselves, plus the starter
 * convenience accounts like "Owner's Equity"/"Operating Expenses" that nothing posts to
 * automatically) is a normal, freely-deactivatable lookup row, same as any other in this app.
 *
 * <p>{@code wellKnownKey} (Fourier: "I want to have possibility of selection on Chart while
 * initiating the app at first time") is what {@link AccountingService} actually looks accounts up
 * by — a stable internal key (e.g. {@code "CASH"}) that never varies, independent of {@code code}.
 * {@code code} is purely the display/bookkeeping number an admin reads, and that DOES vary by which
 * country's chart a deployment was seeded with (see {@link ChartCountry}) — Rwanda's Cash account
 * is code "1000", Chad's is "521", but both carry {@code wellKnownKey = "CASH"} so the exact same
 * automatic postings resolve correctly either way. Null for every account nothing posts to
 * automatically (every admin-added account, and the starter convenience ones).
 */
@Getter
@Setter
@Entity
public class Account extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    private String description;

    private boolean active = true;

    /** See class javadoc — true only for the handful of accounts AccountingService's automatic postings depend on. */
    private boolean systemAccount = false;

    /** See class javadoc — the stable key AccountingService/AccountingReportService resolve by; null for every account that isn't looked up programmatically. */
    @Column(unique = true)
    private String wellKnownKey;
}
