package com.posapp.hr;

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

/**
 * A person who works for the business (not necessarily someone who logs in: {@link #userId} links the staff login, when
 * there is one). Salary lives on the Contract, not here.
 */
@Getter
@Setter
@Entity
public class Employee extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String employeeNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;
    private String nationalId;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;

    @Column(nullable = false)
    private LocalDate hireDate;

    private LocalDate terminationDate;
    private String terminationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    private String jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    private Position position;

    /** Who this employee reports to. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee manager;

    /** Branch the employee works at (null = head office). */
    @Column(name = "branch_id")
    private Long branchId;

    /** Staff login (AppUser id), when the employee also uses the system — lets them clock in and out themselves. */
    private Long userId;

    private String bankName;
    private String bankAccount;
    private String taxNumber;
    private String socialSecurityNumber;

    /** When true, payroll deducts absent days and pays overtime from the attendance records. Off for salaried staff who don't clock in. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean attendanceTracked = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
