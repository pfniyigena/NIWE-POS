package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Manufacturing Module (Fourier: "Add Manufacturing Module" — "Workstations" named as one of its
 * settings screens). Where a {@link RoutingOperation} actually happens on the shop floor — a
 * machine, a bench, a line, or just a named area. Deliberately a flat, admin-managed lookup list,
 * same shape as {@code PaymentMethodEntity}/{@code TaxRate}: a stable {@code code}, a display
 * {@code name}, and {@code active} so one can be retired without breaking a past routing/run that
 * already referenced it.
 *
 * <p>{@code hourlyRate} is this workstation's own operating cost per hour (machine time, power,
 * an operator's time — whatever this store wants to fold in) — used, alongside each {@link
 * RoutingOperation#getEstimatedMinutesPerUnit()}, to estimate labor/overhead cost for a
 * Manufacturing Order that follows a routing through this workstation. Defaults to zero, same
 * "no way to infer a real figure, so report zero until an admin fills it in" stance as {@code
 * Product.costPrice}.
 */
@Getter
@Setter
@Entity
public class Workstation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    private BigDecimal hourlyRate = BigDecimal.ZERO;

    private boolean active = true;
}
