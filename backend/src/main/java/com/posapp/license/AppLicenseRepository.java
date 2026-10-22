package com.posapp.license;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppLicenseRepository extends JpaRepository<AppLicense, Long> {
    Optional<AppLicense> findFirstByActiveTrueOrderBySerialDesc();

    List<AppLicense> findAllByOrderBySerialDesc();

    boolean existsByLicenseId(String licenseId);
}
