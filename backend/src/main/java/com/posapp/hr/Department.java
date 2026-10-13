package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** A department or team; departments nest through {@link #parent}. */
@Getter
@Setter
@Entity
public class Department extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department parent;

    @ManyToOne(fetch = FetchType.LAZY)
    private Employee manager;

    private boolean active = true;
}
