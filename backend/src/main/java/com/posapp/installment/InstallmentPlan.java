package com.posapp.installment;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An agreement to pay one issued invoice in dated instalments. The invoice stays the legal debt (its payments, receipts and accounting
 * are unchanged): the plan only says <i>when</i> the unpaid part falls due and adds optional interest and late fees on top.
 * {@link #baselinePaid} is what the invoice had already received when the plan was made (including the deposit), so anything paid
 * after that counts toward the instalments, whether it is recorded from the plan or straight on the invoice.
 */
@Getter
@Setter
@Entity
public class InstallmentPlan extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String planNumber;

    @Column(nullable = false)
    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;

    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentPlanStatus status = InstallmentPlanStatus.ACTIVE;

    @Column(nullable = false)
    private BigDecimal invoiceTotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal deposit = BigDecimal.ZERO;
    /** What is spread over the instalments (principal): the invoice's unpaid amount after the deposit. */
    @Column(nullable = false)
    private BigDecimal financedAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal baselinePaid = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal interestRate = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalInterest = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestMethod interestMethod = InterestMethod.NONE;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentFrequency frequency = InstallmentFrequency.MONTHLY;

    private int numberOfInstallments;
    private LocalDate firstDueDate;

    /** A late fee is added to an instalment still unpaid this many days after its due date. */
    private int graceDays;
    @Column(nullable = false)
    private BigDecimal lateFeeFixed = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal lateFeePercent = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean remindersEnabled = true;

    /** Amounts are whole currency units (no cents); the last instalment takes any leftover. */
    @Column(nullable = false, columnDefinition = "boolean default false not null")
    private boolean wholeUnits = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** What the system changed on its own since the plan was made (invoice reduced by a return or credit) and when the schedule was edited. */
    @Column(columnDefinition = "TEXT")
    private String adjustmentNote;
    private String createdByName;
    private LocalDate cancelledOn;
    private String cancelReason;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq asc")
    private List<Installment> installments = new ArrayList<>();
}
