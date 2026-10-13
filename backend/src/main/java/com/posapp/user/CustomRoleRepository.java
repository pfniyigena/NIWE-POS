package com.posapp.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomRoleRepository extends JpaRepository<CustomRole, Long> {

    List<CustomRole> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
