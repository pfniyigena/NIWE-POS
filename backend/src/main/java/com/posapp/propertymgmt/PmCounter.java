package com.posapp.propertymgmt;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** Last number used for one series of codes (owners, leases...), so codes run OW-0001, OW-0002... */
@Getter
@Setter
@Entity
public class PmCounter extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String counterKey;

    @Column(nullable = false)
    private int lastSeq = 0;
}
