package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingBlockRepository extends JpaRepository<BookingBlock, Long> {
    /** Blocks that overlap [from, to). */
    List<BookingBlock> findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(LocalDateTime to, LocalDateTime from);
}
