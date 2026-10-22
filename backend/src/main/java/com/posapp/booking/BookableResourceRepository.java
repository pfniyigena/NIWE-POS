package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookableResourceRepository extends JpaRepository<BookableResource, Long> {
    List<BookableResource> findAllByOrderByKindAscNameAsc();

    List<BookableResource> findByActiveTrueOrderByNameAsc();
}
