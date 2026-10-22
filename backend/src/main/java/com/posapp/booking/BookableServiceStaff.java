package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A staff member who can perform a service. */
@Getter
@Setter
@Entity
public class BookableServiceStaff extends BaseEntity {

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
