package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RateRuleRepository extends JpaRepository<RateRule, Long> {

    List<RateRule> findAllByOrderByStartDateDescIdDesc();

    List<RateRule> findByActiveTrue();

    long countByRoomTypeId(Long roomTypeId);
}
