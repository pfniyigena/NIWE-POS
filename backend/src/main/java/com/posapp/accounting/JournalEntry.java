package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounting Module — one double-entry transaction: a dated header plus two or more {@link
 * JournalEntryLine}s whose debits and credits sum to exactly the same total (enforced by whichever
 * caller builds one — see {@link AccountingService#post} for every automatic posting, and {@link
 * JournalEntryService#createManual} for an admin's own manual entry). There is deliberately no
 * "reverse" or "void" action: fixing a mistaken entry means posting a new one that reverses it
 * (same "never rewrite history" convention as every other financial record in this app), which is
 * exactly what a real bookkeeping ledger requires anyway — an immutable trail, not one that gets
 * edited after the fact.
 *
 * <p>{@code sourceType}/{@code sourceId} identify which domain record (if any) generated this
 * entry automatically — {@code "POS_SALE"}/a {@code SaleOrder} id, {@code "MANUFACTURING_RUN"}/a
 * {@code ManufacturingOrderRun} id, and so on (see {@code AccountingService}'s own javadoc for the
 * full list) — both null for a manually-created entry. Purely informational (a "why does this
 * entry exist" breadcrumb for an admin reading the ledger); nothing re-derives or re-posts from
 * them.
 */
@Getter
@Setter
@Entity
public class JournalEntry extends BaseEntity {

    /** Auto-assigned, "JE-" + millis — same stable-identifier shape as every other auto-numbered document in this app. */
    private String entryNumber;

    private Instant entryDate = Instant.now();

    private String memo;

    /** Null for a manual entry — see class javadoc. */
    private String sourceType;

    /** Null for a manual entry — see class javadoc. */
    private Long sourceId;

    /**
     * Fourier: branch P&L — the branch the originating document belonged to (POS sale/return,
     * sale order fulfilment/return, purchase receipt/return, expense and its void/settlement).
     * Snapshot id; null means head office / not attributed to a branch (also every entry posted
     * before branches existed, and sources that carry no branch such as customer/supplier
     * payments, stock adjustments and manufacturing). The branch Profit &amp; Loss filters on it.
     */
    @jakarta.persistence.Column(name = "branch_id")
    private Long branchId;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalEntryLine> lines = new ArrayList<>();
}
