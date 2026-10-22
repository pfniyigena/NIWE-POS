package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A room or piece of equipment that can be booked, one appointment at a time. */
@Getter
@Setter
@Entity
public class BookableResource extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** Groups resources a service can use interchangeably, e.g. "Room". */
    @Column(nullable = false, length = 60)
    private String kind = "Room";

    private Integer capacity;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean active = true;

    private Long branchId;
}
