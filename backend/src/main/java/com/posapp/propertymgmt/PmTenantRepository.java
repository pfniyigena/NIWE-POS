package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PmTenantRepository extends JpaRepository<PmTenant, Long> {
}
