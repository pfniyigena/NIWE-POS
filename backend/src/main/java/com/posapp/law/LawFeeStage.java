package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One instalment of a fixed fee (for example "On filing the claim"). */
@Getter
@Setter
@Entity
public class LawFeeStage extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageStatus status = StageStatus.PLANNED;

    private Long billId;
}
