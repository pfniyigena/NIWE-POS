package com.posapp.license;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A license file that was installed. Every installed file is kept (the history); exactly one row is {@code active}. The whole signed
 * file is stored in {@code envelope} and checked again whenever the license is loaded, so a row edited by hand in the database is
 * not believed; the other columns only make the history readable.
 */
@Getter
@Setter
@Entity
public class AppLicense extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String licenseId;

    @Column(nullable = false)
    private long serial;

    @Column(nullable = false)
    private String customer;

    @Column(nullable = false, length = 64)
    private String installationId;

    @Column(nullable = false)
    private LocalDate issuedAt;

    private LocalDate expiresAt;

    /** Comma separated module keys, as listed in the file. */
    @Column(nullable = false, length = 1000)
    private String modules;

    /** The signed file exactly as it was uploaded. */
    @Column(nullable = false, length = 8000)
    private String envelope;

    private String installedBy;

    private boolean active;
}
