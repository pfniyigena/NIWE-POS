package com.posapp.subscription;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One row: overdue reminders to the customer and how many missed cycles the nightly job bills in one run. */
@Getter
@Setter
@Entity
public class SubscriptionSettings extends BaseEntity {

    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean dunningEnabled = false;

    /** First reminder this many days after the invoice is due (or dated, if it has no due date). */
    @Column(nullable = false, columnDefinition = "integer default 3 not null")
    private int dunningFirstDays = 3;

    @Column(nullable = false, columnDefinition = "integer default 7 not null")
    private int dunningRepeatDays = 7;

    /** At most this many reminders per invoice. */
    @Column(nullable = false, columnDefinition = "integer default 3 not null")
    private int dunningMax = 3;

    /** The nightly job bills up to this many missed cycles per subscription per run (1 = one per day, as before). */
    @Column(nullable = false, columnDefinition = "integer default 1 not null")
    private int catchUpMax = 1;
}
