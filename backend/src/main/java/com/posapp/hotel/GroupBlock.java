package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rooms of a type held for a group over [startDate, endDate). Rooms the group has not booked go back on sale on the release date. */
@Getter
@Setter
@Entity
@Table(name = "group_block")
public class GroupBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private HotelGroup group;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private RoomType roomType;

    private int rooms;

    /** The group's nightly rate before tax (null = the hotel's rates apply). */
    private BigDecimal rate;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate releaseDate;
    private boolean released = false;

    public boolean holdsOn(LocalDate night, LocalDate today) {
        if (released) return false;
        if (releaseDate != null && !today.isBefore(releaseDate)) return false;
        GroupStatus s = group.getStatus();
        if (s != GroupStatus.TENTATIVE && s != GroupStatus.CONFIRMED) return false;
        return !night.isBefore(startDate) && night.isBefore(endDate);
    }
}
