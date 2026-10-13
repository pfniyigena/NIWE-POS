package com.posapp.modules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModuleSettingRepository extends JpaRepository<ModuleSetting, Long> {
    Optional<ModuleSetting> findByModuleKey(ModuleKey moduleKey);
}
