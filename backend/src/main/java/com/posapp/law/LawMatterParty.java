package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** An outside party on a matter, with the part they play. */
@Getter
@Setter
@Entity
public class LawMatterParty extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private Long contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartyRole role = PartyRole.OPPOSING_PARTY;

    @Column(length = 500)
    private String note;
}
