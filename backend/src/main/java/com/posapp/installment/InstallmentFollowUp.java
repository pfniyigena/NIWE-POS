package com.posapp.installment;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of the collections log of a plan: a call, a visit, an e-mail, a note, or a promise to pay. Never edited or deleted. */
@Getter
@Setter
@Entity
public class InstallmentFollowUp extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InstallmentPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentFollowUpKind kind = InstallmentFollowUpKind.NOTE;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** For a PROMISE: the date the customer said they would pay by. */
    private LocalDate promisedOn;

    private BigDecimal promisedAmount;

    private String createdByName;
}
