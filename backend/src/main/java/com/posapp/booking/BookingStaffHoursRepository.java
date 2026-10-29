package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingStaffHoursRepository extends JpaRepository<BookingStaffHours, Long> {

    List<BookingStaffHours> findByUserId(Long userId);

    List<BookingStaffHours> findByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);
}
