package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** Last matter number used for one branch and year, so numbers run RW-2026-0001, RW-2026-0002... and restart each year. */
@Getter
@Setter
@Entity
public class LawMatterCounter extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String counterKey;

    @Column(nullable = false)
    private int lastSeq = 0;
}
