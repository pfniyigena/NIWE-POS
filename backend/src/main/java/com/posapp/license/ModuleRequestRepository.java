package com.posapp.license;

import com.posapp.modules.ModuleKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleRequestRepository extends JpaRepository<ModuleRequest, Long> {
    List<ModuleRequest> findAllByOrderByCreatedAtDesc();

    List<ModuleRequest> findByStatus(ModuleRequestStatus status);

    Optional<ModuleRequest> findFirstByModuleKeyAndStatus(ModuleKey moduleKey, ModuleRequestStatus status);
}
