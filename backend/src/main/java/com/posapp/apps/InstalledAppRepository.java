package com.posapp.apps;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstalledAppRepository extends JpaRepository<InstalledApp, Long> {
    Optional<InstalledApp> findByAppKey(AppKey appKey);
}
