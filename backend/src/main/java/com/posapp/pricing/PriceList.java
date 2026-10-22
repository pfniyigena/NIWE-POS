package com.posapp.pricing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A named set of prices. Products with their own entry use it (by quantity step); every other product is the normal price moved by {@code adjustPercent} (negative = cheaper). */
@Entity
@Table(name = "price_list")
public class PriceList extends BaseEntity {

    @Column(nullable = false)
    private String name;
    private String description;
    @Column(name = "adjust_percent", nullable = false)
    private BigDecimal adjustPercent = BigDecimal.ZERO;
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAdjustPercent() { return adjustPercent; }
    public void setAdjustPercent(BigDecimal adjustPercent) { this.adjustPercent = adjustPercent; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Usable today: switched on and inside its dates. */
    public boolean isCurrent(LocalDate today) {
        return active && (validFrom == null || !today.isBefore(validFrom)) && (validTo == null || !today.isAfter(validTo));
    }
}
