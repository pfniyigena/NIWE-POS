package com.posapp.accounting;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * A named cost/profit center (e.g. "Front Store", "Kitchen", "Delivery", a branch or department) —
 * an optional second dimension a {@link JournalEntryLine} can be tagged with, alongside its
 * required {@link Account}. Same flat-lookup shape as {@link Account} itself ({@code code} +
 * {@code name} + {@code active}), deliberately not modeled as a hierarchy (no parent/child cost
 * centers) — this app has no need for roll-ups deeper than "one flat list of centers," matching
 * how {@code Workstation}/{@code PaymentMethodEntity} keep the same flat shape for a similar
 * "small admin-managed lookup" role. Unlike {@link Account}, a cost center is always optional on a
 * line (see {@code JournalEntryLine#costCenter}) — most stores will never need one, so nothing
 * about posting or reporting requires it to be set.
 */
@Getter
@Setter
@Entity
public class CostCenter extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private boolean active = true;
}
