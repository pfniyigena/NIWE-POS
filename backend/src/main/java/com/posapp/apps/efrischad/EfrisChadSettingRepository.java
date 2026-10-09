package com.posapp.apps.efrischad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EfrisChadSettingRepository extends JpaRepository<EfrisChadSetting, Long> {
    Optional<EfrisChadSetting> findByRegisterId(Long registerId);
}
