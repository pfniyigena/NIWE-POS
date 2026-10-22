package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** A staff member working on a matter. */
@Getter
@Setter
@Entity
public class LawMatterTeam extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamRole role = TeamRole.ASSOCIATE;
}
