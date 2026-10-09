package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * A physical dining table (Fourier: "add Restaurant management in our ERP ... enabled on request
 * as we did for Accounting module"). {@code status} is the front-of-house source of truth for
 * whether a table is currently usable — {@code RestaurantOrderService} flips it to OCCUPIED when
 * an order opens and back to AVAILABLE when that order closes/cancels (see those methods'
 * javadoc); OUT_OF_SERVICE is the one status set by hand rather than derived (a broken table,
 * temporarily out of use) — see {@code DiningTableService.setStatus}.
 */
@Getter
@Setter
@Entity
public class DiningTable extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Optional grouping for the floor-plan view ("Patio", "Main Hall", ...) — purely a display label, never validated against a fixed list. */
    private String section;

    private int capacity = 2;

    @Enumerated(EnumType.STRING)
    private TableStatus status = TableStatus.AVAILABLE;

    /** Soft-disable a table without deleting it — same convention as Category/Brand/PaymentMethod's own active flags. */
    private boolean active = true;
}
