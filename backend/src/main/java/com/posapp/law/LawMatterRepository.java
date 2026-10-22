package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterRepository extends JpaRepository<LawMatter, Long> {
    List<LawMatter> findAllByOrderByOpenedDateDescIdDesc();

    List<LawMatter> findByClientCustomerIdOrderByOpenedDateDescIdDesc(Long clientCustomerId);

    long countByClientCustomerId(Long clientCustomerId);
}
