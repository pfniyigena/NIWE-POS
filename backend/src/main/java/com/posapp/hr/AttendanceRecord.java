package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** One employee, one day. */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
public class AttendanceRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate workDate;

    private LocalTime clockIn;
    private LocalTime clockOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Column(nullable = false)
    private BigDecimal hoursWorked = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String note;
}
