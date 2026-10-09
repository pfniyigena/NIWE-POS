package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A restaurant-facing grouping of existing catalog products (Starters, Mains, Drinks...) — see {@link MenuItem}'s javadoc for why this doesn't duplicate Product data. */
@Getter
@Setter
@Entity
public class MenuCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private int sortOrder = 0;

    private boolean active = true;
}
