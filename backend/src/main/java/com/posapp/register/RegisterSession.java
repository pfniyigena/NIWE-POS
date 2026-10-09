package com.posapp.register;

import com.posapp.common.BaseEntity;
import com.posapp.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
public class RegisterSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser cashier;

    private BigDecimal openingFloat = BigDecimal.ZERO;
    private Instant openedAt = Instant.now();
    private Instant closedAt;

    private BigDecimal countedCash;
    private BigDecimal cashVariance;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.OPEN;
}
