package com.posapp.hotel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    @Query("select g from Guest g where lower(g.fullName) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(g.phone, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(g.email, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(g.idNumber, '')) like lower(concat('%', :q, '%')) order by g.fullName asc")
    List<Guest> search(@Param("q") String q, Pageable pageable);

    List<Guest> findAllByOrderByFullNameAsc(Pageable pageable);
}
