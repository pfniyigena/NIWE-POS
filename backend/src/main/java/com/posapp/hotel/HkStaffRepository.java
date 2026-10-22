package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HkStaffRepository extends JpaRepository<HkStaff, Long> {

    java.util.List<HkStaff> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
