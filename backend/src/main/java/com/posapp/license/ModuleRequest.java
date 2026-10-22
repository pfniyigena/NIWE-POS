package com.posapp.license;

import com.posapp.common.BaseEntity;
import com.posapp.modules.ModuleKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An admin's request to have a module added to the license. Closed automatically when a license that contains the module is installed. */
@Getter
@Setter
@Entity
public class ModuleRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleKey moduleKey;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleRequestStatus status = ModuleRequestStatus.PENDING;

    private Instant closedAt;

    @Column(length = 255)
    private String closedNote;

    private boolean emailed;
}
