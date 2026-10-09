package com.posapp.catalog;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    /** Hex color (e.g. "#8e44ad") used for this category's tile in the terminal. */
    @Column(nullable = false)
    private String colorHex;
}
