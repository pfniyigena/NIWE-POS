package com.posapp.apps.efris;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EfrisSettingRepository extends JpaRepository<EfrisSetting, Long> {
    Optional<EfrisSetting> findByRegisterId(Long registerId);
}
