package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** Last number used for one series of codes (properties, deals...), so codes run PR-0001, PR-0002... */
@Getter
@Setter
@Entity
public class ReCounter extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String counterKey;

    @Column(nullable = false)
    private int lastSeq = 0;
}
