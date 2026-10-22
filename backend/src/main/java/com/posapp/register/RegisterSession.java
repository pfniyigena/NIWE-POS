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

    /** What the system expected in the drawer when the shift was closed (float + cash sales - cash refunds + paid in - paid out - drops). */
    private BigDecimal expectedCash;
    /** Why the count was off, when it was. */
    private String varianceNote;
    private String closedBy;
    /** The Z report's number: 1, 2, 3... per register, given when the shift is closed. */
    private Integer zNumber;
    /** The Z report as it stood when the shift was closed (JSON); reprints show this, never the live figures. */
    @jakarta.persistence.Column(columnDefinition = "text")
    private String zSnapshot;
    private int zPrints;
    /** The notes and coins counted at closing, as JSON [{value,count}], when a denomination count was made. */
    @jakarta.persistence.Column(columnDefinition = "text")
    private String countDetail;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.OPEN;
}
