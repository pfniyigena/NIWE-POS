package com.posapp.workorder;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One checklist step of a service package. */
@Getter
@Setter
@Entity
public class ServicePackageItem extends BaseEntity {

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 500)
    private String text;
}
