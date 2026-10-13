package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    java.util.List<PublicHoliday> findAllByOrderByHolidayDateAsc();

    java.util.List<PublicHoliday> findByHolidayDateBetween(java.time.LocalDate from, java.time.LocalDate to);

    boolean existsByHolidayDate(java.time.LocalDate date);
}
