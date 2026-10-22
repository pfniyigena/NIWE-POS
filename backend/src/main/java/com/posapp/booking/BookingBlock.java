package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Time nobody can be booked: a holiday, leave, a room under repair. */
@Getter
@Setter
@Entity
public class BookingBlock extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockTarget targetType = BlockTarget.ALL;

    /** The staff user or resource; empty when the block is for everyone. */
    private Long targetId;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(length = 500)
    private String reason;

    private String createdByName;
}
