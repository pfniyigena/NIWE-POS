package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A record that a conflict search was run: who searched for what, and how many hits it found. */
@Getter
@Setter
@Entity
public class LawConflictCheck extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String query;

    /** The matter being checked, when the search was run from one. */
    private Long matterId;

    private String matterNumber;

    @Column(nullable = false)
    private int hits;

    private Long checkedByUserId;
    private String checkedByName;
}
