package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnlineAdvertiserRepository extends JpaRepository<OnlineAdvertiser, Long> {

    List<OnlineAdvertiser> findAllByOrderByNameAsc();
}
