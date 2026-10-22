package com.posapp.booking;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A booked slot. Times are local to the business (the store time zone). {@link #busyUntil} is the end plus the service's buffer: the
 * staff member and resource are taken until then. {@code sourceType}/{@code sourceId} let other modules (a law matter, a work order) tag
 * the appointments they create.
 */
@Getter
@Setter
@Entity
@Table(indexes = {@Index(columnList = "startAt"), @Index(columnList = "staffUserId"), @Index(columnList = "resourceId")})
public class Appointment extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String number;

    /** Random code the guest uses to look up or cancel a public booking. */
    @Column(nullable = false, unique = true, length = 20)
    private String confirmationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentSource source = AppointmentSource.STAFF;

    @Column(nullable = false)
    private Long serviceId;

    @Column(nullable = false)
    private String serviceName;

    private String serviceColor;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime busyUntil;

    @Column(nullable = false)
    private int durationMinutes;

    private Long staffUserId;
    private String staffName;

    private Long resourceId;
    private String resourceName;

    private Long customerId;

    @Column(nullable = false)
    private String guestName;

    private String guestPhone;
    private String guestEmail;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode paymentMode = PaymentMode.NONE;

    /** What must be paid on the invoice before the booking is confirmed. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amountDue = BigDecimal.ZERO;

    /** Paid so far on the invoice, as last seen. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    private Long invoiceId;
    private String invoiceNumber;

    private Instant holdExpiresAt;

    @Column(length = 500)
    private String cancelReason;

    private Instant cancelledAt;

    private Long branchId;
    private String createdByName;

    @Column(length = 40)
    private String sourceType;
    private Long sourceId;
}
