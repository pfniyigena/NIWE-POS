package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One physical room. Whether it is occupied comes from the checked-in booking; cleaning and out-of-order state live here. */
@Getter
@Setter
@Entity
public class Room extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    private String floor;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private RoomType roomType;

    /** Replaces the room type's rate for this room (a sea view, a renovated room...). Null = use the room type's rate. */
    private BigDecimal rateOverride;

    @Enumerated(EnumType.STRING)
    private HousekeepingStatus housekeepingStatus = HousekeepingStatus.CLEAN;

    /** Free-text name of whoever is cleaning it (housekeeping staff are not necessarily system users). */
    private String housekeeper;

    @Column(columnDefinition = "TEXT")
    private String housekeepingNote;

    /** True while an open maintenance request blocks the room — it cannot be booked into or checked into. */
    private boolean outOfOrder = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private boolean active = true;

    public BigDecimal effectiveRate() {
        return rateOverride != null ? rateOverride : roomType.getBaseRate();
    }
}
