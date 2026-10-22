package com.posapp.pricing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A group of customers (Wholesale, Staff, VIP) that share one price list. */
@Entity
@Table(name = "customer_group")
public class CustomerGroup extends BaseEntity {

    @Column(nullable = false)
    private String name;
    private String description;
    @Column(name = "price_list_id")
    private Long priceListId;
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getPriceListId() { return priceListId; }
    public void setPriceListId(Long priceListId) { this.priceListId = priceListId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
