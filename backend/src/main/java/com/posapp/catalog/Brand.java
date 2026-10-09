package com.posapp.catalog;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * A product's manufacturer/brand (Fourier: "Add product brands") — a plain reference list, same
 * shape and lifecycle as {@link Category} (simple CRUD, admin-manageable, referenced optionally
 * by Product), but without a color: brands don't get their own terminal tile the way categories
 * do, so there's nothing for a color to drive here.
 */
@Getter
@Setter
@Entity
public class Brand extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
}
