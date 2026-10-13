package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** An employment contract: the pay terms payroll uses. An employee has at most one ACTIVE contract at a time. */
@Getter
@Setter
@Entity
public class Contract extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType type = ContractType.PERMANENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Null for a permanent contract. */
    private LocalDate endDate;

    private String jobTitle;

    /** Monthly basic (gross) salary. */
    @Column(nullable = false)
    private BigDecimal baseSalary;

    private LocalDate terminationDate;
    private String terminationReason;

    /** Number of the contract this one renews, when it does. */
    private String renewsContractNumber;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractComponent> components = new ArrayList<>();
}
