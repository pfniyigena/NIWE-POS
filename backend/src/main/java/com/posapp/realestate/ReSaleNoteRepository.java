package com.posapp.realestate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReSaleNoteRepository extends JpaRepository<ReSaleNote, Long> {
    List<ReSaleNote> findBySaleIdOrderByIdDesc(Long saleId);

    List<ReSaleNote> findBySaleIdIn(Collection<Long> saleIds);
}
