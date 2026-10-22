package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A landlord: the person or company that owns the properties we manage for them. */
@Getter
@Setter
@Entity
@Table(name = "pm_owner")
public class PmOwner extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 40)
    private String phoneKey;

    private String email;

    /** National ID, passport or company registration number. */
    @Column(length = 60)
    private String idNumber;

    @Column(length = 60)
    private String taxNumber;

    @Column(length = 500)
    private String address;

    /** Where rent collected is paid out to the owner. */
    @Column(length = 120)
    private String bankName;

    @Column(length = 60)
    private String bankAccount;

    /** Management fee, as a percentage of the rent collected, for this owner's properties unless a property sets its own. */
    @Column(precision = 7, scale = 2)
    private BigDecimal feePercent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;
}
