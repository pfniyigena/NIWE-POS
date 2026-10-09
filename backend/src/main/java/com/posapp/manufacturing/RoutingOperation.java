package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One ordered step of a {@link Routing} — see that entity's javadoc. {@code sequence} is display
 * order only (lower first), same convention as {@code PaymentMethodEntity.sortOrder}. {@code
 * workstation} is nullable — a step that isn't tied to a particular machine/bench still has a
 * name and a time estimate, it just doesn't roll into any workstation's own hourly-rate cost.
 * {@code estimatedMinutesPerUnit} scales with however many units a Manufacturing Order run
 * actually produces (this operation's time for a run of N units is {@code
 * estimatedMinutesPerUnit * N}), the same "per unit, not per batch" simplification {@link
 * BomLine#getQuantityPerUnit()} uses for material quantities — see that field's javadoc for why.
 */
@Getter
@Setter
@Entity
public class RoutingOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Routing routing;

    private int sequence;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Workstation workstation;

    private BigDecimal estimatedMinutesPerUnit = BigDecimal.ZERO;
}
