package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Someone who rents (or applied to rent) a unit. Becomes a customer when a lease starts, so rent can be invoiced to them. */
@Getter
@Setter
@Entity
@Table(name = "pm_tenant")
public class PmTenant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TenantKind kind = TenantKind.INDIVIDUAL;

    @Column(nullable = false, length = 200)
    private String name;

    /** For a company: the person to talk to. */
    @Column(length = 200)
    private String contactPerson;

    @Column(length = 40)
    private String phone;

    @Column(length = 40)
    private String phoneKey;

    private String email;

    /** National ID, passport or company registration number. */
    @Column(length = 60)
    private String idNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 200)
    private String employer;

    @Column(length = 200)
    private String emergencyName;

    @Column(length = 40)
    private String emergencyPhone;

    private Long customerId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;
}
