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

import java.time.LocalDate;

/** Something a guest left behind. */
@Getter
@Setter
@Entity
public class LostFoundItem extends BaseEntity {

    @Column(unique = true)
    private String itemNo;

    private String description;
    private LocalDate foundOn;

    @ManyToOne(fetch = FetchType.EAGER)
    private Room room;

    private String location;
    private String foundBy;

    @Enumerated(EnumType.STRING)
    private LostFoundStatus status = LostFoundStatus.FOUND;

    /** The guest it probably belongs to (the room's last occupant, or chosen by hand). */
    @ManyToOne(fetch = FetchType.EAGER)
    private Guest guest;

    private String claimedBy;
    private LocalDate claimedOn;

    @Column(columnDefinition = "text")
    private String notes;
}
