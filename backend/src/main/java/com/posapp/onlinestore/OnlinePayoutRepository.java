package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlinePayoutRepository extends JpaRepository<OnlinePayout, Long> {
    List<OnlinePayout> findTop50ByOrderByPaidOnDescIdDesc();
}
