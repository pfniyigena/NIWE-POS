package com.posapp.manufacturing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Manufacturing Module — "Indirect Costs" (Fourier's own term). A named overhead cost pool an
 * admin defines once (electricity, factory rent share, equipment depreciation, and so on) and
 * then picks from when creating/confirming a Manufacturing Order — see {@code
 * ManufacturingOrderOverhead} for how a selected pool's {@code rate}/{@code allocationMethod} are
 * snapshotted onto the order at that moment, the same "don't rewrite history when the admin
 * record later changes" convention every other cost figure in this app already follows (product
 * cost price, tax rate, payment method). Same flat, admin-managed lookup shape as {@code
 * Workstation}/{@code PaymentMethodEntity}.
 */
@Getter
@Setter
@Entity
public class OverheadCost extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private OverheadAllocationMethod allocationMethod = OverheadAllocationMethod.PER_UNIT;

    /** Meaning depends on allocationMethod — a flat total, an amount per unit produced, or a percentage. See OverheadAllocationMethod's javadoc. */
    private BigDecimal rate = BigDecimal.ZERO;

    private boolean active = true;
}
