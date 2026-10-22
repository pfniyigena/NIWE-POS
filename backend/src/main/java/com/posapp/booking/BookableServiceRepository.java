package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookableServiceRepository extends JpaRepository<BookableService, Long> {
    List<BookableService> findAllByOrderByNameAsc();

    List<BookableService> findByActiveTrueOrderByNameAsc();
}
