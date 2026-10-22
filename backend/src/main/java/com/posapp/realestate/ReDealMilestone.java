package com.posapp.realestate;

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
import java.time.LocalDate;

/** One payment the buyer is to make (deposit, an instalment, the balance). Invoiced on its own; the invoice shows what has been paid. */
@Getter
@Setter
@Entity
@Table(name = "re_deal_milestone", indexes = {@Index(columnList = "deal_id")})
public class ReDealMilestone extends BaseEntity {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneKind kind = MilestoneKind.INSTALMENT;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    private LocalDate dueDate;

    private Long invoiceId;

    @Column(nullable = false)
    private int sortOrder = 0;
}
