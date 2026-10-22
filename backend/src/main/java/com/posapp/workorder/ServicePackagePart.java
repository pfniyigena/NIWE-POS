package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A product usually used by a service package. */
@Getter
@Setter
@Entity
public class ServicePackagePart extends BaseEntity {

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity = 1;

    /** True = covered by the package's fixed price; false = charged on top at the product's price. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean included = true;
}
