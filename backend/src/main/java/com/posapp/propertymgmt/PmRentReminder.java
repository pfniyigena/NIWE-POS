package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One reminder e-mailed to a tenant about unpaid rent: before it was due (BEFORE), after it was due (OVERDUE) or sent by hand (MANUAL). Kept so nobody is reminded twice. */
@Getter
@Setter
@Entity
@Table(name = "pm_rent_reminder", indexes = @Index(columnList = "rent_charge_id"))
public class PmRentReminder extends BaseEntity {

    public enum Kind { BEFORE, OVERDUE, MANUAL }

    @Column(nullable = false)
    private Long rentChargeId;

    @Column(nullable = false)
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false)
    private String sentTo;

    /** Who sent it: a user's name, or "The morning job". */
    @Column(length = 120)
    private String sentBy;
}
