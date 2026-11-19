package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlineDriverRepository extends JpaRepository<OnlineDriver, Long> {
    List<OnlineDriver> findAllByOrderByActiveDescNameAsc();

    long countByActiveTrue();
}
