package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingStaffLeaveRepository extends JpaRepository<BookingStaffLeave, Long> {

    List<BookingStaffLeave> findByUserIdOrderByFromDateAsc(Long userId);

    /** Leave that covers the given day for any of the people. */
    List<BookingStaffLeave> findByUserIdInAndFromDateLessThanEqualAndToDateGreaterThanEqual(Collection<Long> userIds, LocalDate day, LocalDate sameDay);

    /** Leave that has not ended before the given day (current and future). */
    List<BookingStaffLeave> findByToDateGreaterThanEqualOrderByFromDateAsc(LocalDate day);
}
