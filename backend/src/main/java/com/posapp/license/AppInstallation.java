package com.posapp.license;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** The one row that names this installation: a random id created at the first start. A license is issued for exactly this id. */
@Getter
@Setter
@Entity
public class AppInstallation extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String installationId;
}
